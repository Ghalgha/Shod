package com.shod.client.platform.android.deploy.ssh

import android.content.Context
import com.shod.client.deploy.ssh.SSH_SESSION_TIMEOUT_MS
import com.shod.client.deploy.ssh.connectSshTransport
import java.io.IOException
import java.security.Security
import java.util.concurrent.atomic.AtomicBoolean
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Android-specific SSH client creation: host-key verification, authentication
 * and socket timeouts. Deployment workflows receive an authenticated client
 * and remain independent from Android storage and UI logging.
 */
internal class AndroidSshClientFactory(
    private val context: Context,
    private val onAudit: (String) -> Unit = {},
    private val onRetry: (String) -> Unit = {},
) {
    fun create(
        host: String,
        user: String,
        password: String,
        port: Int = 22,
        privateKey: String = "",
        keyPassphrase: String = "",
        certificate: String = "",
    ): SSHClient {
        require(host.isNotBlank()) { "SSH host is empty" }
        require(user.isNotBlank()) { "SSH login is empty" }
        val keysMode = privateKey.isNotBlank()
        require(keysMode || password.isNotEmpty()) { "SSH password is empty" }
        require(port in 1..65535) { "Invalid SSH port: $port" }

        ensureBouncyCastle()
        val ssh = connectSshTransport(
            host = host,
            port = port,
            clientFactory = {
                SSHClient().apply {
                    addHostKeyVerifier(
                        SshHostKeyStore.TofuVerifier(context) { message -> onAudit(message) },
                    )
                }
            },
            onRetry = {
                onAudit("SSH handshake timeout at $host:$port; retrying once with a new socket")
                onRetry("SSH не ответил за 15 с; повтор подключения")
            },
        )

        try {
            runCatching { ssh.connection.keepAlive.keepAliveInterval = 10 }
            if (keysMode) {
                authenticateWithKey(ssh, user, privateKey, keyPassphrase, certificate)
            } else {
                authenticateWithPassword(ssh, user, password)
            }

            // Apply the long-running session timeout to the already connected socket.
            ssh.timeout = SSH_SESSION_TIMEOUT_MS
            ssh.socket.soTimeout = SSH_SESSION_TIMEOUT_MS
            ssh.transport.timeoutMs = SSH_SESSION_TIMEOUT_MS
            return ssh
        } catch (error: Exception) {
            runCatching { ssh.disconnect() }
            throw error
        }
    }

    private fun authenticateWithKey(
        ssh: SSHClient,
        user: String,
        privateKey: String,
        keyPassphrase: String,
        certificate: String,
    ) {
        try {
            val certificateText = certificate.takeIf(String::isNotBlank)
            onAudit(
                "SSH key mode: key=${privateKey.length}b, passphrase=${keyPassphrase.isNotBlank()}, " +
                    "certificate=${certificateText?.length ?: 0}b",
            )
            val passwordFinder = if (keyPassphrase.isNotBlank()) {
                PasswordUtils.createOneOff(keyPassphrase.toCharArray())
            } else {
                null
            }
            val keyProvider = ssh.loadKeys(privateKey, certificateText, passwordFinder)
            ssh.authPublickey(user, keyProvider)
        } catch (error: UserAuthException) {
            onAudit("SSH pubkey auth failed: ${error.message}")
            throw IOException("Auth fail (public key): ${error.message}", error)
        } catch (error: IOException) {
            onAudit("SSH key parse error: ${error.message}")
            throw IOException("Auth fail (key parse): ${error.message}", error)
        }
    }

    private fun authenticateWithPassword(ssh: SSHClient, user: String, password: String) {
        try {
            ssh.authPassword(user, password)
        } catch (passwordError: UserAuthException) {
            try {
                ssh.auth(
                    user,
                    listOf(
                        AuthKeyboardInteractive(
                            PasswordResponseProvider(
                                PasswordUtils.createOneOff(password.toCharArray()),
                            ),
                        ),
                    ),
                )
            } catch (keyboardError: UserAuthException) {
                onAudit(
                    "SSH password auth failed: password=${passwordError.message}; " +
                        "keyboard-interactive=${keyboardError.message}",
                )
                throw IOException("Auth fail (password): ${keyboardError.message}", keyboardError)
            }
        }
    }

    private companion object {
        val bcInitialized = AtomicBoolean(false)

        fun ensureBouncyCastle() {
            if (bcInitialized.compareAndSet(false, true)) {
                Security.removeProvider("BC")
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            }
        }
    }
}
