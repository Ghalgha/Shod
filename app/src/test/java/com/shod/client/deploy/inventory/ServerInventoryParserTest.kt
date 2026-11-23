package com.shod.client.deploy.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerInventoryParserTest {
    @Test
    fun parsesServersBusyPortsAndCompletionMarker() {
        val inventory = parseServerInventory(
            """
            unrelated output
            Shod_INV|server|place=standard|runtime=systemd|state=running|version=2.5.3|peer=46000|web=46002|name=shod.service
            Shod_INV|server|place=other|runtime=docker|state=running|version=2.5.1|peer=47000|web=47002|name=custom-shod
            Shod_INV|busy|proto=tcp|port=2222|by=sshd
            Shod_INV|end
            """.trimIndent()
        )
        requireNotNull(inventory)
        assertEquals(2, inventory.servers.size)
        assertEquals(ServerPlace.STANDARD, inventory.servers[0].place)
        assertEquals("2.5.3", inventory.servers[0].version)
        assertEquals(46000, inventory.servers[0].peerPort)
        assertEquals(ServerPlace.OTHER, inventory.servers[1].place)
        assertEquals("sshd", inventory.busy.single().holder)
        assertEquals(2222, inventory.busy.single().port)
    }

    @Test
    fun incompleteOutputIsNotTreatedAsAnEmptyServer() {
        assertNull(parseServerInventory("Shod_INV|server|place=standard|state=running"))
        assertNull(parseServerInventory(""))
    }

    @Test
    fun rejectsUnknownPlacesAndMalformedBusyPorts() {
        assertNull(
            parseServerInventory(
                """
                Shod_INV|server|place=unknown|state=running
                Shod_INV|busy|proto=tcp|port=not-a-port|by=service
                Shod_INV|end
                """.trimIndent()
            )
        )
    }

    @Test
    fun versionCompatibilityRequiresVersionAtLeastTwoFive() {
        assertTrue(isLine25("2.5"))
        assertTrue(isLine25("2.5.0"))
        assertTrue(isLine25("3.0"))
        assertFalse(isLine25("2.4.9"))
        assertFalse(isLine25("2.1"))
        assertFalse(isLine25(null))
        assertFalse(isLine25("unknown"))
    }

    @Test
    fun onlyStandardInstalledServersCanBeRemovedAndForeignServerBlocksRemoval() {
        val standard = FoundServer(ServerPlace.STANDARD, "systemd", "running", "2.5.3", 46000, 46002, "shod.service")
        val foreign = FoundServer(ServerPlace.OTHER, "docker", "running", "2.5.3", 47000, 47002, "other-shod")
        val leftover = FoundServer(ServerPlace.LEFTOVER, "", "leftover", null, null, null, "old-binary")
        val inventory = ServerInventory(listOf(standard, foreign, leftover), emptyList())
        assertTrue(removableServers(inventory).isEmpty())
        assertEquals(listOf(standard), removableServers(ServerInventory(listOf(standard, leftover), emptyList())))
        assertTrue(removalNotes(inventory).isNotEmpty())
    }

    @Test
    fun rejectsMalformedServerRecordsAndOutOfRangePorts() {
        val malformed = listOf(
            "Shod_INV|server|place=standard|runtime=systemd|state=running|peer=46000|web=46002|name=",
            "Shod_INV|server|place=standard|runtime=systemd|state=running|peer=0|web=46002|name=shod.service",
            "Shod_INV|server|place=standard|runtime=systemd|state=running|peer=46000|web=70000|name=shod.service",
        )
        malformed.forEach { assertNull(parseServerInventory("$it\nShod_INV|end")) }
    }

    @Test
    fun rejectsRecordsAfterCompletionMarker() {
        assertNull(
            parseServerInventory(
                "Shod_INV|end\nShod_INV|busy|proto=tcp|port=2222|by=sshd"
            )
        )
    }

    @Test
    fun rejectsMalformedBusyPortRecords() {
        assertNull(parseServerInventory("Shod_INV|busy|proto=tcp|port=0|by=sshd\nShod_INV|end"))
        assertNull(parseServerInventory("Shod_INV|busy|proto=|port=2222|by=sshd\nShod_INV|end"))
    }
}
