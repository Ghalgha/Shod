use crate::model::secret_eq;
use crate::{
    App, lock_unpoison, log_event,
    model::{
        self, ClientGroup, PasswordEntry, RoutingRule, is_expired, now, random_password,
        random_token,
    },
    read_unpoison,
};
use axum::{
    Json, Router,
    body::Body,
    extract::{ConnectInfo, DefaultBodyLimit, Extension, Path, Query, State},
    http::{HeaderMap, Request, StatusCode, header},
    middleware::{self, Next},
    response::{Html, IntoResponse, Redirect, Response},
    routing::{get, post, put},
};
use base64::Engine as _;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    net::SocketAddr,
    sync::{
        Arc, LazyLock, Mutex,
        atomic::{AtomicBool, AtomicU64, Ordering},
    },
    time::Duration,
};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

mod auth;
mod clients;
mod groups;
mod links;
mod monitoring;
mod proxy;
mod routing;
mod security;
mod settings;
mod subscriptions;

use clients::{count_active_sessions, normalize_client_vk_hashes, normalize_vk_access_token};
use links::{make_client_link, make_entry_link, make_main_link};
use security::{
    DualAcceptor, StealthAbortController, fallback_reject, root_with_secret, security_headers,
    stealth_abort_response, web_slowloris_watchdog,
};

pub(crate) use settings::{
    deploy_dns_profile_or_yandex, dns_provider_id, dns_provider_is_known, provider_dns_by_id,
};

const MAX_LOGIN_FIELD_BYTES: usize = 256;

#[derive(Clone)]
struct WebState {
    app: Arc<App>,
}

#[derive(Deserialize)]
struct CreateClientRequest {
    name: String,
    days: i64,
    #[serde(default)]
    workers: Option<u32>,
    #[serde(default)]
    password: Option<String>,
    #[serde(default)]
    hash: String,
    #[serde(default)]
    vk_token: bool,
    #[serde(default, alias = "dtls_port")]
    peer_port: Option<u16>,
    #[serde(default)]
    local_port: Option<u16>,
    #[serde(default)]
    group_id: Option<String>,
}

fn validate_workers(workers: Option<u32>) -> Result<Option<u32>, &'static str> {
    match workers {
        None | Some(0) => Ok(None),
        Some(w) if (3..=100).contains(&w) => Ok(Some(w)),
        _ => Err("workers must be 0 (default) or between 3 and 100"),
    }
}

#[derive(Deserialize)]
struct ExtendClientRequest {
    days: i64,
}

const WEB_BODY_LIMIT_BYTES: usize = 16 * 1024;
const MAX_CLIENT_NAME_BYTES: usize = 128;
fn validate_text_field(
    value: &str,
    max_bytes: usize,
    message: &'static str,
) -> Result<(), &'static str> {
    if value.len() > max_bytes || value.chars().any(char::is_control) {
        return Err(message);
    }
    Ok(())
}

fn validate_client_name(name: &str) -> Result<(), &'static str> {
    validate_text_field(
        name,
        MAX_CLIENT_NAME_BYTES,
        "client name is too long or invalid",
    )
}

#[derive(Serialize)]
struct ClientInfo {
    password: String,
    down: i64,
    up: i64,
    expires: i64,
    active: bool,
    active_sessions: usize,
    vk_hash: String,
    ports: String,
    ip: String,
    name: String,
    vk_hashes: String,
    vk_token: bool,
    peer_port: u16,
    local_port: u16,
    #[serde(default)]
    group_id: String,
    #[serde(default)]
    routing_rule_id: String,
    #[serde(default)]
    link: String,
    #[serde(default)]
    sub_uuid: String,
    #[serde(default)]
    workers: Option<u32>,
}

const CAESAR_SHIFT: u8 = 47;
pub(crate) const MAX_WEB_SESSIONS: usize = 256;
pub(crate) const MAX_LOGIN_LIMITS: usize = 4_096;
fn render_root(state: &WebState, headers: &HeaderMap) -> Response {
    if auth::authorized(&state.app, headers) {
        let mut response = Html(PANEL_HTML).into_response();
        response.headers_mut().insert(
            header::CACHE_CONTROL,
            header::HeaderValue::from_static("no-store, no-cache, must-revalidate, private"),
        );
        return response;
    }
    let mut response = Html(LOGIN_HTML).into_response();
    response.headers_mut().insert(
        header::CACHE_CONTROL,
        header::HeaderValue::from_static("no-store, no-cache, must-revalidate, private"),
    );
    if !auth::cookie_tokens(headers).is_empty() {
        response.headers_mut().insert(
            header::SET_COOKIE,
            auth::expired_session_cookie().parse().unwrap(),
        );
    }
    response
}

async fn panel_root(State(state): State<WebState>, headers: HeaderMap) -> Response {
    render_root(&state, &headers)
}

async fn favicon() -> Response {
    pwa_response(PWA_ICON_SVG, "image/svg+xml", "public, max-age=604800")
}

fn pwa_response(
    body: impl IntoResponse,
    content_type: &'static str,
    cache_control: &'static str,
) -> Response {
    let mut response = body.into_response();
    let headers = response.headers_mut();
    headers.insert(
        header::CONTENT_TYPE,
        header::HeaderValue::from_static(content_type),
    );
    headers.insert(
        header::CACHE_CONTROL,
        header::HeaderValue::from_static(cache_control),
    );
    response
}

fn pwa_binary_response(
    body: Vec<u8>,
    content_type: &'static str,
    cache_control: &'static str,
) -> Response {
    let mut response = body.into_response();
    let headers = response.headers_mut();
    headers.insert(
        header::CONTENT_TYPE,
        header::HeaderValue::from_static(content_type),
    );
    headers.insert(
        header::CACHE_CONTROL,
        header::HeaderValue::from_static(cache_control),
    );
    response
}

async fn pwa_manifest(State(state): State<WebState>) -> Response {
    let secret = state.app.web_secret_path.trim().trim_matches('/');
    if secret.is_empty() {
        pwa_response(
            PWA_MANIFEST,
            "application/manifest+json; charset=utf-8",
            "no-cache",
        )
    } else {
        let prefix = format!("/{}", secret);
        let base = format!("{}/", prefix);
        let manifest = PWA_MANIFEST
            .replace("\"id\": \"/\"", &format!("\"id\": \"{}\"", base))
            .replace(
                "\"start_url\": \"/\"",
                &format!("\"start_url\": \"{}\"", base),
            )
            .replace("\"scope\": \"/\"", &format!("\"scope\": \"{}\"", base))
            .replace(
                "\"/pwa-icon-192.png\"",
                &format!("\"{}/pwa-icon-192.png\"", prefix),
            )
            .replace(
                "\"/pwa-icon-512.png\"",
                &format!("\"{}/pwa-icon-512.png\"", prefix),
            )
            .replace("\"/pwa-icon.svg\"", &format!("\"{}/pwa-icon.svg\"", prefix));
        pwa_response(
            manifest,
            "application/manifest+json; charset=utf-8",
            "no-cache",
        )
    }
}

async fn pwa_service_worker() -> Response {
    let mut response = pwa_response(
        PWA_SERVICE_WORKER,
        "application/javascript; charset=utf-8",
        "no-cache",
    );
    response.headers_mut().insert(
        header::HeaderName::from_static("service-worker-allowed"),
        header::HeaderValue::from_static("/"),
    );
    response
}

async fn pwa_icon() -> Response {
    pwa_response(PWA_ICON_SVG, "image/svg+xml", "public, max-age=604800")
}

async fn pwa_icon_192() -> Response {
    pwa_binary_response(
        PWA_ICON_192_PNG.clone(),
        "image/png",
        "public, max-age=604800",
    )
}

async fn pwa_icon_512() -> Response {
    pwa_binary_response(
        PWA_ICON_512_PNG.clone(),
        "image/png",
        "public, max-age=604800",
    )
}

async fn qrcode_js() -> Response {
    pwa_response(
        QRCODE_JS,
        "application/javascript; charset=utf-8",
        "public, max-age=604800, immutable",
    )
}

async fn wait_for_persistence(state: &WebState, revision: u64) -> Result<(), Response> {
    match tokio::time::timeout(
        Duration::from_secs(15),
        state.app.db_persistence.wait(revision),
    )
    .await
    {
        Ok(Ok(())) => Ok(()),
        Ok(Err(error)) => {
            eprintln!("[WEB] Failed to persist database mutation: {error:#}");
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "failed to persist change",
            )
                .into_response())
        }
        Err(_) => Err((
            StatusCode::GATEWAY_TIMEOUT,
            "database persistence is taking too long; refresh before retrying",
        )
            .into_response()),
    }
}

fn is_valid_hostname(name: &str) -> bool {
    if name.is_empty() || name.len() > 253 {
        return false;
    }
    name.split('.').all(|label| {
        !label.is_empty()
            && label.len() <= 63
            && label
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-')
            && !label.starts_with('-')
            && !label.ends_with('-')
    })
}

fn parse_host_header(host: &str) -> Option<(String, Option<String>)> {
    let host = host.trim();
    if host.is_empty() || host.len() > 255 {
        return None;
    }
    let (name, port) = if let Some(rest) = host.strip_prefix('[') {
        let (name, rest) = rest.split_once(']')?;
        let port = match rest.strip_prefix(':') {
            Some(p) => Some(p.to_string()),
            None if rest.is_empty() => None,
            None => return None,
        };
        (name.to_string(), port)
    } else if let Some((name, port)) = host.rsplit_once(':') {
        (name.to_string(), Some(port.to_string()))
    } else {
        (host.to_string(), None)
    };

    if let Some(port) = port.as_deref()
        && (port.is_empty()
            || port.len() > 5
            || !port.bytes().all(|b| b.is_ascii_digit())
            || !matches!(port.parse::<u16>(), Ok(value) if value > 0))
    {
        return None;
    }

    if name.parse::<std::net::IpAddr>().is_err() && !is_valid_hostname(&name) {
        return None;
    }
    Some((name, port))
}

async fn resolve_client_host(headers: &HeaderMap, state: &WebState) -> String {
    let host_header = headers
        .get(header::HOST)
        .and_then(|h| h.to_str().ok())
        .and_then(parse_host_header)
        .map(|(name, _)| name)
        .unwrap_or_default();

    let is_local_or_unspecified = host_header.is_empty()
        || host_header == "0.0.0.0"
        || host_header == "127.0.0.1"
        || host_header == "localhost"
        || host_header == "::1";

    if !is_local_or_unspecified {
        return host_header.to_string();
    }

    let public_ip = state.app.public_ip.read().await.clone();
    if !public_ip.is_empty() && public_ip != "0.0.0.0" && public_ip != "127.0.0.1" {
        return public_ip;
    }

    let listen_ip = state.app.listen.ip().to_string();
    if listen_ip != "0.0.0.0" && listen_ip != "127.0.0.1" {
        return listen_ip;
    }

    if !host_header.is_empty() {
        return host_header.to_string();
    }

    "0.0.0.0".to_string()
}

pub async fn run(
    app: Arc<App>,
    tls_config: axum_server::tls_rustls::RustlsConfig,
) -> anyhow::Result<()> {
    let state = WebState { app: app.clone() };
    let protected = Router::new()
        .route("/api/stats", get(monitoring::stats))
        .route("/api/memory/trim", post(monitoring::memory_trim))
        .route(
            "/api/settings",
            get(settings::settings_get).post(settings::settings_post),
        )
        .route(
            "/api/clients",
            get(clients::clients_get).post(clients::clients_create),
        )
        .route(
            "/api/clients/{password}",
            get(clients::client_get)
                .post(clients::client_update)
                .delete(clients::client_delete),
        )
        .route(
            "/api/clients/{password}/toggle",
            post(clients::client_toggle),
        )
        .route(
            "/api/clients/{password}/unbind",
            post(clients::client_unbind),
        )
        .route(
            "/api/clients/{password}/reset-traffic",
            post(clients::client_reset_traffic),
        )
        .route(
            "/api/clients/{password}/extend",
            post(clients::client_extend),
        )
        .route(
            "/api/clients/{password}/sub-link/regenerate",
            post(clients::client_sub_link_regenerate),
        )
        .route(
            "/api/groups",
            get(groups::groups_get).post(groups::groups_create),
        )
        .route(
            "/api/groups/{id}",
            put(groups::groups_update).delete(groups::groups_delete),
        )
        .route(
            "/api/groups/{id}/admin-link",
            post(groups::group_admin_link_post).delete(groups::group_admin_link_delete),
        )
        .route(
            "/api/groups/{id}/admin-link/regenerate",
            post(groups::group_admin_link_regenerate),
        )
        .route(
            "/api/groups/{id}/sub-link/regenerate",
            post(groups::group_sub_link_regenerate),
        )
        .route(
            "/api/routing-rules",
            get(routing::routing_rules_get).post(routing::routing_rules_create),
        )
        .route(
            "/api/routing-rules/{id}",
            put(routing::routing_rules_update).delete(routing::routing_rules_delete),
        )
        .route("/api/template-domains", get(routing::template_domains_get))
        .route("/api/logs", get(monitoring::logs_get))
        .route("/api/logs/toggle", post(monitoring::logs_toggle))
        .route("/api/logs/clear", post(monitoring::logs_clear))
        .route("/api/logs/download", get(monitoring::logs_download))
        .route(
            "/api/local-proxy",
            get(proxy::local_proxy_get).post(proxy::local_proxy_create),
        )
        .route(
            "/api/local-proxy/profiles/{id}",
            put(proxy::local_proxy_update).delete(proxy::local_proxy_delete),
        )
        .route(
            "/api/local-proxy/activate/{id}",
            post(proxy::local_proxy_activate),
        )
        .route(
            "/api/local-proxy/deactivate",
            post(proxy::local_proxy_deactivate),
        )
        .route("/api/logout", post(auth::logout))
        .route("/api/logout_all", post(auth::logout_all))
        .route("/api/links", get(clients::api_links_get))
        .route("/api/links/batch-vk", post(clients::api_links_batch_vk))
        .route(
            "/api/clients/{password}/vk",
            put(clients::api_client_vk_update),
        )
        .route("/api/settings/dns", post(settings::api_settings_dns))
        .route(
            "/api/settings/restart-interval",
            post(settings::api_settings_restart_interval),
        )
        .route(
            "/api/settings/master-password",
            post(settings::api_settings_master_password),
        )
        .route(
            "/api/clients/batch-toggle",
            post(clients::api_clients_batch_toggle),
        )
        .route(
            "/api/clients/batch-extend",
            post(clients::api_clients_batch_extend),
        )
        .route(
            "/api/clients/batch-reset-traffic",
            post(clients::api_clients_batch_reset_traffic),
        )
        .route(
            "/api/clients/batch-delete",
            post(clients::api_clients_batch_delete),
        )
        .route(
            "/api/clients/batch-group",
            post(clients::api_clients_batch_group),
        )
        .route("/api/groups/{id}/clients", get(clients::group_clients_get))
        .route("/api/diagnostics/dpi", get(monitoring::api_diagnostics_dpi))
        .route("/api/export", get(settings::api_export_get))
        .route("/api/import", post(settings::api_import_post))
        .route("/api/reboot", post(settings::reboot))
        .route_layer(middleware::from_fn_with_state(
            state.clone(),
            auth::auth_middleware,
        ));

    let panel_routes = Router::new()
        .route("/", get(panel_root))
        .route("/manifest.webmanifest", get(pwa_manifest))
        .route("/sw.js", get(pwa_service_worker))
        .route("/pwa-icon.svg", get(pwa_icon))
        .route("/pwa-icon-192.png", get(pwa_icon_192))
        .route("/pwa-icon-512.png", get(pwa_icon_512))
        .route("/qrcode.min.js", get(qrcode_js))
        .route("/favicon.ico", get(favicon))
        .route("/api/login", post(auth::login))
        .merge(protected);

    let mut router = Router::new();
    let secret = app.web_secret_path.trim().trim_matches('/');
    if secret.is_empty() {
        router = router.merge(panel_routes);
    } else {
        let prefix = format!("/{}", secret);
        let trailing_prefix = format!("{}/", prefix);
        router = router
            .route("/", get(root_with_secret))
            .route(&trailing_prefix, get(panel_root))
            .nest(&prefix, panel_routes);
        router = router
            .route("/manifest.webmanifest", get(pwa_manifest))
            .route("/sw.js", get(pwa_service_worker))
            .route("/pwa-icon.svg", get(pwa_icon))
            .route("/pwa-icon-192.png", get(pwa_icon_192))
            .route("/pwa-icon-512.png", get(pwa_icon_512))
            .route("/qrcode.min.js", get(qrcode_js))
            .route("/favicon.ico", get(favicon));
    }

    let router = router
        .route("/{uuid}/admin", get(groups::group_admin_page))
        .route("/{uuid}/admin/", get(groups::group_admin_page))
        .route("/{uuid}/api/data", get(groups::group_admin_data_get))
        .route(
            "/api/group-admin/{uuid}/data",
            get(groups::group_admin_data_get),
        )
        .route(
            "/{uuid}/api/settings",
            post(groups::group_admin_settings_post),
        )
        .route(
            "/api/group-admin/{uuid}/settings",
            post(groups::group_admin_settings_post),
        )
        .route(
            "/{uuid}/api/clients",
            post(groups::group_admin_client_create),
        )
        .route(
            "/api/group-admin/{uuid}/clients",
            post(groups::group_admin_client_create),
        )
        .route(
            "/{uuid}/api/clients/{password}",
            put(groups::group_admin_client_update).delete(groups::group_admin_client_delete),
        )
        .route(
            "/api/group-admin/{uuid}/clients/{password}",
            put(groups::group_admin_client_update).delete(groups::group_admin_client_delete),
        )
        .route(
            "/{uuid}/api/clients/{password}/toggle",
            post(groups::group_admin_client_toggle),
        )
        .route(
            "/api/group-admin/{uuid}/clients/{password}/toggle",
            post(groups::group_admin_client_toggle),
        )
        .route(
            "/{uuid}/api/clients/{password}/reset-traffic",
            post(groups::group_admin_client_reset_traffic),
        )
        .route(
            "/api/group-admin/{uuid}/clients/{password}/reset-traffic",
            post(groups::group_admin_client_reset_traffic),
        )
        .route(
            "/{uuid}/api/clients/{password}/extend",
            post(groups::group_admin_client_extend),
        )
        .route(
            "/api/group-admin/{uuid}/clients/{password}/extend",
            post(groups::group_admin_client_extend),
        )
        .route(
            "/{uuid}/api/clients/{password}/unbind",
            post(groups::group_admin_client_unbind),
        )
        .route(
            "/api/group-admin/{uuid}/clients/{password}/unbind",
            post(groups::group_admin_client_unbind),
        )
        .route("/{uuid}/sub", get(subscriptions::client_sub_page))
        .route("/{uuid}/sub/", get(subscriptions::client_sub_page))
        .route("/{uuid}/json", get(subscriptions::universal_sub_json))
        .route("/{uuid}/json/", get(subscriptions::universal_sub_json))
        .route("/{uuid}/sub.json", get(subscriptions::client_sub_json))
        .route("/api/sub/{uuid}", get(subscriptions::client_sub_json))
        .route("/api/sub/{uuid}/", get(subscriptions::client_sub_json))
        .route("/{uuid}/sub-group", get(subscriptions::group_sub_page))
        .route("/{uuid}/sub-group/", get(subscriptions::group_sub_page))
        .route("/{uuid}/sub-group.json", get(subscriptions::group_sub_json))
        .route("/api/sub-group/{uuid}", get(subscriptions::group_sub_json))
        .route("/api/sub-group/{uuid}/", get(subscriptions::group_sub_json))
        .fallback(fallback_reject)
        .layer(DefaultBodyLimit::max(WEB_BODY_LIMIT_BYTES))
        .layer(middleware::from_fn(security_headers))
        .with_state(state);

    tokio::spawn(auth::web_session_janitor(app.clone()));
    tokio::spawn(web_slowloris_watchdog());
    println!("[WEB] HTTP/HTTPS listening on 0.0.0.0:{}", app.web_port);
    let acceptor = DualAcceptor::new(tls_config, app.clone());
    axum_server::Server::bind(SocketAddr::from(([0, 0, 0, 0], app.web_port)))
        .acceptor(acceptor)
        .serve(router.into_make_service_with_connect_info::<SocketAddr>())
        .await?;
    Ok(())
}

const PWA_MANIFEST: &str = include_str!("web_panel/assets/manifest.webmanifest");

const PWA_ICON_SVG: &str = include_str!("web_panel/assets/pwa-icon.svg");

static PWA_ICON_192_PNG: LazyLock<Vec<u8>> = LazyLock::new(|| {
    base64::engine::general_purpose::STANDARD
        .decode(include_str!("web_panel/assets/pwa-icon-192.png.b64").trim())
        .unwrap_or_default()
});

static PWA_ICON_512_PNG: LazyLock<Vec<u8>> = LazyLock::new(|| {
    base64::engine::general_purpose::STANDARD
        .decode(include_str!("web_panel/assets/pwa-icon-512.png.b64").trim())
        .unwrap_or_default()
});

const PWA_SERVICE_WORKER: &str = include_str!("web_panel/assets/sw.js");

const QRCODE_JS: &str = include_str!("web_panel/assets/qrcode.min.js");

const LOGIN_HTML: &str = include_str!("web_panel/assets/login.html");

const CLIENT_SUB_HTML: &str = include_str!("web_panel/assets/client-subscription.html");

const GROUP_SUB_HTML: &str = include_str!("web_panel/assets/group-subscription.html");

const GROUP_ADMIN_HTML: &str = include_str!("web_panel/assets/group-admin.html");

const PANEL_HTML: &str = include_str!("web_panel/assets/panel.html");

#[cfg(test)]
mod tests {
    use super::{
        LOGIN_HTML, MAX_CLIENT_NAME_BYTES, PANEL_HTML, PWA_ICON_192_PNG, PWA_ICON_512_PNG,
        PWA_MANIFEST, PWA_SERVICE_WORKER,
        clients::{count_active_sessions, normalize_client_vk_hashes, normalize_vk_access_token},
        dns_provider_id,
        links::{decrypt_shod_link, encrypt_shod_link},
        make_client_link,
        monitoring::{process_memory, process_memory_rollup},
        parse_host_header,
        proxy::validate_proxy_profile_name,
        settings::{
            DNS_PROVIDERS, deploy_dns_profile_or_yandex, dns_provider_by_id, manual_dns,
            provider_dns,
        },
        validate_client_name, validate_workers,
    };

    #[test]
    fn host_header_accepts_only_ports_in_the_valid_range() {
        assert_eq!(
            parse_host_header("example.com:1"),
            Some(("example.com".to_string(), Some("1".to_string())))
        );
        assert_eq!(
            parse_host_header("example.com:65535"),
            Some(("example.com".to_string(), Some("65535".to_string())))
        );
        assert!(parse_host_header("example.com:0").is_none());
        assert!(parse_host_header("example.com:65536").is_none());
        assert!(parse_host_header("example.com:99999").is_none());
        assert!(parse_host_header("example.com:abc").is_none());
    }

    #[test]
    fn dns_provider_catalog_is_complete_and_has_exact_yandex_default() {
        assert_eq!(DNS_PROVIDERS.len(), 13);
        let yandex = dns_provider_by_id("yandex").unwrap();
        assert_eq!(provider_dns(yandex), "77.88.8.8,77.88.8.1");
        assert_eq!(dns_provider_id("1.1.1.1,1.0.0.1"), Some("cloudflare"));
        assert_eq!(
            dns_provider_id("45.90.28.181,45.90.30.181"),
            Some("nextdns")
        );
        assert_eq!(dns_provider_id("195.208.6.1,195.208.7.1"), Some("bizone"));
        assert_eq!(dns_provider_id("195.208.4.1,195.208.5.1"), Some("nsdi"));
        assert_eq!(dns_provider_id("10.0.0.1"), None);
    }

    #[test]
    fn legacy_deploy_dns_migration_normalizes_known_partial_profiles_or_uses_yandex() {
        assert_eq!(
            deploy_dns_profile_or_yandex("111.88.96.55,203.0.113.7"),
            "111.88.96.54,111.88.96.55"
        );
        assert_eq!(
            deploy_dns_profile_or_yandex("111.88.96.51,203.0.113.7"),
            "111.88.96.54,111.88.96.55"
        );
        assert_eq!(
            deploy_dns_profile_or_yandex("95.182.120.241,203.0.113.7"),
            "193.233.112.67,193.233.112.88"
        );
        assert_eq!(
            deploy_dns_profile_or_yandex("8.8.8.8,1.1.1.1"),
            "8.8.8.8,8.8.4.4"
        );
        assert_eq!(
            deploy_dns_profile_or_yandex("203.0.113.7,198.51.100.9"),
            "77.88.8.8,77.88.8.1"
        );
    }

    #[test]
    fn manual_dns_accepts_one_or_two_ipv4_addresses() {
        assert_eq!(manual_dns(Some("1.1.1.1"), None).unwrap(), "1.1.1.1");
        assert_eq!(
            manual_dns(Some(" 1.1.1.1 "), Some(" 1.0.0.1 ")).unwrap(),
            "1.1.1.1,1.0.0.1"
        );
    }

    #[test]
    fn manual_dns_rejects_empty_primary_and_invalid_ipv4() {
        assert!(manual_dns(None, None).is_err());
        assert!(manual_dns(Some(""), None).is_err());
        assert!(manual_dns(Some("1.1.1.256"), None).is_err());
        assert!(manual_dns(Some("1.1.1.1"), Some("not-an-ip")).is_err());
    }

    #[test]
    fn panel_uses_the_complete_dns_provider_list_without_network_checks() {
        assert!(PANEL_HTML.contains("DNS Адреса"));
        assert!(PANEL_HTML.contains("id=\"dns_provider\""));
        assert!(PANEL_HTML.contains("<option value=\"custom\">Ручной DNS</option>"));
        assert!(PANEL_HTML.contains("id=\"dns_manual\""));
        assert!(PANEL_HTML.contains("placeholder=\"ipv4,ipv4\""));
        assert!(PANEL_HTML.contains("Yandex DNS"));
        assert!(PANEL_HTML.contains("Rostelecom DNS"));
        assert!(PANEL_HTML.contains("NextDNS"));
        assert!(PANEL_HTML.contains("BI.ZONE DNS"));
        assert!(PANEL_HTML.contains("НСДИ DNS"));
        assert!(PANEL_HTML.contains("dns_provider: provider"));
        assert!(!PANEL_HTML.contains("/api/dns-providers"));
        assert!(!PANEL_HTML.contains("dns_provider_health"));
        assert!(!PANEL_HTML.contains("refreshDnsProviderPings"));
        assert!(PANEL_HTML.contains("dns_provider: 'custom'"));
        assert!(PANEL_HTML.contains("syncDnsControls"));
    }

    #[test]
    fn persisted_text_fields_have_byte_and_control_character_limits() {
        assert!(validate_client_name("normal client").is_ok());
        assert!(validate_client_name(&"x".repeat(MAX_CLIENT_NAME_BYTES)).is_ok());
        assert!(validate_client_name(&"x".repeat(MAX_CLIENT_NAME_BYTES + 1)).is_err());
        assert!(validate_client_name("bad\nname").is_err());
        assert!(validate_proxy_profile_name(&"x".repeat(64)).is_ok());
        assert!(validate_proxy_profile_name(&"x".repeat(65)).is_err());
    }

    #[test]
    fn client_vk_hashes_accept_empty_or_one_to_six_values() {
        assert_eq!(normalize_client_vk_hashes("").unwrap(), "");
        for count in 1..=6 {
            let hashes = (1..=count)
                .map(|index| format!("abcdefghijklmnop{index}"))
                .collect::<Vec<_>>()
                .join(",");
            assert_eq!(normalize_client_vk_hashes(&hashes).unwrap(), hashes);
        }
    }

    #[test]
    fn client_vk_hashes_reject_spaces_short_values_and_more_than_six() {
        assert!(normalize_client_vk_hashes("abcdefghijklmnop, abcdefghijklmnop").is_err());
        assert!(normalize_client_vk_hashes("short").is_err());
        let seven = (1..=7)
            .map(|index| format!("abcdefghijklmnop{index}"))
            .collect::<Vec<_>>()
            .join(",");
        assert!(normalize_client_vk_hashes(&seven).is_err());
    }

    #[test]
    fn vk_token_accepts_one_compact_value_or_empty() {
        assert_eq!(
            normalize_vk_access_token("  vk1.token-_%2B  ").unwrap(),
            "vk1.token-_%2B"
        );
        assert_eq!(normalize_vk_access_token(" ").unwrap(), "");
        assert!(normalize_vk_access_token("token with space").is_err());
        assert!(normalize_vk_access_token(&"x".repeat(4097)).is_err());
    }

    #[test]
    fn mobile_panel_keeps_compact_labels_and_consistent_memory_value() {
        assert!(PANEL_HTML.contains("Использование памяти"));
        assert!(PANEL_HTML.contains("#ram_val { white-space: nowrap;"));
        assert!(PANEL_HTML.contains("id=\"ram_detail\""));
        assert!(PANEL_HTML.contains("x.ram_used"));
        assert!(PANEL_HTML.contains("text-size-adjust: none"));
        assert!(PANEL_HTML.contains(".toggle-row label"));
        assert!(PANEL_HTML.contains("white-space: nowrap"));
    }

    #[test]
    fn panel_displays_compact_udp_traffic_counters() {
        assert!(PANEL_HTML.contains("id=\"traffic_ingress\""));
        assert!(PANEL_HTML.contains("x.udp_rx_packets"));
        assert!(PANEL_HTML.contains("x.udp_tx_packets"));
        assert!(PANEL_HTML.contains("const compactCounter = input =>"));
        assert!(
            PANEL_HTML.contains("const units = [\"\", \"K\", \"M\", \"G\", \"T\", \"P\", \"E\"]")
        );
        assert!(PANEL_HTML.contains("#traffic_ingress { white-space: nowrap;"));
        assert!(PANEL_HTML.contains(
            "RX ${compactCounter(x.udp_rx_packets)} · TX ${compactCounter(x.udp_tx_packets)}"
        ));
        assert!(!PANEL_HTML.contains("UDP RX ${Number(x.udp_rx_packets) || 0}"));
    }

    #[test]
    fn pwa_uses_standalone_shell_without_caching_authenticated_panel_data() {
        assert!(PWA_MANIFEST.contains("\"display\": \"standalone\""));
        assert!(PWA_MANIFEST.contains("\"start_url\": \"/\""));
        assert!(PWA_MANIFEST.contains("\"sizes\": \"192x192\""));
        assert!(PWA_MANIFEST.contains("pwa-icon-192.png"));
        assert!(PWA_MANIFEST.contains("pwa-icon-512.png"));
        assert!(LOGIN_HTML.contains("manifest.webmanifest"));
        assert!(PANEL_HTML.contains("manifest.webmanifest"));
        assert!(LOGIN_HTML.contains("pwa-icon-192.png"));
        assert!(PANEL_HTML.contains("pwa-icon-192.png"));
        assert!(PWA_SERVICE_WORKER.contains("url.pathname.startsWith('/api/')"));
        assert!(PWA_SERVICE_WORKER.contains("SHELL_ASSETS"));
        assert!(!PWA_SERVICE_WORKER.contains("OFFLINE_PAGE"));
        assert!(!PWA_SERVICE_WORKER.contains("request.mode === 'navigate'"));
    }

    #[test]
    fn participant_cards_contain_ip_info() {
        assert!(PANEL_HTML.contains("IP:"));
    }

    #[test]
    fn pwa_raster_icon_fallbacks_are_valid_pngs() {
        const PNG_SIGNATURE: &[u8] = b"\x89PNG\r\n\x1a\n";
        assert!(PWA_ICON_192_PNG.starts_with(PNG_SIGNATURE));
        assert!(PWA_ICON_512_PNG.starts_with(PNG_SIGNATURE));
        assert!(PWA_ICON_512_PNG.len() > PWA_ICON_192_PNG.len());
    }

    #[test]
    fn process_memory_reads_linux_status_fields() {
        let memory = process_memory(
            "VmHWM:\t850000 kB\nVmRSS:\t400000 kB\nRssAnon:\t350000 kB\nRssFile:\t45000 kB\nRssShmem:\t5000 kB\nVmSwap:\t12000 kB\n",
        );
        assert_eq!(memory.rss, 400000);
        assert_eq!(memory.peak, 850000);
        assert_eq!(memory.anonymous, 350000);
        assert_eq!(memory.file, 45000);
        assert_eq!(memory.shared, 5000);
        assert_eq!(memory.swap, 12000);
    }

    #[test]
    fn process_memory_rollup_reads_attribution_fields() {
        let memory = process_memory_rollup(
            "Pss:\t700 kB\nPss_Anon:\t650 kB\nPss_File:\t40 kB\nPss_Shmem:\t10 kB\nAnonymous:\t680 kB\nShared_Clean:\t12 kB\nShared_Dirty:\t3 kB\nPrivate_Clean:\t20 kB\nPrivate_Dirty:\t640 kB\nSwapPss:\t7 kB\n",
        );
        assert_eq!(memory.pss, 700);
        assert_eq!(memory.pss_anon, 650);
        assert_eq!(memory.anonymous, 680);
        assert_eq!(memory.file, 40);
        assert_eq!(memory.shmem, 10);
        assert_eq!(memory.private, 660);
        assert_eq!(memory.shared, 15);
        assert_eq!(memory.swap_pss, 7);
    }

    #[test]
    fn shod_link_encryption_and_decryption() {
        let payload = "connect?v=2&host=1.2.3.4&peer=46000&password=testpass";
        let encrypted1 = encrypt_shod_link(payload);
        let encrypted2 = encrypt_shod_link(payload);
        assert!(encrypted1.starts_with("shod://"));
        assert_eq!(
            encrypted1, encrypted2,
            "link encryption must be deterministic for identical payload"
        );
        let decrypted = decrypt_shod_link(&encrypted1).expect("decryption should succeed");
        assert_eq!(decrypted, payload);
    }

    #[test]
    fn shod_link_legacy_plaintext_rejected() {
        use base64::Engine;
        let payload = "connect?v=2&host=1.2.3.4&peer=46000&password=legacy";
        let legacy_b64 =
            base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(payload.as_bytes());
        let legacy_link = format!("shod://{legacy_b64}");
        assert!(decrypt_shod_link(&legacy_link).is_none());
    }

    #[test]
    fn make_client_link_contains_password_and_aes_roundtrip() {
        let link = make_client_link("1.2.3.4", 46000, "abc1234567890123", "", false, "");
        assert!(link.starts_with("shod://"));
        let decrypted = decrypt_shod_link(&link).expect("must decrypt");
        assert!(decrypted.contains("password=abc1234567890123"));
        assert!(!decrypted.contains("client="));
        assert!(!decrypted.contains("&workers="));
    }

    #[test]
    fn owner_link_contains_owner_password_without_workers() {
        let link = make_client_link("1.2.3.4", 46000, "ownerpass1234567", "", false, "");
        let decrypted = decrypt_shod_link(&link).expect("must decrypt");
        assert!(decrypted.contains("password=ownerpass1234567"));
        assert!(!decrypted.contains("client="));
        assert!(!decrypted.contains("&workers="));
    }

    #[test]
    fn count_active_sessions_isolates_passwords() {
        use crate::protocol::Session;
        use dashmap::DashMap;
        use std::net::SocketAddr;
        use std::sync::Arc;
        use std::sync::atomic::AtomicBool;

        let sessions = DashMap::new();
        let debug_flag = Arc::new(AtomicBool::new(false));
        let addr: SocketAddr = "127.0.0.1:46001".parse().unwrap();

        let s1 = Session::new(
            1,
            addr,
            "pass_a",
            1,
            "salt1",
            false,
            debug_flag.clone(),
            100,
        );
        sessions.insert(1, s1);

        assert_eq!(count_active_sessions(&sessions, "pass_a"), 1);
        assert_eq!(count_active_sessions(&sessions, "pass_b"), 0);

        let s_owner = Session::new(2, addr, "owner_pass", 2, "salt2", false, debug_flag, 100);
        sessions.insert(2, s_owner);

        assert_eq!(count_active_sessions(&sessions, "owner_pass"), 1);
        assert_eq!(count_active_sessions(&sessions, "pass_a"), 1);
        assert_eq!(count_active_sessions(&sessions, "pass_b"), 0);
    }

    #[test]
    fn workers_validation() {
        assert_eq!(validate_workers(None), Ok(None));
        assert_eq!(validate_workers(Some(0)), Ok(None));
        assert_eq!(validate_workers(Some(3)), Ok(Some(3)));
        assert_eq!(validate_workers(Some(36)), Ok(Some(36)));
        assert_eq!(validate_workers(Some(99)), Ok(Some(99)));
        assert_eq!(validate_workers(Some(100)), Ok(Some(100)));
        assert!(validate_workers(Some(1)).is_err());
        assert!(validate_workers(Some(2)).is_err());
        assert!(validate_workers(Some(101)).is_err());
    }
}
