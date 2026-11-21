package com.shod.client.vpn.routing

/**
 * IPv4-only direct-routing rules: a single address, CIDR block, or address range.
 * IPv6 is intentionally not accepted until the tunnel routing engine supports it.
 */
object IpRoutingRules {
    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val number = part.toIntOrNull() ?: return@all false
            number in 0..255 && (part == "0" || !part.startsWith("0"))
        }
    }

    fun clean(raw: String): String {
        val item = raw.trim()
        if (item.contains('-')) {
            val parts = item.split('-')
            if (parts.size == 2) return "${parts[0].trim()}-${parts[1].trim()}"
        }
        return item
    }

    fun isValid(raw: String): Boolean {
        val item = raw.trim()
        if (item.isEmpty()) return false
        if (item.contains('/')) {
            val parts = item.split('/')
            if (parts.size != 2) return false
            val prefix = parts[1].toIntOrNull() ?: return false
            return prefix in 0..32 && isValidIpv4(parts[0].trim())
        }
        if (item.contains('-')) {
            val parts = item.split('-')
            if (parts.size != 2) return false
            return isValidIpv4(parts[0].trim()) && isValidIpv4(parts[1].trim())
        }
        return isValidIpv4(item)
    }

    fun parse(text: String): List<String> = text
        .split(Regex("[,;\\s\\n\\r]+"))
        .map(::clean)
        .filter(::isValid)
        .distinct()
        .take(1024)
}
