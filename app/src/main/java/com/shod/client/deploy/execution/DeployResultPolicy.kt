package com.shod.client.deploy.execution

import java.io.IOException
import java.util.Locale

internal fun sanitizeSshPassword(value: String): String =
    value.replace("\r", "").replace("\n", "")

internal fun isSuccessfulDeployResult(exitStatus: Int, output: String): Boolean =
    exitStatus == 0 && output.lineSequence().any { it.trim() == "Shod_DEPLOY_OK" }

// deploy.sh ends a finished uninstall with this line; the command used to be fire-and-forget (30 s, result ignored) and
// the app reported "removed" whatever had happened
private const val UNINSTALL_TIMEOUT = 120_000L

internal fun isSuccessfulUninstallResult(exitStatus: Int, output: String): Boolean =
    exitStatus == 0 && output.lineSequence().any { it.trim() == "Shod_UNINSTALL_OK" }

internal fun uninstallFailureMessage(output: String): String =
    deployFailureDetail(output)?.let { "Удаление не завершено. Причина: $it" }
        ?: "Удаление не завершено; подробности сохранены в errors.log"

private fun deployFailureDetail(output: String): String? =
    output.lineSequence()
        .map(String::trim)
        .lastOrNull { it.startsWith("Shod_DEPLOY_ERROR|") }
        ?.split("|", limit = 3)
        ?.getOrNull(2)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(360)

internal fun deployFailureMessage(exitStatus: Int, output: String): String {
    val detail = deployFailureDetail(output)
    fun withDetail(message: String): String =
        if (detail == null) message else "$message Причина: $detail"

    return when {
        output.lineSequence().any { it.trim() == "Shod_DEPLOY_ROLLED_BACK" } || exitStatus == 30 ->
            withDetail("Новый релиз не прошёл проверку; предыдущая установка восстановлена")
        output.lineSequence().any { it.trim() == "Shod_DEPLOY_ROLLBACK_FAILED" } || exitStatus == 31 ->
            withDetail("Новый релиз не запустился, а автоматический rollback завершился с ошибкой — требуется проверка VPS")
        exitStatus == 20 ->
            withDetail("Кандидат не прошёл предзапусковую проверку; работающий сервер не останавливался")
        exitStatus == 2 ->
            withDetail("Установщик отклонил параметры развёртывания")
        else ->
            detail ?: "Установщик завершился с кодом $exitStatus; подробности сохранены в errors.log"
    }
}

private val REFUSED_PORT = Regex("""\(port (\d{1,5})\)""")

internal fun friendlyDeployError(message: String?): String {
    val text = message.orEmpty()
    return when {
        text.contains("Auth fail (public key)", ignoreCase = true) ||
            text.contains("Auth fail (key parse)", ignoreCase = true) ->
            "SSH: Auth fail — ключ не принят сервером. Проверьте: 1) приватный ключ вставлен целиком (-----BEGIN...-----END), 2) публичный ключ добавлен в authorized_keys на VPS, 3) логин верный"
        text.contains("Auth fail", ignoreCase = true) ->
            "SSH-аутентификация отклонена: проверьте логин, пароль и разрешение PasswordAuthentication на VPS"
        text.contains("authentication", ignoreCase = true) ->
            "SSH-аутентификация отклонена: проверьте логин, пароль и разрешение PasswordAuthentication на VPS"
        text.contains("SFTP/SCP upload failed", ignoreCase = true) ->
            "Загрузка на VPS оборвалась даже после автоматического переподключения SSH; проверьте стабильность Wi-Fi и подробную причину в errors.log"
        text.contains("reject HostKey", ignoreCase = true) ||
            text.contains("HostKey", ignoreCase = true) ||
            text.contains("host key", ignoreCase = true) ->
            "SSH-ключ сервера изменился или не принят"
        text.contains("timeout", ignoreCase = true) || text.contains("timed out", ignoreCase = true) ->
            "Истекло время ожидания SSH/SFTP"
        text.contains("UnknownHost", ignoreCase = true) ||
            text.contains("No route to host", ignoreCase = true) ->
            "VPS недоступен по указанному адресу"
        text.contains("session is down", ignoreCase = true) ||
            text.contains("connection is closed", ignoreCase = true) ->
            "SSH-соединение было разорвано"
        text.contains("Connection refused", ignoreCase = true) -> {
            // Android's ConnectException names the port it dialled first: "failed to connect to /1.2.3.4 (port 22) from …"
            val port = REFUSED_PORT.find(text)?.groupValues?.get(1)
            val which = port?.let { "Порт SSH $it" } ?: "Порт SSH"
            "$which не принимает соединения. Проверьте порт SSH в данных сервера (SSH может работать не на 22) и что VPS включён. " +
                "После нескольких неверных паролей VPS может временно блокировать ваш IP (fail2ban)"
        }
        text.isBlank() -> "Неизвестная ошибка установки"
        else -> text.take(240)
    }
}

internal enum class DeployOutputLevel { LOG, OK, ERR }

internal data class DeployOutputLine(
    val message: String,
    val level: DeployOutputLevel,
)

private val ANSI_ESCAPE = Regex("\u001B\\[[;\\d]*m")
private val DEPLOY_SYMBOLS = Regex("[\\p{So}\\p{Sk}\\uFE0F\\u200D]")
private val DEPLOY_STATUS_PREFIX = Regex("^\\[(?:OK|ERR|LOG|WARN|✓|!|✗|►)]\\s*", RegexOption.IGNORE_CASE)

internal fun parseDeployOutputLine(rawLine: String): DeployOutputLine? {
    val raw = rawLine.replace(ANSI_ESCAPE, "").trim()
    if (
        raw.isEmpty() ||
            raw.startsWith("Shod_PROGRESS|") ||
            raw.startsWith("Shod_DEPLOY_ERROR|") ||
            raw.startsWith("Shod_INV|") ||
            raw == "Shod_DEPLOY_OK" ||
            raw == "Shod_UNINSTALL_OK" ||
            raw == "Shod_DEPLOY_ROLLED_BACK" ||
            raw == "Shod_DEPLOY_ROLLBACK_FAILED"
    ) return null

    val warning = raw.startsWith("[WARN]", ignoreCase = true) || raw.startsWith("[!]") || raw.startsWith("⚠")
    val explicitError = raw.startsWith("[ERR]", ignoreCase = true) || raw.startsWith("[✗]") || raw.startsWith("✗")
    val success = raw.startsWith("[OK]", ignoreCase = true) || raw.startsWith("[✓]") || raw.startsWith("✓")
    val lower = raw.lowercase()
    val looksLikeError = explicitError || (!warning && (
        lower.contains("error:") ||
            lower.contains("failed") ||
            lower.contains("fatal") ||
            lower.contains("не запуст") ||
            lower.contains("ошибка")
        ))

    var clean = raw
        .replace(DEPLOY_STATUS_PREFIX, "")
        .replace(DEPLOY_SYMBOLS, "")
        .trim { it.isWhitespace() || it in "═║╔╗╚╝─━" }
        .replace(Regex("\\s+"), " ")
        .trim()
    if (clean.isEmpty() || clean.all { it == '=' || it == '-' || it == '_' }) return null
    if (warning && !clean.startsWith("Предупреждение:", ignoreCase = true)) {
        clean = "Предупреждение: $clean"
    }

    val level = when {
        looksLikeError -> DeployOutputLevel.ERR
        success -> DeployOutputLevel.OK
        else -> DeployOutputLevel.LOG
    }
    return DeployOutputLine(clean.take(500), level)
}

internal enum class ServerArchitecture(
    val assetName: String,
    val displayName: String,
) {
    AMD64("shod-linux-amd64", "amd64"),
    ARM64("shod-linux-arm64", "ARM64"),
    ARMV7("shod-linux-armv7", "ARM32"),
}

internal fun serverArchitectureForMachine(output: String): ServerArchitecture {
    val machine = output.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }
        ?.lowercase(Locale.ROOT)
        ?: throw IOException("VPS не вернул архитектуру через uname -m")
    return when (machine) {
        "x86_64", "amd64" -> ServerArchitecture.AMD64
        "aarch64", "arm64" -> ServerArchitecture.ARM64
        "armv7l", "armv7", "armhf" -> ServerArchitecture.ARMV7
        else -> throw IOException(
            "Архитектура VPS $machine пока не поддерживается. Поддерживаются: x86_64, aarch64, armv7l",
        )
    }
}
