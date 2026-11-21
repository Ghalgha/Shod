package com.shod.client.vpn.routing

import org.junit.Assert.*
import org.junit.Test

class DomainRoutingRulesTest {
    @Test
    fun normalizesAndParsesDomainsWithoutAcceptingMalformedEntries() {
        assertEquals(
            listOf("example.com", "пример.рф"),
            DomainRoutingRules.parse(" HTTPS://WWW.Example.com/path, пример.рф; bad_domain, example.com ")
        )
        assertFalse(DomainRoutingRules.isValid("localhost"))
        assertFalse(DomainRoutingRules.isValid("-bad.example"))
        assertFalse(DomainRoutingRules.isValid("a".repeat(254) + ".com"))
    }

    @Test
    fun capsEntriesAndRemovesDuplicates() {
        assertEquals(1, DomainRoutingRules.parse("a.example,a.example").size)
        val tooMany = (1..1100).joinToString(" ") { "host$it.example" }
        assertEquals(1024, DomainRoutingRules.parse(tooMany).size)
    }
}
