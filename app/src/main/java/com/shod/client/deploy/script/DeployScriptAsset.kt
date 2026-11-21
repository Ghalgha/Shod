package com.shod.client.deploy.script

import android.content.Context
import java.io.File

/**
 * Reads the packaged installer and normalizes line endings before it is sent
 * to a remote Linux host. Script assets are infrastructure, not UI operations.
 */
internal fun normalizeDeployScript(script: String): String =
    script.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

internal fun writeDeployScript(context: Context, target: File) {
    val script = context.assets.open("deploy.sh").bufferedReader(Charsets.UTF_8).use { it.readText() }
    target.writeText(normalizeDeployScript(script), Charsets.UTF_8)
}
