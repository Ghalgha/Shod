package com.shod.client.platform.android.deploy.ssh

import android.content.Context
import com.shod.client.platform.android.deploy.DeployDiagnostics
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.Collections

/**
 * Android-backed TOFU (trust on first use) storage for SSH server host keys.
 *
 * A changed key is rejected until the user explicitly trusts the exact
 * fingerprint presented by the server.
 */
object SshHostKeyStore {
    private const val PREFS_NAME = "ssh_known_hosts"
    private const val FINGERPRINT_ALGORITHM = "SHA-256"

    private fun entryKey(host: String, port: Int): String =
        "${host.trim().lowercase()}:$port"

    fun fingerprint(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM).digest(blob)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$encoded"
    }

    data class KeyChange(val host: String, val port: Int, val pinned: String, val presented: String)

    @Volatile
    private var lastChange: KeyChange? = null

    fun takeKeyChange(host: String, port: Int): KeyChange? {
        val change = lastChange ?: return null
        if (entryKey(change.host, change.port) != entryKey(host, port)) return null
        lastChange = null
        return change
    }

    fun trust(
        context: Context,
        change: KeyChange,
        onAudit: (String) -> Unit = { DeployDiagnostics.recordError(it) },
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(entryKey(change.host, change.port), change.presented)
            .apply()
        onAudit("SSH host key for ${entryKey(change.host, change.port)} trusted by the user: ${change.presented}")
    }

    fun clear(context: Context, host: String, port: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(entryKey(host, port))
            .apply()
    }

    class TofuVerifier(
        context: Context,
        private val onAudit: (String) -> Unit = { DeployDiagnostics.recordError(it) },
    ) : HostKeyVerifier {
        private val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val presented = fingerprint(key)
            val entry = entryKey(hostname, port)
            val pinned = prefs.getString(entry, null)
            if (pinned == null) {
                prefs.edit().putString(entry, presented).apply()
                onAudit("SSH host key pinned for $entry: $presented")
                return true
            }
            if (pinned == presented) {
                if (lastChange?.let { entryKey(it.host, it.port) } == entry) lastChange = null
                return true
            }
            lastChange = KeyChange(hostname, port, pinned, presented)
            onAudit("SSH host key mismatch for $entry: expected $pinned, presented $presented")
            return false
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
            Collections.emptyList()
    }
}
