package com.shod.client.vpn.routing

import org.junit.Assert.*
import org.junit.Test

class RoutingRuleTextParserTest {
    @Test fun normalizesAndParsesDomainsWithoutAcceptingMalformedEntries() {
        assertEquals(listOf("example.com", "пример.рф"), DomainRoutingRules.parse(
            " HTTPS://WWW.Example.com/path, пример.рф; bad_domain, example.com "
        ))
        assertFalse(DomainRoutingRules.isValid("localhost"))
        assertFalse(DomainRoutingRules.isValid("-bad.example"))
        assertFalse(DomainRoutingRules.isValid("a".repeat(254) + ".com"))
    }

    @Test fun parsesIpv4AddressesCidrsAndRanges() {
        assertEquals(
            listOf("1.1.1.1", "10.0.0.0/8", "192.168.1.1-192.168.1.9"),
            IpRoutingRules.parse("1.1.1.1, 10.0.0.0/8; 192.168.1.1 - 192.168.1.9")
        )
        assertTrue(IpRoutingRules.isValid("0.0.0.0/0"))
        assertTrue(IpRoutingRules.isValid("255.255.255.255"))
    }

    @Test fun rejectsInvalidIpv4OctetsPrefixesAndRanges() {
        for (value in listOf("256.1.1.1", "01.2.3.4", "1.2.3", "1.2.3.4/33", "1.2.3.4/-1", "1.2.3.4-1.2.3.999")) {
            assertFalse(value, IpRoutingRules.isValid(value))
        }
    }

    @Test fun capsEntriesAndRemovesDuplicates() {
        assertEquals(1, DomainRoutingRules.parse("a.example,a.example").size)
        assertEquals(1, IpRoutingRules.parse("1.2.3.4 1.2.3.4").size)
        assertEquals(1024, IpRoutingRules.parse((1..1100).joinToString(" ") { "10.0.${it / 256}.${it % 256}" }).size)
    }
}
