package com.shod.client.deploy.script

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployScriptPreflightTest {
    private fun script(): String {
        val candidates = listOf(
            File("app/src/main/assets/deploy.sh"),
            File("src/main/assets/deploy.sh"),
        )
        return candidates.first(File::isFile).readText().replace("\r\n", "\n")
    }

    private fun deployClientCode(): String {
        val candidates = listOf(
            "app/src/main/java/com/shod/client/platform/android/deploy/AndroidDeployWorkflow.kt",
            "app/src/main/java/com/shod/client/platform/android/deploy/AndroidDeployCoordinator.kt",
            "app/src/main/java/com/shod/client/deploy/artifact/DeployArtifactStager.kt",
            "app/src/main/java/com/shod/client/deploy/artifact/DeployConfigurationStager.kt",
            "app/src/main/java/com/shod/client/deploy/script/DeployScriptAsset.kt",
            "app/src/main/java/com/shod/client/deploy/transfer/SshDeployFileTransfer.kt",
        )
        return candidates
            .map { File(it) }
            .filter(File::isFile)
            .joinToString("\n") { it.readText().replace("\r\n", "\n") }
    }
    @Test
    fun `preflight performs working kernel probes`() {
        val deploy = script()
        assertTrue(deploy.contains("ip tuntap add dev \"\$probe_iface\" mode tun"))
        assertTrue(deploy.contains("setup_nat_and_firewall"))
        assertTrue(deploy.contains("iptables -w \"\$XT_WAIT\" -t nat -C POSTROUTING"))
        assertTrue(deploy.contains("probe_tun_support"))
        assertTrue(deploy.contains("prepare_uploaded_release()"))
        assertTrue(deploy.contains("run_platform_preflight"))
    }

    @Test
    fun `installer accepts the supported server architectures`() {
        val deploy = script()
        assertTrue(deploy.contains("x86_64|amd64"))
        assertTrue(deploy.contains("aarch64|arm64"))
        assertTrue(deploy.contains("armv7l|armv7|armhf"))
        assertTrue(deploy.contains("Поддерживаются: x86_64, aarch64, armv7l"))
    }

    @Test
    fun `client uploads the release before one atomic runtime switch`() {
        val deploy = script()
        val installBranch = deploy.substringAfter("install|--install|-i|update|--update|-up)")
        val uploadCheck = installBranch.indexOf("prepare_uploaded_release")
        val cleanup = installBranch.indexOf("shod_cleanup")
        val binary = installBranch.indexOf("setup_shod_binary")
        val preflight = installBranch.indexOf("run_platform_preflight")
        val firewall = installBranch.indexOf("setup_nat_and_firewall")
        assertTrue(uploadCheck >= 0)
        assertTrue(cleanup >= 0)
        assertTrue(binary >= 0)
        assertTrue(binary > preflight)
        assertTrue(binary > firewall)
        assertTrue(installBranch.contains("run_timed \"preflight\" run_platform_preflight"))
        assertTrue(cleanup > uploadCheck)
        assertTrue(binary > cleanup)
        assertTrue(installBranch.contains("local total_started=\$SECONDS\n            detect_os\n            run_timed \"зависимости\" install_prerequisites\n            require_runtime_tools"))

        val client = deployClientCode()
        assertFalse(client.contains("bash /tmp/deploy.sh prepare"))
        assertTrue(client.indexOf("/tmp/.shod-upload-server") < client.indexOf("bash /tmp/deploy.sh \${mode.scriptAction}"))
    }

    @Test
    fun `activation uses direct runtime paths and has no staged candidate`() {
        val deploy = script()
        assertTrue(deploy.contains("readonly UPLOAD_BINARY=\"/tmp/.shod-upload-server\""))
        assertTrue(deploy.contains("install -m 0755 \"\$UPLOAD_BINARY\" \"\$Shod_BIN\""))
        assertTrue(deploy.contains("install -m 0600 \"\$UPLOAD_ENV_FILE\" \"\$Shod_ENV_FILE\""))
        assertTrue(deploy.contains("rm -f \"\$Shod_BIN\""))
        assertTrue(deploy.contains("rm -rf \"\$Shod_LIB_DIR\""))
        // «Вместо 2.1.9» keeps the stock layout
        assertTrue(deploy.contains("_bin=/usr/local/bin/shod; _lib=/usr/local/lib/shod; _conf=/etc/shod"))
        assertTrue(deploy.contains("clear_runtime_config_preserving_database()"))
        assertFalse(deploy.contains("shod.next.XXXXXX"))
        assertFalse(deploy.contains("shod-stage"))
        assertFalse(deploy.contains("verify_staged_release"))
        assertFalse(deploy.contains("Shod_DEPLOY_READY_FOR_UPLOAD"))
        assertTrue(deploy.contains("probe_tun_support"))
    }

    @Test
    fun `reserved runtime directories recover from a failed previous install`() {
        val deploy = script()
        assertTrue(deploy.contains("ensure_shod_directory()"))
        assertTrue(deploy.contains("rm -rf -- \"\$path\" || die \"Не удалось очистить путь Shod: \$path\""))
        assertTrue(deploy.contains("ensure_shod_directory \"\$Shod_CONFIG_DIR\" 700"))
        assertTrue(deploy.contains("ensure_shod_directory \"\$Shod_LIB_DIR\" 755"))
        assertTrue(deploy.contains("setup_shod_environment() {\n    ensure_shod_directory \"\$Shod_CONFIG_DIR\" 700"))
        assertTrue(deploy.contains("install -m 0600 \"\$UPLOAD_ENV_FILE\" \"\$Shod_ENV_FILE\""))
    }

    @Test
    fun `redeploy removes old runtime config but preserves SQLite and its WAL files`() {
        val deploy = script()
        assertTrue(deploy.contains("readonly Shod_DATABASE_FILE=\"shod.db\""))
        assertTrue(deploy.contains("readonly Shod_DATABASE_WAL_FILE=\"shod.db-wal\""))
        assertTrue(deploy.contains("readonly Shod_DATABASE_SHM_FILE=\"shod.db-shm\""))
        assertTrue(deploy.contains("clear_runtime_config_preserving_database"))
        assertTrue(deploy.contains("\"\$Shod_DATABASE_FILE\"|\"\$Shod_DATABASE_WAL_FILE\"|\"\$Shod_DATABASE_SHM_FILE\"|\"\$Shod_LEGACY_MIGRATION_JSON\"|\"\$Shod_LEGACY_MIGRATION_IMPORTED_JSON\"|web_cert.pem|web_key.pem|letsencrypt-ip.env|runtime-archive) continue"))
        assertFalse(deploy.contains("find \"\$Shod_CONFIG_DIR\" -mindepth 1 -maxdepth 1"))
    }

    @Test
    fun `docker uses minimal capabilities and repeats probes inside container`() {
        val deploy = script()
        assertTrue(deploy.contains("--cap-drop ALL"))
        assertTrue(deploy.contains("--cap-add NET_ADMIN"))
        assertTrue(deploy.contains("--cap-add NET_RAW"))
        assertTrue(deploy.contains("--device /dev/net/tun:/dev/net/tun"))
        assertTrue(deploy.contains("--security-opt seccomp=unconfined"))
        assertTrue(deploy.contains("probe_docker_runtime"))
        assertTrue(deploy.split("--cap-drop ALL").size >= 3)
        assertTrue(deploy.contains("COPY network-up.sh @IMG_LIB@/network-up.sh"))
        assertTrue(deploy.contains("exec @IMG_BIN@ \\\"\$@\\\""))
        assertTrue(deploy.contains("s#@IMG_BIN@#\${Shod_IMAGE_BIN}#g; s#@IMG_LIB@#\${Shod_IMAGE_LIB_DIR}#g"))
        assertTrue(deploy.contains("_img_bin=/usr/local/bin/shod; _img_lib=/usr/local/lib/shod; _img_conf=/etc/shod"))
        assertTrue(deploy.contains("write_network_helper \"\$context_dir/network-up.sh\""))
    }

    @Test
    fun `docker image build retries DNS with Yandex resolvers`() {
        val deploy = script()
        assertTrue(deploy.contains("DOCKER_BUILD_TIMEOUT_SECONDS"))
        assertTrue(deploy.contains("docker build --network host"))
        assertTrue(deploy.contains("Shod_BUILD_DNS_PRIMARY:-"))
        assertTrue(deploy.contains("FROM alpine:3.21"))
        assertTrue(deploy.contains("apk add --no-cache --no-progress ca-certificates iproute2 iptables"))
        assertTrue(deploy.contains("Shod_BUILD_DNS_PRIMARY=77.88.8.8"))
        assertTrue(deploy.contains("Shod_BUILD_DNS_SECONDARY=77.88.8.1"))
        assertTrue(deploy.contains("Temporary failure resolving|Could not resolve|Name or service not known"))
    }

    @Test
    fun `deploy payload leaves DNS selection to the persisted server database`() {
        val deploy = script()
        val client = deployClientCode()
        assertFalse(client.contains(".put(\"dns\""))
        assertFalse(client.contains("dnsValue"))
        assertFalse(deploy.contains("Не удалось сохранить DNS из существующей SQLite-конфигурации"))
        assertTrue(client.contains("\"shod-deploy.json\" -> \"настройки доступа\""))
    }

    @Test
    fun `web health probe accepts the panel's unauthenticated response without creating sessions`() {
        val deploy = script()
        assertTrue(deploy.contains("200|301|302|401"))
        assertTrue(deploy.contains("${'$'}{scheme}://127.0.0.1:${'$'}{WEB_PORT}/"))
        assertFalse(deploy.contains("--data-binary @-"))
        assertFalse(deploy.contains("/api/login"))
        assertFalse(deploy.contains("NETWORK_READY_STAMP"))
    }

    @Test
    fun `configured network is verified after applying rules and after startup`() {
        val deploy = script()
        assertTrue(deploy.contains("verify_configured_network()"))
        assertTrue(deploy.split("verify_configured_network").size >= 4)
        assertTrue(deploy.contains("-j MASQUERADE >/dev/null 2>&1"))
        assertTrue(deploy.contains("-j TCPMSS --clamp-mss-to-pmtu 2>/dev/null"))
    }

    @Test
    fun `startup removes stale tunnel state and waits through the TUN initialization window`() {
        val deploy = script()
        assertTrue(deploy.contains("remove_shod_tun_interface()"))
        assertTrue(deploy.contains("timeout 2 ip link del \"\$Shod_IFACE\""))
        assertTrue(deploy.contains("write_tun_recovery_helper \"\$Shod_LIB_DIR/tun-recover.sh\""))
        assertTrue(deploy.contains("ExecStartPre=\${Shod_LIB_DIR}/tun-recover.sh"))
        assertTrue(deploy.contains("release_owned_peer_port()"))
        assertTrue(deploy.contains("shod_process_is_owned()"))
        assertTrue(deploy.contains("""UDP/\${'$'}PEER_PORT runtime PID \${'$'}pid; terminating"""))
        assertTrue(deploy.contains("readonly START_STABILITY_SECONDS=\"\${Shod_START_STABILITY_SECONDS:-4}\""))
        assertTrue(deploy.contains("StartLimitIntervalSec=60"))
        assertTrue(deploy.contains("RestartSec=3"))
        assertTrue(deploy.contains("COPY tun-recover.sh @IMG_LIB@/tun-recover.sh"))
        assertTrue(deploy.contains("@IMG_LIB@/tun-recover.sh; exec @IMG_BIN@"))
    }

    @Test
    fun `runtime mode switch removes Shod paths and force releases the configured peer port`() {
        val deploy = script()
        val client = deployClientCode()
        assertTrue(deploy.contains("docker_container_runs_shod()"))
        assertTrue(deploy.contains("docker top \"\$container\" -eo pid,args"))
        assertTrue(deploy.contains("docker-\${container_id}.scope"))
        assertTrue(deploy.contains("remove_all_shod_docker_containers"))
        assertTrue(deploy.contains("stop_all_running_shod_systemd_units"))
        assertTrue(deploy.contains("remove_all_shod_systemd_units"))
        assertTrue(deploy.contains("assert_peer_port_is_available"))
        assertTrue(deploy.contains("force_release_peer_port"))
        assertTrue(deploy.contains("docker_container_for_pid"))
        assertTrue(deploy.contains("docker rm -f \"\$container\""))
        assertTrue(deploy.contains("--label com.shod.managed=true"))
        assertTrue(deploy.contains("archive_shod_docker_state()"))
        assertTrue(deploy.contains("Shod_RUNTIME_ARCHIVE_DIR"))
        assertTrue(deploy.contains("runtime-archive) continue"))
        assertTrue(deploy.contains("force_stop_shod_processes"))
        assertTrue(deploy.contains("all_shod_process_pids"))
        assertTrue(deploy.contains("shod_peer_port_is_owned"))
        assertTrue(deploy.contains("shod_process_owns_peer_port"))
        assertTrue(deploy.contains("readlink \"/proc/\$pid/exe\""))
        assertTrue(deploy.contains("--config-dir \${Shod_CONFIG_DIR}"))
        assertTrue(deploy.contains("kill -KILL \"\$pid\""))
        assertTrue(deploy.contains("kill -TERM \"\$pid\""))
        assertTrue(deploy.contains("выполняется освобождение собственного runtime"))
        assertFalse(deploy.contains("unknown runtime; refusing to alter it"))
        assertFalse(deploy.contains("Shod не будет их удалять"))
        assertFalse(deploy.contains("pkill -"))
        assertFalse(deploy.contains("pgrep -x shod"))
        assertFalse(deploy.contains("ipt_del_repeat"))
        assertFalse(deploy.contains("nft delete table ip shod"))
        assertFalse(deploy.contains("fw_add_input_tcp \"\$SSH_PORT\""))
        assertFalse(deploy.contains("--dport \"\$SSH_PORT\" -m comment --comment \"\$IPT_COMMENT\""))
        assertTrue(client.contains("bash /tmp/deploy.sh \\${mode.scriptAction}"))
        assertFalse(client.contains("pkill -x shod"))
        assertFalse(client.contains("docker rm -f shod"))
        assertFalse(client.contains("nft delete table ip shod"))
    }

    @Test
    fun `runtime cleanup stops processes before deleting Shod runtime paths`() {
        val deploy = script()
        val cleanup = deploy.substringAfter("shod_cleanup() {").substringBefore("setup_sysctl()")
        val forceStop = deploy.substringAfter("force_stop_shod_processes() {")
            .substringBefore("force_release_peer_port()")

        assertTrue(forceStop.contains("kill -KILL \"\$pid\""))
        assertTrue(cleanup.indexOf("force_stop_shod_processes") <
            cleanup.indexOf("rm -f \"\$Shod_BIN\""))
        assertTrue(cleanup.contains("systemctl daemon-reload || die \"systemctl daemon-reload завершился ошибкой после удаления старого runtime\""))
    }

    @Test
    fun `systemd runtime discovery and shutdown tolerate expected nonzero states`() {
        val deploy = script()
        assertTrue(deploy.contains("if systemd_unit_runs_shod \"\$unit\"; then"))
        assertFalse(deploy.contains("systemd_unit_runs_shod \"\$unit\" &&"))
        assertTrue(deploy.contains("systemctl list-units --type=service --all --no-legend --plain 2>/dev/null || true"))
        assertTrue(deploy.contains("systemctl list-unit-files --type=service --no-legend --plain 2>/dev/null || true"))
        assertTrue(deploy.contains("if ! timeout 10 systemctl stop \"\$unit\" >/dev/null 2>&1; then"))
        assertTrue(deploy.contains("systemctl is-active --quiet \"\$unit\"; then\n            die \"Не удалось остановить systemd runtime Shod: \$unit\""))
    }

    @Test
    fun `systemd service leaves resource limits to the VPS defaults`() {
        val deploy = script()
        assertFalse(deploy.contains("LimitNOFILE="))
        assertFalse(deploy.contains("LimitMEMLOCK="))
        assertFalse(deploy.contains("LimitNPROC="))
        assertFalse(deploy.contains("TasksMax="))
    }

    @Test
    fun `web panel prefers renewable trusted IP TLS with self signed fallback`() {
        val deploy = script()
        assertTrue(deploy.contains("--preferred-profile shortlived"))
        assertTrue(deploy.contains("--ip-address \"\$public_ip\""))
        assertTrue(deploy.contains("--force-renewal"))
        assertTrue(deploy.contains("openssl x509 -noout -checkend \"\$LE_RENEW_BEFORE_SECONDS\""))
        assertTrue(deploy.contains("OnUnitInactiveSec=\${LE_RENEW_EVERY}"))
        assertTrue(deploy.contains("_le_before=259200; _le_every=24h"))
        assertTrue(deploy.contains("fw_add_input_tcp \"\$LE_HTTP_PORT\""))
        assertTrue(deploy.contains("web_cert.pem|web_key.pem|letsencrypt-ip.env"))
        assertTrue(deploy.contains("systemctl kill --kill-who=main --signal USR1 \"\$UNIT_NAME\""))
        assertTrue(deploy.contains("docker kill --signal USR1 \"\$UNIT_NAME\""))
        assertTrue(deploy.contains("Environment=Shod_UNIT_NAME=\${Shod_UNIT}"))
        assertTrue(deploy.contains("issuer=\"\${issuer#issuer=}\""))
        assertTrue(deploy.contains("subject=\"\${subject#subject=}\""))
        assertTrue(deploy.contains("! python3 -c 'import ensurepip'"))
        assertTrue(deploy.contains("apt) python_packages=(python3 python3-venv)"))
        assertTrue(deploy.contains("pkg_install_with_refresh()"))
        assertTrue(deploy.contains("pkg_install_with_refresh \"\${python_packages[@]}\""))
        assertTrue(deploy.contains("local ip=\"\$1\" first second third fourth value"))
        assertTrue(deploy.contains("self-signed fallback"))
    }

    @Test
    fun `certificate ownership keeps user TLS untouched and renews only Shod certificates`() {
        val deploy = script()
        assertTrue(deploy.contains("certificate_is_shod_rcgen()"))
        assertTrue(deploy.contains("certificate_is_shod_letsencrypt()"))
        assertTrue(deploy.contains("web_certificate_is_shod_managed()"))
        assertTrue(deploy.contains("web_certificate_is_user_managed()"))
        assertTrue(deploy.contains("CN=rcgen self signed cert"))
        assertTrue(deploy.contains("O=Let's Encrypt"))
        assertTrue(deploy.contains("IP Address:\${saved_ip}"))
        assertTrue(deploy.contains("rm -f -- \"\$Shod_LE_STATE_FILE\""))
        assertTrue(deploy.contains("disable_letsencrypt_renewal_timer"))
        assertTrue(deploy.contains("Shod не изменяет его и отключает собственное продление"))
    }

    @Test
    fun `single server preflight refuses foreign interfaces and ports before cutover`() {
        val deploy = script()
        fun body(name: String) = deploy.substringAfter("$name() {").substringBefore("\\n}\\n")
        val p = body("main_preflight")
        assertTrue(p.contains("refuse_anothers_server"))
        assertTrue(p.contains("refuse_foreign_port_holder u \"\$PEER_PORT\""))
        assertTrue(p.contains("refuse_foreign_port_holder t \"\$WEB_PORT\""))
        val install = deploy.substringAfter("install|--install|-i|update|--update|-up)")
        assertTrue(install.indexOf("main_preflight") in 0 until install.indexOf("shod_cleanup"))
        // a live interface another process holds (its fdinfo names it: "iff:") is never deleted as a leftover
        val tun = body("tun_holder_pids")
        assertTrue(tun.contains("-lname /dev/net/tun") && tun.contains("iff:"))
        val remove = body("remove_shod_tun_interface")
        assertTrue(remove.indexOf("foreign_tun_holders") in 0 until remove.indexOf("ip link del"))
        val foreignTunGuard = body("refuse_anothers_server")
        assertTrue(foreignTunGuard.contains("foreign_tun_holders"))
        assertFalse(deploy.contains("panel_units_claiming"))
        assertFalse(deploy.contains("SWG_IFACE"))
    }

    @Test
    fun `the server check reads only and runs before the installer prints or logs anything`() {
        val deploy = script()
        fun body(name: String) = deploy.substringAfter("$name() {").substringBefore("\n}\n")
        // the last main(): deploy.sh writes a helper script with its own main() further up
        val main = deploy.substringAfterLast("\nmain() {").substringBefore("\n}\n")
        val check = main.indexOf("= inventory ]")
        assertTrue(check >= 0)
        assertTrue(check < main.indexOf("echo \"╔") && check < main.indexOf("\$LOG_FILE"))
        val readers = listOf("do_inventory", "inv_place", "main_place_strangers", "server_ports_in", "pid_in_cgroups", "foreign_tun_holders")
        for (name in readers) {
            val b = body(name)
            for (write in listOf("mkdir", "rm ", ">>", "tee ", "systemctl stop", "systemctl disable", "docker stop", "docker rm", "kill ", "ip link del", "iptables")) {
                assertFalse("$name: $write", b.contains(write))
            }
        }
        assertTrue(body("do_inventory").trimEnd().endsWith("inv_line end"))
    }

    @Test
    fun `the standard place refuses a server it did not install instead of wiping it`() {
        val deploy = script()
        fun body(name: String) = deploy.substringAfter("$name() {").substringBefore("\n}\n")
        // main mode's cleanup stops, kills and deletes every shod unit, container and process it matches: anything of
        // that kind that is not the standard place's own is refused before it runs
        val preflight = body("main_preflight")
        assertTrue(preflight.contains("main_place_strangers"))
        // removing the standard place runs the same cleanup: refused the same way, before anything stops
        val uninstall = body("do_uninstall")
        assertTrue(uninstall.indexOf("main_place_strangers") in 0 until uninstall.indexOf("remove_all_shod_docker_containers"))
        // a unit between crash restarts is a running server, not a stopped leftover
        assertTrue(body("main_place_strangers").contains("unit_live"))
        val install = deploy.substringAfter("install|--install|-i|update|--update|-up)")
        assertTrue(install.indexOf("main_preflight") in 0 until install.indexOf("shod_cleanup"))
        // 2.1.9's cascade helper (/usr/local/bin/shod-cascade) matches the stock pattern but is the standard place's own
        val units = Regex("STANDARD_PLACE_UNITS=\"([^\"]+)\"").find(deploy)!!.groupValues[1].split(" ")
        for (unit in listOf("shod.service", "shod-cascade.service", "shod-letsencrypt.service", "shod-docker-prereq.service")) {
            assertTrue(unit, unit in units)
        }
        val strangers = body("main_place_strangers")
        assertTrue(strangers.contains("list_shod_systemd_units") && strangers.contains("docker_container_runs_shod") &&
            strangers.contains("all_shod_process_pids"))
        // what the user reads no longer names a version for a mode
        assertFalse(deploy.contains("«Рядом с 2.1.9»") || deploy.contains("«Вместо 2.1.9»"))
    }

    @Test
    fun `installer contains only the standard Shod server architecture`() {
        val deploy = script()
        assertTrue(deploy.contains("_name=shod; _iface=shod1"))
        assertTrue(deploy.contains("inv_place standard shod.service shod"))
        for (retired in listOf("Shod_ROLE", "Shod_INSTANCE", "shod25", "SWG_IFACE", "swg-shod-", "panel_units_claiming")) {
            assertFalse("retired integration: $retired", deploy.contains(retired))
        }
        assertFalse("removed side-server guard must not be called", deploy.contains("refuse_legacy_side_server"))
        assertTrue(deploy.contains("refuse_anothers_server"))
        assertTrue(deploy.contains("refuse_foreign_port_holder"))
    }
}
