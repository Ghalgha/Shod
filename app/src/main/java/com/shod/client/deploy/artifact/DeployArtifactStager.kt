package com.shod.client.deploy.artifact

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Extracts the VPS-architecture-specific server core bundled in the APK.
 * The build pipeline verifies the packaged binaries against server provenance before APK assembly.
 */
internal class DeployArtifactStager(
    private val context: Context,
) {
    fun stageServerCore(
        assetName: String,
        target: File,
        checkCancelled: () -> Unit = {},
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): File {
        check(assetName.matches(Regex("shod-linux-(amd64|arm64|armv7)"))) {
            "Unsupported server core: $assetName"
        }
        checkCancelled()
        onProgress(0.06f, "Подготовка серверного ядра из APK…")
        context.assets.open(assetName).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        checkCancelled()
        if (!target.isFile || target.length() == 0L) {
            throw IOException("Файл $assetName отсутствует или пуст в APK")
        }
        return target
    }
}
