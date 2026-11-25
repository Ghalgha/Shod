//! Client management handlers and client-related operations.

use super::*;

#[derive(Deserialize, Default)]
pub(super) struct ClientListQuery {
    #[serde(default)]
    group_id: Option<String>,
    #[serde(default)]
    active: Option<bool>,
    #[serde(default)]
    search: Option<String>,
}

pub(super) fn count_active_sessions(
    sessions: &dashmap::DashMap<u64, Arc<crate::protocol::Session>>,
    password: &str,
) -> usize {
    if password.is_empty() {
        return 0;
    }
    let mut count = 0;
    for entry in sessions.iter() {
        if entry.value().password == password {
            count += 1;
        }
    }
    count
}

pub(super) async fn clients_get(
    State(state): State<WebState>,
    headers: HeaderMap,
    Query(query): Query<ClientListQuery>,
) -> impl IntoResponse {
    let db = state.app.db.read().await;
    let host = resolve_client_host(&headers, &state).await;

    let mut list: Vec<ClientInfo> = db
        .passwords
        .iter()
        .map(|(password, entry)| {
            let client_peer_port = if entry.dtls_port == 0 {
                state.app.listen.port()
            } else {
                entry.dtls_port
            };
            ClientInfo {
                password: password.clone(),
                down: entry.down_bytes,
                up: entry.up_bytes,
                expires: entry.expires_at,
                active: !entry.is_deactivated && !is_expired(entry),
                active_sessions: count_active_sessions(&state.app.sessions, password),
                vk_hash: entry.vk_hash.clone(),
                ports: entry.ports.clone(),
                ip: if entry.ip.is_empty() {
                    crate::server_config::FALLBACK_CLIENT_IP.to_owned()
                } else {
                    entry.ip.clone()
                },
                name: entry.name.clone(),
                vk_hashes: entry.vk_hashes.clone(),
                vk_token: entry.vk_token_enabled,
                peer_port: client_peer_port,
                local_port: entry.local_port,
                group_id: entry.group_id.clone(),
                routing_rule_id: entry.routing_rule_id.clone(),
                link: make_entry_link(
                    &host,
                    client_peer_port,
                    password,
                    entry,
                    &db.vk_access_token,
                ),
                sub_uuid: entry.sub_uuid.clone(),
                workers: entry.workers,
            }
        })
        .collect();
    if !db.main_password.is_empty() {
        let mut session_up = 0;
        let mut session_down = 0;
        for s in state.app.sessions.iter() {
            let sess = s.value();
            if secret_eq(&sess.password, &db.main_password) {
                session_up += sess.up_bytes.load(Ordering::Relaxed) as i64;
                session_down += sess.down_bytes.load(Ordering::Relaxed) as i64;
            }
        }

        let main_peer_port = state.app.listen.port();
        list.push(ClientInfo {
            password: db.main_password.clone(),
            down: db.main_down_bytes + session_down,
            up: db.main_up_bytes + session_up,
            expires: 0,
            active: true,
            active_sessions: count_active_sessions(&state.app.sessions, &db.main_password),
            vk_hash: String::new(),
            ports: String::new(),
            ip: crate::model::owner_tunnel_ip(),
            name: "Владелец".to_string(),
            vk_hashes: if db.main_vk_hashes.is_empty() {
                db.vk_hashes.clone()
            } else {
                db.main_vk_hashes.clone()
            },
            vk_token: db.main_vk_token_enabled,
            peer_port: main_peer_port,
            local_port: 0,
            group_id: String::new(),
            routing_rule_id: db.main_routing_rule_id.clone(),
            link: make_main_link(&host, main_peer_port, &db),
            sub_uuid: db.main_sub_uuid.clone(),
            workers: db.main_workers,
        });
    }

    if let Some(ref gid) = query.group_id {
        let g = gid.trim();
        if g == "root" || g == "ungrouped" {
            list.retain(|c| c.group_id.is_empty());
        } else {
            list.retain(|c| c.group_id == g);
        }
    }
    if let Some(act) = query.active {
        list.retain(|c| c.active == act);
    }
    if let Some(ref s) = query.search {
        let q = s.trim().to_lowercase();
        if !q.is_empty() {
            list.retain(|c| {
                c.name.to_lowercase().contains(&q) || c.password.to_lowercase().contains(&q)
            });
        }
    }

    Json(list)
}

pub(super) async fn group_clients_get(
    State(state): State<WebState>,
    Path(id): Path<String>,
    headers: HeaderMap,
) -> Response {
    let db = state.app.db.read().await;
    if !db.groups.contains_key(&id) {
        return (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "error": "group not found" })),
        )
            .into_response();
    }
    let host = resolve_client_host(&headers, &state).await;
    let effective_vk_token = db
        .groups
        .get(&id)
        .and_then(|g| g.vk_token.as_deref())
        .unwrap_or(&db.vk_access_token);
    let list: Vec<ClientInfo> = db
        .passwords
        .iter()
        .filter(|(_, entry)| entry.group_id == id)
        .map(|(password, entry)| {
            let client_peer_port = if entry.dtls_port == 0 {
                state.app.listen.port()
            } else {
                entry.dtls_port
            };
            ClientInfo {
                password: password.clone(),
                down: entry.down_bytes,
                up: entry.up_bytes,
                expires: entry.expires_at,
                active: !entry.is_deactivated && !is_expired(entry),
                active_sessions: count_active_sessions(&state.app.sessions, password),
                vk_hash: entry.vk_hash.clone(),
                ports: entry.ports.clone(),
                ip: if entry.ip.is_empty() {
                    crate::server_config::FALLBACK_CLIENT_IP.to_owned()
                } else {
                    entry.ip.clone()
                },
                name: entry.name.clone(),
                vk_hashes: entry.vk_hashes.clone(),
                vk_token: entry.vk_token_enabled,
                peer_port: client_peer_port,
                local_port: entry.local_port,
                group_id: entry.group_id.clone(),
                routing_rule_id: entry.routing_rule_id.clone(),
                link: make_entry_link(&host, client_peer_port, password, entry, effective_vk_token),
                sub_uuid: entry.sub_uuid.clone(),
                workers: entry.workers,
            }
        })
        .collect();
    Json(list).into_response()
}

pub(super) fn normalize_client_vk_hashes(raw: &str) -> Result<String, &'static str> {
    let value = raw.trim();
    if value.is_empty() {
        return Ok(String::new());
    }
    if value.len() > 1024 {
        return Err("VK hashes are too long");
    }
    if value.chars().any(char::is_whitespace) {
        return Err("VK hashes must be comma-separated without spaces");
    }
    let hashes: Vec<_> = value.split(',').collect();
    if hashes.len() > 6 || hashes.iter().any(|hash| hash.len() < 16) {
        return Err("provide 1 to 6 valid VK hashes");
    }
    Ok(hashes.join(","))
}

pub(super) fn normalize_vk_access_token(raw: &str) -> Result<String, &'static str> {
    let token = raw.trim();
    if token.is_empty() {
        return Ok(String::new());
    }
    if token.len() > 4096 || token.chars().any(char::is_whitespace) {
        return Err("VK token is too long or contains whitespace");
    }
    Ok(token.to_owned())
}

pub(super) async fn clients_create(
    State(state): State<WebState>,
    headers: HeaderMap,
    Json(request): Json<CreateClientRequest>,
) -> Response {
    if let Err(message) = validate_client_name(&request.name) {
        return (StatusCode::BAD_REQUEST, message).into_response();
    }
    if request.days < 0 || request.days > 3650 {
        return (StatusCode::BAD_REQUEST, "days must be 0..3650").into_response();
    }
    let vk_hashes = match normalize_client_vk_hashes(&request.hash) {
        Ok(value) => value,
        Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
    };
    if request.vk_token && !vk_hashes.is_empty() {
        return (StatusCode::BAD_REQUEST, "choose VK hashes or VK token").into_response();
    }

    let default_peer_port = state.app.listen.port();
    let peer_port = request.peer_port.unwrap_or(default_peer_port);
    let local_port = request.local_port.unwrap_or(0);
    let ports = format!("{},0,{}", peer_port, local_port);
    let group_id = request.group_id.unwrap_or_default();
    let validated_workers = match validate_workers(request.workers) {
        Ok(w) => w,
        Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
    };

    let host = resolve_client_host(&headers, &state).await;

    let (password, expires, revision, link, sub_uuid) = {
        let mut db = state.app.db.write().await;
        if request.vk_token && db.vk_access_token.is_empty() {
            return (StatusCode::BAD_REQUEST, "configure a VK token first").into_response();
        }
        db.passwords.retain(|_, entry| !is_expired(entry));

        let client_name = if request.name.trim().is_empty() {
            model::generate_unique_client_name(model::existing_client_names_in_scope(
                &db, &group_id, None,
            ))
        } else {
            let trimmed = request.name.trim().to_string();
            if model::is_client_name_taken(
                model::existing_client_names_in_scope(&db, &group_id, None),
                &trimmed,
            ) {
                return (
                    StatusCode::BAD_REQUEST,
                    "Участник с таким именем уже существует",
                )
                    .into_response();
            }
            trimmed
        };

        let password = if let Some(ref custom_pw) = request.password {
            let pw = custom_pw.trim();
            if pw.is_empty() || pw.len() > 128 {
                return (
                    StatusCode::BAD_REQUEST,
                    "password must be 1..128 characters",
                )
                    .into_response();
            }
            if db.passwords.contains_key(pw) || pw == db.main_password {
                return (StatusCode::BAD_REQUEST, "password already exists").into_response();
            }
            pw.to_string()
        } else {
            let candidate = (0..64)
                .map(|_| random_password())
                .find(|c| !db.passwords.contains_key(c) && c != &db.main_password);
            let Some(p) = candidate else {
                return StatusCode::INTERNAL_SERVER_ERROR.into_response();
            };
            p
        };

        let expires = if request.days == 0 {
            0
        } else {
            now().saturating_add(request.days.saturating_mul(86400))
        };
        let sub_uuid = model::random_uuid_v4();
        let ip = model::get_next_ip(&db).unwrap_or_default();
        db.passwords.insert(
            password.clone(),
            PasswordEntry {
                ip,
                expires_at: expires,
                down_bytes: 0,
                up_bytes: 0,
                vk_hash: vk_hashes.clone(),
                ports,
                is_deactivated: false,
                name: client_name,
                vk_hashes: vk_hashes.clone(),
                vk_token_enabled: request.vk_token,
                dtls_port: peer_port,
                wg_port: 0,
                local_port,
                group_id: group_id.clone(),
                routing_rule_id: String::new(),
                sub_uuid: sub_uuid.clone(),
                workers: validated_workers,
            },
        );
        let link = make_client_link(
            &host,
            peer_port,
            &password,
            &vk_hashes,
            request.vk_token,
            &db.vk_access_token,
        );
        let revision = state.app.db_persistence.submit(db.clone());
        (password, expires, revision, link, sub_uuid)
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let reload_error = auth::refresh_credentials_report(&state).await.err();

    Json(serde_json::json!({
        "password": password,
        "expires": expires,
        "peer_port": peer_port,
        "local_port": local_port,
        "vk_hashes": vk_hashes,
        "vk_token": request.vk_token,
        "group_id": group_id,
        "workers": validated_workers.unwrap_or(0),
        "reload_error": reload_error,
        "link": link,
        "sub_uuid": sub_uuid,
    }))
    .into_response()
}

pub(super) async fn client_update(
    State(state): State<WebState>,
    Path(password): Path<String>,
    Json(request): Json<CreateClientRequest>,
) -> Response {
    if let Err(message) = validate_client_name(&request.name) {
        return (StatusCode::BAD_REQUEST, message).into_response();
    }
    if request.days < 0 || request.days > 3650 {
        return (StatusCode::BAD_REQUEST, "days must be 0..3650").into_response();
    }
    let vk_hashes = match normalize_client_vk_hashes(&request.hash) {
        Ok(value) => value,
        Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
    };
    if request.vk_token && !vk_hashes.is_empty() {
        return (StatusCode::BAD_REQUEST, "choose VK hashes or VK token").into_response();
    }
    let validated_workers = match validate_workers(request.workers) {
        Ok(w) => w,
        Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
    };

    let revision = {
        let mut db = state.app.db.write().await;
        if request.vk_token && db.vk_access_token.is_empty() {
            return (StatusCode::BAD_REQUEST, "configure a VK token first").into_response();
        }
        if secret_eq(&password, &db.main_password) {
            db.main_vk_hashes = vk_hashes;
            db.main_vk_token_enabled = request.vk_token;
            db.main_workers = validated_workers;
            state.app.db_persistence.submit(db.clone())
        } else {
            let current_group_id = match db.passwords.get(&password) {
                Some(e) => e.group_id.clone(),
                None => return StatusCode::NOT_FOUND.into_response(),
            };
            let target_group_id = request.group_id.as_deref().unwrap_or(&current_group_id);
            let scope_names =
                model::existing_client_names_in_scope(&db, target_group_id, Some(&password));
            let client_name = if request.name.trim().is_empty() {
                model::generate_unique_client_name(&scope_names)
            } else {
                let trimmed = request.name.trim().to_string();
                if model::is_client_name_taken(&scope_names, &trimmed) {
                    return (
                        StatusCode::BAD_REQUEST,
                        "Участник с таким именем уже существует",
                    )
                        .into_response();
                }
                trimmed
            };

            let entry = db.passwords.get_mut(&password).unwrap();

            let default_peer_port = state.app.listen.port();
            let fallback_port = if entry.dtls_port == 0 {
                default_peer_port
            } else {
                entry.dtls_port
            };
            let peer_port = request.peer_port.unwrap_or(fallback_port);
            let local_port = request.local_port.unwrap_or(0);

            entry.name = client_name;
            entry.expires_at = if request.days == 0 {
                0
            } else {
                now().saturating_add(request.days.saturating_mul(86400))
            };
            entry.vk_hash = vk_hashes.clone();
            entry.vk_hashes = vk_hashes;
            entry.vk_token_enabled = request.vk_token;
            entry.dtls_port = peer_port;
            entry.wg_port = 0;
            entry.local_port = local_port;
            entry.ports = format!("{},0,{}", peer_port, local_port);
            if let Some(ref g_id) = request.group_id {
                entry.group_id = g_id.clone();
            }
            entry.workers = validated_workers;
            state.app.db_persistence.submit(db.clone())
        }
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if let Err(message) = auth::refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-shod-reload-error", warning)], "saved").into_response();
    }
    StatusCode::OK.into_response()
}

pub(super) async fn client_toggle(
    State(state): State<WebState>,
    Path(password): Path<String>,
) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        let Some(entry) = db.passwords.get_mut(&password) else {
            return StatusCode::NOT_FOUND.into_response();
        };
        entry.is_deactivated = !entry.is_deactivated;
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }

    crate::protocol::drop_password_sessions(&state.app, &password);
    if let Err(message) = auth::refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-shod-reload-error", warning)], "saved").into_response();
    }

    StatusCode::OK.into_response()
}

pub(super) async fn client_unbind(
    State(state): State<WebState>,
    Path(password): Path<String>,
) -> Response {
    crate::protocol::drop_password_sessions(&state.app, &password);
    crate::protocol::forget_idle_password_epoch(&state.app, &password);
    StatusCode::OK.into_response()
}

pub(super) async fn client_delete(
    State(state): State<WebState>,
    Path(password): Path<String>,
) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        if db.passwords.remove(&password).is_none() {
            return StatusCode::NOT_FOUND.into_response();
        };
        state.app.derived_keys.remove(&password);
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }

    crate::protocol::drop_password_sessions(&state.app, &password);
    if let Err(message) = auth::refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-shod-reload-error", warning)], "saved").into_response();
    }

    StatusCode::OK.into_response()
}

pub(super) async fn client_get(
    State(state): State<WebState>,
    headers: HeaderMap,
    Path(password): Path<String>,
) -> Response {
    let db = state.app.db.read().await;
    let host = resolve_client_host(&headers, &state).await;

    if secret_eq(&password, &db.main_password) {
        let mut session_up = 0;
        let mut session_down = 0;
        let mut active_sessions = 0;
        for entry in state.app.sessions.iter() {
            let sess = entry.value();
            if secret_eq(&sess.password, &db.main_password) {
                active_sessions += 1;
                session_up += sess.up_bytes.load(Ordering::Relaxed) as i64;
                session_down += sess.down_bytes.load(Ordering::Relaxed) as i64;
            }
        }

        let main_peer_port = state.app.listen.port();
        let info = ClientInfo {
            password: db.main_password.clone(),
            down: db.main_down_bytes + session_down,
            up: db.main_up_bytes + session_up,
            expires: 0,
            active: true,
            active_sessions,
            vk_hash: String::new(),
            ports: String::new(),
            ip: crate::model::owner_tunnel_ip(),
            name: "Главный пароль".to_string(),
            vk_hashes: if db.main_vk_hashes.is_empty() {
                db.vk_hashes.clone()
            } else {
                db.main_vk_hashes.clone()
            },
            vk_token: db.main_vk_token_enabled,
            peer_port: main_peer_port,
            local_port: 0,
            group_id: String::new(),
            routing_rule_id: db.main_routing_rule_id.clone(),
            link: make_main_link(&host, main_peer_port, &db),
            sub_uuid: db.main_sub_uuid.clone(),
            workers: db.main_workers,
        };
        return Json(info).into_response();
    }

    if let Some(entry) = db.passwords.get(&password) {
        let active_sessions = count_active_sessions(&state.app.sessions, &password);
        let client_peer_port = if entry.dtls_port == 0 {
            state.app.listen.port()
        } else {
            entry.dtls_port
        };
        let info = ClientInfo {
            password: password.clone(),
            down: entry.down_bytes,
            up: entry.up_bytes,
            expires: entry.expires_at,
            active: !entry.is_deactivated && !is_expired(entry),
            active_sessions,
            vk_hash: entry.vk_hash.clone(),
            ports: entry.ports.clone(),
            ip: if entry.ip.is_empty() {
                crate::server_config::FALLBACK_CLIENT_IP.to_owned()
            } else {
                entry.ip.clone()
            },
            name: entry.name.clone(),
            vk_hashes: entry.vk_hashes.clone(),
            vk_token: entry.vk_token_enabled,
            peer_port: client_peer_port,
            local_port: entry.local_port,
            group_id: entry.group_id.clone(),
            routing_rule_id: entry.routing_rule_id.clone(),
            link: make_entry_link(
                &host,
                client_peer_port,
                &password,
                entry,
                &db.vk_access_token,
            ),
            sub_uuid: entry.sub_uuid.clone(),
            workers: entry.workers,
        };
        return Json(info).into_response();
    }

    (StatusCode::NOT_FOUND, "client not found").into_response()
}

pub(super) async fn client_reset_traffic(
    State(state): State<WebState>,
    Path(password): Path<String>,
) -> Response {
    let result = {
        let mut db = state.app.db.write().await;
        if secret_eq(&password, &db.main_password) {
            db.main_up_bytes = 0;
            db.main_down_bytes = 0;
            for s in state.app.sessions.iter() {
                if s.value().password == password {
                    s.value().up_bytes.store(0, Ordering::Relaxed);
                    s.value().down_bytes.store(0, Ordering::Relaxed);
                }
            }
            let revision = state.app.db_persistence.submit(db.clone());
            Some(revision)
        } else if let Some(entry) = db.passwords.get_mut(&password) {
            entry.up_bytes = 0;
            entry.down_bytes = 0;
            for s in state.app.sessions.iter() {
                if s.value().password == password {
                    s.value().up_bytes.store(0, Ordering::Relaxed);
                    s.value().down_bytes.store(0, Ordering::Relaxed);
                }
            }
            let revision = state.app.db_persistence.submit(db.clone());
            Some(revision)
        } else {
            None
        }
    };

    let Some(revision) = result else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };

    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    Json(serde_json::json!({ "ok": true })).into_response()
}

pub(super) async fn client_extend(
    State(state): State<WebState>,
    Path(password): Path<String>,
    Json(request): Json<ExtendClientRequest>,
) -> Response {
    if request.days <= 0 || request.days > 3650 {
        return (StatusCode::BAD_REQUEST, "days must be 1..3650").into_response();
    }
    let current = now();
    let result = {
        let mut db = state.app.db.write().await;
        if let Some(entry) = db.passwords.get_mut(&password) {
            let base = if entry.expires_at > current {
                entry.expires_at
            } else {
                current
            };
            let new_expires = base.saturating_add(request.days.saturating_mul(86400));
            entry.expires_at = new_expires;
            let revision = state.app.db_persistence.submit(db.clone());
            Some((new_expires, revision))
        } else {
            None
        }
    };

    let Some((expires, revision)) = result else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };

    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let _ = auth::refresh_credentials_report(&state).await;
    Json(serde_json::json!({ "ok": true, "expires": expires })).into_response()
}

#[derive(Serialize)]
pub(super) struct ClientLinkItem {
    name: String,
    password: String,
    link: String,
    expires: i64,
    peer_port: u16,
    group_id: String,
    #[serde(default)]
    sub_uuid: String,
}

pub(super) async fn api_links_get(
    State(state): State<WebState>,
    headers: HeaderMap,
) -> impl IntoResponse {
    let db = state.app.db.read().await;
    let host = resolve_client_host(&headers, &state).await;
    let mut list: Vec<ClientLinkItem> = db
        .passwords
        .iter()
        .map(|(password, entry)| {
            let client_peer_port = if entry.dtls_port == 0 {
                state.app.listen.port()
            } else {
                entry.dtls_port
            };
            ClientLinkItem {
                name: entry.name.clone(),
                password: password.clone(),
                link: make_entry_link(
                    &host,
                    client_peer_port,
                    password,
                    entry,
                    &db.vk_access_token,
                ),
                expires: entry.expires_at,
                peer_port: client_peer_port,
                group_id: entry.group_id.clone(),
                sub_uuid: entry.sub_uuid.clone(),
            }
        })
        .collect();
    if !db.main_password.is_empty() {
        let main_peer_port = state.app.listen.port();
        list.push(ClientLinkItem {
            name: "Владелец".to_owned(),
            password: db.main_password.clone(),
            link: make_main_link(&host, main_peer_port, &db),
            expires: 0,
            peer_port: main_peer_port,
            group_id: String::new(),
            sub_uuid: db.main_sub_uuid.clone(),
        });
    }
    Json(list)
}

#[derive(Deserialize)]
pub(super) struct BatchVkRequest {
    #[serde(default)]
    vk_token: Option<String>,
    #[serde(default)]
    vk_hashes: Option<String>,
    #[serde(default)]
    apply_to_all_clients: Option<bool>,
}

pub(super) async fn api_links_batch_vk(
    State(state): State<WebState>,
    Json(request): Json<BatchVkRequest>,
) -> Response {
    let token = match request.vk_token {
        Some(t) => match normalize_vk_access_token(&t) {
            Ok(v) => Some(v),
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        },
        None => None,
    };
    let hashes = match request.vk_hashes {
        Some(h) => match normalize_client_vk_hashes(&h) {
            Ok(v) => Some(v),
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        },
        None => None,
    };
    let apply_all = request.apply_to_all_clients.unwrap_or(true);
    let mut updated_count = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        if let Some(ref t) = token {
            db.vk_access_token = t.clone();
        }
        if let Some(ref h) = hashes {
            db.vk_hashes = h.clone();
        }
        if apply_all {
            if let Some(ref h) = hashes {
                db.main_vk_hashes = h.clone();
            }
            if let Some(ref t) = token {
                db.main_vk_token_enabled = !t.is_empty();
            }
            for entry in db.passwords.values_mut() {
                if let Some(ref h) = hashes {
                    entry.vk_hashes = h.clone();
                }
                if let Some(ref t) = token {
                    entry.vk_token_enabled = !t.is_empty();
                }
                updated_count += 1;
            }
        }
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let _ = auth::refresh_credentials_report(&state).await;
    Json(serde_json::json!({
        "ok": true,
        "updated_clients": updated_count
    }))
    .into_response()
}

#[derive(Deserialize)]
pub(super) struct ClientVkUpdateRequest {
    #[serde(default)]
    vk_token: Option<bool>,
    #[serde(default)]
    vk_hashes: Option<String>,
}

pub(super) async fn api_client_vk_update(
    State(state): State<WebState>,
    Path(password): Path<String>,
    Json(request): Json<ClientVkUpdateRequest>,
) -> Response {
    let hashes = match request.vk_hashes {
        Some(h) => match normalize_client_vk_hashes(&h) {
            Ok(v) => Some(v),
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        },
        None => None,
    };
    let revision = {
        let mut db = state.app.db.write().await;
        if secret_eq(&password, &db.main_password) {
            if let Some(h) = hashes {
                db.main_vk_hashes = h;
            }
            if let Some(token_enabled) = request.vk_token {
                db.main_vk_token_enabled = token_enabled;
            }
            state.app.db_persistence.submit(db.clone())
        } else {
            let Some(entry) = db.passwords.get_mut(&password) else {
                return (StatusCode::NOT_FOUND, "client not found").into_response();
            };
            if let Some(h) = hashes {
                entry.vk_hashes = h;
            }
            if let Some(token_enabled) = request.vk_token {
                entry.vk_token_enabled = token_enabled;
            }
            state.app.db_persistence.submit(db.clone())
        }
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let _ = auth::refresh_credentials_report(&state).await;
    Json(serde_json::json!({ "ok": true })).into_response()
}

pub(super) async fn client_sub_link_regenerate(
    State(state): State<WebState>,
    Path(password): Path<String>,
) -> Response {
    let (revision, new_sub) = {
        let mut db = state.app.db.write().await;
        if secret_eq(&password, &db.main_password) {
            db.main_sub_uuid = model::random_uuid_v4();
            let new_sub = db.main_sub_uuid.clone();
            let rev = state.app.db_persistence.submit(db.clone());
            (rev, new_sub)
        } else if let Some(entry) = db.passwords.get_mut(&password) {
            entry.sub_uuid = model::random_uuid_v4();
            let new_sub = entry.sub_uuid.clone();
            let rev = state.app.db_persistence.submit(db.clone());
            (rev, new_sub)
        } else {
            return StatusCode::NOT_FOUND.into_response();
        }
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true,
            "sub_uuid": new_sub
        })),
    )
        .into_response()
}

#[derive(Deserialize)]
pub(super) struct BatchToggleRequest {
    passwords: Vec<String>,
    active: bool,
}

pub(super) async fn api_clients_batch_toggle(
    State(state): State<WebState>,
    Json(req): Json<BatchToggleRequest>,
) -> Response {
    let mut updated = 0usize;
    let mut passwords_to_disconnect = Vec::new();
    let revision = {
        let mut db = state.app.db.write().await;
        for pwd in &req.passwords {
            if let Some(entry) = db.passwords.get_mut(pwd) {
                entry.is_deactivated = !req.active;
                updated += 1;
                if !req.active {
                    passwords_to_disconnect.push(pwd.clone());
                }
            }
        }
        if updated > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    for pwd in &passwords_to_disconnect {
        crate::protocol::drop_password_sessions(&state.app, pwd);
    }
    if updated > 0 {
        let _ = auth::refresh_credentials_report(&state).await;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "updated": updated })),
    )
        .into_response()
}

#[derive(Deserialize)]
pub(super) struct BatchExtendRequest {
    passwords: Vec<String>,
    days: i64,
}

pub(super) async fn api_clients_batch_extend(
    State(state): State<WebState>,
    Json(req): Json<BatchExtendRequest>,
) -> Response {
    if req.days <= 0 || req.days > 3650 {
        return (StatusCode::BAD_REQUEST, "days must be between 1 and 3650").into_response();
    }
    let add_secs = req.days.saturating_mul(86400);
    let current_time = now();
    let mut updated = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        for pwd in &req.passwords {
            if let Some(entry) = db.passwords.get_mut(pwd) {
                let base = if entry.expires_at > current_time {
                    entry.expires_at
                } else {
                    current_time
                };
                entry.expires_at = base.saturating_add(add_secs);
                updated += 1;
            }
        }
        if updated > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "updated": updated })),
    )
        .into_response()
}

#[derive(Deserialize)]
pub(super) struct BatchPasswordsRequest {
    passwords: Vec<String>,
}

pub(super) async fn api_clients_batch_reset_traffic(
    State(state): State<WebState>,
    Json(req): Json<BatchPasswordsRequest>,
) -> Response {
    let mut updated = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        for pwd in &req.passwords {
            if pwd == &db.main_password {
                db.main_down_bytes = 0;
                db.main_up_bytes = 0;
                updated += 1;
            } else if let Some(entry) = db.passwords.get_mut(pwd) {
                entry.down_bytes = 0;
                entry.up_bytes = 0;
                updated += 1;
            }
        }
        if updated > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    for pwd in &req.passwords {
        for s in state.app.sessions.iter() {
            if &s.password == pwd {
                s.down_bytes.store(0, Ordering::Relaxed);
                s.up_bytes.store(0, Ordering::Relaxed);
            }
        }
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "updated": updated })),
    )
        .into_response()
}

pub(super) async fn api_clients_batch_delete(
    State(state): State<WebState>,
    Json(req): Json<BatchPasswordsRequest>,
) -> Response {
    let mut deleted = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        for pwd in &req.passwords {
            if db.passwords.remove(pwd).is_some() {
                state.app.derived_keys.remove(pwd);
                deleted += 1;
            }
        }
        if deleted > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    for pwd in &req.passwords {
        crate::protocol::drop_password_sessions(&state.app, pwd);
    }
    if deleted > 0 {
        let _ = auth::refresh_credentials_report(&state).await;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "deleted": deleted })),
    )
        .into_response()
}

#[derive(Deserialize)]
pub(super) struct BatchGroupAssignRequest {
    passwords: Vec<String>,
    group_id: String,
}

pub(super) async fn api_clients_batch_group(
    State(state): State<WebState>,
    Json(req): Json<BatchGroupAssignRequest>,
) -> Response {
    let mut moved = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        let gid = req.group_id.trim();
        if !gid.is_empty() && !db.groups.contains_key(gid) {
            return (StatusCode::BAD_REQUEST, "target group not found").into_response();
        }
        for pwd in &req.passwords {
            if let Some(entry) = db.passwords.get_mut(pwd) {
                entry.group_id = gid.to_string();
                moved += 1;
            }
        }
        if moved > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "moved": moved })),
    )
        .into_response()
}
