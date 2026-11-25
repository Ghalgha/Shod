//! Group CRUD handlers.

use super::*;

#[derive(Serialize)]
struct GroupInfo {
    id: String,
    name: String,
    routing_rule_id: String,
    admin_uuid: String,
    sub_uuid: String,
    member_count: usize,
    up: i64,
    down: i64,
    active_sessions: usize,
}

pub(super) async fn groups_get(State(state): State<WebState>) -> Response {
    let db = state.app.db.read().await;
    let mut list = Vec::new();
    for group in db.groups.values() {
        let mut member_count = 0;
        let mut up = 0i64;
        let mut down = 0i64;
        let mut active_sessions = 0;
        for (_pass, p_entry) in &db.passwords {
            if p_entry.group_id == group.id {
                member_count += 1;
                up += p_entry.up_bytes;
                down += p_entry.down_bytes;
                active_sessions += count_active_sessions(&state.app.sessions, _pass);
            }
        }
        list.push(GroupInfo {
            id: group.id.clone(),
            name: group.name.clone(),
            routing_rule_id: group.routing_rule_id.clone(),
            admin_uuid: group.admin_uuid.clone(),
            sub_uuid: group.sub_uuid.clone(),
            member_count,
            up,
            down,
            active_sessions,
        });
    }
    Json(list).into_response()
}

#[derive(Deserialize)]
pub(super) struct CreateGroupRequest {
    name: String,
}

pub(super) async fn groups_create(
    State(state): State<WebState>,
    Json(request): Json<CreateGroupRequest>,
) -> Response {
    let name = request.name.trim();
    if name.is_empty() || name.len() > 64 {
        return (StatusCode::BAD_REQUEST, "invalid group name").into_response();
    }
    let id = format!("grp_{}", random_token(8));
    let group = ClientGroup {
        id: id.clone(),
        name: name.to_string(),
        routing_rule_id: String::new(),
        admin_uuid: String::new(),
        sub_uuid: model::random_uuid_v4(),
        workers: None,
        vk_token: None,
        vk_hashes: None,
    };
    let revision = {
        let mut db = state.app.db.write().await;
        db.groups.insert(id, group.clone());
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    Json(group).into_response()
}

#[derive(Deserialize)]
pub(super) struct UpdateGroupRequest {
    name: Option<String>,
    routing_rule_id: Option<String>,
}

pub(super) async fn groups_update(
    State(state): State<WebState>,
    Path(id): Path<String>,
    Json(request): Json<UpdateGroupRequest>,
) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        let Some(group) = db.groups.get_mut(&id) else {
            return StatusCode::NOT_FOUND.into_response();
        };
        if let Some(name) = request.name {
            let n = name.trim();
            if n.is_empty() || n.len() > 64 {
                return (StatusCode::BAD_REQUEST, "invalid group name").into_response();
            }
            group.name = n.to_string();
        }
        if let Some(r_id) = request.routing_rule_id {
            group.routing_rule_id = r_id;
        }
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let _ = crate::protocol::broadcast_tunnel_configuration(&state.app).await;
    StatusCode::OK.into_response()
}

pub(super) async fn groups_delete(
    State(state): State<WebState>,
    Path(id): Path<String>,
) -> Response {
    let (revision, deleted_passwords) = {
        let mut db = state.app.db.write().await;
        if db.groups.remove(&id).is_none() {
            return StatusCode::NOT_FOUND.into_response();
        }
        let mut deleted = Vec::new();
        db.passwords.retain(|pwd, entry| {
            if entry.group_id == id {
                deleted.push(pwd.clone());
                false
            } else {
                true
            }
        });
        for pwd in &deleted {
            state.app.derived_keys.remove(pwd);
        }
        let rev = state.app.db_persistence.submit(db.clone());
        (rev, deleted)
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    for pwd in &deleted_passwords {
        crate::protocol::drop_password_sessions(&state.app, pwd);
    }
    if let Err(message) = auth::refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-shod-reload-error", warning)], "saved").into_response();
    }
    StatusCode::OK.into_response()
}

pub(super) async fn group_admin_link_post(
    State(state): State<WebState>,
    Path(id): Path<String>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db.groups.get_mut(&id) else {
        return (StatusCode::NOT_FOUND, "group not found").into_response();
    };
    if group.admin_uuid.is_empty() {
        group.admin_uuid = model::random_uuid_v4();
    }
    let admin_uuid = group.admin_uuid.clone();
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true,
            "admin_uuid": admin_uuid
        })),
    )
        .into_response()
}

pub(super) async fn group_admin_link_regenerate(
    State(state): State<WebState>,
    Path(id): Path<String>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db.groups.get_mut(&id) else {
        return (StatusCode::NOT_FOUND, "group not found").into_response();
    };
    group.admin_uuid = model::random_uuid_v4();
    let admin_uuid = group.admin_uuid.clone();
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true,
            "admin_uuid": admin_uuid
        })),
    )
        .into_response()
}

pub(super) async fn group_admin_link_delete(
    State(state): State<WebState>,
    Path(id): Path<String>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db.groups.get_mut(&id) else {
        return (StatusCode::NOT_FOUND, "group not found").into_response();
    };
    group.admin_uuid.clear();
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true
        })),
    )
        .into_response()
}

pub(super) async fn group_admin_page(
    State(state): State<WebState>,
    Path(admin_uuid): Path<String>,
    token: Option<Extension<Arc<StealthAbortController>>>,
) -> Response {
    let db = state.app.db.read().await;
    let exists = db
        .groups
        .values()
        .any(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid);
    if !exists {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    }
    Html(GROUP_ADMIN_HTML).into_response()
}

pub(super) async fn group_admin_data_get(
    State(state): State<WebState>,
    Path(admin_uuid): Path<String>,
) -> Response {
    let db = state.app.db.read().await;
    let Some(group) = db
        .groups
        .values()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
    else {
        return stealth_abort_response();
    };
    let server_ip = state.app.public_ip.read().await.clone();
    let host = if server_ip.is_empty() {
        "0.0.0.0".to_string()
    } else {
        server_ip.clone()
    };
    let peer_port = state.app.listen.port();
    let effective_vk_token = group.vk_token.as_deref().unwrap_or(&db.vk_access_token);
    let effective_vk_hashes = group.vk_hashes.as_deref().unwrap_or(&db.vk_hashes);
    let mut clients = Vec::new();
    for (_pass, p_entry) in &db.passwords {
        if p_entry.group_id == group.id {
            let link = make_entry_link(&host, peer_port, _pass, p_entry, effective_vk_token);
            clients.push(serde_json::json!({
                "password": _pass,
                "name": p_entry.name,
                "expires": p_entry.expires_at,
                "up": p_entry.up_bytes,
                "down": p_entry.down_bytes,
                "active": !p_entry.is_deactivated && !model::is_expired(p_entry),
                "is_deactivated": p_entry.is_deactivated,
                "active_sessions": count_active_sessions(&state.app.sessions, _pass),
                "ip": if p_entry.ip.is_empty() { crate::server_config::FALLBACK_CLIENT_IP.to_owned() } else { p_entry.ip.clone() },
                "device_ip": if p_entry.ip.is_empty() { crate::server_config::FALLBACK_CLIENT_IP.to_owned() } else { p_entry.ip.clone() },
                "group_id": p_entry.group_id,
                "peer_port": p_entry.wg_port,
                "vk_hashes": p_entry.vk_hashes,
                "vk_token": p_entry.vk_token_enabled,
                "sub_uuid": p_entry.sub_uuid,
                "workers": p_entry.workers.unwrap_or(0),
                "link": link,
            }));
        }
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true,
            "group": {
                "id": group.id,
                "name": group.name,
                "workers": group.workers.unwrap_or(0),
                "vk_token": effective_vk_token,
                "vk_hashes": effective_vk_hashes,
            },
            "clients": clients,
            "server_ip": server_ip,
            "peer_port": peer_port
        })),
    )
        .into_response()
}

#[derive(Deserialize)]
pub(super) struct GroupAdminSettingsRequest {
    #[serde(default)]
    vk_token: Option<String>,
    #[serde(default)]
    vk_hashes: Option<String>,
    #[serde(default)]
    workers: Option<u32>,
}

pub(super) async fn group_admin_settings_post(
    State(state): State<WebState>,
    Path(admin_uuid): Path<String>,
    Json(payload): Json<GroupAdminSettingsRequest>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db
        .groups
        .values_mut()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
    else {
        return stealth_abort_response();
    };
    if let Some(token) = payload.vk_token {
        let normalized = match normalize_vk_access_token(&token) {
            Ok(t) => t,
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        };
        group.vk_token = Some(normalized);
    }
    if let Some(hashes) = payload.vk_hashes {
        let normalized = match normalize_client_vk_hashes(&hashes) {
            Ok(h) => h,
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        };
        group.vk_hashes = Some(normalized);
    }
    if let Some(w) = payload.workers {
        match validate_workers(Some(w)) {
            Ok(validated) => group.workers = validated,
            Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
        }
    }
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    let _ = auth::refresh_credentials_report(&state).await;
    (StatusCode::OK, Json(serde_json::json!({ "ok": true }))).into_response()
}

#[derive(Deserialize)]
pub(super) struct GroupAdminCreateClientRequest {
    #[serde(default)]
    name: String,
    #[serde(default)]
    days: i64,
    #[serde(default)]
    workers: Option<u32>,
}

pub(super) async fn group_admin_client_create(
    State(state): State<WebState>,
    Path(admin_uuid): Path<String>,
    Json(payload): Json<GroupAdminCreateClientRequest>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group_id) = db
        .groups
        .values()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
        .map(|g| g.id.clone())
    else {
        return stealth_abort_response();
    };
    let client_name = if payload.name.trim().is_empty() {
        model::generate_unique_client_name(model::existing_client_names_in_scope(
            &db, &group_id, None,
        ))
    } else {
        let trimmed = payload.name.trim().to_string();
        if let Err(msg) = validate_client_name(&trimmed) {
            return (StatusCode::BAD_REQUEST, msg).into_response();
        }
        if model::is_client_name_taken(
            model::existing_client_names_in_scope(&db, &group_id, None),
            &trimmed,
        ) {
            return (
                StatusCode::BAD_REQUEST,
                "Участник с таким именем уже существует в этой группе",
            )
                .into_response();
        }
        trimmed
    };
    let validated_workers = match validate_workers(payload.workers) {
        Ok(w) => w,
        Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
    };
    let mut pass = model::random_password();
    while db.passwords.contains_key(&pass) {
        pass = model::random_password();
    }
    let now_ts = model::now();
    let expires_at = if payload.days > 0 {
        now_ts.saturating_add(payload.days.saturating_mul(86400))
    } else {
        0
    };
    let sub_uuid = model::random_uuid_v4();
    let ip = model::get_next_ip(&db).unwrap_or_default();
    let entry = model::PasswordEntry {
        ip,
        expires_at,
        down_bytes: 0,
        up_bytes: 0,
        vk_hash: String::new(),
        ports: String::new(),
        is_deactivated: false,
        name: client_name,
        vk_hashes: String::new(),
        vk_token_enabled: false,
        dtls_port: 0,
        wg_port: 0,
        local_port: 0,
        group_id,
        routing_rule_id: String::new(),
        sub_uuid: sub_uuid.clone(),
        workers: validated_workers,
    };
    db.passwords.insert(pass.clone(), entry);
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    if let Err(message) = auth::refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (
            StatusCode::OK,
            [("x-shod-reload-error", warning)],
            Json(serde_json::json!({
                "ok": true,
                "password": pass,
                "sub_uuid": sub_uuid
            })),
        )
            .into_response();
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true,
            "password": pass,
            "sub_uuid": sub_uuid
        })),
    )
        .into_response()
}

pub(super) async fn group_admin_client_toggle(
    State(state): State<WebState>,
    Path((admin_uuid, password)): Path<(String, String)>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db
        .groups
        .values()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
    else {
        return stealth_abort_response();
    };
    let group_id = group.id.clone();
    let Some(entry) = db.passwords.get_mut(&password) else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };
    if entry.group_id != group_id {
        return (StatusCode::FORBIDDEN, "forbidden").into_response();
    }
    entry.is_deactivated = !entry.is_deactivated;
    let is_deactivated = entry.is_deactivated;
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    if is_deactivated {
        crate::protocol::drop_password_sessions(&state.app, &password);
    }
    let _ = auth::refresh_credentials_report(&state).await;
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true,
            "is_deactivated": is_deactivated
        })),
    )
        .into_response()
}

pub(super) async fn group_admin_client_reset_traffic(
    State(state): State<WebState>,
    Path((admin_uuid, password)): Path<(String, String)>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db
        .groups
        .values()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
    else {
        return stealth_abort_response();
    };
    let group_id = group.id.clone();
    let Some(entry) = db.passwords.get_mut(&password) else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };
    if entry.group_id != group_id {
        return (StatusCode::FORBIDDEN, "forbidden").into_response();
    }
    entry.up_bytes = 0;
    entry.down_bytes = 0;
    for s in state.app.sessions.iter() {
        if s.value().password == password {
            s.value().up_bytes.store(0, Ordering::Relaxed);
            s.value().down_bytes.store(0, Ordering::Relaxed);
        }
    }
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    (StatusCode::OK, Json(serde_json::json!({ "ok": true }))).into_response()
}

pub(super) async fn group_admin_client_extend(
    State(state): State<WebState>,
    Path((admin_uuid, password)): Path<(String, String)>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db
        .groups
        .values()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
    else {
        return stealth_abort_response();
    };
    let group_id = group.id.clone();
    let Some(entry) = db.passwords.get_mut(&password) else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };
    if entry.group_id != group_id {
        return (StatusCode::FORBIDDEN, "forbidden").into_response();
    }
    let now_ts = model::now();
    let base = if entry.expires_at > now_ts {
        entry.expires_at
    } else {
        now_ts
    };
    entry.expires_at = base.saturating_add(30 * 86400);
    let expires = entry.expires_at;
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    let _ = auth::refresh_credentials_report(&state).await;
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "expires": expires })),
    )
        .into_response()
}

pub(super) async fn group_admin_client_unbind(
    State(state): State<WebState>,
    Path((admin_uuid, password)): Path<(String, String)>,
) -> Response {
    let db = state.app.db.read().await;
    let Some(group) = db
        .groups
        .values()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
    else {
        return stealth_abort_response();
    };
    let group_id = group.id.clone();
    let Some(entry) = db.passwords.get(&password) else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };
    if entry.group_id != group_id {
        return (StatusCode::FORBIDDEN, "forbidden").into_response();
    }
    drop(db);
    crate::protocol::drop_password_sessions(&state.app, &password);
    crate::protocol::forget_idle_password_epoch(&state.app, &password);
    let _ = auth::refresh_credentials_report(&state).await;
    (StatusCode::OK, Json(serde_json::json!({ "ok": true }))).into_response()
}

#[derive(Deserialize)]
pub(super) struct GroupAdminUpdateClientRequest {
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    days: Option<i64>,
    #[serde(default)]
    workers: Option<u32>,
    #[serde(default)]
    hash: Option<String>,
    #[serde(default)]
    vk_token: Option<bool>,
}

pub(super) async fn group_admin_client_update(
    State(state): State<WebState>,
    Path((admin_uuid, password)): Path<(String, String)>,
    Json(payload): Json<GroupAdminUpdateClientRequest>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db
        .groups
        .values()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
    else {
        return stealth_abort_response();
    };
    let group_id = group.id.clone();
    let current_group_id = match db.passwords.get(&password) {
        Some(e) => e.group_id.clone(),
        None => return (StatusCode::NOT_FOUND, "client not found").into_response(),
    };
    if current_group_id != group_id {
        return (StatusCode::FORBIDDEN, "forbidden").into_response();
    }
    let validated_name = if let Some(name) = payload.name {
        let trimmed = name.trim().to_string();
        let scope_names = model::existing_client_names_in_scope(&db, &group_id, Some(&password));
        let client_name = if trimmed.is_empty() {
            model::generate_unique_client_name(&scope_names)
        } else {
            if let Err(msg) = validate_client_name(&trimmed) {
                return (StatusCode::BAD_REQUEST, msg).into_response();
            }
            if model::is_client_name_taken(&scope_names, &trimmed) {
                return (
                    StatusCode::BAD_REQUEST,
                    "Участник с таким именем уже существует в этой группе",
                )
                    .into_response();
            }
            trimmed
        };
        Some(client_name)
    } else {
        None
    };

    let entry = db.passwords.get_mut(&password).unwrap();
    if let Some(name) = validated_name {
        entry.name = name;
    }
    if let Some(days) = payload.days {
        if days > 0 {
            entry.expires_at = model::now().saturating_add(days.saturating_mul(86400));
        } else {
            entry.expires_at = 0;
        }
    }
    if let Some(w) = payload.workers {
        match validate_workers(Some(w)) {
            Ok(validated) => entry.workers = validated,
            Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
        }
    }
    if let Some(ref h) = payload.hash {
        let vk_hashes = match normalize_client_vk_hashes(h) {
            Ok(v) => v,
            Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
        };
        entry.vk_hash = vk_hashes.clone();
        entry.vk_hashes = vk_hashes;
    }
    if let Some(vt) = payload.vk_token {
        entry.vk_token_enabled = vt;
        if vt {
            entry.vk_hash.clear();
            entry.vk_hashes.clear();
        }
    }
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    let _ = auth::refresh_credentials_report(&state).await;
    (StatusCode::OK, Json(serde_json::json!({ "ok": true }))).into_response()
}

pub(super) async fn group_admin_client_delete(
    State(state): State<WebState>,
    Path((admin_uuid, password)): Path<(String, String)>,
) -> Response {
    let mut db = state.app.db.write().await;
    let Some(group) = db
        .groups
        .values()
        .find(|g| !g.admin_uuid.is_empty() && g.admin_uuid == admin_uuid)
    else {
        return stealth_abort_response();
    };
    let group_id = group.id.clone();
    let Some(entry) = db.passwords.get(&password) else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };
    if entry.group_id != group_id {
        return (StatusCode::FORBIDDEN, "forbidden").into_response();
    }
    db.passwords.remove(&password);
    let snapshot = db.clone();
    drop(db);
    state.app.db_persistence.submit(snapshot);
    crate::protocol::drop_password_sessions(&state.app, &password);
    let _ = auth::refresh_credentials_report(&state).await;
    (StatusCode::OK, Json(serde_json::json!({ "ok": true }))).into_response()
}
