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
