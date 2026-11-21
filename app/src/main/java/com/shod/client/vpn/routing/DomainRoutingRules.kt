package com.shod.client.vpn.routing

/**
 * Domain normalization and validation for direct-routing rules.
 *
 * Pure domain logic: deliberately independent of Compose, Android and SettingsStore.
 */
object DomainRoutingRules {
    private val labelPattern =
        Regex("^[a-z0-9\\u0400-\\u04ff]([a-z0-9\\u0400-\\u04ff\\-]{0,61}[a-z0-9\\u0400-\\u04ff])?$")

    fun clean(raw: String): String = raw.trim()
        .lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('/')
        .substringBefore(':')
        .substringBefore('?')
        .substringBefore('#')
        .trim(',', ';', ' ', '\\t', '\\n', '\\r', '.', '(', ')', '[', ']', '"', '\\'')

    fun isValid(domain: String): Boolean {
        if (domain.length < 3 || domain.length > 253) return false
        val parts = domain.split('.')
        if (parts.size < 2) return false
        val topLevelDomain = parts.last()
        if (topLevelDomain.length < 2 || !topLevelDomain.any { it.isLetter() }) return false
        return parts.all { it.matches(labelPattern) }
    }

    fun parse(text: String): List<String> = text
        .split(Regex("[,;\\\\s\\n\\r]+"))
        .map(::clean)
        .filter(::isValid)
        .distinct()
        .take(1024)
}
