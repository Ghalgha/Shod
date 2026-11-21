package com.shod.client.deploy.artifact

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.json.JSONObject

/**
 * Stages the architecture-specific server core for a deployment.
 *
 * APK assets remain the fallback for builds without a pinned download manifest.
 * When the manifest is present, only its packaged URL, size and digest are used
 * to obtain the binary through [ServerCoreCache].
 */
internal class DeployArtifactStager(
    private val context: Context,
    private val cache: ServerCoreCache = ServerCoreCache(File(context.filesDir, "server-cores")),
) {
    fun stageServerCore(
        assetName: String,
        target: File,
        checkCancelled: () -> Unit = {},
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onVerified: () -> Unit = {},
    ): File {
        check(assetName.matches(Regex("shod-linux-(amd64|arm64|armv7)"))) {
            "Unsupported server core: $assetName"
        }
        checkCancelled()

        if (context.assets.list("")?.contains("server-downloads.json") == true) {
            val manifest = context.assets.open("server-downloads.json")
                .bufferedReader(Charsets.UTF_8)
                .use { JSONObject(it.readText()) }
            val entry = manifest.optJSONObject(assetName)
                ?: throw IOException("В манифесте загрузок отсутствует $assetName")
            val artifact = ServerCoreCache.Artifact(
                name = assetName,
                url = entry.getString("url"),
                size = entry.getLong("size"),
                sha256 = entry.getString("sha256"),
            )
            onProgress(0.06f, "Загрузка и проверка серверного ядра...")
            val cached = cache.obtain(artifact, checkCancelled)
            checkCancelled()
            cached.copyTo(target, overwrite = true)
            if (!target.isFile || target.length() != artifact.size) {
                throw IOException("Не удалось подготовить проверенное серверное ядро")
            }
            onVerified()
            return target
        }

        context.assets.open(assetName).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        checkCancelled()
        if (!target.isFile || target.length() == 0L) {
            throw IOException("Файл $assetName отсутствует или пуст в assets")
        }
        return target
    }
}
