//! Routing-rule handlers and domain-template access.

use super::*;

#[derive(Serialize)]
struct RoutingRuleInfo {
    id: String,
    name: String,
    is_whitelist: bool,
    mode: String,
    packages: Vec<String>,
    template_domains: bool,
    direct_domains: Vec<String>,
    domains: Vec<String>,
    domains_is_whitelist: bool,
    domains_mode: String,
    ip_is_whitelist: bool,
    ip_mode: String,
    ips: Vec<String>,
    assigned_groups: Vec<String>,
    target_group_ids: Vec<String>,
    assigned_clients: Vec<String>,
    target_client_passwords: Vec<String>,
}

pub(super) async fn routing_rules_get(State(state): State<WebState>) -> Response {
    let db = state.app.db.read().await;
    let mut list = Vec::new();
    for rule in db.routing_rules.values() {
        let mut assigned_groups = Vec::new();
        for group in db.groups.values() {
            if group.routing_rule_id == rule.id {
                assigned_groups.push(group.id.clone());
            }
        }
        let mut assigned_clients = Vec::new();
        if !db.main_password.is_empty() && db.main_routing_rule_id == rule.id {
            assigned_clients.push(db.main_password.clone());
        }
        for (pass, entry) in &db.passwords {
            if entry.routing_rule_id == rule.id {
                assigned_clients.push(pass.clone());
            }
        }
        list.push(RoutingRuleInfo {
            id: rule.id.clone(),
            name: rule.name.clone(),
            is_whitelist: rule.is_whitelist,
            mode: if rule.is_whitelist {
                "whitelist".to_string()
            } else {
                "blacklist".to_string()
            },
            packages: rule.packages.clone(),
            template_domains: rule.template_domains,
            direct_domains: rule.direct_domains.clone(),
            domains: rule.direct_domains.clone(),
            domains_is_whitelist: rule.domains_is_whitelist,
            domains_mode: if rule.domains_is_whitelist {
                "whitelist".to_string()
            } else {
                "blacklist".to_string()
            },
            ip_is_whitelist: rule.ip_is_whitelist,
            ip_mode: if rule.ip_is_whitelist {
                "whitelist".to_string()
            } else {
                "blacklist".to_string()
            },
            ips: rule.ips.clone(),
            assigned_groups: assigned_groups.clone(),
            target_group_ids: assigned_groups,
            assigned_clients: assigned_clients.clone(),
            target_client_passwords: assigned_clients,
        });
    }
    Json(list).into_response()
}

const TEMPLATE_DOMAINS_TXT: &str = include_str!("../../../app/src/main/assets/domains.txt");

pub(super) async fn template_domains_get() -> impl IntoResponse {
    (
        [(
            axum::http::header::CONTENT_TYPE,
            "text/plain; charset=utf-8",
        )],
        TEMPLATE_DOMAINS_TXT,
    )
}

#[derive(Deserialize)]
struct CreateRoutingRuleRequest {
    #[serde(default)]
    name: String,
    #[serde(default)]
    is_whitelist: bool,
    #[serde(default)]
    mode: Option<String>,
    #[serde(default)]
    packages: Vec<String>,
    #[serde(default)]
    template_domains: bool,
    #[serde(default)]
    direct_domains: Vec<String>,
    #[serde(default)]
    domains: Option<Vec<String>>,
    #[serde(default)]
    domains_mode: Option<String>,
    #[serde(default)]
    domains_is_whitelist: bool,
    #[serde(default)]
    ip_mode: Option<String>,
    #[serde(default)]
    ip_is_whitelist: bool,
    #[serde(default)]
    ips: Vec<String>,
    #[serde(default)]
    assigned_groups: Vec<String>,
    #[serde(default)]
    target_group_ids: Option<Vec<String>>,
    #[serde(default)]
    assigned_clients: Vec<String>,
    #[serde(default)]
    target_client_passwords: Option<Vec<String>>,
}

pub(super) async fn routing_rules_create(
    State(state): State<WebState>,
    Json(request): Json<CreateRoutingRuleRequest>,
) -> Response {
    let name_str = request.name.trim();
    let name = if name_str.is_empty() {
        "Правило".to_string()
    } else {
        name_str.chars().take(64).collect::<String>()
    };
    let is_whitelist = if let Some(ref m) = request.mode {
        m == "whitelist"
    } else {
        request.is_whitelist
    };
    let direct_domains = if !request.direct_domains.is_empty() {
        request.direct_domains
    } else {
        request.domains.unwrap_or_default()
    };
    let domains_is_whitelist = if let Some(ref m) = request.domains_mode {
        m == "whitelist"
    } else {
        request.domains_is_whitelist
    };
    let ip_is_whitelist = if let Some(ref m) = request.ip_mode {
        m == "whitelist"
    } else {
        request.ip_is_whitelist
    };
    let assigned_groups = if !request.assigned_groups.is_empty() {
        request.assigned_groups
    } else {
        request.target_group_ids.unwrap_or_default()
    };
    let assigned_clients = if !request.assigned_clients.is_empty() {
        request.assigned_clients
    } else {
        request.target_client_passwords.unwrap_or_default()
    };
    let id = format!("rule_{}", random_token(8));
    let rule = RoutingRule {
        id: id.clone(),
        name,
        is_whitelist,
        packages: request.packages,
        template_domains: request.template_domains,
        direct_domains,
        domains_is_whitelist,
        ip_is_whitelist,
        ips: request.ips,
    };
    let revision = {
        let mut db = state.app.db.write().await;
        for g_id in &assigned_groups {
            if let Some(g) = db.groups.get_mut(g_id) {
                g.routing_rule_id = id.clone();
            }
        }
        for c_pass in &assigned_clients {
            if let Some(p) = db.passwords.get_mut(c_pass) {
                p.routing_rule_id = id.clone();
            }
        }
        if !db.main_password.is_empty() && assigned_clients.contains(&db.main_password) {
            db.main_routing_rule_id = id.clone();
        }
        db.routing_rules.insert(id, rule.clone());
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    Json(rule).into_response()
}

pub(super) async fn routing_rules_update(
    State(state): State<WebState>,
    Path(id): Path<String>,
    Json(request): Json<CreateRoutingRuleRequest>,
) -> Response {
    let name_str = request.name.trim();
    let name = if name_str.is_empty() {
        "Правило".to_string()
    } else {
        name_str.chars().take(64).collect::<String>()
    };
    let is_whitelist = if let Some(ref m) = request.mode {
        m == "whitelist"
    } else {
        request.is_whitelist
    };
    let direct_domains = if !request.direct_domains.is_empty() {
        request.direct_domains
    } else {
        request.domains.unwrap_or_default()
    };
    let domains_is_whitelist = if let Some(ref m) = request.domains_mode {
        m == "whitelist"
    } else {
        request.domains_is_whitelist
    };
    let ip_is_whitelist = if let Some(ref m) = request.ip_mode {
        m == "whitelist"
    } else {
        request.ip_is_whitelist
    };
    let assigned_groups = if !request.assigned_groups.is_empty() {
        request.assigned_groups
    } else {
        request.target_group_ids.unwrap_or_default()
    };
    let assigned_clients = if !request.assigned_clients.is_empty() {
        request.assigned_clients
    } else {
        request.target_client_passwords.unwrap_or_default()
    };
    let revision = {
        let mut db = state.app.db.write().await;
        let Some(rule) = db.routing_rules.get_mut(&id) else {
            return StatusCode::NOT_FOUND.into_response();
        };
        rule.name = name;
        rule.is_whitelist = is_whitelist;
        rule.packages = request.packages;
        rule.template_domains = request.template_domains;
        rule.direct_domains = direct_domains;
        rule.domains_is_whitelist = domains_is_whitelist;
        rule.ip_is_whitelist = ip_is_whitelist;
        rule.ips = request.ips;

        for group in db.groups.values_mut() {
            if assigned_groups.contains(&group.id) {
                group.routing_rule_id = id.clone();
            } else if group.routing_rule_id == id {
                group.routing_rule_id.clear();
            }
        }
        for (pass, entry) in db.passwords.iter_mut() {
            if assigned_clients.contains(pass) {
                entry.routing_rule_id = id.clone();
            } else if entry.routing_rule_id == id {
                entry.routing_rule_id.clear();
            }
        }
        if !db.main_password.is_empty() {
            if assigned_clients.contains(&db.main_password) {
                db.main_routing_rule_id = id.clone();
            } else if db.main_routing_rule_id == id {
                db.main_routing_rule_id.clear();
            }
        }
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    StatusCode::OK.into_response()
}

pub(super) async fn routing_rules_delete(State(state): State<WebState>, Path(id): Path<String>) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        if db.routing_rules.remove(&id).is_none() {
            return StatusCode::NOT_FOUND.into_response();
        }
        for group in db.groups.values_mut() {
            if group.routing_rule_id == id {
                group.routing_rule_id.clear();
            }
        }
        for entry in db.passwords.values_mut() {
            if entry.routing_rule_id == id {
                entry.routing_rule_id.clear();
            }
        }
        if db.main_routing_rule_id == id {
            db.main_routing_rule_id.clear();
        }
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    StatusCode::OK.into_response()
}
