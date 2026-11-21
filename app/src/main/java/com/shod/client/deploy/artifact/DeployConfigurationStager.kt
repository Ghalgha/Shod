package com.shod.client.deploy.artifact

import com.shod.client.deploy.execution.DeployActionMode
import com.shod.client.deploy.execution.deployEnvironmentValue
import java.io.File
import org.json.JSONObject

/**
 * Stages deployment configuration files on the device before they are uploaded.
 *
 * Local staging names are generic; the installer remains responsible for placing
 * the files at its established server-side paths.
 */
internal data class DeployConfigurationFiles(
    val environment: File?,
    val overrides: File?,
)

internal class DeployConfigurationStager {
    fun stage(
        workingDirectory: File,
        mode: DeployActionMode,
        installInDocker: Boolean,
        webLogin: String,
        webPassword: String,
        publicHost: String,
        secretPath: String,
        mainPassword: String,
    ): DeployConfigurationFiles {
        if (mode == DeployActionMode.Update) {
            return DeployConfigurationFiles(environment = null, overrides = null)
        }

        val environmentFile = File(workingDirectory, "environment.env").apply {
            writeText(
                buildString {
                    appendLine("Shod_WEB_USER=" + deployEnvironmentValue(webLogin, installInDocker))
                    appendLine("Shod_WEB_PASS=" + deployEnvironmentValue(webPassword, installInDocker))
                    appendLine("Shod_PUBLIC_IP=" + deployEnvironmentValue(publicHost.trim(), installInDocker))
                    if (secretPath.isNotBlank()) {
                        appendLine(
                            "Shod_WEB_SECRET_PATH=" +
                                deployEnvironmentValue(secretPath.trim(), installInDocker)
                        )
                    }
                }
            )
        }
        val overridesFile = File(workingDirectory, "deploy-overrides.json").apply {
            writeText(JSONObject().put("main_password", mainPassword).toString())
        }
        return DeployConfigurationFiles(environmentFile, overridesFile)
    }

}
