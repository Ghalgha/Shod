package com.shod.client.vpn.routing

/**
 * Validates and normalizes domain entries accepted by the routing editor.
 * Kept independent of Compose so the screen can delegate parsing to this layer.
 */
object DomainRoutingRules {
    private val labelPattern = Regex("^[a-z0-9\\u0400-\\u04ff]([a-z0-9\\u0400-\\u04ff\\-]{0,61}[a-z0-9\\u0400-\\u04ff])?$")

    fun clean(raw: String): String = raw.trim()
        .lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('/')
        .substringBefore(':')
        .substringBefore('?')
        .substringBefore('#')
        .trim(',', ';', ' ', '\t', '\n', '\r', '.', '(', ')', '[', ']', '"', '\'')

    fun isValid(domain: String): Boolean {
        if (domain.length < 3 || domain.length > 253) return false
        val parts = domain.split('.')
        if (parts.size < 2) return false
        val tld = parts.last()
        if (tld.length < 2 || !tld.any { it.isLetter() }) return false
        return parts.all { it.matches(labelPattern) }
    }

    fun parse(text: String): List<String> = text
        .split(Regex("[,;\\s\\n\\r]+"))
        .map(::clean)
        .filter(::isValid)
        .distinct()
        .take(1024)
}

/** IPv4-only direct-routing entries: address, CIDR, or inclusive address range. */
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
