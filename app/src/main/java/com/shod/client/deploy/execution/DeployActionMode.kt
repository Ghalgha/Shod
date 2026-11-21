package com.shod.client.deploy.execution

internal enum class DeployActionMode(
    val logTitle: String,
    val scriptAction: String,
) {
    Install("Начало установки", "install"),
    Update("Начало обновления", "update"),
    Reinstall("Начало переустановки", "install"),
}
