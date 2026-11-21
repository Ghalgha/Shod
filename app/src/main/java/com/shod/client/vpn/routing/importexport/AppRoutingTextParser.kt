package com.shod.client.vpn.routing.importexport

/**
 * Parses the legacy plain-text app routing format accepted by RoutingScreen.
 *
 * A section starts with a Russian or English blacklist/whitelist header.
 * Package IDs on that line and subsequent lines belong to the active section.
 * A missing header is distinguished from two intentionally empty lists.
 */
data class AppRoutingLists(
    val blacklist: Set<String>,
    val whitelist: Set<String>,
)

object AppRoutingTextParser {
    private val packagePattern =
        Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    fun isValidPackage(value: String): Boolean {
        if (value.length !in 3..255) return false
        return value.matches(packagePattern)
    }

    fun parse(text: String): AppRoutingLists? {
        var currentSection: Section? = null
        val blacklist = linkedSetOf<String>()
        val whitelist = linkedSetOf<String>()
        var headerFound = false

        fun extractPackages(line: String): List<String> =
            line.split(Regex("[,;\\s\\t]+"))
                .map { it.trim().trim(',', ';', '"', '\'') }
                .filter(::isValidPackage)

        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val lower = trimmed.lowercase()

            when {
                lower.startsWith("чёрный-список:") ||
                    lower.startsWith("черный-список:") ||
                    lower.startsWith("чёрный список:") ||
                    lower.startsWith("черный список:") ||
                    lower.startsWith("blacklist:") ||
                    lower.startsWith("black:") -> {
                    headerFound = true
                    currentSection = Section.BLACKLIST
                    blacklist.addAll(extractPackages(trimmed.substringAfter(':')))
                }

                lower.startsWith("белый-список:") ||
                    lower.startsWith("белый список:") ||
                    lower.startsWith("whitelist:") ||
                    lower.startsWith("white:") -> {
                    headerFound = true
                    currentSection = Section.WHITELIST
                    whitelist.addAll(extractPackages(trimmed.substringAfter(':')))
                }

                else -> when (currentSection) {
                    Section.BLACKLIST -> blacklist.addAll(extractPackages(trimmed))
                    Section.WHITELIST -> whitelist.addAll(extractPackages(trimmed))
                    null -> Unit
                }
            }
        }

        if (!headerFound) return null
        return AppRoutingLists(blacklist = blacklist, whitelist = whitelist)
    }

    private enum class Section { BLACKLIST, WHITELIST }
}
