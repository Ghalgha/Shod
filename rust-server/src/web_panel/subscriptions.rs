//! Client and group subscription pages, data, and link handling.

use super::*;

fn html_escape(input: &str) -> String {
    input
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

fn replace_html_placeholders(template: &str, replacements: &[(&str, &str)]) -> String {
    let mut output = String::with_capacity(template.len());
    let mut remaining = template;

    while !remaining.is_empty() {
        let next = replacements
            .iter()
            .filter_map(|(marker, value)| {
                remaining.find(marker).map(|index| (index, *marker, *value))
            })
            .min_by_key(|(index, _, _)| *index);

        let Some((index, marker, value)) = next else {
            output.push_str(remaining);
            break;
        };

        output.push_str(&remaining[..index]);
        output.push_str(value);
        remaining = &remaining[index + marker.len()..];
    }

    output
}

fn render_client_sub_html(client_name: &str, shod_link: &str) -> String {
    let name_esc = html_escape(client_name);
    let link_esc = html_escape(shod_link);
    replace_html_placeholders(
        CLIENT_SUB_HTML,
        &[("__CLIENT_NAME__", &name_esc), ("__Shod_LINK__", &link_esc)],
    )
}

fn render_group_sub_html(group_name: &str, clients_json: &str, count: usize) -> String {
    let name_esc = html_escape(group_name);
    let json_esc = js_json_embed(clients_json);
    let count_text = count.to_string();
    replace_html_placeholders(
        GROUP_SUB_HTML,
        &[
            ("__GROUP_NAME__", &name_esc),
            ("__CLIENTS_COUNT__", &count_text),
            ("__CLIENTS_JSON__", &json_esc),
        ],
    )
}

fn js_json_embed(json: &str) -> String {
    json.replace('<', "\\u003C")
        .replace('>', "\\u003E")
        .replace('&', "\\u0026")
        .replace('\u{2028}', "\\u2028")
        .replace('\u{2029}', "\\u2029")
}

pub(super) async fn client_sub_page(
    State(state): State<WebState>,
    Path(sub_uuid): Path<String>,
    headers: HeaderMap,
    token: Option<Extension<Arc<StealthAbortController>>>,
) -> Response {
    let sub_uuid = sub_uuid.trim();
    if sub_uuid.is_empty() {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    }
    let db = state.app.db.read().await;
    let host = resolve_client_host(&headers, &state).await;

    if !db.main_sub_uuid.is_empty() && db.main_sub_uuid == sub_uuid && !db.main_password.is_empty()
    {
        let main_peer_port = state.app.listen.port();
        let link = make_main_link(&host, main_peer_port, &db);
        return Html(render_client_sub_html("Владелец", &link)).into_response();
    }

    let client_entry = db
        .passwords
        .iter()
        .find(|(_, entry)| !entry.sub_uuid.is_empty() && entry.sub_uuid == sub_uuid);
    let Some((password, entry)) = client_entry else {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    };
    let client_peer_port = if entry.dtls_port == 0 {
        state.app.listen.port()
    } else {
        entry.dtls_port
    };
    let effective_vk_token = if !entry.group_id.is_empty() {
        db.groups
            .get(&entry.group_id)
            .and_then(|g| g.vk_token.as_deref())
            .unwrap_or(&db.vk_access_token)
    } else {
        &db.vk_access_token
    };
    let link = make_entry_link(&host, client_peer_port, password, entry, effective_vk_token);
    let client_name = if entry.name.is_empty() {
        "Клиент".to_string()
    } else {
        entry.name.clone()
    };
    Html(render_client_sub_html(&client_name, &link)).into_response()
}

pub(super) async fn client_sub_json(
    State(state): State<WebState>,
    Path(sub_uuid): Path<String>,
    headers: HeaderMap,
    token: Option<Extension<Arc<StealthAbortController>>>,
) -> Response {
    let sub_uuid = sub_uuid.trim();
    if sub_uuid.is_empty() {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    }
    let db = state.app.db.read().await;
    let host = resolve_client_host(&headers, &state).await;

    if !db.main_sub_uuid.is_empty() && db.main_sub_uuid == sub_uuid && !db.main_password.is_empty()
    {
        let main_peer_port = state.app.listen.port();
        let link = make_main_link(&host, main_peer_port, &db);
        return (
            StatusCode::OK,
            Json(serde_json::json!({
                "ok": true,
                "name": "Владелец",
                "link": link,
                "sub_uuid": db.main_sub_uuid,
            })),
        )
            .into_response();
    }

    let client_entry = db
        .passwords
        .iter()
        .find(|(_, entry)| !entry.sub_uuid.is_empty() && entry.sub_uuid == sub_uuid);
    let Some((password, entry)) = client_entry else {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    };
    let client_peer_port = if entry.dtls_port == 0 {
        state.app.listen.port()
    } else {
        entry.dtls_port
    };
    let effective_vk_token = if !entry.group_id.is_empty() {
        db.groups
            .get(&entry.group_id)
            .and_then(|g| g.vk_token.as_deref())
            .unwrap_or(&db.vk_access_token)
    } else {
        &db.vk_access_token
    };
    let link = make_entry_link(&host, client_peer_port, password, entry, effective_vk_token);
    let client_name = if entry.name.is_empty() {
        "Клиент".to_string()
    } else {
        entry.name.clone()
    };
    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true,
            "name": client_name,
            "link": link,
            "sub_uuid": entry.sub_uuid,
        })),
    )
        .into_response()
}

#[derive(Serialize)]
struct GroupSubClientItem {
    name: String,
    link: String,
    sub_uuid: String,
}

pub(super) async fn group_sub_page(
    State(state): State<WebState>,
    Path(group_sub_uuid): Path<String>,
    headers: HeaderMap,
    token: Option<Extension<Arc<StealthAbortController>>>,
) -> Response {
    let group_sub_uuid = group_sub_uuid.trim();
    if group_sub_uuid.is_empty() {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    }
    let db = state.app.db.read().await;
    let group_entry = db
        .groups
        .values()
        .find(|g| !g.sub_uuid.is_empty() && g.sub_uuid == group_sub_uuid);
    let Some(group) = group_entry else {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    };

    let host = resolve_client_host(&headers, &state).await;
    let effective_vk_token = group.vk_token.as_deref().unwrap_or(&db.vk_access_token);
    let mut items = Vec::new();
    for (password, entry) in &db.passwords {
        if entry.group_id == group.id {
            let client_peer_port = if entry.dtls_port == 0 {
                state.app.listen.port()
            } else {
                entry.dtls_port
            };
            let link =
                make_entry_link(&host, client_peer_port, password, entry, effective_vk_token);
            let name = if entry.name.is_empty() {
                "Клиент".to_string()
            } else {
                entry.name.clone()
            };
            items.push(GroupSubClientItem {
                name,
                link,
                sub_uuid: entry.sub_uuid.clone(),
            });
        }
    }
    items.sort_by(|a, b| a.name.cmp(&b.name));
    let count = items.len();
    let clients_json = serde_json::to_string(&items).unwrap_or_else(|_| "[]".to_string());
    Html(render_group_sub_html(&group.name, &clients_json, count)).into_response()
}

pub(super) async fn group_sub_json(
    State(state): State<WebState>,
    Path(group_sub_uuid): Path<String>,
    headers: HeaderMap,
    token: Option<Extension<Arc<StealthAbortController>>>,
) -> Response {
    let group_sub_uuid = group_sub_uuid.trim();
    if group_sub_uuid.is_empty() {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    }
    let db = state.app.db.read().await;
    let group_entry = db
        .groups
        .values()
        .find(|g| !g.sub_uuid.is_empty() && g.sub_uuid == group_sub_uuid);
    let Some(group) = group_entry else {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    };

    let host = resolve_client_host(&headers, &state).await;
    let effective_vk_token = group.vk_token.as_deref().unwrap_or(&db.vk_access_token);
    let mut items = Vec::new();
    for (password, entry) in &db.passwords {
        if entry.group_id == group.id {
            let client_peer_port = if entry.dtls_port == 0 {
                state.app.listen.port()
            } else {
                entry.dtls_port
            };
            let link =
                make_entry_link(&host, client_peer_port, password, entry, effective_vk_token);
            let name = if entry.name.is_empty() {
                "Клиент".to_string()
            } else {
                entry.name.clone()
            };
            items.push(GroupSubClientItem {
                name,
                link,
                sub_uuid: entry.sub_uuid.clone(),
            });
        }
    }
    items.sort_by(|a, b| a.name.cmp(&b.name));

    (
        StatusCode::OK,
        Json(serde_json::json!({
            "ok": true,
            "group": {
                "id": group.id,
                "name": group.name,
                "sub_uuid": group.sub_uuid,
            },
            "clients": items,
        })),
    )
        .into_response()
}

pub(super) async fn universal_sub_json(
    State(state): State<WebState>,
    Path(uuid): Path<String>,
    headers: HeaderMap,
    token: Option<Extension<Arc<StealthAbortController>>>,
) -> Response {
    let trimmed = uuid.trim();
    if trimmed.is_empty() {
        if let Some(Extension(ctrl)) = token {
            ctrl.abort();
        }
        return stealth_abort_response();
    }
    let is_client = {
        let db = state.app.db.read().await;
        (!db.main_sub_uuid.is_empty() && db.main_sub_uuid == trimmed)
            || db
                .passwords
                .values()
                .any(|p| !p.sub_uuid.is_empty() && p.sub_uuid == trimmed)
    };
    if is_client {
        return client_sub_json(State(state), Path(uuid), headers, token).await;
    }
    let is_group = {
        let db = state.app.db.read().await;
        db.groups
            .values()
            .any(|g| !g.sub_uuid.is_empty() && g.sub_uuid == trimmed)
    };
    if is_group {
        return group_sub_json(State(state), Path(uuid), headers, token).await;
    }
    if let Some(Extension(ctrl)) = token {
        ctrl.abort();
    }
    stealth_abort_response()
}
