package com.shod.client.settings.profile

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Resolves the persisted Preferences key for a profile slot.
 *
 * Profile zero intentionally uses the original, unsuffixed key. Other profiles
 * use the historical "<key>_<index>" naming convention.
 *
 * The key groups mirror SettingsStore.getProfileKey exactly. Unknown keys retain
 * the legacy String fallback; do not infer a type from the generic parameter.
 */
internal object ProfilePreferenceKeys {
    fun <T> forProfile(baseKey: Preferences.Key<T>, profile: Int): Preferences.Key<T> {
        if (profile == 0) return baseKey

        val name = "${baseKey.name}_$profile"
        @Suppress("UNCHECKED_CAST")
        return when (baseKey.name) {
            in stringKeyNames -> stringPreferencesKey(name) as Preferences.Key<T>
            in intKeyNames -> intPreferencesKey(name) as Preferences.Key<T>
            in booleanKeyNames -> booleanPreferencesKey(name) as Preferences.Key<T>
            in longKeyNames -> longPreferencesKey(name) as Preferences.Key<T>
            else -> stringPreferencesKey(name) as Preferences.Key<T>
        }
    }

    private val stringKeyNames = setOf(
        "peer", "vk_hashes", "secondary_vk_hash", "protocol", "sni", "user_agent",
        "deploy_ip", "deploy_login", "deploy_password", "deploy_password_encrypted",
        "deploy_ssh_port", "excluded_apps", "whitelist_apps", "direct_domains",
        "direct_domains_black", "direct_domains_white", "direct_ips_black", "direct_ips_white",
        "connection_password", "connection_password_encrypted", "connection_password_local",
        "deploy_main_password", "deploy_main_password_encrypted", "deploy_web_login",
        "deploy_web_password", "deploy_web_password_encrypted", "deploy_last_inspection_arch",
        "deploy_last_inspection_service", "deploy_last_inspection_version", "deploy_last_inspection_message", "proxy_mode", "proxy_host",
        "vk_auth_mode", "obfs_mode", "turn_transport", "srtp_mode", "captcha_mode",
        "captcha_solve_method", "captcha_wbv_solve_method", "shod_link",
        "selected_fingerprint", "active_client_ids", "vk_hash_mode", "vk_access_token",
        "vk_access_token_encrypted", "vk_access_token_user_id", "ssh_private_key",
        "ssh_private_key_encrypted", "ssh_key_passphrase", "ssh_key_passphrase_encrypted",
        "ssh_certificate", "ssh_certificate_encrypted", "vk_hash_check_results",
        "client_id_check_results", "custom_turn_ips", "selected_turn_subnet",
        "subscription_url", "subscription_title", "subscription_uuid", "subscription_node",
    )

    private val intKeyNames = setOf(
        "workers_per_hash", "listen_port", "server_peer_port", "proxy_port",
        "server_web_port", "subscription_power", "deploy_connection_profile",
    )

    private val booleanKeyNames = setOf(
        "app_routing_all", "manual_ports_enabled", "deploy_manual_ports", "no_dns",
        "is_whitelist", "shod_link_mode", "detailed_logs", "ssh_keys_mode", "docker_install",
        "extra_workers", "split_tunnel_whitelist_migrated", "deploy_last_inspection_ok",
        "deploy_last_inspection_panel", "domain_routing_enabled",
        "domain_is_whitelist", "exclude_ru_zones_enabled", "template_domains_enabled",
        "ip_routing_enabled", "ip_is_whitelist", "deploy_server_configured", "profile_removed",
    )

    private val longKeyNames = setOf("connection_generation", "profile_revision", "deploy_last_inspection_at")
}
