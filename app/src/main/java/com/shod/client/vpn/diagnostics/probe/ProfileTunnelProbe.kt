package com.shod.client.vpn.diagnostics.probe

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import com.shod.client.ConnectionSource
import com.shod.client.SettingsStore
import com.shod.client.ShodConstants
import com.shod.client.TunnelManager
import com.shod.client.integration.vk.calls.api.VkApi
import com.shod.client.integration.vk.calls.api.VkApiResult
import com.shod.client.integration.vk.calls.policy.ProfileProbeVkAuthPolicy
import com.shod.client.integration.vk.calls.policy.ProfileProbeVkAuthStrategy
import com.shod.client.platform.android.process.probe.readProbeOutput
import com.shod.client.platform.android.vpn.diagnostics.probe.ProbeTunTransport
import com.shod.client.vpn.events.TunnelEventParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** Executes a profile-only native probe without creating an Android VPN interface. */
internal object ProfileTunnelProbe {
    private const val PROBE_TIMEOUT_MS = 60_000L
    private val mutex = Mutex()
    private val checking = MutableStateFlow(false)
    val isChecking = checking.asStateFlow()

    suspend fun check(context: Context, profile: Int): ProfilePingResult = mutex.withLock {
        checking.value = true
        try {
            withContext(Dispatchers.IO) {
                if (mainTunnelBusy()) {
                    return@withContext ProfilePingResult.Failure("Отключите VPN перед проверкой")
                }
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                if (connectivity.allNetworks.any { network ->
                        connectivity.getNetworkCapabilities(network)
                            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                    }
                ) {
                    return@withContext ProfilePingResult.Failure("Отключите другой VPN перед проверкой")
                }
                try {
                    withTimeout(PROBE_TIMEOUT_MS) {
                        runProbe(context.applicationContext, profile)
                    }
                } catch (_: TimeoutCancellationException) {
                    ProfilePingResult.Failure("Тайм-аут VK/TURN или сервера")
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    ProfilePingResult.Failure("Не удалось проверить туннель")
                }
            }
        } finally {
            checking.value = false
        }
    }

    private fun mainTunnelBusy(): Boolean = ProfileProbeRuntimePolicy.mainTunnelBusy(
        running = TunnelManager.running.value,
        starting = TunnelManager.starting.value,
        stopping = TunnelManager.stopping.value,
        autoPausedForWifi = TunnelManager.autoPausedForWifi.value,
    )

    private suspend fun runProbe(context: Context, profile: Int): ProfilePingResult = coroutineScope {
        val store = SettingsStore(context, readOnlyProfile = profile)
        val source = com.shod.client.resolveConnectionSource(store, updateLogMask = false)
            ?: return@coroutineScope ProfilePingResult.Failure("Нет адреса сервера")
        if (source.password.isBlank()) {
            return@coroutineScope ProfilePingResult.Failure("Нет пароля туннеля")
        }

        val binary = File(context.applicationInfo.nativeLibraryDir, ShodConstants.Tunnel.BINARY_NAME)
        if (!binary.isFile) {
            return@coroutineScope ProfilePingResult.Failure("Нет ядра для этой архитектуры")
        }

        val authPlan = ProfileProbeVkAuthPolicy.resolve(
            hashes = source.hashes,
            hashesFromLink = source.hashesFromLink,
            tokenFromLink = source.tokenFromLink,
            linkToken = source.vkAccessToken,
            storedToken = store.vkAccessToken.first(),
            configuredHashMode = store.vkHashMode.first(),
            configuredAuthMode = store.vkAuthMode.first(),
        )
        if (authPlan.strategy == ProfileProbeVkAuthStrategy.VK_AUTO_JS && authPlan.accessToken.isBlank()) {
            return@coroutineScope ProfilePingResult.Failure("Войдите в VK")
        }
        if (authPlan.strategy == ProfileProbeVkAuthStrategy.VK_CALLS_API && authPlan.accessToken.isBlank()) {
            return@coroutineScope ProfilePingResult.Failure("Войдите в VK")
        }

        var createdCall: VkApi.StartedCall? = null
        var process: Process? = null
        var reader: Job? = null
        var transport: ProbeTunTransport? = null
        try {
            var resolvedAuthPlan = authPlan
            if (authPlan.strategy == ProfileProbeVkAuthStrategy.VK_CALLS_API) {
                when (val result = VkApi.startCall(authPlan.accessToken)) {
                    is VkApiResult.Ok -> {
                        createdCall = result.value
                        resolvedAuthPlan = authPlan.copy(firstHash = result.value.hash)
                    }
                    else -> return@coroutineScope ProfilePingResult.Failure("Ошибка авторизации VK API")
                }
                currentCoroutineContext().ensureActive()
            }

            if (resolvedAuthPlan.strategy != ProfileProbeVkAuthStrategy.VK_AUTO_JS &&
                resolvedAuthPlan.firstHash.isBlank()
            ) {
                return@coroutineScope ProfilePingResult.Failure("Нет VK-хешей")
            }

            val sessionSalt = com.shod.client.newTunnelSessionId()
            val udsName = "shod_probe_$sessionSalt"
            val command = ProfileProbeProcessCommand.build(
                binaryPath = binary.absolutePath,
                peer = source.peer,
                udsName = udsName,
                authPlan = resolvedAuthPlan,
                turnTransport = store.turnTransport.first(),
                srtpMode = store.srtpMode.first(),
                customTurnIps = store.customTurnIps.first(),
                captchaMode = store.captchaMode.first(),
                fingerprint = store.selectedFingerprint.first(),
                clientIds = store.activeClientIds.first(),
                sessionSalt = sessionSalt,
                generationId = System.currentTimeMillis() / 1000L,
            )

            val started = ProcessBuilder(command)
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                    environment()["Shod_EVENTS"] = "1"
                    environment()["Shod_PASSWORD"] = source.password
                    environment()["RAYON_NUM_THREADS"] = "2"
                }
                .start()
            process = started

            if (resolvedAuthPlan.strategy == ProfileProbeVkAuthStrategy.VK_AUTO_JS) {
                val bootstrap = Base64.encodeToString(
                    JSONObject().put("token", resolvedAuthPlan.accessToken)
                        .toString().toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
                started.outputStream.write("VK_JS_BOOTSTRAP:$bootstrap\n".toByteArray(Charsets.UTF_8))
                started.outputStream.flush()
            }

            val config = CompletableDeferred<String>()
            reader = launch(Dispatchers.IO) {
                try {
                    readProbeOutput(started.inputStream, { started.isAlive }) { line ->
                        when (val event = TunnelEventParser.parse(line)) {
                            is TunnelEventParser.Event.Config -> {
                                if (event.config.startsWith("TUNCONF:")) config.complete(event.config)
                            }
                            is TunnelEventParser.Event.CaptchaRequest -> config.completeExceptionally(
                                ProbeFailure("Требуется капча: сначала подключитесь вручную"),
                            )
                            is TunnelEventParser.Event.Error -> if (event.fatal) {
                                config.completeExceptionally(ProbeFailure("Сервер или VK отклонил подключение"))
                            }
                            else -> Unit
                        }
                    }
                    if (!config.isCompleted) {
                        config.completeExceptionally(ProbeFailure("Ядро завершило проверку"))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (!config.isCompleted) {
                        config.completeExceptionally(ProbeFailure("Ядро завершило проверку"))
                    }
                }
            }

            while (!config.isCompleted) {
                currentCoroutineContext().ensureActive()
                if (mainTunnelBusy()) {
                    return@coroutineScope ProfilePingResult.Failure("Проверка прервана запуском VPN")
                }
                delay(100)
            }

            val parts = config.await().removePrefix("TUNCONF:").split(':')
            val sourceIp = parseIpv4Literal(parts.getOrNull(0).orEmpty())
                ?: return@coroutineScope ProfilePingResult.Failure("Неподдерживаемый адрес туннеля")
            val dnsIp = parts.getOrNull(1)
                ?.split(',')
                ?.firstOrNull()
                ?.let(::parseIpv4Literal)
                ?: return@coroutineScope ProfilePingResult.Failure("Сервер не передал IPv4 DNS")

            val tun = ProbeTunTransport()
            transport = tun
            while (true) {
                currentCoroutineContext().ensureActive()
                try {
                    tun.attach(udsName)
                    break
                } catch (_: Exception) {
                    delay(100)
                }
            }

            repeat(3) {
                val packet = TunnelProbePacket(
                    source = sourceIp.address,
                    dns = dnsIp.address,
                    id = SecureRandom().nextInt(65_536),
                )
                tun.exchange(packet, shouldAbort = ::mainTunnelBusy)?.let { latency ->
                    return@coroutineScope ProfilePingResult.Success(latency)
                }
            }
            ProfilePingResult.Failure("Туннель создан, ответа на тестовый пакет нет")
        } catch (error: ProbeFailure) {
            ProfilePingResult.Failure(error.message.orEmpty())
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                transport?.close()
                process?.let { active ->
                    runCatching {
                        active.outputStream.write("FINISH_VK_CALLS\nSTOP\n".toByteArray(Charsets.UTF_8))
                        active.outputStream.flush()
                    }
                    if (!active.waitFor(2, TimeUnit.SECONDS)) active.destroyForcibly()
                }
                reader?.cancelAndJoin()
                runCatching { process?.inputStream?.close() }
                createdCall?.let { call ->
                    VkApi.forceFinishCall(authPlan.accessToken, call.callId)
                }
            }
        }
    }

    private class ProbeFailure(message: String) : Exception(message)
}
