//! Server settings, DNS-provider operations, and configuration import/export.

use super::{
    ClientGroup, PasswordEntry, RoutingRule, WebState, auth, log_event, model,
    normalize_client_vk_hashes, normalize_vk_access_token, now, wait_for_persistence,
};
use axum::{
    Json,
    extract::State,
    http::{StatusCode, header},
    response::{IntoResponse, Response},
};
use serde::{Deserialize, Serialize};
use std::net::Ipv4Addr;
use std::sync::atomic::Ordering;

#[derive(Deserialize)]
struct SettingsRequest {
    #[serde(default)]
    main_password: Option<String>,
    #[serde(default)]
    dns_provider: Option<String>,
    #[serde(default)]
    dns_primary: Option<String>,
    #[serde(default)]
    dns_secondary: Option<String>,
    #[serde(default)]
    auto_restart_interval_hours: Option<u8>,
    #[serde(default)]
    vk_token: Option<String>,
    #[serde(default)]
    vk_hashes: Option<String>,
}

#[derive(Clone, Copy)]
pub(super) struct DnsProvider {
    id: &'static str,
    primary: &'static str,
    secondary: &'static str,
}

pub(super) const DNS_PROVIDERS: [DnsProvider; 13] = [
    DnsProvider {
        id: "yandex",
        primary: "77.88.8.8",
        secondary: "77.88.8.1",
    },
    DnsProvider {
        id: "cloudflare",
        primary: "1.1.1.1",
        secondary: "1.0.0.1",
    },
    DnsProvider {
        id: "google",
        primary: "8.8.8.8",
        secondary: "8.8.4.4",
    },
    DnsProvider {
        id: "adguard",
        primary: "94.140.14.14",
        secondary: "94.140.15.14",
    },
    DnsProvider {
        id: "xbox",
        primary: "111.88.96.54",
        secondary: "111.88.96.55",
    },
    DnsProvider {
        id: "comms",
        primary: "83.220.169.155",
        secondary: "212.109.195.93",
    },
    DnsProvider {
        id: "geohide",
        primary: "193.233.112.67",
        secondary: "193.233.112.88",
    },
    DnsProvider {
        id: "quad9",
        primary: "9.9.9.9",
        secondary: "149.112.112.112",
    },
    DnsProvider {
        id: "opendns",
        primary: "208.67.222.222",
        secondary: "208.67.220.220",
    },
    DnsProvider {
        id: "rostelecom",
        primary: "95.189.32.74",
        secondary: "195.208.5.1",
    },
    DnsProvider {
        id: "nextdns",
        primary: "45.90.28.181",
        secondary: "45.90.30.181",
    },
    DnsProvider {
        id: "bizone",
        primary: "195.208.6.1",
        secondary: "195.208.7.1",
    },
    DnsProvider {
        id: "nsdi",
        primary: "195.208.4.1",
        secondary: "195.208.5.1",
    },
];

pub(super) fn dns_provider_by_id(id: &str) -> Option<DnsProvider> {
    DNS_PROVIDERS
        .iter()
        .copied()
        .find(|provider| provider.id == id)
}

pub(crate) fn dns_provider_is_known(id: &str) -> bool {
    dns_provider_by_id(id).is_some()
}

pub(crate) fn dns_provider_id(dns: &str) -> Option<&'static str> {
    DNS_PROVIDERS
        .iter()
        .find(|provider| dns == format!("{},{}", provider.primary, provider.secondary))
        .map(|provider| provider.id)
}

pub(super) fn provider_dns(provider: DnsProvider) -> String {
    format!("{},{}", provider.primary, provider.secondary)
}

pub(crate) fn provider_dns_by_id(id: &str) -> Option<String> {
    dns_provider_by_id(id).map(provider_dns)
}

pub(super) fn manual_dns(primary: Option<&str>, secondary: Option<&str>) -> Result<String, &'static str> {
    let Some(primary) = primary else {
        return Err("primary DNS is required");
    };
    let primary = primary.trim();
    let secondary = secondary.unwrap_or_default().trim();
    if primary.is_empty() {
        return Err("primary DNS is required");
    }
    if primary.parse::<Ipv4Addr>().is_err()
        || (!secondary.is_empty() && secondary.parse::<Ipv4Addr>().is_err())
    {
        return Err("DNS must be a valid IPv4 address");
    }
    Ok(if secondary.is_empty() {
        primary.to_owned()
    } else {
        format!("{primary},{secondary}")
    })
}

pub(crate) fn deploy_dns_profile_or_yandex(existing_dns: &str) -> String {
    let addresses: Vec<_> = existing_dns
        .split(',')
        .map(str::trim)
        .filter(|address| !address.is_empty())
        .collect();
    if addresses
        .iter()
        .any(|&a| a == "95.182.120.241" || a == "37.230.192.51")
    {
        return "193.233.112.67,193.233.112.88".to_owned();
    }
    if addresses
        .iter()
        .any(|&a| a == "111.88.96.50" || a == "111.88.96.51")
    {
        return "111.88.96.54,111.88.96.55".to_owned();
    }
    let first = addresses.first().copied();
    let selected = DNS_PROVIDERS
        .iter()
        .filter_map(|provider| {
            let matches = addresses
                .iter()
                .filter(|address| **address == provider.primary || **address == provider.secondary)
                .count();
            (matches > 0).then_some((
                provider,
                (
                    matches,
                    usize::from(first == Some(provider.primary)),
                    usize::from(first == Some(provider.secondary)),
                ),
            ))
        })
        .max_by_key(|(_, score)| *score)
        .map(|(provider, _)| *provider)
        .unwrap_or(DNS_PROVIDERS[0]);
    provider_dns(selected)
}

pub(super) async fn settings_get(State(state): State<WebState>) -> impl IntoResponse {
    let db = state.app.db.read().await;
    let dns = db.dns.clone();
    let mut parts = dns.splitn(2, ',');
    let primary = parts.next().unwrap_or("77.88.8.8").trim().to_owned();
    let secondary = parts.next().unwrap_or("77.88.8.1").trim().to_owned();
    let provider = if db.dns_provider == "custom" || dns_provider_by_id(&db.dns_provider).is_some()
    {
        db.dns_provider.as_str()
    } else {
        dns_provider_id(&dns).unwrap_or("custom")
    };
    let vk_hashes = if !db.vk_hashes.is_empty() {
        db.vk_hashes.clone()
    } else {
        db.passwords
            .values()
            .find(|p| !p.vk_hashes.is_empty())
            .map(|p| p.vk_hashes.clone())
            .unwrap_or_default()
    };
    Json(serde_json::json!({
        "main_password": db.main_password,
        "dns_provider": provider,
        "dns_primary": primary,
        "dns_secondary": secondary,
        "auto_restart_interval_hours": db.auto_restart_interval_hours(),
        "vk_token": db.vk_access_token,
        "vk_hashes": vk_hashes,
        "peer_port": state.app.listen.port(),
        "restart_required": db.main_password != state.app.startup_main_password
    }))
}

pub(super) async fn settings_post(
    State(state): State<WebState>,
    Json(request): Json<SettingsRequest>,
) -> Response {
    if let Some(password) = request.main_password.as_ref()
        && !password.is_empty()
        && password.len() > 128
    {
        return (
            StatusCode::BAD_REQUEST,
            "password length must be 1..128 or empty",
        )
            .into_response();
    }
    let dns = match request.dns_provider.as_deref() {
        Some("custom") | None
            if request.dns_primary.is_some() || request.dns_secondary.is_some() =>
        {
            match manual_dns(
                request.dns_primary.as_deref(),
                request.dns_secondary.as_deref(),
            ) {
                Ok(dns) => Some((dns, "custom".to_owned())),
                Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
            }
        }
        Some("custom") => {
            return (StatusCode::BAD_REQUEST, "primary DNS is required").into_response();
        }
        Some(provider_id) => {
            let Some(provider) = dns_provider_by_id(provider_id) else {
                return (StatusCode::BAD_REQUEST, "unknown DNS provider").into_response();
            };
            Some((provider_dns(provider), provider_id.to_owned()))
        }
        None => None,
    };
    if let Some(hours) = request.auto_restart_interval_hours
        && !matches!(hours, 0 | 12 | 24 | 48 | 72)
    {
        return (
            StatusCode::BAD_REQUEST,
            "auto restart interval must be 0, 12, 24, 48, or 72 hours",
        )
            .into_response();
    }
    let requested_vk_access_token = match request.vk_token {
        Some(token) => match normalize_vk_access_token(&token) {
            Ok(token) => Some(token),
            Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
        },
        None => None,
    };
    let requested_vk_hashes = match request.vk_hashes {
        Some(raw) => match normalize_client_vk_hashes(&raw) {
            Ok(hashes) => Some(hashes),
            Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
        },
        None => None,
    };
    let requested_auto_restart_interval = request.auto_restart_interval_hours;
    let (revision, retired_main_password, applied_dns) = {
        let mut db = state.app.db.write().await;
        let retired_main_password = if let Some(password) = request.main_password {
            if db.passwords.contains_key(&password) {
                return (
                    StatusCode::BAD_REQUEST,
                    "Пароль совпадает с паролем клиента",
                )
                    .into_response();
            }
            (db.main_password != password)
                .then(|| std::mem::replace(&mut db.main_password, password))
        } else {
            None
        };
        let applied_dns = dns
            .as_ref()
            .and_then(|(dns, _)| (db.dns != *dns).then(|| dns.clone()));
        if let Some((dns, provider)) = dns.as_ref() {
            db.dns.clone_from(dns);
            db.dns_provider.clone_from(provider);
        }
        if let Some(hours) = requested_auto_restart_interval {
            db.set_auto_restart_interval_hours(hours);
        }
        if let Some(token) = requested_vk_access_token {
            db.vk_access_token = token;
        }
        if let Some(hashes) = requested_vk_hashes {
            db.vk_hashes = hashes;
        }
        (
            state.app.db_persistence.submit(db.clone()),
            retired_main_password,
            applied_dns,
        )
    };
    match state.app.db_persistence.wait(revision).await {
        Ok(()) => {}
        Err(error) => {
            eprintln!("[SETTINGS] Failed to persist settings: {error}");
            return (
                StatusCode::INTERNAL_SERVER_ERROR,
                "failed to persist settings",
            )
                .into_response();
        }
    }
    if let Some(hours) = requested_auto_restart_interval {
        state.app.auto_restart_interval_tx.send_replace(hours);
    }
    let main_password_changed = retired_main_password.is_some();
    if let Some(password) = retired_main_password
        && !password.is_empty()
    {
        state.app.derived_keys.remove(&password);
    }
    if main_password_changed && let Err(message) = auth::refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-shod-reload-error", warning)], "saved").into_response();
    }
    if let Some(dns) = applied_dns {
        *state.app.dns.write().await = dns;
        match crate::protocol::broadcast_tunnel_configuration(&state.app).await {
            Ok(sent) => log_event(
                &state.app,
                "INFO",
                "DNS",
                &format!("DNS configuration applied to {sent} active paths"),
            ),
            Err(error) => {
                let warning = format!("saved but DNS apply failed: {error:#}");
                return (StatusCode::OK, [("x-shod-reload-error", warning)], "saved")
                    .into_response();
            }
        }
    }
    let restart_required = {
        let db = state.app.db.read().await;
        db.main_password != state.app.startup_main_password
    };
    Json(serde_json::json!({ "restart_required": restart_required })).into_response()
}

#[derive(Deserialize)]
struct ApiDnsRequest {
    #[serde(default)]
    dns_provider: Option<String>,
    #[serde(default)]
    dns_primary: Option<String>,
    #[serde(default)]
    dns_secondary: Option<String>,
}

pub(super) async fn api_settings_dns(
    State(state): State<WebState>,
    Json(request): Json<ApiDnsRequest>,
) -> Response {
    let dns = match request.dns_provider.as_deref() {
        Some("custom") | None
            if request.dns_primary.is_some() || request.dns_secondary.is_some() =>
        {
            match manual_dns(
                request.dns_primary.as_deref(),
                request.dns_secondary.as_deref(),
            ) {
                Ok(dns) => (dns, "custom".to_owned()),
                Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
            }
        }
        Some("custom") => {
            return (StatusCode::BAD_REQUEST, "primary DNS is required").into_response();
        }
        Some(provider_id) => {
            let Some(provider) = dns_provider_by_id(provider_id) else {
                return (StatusCode::BAD_REQUEST, "unknown DNS provider").into_response();
            };
            (provider_dns(provider), provider_id.to_owned())
        }
        None => return (StatusCode::BAD_REQUEST, "missing DNS parameters").into_response(),
    };
    let (revision, applied_dns) = {
        let mut db = state.app.db.write().await;
        let applied_dns = (db.dns != dns.0).then(|| dns.0.clone());
        db.dns = dns.0;
        db.dns_provider = dns.1;
        (state.app.db_persistence.submit(db.clone()), applied_dns)
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if let Some(new_dns) = applied_dns {
        *state.app.dns.write().await = new_dns;
        let _ = crate::protocol::broadcast_tunnel_configuration(&state.app).await;
    }
    Json(serde_json::json!({ "ok": true })).into_response()
}

#[derive(Deserialize)]
struct RestartIntervalRequest {
    interval_hours: u8,
}

pub(super) async fn api_settings_restart_interval(
    State(state): State<WebState>,
    Json(request): Json<RestartIntervalRequest>,
) -> Response {
    if !matches!(request.interval_hours, 0 | 12 | 24 | 48 | 72) {
        return (
            StatusCode::BAD_REQUEST,
            "auto restart interval must be 0, 12, 24, 48, or 72 hours",
        )
            .into_response();
    }
    let revision = {
        let mut db = state.app.db.write().await;
        db.set_auto_restart_interval_hours(request.interval_hours);
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    state
        .app
        .auto_restart_interval_tx
        .send_replace(request.interval_hours);
    Json(serde_json::json!({ "ok": true, "interval_hours": request.interval_hours }))
        .into_response()
}

#[derive(Deserialize)]
struct MasterPasswordRequest {
    password: String,
}

pub(super) async fn api_settings_master_password(
    State(state): State<WebState>,
    Json(request): Json<MasterPasswordRequest>,
) -> Response {
    if !request.password.is_empty() && request.password.len() > 128 {
        return (
            StatusCode::BAD_REQUEST,
            "password length must be 1..128 or empty",
        )
            .into_response();
    }
    let (revision, retired_password) = {
        let mut db = state.app.db.write().await;
        if !request.password.is_empty() && db.passwords.contains_key(&request.password) {
            return (
                StatusCode::BAD_REQUEST,
                "Пароль совпадает с паролем клиента",
            )
                .into_response();
        }
        let retired = (db.main_password != request.password)
            .then(|| std::mem::replace(&mut db.main_password, request.password));
        (state.app.db_persistence.submit(db.clone()), retired)
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if let Some(p) = retired_password
        && !p.is_empty()
    {
        state.app.derived_keys.remove(&p);
    }
    let _ = auth::refresh_credentials_report(&state).await;
    Json(serde_json::json!({ "ok": true })).into_response()
}

#[derive(Serialize, Deserialize, Clone)]
struct ServerExportData {
    version: u32,
    exported_at: i64,
    main_password: String,
    dns: String,
    dns_provider: String,
    auto_restart_interval_hours: Option<u8>,
    vk_access_token: String,
    vk_hashes: String,
    groups: std::collections::BTreeMap<String, ClientGroup>,
    routing_rules: std::collections::BTreeMap<String, RoutingRule>,
    clients: Vec<ExportClientEntry>,
}

#[derive(Serialize, Deserialize, Clone)]
struct ExportClientEntry {
    password: String,
    name: String,
    expires_at: i64,
    down_bytes: i64,
    up_bytes: i64,
    #[serde(default)]
    vk_hash: String,
    #[serde(default)]
    vk_hashes: String,
    #[serde(default)]
    vk_token_enabled: bool,
    #[serde(default)]
    ports: String,
    #[serde(default)]
    dtls_port: u16,
    #[serde(default)]
    local_port: u16,
    #[serde(default)]
    group_id: String,
    #[serde(default)]
    routing_rule_id: String,
    #[serde(default)]
    is_deactivated: bool,
    #[serde(default)]
    sub_uuid: String,
    #[serde(default)]
    workers: Option<u32>,
    #[serde(default)]
    ip: String,
}

pub(super) async fn api_export_get(State(state): State<WebState>) -> impl IntoResponse {
    let db = state.app.db.read().await;
    let clients: Vec<ExportClientEntry> = db
        .passwords
        .iter()
        .map(|(pwd, entry)| ExportClientEntry {
            password: pwd.clone(),
            name: entry.name.clone(),
            expires_at: entry.expires_at,
            down_bytes: entry.down_bytes,
            up_bytes: entry.up_bytes,
            vk_hash: entry.vk_hash.clone(),
            vk_hashes: entry.vk_hashes.clone(),
            vk_token_enabled: entry.vk_token_enabled,
            ports: entry.ports.clone(),
            dtls_port: entry.dtls_port,
            local_port: entry.local_port,
            group_id: entry.group_id.clone(),
            routing_rule_id: entry.routing_rule_id.clone(),
            is_deactivated: entry.is_deactivated,
            sub_uuid: entry.sub_uuid.clone(),
            workers: entry.workers,
            ip: entry.ip.clone(),
        })
        .collect();
    let export = ServerExportData {
        version: 1,
        exported_at: now(),
        main_password: db.main_password.clone(),
        dns: db.dns.clone(),
        dns_provider: db.dns_provider.clone(),
        auto_restart_interval_hours: db.auto_restart_interval_hours,
        vk_access_token: db.vk_access_token.clone(),
        vk_hashes: db.vk_hashes.clone(),
        groups: db.groups.clone(),
        routing_rules: db.routing_rules.clone(),
        clients,
    };
    Json(export)
}

#[derive(Deserialize)]
struct ServerImportPayload {
    #[serde(default)]
    import_clients: Option<bool>,
    #[serde(default)]
    import_settings: Option<bool>,
    #[serde(default)]
    config: Option<ServerExportData>,
    #[serde(flatten)]
    direct_config: Option<ServerExportData>,
}

pub(super) async fn api_import_post(
    State(state): State<WebState>,
    Json(payload): Json<ServerImportPayload>,
) -> Response {
    let export_opt = payload.config.or(payload.direct_config);
    let Some(export) = export_opt else {
        return (StatusCode::BAD_REQUEST, "invalid or empty config").into_response();
    };
    let do_clients = payload.import_clients.unwrap_or(true);
    let do_settings = payload.import_settings.unwrap_or(true);
    let mut added_clients = 0usize;
    let mut applied_dns = None;
    let mut applied_interval = None;

    let revision = {
        let mut db = state.app.db.write().await;
        if do_clients {
            for c in export.clients {
                if !db.passwords.contains_key(&c.password) {
                    let sub_uuid = if c.sub_uuid.trim().is_empty() {
                        model::random_uuid_v4()
                    } else {
                        c.sub_uuid
                    };
                    let ip = model::import_ip(&db, &c.ip);
                    let client_name = if c.name.trim().is_empty()
                        || model::is_client_name_taken(
                            model::existing_client_names_in_scope(&db, &c.group_id, None),
                            &c.name,
                        ) {
                        model::generate_unique_client_name(model::existing_client_names_in_scope(
                            &db,
                            &c.group_id,
                            None,
                        ))
                    } else {
                        c.name
                    };
                    db.passwords.insert(
                        c.password.clone(),
                        PasswordEntry {
                            ip,
                            expires_at: c.expires_at,
                            down_bytes: c.down_bytes,
                            up_bytes: c.up_bytes,
                            vk_hash: c.vk_hash,
                            ports: c.ports,
                            is_deactivated: c.is_deactivated,
                            name: client_name,
                            vk_hashes: c.vk_hashes,
                            vk_token_enabled: c.vk_token_enabled,
                            dtls_port: c.dtls_port,
                            wg_port: 0,
                            local_port: c.local_port,
                            group_id: c.group_id,
                            routing_rule_id: c.routing_rule_id,
                            sub_uuid,
                            workers: c.workers.filter(|&w| (3..=100).contains(&w)),
                        },
                    );
                    added_clients += 1;
                }
            }
            for (gid, g) in export.groups {
                db.groups.entry(gid).or_insert(g);
            }
            for (rid, r) in export.routing_rules {
                db.routing_rules.entry(rid).or_insert(r);
            }
        }
        if do_settings {
            if !export.main_password.is_empty() && !db.passwords.contains_key(&export.main_password)
            {
                db.main_password = export.main_password;
            }
            if !export.dns.is_empty() {
                applied_dns = (db.dns != export.dns).then(|| export.dns.clone());
                db.dns = export.dns;
                db.dns_provider = export.dns_provider;
            }
            if let Some(hours) = export.auto_restart_interval_hours {
                db.set_auto_restart_interval_hours(hours);
                applied_interval = Some(hours);
            }
            if !export.vk_access_token.is_empty() {
                db.vk_access_token = export.vk_access_token;
            }
            if !export.vk_hashes.is_empty() {
                db.vk_hashes = export.vk_hashes;
            }
        }
        state.app.db_persistence.submit(db.clone())
    };

    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if let Some(dns) = applied_dns {
        *state.app.dns.write().await = dns;
        let _ = crate::protocol::broadcast_tunnel_configuration(&state.app).await;
    }
    if let Some(hours) = applied_interval {
        state.app.auto_restart_interval_tx.send_replace(hours);
    }
    let _ = auth::refresh_credentials_report(&state).await;

    Json(serde_json::json!({
        "ok": true,
        "added_clients": added_clients,
        "settings_imported": do_settings
    }))
    .into_response()
}

pub(super) async fn reboot(State(state): State<WebState>) -> Response {
    if state.app.restart_pending.swap(true, Ordering::AcqRel) {
        return (
            StatusCode::ACCEPTED,
            [
                (header::RETRY_AFTER, "3"),
                (header::CACHE_CONTROL, "no-store"),
            ],
        )
            .into_response();
    }

    match crate::request_managed_restart(&state.app, "panel") {
        Ok(()) => (
            StatusCode::ACCEPTED,
            [
                (header::RETRY_AFTER, "3"),
                (header::CACHE_CONTROL, "no-store"),
            ],
        )
            .into_response(),
        Err(error) => {
            state.app.restart_pending.store(false, Ordering::Release);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                format!("restart failed: {error:#}"),
            )
                .into_response()
        }
    }
}

