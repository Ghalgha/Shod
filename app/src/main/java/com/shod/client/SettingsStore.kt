package com.shod.client

import com.shod.client.settings.theme.UiDesignPresets
import com.shod.client.settings.theme.UiScale

import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shod.client.integration.vk.storage.SavedVkAccounts
import com.shod.client.settings.model.DeployServerInfo
import com.shod.client.settings.model.DeploySettingsSnapshot
import com.shod.client.settings.model.ImportedConnectionSettings
import com.shod.client.settings.model.ProfileConnectionSnapshot
import com.shod.client.settings.model.ProfileSubscriptionInfo
import com.shod.client.settings.model.SubscriptionMeta
import com.shod.client.settings.model.TunnelAuthSnapshot
import com.shod.client.settings.model.TunnelScreenSnapshot
import com.shod.client.settings.profile.ProfileConnectionStore
import com.shod.client.settings.profile.ProfileCursorStore
import com.shod.client.settings.profile.ProfileLifecycleStore
import com.shod.client.settings.profile.ProfileMetadataStore
import com.shod.client.settings.profile.ProfilePreferenceKeys
import com.shod.client.settings.profile.defaultProfileName
import com.shod.client.settings.secret.SecureStringStore
import com.shod.client.settings.secret.StoredSecret
import com.shod.client.settings.secret.StoredSecretState
import com.shod.client.settings.secret.resolveTunnelPasswordStorage
import com.shod.client.settings.secret.shouldResetUnreadableTunnelPassword
import com.shod.client.settings.secret.resolveStoredSecret
import com.shod.client.settings.storage.DeployScopedDataStore
import com.shod.client.settings.storage.ReadOnlyProfileDataStore
import com.shod.client.settings.storage.settingsDataStore
import com.shod.client.settings.subscription.ProfileSubscriptionStore
import com.shod.client.settings.subscription.SubscriptionMetaCodec
import com.shod.client.subscriptions.SubscriptionProfileWrite
import com.shod.client.settings.theme.ThemePreferencesStore
import com.shod.client.settings.update.UpdatePreferencesStore
import com.shod.client.settings.deploy.DeployInspectionStore
import com.shod.client.settings.system.BatteryOptimizationPromptStore
import com.shod.client.vpn.lifecycle.TunnelRuntimePreferencesStore
import com.shod.client.network.wifi.policy.WifiNetworkListMode
import com.shod.client.network.wifi.preferences.WifiPreferences
import com.shod.client.network.wifi.preferences.WifiPreferenceStore
import com.shod.client.network.wifi.policy.decodeWifiSsids
import com.shod.client.network.wifi.policy.encodeWifiSsids
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun <T> Flow<T>.onIo(): Flow<T> = flowOn(Dispatchers.IO)

class SettingsStore(context: Context, deployScope: Boolean = false, readOnlyProfile: Int? = null) {
    private val appContext = context.applicationContext
    companion object {
        private val ACTIVE_PROFILE = intPreferencesKey("active_profile")
        private val UI_OWNER_MODE = booleanPreferencesKey("ui_owner_mode")
        private val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        private val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        private val INFO_ACTIONS_EXPANDED = booleanPreferencesKey("info_actions_expanded")
        private val INFO_PROJECT_EXPANDED = booleanPreferencesKey("info_project_expanded")
        private val Shod_LINK = stringPreferencesKey("shod_link")
        private val Shod_LINK_MODE = booleanPreferencesKey("shod_link_mode")
        // the owner's link hashes a participant took out of the connection (per profile, comma-separated)
        private val LINK_HASHES_REMOVED = stringPreferencesKey("link_hashes_removed")
        private val PARTICIPANT_SHOW_LOGS = booleanPreferencesKey("participant_show_logs")
        private val SUBSCRIPTION_URL = stringPreferencesKey("subscription_url")
        private val SUBSCRIPTION_TITLE = stringPreferencesKey("subscription_title")
        private val SUBSCRIPTION_UUID = stringPreferencesKey("subscription_uuid")
        private val SUBSCRIPTION_NODE = stringPreferencesKey("subscription_node")
        private val SUBSCRIPTION_POWER = intPreferencesKey("subscription_power")
        private val SUBSCRIPTION_META = stringPreferencesKey("subscription_meta_v1")

        private val PEER = stringPreferencesKey("peer")
        private val VK_HASHES = stringPreferencesKey("vk_hashes")
        private val SECONDARY_VK_HASH = stringPreferencesKey("secondary_vk_hash")
        private val WORKERS_PER_HASH = intPreferencesKey("workers_per_hash")
        private val WORKER_PROFILE_MODE = stringPreferencesKey("worker_profile_mode")
        private val WORKER_PROFILE_CUSTOM = intPreferencesKey("worker_profile_custom")
        private val PROTOCOL = stringPreferencesKey("protocol")
        private val LISTEN_PORT = intPreferencesKey("listen_port")
        private val MANUAL_PORTS_ENABLED = booleanPreferencesKey("manual_ports_enabled")
        private val DEPLOY_MANUAL_PORTS = booleanPreferencesKey("deploy_manual_ports")
        private val SERVER_PEER_PORT = intPreferencesKey("server_peer_port")
        private val SNI = stringPreferencesKey("sni")
        private val NO_DNS = booleanPreferencesKey("no_dns")
        private val CUSTOM_TURN_IPS = stringPreferencesKey("custom_turn_ips")
        private val SELECTED_TURN_SUBNET = stringPreferencesKey("selected_turn_subnet")
        private val TURN_SUBNET_IP_LIMIT = stringPreferencesKey("turn_subnet_ip_limit")

        private val USER_AGENT = stringPreferencesKey("user_agent")
        private val PROFILE_NAME = stringPreferencesKey("profile_name")

        private val DEPLOY_IP = stringPreferencesKey("deploy_ip")
        private val DEPLOY_SERVER_NAME = stringPreferencesKey("deploy_server_name")
        private val DEPLOY_PROFILE_NAMES_CLEANED = booleanPreferencesKey("deploy_profile_names_cleaned_v2")
        private val DEFAULT_ROLE_DECIDED = booleanPreferencesKey("default_role_decided")
        private val DEPLOY_LOGIN = stringPreferencesKey("deploy_login")
        private val DEPLOY_PASSWORD = stringPreferencesKey("deploy_password")
        private val DEPLOY_PASSWORD_ENCRYPTED = stringPreferencesKey("deploy_password_encrypted")
        private val DEPLOY_SSH_PORT = stringPreferencesKey("deploy_ssh_port")
        private val SSH_KEYS_MODE = booleanPreferencesKey("ssh_keys_mode")
        private val DOCKER_INSTALL = booleanPreferencesKey("docker_install")
private val DEPLOY_SECRET_PATH_ENABLED = booleanPreferencesKey("deploy_secret_path_enabled")
private val DEPLOY_SECRET_PATH_UUID = stringPreferencesKey("deploy_secret_path_uuid")
        private val SSH_PRIVATE_KEY = stringPreferencesKey("ssh_private_key")
        private val SSH_PRIVATE_KEY_ENCRYPTED = stringPreferencesKey("ssh_private_key_encrypted")
        private val SSH_KEY_PASSPHRASE = stringPreferencesKey("ssh_key_passphrase")
        private val SSH_KEY_PASSPHRASE_ENCRYPTED = stringPreferencesKey("ssh_key_passphrase_encrypted")
        private val SSH_CERTIFICATE = stringPreferencesKey("ssh_certificate")
        private val SSH_CERTIFICATE_ENCRYPTED = stringPreferencesKey("ssh_certificate_encrypted")
        private val EXCLUDED_APPS = stringPreferencesKey("excluded_apps")
        private val APP_ROUTING_ALL = booleanPreferencesKey("app_routing_all")
        private val APP_PRESETS_USED = stringPreferencesKey("app_presets_used")
        private val WHITELIST_APPS = stringPreferencesKey("whitelist_apps")

        private val DETAILED_LOGS = booleanPreferencesKey("detailed_logs")

        private val CONNECTION_PASSWORD = stringPreferencesKey("connection_password")
        private val CONNECTION_PASSWORD_ENCRYPTED = stringPreferencesKey("connection_password_encrypted")
        private val CONNECTION_PASSWORD_LOCAL = stringPreferencesKey("connection_password_local")
        private val DEPLOY_MAIN_PASSWORD = stringPreferencesKey("deploy_main_password")
        private val DEPLOY_MAIN_PASSWORD_ENCRYPTED = stringPreferencesKey("deploy_main_password_encrypted")
        private val DEPLOY_WEB_LOGIN = stringPreferencesKey("deploy_web_login")
        private val DEPLOY_WEB_PASSWORD = stringPreferencesKey("deploy_web_password")
        private val DEPLOY_WEB_PASSWORD_ENCRYPTED = stringPreferencesKey("deploy_web_password_encrypted")
        private val SERVER_WEB_PORT = intPreferencesKey("server_web_port")

        private val PROXY_MODE = stringPreferencesKey("proxy_mode") 
        private val PROXY_HOST = stringPreferencesKey("proxy_host")
        private val PROXY_PORT = intPreferencesKey("proxy_port")

        private val VK_AUTH_MODE = stringPreferencesKey("vk_auth_mode") 
        private val OBFS_MODE = stringPreferencesKey("obfs_mode") 
        private val TURN_TRANSPORT = stringPreferencesKey("turn_transport")
        private val SRTP_MODE = stringPreferencesKey("srtp_mode")
        private val CAPTCHA_MODE = stringPreferencesKey("captcha_mode")
        private val CAPTCHA_SOLVE_METHOD = stringPreferencesKey("captcha_solve_method") 
        private val CAPTCHA_WBV_SOLVE_METHOD = stringPreferencesKey("captcha_wbv_solve_method") 

        private val VK_HASH_MODE = stringPreferencesKey("vk_hash_mode")
        private val VK_ACCESS_TOKEN = stringPreferencesKey("vk_access_token")
        private val VK_ACCESS_TOKEN_ENCRYPTED = stringPreferencesKey("vk_access_token_encrypted")
        private val VK_ACCESS_TOKEN_USER_ID = stringPreferencesKey("vk_access_token_user_id")
        private val VK_ACCOUNT_TOKEN = stringPreferencesKey("vk_account_token")
        private val VK_ACCOUNT_TOKEN_ENCRYPTED = stringPreferencesKey("vk_account_token_encrypted")
        private val VK_ACCOUNT_USER_ID = stringPreferencesKey("vk_account_user_id")
        private val UI_DESIGN = stringPreferencesKey("ui_design")
        private val UI_SCALE = intPreferencesKey("ui_scale_percent")
        private val BATTERY_SAVER_MODE = stringPreferencesKey("battery_saver_mode")
        private val BATTERY_SAVER_PERCENT = intPreferencesKey("battery_saver_percent")
        private val DEPLOY_CONNECTION_PROFILE = intPreferencesKey("deploy_connection_profile")
        private val PROFILE_REVISION = longPreferencesKey("profile_revision")
        private val PROFILE_REMOVED = booleanPreferencesKey("profile_removed")

        private val IS_WHITELIST = booleanPreferencesKey("is_whitelist")
        private val SPLIT_TUNNEL_WHITELIST_MIGRATED = booleanPreferencesKey("split_tunnel_whitelist_migrated")
        private val EXTRA_WORKERS = booleanPreferencesKey("extra_workers")
        private val AUTO_JS_RISK_ACKNOWLEDGED = booleanPreferencesKey("auto_js_risk_acknowledged")
        private val TCP_TRANSPORT_RISK_ACKNOWLEDGED = booleanPreferencesKey("tcp_transport_risk_acknowledged")
        private val DOMAIN_ROUTING_ENABLED = booleanPreferencesKey("domain_routing_enabled")
        private val DOMAIN_IS_WHITELIST = booleanPreferencesKey("domain_is_whitelist")
        private val EXCLUDE_RU_ZONES_ENABLED = booleanPreferencesKey("exclude_ru_zones_enabled")
        private val TEMPLATE_DOMAINS_ENABLED = booleanPreferencesKey("template_domains_enabled")
        private val DIRECT_DOMAINS = stringPreferencesKey("direct_domains")
        private val DIRECT_DOMAINS_BLACK = stringPreferencesKey("direct_domains_black")
        private val DIRECT_DOMAINS_WHITE = stringPreferencesKey("direct_domains_white")

        private val IP_ROUTING_ENABLED = booleanPreferencesKey("ip_routing_enabled")
        private val IP_IS_WHITELIST = booleanPreferencesKey("ip_is_whitelist")
        private val DIRECT_IPS_BLACK = stringPreferencesKey("direct_ips_black")
        private val DIRECT_IPS_WHITE = stringPreferencesKey("direct_ips_white")


        private val SELECTED_FINGERPRINT = stringPreferencesKey("selected_fingerprint")
        private val ACTIVE_CLIENT_IDS = stringPreferencesKey("active_client_ids")


        private val CLIENT_ID_CHECK_RESULTS = stringPreferencesKey("client_id_check_results")
        private val VK_HASH_CHECK_RESULTS = stringPreferencesKey("vk_hash_check_results")
        private val CONNECTION_GENERATION = longPreferencesKey("connection_generation")

        @Volatile
        internal var cachedVkHashMode: String? = null

        @Volatile
        internal var cachedVkAccessToken: String? = null

        @Volatile
        internal var cachedObfsMode: String? = null

        @Volatile
        internal var cachedTurnTransport: String? = null

        @Volatile
        internal var cachedSrtpMode: String? = null

        @Volatile
        internal var cachedShodLinkMode: Boolean? = null

        @Volatile
        internal var cachedWorkersPerHash: Int? = null

        @Volatile
        internal var cachedManualPortsEnabled: Boolean? = null

        @Volatile
        internal var cachedServerPeerPort: Int? = null

        @Volatile
        internal var cachedExtraWorkers: Boolean? = null

        @Volatile
        internal var cachedCustomTurnIps: String? = null

        @Volatile
        internal var cachedExcludedApps: String? = null

        @Volatile
        internal var cachedWhitelistApps: String? = null

        @Volatile
        internal var cachedIsWhitelist: Boolean? = null

        @Volatile
        internal var cachedDomainRoutingEnabled: Boolean? = null

        @Volatile
        internal var cachedDomainIsWhitelist: Boolean? = null

        @Volatile
        internal var cachedExcludeRuZonesEnabled: Boolean? = null

        @Volatile
        internal var cachedTemplateDomainsEnabled: Boolean? = null

        @Volatile
        internal var cachedDirectDomains: String? = null

        @Volatile
        internal var cachedDirectDomainsBlack: String? = null

        @Volatile
        internal var cachedDirectDomainsWhite: String? = null

        @Volatile
        internal var cachedIpRoutingEnabled: Boolean? = null

        @Volatile
        internal var cachedIpIsWhitelist: Boolean? = null

        @Volatile
        internal var cachedDirectIpsBlack: String? = null

        @Volatile
        internal var cachedDirectIpsWhite: String? = null

        @Volatile
        internal var cachedDeploySnapshot: DeploySettingsSnapshot = DeploySettingsSnapshot()

        private val uiCacheStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        private val migrationsStarted = java.util.concurrent.atomic.AtomicBoolean(false)

        private fun <T> getProfileKey(
            baseKey: Preferences.Key<T>,
            profile: Int,
        ): Preferences.Key<T> = ProfilePreferenceKeys.forProfile(baseKey, profile)
    }

    private val dataStore = when {
        readOnlyProfile != null -> ReadOnlyProfileDataStore(appContext.settingsDataStore, readOnlyProfile)
        deployScope -> DeployScopedDataStore(appContext.settingsDataStore)
        else -> appContext.settingsDataStore
    }
    private val batteryOptimizationPromptStore = BatteryOptimizationPromptStore(dataStore)
    private val tunnelRuntimePreferencesStore = TunnelRuntimePreferencesStore(dataStore)
    private val updatePreferencesStore = UpdatePreferencesStore(dataStore)
    private val deployInspectionStore = DeployInspectionStore()
    val uiOwnerMode: Flow<Boolean> = appContext.settingsDataStore.data.map { it[UI_OWNER_MODE] ?: false }
    val uiDesign: Flow<String> = appContext.settingsDataStore.data.map { UiDesignPresets.normalize(it[UI_DESIGN]) }
    val uiScalePercent: Flow<Int> = appContext.settingsDataStore.data.map { UiScale.normalize(it[UI_SCALE]) }.distinctUntilChanged()
    suspend fun saveUiScalePercent(percent: Int) {
        require(percent in UiScale.choices)
        appContext.settingsDataStore.edit { it[UI_SCALE] = percent }
    }
    suspend fun saveUiDesign(design: String) { require(UiDesignPresets.options.any { it.first == design }); appContext.settingsDataStore.edit { it[UI_DESIGN] = design } }
    val batterySaverMode: Flow<String> = appContext.settingsDataStore.data.map { prefs -> prefs[BATTERY_SAVER_MODE]?.takeIf { mode -> BatterySaver.MODES.any { it.first == mode } } ?: BatterySaver.DEFAULT_MODE }
    val batterySaverPercent: Flow<Int> = appContext.settingsDataStore.data.map { (it[BATTERY_SAVER_PERCENT] ?: BatterySaver.DEFAULT_PERCENT).coerceIn(5, 95) }
    suspend fun saveBatterySaverMode(mode: String) { require(BatterySaver.MODES.any { it.first == mode }); appContext.settingsDataStore.edit { it[BATTERY_SAVER_MODE] = mode } }
    suspend fun saveBatterySaverPercent(percent: Int) { appContext.settingsDataStore.edit { it[BATTERY_SAVER_PERCENT] = percent.coerceIn(5, 95) } }
    suspend fun saveUiOwnerMode(owner: Boolean) { appContext.settingsDataStore.edit { it[UI_OWNER_MODE] = owner } }
    private val secureStore = SecureStringStore(appContext)
    private val themePreferencesStore = ThemePreferencesStore(dataStore)
    private val savedVkAccounts = SavedVkAccounts(secureStore::encrypt, secureStore::decrypt)

    // All profile repositories share this instance (and therefore the same persisted file).
    private val profileCursorStore = ProfileCursorStore(
        dataStore,
        ShodConstants.Profiles.MIN_INDEX,
        ShodConstants.Profiles.MAX_INDEX,
    )
    private val profileMetadataStore = ProfileMetadataStore(
        dataStore,
        ShodConstants.Profiles.MIN_INDEX,
        ShodConstants.Profiles.MAX_INDEX,
    )
    private val profileLifecycleStore = ProfileLifecycleStore(
        dataStore,
        ShodConstants.Profiles.MIN_INDEX,
        ShodConstants.Profiles.MAX_INDEX,
    ) { profile -> PingResultsCache.removeProfile(profile) }
    private val profileSubscriptionStore = ProfileSubscriptionStore(
        dataStore,
        profileLifecycleStore,
        ShodConstants.Profiles.MIN_INDEX,
        ShodConstants.Profiles.MAX_INDEX,
    )
    private val profileConnectionStore = ProfileConnectionStore(
        dataStore = dataStore,
        secureStringStore = secureStore,
        normalizeWorkers = { workers ->
            WorkerCountPolicy.normalize(workers, maximum = ShodConstants.Tunnel.MAX_WORKERS)
        },
        invalidateProfileCache = { profile -> PingResultsCache.removeProfile(profile) },
        rewriteParticipantLink = { currentLink, peer, port, password ->
            com.shod.client.ui.utils.parseShodLink(currentLink)?.let { link ->
                com.shod.client.ui.utils.formatShodLink(
                    host = peer,
                    peer = port,
                    password = password,
                    hashes = link.hashes,
                    token = link.vkAccessToken,
                    power = link.power,
                )
            }
        },
    )

    val activeProfile: Flow<Int> = profileCursorStore.activeProfile
    val tunnelLastDiagnostic: Flow<String> = tunnelRuntimePreferencesStore.lastDiagnostic
    val showSystemApps: Flow<Boolean> = dataStore.data.map { it[SHOW_SYSTEM_APPS] ?: false }
    // Recording is always enabled; the UI preference only controls tab visibility.
    // Ignore historical false values, including those imported from older backups.
    val loggingEnabled: Flow<Boolean> = dataStore.data.map { it[LOGGING_ENABLED] ?: true }.distinctUntilChanged()
    val infoActionsExpanded: Flow<Boolean> = dataStore.data.map { it[INFO_ACTIONS_EXPANDED] ?: false }
    val infoProjectExpanded: Flow<Boolean> = dataStore.data.map { it[INFO_PROJECT_EXPANDED] ?: false }
    val shodLink: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(Shod_LINK, profile)] ?: ""
    }
    val shodLinkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(Shod_LINK_MODE, profile)] ?: false
    }
    val participantShowLogs: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(PARTICIPANT_SHOW_LOGS, profile)] ?: (prefs[UI_OWNER_MODE] ?: false)
    }

    val peer: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(PEER, profile)] ?: ""
    }
    val vkHashes: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(VK_HASHES, profile)] ?: ""
    }
    val secondaryVkHash: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SECONDARY_VK_HASH, profile)] ?: ""
    }
    /** The most workers the active profile may use: «Экстра потоки» lifts 70 to 100, a subscription can lower it. */
    private fun workersMaximum(prefs: Preferences): Int {
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val isExtra = prefs[getProfileKey(EXTRA_WORKERS, profile)] ?: false
        val defaultMax = WorkerCountPolicy.defaultMaximum(isExtra)
        return prefs[getProfileKey(SUBSCRIPTION_POWER, profile)]
            ?.takeIf { it in 10..100 }
            ?.coerceAtMost(defaultMax)
            ?: defaultMax
    }
    val workersMaximum: Flow<Int> = dataStore.data.map { prefs -> workersMaximum(prefs) }
    /** The selected worker preset is global: it follows the user when they switch servers. */
    val workerProfileMode: Flow<String> = dataStore.data.map { prefs ->
        val savedMode = prefs[WORKER_PROFILE_MODE]
        if (savedMode != null && savedMode in setOf("economy", "balanced", "maximum", "custom")) {
            savedMode
        } else {
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val maximum = workersMaximum(prefs)
            val legacyValue = WorkerCountPolicy.normalize(
                prefs[getProfileKey(WORKERS_PER_HASH, profile)] ?: 18,
                maximum = maximum,
            )
            when (legacyValue) {
                WorkerCountPolicy.normalize(10, maximum) -> "economy"
                WorkerCountPolicy.normalize(20, maximum) -> "balanced"
                maximum -> "maximum"
                else -> "custom"
            }
        }
    }
    val workersPerHash: Flow<Int> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val maximum = workersMaximum(prefs)
        val legacyValue = prefs[getProfileKey(WORKERS_PER_HASH, profile)] ?: 18
        val current = when (prefs[WORKER_PROFILE_MODE]) {
            "economy" -> 10
            "balanced" -> 20
            "maximum" -> maximum
            "custom" -> prefs[WORKER_PROFILE_CUSTOM] ?: legacyValue
            else -> legacyValue
        }
        WorkerCountPolicy.normalize(current, maximum = maximum)
    }
    val protocol: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(PROTOCOL, profile)] ?: "udp"
    }
    val listenPort: Flow<Int> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(LISTEN_PORT, profile)] ?: 0
    }
    val manualPortsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] ?: false
    }
    val serverPeerPort: Flow<Int> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SERVER_PEER_PORT, profile)] ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT
    }
    val sni: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SNI, profile)] ?: ""
    }

    val deployIp: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DEPLOY_IP, profile)] ?: ""
    }
    val deployLogin: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DEPLOY_LOGIN, profile)] ?: ""
    }
    val deployPassword: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readSecret(prefs, DEPLOY_PASSWORD_ENCRYPTED, DEPLOY_PASSWORD, profile)
    }.onIo()
    val deploySshPort: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DEPLOY_SSH_PORT, profile)] ?: ""
    }
    internal val deploySettingsSnapshot: Flow<DeploySettingsSnapshot> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val inspection = deployInspectionStore.read(prefs, profile)
        DeploySettingsSnapshot(
            profile = profile,
            ip = prefs[getProfileKey(DEPLOY_IP, profile)] ?: "",
            serverName = prefs[getProfileKey(DEPLOY_SERVER_NAME, profile)] ?: "",
            sshLogin = prefs[getProfileKey(DEPLOY_LOGIN, profile)] ?: "",
            sshPassword = readSecret(prefs, DEPLOY_PASSWORD_ENCRYPTED, DEPLOY_PASSWORD, profile),
            webLogin = prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)] ?: "",
            webPassword = readSecret(prefs, DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, profile),
            mainPassword = readSecret(prefs, DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, profile),
            sshPort = prefs[getProfileKey(DEPLOY_SSH_PORT, profile)]
                ?.ifBlank { ShodConstants.Network.DEFAULT_SSH_PORT.toString() }
                ?: ShodConstants.Network.DEFAULT_SSH_PORT.toString(),
            manualPortsEnabled = prefs[getProfileKey(DEPLOY_MANUAL_PORTS, profile)] ?: prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] ?: false,
            serverPeerPort = prefs[getProfileKey(SERVER_PEER_PORT, profile)]
                ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
            serverWebPort = prefs[getProfileKey(SERVER_WEB_PORT, profile)]
                ?: ShodConstants.Network.DEFAULT_SERVER_WEB_PORT,
            sshKeysMode = prefs[getProfileKey(SSH_KEYS_MODE, profile)] ?: false,
            dockerInstall = prefs[getProfileKey(DOCKER_INSTALL, profile)] ?: false,
            secretPathEnabled = true,
            secretPathUuid = prefs[getProfileKey(DEPLOY_SECRET_PATH_UUID, profile)] ?: "",
            serverConfigured = inspection.serverConfigured,
            lastInspectionAtMs = inspection.checkedAtMs,
            lastInspectionOk = inspection.inspectionOk,
            lastInspectionArch = inspection.arch,
            lastInspectionServiceStatus = inspection.serviceStatus,
            lastInspectionVersion = inspection.installedVersion,
            lastInspectionMessage = inspection.message,
            lastPanelAvailable = inspection.panelAvailable,
            sshPrivateKey = readSecret(prefs, SSH_PRIVATE_KEY_ENCRYPTED, SSH_PRIVATE_KEY, profile),
            sshKeyPassphrase = readSecret(prefs, SSH_KEY_PASSPHRASE_ENCRYPTED, SSH_KEY_PASSPHRASE, profile),
            sshCertificate = readSecret(prefs, SSH_CERTIFICATE_ENCRYPTED, SSH_CERTIFICATE, profile),
        )
    }.onIo()
    internal val tunnelAuthSnapshot: Flow<TunnelAuthSnapshot> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val connectionPassword = readConnectionPasswordState(prefs, profile)
        TunnelAuthSnapshot(
            profile = profile,
            connectionPassword = connectionPassword.value,
            connectionPasswordState = connectionPassword.state,
            shodLink = prefs[getProfileKey(Shod_LINK, profile)] ?: "",
            shodLinkMode = prefs[getProfileKey(Shod_LINK_MODE, profile)] ?: false,
        )
    }.onIo()
    val appRoutingAll: Flow<Boolean?> = dataStore.data.map { prefs ->
        prefs[getProfileKey(APP_ROUTING_ALL, prefs[ACTIVE_PROFILE] ?: 0)]
    }

    val appPresetsUsed: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[getProfileKey(APP_PRESETS_USED, prefs[ACTIVE_PROFILE] ?: 0)].orEmpty().split(',').filter(String::isNotBlank).toSet()
    }

    val excludedApps: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(EXCLUDED_APPS, profile)] ?: ""
    }
    val whitelistApps: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(WHITELIST_APPS, profile)] ?: ""
    }

    internal val connectionPasswordState: Flow<StoredSecret> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readConnectionPasswordState(prefs, profile)
    }.onIo()
    val connectionPassword: Flow<String> = connectionPasswordState.map { it.value }
    val deployMainPassword: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readSecret(prefs, DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, profile)
    }.onIo()
    val deployWebLogin: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)] ?: ""
    }
    val deployWebPassword: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        readSecret(prefs, DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, profile)
    }.onIo()
    val serverWebPort: Flow<Int> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SERVER_WEB_PORT, profile)] ?: ShodConstants.Network.DEFAULT_SERVER_WEB_PORT
    }

    val vkAuthMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        when (prefs[getProfileKey(VK_AUTH_MODE, profile)]) {
            ShodConstants.VkAuth.MODE_CAPTCHA -> ShodConstants.VkAuth.MODE_CAPTCHA
            ShodConstants.VkAuth.MODE_AUTO_JS -> ShodConstants.VkAuth.MODE_AUTO_JS
            else -> ShodConstants.VkAuth.MODE_CALLS
        }
    }
    val obfsMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val mode = prefs[getProfileKey(OBFS_MODE, profile)]
        if (mode == "unified") {
            mode
        } else {
            ShodConstants.Tunnel.DEFAULT_OBFS_MODE
        }
    }

    val turnTransport: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        when (prefs[getProfileKey(TURN_TRANSPORT, profile)]) {
            ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS -> ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
            else -> ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT
        }
    }

    val srtpMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        when (prefs[getProfileKey(SRTP_MODE, profile)]) {
            ShodConstants.SrtpMode.WRAP -> ShodConstants.SrtpMode.WRAP
            ShodConstants.SrtpMode.DTLS -> ShodConstants.SrtpMode.DTLS
            else -> ShodConstants.SrtpMode.DEFAULT
        }
    }

    val customTurnIps: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val subnetId = prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)] ?: ""
        if (subnetId.isNotBlank()) {
            val stored = prefs[getProfileKey(CUSTOM_TURN_IPS, profile)]
            if (!stored.isNullOrBlank()) {
                stored
            } else {
                TurnSubnetRegistry.getJoinedIpsForSubnet(subnetId)
            }
        } else {
            ""
        }
    }

    val selectedTurnSubnet: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)] ?: ""
    }

    val turnSubnetIpLimit: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] ?: "ALL"
    }

    val captchaMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(CAPTCHA_MODE, profile)] ?: "auto"
    }
    val captchaSolveMethod: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(CAPTCHA_SOLVE_METHOD, profile)] ?: "auto"
    }
    val captchaWbvSolveMethod: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(CAPTCHA_WBV_SOLVE_METHOD, profile)] ?: "auto"
    }

    val vkHashMode: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        val mode = prefs[getProfileKey(VK_HASH_MODE, profile)]
        when (mode) {
            "auto", ShodConstants.VkAutoHash.MODE_AUTO_API ->
                ShodConstants.VkAutoHash.MODE_AUTO_API
            ShodConstants.VkAutoHash.MODE_AUTO_JS ->
                ShodConstants.VkAutoHash.MODE_AUTO_JS
            else -> ShodConstants.VkAutoHash.MODE_MANUAL
        }
    }
    val vkAccessToken: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        effectiveVkToken(prefs, profile)
    }.onIo()
    val vkAccessTokenUserId: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        if (hasImportedVkToken(prefs, profile)) "" else personalVkAccount(prefs).second
    }

    internal val savedVkAccountIds: Flow<List<String>> = dataStore.data.map { prefs ->
        (savedVkAccounts.available(prefs) + legacyPersonalVkAccounts(prefs).keys).distinct()
    }.onIo()

    val isWhitelist: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(IS_WHITELIST, profile)] ?: false
    }

    val domainRoutingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DOMAIN_ROUTING_ENABLED, profile)] ?: false
    }

    val excludeRuZonesEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(EXCLUDE_RU_ZONES_ENABLED, profile)] ?: false
    }

    val templateDomainsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(TEMPLATE_DOMAINS_ENABLED, profile)] ?: false
    }

    val domainIsWhitelist: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DOMAIN_IS_WHITELIST, profile)] ?: false
    }

    val directDomains: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_DOMAINS, profile)] ?: ""
    }

    val directDomainsBlack: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_DOMAINS_BLACK, profile)]
            ?: prefs[getProfileKey(DIRECT_DOMAINS, profile)]
            ?: ""
    }

    val directDomainsWhite: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_DOMAINS_WHITE, profile)] ?: ""
    }

    val ipRoutingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(IP_ROUTING_ENABLED, profile)] ?: false
    }

    val ipIsWhitelist: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(IP_IS_WHITELIST, profile)] ?: false
    }

    val directIpsBlack: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_IPS_BLACK, profile)] ?: ""
    }

    val directIpsWhite: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(DIRECT_IPS_WHITE, profile)] ?: ""
    }

    val profileNames: Flow<Map<Int, String>> = profileMetadataStore.profileNames

    /** Every profile slot that has a server configured on the deploy screen. */
    internal val deployServers: Flow<List<DeployServerInfo>> = dataStore.data.map { prefs ->
        (ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX).mapNotNull { p ->
            val ip = prefs[getProfileKey(DEPLOY_IP, p)]?.trim().orEmpty()
            if (ip.isEmpty()) return@mapNotNull null
            val inspection = deployInspectionStore.read(prefs, p)
            val configured = inspection.serverConfigured
            val inspectionOk = inspection.inspectionOk
            val serviceStatus = inspection.serviceStatus
            val installedVersion = inspection.installedVersion
            val panelAvailable = inspection.panelAvailable
            val deployed = configured || (
                inspectionOk && (
                    serviceStatus in listOf("systemd_active", "docker_active", "process_running") ||
                        panelAvailable ||
                        (
                            installedVersion.isNotBlank() &&
                                installedVersion != "не определена" &&
                                installedVersion != "unknown"
                            )
                    )
                )
            DeployServerInfo(
                profile = p,
                name = prefs[getProfileKey(DEPLOY_SERVER_NAME, p)]
                    ?.takeIf { it.isNotBlank() }
                    ?: "Сервер ${p + 1}",
                ip = ip,
                configured = deployed,
                installedVersion = installedVersion,
                serviceStatus = serviceStatus,
                panelAvailable = panelAvailable,
            )
        }
    }

    /** Profile slots that hold a peer or a participant link. */
    val occupiedProfiles: Flow<Set<Int>> = profileMetadataStore.occupiedProfiles

    /** Subscription metadata of every slot imported from a subscription. */
    internal val profileSubscriptions: Flow<Map<Int, ProfileSubscriptionInfo>> =
        profileSubscriptionStore.profileSubscriptions

    /** Subscription-level metadata keyed by subscription URL. */
    internal val subscriptionMeta: Flow<Map<String, SubscriptionMeta>> = dataStore.data.map { prefs ->
        SubscriptionMetaCodec.decode(prefs[SUBSCRIPTION_META])
    }

    suspend fun saveProfileName(profile: Int, name: String) {
        profileMetadataStore.saveProfileName(profile, name)
    }

    internal suspend fun readProfileConnectionSettings(profile: Int): ProfileConnectionSnapshot =
        profileConnectionStore.read(profile)

    private fun profileConnectionSettings(prefs: Preferences, profile: Int): ProfileConnectionSnapshot {
            val secret = readConnectionPasswordState(prefs, profile)
            return ProfileConnectionSnapshot(
                revision = prefs[getProfileKey(PROFILE_REVISION, profile)] ?: 0L,
                peer = prefs[getProfileKey(PEER, profile)] ?: "",
                serverPeerPort = prefs[getProfileKey(SERVER_PEER_PORT, profile)]
                    ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT,
                connectionPassword = secret.value,
                connectionPasswordState = secret.state,
                workersPerHash = WorkerCountPolicy.normalize(
                    prefs[getProfileKey(WORKERS_PER_HASH, profile)] ?: 18,
                ),
                extraWorkers = prefs[getProfileKey(EXTRA_WORKERS, profile)] ?: false,
                shodLink = prefs[getProfileKey(Shod_LINK, profile)] ?: "",
                shodLinkMode = prefs[getProfileKey(Shod_LINK_MODE, profile)] ?: false,
                subscriptionUrl = prefs[getProfileKey(SUBSCRIPTION_URL, profile)] ?: "",
                subscriptionTitle = prefs[getProfileKey(SUBSCRIPTION_TITLE, profile)] ?: "",
                subscriptionPower = prefs[getProfileKey(SUBSCRIPTION_POWER, profile)] ?: 0,
            )
    }

    internal val tunnelScreenSnapshot: Flow<TunnelScreenSnapshot> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        TunnelScreenSnapshot(
            profile = profile,
            name = prefs[getProfileKey(PROFILE_NAME, profile)] ?: defaultProfileName(profile),
            connection = profileConnectionSettings(prefs, profile),
            hashes = prefs[getProfileKey(VK_HASHES, profile)].orEmpty(),
            hashMode = when (prefs[getProfileKey(VK_HASH_MODE, profile)]) {
                "auto", ShodConstants.VkAutoHash.MODE_AUTO_API -> ShodConstants.VkAutoHash.MODE_AUTO_API
                ShodConstants.VkAutoHash.MODE_AUTO_JS -> ShodConstants.VkAutoHash.MODE_AUTO_JS
                else -> ShodConstants.VkAutoHash.MODE_MANUAL
            },
            authMode = when (prefs[getProfileKey(VK_AUTH_MODE, profile)]) {
                ShodConstants.VkAuth.MODE_CAPTCHA -> ShodConstants.VkAuth.MODE_CAPTCHA
                ShodConstants.VkAuth.MODE_AUTO_JS -> ShodConstants.VkAuth.MODE_AUTO_JS
                else -> ShodConstants.VkAuth.MODE_CALLS
            },
            token = effectiveVkToken(prefs, profile),
            srtp = when (prefs[getProfileKey(SRTP_MODE, profile)]) {
                ShodConstants.SrtpMode.WRAP -> ShodConstants.SrtpMode.WRAP
                ShodConstants.SrtpMode.DTLS -> ShodConstants.SrtpMode.DTLS
                else -> ShodConstants.SrtpMode.DEFAULT
            },
            transport = when (prefs[getProfileKey(TURN_TRANSPORT, profile)]) {
                ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS -> ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
                else -> ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT
            },
            subnet = prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)].orEmpty(),
            ipLimit = prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] ?: "ALL",
            hashResults = prefs[getProfileKey(VK_HASH_CHECK_RESULTS, profile)] ?: "{}",
            removedOwnerHashes = prefs[getProfileKey(LINK_HASHES_REMOVED, profile)].orEmpty().split(',').filter(String::isNotBlank).toSet(),
            autoJsAcknowledged = prefs[AUTO_JS_RISK_ACKNOWLEDGED] ?: false,
            tcpAcknowledged = prefs[TCP_TRANSPORT_RISK_ACKNOWLEDGED] ?: false,
        )
    }.onIo()

    suspend fun saveProfileConnectionSettings(
        profile: Int,
        peer: String,
        serverPeerPort: Int,
        connectionPassword: String,
        workersPerHash: Int,
        expectedRevision: Long? = null,
    ) {
        profileConnectionStore.save(
            profile = profile,
            peer = peer,
            serverPeerPort = serverPeerPort,
            connectionPassword = connectionPassword,
            workersPerHash = workersPerHash,
            expectedRevision = expectedRevision,
        )
    }

    suspend fun saveProfileParticipantLink(profile: Int, link: String) {
        dataStore.edit { prefs ->
            prefs[getProfileKey(Shod_LINK, profile)] = link.trim()
            prefs.remove(getProfileKey(LINK_HASHES_REMOVED, profile))
        }
    }

    suspend fun saveProfileParticipantMode(profile: Int, enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[getProfileKey(Shod_LINK_MODE, profile)] = enabled
        }
    }

    /**
     * Materializes an imported shod:// link into profile fields: peer/port, tunnel password,
     * VK token, worker limit and name. Hash links keep their participant source separately so
     * owner hashes remain hidden; links without hashes use the materialized connection fields.
     */
    /** False, and nothing written, without a host, a valid port or a tunnel password. */
    internal suspend fun saveImportedConnection(profile: Int, imported: ImportedConnectionSettings, participantLink: String? = null): Boolean {
        val host = imported.host.trim().removePrefix("[").removeSuffix("]")
        if (host.isEmpty() || imported.port !in 1..65535 || imported.password.isBlank()) return false
        dataStore.edit { prefs ->
            prefs[getProfileKey(PROFILE_REMOVED, profile)] = false
            prefs[getProfileKey(PROFILE_REVISION, profile)] = (prefs[getProfileKey(PROFILE_REVISION, profile)] ?: 0L) + 1L
            PingResultsCache.removeProfile(profile)
            prefs[getProfileKey(PEER, profile)] =
                if (host.contains(':')) "[$host]:${imported.port}" else host
            prefs[getProfileKey(SERVER_PEER_PORT, profile)] = imported.port

            val localKey = getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)
            prefs[localKey] = imported.password
            prefs.remove(getProfileKey(CONNECTION_PASSWORD, profile))
            prefs.remove(getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile))

            prefs[getProfileKey(Shod_LINK_MODE, profile)] = participantLink != null
            if (participantLink != null) prefs[getProfileKey(Shod_LINK, profile)] = participantLink
            else prefs.remove(getProfileKey(Shod_LINK, profile))
            prefs.remove(getProfileKey(LINK_HASHES_REMOVED, profile))
            prefs.remove(getProfileKey(SUBSCRIPTION_URL, profile))
            prefs.remove(getProfileKey(SUBSCRIPTION_TITLE, profile))
            prefs.remove(getProfileKey(SUBSCRIPTION_UUID, profile))
            prefs.remove(getProfileKey(SUBSCRIPTION_NODE, profile))
            prefs.remove(getProfileKey(SUBSCRIPTION_POWER, profile))

            val power = imported.power?.takeIf { it > 0 }?.coerceIn(10, 100)
            if (power != null) {
                prefs[getProfileKey(WORKERS_PER_HASH, profile)] =
                    WorkerCountPolicy.normalize(power, maximum = ShodConstants.Tunnel.MAX_WORKERS)
            }

            val hashes = imported.hashes
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (hashes.isNotEmpty()) {
                prefs[getProfileKey(VK_HASHES, profile)] = if (participantLink == null) hashes.joinToString(",") else ""
                prefs[getProfileKey(VK_HASH_MODE, profile)] = ShodConstants.VkAutoHash.MODE_MANUAL
                prefs[getProfileKey(VK_AUTH_MODE, profile)] = ShodConstants.VkAuth.MODE_CALLS
            }

            if (imported.vkAccessToken.isNotBlank()) {
                prefs.putSecret(VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, imported.vkAccessToken, profile)
                prefs[getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile)] = ""
                prefs[getProfileKey(VK_AUTH_MODE, profile)] = ShodConstants.VkAuth.MODE_CALLS
                prefs[getProfileKey(VK_HASH_MODE, profile)] = ShodConstants.VkAutoHash.MODE_AUTO_API
            }

            val name = imported.name?.trim().orEmpty()
            if (name.isEmpty() || name == defaultProfileName(profile)) {
                prefs.remove(getProfileKey(PROFILE_NAME, profile))
            } else {
                prefs[getProfileKey(PROFILE_NAME, profile)] = name
            }
        }
        return true
    }

    suspend fun saveProfileWorkersPerHash(profile: Int, workersPerHash: Int) {
        profileConnectionStore.saveWorkersPerHash(profile, workersPerHash)
    }

    /**
     * Writes subscription nodes into profile slots as owner profiles.
     * Peer and password come from the decrypted link; power caps the worker count.
     */
    internal suspend fun applySubscriptionImport(writes: List<SubscriptionProfileWrite>) {
        if (writes.isEmpty()) return
        dataStore.edit { prefs ->
            for (write in writes) {
                val profile = write.profile
                val host = write.host.trim().removePrefix("[").removeSuffix("]")
                if (host.isEmpty() || write.port !in 1..65535) continue

                prefs[getProfileKey(PEER, profile)] =
                    if (host.contains(':')) "[$host]:${write.port}" else host
                prefs[getProfileKey(SERVER_PEER_PORT, profile)] = write.port
                prefs[getProfileKey(Shod_LINK_MODE, profile)] = false
                prefs.remove(getProfileKey(Shod_LINK, profile))

                val localKey = getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)
                prefs[localKey] = write.password
                prefs.remove(getProfileKey(CONNECTION_PASSWORD, profile))
                prefs.remove(getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile))

                val power = write.power?.coerceIn(1, ShodConstants.Tunnel.MAX_WORKERS)
                val storedWorkers = prefs[getProfileKey(WORKERS_PER_HASH, profile)]
                val targetWorkers = when {
                    power == null -> storedWorkers ?: 10
                    storedWorkers == null -> power
                    else -> storedWorkers.coerceAtMost(power)
                }
                prefs[getProfileKey(WORKERS_PER_HASH, profile)] = WorkerCountPolicy.normalize(
                    targetWorkers,
                    maximum = power ?: ShodConstants.Tunnel.MAX_WORKERS,
                )

                val title = write.title.trim()
                val nodeName = write.nodeName.trim()
                val previousNode = prefs[getProfileKey(SUBSCRIPTION_NODE, profile)].orEmpty()
                val currentName = prefs[getProfileKey(PROFILE_NAME, profile)]
                val defaultName = defaultProfileName(profile)
                val renameAllowed = currentName.isNullOrBlank() ||
                    currentName == defaultName ||
                    currentName == previousNode
                if (renameAllowed) {
                    if (nodeName.isEmpty() || nodeName == defaultName) {
                        prefs.remove(getProfileKey(PROFILE_NAME, profile))
                    } else {
                        prefs[getProfileKey(PROFILE_NAME, profile)] = nodeName
                    }
                }

                prefs[getProfileKey(SUBSCRIPTION_URL, profile)] = write.url.trim()
                if (title.isEmpty()) {
                    prefs.remove(getProfileKey(SUBSCRIPTION_TITLE, profile))
                } else {
                    prefs[getProfileKey(SUBSCRIPTION_TITLE, profile)] = title
                }
                if (write.subUuid.isNullOrBlank()) {
                    prefs.remove(getProfileKey(SUBSCRIPTION_UUID, profile))
                } else {
                    prefs[getProfileKey(SUBSCRIPTION_UUID, profile)] = write.subUuid
                }
                prefs[getProfileKey(SUBSCRIPTION_NODE, profile)] = nodeName
                if (power == null) {
                    prefs.remove(getProfileKey(SUBSCRIPTION_POWER, profile))
                } else {
                    prefs[getProfileKey(SUBSCRIPTION_POWER, profile)] = power
                }

                if (write.hashes.isNotEmpty()) {
                    prefs[getProfileKey(VK_HASHES, profile)] = write.hashes.joinToString(",")
                }
            }
        }
    }

    /** A profile slot is considered free when it has neither a peer nor a participant link. */
    internal suspend fun findFirstFreeProfile(): Int? =
        profileMetadataStore.findFirstFreeProfile()

    /** A slot is free for a new server only when it has no connection and no deploy IP. */
    internal suspend fun findFirstFreeDeployProfile(): Int? =
        profileMetadataStore.findFirstFreeDeployProfile()

    /** Wipes every connection-related key of a profile slot, making it free again. */
    suspend fun clearProfile(profile: Int) {
        profileLifecycleStore.clearProfile(profile)
    }

    /** Wipes all profile slots associated with the given subscription URL. */
    suspend fun clearSubscription(url: String) {
        profileSubscriptionStore.clearSubscription(url)
    }

    internal suspend fun recordSubscriptionImport(
        url: String,
        title: String,
        allowHttp: Boolean,
    ) {
        val key = url.trim()
        if (key.isEmpty()) return
        dataStore.edit { prefs ->
            val meta = SubscriptionMetaCodec.decode(prefs[SUBSCRIPTION_META]).toMutableMap()
            val existing = meta[key]
            meta[key] = SubscriptionMeta(
                url = key,
                title = existing?.title?.takeIf { it.isNotBlank() } ?: title.trim(),
                autoUpdateMinutes = existing?.autoUpdateMinutes ?: 0,
                lastUpdatedAt = existing?.lastUpdatedAt ?: 0L,
                lastAttemptAt = existing?.lastAttemptAt ?: 0L,
                allowHttp = allowHttp || existing?.allowHttp == true,
            )
            prefs[SUBSCRIPTION_META] = SubscriptionMetaCodec.encode(meta)
        }
    }

    internal suspend fun markSubscriptionAttempt(
        url: String,
        at: Long = System.currentTimeMillis(),
    ) {
        updateSubscriptionMeta(url) { meta ->
            meta.copy(lastAttemptAt = at)
        }
    }

    internal suspend fun markSubscriptionUpdated(
        url: String,
        title: String? = null,
        at: Long = System.currentTimeMillis(),
    ) {
        updateSubscriptionMeta(url) { meta ->
            val resolvedTitle = meta.title.takeIf { it.isNotBlank() }
                ?: title?.trim().orEmpty()
            meta.copy(
                title = resolvedTitle,
                lastUpdatedAt = at,
                lastAttemptAt = at,
            )
        }
    }

    internal suspend fun saveSubscriptionAutoUpdate(url: String, minutes: Int) {
        updateSubscriptionMeta(url) { meta ->
            meta.copy(autoUpdateMinutes = minutes.coerceAtLeast(0))
        }
    }

    internal suspend fun renameSubscription(url: String, oldTitle: String, newTitle: String) {
        val key = url.trim()
        val title = newTitle.trim()
        if (key.isEmpty()) return
        dataStore.edit { prefs ->
            val meta = SubscriptionMetaCodec.decode(prefs[SUBSCRIPTION_META]).toMutableMap()
            val existing = meta[key] ?: SubscriptionMeta(url = key, title = oldTitle)
            meta[key] = existing.copy(url = key, title = title)
            prefs[SUBSCRIPTION_META] = SubscriptionMetaCodec.encode(meta)

            for (profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                if (prefs[getProfileKey(SUBSCRIPTION_URL, profile)].orEmpty() != key) continue
                if (title.isEmpty()) {
                    prefs.remove(getProfileKey(SUBSCRIPTION_TITLE, profile))
                } else {
                    prefs[getProfileKey(SUBSCRIPTION_TITLE, profile)] = title
                }
                val profileName = prefs[getProfileKey(PROFILE_NAME, profile)]
                if (profileName == oldTitle) {
                    if (title.isEmpty()) {
                        prefs.remove(getProfileKey(PROFILE_NAME, profile))
                    } else {
                        prefs[getProfileKey(PROFILE_NAME, profile)] = title
                    }
                }
            }
        }
    }

    internal suspend fun changeSubscriptionUrl(oldUrl: String, newUrl: String) {
        val from = oldUrl.trim()
        val to = newUrl.trim()
        if (from.isEmpty() || to.isEmpty() || from == to) return
        dataStore.edit { prefs ->
            val meta = SubscriptionMetaCodec.decode(prefs[SUBSCRIPTION_META]).toMutableMap()
            val moved = meta.remove(from) ?: SubscriptionMeta(url = to)
            meta[to] = moved.copy(url = to)
            prefs[SUBSCRIPTION_META] = SubscriptionMetaCodec.encode(meta)

            for (profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                if (prefs[getProfileKey(SUBSCRIPTION_URL, profile)].orEmpty() == from) {
                    prefs[getProfileKey(SUBSCRIPTION_URL, profile)] = to
                }
            }
        }
    }

    internal suspend fun deleteSubscriptionMeta(url: String) {
        val key = url.trim()
        if (key.isEmpty()) return
        dataStore.edit { prefs ->
            val meta = SubscriptionMetaCodec.decode(prefs[SUBSCRIPTION_META]).toMutableMap()
            if (meta.remove(key) != null) {
                prefs[SUBSCRIPTION_META] = SubscriptionMetaCodec.encode(meta)
            }
        }
    }

    private suspend fun updateSubscriptionMeta(
        url: String,
        transform: (SubscriptionMeta) -> SubscriptionMeta,
    ) {
        val key = url.trim()
        if (key.isEmpty()) return
        dataStore.edit { prefs ->
            val meta = SubscriptionMetaCodec.decode(prefs[SUBSCRIPTION_META]).toMutableMap()
            meta[key] = transform(meta[key] ?: SubscriptionMeta(url = key))
            prefs[SUBSCRIPTION_META] = SubscriptionMetaCodec.encode(meta)
        }
    }

    val linkHashesRemoved: Flow<Set<String>> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        (prefs[getProfileKey(LINK_HASHES_REMOVED, profile)] ?: "").split(",").filter(String::isNotBlank).toSet()
    }

    suspend fun saveLinkHashesRemoved(removed: Set<String>, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: (prefs[ACTIVE_PROFILE] ?: 0)
            if (removed.isEmpty()) {
                prefs.remove(getProfileKey(LINK_HASHES_REMOVED, profile))
            } else {
                prefs[getProfileKey(LINK_HASHES_REMOVED, profile)] = removed.joinToString(",")
            }
        }
    }

    val extraWorkers: Flow<Boolean> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(EXTRA_WORKERS, profile)] ?: false
    }
    val autoJsRiskAcknowledged: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_JS_RISK_ACKNOWLEDGED] ?: false
    }
    val tcpTransportRiskAcknowledged: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[TCP_TRANSPORT_RISK_ACKNOWLEDGED] ?: false
    }
    val autoPauseOnWifi: Flow<Boolean> = dataStore.data.map { prefs ->
        WifiPreferenceStore.read(prefs).enabled
    }
    internal val wifiPreferences: Flow<WifiPreferences> = dataStore.data.map(WifiPreferenceStore::read).distinctUntilChanged()
    internal suspend fun rememberWifiNetwork(ssid: String, expectedMode: WifiNetworkListMode? = null,
        requireEnabled: Boolean = false): Boolean {
        var applied = false
        dataStore.edit { applied = WifiPreferenceStore.remember(it, ssid, expectedMode, requireEnabled) }
        return applied
    }
    internal suspend fun selectWifiNetwork(ssid: String, selected: Boolean) { dataStore.edit { WifiPreferenceStore.select(it, ssid, selected) } }
    internal suspend fun forgetWifiNetwork(ssid: String) { dataStore.edit { WifiPreferenceStore.forget(it, ssid) } }
    val wifiNetworkListMode: Flow<String> = dataStore.data.map { prefs ->
        WifiPreferenceStore.read(prefs).mode.storedValue
    }
    val wifiNetworkListSsids: Flow<String> = dataStore.data.map { prefs ->
        prefs[WifiPreferenceStore.selectedKey] ?: ""
    }
    suspend fun isBatteryOptimizationPromptHandled(): Boolean =
        batteryOptimizationPromptStore.isHandled()

    val themeMode: Flow<String> = themePreferencesStore.themeMode
    val isDynamicColor: Flow<Boolean> = themePreferencesStore.isDynamicColor
    val themePalette: Flow<String> = themePreferencesStore.themePalette

    val selectedFingerprint: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(SELECTED_FINGERPRINT, profile)] ?: "firefox"
    }
    val activeClientIds: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(ACTIVE_CLIENT_IDS, profile)] ?: ShodConstants.Tunnel.DEFAULT_CLIENT_IDS
    }
    val clientIdCheckResults: Flow<String> = dataStore.data.map { prefs ->
        prefs[CLIENT_ID_CHECK_RESULTS] ?: "{}"
    }
    val vkHashCheckResults: Flow<String> = dataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: 0
        prefs[getProfileKey(VK_HASH_CHECK_RESULTS, profile)] ?: "{}"
    }
    val connectionGeneration: Flow<Long> = dataStore.data.map { prefs ->
        prefs[CONNECTION_GENERATION] ?: 0L
    }

    val tunnelWasRunning: Flow<Boolean> = tunnelRuntimePreferencesStore.wasRunning
    val updateLastCheckAt: Flow<Long> = updatePreferencesStore.lastCheckAt
    val updateLatestVersion: Flow<String> = updatePreferencesStore.latestVersion
    val updateLastError: Flow<String> = updatePreferencesStore.lastError
    val updateCheckIntervalHours: Flow<Int> = updatePreferencesStore.checkIntervalHours
    val updatePostponeUntil: Flow<Long> = updatePreferencesStore.postponeUntil
    val updatePostponeVersion: Flow<String> = updatePreferencesStore.postponeVersion

    // Last in the class, after every property: Kotlin assigns them top to bottom, and the collectors below start on
    // other threads at once. Above the flows they read, a collector could run before its flow was assigned and call
    // collect on null, which crashed the app at launch on slower phones (Redmi).
    init {
        if (readOnlyProfile == null && migrationsStarted.compareAndSet(false, true)) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                migrateDefaultRole()
                migrateConnectionPasswordsToLocalStorage()
                migrateSecretsToKeystore()
                migrateLegacyWhitelistMode()
                migrateSubscriptionMeta()
                migrateDeployServerNames()
            }
        }
        if (readOnlyProfile == null && uiCacheStarted.compareAndSet(false, true)) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                launch { vkHashMode.collect { cachedVkHashMode = it } }
                launch { vkAccessToken.collect { cachedVkAccessToken = it } }
                launch { obfsMode.collect { cachedObfsMode = it } }
                launch { turnTransport.collect { cachedTurnTransport = it } }
                launch { srtpMode.collect { cachedSrtpMode = it } }
                launch { shodLinkMode.collect { cachedShodLinkMode = it } }
                launch { workersPerHash.collect { cachedWorkersPerHash = it } }
                launch { manualPortsEnabled.collect { cachedManualPortsEnabled = it } }
                launch { serverPeerPort.collect { cachedServerPeerPort = it } }
                launch { extraWorkers.collect { cachedExtraWorkers = it } }
                launch { customTurnIps.collect { cachedCustomTurnIps = it } }
                launch { excludedApps.collect { cachedExcludedApps = it } }
                launch { whitelistApps.collect { cachedWhitelistApps = it } }
                launch { isWhitelist.collect { cachedIsWhitelist = it } }
                launch { domainRoutingEnabled.collect { cachedDomainRoutingEnabled = it } }
                launch { domainIsWhitelist.collect { cachedDomainIsWhitelist = it } }
                launch { excludeRuZonesEnabled.collect { cachedExcludeRuZonesEnabled = it } }
                launch { templateDomainsEnabled.collect { cachedTemplateDomainsEnabled = it } }
                launch { directDomains.collect { cachedDirectDomains = it } }
                launch { directDomainsBlack.collect { cachedDirectDomainsBlack = it } }
                launch { directDomainsWhite.collect { cachedDirectDomainsWhite = it } }
                launch { ipRoutingEnabled.collect { cachedIpRoutingEnabled = it } }
                launch { ipIsWhitelist.collect { cachedIpIsWhitelist = it } }
                launch { directIpsBlack.collect { cachedDirectIpsBlack = it } }
                launch { directIpsWhite.collect { cachedDirectIpsWhite = it } }
                launch { deploySettingsSnapshot.collect { cachedDeploySnapshot = it } }
            }
        }
    }

    suspend fun saveTunnelWasRunning(running: Boolean) {
        tunnelRuntimePreferencesStore.saveWasRunning(running)
    }

    suspend fun saveThemeMode(mode: String) {
        themePreferencesStore.saveThemeMode(mode)
    }


    suspend fun saveDynamicColor(enabled: Boolean) {
        themePreferencesStore.saveDynamicColor(enabled)
    }

    suspend fun saveThemePalette(palette: String) {
        themePreferencesStore.saveThemePalette(palette)
    }

    suspend fun saveFingerprint(fingerprint: String, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: (prefs[ACTIVE_PROFILE] ?: 0)
            prefs[getProfileKey(SELECTED_FINGERPRINT, profile)] = fingerprint
        }
    }

    suspend fun saveActiveClientIds(clientIds: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(ACTIVE_CLIENT_IDS, profile)] = clientIds
        }
    }

    suspend fun saveClientIdCheckResults(resultsJson: String) {
        dataStore.edit { prefs ->
            prefs[CLIENT_ID_CHECK_RESULTS] = resultsJson
        }
    }

    suspend fun saveVkHashCheckResults(resultsJson: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(VK_HASH_CHECK_RESULTS, profile)] = resultsJson
        }
    }

    suspend fun invalidateVkHash(hash: String) {
        val normalized = hash.trim()
        if (normalized.isEmpty()) return
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val key = getProfileKey(VK_HASH_CHECK_RESULTS, profile)
            val results = VkHashValidationCodec.decode(prefs[key] ?: "{}") +
                (normalized to VkHashValidationStatus.Invalid)
            prefs[key] = VkHashValidationCodec.encode(results)
        }
    }

    suspend fun reserveConnectionGeneration(
        proposed: Long = 0L,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): Long {
        var reserved = 0L
        dataStore.edit { prefs ->
            reserved = reserveTunnelGenerationId(
                nowSeconds.coerceAtLeast(0L),
                proposed.coerceAtLeast(0L),
                prefs[CONNECTION_GENERATION] ?: 0L,
            )
            prefs[CONNECTION_GENERATION] = reserved
        }
        return reserved
    }

    suspend fun recordConnectionGeneration(generation: Long): Long {
        var persisted = 0L
        dataStore.edit { prefs ->
            persisted = maxOf(prefs[CONNECTION_GENERATION] ?: 0L, generation.coerceAtLeast(0L))
            prefs[CONNECTION_GENERATION] = persisted
        }
        return persisted
    }

    suspend fun saveTunnelDiagnostic(code: String, message: String) {
        tunnelRuntimePreferencesStore.saveDiagnostic(code, message)
    }

    suspend fun clearTunnelDiagnostic() {
        tunnelRuntimePreferencesStore.clearDiagnostic()
    }

    suspend fun saveUpdateState(lastCheckAt: Long, latestVersion: String, error: String) {
        updatePreferencesStore.saveState(lastCheckAt, latestVersion, error)
    }

    suspend fun saveUpdateCheckIntervalHours(hours: Int) {
        updatePreferencesStore.saveCheckIntervalHours(hours)
    }

    suspend fun saveUpdatePostpone(version: String, until: Long) {
        updatePreferencesStore.savePostpone(version, until)
    }

    suspend fun saveUpdateDialogShown(version: String, shownAt: Long) {
        updatePreferencesStore.saveDialogShown(version, shownAt)
    }

    suspend fun saveUpdateDialogAction(version: String, action: String, actedAt: Long) {
        updatePreferencesStore.saveDialogAction(version, action, actedAt)
    }

    suspend fun saveActiveProfile(profile: Int) {
        profileCursorStore.saveActiveProfile(profile)
    }

    suspend fun saveShowSystemApps(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_SYSTEM_APPS] = enabled
        }
    }

    suspend fun saveLoggingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[LOGGING_ENABLED] = enabled
        }
    }

    suspend fun saveInfoActionsExpanded(expanded: Boolean) {
        dataStore.edit { prefs ->
            prefs[INFO_ACTIONS_EXPANDED] = expanded
        }
    }

    suspend fun saveInfoProjectExpanded(expanded: Boolean) {
        dataStore.edit { prefs ->
            prefs[INFO_PROJECT_EXPANDED] = expanded
        }
    }

    suspend fun saveShodLink(link: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(Shod_LINK, profile)] = link
        }
    }

    suspend fun saveShodLinkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(Shod_LINK_MODE, profile)] = enabled
        }
    }

    suspend fun saveShodParticipantLink(link: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(Shod_LINK, profile)] = link
            prefs[getProfileKey(Shod_LINK_MODE, profile)] = true
            // a new link from the owner (QR, shod:// link) starts with all of its hashes in use
            prefs.remove(getProfileKey(LINK_HASHES_REMOVED, profile))
        }
    }

    suspend fun saveParticipantShowLogs(show: Boolean, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: (prefs[ACTIVE_PROFILE] ?: 0)
            prefs[getProfileKey(PARTICIPANT_SHOW_LOGS, profile)] = show
        }
    }

    /** Manual worker count is a global custom profile, independent of the selected server. */
    suspend fun saveWorkersPerHash(workersPerHash: Int) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val normalized = WorkerCountPolicy.normalize(
                workersPerHash,
                maximum = workersMaximum(prefs),
            )
            prefs[WORKER_PROFILE_MODE] = "custom"
            prefs[WORKER_PROFILE_CUSTOM] = normalized
            // Keep the legacy slot value in sync for older app versions and backup compatibility.
            prefs[getProfileKey(WORKERS_PER_HASH, profile)] = normalized
        }
    }

    suspend fun saveWorkerProfileMode(mode: String) {
        require(mode in setOf("economy", "balanced", "maximum"))
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val maximum = workersMaximum(prefs)
            val workers = when (mode) {
                "economy" -> WorkerCountPolicy.normalize(10, maximum)
                "balanced" -> WorkerCountPolicy.normalize(20, maximum)
                else -> maximum
            }
            prefs[WORKER_PROFILE_MODE] = mode
            prefs[getProfileKey(WORKERS_PER_HASH, profile)] = workers
        }
    }

    suspend fun saveParticipantTunnelSettings(vkHashes: String, workersPerHash: Int) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val normalized = WorkerCountPolicy.normalize(
                workersPerHash,
                maximum = workersMaximum(prefs),
            )
            prefs[getProfileKey(VK_HASHES, profile)] = vkHashes
            prefs[getProfileKey(WORKERS_PER_HASH, profile)] = normalized
        }
    }

    suspend fun save(
        peer: String,
        vkHashes: String,
        secondaryVkHash: String,
        workersPerHash: Int,
        protocol: String,
        listenPort: Int,
        sni: String = "",
        noDns: Boolean = false
    ) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(PEER, profile)] = peer
            prefs[getProfileKey(VK_HASHES, profile)] = vkHashes
            prefs[getProfileKey(SECONDARY_VK_HASH, profile)] = secondaryVkHash
            val normalizedWorkers = WorkerCountPolicy.normalize(
                workersPerHash,
                maximum = workersMaximum(prefs),
            )
            prefs[getProfileKey(WORKERS_PER_HASH, profile)] = normalizedWorkers
            prefs[getProfileKey(PROTOCOL, profile)] = protocol
            prefs[getProfileKey(LISTEN_PORT, profile)] = listenPort
            prefs[getProfileKey(SNI, profile)] = sni
            prefs[getProfileKey(NO_DNS, profile)] = noDns
        }
    }

    suspend fun saveManualPortsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] = enabled
            // The SSH port is the server's own setting, not one of the manual ports: it stays as saved.
            if (!enabled) {
                prefs[getProfileKey(SERVER_PEER_PORT, profile)] = ShodConstants.Network.DEFAULT_SERVER_PEER_PORT
                prefs[getProfileKey(SERVER_WEB_PORT, profile)] = ShodConstants.Network.DEFAULT_SERVER_WEB_PORT
            }
        }
    }

    suspend fun saveVkHashesOnly(vkHashes: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(VK_HASHES, profile)] = vkHashes
        }
    }

    suspend fun saveSshKeysMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(SSH_KEYS_MODE, profile)] = enabled
        }
    }

    // After a successful deploy the profile points at the server it installed or updated: its ports are written whatever
    // they are (a profile that last deployed a second server on 46100 and now updates the one on 46000 must dial 46000),
    // ports other than 46000/46002 turn "Ручные порты" on — the connection dials serverPeerPort only then — and an empty
    // server address becomes the host. Nothing changes before a deploy succeeds.
    suspend fun saveDeployedServer(profile: Int, host: String, peerPort: Int, webPort: Int) {
        dataStore.edit { prefs ->
            val connectionProfile = prefs[getProfileKey(DEPLOY_CONNECTION_PROFILE, profile)] ?: profile
            // the profile that started the deploy, even if another is active by the time it finished
            if (prefs[getProfileKey(PROFILE_REMOVED, connectionProfile)] != true && (prefs[getProfileKey(PEER, connectionProfile)] ?: "").isBlank() && host.isNotBlank()) {
                prefs[getProfileKey(PEER, connectionProfile)] = host
            }
            prefs[getProfileKey(SERVER_PEER_PORT, connectionProfile)] = peerPort
            prefs[getProfileKey(MANUAL_PORTS_ENABLED, connectionProfile)] = true
            prefs[getProfileKey(SERVER_PEER_PORT, profile)] = peerPort
            prefs[getProfileKey(SERVER_WEB_PORT, profile)] = webPort
            if (peerPort != ShodConstants.Network.DEFAULT_SERVER_PEER_PORT || webPort != ShodConstants.Network.DEFAULT_SERVER_WEB_PORT) {
                prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] = true
            }
        }
    }

    suspend fun saveDockerInstall(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DOCKER_INSTALL, profile)] = enabled
        }
    }

    suspend fun saveDeploySecretPath(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val keyEnabled = getProfileKey(DEPLOY_SECRET_PATH_ENABLED, profile)
            val keyUuid = getProfileKey(DEPLOY_SECRET_PATH_UUID, profile)
            prefs[keyEnabled] = enabled
            if (enabled && prefs[keyUuid].isNullOrBlank()) {
                prefs[keyUuid] = UUID.randomUUID().toString()
            }
        }
    }

    suspend fun regenerateDeploySecretPathUuid(): String {
        val newUuid = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DEPLOY_SECRET_PATH_UUID, profile)] = newUuid
        }
        return newUuid
    }

    /** Persists a panel secret path that has already been applied and verified on the server. */
    suspend fun saveDeploySecretPathUuid(uuid: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DEPLOY_SECRET_PATH_UUID, profile)] = uuid
        }
    }

    /** Removes every stored deploy setting and secret for the active profile. */
    suspend fun clearDeployProfile(forProfile: Int? = null) {
        val profile = forProfile ?: activeProfile.first()
        dataStore.edit { prefs ->
            val hasConnection = !prefs[getProfileKey(PEER, profile)].isNullOrBlank() || !prefs[getProfileKey(Shod_LINK, profile)].isNullOrBlank()
            prefs.remove(getProfileKey(DEPLOY_IP, profile))
            prefs.remove(getProfileKey(DEPLOY_SERVER_NAME, profile))
            prefs.remove(getProfileKey(DEPLOY_LOGIN, profile))
            prefs.remove(getProfileKey(DEPLOY_CONNECTION_PROFILE, profile))
            prefs.remove(getProfileKey(DEPLOY_PASSWORD, profile))
            prefs.remove(getProfileKey(DEPLOY_PASSWORD_ENCRYPTED, profile))
            prefs.remove(getProfileKey(DEPLOY_MAIN_PASSWORD, profile))
            prefs.remove(getProfileKey(DEPLOY_MAIN_PASSWORD_ENCRYPTED, profile))
            prefs.remove(getProfileKey(DEPLOY_WEB_LOGIN, profile))
            prefs.remove(getProfileKey(DEPLOY_WEB_PASSWORD, profile))
            prefs.remove(getProfileKey(DEPLOY_WEB_PASSWORD_ENCRYPTED, profile))
            prefs.remove(getProfileKey(DEPLOY_SSH_PORT, profile))
            prefs.remove(getProfileKey(DEPLOY_MANUAL_PORTS, profile))
            if (!hasConnection) prefs.remove(getProfileKey(SERVER_PEER_PORT, profile))
            prefs.remove(getProfileKey(SERVER_WEB_PORT, profile))
            if (!hasConnection) prefs.remove(getProfileKey(MANUAL_PORTS_ENABLED, profile))
            prefs.remove(getProfileKey(SSH_KEYS_MODE, profile))
            prefs.remove(getProfileKey(DOCKER_INSTALL, profile))
            prefs.remove(getProfileKey(DEPLOY_SECRET_PATH_ENABLED, profile))
            prefs.remove(getProfileKey(DEPLOY_SECRET_PATH_UUID, profile))
            deployInspectionStore.clearServerConfigured(prefs, profile)
            deployInspectionStore.clearInspection(prefs, profile)
            prefs.remove(getProfileKey(SSH_PRIVATE_KEY, profile))
            prefs.remove(getProfileKey(SSH_PRIVATE_KEY_ENCRYPTED, profile))
            prefs.remove(getProfileKey(SSH_KEY_PASSPHRASE, profile))
            prefs.remove(getProfileKey(SSH_KEY_PASSPHRASE_ENCRYPTED, profile))
            prefs.remove(getProfileKey(SSH_CERTIFICATE, profile))
            prefs.remove(getProfileKey(SSH_CERTIFICATE_ENCRYPTED, profile))
            if (!hasConnection) {
                prefs.remove(getProfileKey(CONNECTION_PASSWORD_LOCAL, profile))
                prefs.remove(getProfileKey(CONNECTION_PASSWORD, profile))
                prefs.remove(getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile))
            }
        }
    }

    suspend fun saveSshKeys(privateKey: String, keyPassphrase: String, certificate: String, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: prefs[ACTIVE_PROFILE] ?: 0
            prefs.putSecret(SSH_PRIVATE_KEY_ENCRYPTED, SSH_PRIVATE_KEY, privateKey, profile)
            prefs.putSecret(SSH_KEY_PASSPHRASE_ENCRYPTED, SSH_KEY_PASSPHRASE, keyPassphrase, profile)
            prefs.putSecret(SSH_CERTIFICATE_ENCRYPTED, SSH_CERTIFICATE, certificate, profile)
        }
    }

    internal suspend fun ensureDeployProfileDefaults(profile: Int) {
        dataStore.edit { prefs ->
            if (deployInspectionStore.read(prefs, profile).serverConfigured) return@edit
            if (readSecret(prefs, DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, profile).isBlank())
                prefs.putSecret(DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, com.shod.client.ui.utils.generate16CharPassword(), profile)
            if (prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)].isNullOrBlank()) prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)] = "admin"
            if (readSecret(prefs, DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, profile).isBlank())
                prefs.putSecret(DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, com.shod.client.ui.utils.generate16CharPassword(), profile)
        }
    }

    internal suspend fun selectConnectionProfile(profile: Int) {
        require(profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX)
        appContext.settingsDataStore.edit { it[ACTIVE_PROFILE] = profile }
    }

    internal suspend fun linkedConnectionProfile(deployProfile: Int): Int = dataStore.data.first()[getProfileKey(DEPLOY_CONNECTION_PROFILE, deployProfile)] ?: deployProfile

    /** One VPS and its matching connection, written to an explicit slot without moving cursors. */
    internal suspend fun saveDeployProfileDraft(draft: DeploySettingsSnapshot, restoreConnection: Boolean = false): Int? {
        require(draft.profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX)
        require(draft.serverPeerPort in 1..65535 && draft.serverWebPort in 1..65535 && draft.sshPort.toIntOrNull() in 1..65535)
        val profile = draft.profile
        var connectionProfile: Int? = null
        dataStore.edit { prefs ->
            val previousHost = prefs[getProfileKey(DEPLOY_IP, profile)].orEmpty()
            val mainPassword = draft.mainPassword.ifBlank {
                readSecret(prefs, DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, profile)
                    .ifBlank { com.shod.client.ui.utils.generate16CharPassword() }
            }
            val matches = (ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX).filter { p ->
                prefs[getProfileKey(PEER, p)] == draft.ip.trim() &&
                    (prefs[getProfileKey(SERVER_PEER_PORT, p)] ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT) == draft.serverPeerPort &&
                    prefs[getProfileKey(Shod_LINK, p)].isNullOrBlank()
            }
            val existing = prefs[getProfileKey(DEPLOY_CONNECTION_PROFILE, profile)]?.takeIf { p ->
                prefs[getProfileKey(Shod_LINK, p)].isNullOrBlank() &&
                    prefs[getProfileKey(PEER, p)].orEmpty() in listOf("", previousHost, draft.ip.trim())
            }
            val ownPeer = prefs[getProfileKey(PEER, profile)].orEmpty()
            connectionProfile = if (draft.ip.isBlank()) null else (existing
                ?: matches.singleOrNull()
                ?: matches.filter { prefs[getProfileKey(PROFILE_NAME, it)] == draft.serverName.trim() }.singleOrNull()
                ?: profile.takeIf { prefs[getProfileKey(Shod_LINK, it)].isNullOrBlank() && (ownPeer.isBlank() || ownPeer == previousHost || ownPeer == draft.ip.trim()) }
                ?: (ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX).firstOrNull { p ->
                    prefs[getProfileKey(PEER, p)].isNullOrBlank() && prefs[getProfileKey(Shod_LINK, p)].isNullOrBlank() && prefs[getProfileKey(DEPLOY_IP, p)].isNullOrBlank()
                })
            if (connectionProfile?.let { prefs[getProfileKey(PROFILE_REMOVED, it)] == true } == true && !restoreConnection) connectionProfile = null
            connectionProfile?.let { target ->
                if (restoreConnection && prefs[getProfileKey(PROFILE_REMOVED, target)] == true) {
                    prefs[getProfileKey(PROFILE_REVISION, target)] = (prefs[getProfileKey(PROFILE_REVISION, target)] ?: 0L) + 1L
                }
                prefs[getProfileKey(PROFILE_REMOVED, target)] = false
                val peer = prefs[getProfileKey(PEER, target)].orEmpty()
                val isNew = peer.isBlank()
                val oldPassword = readConnectionPasswordState(prefs, target).value
                val updatePassword = isNew || oldPassword.isBlank() ||
                    (target == profile && !deployInspectionStore.read(prefs, profile).serverConfigured)
                if (peer != draft.ip.trim() ||
                    (prefs[getProfileKey(SERVER_PEER_PORT, target)] ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT) != draft.serverPeerPort ||
                    (updatePassword && oldPassword != mainPassword)) {
                    prefs[getProfileKey(PROFILE_REVISION, target)] = (prefs[getProfileKey(PROFILE_REVISION, target)] ?: 0L) + 1L
                    PingResultsCache.removeProfile(target)
                }
                prefs[getProfileKey(DEPLOY_CONNECTION_PROFILE, profile)] = target
                prefs[getProfileKey(PEER, target)] = draft.ip.trim()
                prefs[getProfileKey(PROFILE_NAME, target)] = draft.serverName.trim().ifBlank { draft.ip.trim() }
                prefs[getProfileKey(SERVER_PEER_PORT, target)] = draft.serverPeerPort
                prefs[getProfileKey(MANUAL_PORTS_ENABLED, target)] = true
                if (updatePassword) {
                    prefs[getProfileKey(CONNECTION_PASSWORD_LOCAL, target)] = mainPassword
                    prefs.remove(getProfileKey(CONNECTION_PASSWORD, target))
                    prefs.remove(getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, target))
                }
                if (isNew && prefs[getProfileKey(VK_HASHES, target)].isNullOrBlank()) {
                    prefs[getProfileKey(VK_HASH_MODE, target)] = ShodConstants.VkAutoHash.MODE_AUTO_API
                    prefs[getProfileKey(VK_AUTH_MODE, target)] = "vkcalls"
                }
            }
            if (prefs[getProfileKey(DEPLOY_IP, profile)] != draft.ip.trim()) {
                deployInspectionStore.setServerConfigured(prefs, profile, false)
                deployInspectionStore.clearInspection(prefs, profile)
            }
            prefs[getProfileKey(DEPLOY_IP, profile)] = draft.ip.trim()
            prefs[getProfileKey(DEPLOY_SERVER_NAME, profile)] = draft.serverName.trim()
            prefs[getProfileKey(DEPLOY_LOGIN, profile)] = draft.sshLogin.trim()
            prefs.putSecret(DEPLOY_PASSWORD_ENCRYPTED, DEPLOY_PASSWORD, draft.sshPassword, profile)
            prefs.putSecret(DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, mainPassword, profile)
            prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)] = draft.webLogin.ifBlank { prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)].orEmpty().ifBlank { "admin" } }
            val webPassword = draft.webPassword.ifBlank { readSecret(prefs, DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, profile).ifBlank { com.shod.client.ui.utils.generate16CharPassword() } }
            prefs.putSecret(DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, webPassword, profile)
            prefs[getProfileKey(DEPLOY_SSH_PORT, profile)] = draft.sshPort
            prefs[getProfileKey(DEPLOY_MANUAL_PORTS, profile)] = draft.manualPortsEnabled
            prefs[getProfileKey(SERVER_PEER_PORT, profile)] = draft.serverPeerPort
            prefs[getProfileKey(SERVER_WEB_PORT, profile)] = draft.serverWebPort
            prefs[getProfileKey(MANUAL_PORTS_ENABLED, profile)] = true
            prefs[getProfileKey(SSH_KEYS_MODE, profile)] = draft.sshKeysMode
            prefs[getProfileKey(DOCKER_INSTALL, profile)] = draft.dockerInstall
        }
        return connectionProfile
    }

    suspend fun savePorts(peer: Int, web: Int, sshPortStr: String) {
        val profile = activeProfile.first()
        dataStore.edit { prefs ->
            prefs[getProfileKey(SERVER_PEER_PORT, profile)] = peer
            prefs[getProfileKey(SERVER_WEB_PORT, profile)] = web
            prefs[getProfileKey(DEPLOY_SSH_PORT, profile)] = sshPortStr
        }
    }

    suspend fun saveServerPeerPort(peer: Int) {
        if (peer !in 1..65535) return
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(SERVER_PEER_PORT, profile)] = peer
        }
    }

    suspend fun saveDeployServerName(name: String) {
        val profile = activeProfile.first()
        dataStore.edit { prefs ->
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                prefs.remove(getProfileKey(DEPLOY_SERVER_NAME, profile))
            } else {
                prefs[getProfileKey(DEPLOY_SERVER_NAME, profile)] = trimmed
            }
        }
    }

    suspend fun saveDeploy(ip: String) {
        val profile = activeProfile.first()
        dataStore.edit { prefs ->
            val previous = prefs[getProfileKey(DEPLOY_IP, profile)]
            prefs[getProfileKey(DEPLOY_IP, profile)] = ip
            if (previous != ip) {
                deployInspectionStore.setServerConfigured(prefs, profile, false)
                deployInspectionStore.clearInspection(prefs, profile)
            }
        }
    }

    suspend fun saveServerConfigured(configured: Boolean) {
        val profile = activeProfile.first()
        dataStore.edit { prefs ->
            deployInspectionStore.setServerConfigured(prefs, profile, configured)
        }
    }

    /** Persists the last successful server inspection so the deploy screen remembers it after restart. */
    suspend fun saveDeployInspection(
        checkedAtMs: Long,
        arch: String,
        serviceStatus: String,
        installedVersion: String,
        message: String,
        panelAvailable: Boolean,
    ) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            deployInspectionStore.saveInspection(prefs, profile, checkedAtMs, arch, serviceStatus, installedVersion, message, panelAvailable)
        }
    }

    suspend fun clearDeployInspection() {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            deployInspectionStore.clearInspection(prefs, profile)
        }
    }

    suspend fun saveExcludedApps(packages: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(EXCLUDED_APPS, profile)] = packages
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveWhitelistApps(packages: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(WHITELIST_APPS, profile)] = packages
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveAppRoutingSelection(black: String, white: String, mode: Boolean, used: Set<String>, profile: Int, allApps: Boolean) {
        dataStore.edit { prefs ->
            prefs[getProfileKey(EXCLUDED_APPS, profile)] = black
            prefs[getProfileKey(WHITELIST_APPS, profile)] = white
            prefs[getProfileKey(IS_WHITELIST, profile)] = mode
            prefs[getProfileKey(APP_ROUTING_ALL, profile)] = allApps
            prefs[getProfileKey(APP_PRESETS_USED, profile)] = used.sorted().joinToString(",")
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveAppRoutingLists(blacklist: String, whitelist: String, isWhitelist: Boolean? = null) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(EXCLUDED_APPS, profile)] = blacklist
            prefs[getProfileKey(WHITELIST_APPS, profile)] = whitelist
            if (isWhitelist != null) prefs[getProfileKey(IS_WHITELIST, profile)] = isWhitelist
            prefs[getProfileKey(APP_ROUTING_ALL, profile)] = false
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    // [forProfile]: a deploy writes into the profile that started it, as saveDeployedServer does — the active one may
    // have changed during the minutes it ran
    suspend fun saveConnectionPassword(password: String, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: prefs[ACTIVE_PROFILE] ?: 0
            if (prefs[getProfileKey(PROFILE_REMOVED, profile)] == true) return@edit
            val localKey = getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)
            val legacyKey = getProfileKey(CONNECTION_PASSWORD, profile)
            val encryptedKey = getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile)
            if (password.isBlank()) {
                prefs.remove(localKey)
            } else {
                prefs[localKey] = password
            }
            prefs.remove(legacyKey)
            prefs.remove(encryptedKey)
        }
    }

    suspend fun resetUnreadableConnectionPassword(): Boolean {
        var reset = false
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            val localKey = getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)
            val legacyKey = getProfileKey(CONNECTION_PASSWORD, profile)
            val encryptedKey = getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile)
            if (
                shouldResetUnreadableTunnelPassword(
                    prefs[localKey],
                    prefs[legacyKey],
                    prefs[encryptedKey],
                    secureStore::decrypt,
                )
            ) {
                prefs.remove(localKey)
                prefs.remove(legacyKey)
                prefs.remove(encryptedKey)
                reset = true
            }
        }
        return reset
    }

    suspend fun saveDeploySecrets(mainPass: String, sshLogin: String, sshPass: String, webLogin: String, webPass: String) {
        val profile = activeProfile.first()
        dataStore.edit { prefs ->
            prefs.putSecret(DEPLOY_MAIN_PASSWORD_ENCRYPTED, DEPLOY_MAIN_PASSWORD, mainPass, profile)
            prefs[getProfileKey(DEPLOY_LOGIN, profile)] = sshLogin
            prefs.putSecret(DEPLOY_PASSWORD_ENCRYPTED, DEPLOY_PASSWORD, sshPass, profile)
            prefs[getProfileKey(DEPLOY_WEB_LOGIN, profile)] = webLogin
            prefs.putSecret(DEPLOY_WEB_PASSWORD_ENCRYPTED, DEPLOY_WEB_PASSWORD, webPass, profile)
        }
    }

    suspend fun saveVkAuthMode(mode: String, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: prefs[ACTIVE_PROFILE] ?: 0
            val normalized = when (mode) {
                ShodConstants.VkAuth.MODE_CAPTCHA -> ShodConstants.VkAuth.MODE_CAPTCHA
                ShodConstants.VkAuth.MODE_AUTO_JS -> ShodConstants.VkAuth.MODE_AUTO_JS
                else -> ShodConstants.VkAuth.MODE_CALLS
            }
            prefs[getProfileKey(VK_AUTH_MODE, profile)] = normalized
            prefs[getProfileKey(VK_HASH_MODE, profile)] = vkHashModeForAuthMode(
                requestedAuthMode = normalized,
                currentHashMode = prefs[getProfileKey(VK_HASH_MODE, profile)],
                hasVkToken = effectiveVkToken(prefs, profile).isNotBlank(),
            )
        }
    }

    suspend fun saveObfsMode(mode: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(OBFS_MODE, profile)] = mode
        }
    }

    suspend fun saveTurnTransport(transport: String, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(TURN_TRANSPORT, profile)] = when (transport) {
                ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS -> ShodConstants.Tunnel.TURN_TRANSPORT_TCP_TLS
                else -> ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT
            }
        }
    }

    suspend fun saveSrtpMode(mode: String, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(SRTP_MODE, profile)] = when (mode) {
                ShodConstants.SrtpMode.WRAP -> ShodConstants.SrtpMode.WRAP
                ShodConstants.SrtpMode.DTLS -> ShodConstants.SrtpMode.DTLS
                else -> ShodConstants.SrtpMode.DEFAULT
            }
        }
    }

    suspend fun saveSelectedTurnSubnet(subnetId: String, ipLimit: String? = null, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: prefs[ACTIVE_PROFILE] ?: 0
            val limit = ipLimit ?: prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] ?: "ALL"
            val allIps = if (subnetId.isNotBlank()) TurnSubnetRegistry.getIpsForSubnet(subnetId) else emptyList()
            val limitCount = limit.toIntOrNull()
            val ips = if (allIps.isEmpty()) {
                ""
            } else if (limitCount != null && limitCount > 0) {
                allIps.take(limitCount).joinToString(",")
            } else {
                allIps.joinToString(",")
            }
            prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)] = subnetId
            prefs[getProfileKey(CUSTOM_TURN_IPS, profile)] = ips
            if (ipLimit != null) {
                prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] = ipLimit
            }
        }
    }

    suspend fun saveTurnSubnetIpLimit(limit: String, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: prefs[ACTIVE_PROFILE] ?: 0
            val subnetId = prefs[getProfileKey(SELECTED_TURN_SUBNET, profile)] ?: ""
            val allIps = if (subnetId.isNotBlank()) TurnSubnetRegistry.getIpsForSubnet(subnetId) else emptyList()
            val limitCount = limit.toIntOrNull()
            val ips = if (allIps.isEmpty()) {
                ""
            } else if (limitCount != null && limitCount > 0) {
                allIps.take(limitCount).joinToString(",")
            } else {
                allIps.joinToString(",")
            }
            prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, profile)] = limit
            prefs[getProfileKey(CUSTOM_TURN_IPS, profile)] = ips
        }
    }

    suspend fun saveExtraWorkers(enabled: Boolean, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: (prefs[ACTIVE_PROFILE] ?: 0)
            prefs[getProfileKey(EXTRA_WORKERS, profile)] = enabled
            if (!enabled) {
                val current = prefs[getProfileKey(WORKERS_PER_HASH, profile)] ?: 18
                val maximum = WorkerCountPolicy.defaultMaximum(extraWorkers = false)
                if (current > maximum) {
                    prefs[getProfileKey(WORKERS_PER_HASH, profile)] =
                        WorkerCountPolicy.normalize(current, maximum = maximum)
                }
            }
        }
    }

    suspend fun saveAutoJsRiskAcknowledged(acknowledged: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_JS_RISK_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun saveTcpTransportRiskAcknowledged(acknowledged: Boolean) {
        dataStore.edit { prefs ->
            prefs[TCP_TRANSPORT_RISK_ACKNOWLEDGED] = acknowledged
        }
    }

    suspend fun saveAutoPauseOnWifi(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WifiPreferenceStore.enabledKey] = enabled
        }
    }

    suspend fun saveWifiNetworkListMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[WifiPreferenceStore.modeKey] = WifiNetworkListMode.fromStored(mode).storedValue
        }
    }

    suspend fun saveWifiNetworkListSsids(ssids: Collection<String>) {
        dataStore.edit { prefs ->
            prefs[WifiPreferenceStore.selectedKey] = com.shod.client.network.wifi.policy.encodeWifiSsids(ssids)
        }
    }

    suspend fun saveWifiNetworkListSsids(raw: String) {
        saveWifiNetworkListSsids(decodeWifiSsids(raw))
    }



    suspend fun markBatteryOptimizationPromptHandled() {
        batteryOptimizationPromptStore.markHandled()
    }

    /** Keep the selected captcha mode and its solver consistent in a single profile write. */
    suspend fun saveCaptchaSettings(mode: String, wbvMethod: String, forProfile: Int) {
        require(mode in listOf("auto", "wv", "rjs"))
        require(wbvMethod in listOf("auto", "manual"))
        dataStore.edit { prefs ->
            prefs[getProfileKey(CAPTCHA_MODE, forProfile)] = mode
            prefs[getProfileKey(CAPTCHA_WBV_SOLVE_METHOD, forProfile)] = wbvMethod
            prefs[getProfileKey(CAPTCHA_SOLVE_METHOD, forProfile)] = if (mode == "wv") wbvMethod else "auto"
        }
    }

    suspend fun saveCaptchaMode(mode: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CAPTCHA_MODE, profile)] = mode
        }
    }

    suspend fun saveCaptchaSolveMethod(method: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CAPTCHA_SOLVE_METHOD, profile)] = method
        }
    }

    suspend fun saveWbvCaptchaSolveMethod(method: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(CAPTCHA_WBV_SOLVE_METHOD, profile)] = method
            if (prefs[getProfileKey(CAPTCHA_MODE, profile)] == "wv") {
                prefs[getProfileKey(CAPTCHA_SOLVE_METHOD, profile)] = method
            }
        }
    }

    suspend fun saveVkHashMode(mode: String, forProfile: Int? = null) {
        dataStore.edit { prefs ->
            val profile = forProfile ?: prefs[ACTIVE_PROFILE] ?: 0
            val currentAuthMode = prefs[getProfileKey(VK_AUTH_MODE, profile)]
            val nextAuthMode = vkAuthModeForHashMode(mode, currentAuthMode)
            val selection = normalizeVkModeSelection(
                requestedAuthMode = nextAuthMode,
                requestedHashMode = mode,
                hasVkToken = effectiveVkToken(prefs, profile).isNotBlank(),
            )
            prefs[getProfileKey(VK_AUTH_MODE, profile)] = selection.authMode
            prefs[getProfileKey(VK_HASH_MODE, profile)] = selection.hashMode
        }
    }

    suspend fun saveVkAccessToken(token: String, userId: String) {
        dataStore.edit { prefs ->
            check(!TunnelManager.running.value && !TunnelManager.starting.value && !TunnelManager.stopping.value) {
                "Сначала остановите туннель"
            }
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            archivePersonalVkAccounts(prefs)
            savedVkAccounts.remember(prefs, userId, token)
            prefs.putSecret(VK_ACCOUNT_TOKEN_ENCRYPTED, VK_ACCOUNT_TOKEN, token, 0)
            prefs[VK_ACCOUNT_USER_ID] = userId
            if (!hasImportedVkToken(prefs, profile)) {
                prefs.remove(getProfileKey(VK_ACCESS_TOKEN_ENCRYPTED, profile))
                prefs.remove(getProfileKey(VK_ACCESS_TOKEN, profile))
                prefs.remove(getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile))
            }
        }
    }

    suspend fun clearVkAccessToken() {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            if (hasImportedVkToken(prefs, profile)) {
                prefs.remove(getProfileKey(VK_ACCESS_TOKEN_ENCRYPTED, profile))
                prefs.remove(getProfileKey(VK_ACCESS_TOKEN, profile))
                prefs.remove(getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile))
            } else {
                val removedId = personalVkAccount(prefs).second
                legacyPersonalVkAccounts(prefs).filterKeys { it != removedId }.forEach { (id, value) ->
                    savedVkAccounts.rememberLegacy(prefs, id, value)
                }
                savedVkAccounts.forget(prefs, removedId)
                prefs.remove(VK_ACCOUNT_TOKEN_ENCRYPTED)
                prefs.remove(VK_ACCOUNT_TOKEN)
                prefs.remove(VK_ACCOUNT_USER_ID)
                for (p in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                    if (!prefs[getProfileKey(VK_ACCESS_TOKEN_USER_ID, p)].isNullOrBlank()) {
                        prefs.remove(getProfileKey(VK_ACCESS_TOKEN_ENCRYPTED, p))
                        prefs.remove(getProfileKey(VK_ACCESS_TOKEN, p))
                        prefs.remove(getProfileKey(VK_ACCESS_TOKEN_USER_ID, p))
                    }
                }
            }
        }
    }

    private fun hasImportedVkToken(prefs: Preferences, profile: Int): Boolean =
        prefs[getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile)].isNullOrBlank() &&
            readSecret(prefs, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, profile).isNotBlank()

    /** Forget one personal account only, including its old per-profile login copies. */
    internal suspend fun removeSavedVkAccount(userId: String) {
        dataStore.edit { prefs ->
            check(!TunnelManager.running.value && !TunnelManager.starting.value && !TunnelManager.stopping.value) {
                "Сначала остановите туннель"
            }
            val removingActive = personalVkAccount(prefs).second == userId
            archivePersonalVkAccounts(prefs)
            savedVkAccounts.forget(prefs, userId)
            if (prefs[VK_ACCOUNT_USER_ID] == userId) {
                prefs.remove(VK_ACCOUNT_TOKEN_ENCRYPTED)
                prefs.remove(VK_ACCOUNT_TOKEN)
                prefs.remove(VK_ACCOUNT_USER_ID)
            }
            for (profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                val legacyId = prefs[getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile)].orEmpty()
                // Other logins are already archived; do not silently select one via legacy fallback.
                if (legacyId == userId || (removingActive && legacyId.isNotBlank())) {
                    prefs.remove(getProfileKey(VK_ACCESS_TOKEN_ENCRYPTED, profile))
                    prefs.remove(getProfileKey(VK_ACCESS_TOKEN, profile))
                    prefs.remove(getProfileKey(VK_ACCESS_TOKEN_USER_ID, profile))
                }
            }
        }
    }

    internal suspend fun selectSavedVkAccount(userId: String) {
        dataStore.edit { prefs ->
            check(!TunnelManager.running.value && !TunnelManager.starting.value && !TunnelManager.stopping.value) {
                "Сначала остановите туннель"
            }
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            check(!hasImportedVkToken(prefs, profile)) { "Профиль использует токен из ссылки" }
            val current = personalVkAccount(prefs)
            if (current.second == userId && current.first.isNotBlank()) return@edit
            val token = requireNotNull(savedVkAccounts.token(prefs, userId) ?: legacyPersonalVkAccounts(prefs)[userId]) { "Войдите в этот аккаунт снова" }
            archivePersonalVkAccounts(prefs)
            prefs.putSecret(VK_ACCOUNT_TOKEN_ENCRYPTED, VK_ACCOUNT_TOKEN, token, 0)
            prefs[VK_ACCOUNT_USER_ID] = userId
        }
    }

    // Legacy personal logins are reused across slots; tokens from links have no account UID.
    private fun archivePersonalVkAccounts(prefs: MutablePreferences) {
        legacyPersonalVkAccounts(prefs).forEach { (id, value) -> savedVkAccounts.rememberLegacy(prefs, id, value) }
        // Only the explicit shared login is authoritative over an existing archive entry.
        val shared = readSecret(prefs, VK_ACCOUNT_TOKEN_ENCRYPTED, VK_ACCOUNT_TOKEN, 0)
        savedVkAccounts.remember(prefs, prefs[VK_ACCOUNT_USER_ID].orEmpty(), shared)
    }

    private fun legacyPersonalVkAccounts(prefs: Preferences): Map<String, String> = buildMap {
        for (p in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
            val uid = prefs[getProfileKey(VK_ACCESS_TOKEN_USER_ID, p)].orEmpty()
            if (uid.isNotBlank()) {
                val token = readSecret(prefs, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, p)
                if (token.isNotBlank()) put(uid, token)
            }
        }
        val token = readSecret(prefs, VK_ACCOUNT_TOKEN_ENCRYPTED, VK_ACCOUNT_TOKEN, 0)
        val uid = prefs[VK_ACCOUNT_USER_ID].orEmpty()
        if (uid.isNotBlank() && token.isNotBlank()) put(uid, token)
    }

    private fun personalVkAccount(prefs: Preferences): Pair<String, String> {
        val shared = readSecret(prefs, VK_ACCOUNT_TOKEN_ENCRYPTED, VK_ACCOUNT_TOKEN, 0)
        if (shared.isNotBlank()) return shared to (prefs[VK_ACCOUNT_USER_ID] ?: "")
        for (p in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
            val uid = prefs[getProfileKey(VK_ACCESS_TOKEN_USER_ID, p)].orEmpty()
            if (uid.isNotBlank()) {
                val token = readSecret(prefs, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, p)
                if (token.isNotBlank()) return token to uid
            }
        }
        return "" to ""
    }

    private fun effectiveVkToken(prefs: Preferences, profile: Int): String =
        if (hasImportedVkToken(prefs, profile)) readSecret(prefs, VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, profile)
        else personalVkAccount(prefs).first

    suspend fun saveIsWhitelist(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(IS_WHITELIST, profile)] = enabled
            prefs[getProfileKey(APP_ROUTING_ALL, profile)] = false
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveExceptionsMode(packages: String, isWhitelist: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            if (isWhitelist) {
                prefs[getProfileKey(WHITELIST_APPS, profile)] = packages
            } else {
                prefs[getProfileKey(EXCLUDED_APPS, profile)] = packages
            }
            prefs[getProfileKey(IS_WHITELIST, profile)] = isWhitelist
            prefs[getProfileKey(APP_ROUTING_ALL, profile)] = false
            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    suspend fun saveDomainRoutingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DOMAIN_ROUTING_ENABLED, profile)] = enabled
        }
    }

    suspend fun saveExcludeRuZonesEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(EXCLUDE_RU_ZONES_ENABLED, profile)] = enabled
        }
    }

    suspend fun saveTemplateDomainsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(TEMPLATE_DOMAINS_ENABLED, profile)] = enabled
        }
    }

    suspend fun saveDomainIsWhitelist(isWhitelist: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DOMAIN_IS_WHITELIST, profile)] = isWhitelist
        }
    }

    suspend fun saveDirectDomains(domains: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_DOMAINS, profile)] = domains
            prefs[getProfileKey(DIRECT_DOMAINS_BLACK, profile)] = domains
        }
    }

    suspend fun saveDirectDomainsBlack(domains: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_DOMAINS_BLACK, profile)] = domains
            prefs[getProfileKey(DIRECT_DOMAINS, profile)] = domains
        }
    }

    suspend fun saveDirectDomainsWhite(domains: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_DOMAINS_WHITE, profile)] = domains
        }
    }

    suspend fun saveIpRoutingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(IP_ROUTING_ENABLED, profile)] = enabled
        }
    }

    suspend fun saveIpIsWhitelist(isWhitelist: Boolean) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(IP_IS_WHITELIST, profile)] = isWhitelist
        }
    }

    suspend fun saveDirectIpsBlack(ips: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_IPS_BLACK, profile)] = ips
        }
    }

    suspend fun saveDirectIpsWhite(ips: String) {
        dataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: 0
            prefs[getProfileKey(DIRECT_IPS_WHITE, profile)] = ips
        }
    }

    private suspend fun migrateSubscriptionMeta() {
        dataStore.edit { prefs ->
            val meta = SubscriptionMetaCodec.decode(prefs[SUBSCRIPTION_META]).toMutableMap()
            var changed = false
            for (profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                val url = prefs[getProfileKey(SUBSCRIPTION_URL, profile)]?.trim().orEmpty()
                if (url.isEmpty() || meta.containsKey(url)) continue
                meta[url] = SubscriptionMeta(
                    url = url,
                    title = prefs[getProfileKey(SUBSCRIPTION_TITLE, profile)]?.trim().orEmpty(),
                )
                changed = true
            }
            if (changed) {
                prefs[SUBSCRIPTION_META] = SubscriptionMetaCodec.encode(meta)
            }
        }
    }

    /**
     * Server names used to be stored in the shared connection profile name, which made
     * deploy-only profiles show up as named owner profiles. Move that name to its own
     * deploy key and clear the connection profile name for server-only slots.
     */
    /**
     * A fresh install starts as «Участник»: most people get a link or a QR code, not a server. Decided once, by what is
     * stored rather than by what was written first (other startup code writes settings at the same time): an install
     * that holds a connection, a link, a subscription, hashes, a deployed server or a chosen role keeps its role.
     */
    private suspend fun migrateDefaultRole() {
        dataStore.edit { prefs ->
            if (prefs[DEFAULT_ROLE_DECIDED] == true) return@edit
            prefs[DEFAULT_ROLE_DECIDED] = true
            val configured = (ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX).any { profile ->
                listOf(PEER, Shod_LINK, SUBSCRIPTION_URL, VK_HASHES, DEPLOY_IP, CONNECTION_PASSWORD,
                    CONNECTION_PASSWORD_ENCRYPTED, CONNECTION_PASSWORD_LOCAL)
                    .any { !prefs[getProfileKey(it, profile)].isNullOrBlank() } ||
                    prefs[getProfileKey(Shod_LINK_MODE, profile)] != null
            }
            if (prefs[UI_OWNER_MODE] == null) {
                prefs[UI_OWNER_MODE] = configured &&
                    prefs[getProfileKey(Shod_LINK_MODE, prefs[ACTIVE_PROFILE] ?: 0)] != true
            }
        }
    }

    private suspend fun migrateDeployServerNames() {
        val current = dataStore.data.first()
        if (current[DEPLOY_PROFILE_NAMES_CLEANED] == true) return
        dataStore.edit { prefs ->
            for (profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                val ip = prefs[getProfileKey(DEPLOY_IP, profile)]?.trim().orEmpty()
                if (ip.isEmpty()) continue
                val profileName = prefs[getProfileKey(PROFILE_NAME, profile)]?.trim().orEmpty()
                if (profileName.isNotEmpty() &&
                    prefs[getProfileKey(DEPLOY_SERVER_NAME, profile)].isNullOrBlank()
                ) {
                    prefs[getProfileKey(DEPLOY_SERVER_NAME, profile)] = profileName
                }
                val hasConnection = !prefs[getProfileKey(PEER, profile)].isNullOrBlank() ||
                    !prefs[getProfileKey(Shod_LINK, profile)].isNullOrBlank()
                if (!hasConnection) {
                    prefs.remove(getProfileKey(PROFILE_NAME, profile))
                }
            }
            prefs[DEPLOY_PROFILE_NAMES_CLEANED] = true
        }
    }

    private suspend fun migrateSecretsToKeystore() {
        dataStore.edit { prefs ->
            for (profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                prefs.migrateSecret(getProfileKey(DEPLOY_PASSWORD_ENCRYPTED, profile), getProfileKey(DEPLOY_PASSWORD, profile))
                prefs.migrateSecret(getProfileKey(DEPLOY_MAIN_PASSWORD_ENCRYPTED, profile), getProfileKey(DEPLOY_MAIN_PASSWORD, profile))
                prefs.migrateSecret(getProfileKey(DEPLOY_WEB_PASSWORD_ENCRYPTED, profile), getProfileKey(DEPLOY_WEB_PASSWORD, profile))
            }
        }
    }

    private suspend fun migrateConnectionPasswordsToLocalStorage() {
        dataStore.edit { prefs ->
            for (profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                val localKey = getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)
                val legacyKey = getProfileKey(CONNECTION_PASSWORD, profile)
                val encryptedKey = getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile)
                val password = resolveTunnelPasswordStorage(
                    prefs[localKey],
                    prefs[legacyKey],
                    prefs[encryptedKey],
                    secureStore::decrypt,
                )
                if (password.state == StoredSecretState.Readable) {
                    if (password.value.isBlank()) {
                        prefs.remove(localKey)
                    } else {
                        prefs[localKey] = password.value
                    }
                    prefs.remove(legacyKey)
                    prefs.remove(encryptedKey)
                }
            }
        }
    }

    suspend fun migrateLegacyWhitelistMode() {
        val currentPrefs = dataStore.data.first()
        if (currentPrefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] == true) return

        val hasLegacyWhitelist =
            (ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX).any { profile ->
            currentPrefs[getProfileKey(IS_WHITELIST, profile)] == true
        }
        val installedApps = if (hasLegacyWhitelist) installedSplitTunnelPackages() else emptySet()
        dataStore.edit { prefs ->
            if (prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] == true) return@edit

            for (profile in ShodConstants.Profiles.MIN_INDEX..ShodConstants.Profiles.MAX_INDEX) {
                val whitelistKey = getProfileKey(IS_WHITELIST, profile)
                val appsKey = getProfileKey(EXCLUDED_APPS, profile)
                val whitelistAppsKey = getProfileKey(WHITELIST_APPS, profile)
                if (prefs[whitelistAppsKey] == null && prefs[whitelistKey] == true) {
                    val existing = prefs[appsKey]
                    if (!existing.isNullOrEmpty()) {
                        prefs[whitelistAppsKey] = existing
                    }
                }
                if (prefs[whitelistKey] == true) {
                    val legacyExcluded = prefs[appsKey]
                        ?.split(",")
                        ?.filter { it.isNotEmpty() }
                        ?.toSet()
                        ?: emptySet()
                    prefs[appsKey] = (installedApps - legacyExcluded).sorted().joinToString(",")
                }
            }

            prefs[SPLIT_TUNNEL_WHITELIST_MIGRATED] = true
        }
    }

    private fun installedSplitTunnelPackages(): Set<String> {
        return runCatching {
            appContext.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }
                .filter {
                    it != appContext.packageName &&
                        !it.contains("vkontakte") &&
                        !it.contains("vk.calls")
                }
                .toSet()
        }.getOrDefault(emptySet())
    }

    private fun readSecret(
        prefs: Preferences,
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
        profile: Int
    ): String = readSecretState(prefs, encryptedKey, legacyKey, profile).value

    private fun readSecretState(
        prefs: Preferences,
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
        profile: Int
    ): StoredSecret {
        val profEncryptedKey = getProfileKey(encryptedKey, profile)
        val profLegacyKey = getProfileKey(legacyKey, profile)
        return resolveStoredSecret(
            prefs[profEncryptedKey],
            prefs[profLegacyKey],
            secureStore::decrypt,
        )
    }

    private fun readConnectionPasswordState(
        prefs: Preferences,
        profile: Int,
    ): StoredSecret = resolveTunnelPasswordStorage(
        prefs[getProfileKey(CONNECTION_PASSWORD_LOCAL, profile)],
        prefs[getProfileKey(CONNECTION_PASSWORD, profile)],
        prefs[getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, profile)],
        secureStore::decrypt,
    )

    private fun MutablePreferences.putSecret(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
        value: String,
        profile: Int
    ) {
        val profEncryptedKey = getProfileKey(encryptedKey, profile)
        val profLegacyKey = getProfileKey(legacyKey, profile)
        if (value.isBlank()) {
            remove(profEncryptedKey)
            remove(profLegacyKey)
        } else {
            // An unchanged draft must not create fresh ciphertext and wake every collector.
            if (!this[profEncryptedKey].isNullOrBlank() &&
                readSecret(this, encryptedKey, legacyKey, profile) == value) {
                remove(profLegacyKey)
                return
            }
            this[profEncryptedKey] = secureStore.encrypt(value)
            remove(profLegacyKey)
        }
    }

    private fun MutablePreferences.migrateSecret(
        encryptedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>
    ) {
        val legacyValue = this[legacyKey]
        val encryptedValue = this[encryptedKey]
        if (!encryptedValue.isNullOrBlank()) {
            remove(legacyKey)
            return
        }
        if (!legacyValue.isNullOrBlank()) {
            runCatching {
                this[encryptedKey] = secureStore.encrypt(legacyValue)
                remove(legacyKey)
            }
        }
    }

    suspend fun exportConfigJson(
        includeTunnel: Boolean,
        includeDeploy: Boolean,
        includeExceptions: Boolean,
        includeSettings: Boolean
    ): String {
        val prefs = dataStore.data.first()
        // the ACTIVE profile's values (base keys are profile 0's), and secrets the way the store keeps them
        val p = prefs[ACTIVE_PROFILE] ?: 0
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())

        if (includeTunnel) {
            val tunnel = JSONObject()
            tunnel.put("peer", prefs[getProfileKey(PEER, p)] ?: "")
            tunnel.put("listen_port", prefs[getProfileKey(LISTEN_PORT, p)] ?: ShodConstants.Network.DEFAULT_LOCAL_PORT)
            tunnel.put("manual_ports_enabled", prefs[getProfileKey(MANUAL_PORTS_ENABLED, p)] ?: false)
            tunnel.put("server_peer_port", prefs[getProfileKey(SERVER_PEER_PORT, p)] ?: ShodConstants.Network.DEFAULT_SERVER_PEER_PORT)
            tunnel.put("connection_password", readConnectionPasswordState(prefs, p).value)
            tunnel.put("obfs_mode", prefs[getProfileKey(OBFS_MODE, p)] ?: ShodConstants.Tunnel.DEFAULT_OBFS_MODE)
            tunnel.put("turn_transport", prefs[getProfileKey(TURN_TRANSPORT, p)] ?: ShodConstants.Tunnel.DEFAULT_TURN_TRANSPORT)
            tunnel.put("srtp_mode", prefs[getProfileKey(SRTP_MODE, p)] ?: ShodConstants.SrtpMode.DEFAULT)
            tunnel.put("custom_turn_ips", prefs[getProfileKey(CUSTOM_TURN_IPS, p)] ?: "")
            tunnel.put("sni", prefs[getProfileKey(SNI, p)] ?: "")
            tunnel.put("no_dns", prefs[getProfileKey(NO_DNS, p)] ?: false)
            root.put("tunnel", tunnel)
        }

        if (includeDeploy) {
            val deploy = JSONObject()
            deploy.put("deploy_ip", prefs[getProfileKey(DEPLOY_IP, p)] ?: "")
            deploy.put("deploy_login", prefs[getProfileKey(DEPLOY_LOGIN, p)] ?: "")
            deploy.put("deploy_ssh_port", prefs[getProfileKey(DEPLOY_SSH_PORT, p)] ?: "")
            deploy.put("manual_ports", prefs[getProfileKey(DEPLOY_MANUAL_PORTS, p)] ?: prefs[getProfileKey(MANUAL_PORTS_ENABLED, p)] ?: false)
            deploy.put("server_web_port", prefs[getProfileKey(SERVER_WEB_PORT, p)] ?: ShodConstants.Network.DEFAULT_SERVER_WEB_PORT)
            deploy.put("docker_install", prefs[getProfileKey(DOCKER_INSTALL, p)] ?: true)
            deploy.put("ssh_keys_mode", prefs[getProfileKey(SSH_KEYS_MODE, p)] ?: false)
            deploy.put("vk_hashes", prefs[getProfileKey(VK_HASHES, p)] ?: "")
            deploy.put("secondary_vk_hash", prefs[getProfileKey(SECONDARY_VK_HASH, p)] ?: "")
            deploy.put("vk_auth_mode", prefs[getProfileKey(VK_AUTH_MODE, p)] ?: ShodConstants.VkAuth.MODE_CALLS)
            deploy.put("vk_hash_mode", prefs[getProfileKey(VK_HASH_MODE, p)] ?: ShodConstants.VkAutoHash.MODE_MANUAL)
            // the VK access token is a VK account credential: it stays on this device (an export goes to the clipboard
            // and Downloads). Exports before 2.5.2 carried it blank in practice; the import below leaves a stored one alone.
            deploy.put("workers_per_hash", prefs[getProfileKey(WORKERS_PER_HASH, p)] ?: 18)
            deploy.put("extra_workers", prefs[getProfileKey(EXTRA_WORKERS, p)] ?: false)
            deploy.put("shod_link", prefs[getProfileKey(Shod_LINK, p)] ?: "")
            deploy.put("shod_link_mode", prefs[getProfileKey(Shod_LINK_MODE, p)] ?: false)
            deploy.put("secret_path_enabled", prefs[getProfileKey(DEPLOY_SECRET_PATH_ENABLED, p)] ?: false)
            deploy.put("secret_path_uuid", prefs[getProfileKey(DEPLOY_SECRET_PATH_UUID, p)] ?: "")
            root.put("deploy", deploy)
        }

        if (includeExceptions) {
            val exc = JSONObject()
            prefs[getProfileKey(APP_ROUTING_ALL, p)]?.let { exc.put("all_apps", it) }
            exc.put("app_presets_used", prefs[getProfileKey(APP_PRESETS_USED, p)] ?: "")
            exc.put("excluded_apps", prefs[getProfileKey(EXCLUDED_APPS, p)] ?: "")
            exc.put("whitelist_apps", prefs[getProfileKey(WHITELIST_APPS, p)] ?: "")
            exc.put("is_whitelist", prefs[getProfileKey(IS_WHITELIST, p)] ?: false)
            exc.put("domain_routing_enabled", prefs[getProfileKey(DOMAIN_ROUTING_ENABLED, p)] ?: false)
            exc.put("domain_is_whitelist", prefs[getProfileKey(DOMAIN_IS_WHITELIST, p)] ?: false)
            exc.put("exclude_ru_zones_enabled", prefs[getProfileKey(EXCLUDE_RU_ZONES_ENABLED, p)] ?: false)
            exc.put("template_domains_enabled", prefs[getProfileKey(TEMPLATE_DOMAINS_ENABLED, p)] ?: false)
            exc.put("direct_domains_black", prefs[getProfileKey(DIRECT_DOMAINS_BLACK, p)] ?: prefs[getProfileKey(DIRECT_DOMAINS, p)] ?: "")
            exc.put("direct_domains_white", prefs[getProfileKey(DIRECT_DOMAINS_WHITE, p)] ?: "")
            exc.put("ip_routing_enabled", prefs[getProfileKey(IP_ROUTING_ENABLED, p)] ?: false)
            exc.put("ip_is_whitelist", prefs[getProfileKey(IP_IS_WHITELIST, p)] ?: false)
            exc.put("direct_ips_black", prefs[getProfileKey(DIRECT_IPS_BLACK, p)] ?: "")
            exc.put("direct_ips_white", prefs[getProfileKey(DIRECT_IPS_WHITE, p)] ?: "")
            root.put("exceptions", exc)
        }

        if (includeSettings) {
            val settings = JSONObject()
            settings.put("theme_mode", prefs[ThemePreferencesStore.themeModeKey] ?: "dark")
            settings.put("ui_design", UiDesignPresets.normalize(prefs[UI_DESIGN]))
            settings.put("ui_scale_percent", UiScale.normalize(prefs[UI_SCALE]))
            settings.put("battery_saver_mode", prefs[BATTERY_SAVER_MODE] ?: BatterySaver.DEFAULT_MODE)
            settings.put("battery_saver_percent", prefs[BATTERY_SAVER_PERCENT] ?: BatterySaver.DEFAULT_PERCENT)
            settings.put("is_dynamic_color", prefs[ThemePreferencesStore.dynamicColorKey] ?: false)
            settings.put("theme_palette", prefs[ThemePreferencesStore.themePaletteKey] ?: "pure_black")
            settings.put("auto_pause_on_wifi", WifiPreferenceStore.read(prefs).enabled)
            settings.put("wifi_network_list_mode", WifiPreferenceStore.read(prefs).mode.storedValue)
            settings.put("wifi_network_list_ssids", prefs[WifiPreferenceStore.selectedKey] ?: "")
            settings.put("wifi_saved_ssids", encodeWifiSsids(WifiPreferenceStore.read(prefs).saved))
            settings.put("selected_fingerprint", prefs[getProfileKey(SELECTED_FINGERPRINT, p)] ?: "chrome")
            settings.put("selected_turn_subnet", prefs[getProfileKey(SELECTED_TURN_SUBNET, p)] ?: "")
            settings.put("turn_subnet_ip_limit", prefs[getProfileKey(TURN_SUBNET_IP_LIMIT, p)] ?: "ALL")
            settings.put("logging_enabled", true)
            settings.put("detailed_logs", prefs[DETAILED_LOGS] ?: false)
            settings.put("participant_show_logs", prefs[getProfileKey(PARTICIPANT_SHOW_LOGS, p)] ?: (prefs[UI_OWNER_MODE] ?: false))
            root.put("settings", settings)
        }

        return root.toString(2)
    }

    suspend fun importConfigJson(
        jsonString: String,
        includeTunnel: Boolean,
        includeDeploy: Boolean,
        includeExceptions: Boolean,
        includeSettings: Boolean
    ): Boolean {
        return runCatching {
            val root = JSONObject(jsonString)
            dataStore.edit { mutable ->
                // into the ACTIVE profile (base keys are profile 0's), secrets the way the store keeps them
                val p = mutable[ACTIVE_PROFILE] ?: 0
                if (includeTunnel && root.has("tunnel")) {
                    val tunnel = root.getJSONObject("tunnel")
                    if (tunnel.has("peer")) mutable[getProfileKey(PEER, p)] = tunnel.getString("peer")
                    if (tunnel.has("listen_port")) mutable[getProfileKey(LISTEN_PORT, p)] = tunnel.getInt("listen_port")
                    if (tunnel.has("manual_ports_enabled")) mutable[getProfileKey(MANUAL_PORTS_ENABLED, p)] = tunnel.getBoolean("manual_ports_enabled")
                    if (tunnel.has("server_peer_port")) mutable[getProfileKey(SERVER_PEER_PORT, p)] = tunnel.getInt("server_peer_port")
                    // a blank password in an export is "none in that export" (older exports carried a stored one blank):
                    // it never wipes the password this profile has
                    val pw = if (tunnel.has("connection_password")) tunnel.getString("connection_password") else ""
                    if (pw.isNotBlank()) {
                        // as saveConnectionPassword stores it
                        mutable[getProfileKey(CONNECTION_PASSWORD_LOCAL, p)] = pw
                        mutable.remove(getProfileKey(CONNECTION_PASSWORD, p))
                        mutable.remove(getProfileKey(CONNECTION_PASSWORD_ENCRYPTED, p))
                    }
                    if (tunnel.has("obfs_mode")) mutable[getProfileKey(OBFS_MODE, p)] = tunnel.getString("obfs_mode")
                    if (tunnel.has("turn_transport")) mutable[getProfileKey(TURN_TRANSPORT, p)] = tunnel.getString("turn_transport")
                    if (tunnel.has("srtp_mode")) mutable[getProfileKey(SRTP_MODE, p)] = tunnel.getString("srtp_mode")
                    if (tunnel.has("custom_turn_ips")) mutable[getProfileKey(CUSTOM_TURN_IPS, p)] = tunnel.getString("custom_turn_ips")
                    if (tunnel.has("sni")) mutable[getProfileKey(SNI, p)] = tunnel.getString("sni")
                    if (tunnel.has("no_dns")) mutable[getProfileKey(NO_DNS, p)] = tunnel.getBoolean("no_dns")
                }
                if (includeDeploy && root.has("deploy")) {
                    val deploy = root.getJSONObject("deploy")
                    if (deploy.has("deploy_ip")) mutable[getProfileKey(DEPLOY_IP, p)] = deploy.getString("deploy_ip")
                    if (deploy.has("deploy_login")) mutable[getProfileKey(DEPLOY_LOGIN, p)] = deploy.getString("deploy_login")
                    if (deploy.has("deploy_ssh_port")) mutable[getProfileKey(DEPLOY_SSH_PORT, p)] = deploy.getString("deploy_ssh_port")
                    if (deploy.has("manual_ports")) mutable[getProfileKey(DEPLOY_MANUAL_PORTS, p)] = deploy.getBoolean("manual_ports")
                    if (deploy.has("server_web_port")) mutable[getProfileKey(SERVER_WEB_PORT, p)] = deploy.getInt("server_web_port")
                    if (deploy.has("docker_install")) mutable[getProfileKey(DOCKER_INSTALL, p)] = deploy.getBoolean("docker_install")
                    if (deploy.has("ssh_keys_mode")) mutable[getProfileKey(SSH_KEYS_MODE, p)] = deploy.getBoolean("ssh_keys_mode")
                    if (deploy.has("vk_hashes")) mutable[getProfileKey(VK_HASHES, p)] = deploy.getString("vk_hashes")
                    if (deploy.has("secondary_vk_hash")) mutable[getProfileKey(SECONDARY_VK_HASH, p)] = deploy.getString("secondary_vk_hash")
                    if (deploy.has("vk_auth_mode")) mutable[getProfileKey(VK_AUTH_MODE, p)] = deploy.getString("vk_auth_mode")
                    if (deploy.has("vk_hash_mode")) mutable[getProfileKey(VK_HASH_MODE, p)] = deploy.getString("vk_hash_mode")
                    // blank (every export before 2.5.2) leaves the stored token alone; exports no longer carry it
                    deploy.optString("vk_access_token").takeIf { it.isNotBlank() }?.let {
                        mutable.putSecret(VK_ACCESS_TOKEN_ENCRYPTED, VK_ACCESS_TOKEN, it, p)
                    }
                    if (deploy.has("workers_per_hash")) mutable[getProfileKey(WORKERS_PER_HASH, p)] = deploy.getInt("workers_per_hash")
                    if (deploy.has("extra_workers")) mutable[getProfileKey(EXTRA_WORKERS, p)] = deploy.getBoolean("extra_workers")
                    if (deploy.has("shod_link")) mutable[getProfileKey(Shod_LINK, p)] = deploy.getString("shod_link")
                    if (deploy.has("shod_link_mode")) mutable[getProfileKey(Shod_LINK_MODE, p)] = deploy.getBoolean("shod_link_mode")
                    if (deploy.has("secret_path_enabled")) mutable[getProfileKey(DEPLOY_SECRET_PATH_ENABLED, p)] = deploy.getBoolean("secret_path_enabled")
                    if (deploy.has("secret_path_uuid")) mutable[getProfileKey(DEPLOY_SECRET_PATH_UUID, p)] = deploy.getString("secret_path_uuid")
                }
                if (includeExceptions && root.has("exceptions")) {
                    val exc = root.getJSONObject("exceptions")
                    if (exc.has("all_apps")) mutable[getProfileKey(APP_ROUTING_ALL, p)] = exc.getBoolean("all_apps")
                    else mutable.remove(getProfileKey(APP_ROUTING_ALL, p))
                    mutable[getProfileKey(APP_PRESETS_USED, p)] = exc.optString("app_presets_used", "black,white")
                    if (exc.has("excluded_apps")) mutable[getProfileKey(EXCLUDED_APPS, p)] = exc.getString("excluded_apps")
                    if (exc.has("whitelist_apps")) mutable[getProfileKey(WHITELIST_APPS, p)] = exc.getString("whitelist_apps")
                    if (exc.has("is_whitelist")) mutable[getProfileKey(IS_WHITELIST, p)] = exc.getBoolean("is_whitelist")
                    if (exc.has("domain_routing_enabled")) mutable[getProfileKey(DOMAIN_ROUTING_ENABLED, p)] = exc.getBoolean("domain_routing_enabled")
                    if (exc.has("domain_is_whitelist")) mutable[getProfileKey(DOMAIN_IS_WHITELIST, p)] = exc.getBoolean("domain_is_whitelist")
                    if (exc.has("exclude_ru_zones_enabled")) mutable[getProfileKey(EXCLUDE_RU_ZONES_ENABLED, p)] = exc.getBoolean("exclude_ru_zones_enabled")
                    if (exc.has("template_domains_enabled")) mutable[getProfileKey(TEMPLATE_DOMAINS_ENABLED, p)] = exc.getBoolean("template_domains_enabled")
                    if (exc.has("direct_domains_black")) mutable[getProfileKey(DIRECT_DOMAINS_BLACK, p)] = exc.getString("direct_domains_black")
                    if (exc.has("direct_domains_white")) mutable[getProfileKey(DIRECT_DOMAINS_WHITE, p)] = exc.getString("direct_domains_white")
                    if (exc.has("ip_routing_enabled")) mutable[getProfileKey(IP_ROUTING_ENABLED, p)] = exc.getBoolean("ip_routing_enabled")
                    if (exc.has("ip_is_whitelist")) mutable[getProfileKey(IP_IS_WHITELIST, p)] = exc.getBoolean("ip_is_whitelist")
                    if (exc.has("direct_ips_black")) mutable[getProfileKey(DIRECT_IPS_BLACK, p)] = exc.getString("direct_ips_black")
                    if (exc.has("direct_ips_white")) mutable[getProfileKey(DIRECT_IPS_WHITE, p)] = exc.getString("direct_ips_white")
                }
                if (includeSettings && root.has("settings")) {
                    val s = root.getJSONObject("settings")
                    if (s.has("theme_mode")) mutable[ThemePreferencesStore.themeModeKey] = s.getString("theme_mode")
                    s.optString("ui_design").takeIf { value -> UiDesignPresets.options.any { it.first == value } }?.let { mutable[UI_DESIGN] = it }
                    if (s.has("ui_scale_percent")) mutable[UI_SCALE] = UiScale.normalize(s.optInt("ui_scale_percent", 100))
                    s.optString("battery_saver_mode").takeIf { mode -> BatterySaver.MODES.any { it.first == mode } }?.let { mutable[BATTERY_SAVER_MODE] = it }
                    if (s.has("battery_saver_percent")) mutable[BATTERY_SAVER_PERCENT] = s.getInt("battery_saver_percent").coerceIn(5, 95)
                    if (s.has("is_dynamic_color")) mutable[ThemePreferencesStore.dynamicColorKey] = s.getBoolean("is_dynamic_color")
                    if (s.has("theme_palette")) mutable[ThemePreferencesStore.themePaletteKey] = s.getString("theme_palette")
                    if (s.has("auto_pause_on_wifi")) mutable[WifiPreferenceStore.enabledKey] = s.getBoolean("auto_pause_on_wifi")
                    if (s.has("wifi_saved_ssids")) mutable[WifiPreferenceStore.savedKey] = com.shod.client.network.wifi.policy.encodeWifiSsids(decodeWifiSsids(s.getString("wifi_saved_ssids")))
                    if (s.has("wifi_network_list_mode")) mutable[WifiPreferenceStore.modeKey] = WifiNetworkListMode.fromStored(s.getString("wifi_network_list_mode")).storedValue
                    if (s.has("wifi_network_list_ssids")) mutable[WifiPreferenceStore.selectedKey] = encodeWifiSsids(decodeWifiSsids(s.getString("wifi_network_list_ssids")) )
                    if (s.has("selected_fingerprint")) mutable[getProfileKey(SELECTED_FINGERPRINT, p)] = s.getString("selected_fingerprint")
                    if (s.has("selected_turn_subnet")) mutable[getProfileKey(SELECTED_TURN_SUBNET, p)] = s.getString("selected_turn_subnet")
                    if (s.has("turn_subnet_ip_limit")) mutable[getProfileKey(TURN_SUBNET_IP_LIMIT, p)] = s.getString("turn_subnet_ip_limit")
                    if (s.has("logging_enabled")) mutable[LOGGING_ENABLED] = s.getBoolean("logging_enabled")
                    if (s.has("detailed_logs")) mutable[DETAILED_LOGS] = s.getBoolean("detailed_logs")
                    val currentProfile = mutable[ACTIVE_PROFILE] ?: 0
                    if (s.has("participant_show_logs")) mutable[getProfileKey(PARTICIPANT_SHOW_LOGS, currentProfile)] = s.getBoolean("participant_show_logs")
                }
            }
            true
        }.getOrDefault(false)
    }
}
