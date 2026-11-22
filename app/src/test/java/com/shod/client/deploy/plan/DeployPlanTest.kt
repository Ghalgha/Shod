package com.shod.client.deploy.plan

import com.shod.client.deploy.inventory.FoundServer
import com.shod.client.deploy.inventory.ServerInventory
import com.shod.client.deploy.inventory.ServerPlace
import com.shod.client.deploy.inventory.parseServerInventory
import com.shod.client.deploy.inventory.removableServers
import com.shod.client.deploy.inventory.removalNotes
import com.shod.client.deploy.inventory.peerPortOrDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCheckTest {
    private fun inv(vararg lines: String): ServerInventory =
        parseServerInventory((lines.toList() + "Shod_INV|end").joinToString("\n"))!!

    private fun server(place: String, version: String, peer: Int, web: Int, state: String = "running", runtime: String = "systemd", name: String = "shod.service") =
        "Shod_INV|server|place=$place|runtime=$runtime|state=$state|version=$version|peer=$peer|web=$web|name=$name"

    private val std21 = server("standard", "2.1.9", 46000, 46002)
    private val std250 = server("standard", "2.5.0", 46000, 46002)
    private val storeOnly = server("standard", "?", 46000, 46002, state = "leftover", runtime = "none", name = "/etc/shod")

    private fun plan(inventory: ServerInventory, manual: ServerPorts? = null, profilePeer: Int? = 46000, docker: Boolean = false) =
        planDeploy(inventory, manual, profilePeer, docker, "2.5.2")

    private fun DeployPlan.kinds() = actions.map { "${it.kind}:${it.peerPort}/${it.webPort}" }

    @Test
    fun `the check result is trusted only when it finished`() {
        assertNull(parseServerInventory(std250))
        val parsed = inv("noise", std250 + "|future=1", "Shod_INV|server|place=nowhere|state=running", "Shod_INV|busy|proto=udp|port=46000|by=nginx (PID 9, /usr/sbin/nginx)")
        assertEquals(1, parsed.servers.size)
        assertEquals("2.5.0", parsed.servers[0].version)
        assertEquals(46000, parsed.busy.single().port)
        assertNull(inv(server("standard", "?", 46000, 46002)).servers.single().version)
    }

    @Test
    fun `the protocol line comes from the version label`() {
        assertTrue(isLine25("2.5.0") && isLine25("2.5.2") && isLine25("2.6") && isLine25("3.0"))
        assertFalse(isLine25("2.1.9") || isLine25("2.0") || isLine25("2") || isLine25(null) || isLine25("x"))
    }

    @Test
    fun `an empty server gets the standard place on the standard ports, or the form's`() {
        assertEquals(listOf("INSTALL:46000/46002"), plan(inv()).kinds())
        assertEquals(listOf("INSTALL:47000/47002"), plan(inv(), ServerPorts(22, 47000, 47002)).kinds())
        // the store an uninstall kept is reused: its clients come back
        val reuse = plan(inv(storeOnly))
        assertEquals(listOf("INSTALL:46000/46002"), reuse.kinds())
        assertTrue(reuse.actions[0].detail.contains("Клиенты прежней установки сохранятся"))
    }

    @Test
    fun `a 2_5 server is updated on its own ports, whatever the form says`() {
        val p = plan(inv(server("standard", "2.5.0", 47000, 47002)), EffectiveServerPorts(22, 46000, 46002))
        assertEquals(listOf("UPDATE:47000/47002"), p.kinds())
        assertEquals("Обновить", p.actions[0].title)
        assertTrue(p.actions[0].detail.contains("2.5.0 → 2.5.2"))
        assertTrue(plan(inv(std250), docker = true).actions[0].detail.contains("будет Docker"))
        assertFalse(plan(inv(std250)).actions[0].detail.contains("будет"))
    }

    @Test
    fun `an older server offers only a confirmed replacement`() {
        val p = plan(inv(std21))
        assertEquals(listOf("REPLACE:46000/46002"), p.kinds())
        assertTrue(p.actions.single().destructive)
        assertTrue(p.actions.single().detail.contains("Клиенты и пароли сохранятся"))
        assertTrue(p.actions.single().detail.contains("приложения 2.1 перестанут подключаться"))
        assertEquals(1, plan(inv(server("standard", "?", 46000, 46002))).actions.size)
    }

    @Test
    fun `unsupported inventory places are ignored`() {
        val parsed = inv(server("unrecognized", "2.5.0", 56000, 56002), std250)
        assertEquals(listOf(ServerPlace.STANDARD), parsed.servers.map { it.place })
        assertEquals(listOf("UPDATE:46000/46002"), plan(parsed).kinds())
    }

    @Test
    fun `a server this app did not install keeps the standard place out of reach`() {
        val other = server("other", "2.5.0", 47000, 47002, name = "shod47.service")
        val p = plan(inv(std250, other))
        assertTrue(p.actions.isEmpty())
        assertTrue(p.notes.single().contains("shod47.service") && p.notes.single().contains("Остановите его"))
        assertFalse(plan(inv(other)).notes.single().contains("Остановите его"))
        assertTrue(plan(inv(std21, other)).actions.isEmpty())
        assertTrue(plan(inv(other)).actions.isEmpty())
        // a stopped one the standard place's cleanup removes is said so
        val gone = plan(inv(std250, server("leftover", "?", 47000, 47002, state = "stopped", name = "shod47.service")))
        assertEquals(listOf("UPDATE:46000/46002"), gone.kinds())
        assertTrue(gone.actions[0].detail.contains("Будет удалено: shod47.service"))
    }


    @Test
    fun `a busy port withholds the install that needs it and says what holds it`() {
        val busy = "Shod_INV|busy|proto=udp|port=46000|by=nginx (PID 9, /usr/sbin/nginx)"
        val p = plan(inv(busy))
        assertTrue(p.actions.isEmpty())
        assertTrue(p.notes.single().contains("nginx"))
        assertEquals(listOf("INSTALL:47000/47002"), plan(inv(busy), EffectiveServerPorts(22, 47000, 47002)).kinds())
    }

    @Test
    fun `removal offers only what this app installs, and the standard place only with no foreign server`() {
        val other = server("other", "?", 47000, 47002, name = "foreign-shod.service")
        val mine = inv(std21, storeOnly)
        assertEquals(listOf(ServerPlace.STANDARD), removableServers(mine).map { it.place })
        assertEquals(46000, removableServers(mine).single().peerPortOrDefault())
        assertTrue(removalNotes(mine).isEmpty())
        // the stock cleanup of a standard-place removal would stop the foreign server: withheld, and said why
        for (foreign in listOf(other)) {
            val box = inv(std21, foreign)
            assertTrue(removableServers(box).isEmpty())
            assertTrue(removalNotes(box).single().contains("не удалить"))
        }
        // what else the standard removal deletes is named
        val withLeftover = inv(std250, server("leftover", "?", 47000, 47002, state = "stopped", runtime = "docker", name = "myshod"))
        assertTrue(removalNotes(withLeftover).single().contains("myshod"))
    }

    @Test
    fun `an update or a replace says the tunnel password and web login come from this profile`() {
        assertTrue(plan(inv(std250)).actions[0].detail.contains("«Авторизации» этого профиля"))
        assertTrue(plan(inv(std21)).actions[0].detail.contains("«Авторизации» этого профиля"))
    }
}
