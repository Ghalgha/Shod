package com.shod.client.deploy.execution

import org.junit.Assert.assertEquals
import org.junit.Test

internal class DeployActionModeTest {
    @Test
    fun eachActionMapsToTheOriginalScriptActionAndLogTitle() {
        assertEquals("Начало установки", DeployActionMode.Install.logTitle)
        assertEquals("install", DeployActionMode.Install.scriptAction)
        assertEquals("Начало обновления", DeployActionMode.Update.logTitle)
        assertEquals("update", DeployActionMode.Update.scriptAction)
        assertEquals("Начало переустановки", DeployActionMode.Reinstall.logTitle)
        assertEquals("install", DeployActionMode.Reinstall.scriptAction)
    }
}
