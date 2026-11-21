package com.shod.client.vpn.diagnostics

/**
 * Normalizes diagnostic text before it enters the UI log stream.
 * Keeps presentation cleanup and participant-data masking out of process orchestration.
 */
internal object TunnelLogSanitizer {
    private val stickers = Regex("[\\p{So}\\p{Sk}\\uFE0F\\u200D]")
    private val emptyTags = Regex("\\[\\s*]")
    private val repeatedWhitespace = Regex("[^\\S\\r\\n]+")

    fun sanitize(message: String, ownerHashes: List<String>): String =
        maskOwnerHashes(withoutLogStickers(message), ownerHashes)

    fun maskOwnerHashes(text: String, ownerHashes: List<String>): String =
        ownerHashes.map(String::trim).filter { it.length >= 16 }.fold(text) { out, hash ->
            out.replace(
                Regex(Regex.escape(hash.take(8)) + "[A-Za-z0-9_-]*(?:…|\\.\\.\\.)?"),
                "от владельца",
            )
        }

    fun displayVkHash(hash: String): String = hash.trim().let { value ->
        if (value.length <= 10) value else value.take(10) + "…"
    }

    fun dnsProfileName(dns: String): String = when (dns.trim()) {
        "77.88.8.8,77.88.8.1" -> "Yandex DNS"
        "1.1.1.1,1.0.0.1" -> "Cloudflare DNS"
        "8.8.8.8,8.8.4.4" -> "Google DNS"
        "94.140.14.14,94.140.15.14" -> "AdGuard DNS"
        "111.88.96.54,111.88.96.55", "111.88.96.50,111.88.96.51" -> "Xbox DNS"
        "83.220.169.155,212.109.195.93" -> "Comms DNS"
        "193.233.112.67,193.233.112.88", "95.182.120.241,37.230.192.51" -> "Geohide DNS"
        "9.9.9.9,149.112.112.112" -> "Quad9 DNS"
        "208.67.222.222,208.67.220.220" -> "OpenDNS"
        "95.189.32.74,195.208.5.1" -> "Rostelecom DNS"
        "45.90.28.181,45.90.30.181" -> "NextDNS"
        "195.208.6.1,195.208.7.1" -> "BI.ZONE DNS"
        "195.208.4.1,195.208.5.1" -> "НСДИ DNS"
        else -> "Пользовательский DNS"
    }

    fun withoutLogStickers(message: String): String = message
        .replace(stickers) { symbol ->
            symbol.value.takeIf { it == "✓" || it == "✗" }.orEmpty()
        }
        .replace(emptyTags, "")
        .replace(repeatedWhitespace, " ")
        .trim()
}
