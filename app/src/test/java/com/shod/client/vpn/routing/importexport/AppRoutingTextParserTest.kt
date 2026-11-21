package com.shod.client.vpn.routing.importexport

import org.junit.Assert.*
import org.junit.Test

class AppRoutingTextParserTest {
    @Test
    fun parsesRussianAndEnglishSectionHeaders() {
        val parsed = requireNotNull(
            AppRoutingTextParser.parse(
                """
                Чёрный список: com.example.blocked, org.example.denied
                com.example.second
                Whitelist: com.example.allowed
                white: org.example.trusted
                """.trimIndent()
            )
        )

        assertEquals(
            linkedSetOf("com.example.blocked", "org.example.denied", "com.example.second"),
            parsed.blacklist
        )
        assertEquals(
            linkedSetOf("com.example.allowed", "org.example.trusted"),
            parsed.whitelist
        )
    }

    @Test
    fun supportsHyphenatedAndAlternativeRussianHeaders() {
        val parsed = requireNotNull(
            AppRoutingTextParser.parse(
                """
                черный-список: com.example.one
                белый-список: com.example.two
                """.trimIndent()
            )
        )

        assertEquals(setOf("com.example.one"), parsed.blacklist)
        assertEquals(setOf("com.example.two"), parsed.whitelist)
    }

    @Test
    fun preservesIntentionalEmptyListsAndReturnsNullWithoutHeaders() {
        val empty = requireNotNull(AppRoutingTextParser.parse("blacklist:\nwhitelist:"))
        assertTrue(empty.blacklist.isEmpty())
        assertTrue(empty.whitelist.isEmpty())
        assertNull(AppRoutingTextParser.parse("com.example.app\norg.example.app"))
    }

    @Test
    fun ignoresInvalidPackagesAndRemovesDuplicates() {
        val parsed = requireNotNull(
            AppRoutingTextParser.parse(
                """
                blacklist: com.example.app, com.example.app, ../invalid, one
                whitelist: com..broken, org.example.good
                """.trimIndent()
            )
        )

        assertEquals(setOf("com.example.app"), parsed.blacklist)
        assertEquals(setOf("org.example.good"), parsed.whitelist)
    }

    @Test
    fun validatesPackageIdsUsingLegacyRules() {
        assertTrue(AppRoutingTextParser.isValidPackage("com.example.app"))
        assertTrue(AppRoutingTextParser.isValidPackage("com.example_app.client2"))
        assertFalse(AppRoutingTextParser.isValidPackage("android"))
        assertFalse(AppRoutingTextParser.isValidPackage("com..example"))
        assertFalse(AppRoutingTextParser.isValidPackage("1com.example"))
        assertFalse(AppRoutingTextParser.isValidPackage("a".repeat(256)))
    }
}
