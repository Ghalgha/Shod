package com.shod.client.vpn.diagnostics

import com.shod.client.vpn.diagnostics.LogEntry
import com.shod.client.vpn.diagnostics.LogLevel
import com.shod.client.vpn.diagnostics.NativeTunnelLogParser

/** Each outcome has its own counter; fallback must never increment DTLS success. */
internal object DtlsLogParser {
    fun parse(worker: Int, message: String): LogEntry {
        val text = NativeTunnelLogParser.redact(message.trim())
        val failure = text.contains("не состоялось", true) || text.contains("ошибка", true)
        val fallback = text.contains("fallback", true)
        val negatedSuccess = text.contains("неуспеш", true) ||
            Regex("""не\\s+(?:\\S+\\s+){0,2}(?:успеш|заверш)""", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)
        val success = !negatedSuccess &&
            (text.contains("успешен", true) || text.contains("завершено", true))
        return when {
            failure -> LogEntry("dtls_err_$worker", "[DTLS] Поток $worker: $text", priority = 30, isError = true, level = LogLevel.ERR)
            fallback -> LogEntry("dtls_fallback", "[DTLS] Переход на WRAP-SRTP после неудачного DTLS", priority = 30, level = LogLevel.LOG)
            success -> LogEntry("dtls_success", "[DTLS] Рукопожатие успешно, ключи SRTP установлены ✓", priority = 30, level = LogLevel.OK)
            else -> LogEntry("dtls_progress_$worker", "[DTLS] Поток $worker: ${text.ifBlank { "Рукопожатие выполняется" }}", priority = 30, level = LogLevel.DBG)
        }
    }
}
