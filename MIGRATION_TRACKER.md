# Shodarchive → Shod: реестр переноса

Источник: `Ghalgha/Shodarchive`, `main`, commit `b7f02d26ff3cf37708d1b607ad7cca67ef71f709`.
Цель: `Ghalgha/Shod`, `migration/phase-1-foundation`, исходный HEAD реестра `a0a4687583af475c86f3c6444cdf958891d96144`.

Статусы:
- `НЕ ПРОВЕРЕН` — содержимое и его представление в целевом проекте ещё не сопоставлены.
- `ТОЧНОЕ СОВПАДЕНИЕ` — в целевом дереве есть blob с тем же SHA; архитектурная пригодность отдельно не оценена.
- `ПЕРЕНЕСЁН / РАСПРЕДЕЛЁН` — только после проверки всего содержимого и его функционального эквивалента в целевом проекте.
- `НЕ ПЕРЕНОСИТЬ` — только с зафиксированной причиной (дубль, устаревшее, неиспользуемое и т. п.).

Правило: строка получает финальный статус только после проверки содержимого исходного файла и поиска его логики/ресурсов во всём целевом дереве. Совпадение имени или наличие похожего файла само по себе не считается переносом.

Всего файлов источника: **359**. Побайтовые совпадения blob в целевом репозитории: **115**. Совпадения путей: **91**.

## Список файлов

| № | Файл источника | SHA исходного файла | Статус |
|---:|---|---|---|
| 1 | `.gitattributes` | `16e50b1c5a0cb98910dabc2ae0c3b2eca49ed844` | НЕ ПРОВЕРЕН |
| 2 | `.github/workflows/ci.yml` | `1cdc496180a8fb258c6c619c77d1e8fc00bfdfa6` | НЕ ПРОВЕРЕН |
| 3 | `.gitignore` | `62c0626eba3467e66a69b8d7defc7bde5c905f01` | НЕ ПРОВЕРЕН |
| 4 | `app/build.gradle.kts` | `9304dd5cfd58e5d8835f0eeda6d1cc9547048271` | ТОЧНОЕ СОВПАДЕНИЕ |
| 5 | `app/lint.xml` | `b9d9033d384c37a7d1b11989d3b7b79a5d07ff89` | НЕ ПРОВЕРЕН |
| 6 | `app/proguard-rules.pro` | `dbed9f910eb52233f14ef2815aa04a971918cab7` | ТОЧНОЕ СОВПАДЕНИЕ |
| 7 | `app/src/androidTest/java/com/shod/client/DeployScriptAssetTest.kt` | `d9a590f34008ba74c38a8ee04287c2f1b1ecf873` | НЕ ПРОВЕРЕН |
| 8 | `app/src/androidTest/java/com/shod/client/DesignCompatibilityTest.kt` | `9f03bd4d4e24fd81716caf51c6f395acc416d1c9` | ТОЧНОЕ СОВПАДЕНИЕ |
| 9 | `app/src/androidTest/java/com/shod/client/HappInterfaceTest.kt` | `7ef94bb9c4543612369e29c85df21f022914a34a` | ТОЧНОЕ СОВПАДЕНИЕ |
| 10 | `app/src/androidTest/java/com/shod/client/ProbeTunTransportTest.kt` | `8810c9b2317470c01983a4c49b128425a642d745` | НЕ ПРОВЕРЕН |
| 11 | `app/src/androidTest/java/com/shod/client/ReviewedDeployInterfaceTest.kt` | `a30cc98dc7407dc95a45acf1507fa909023030fd` | ТОЧНОЕ СОВПАДЕНИЕ |
| 12 | `app/src/androidTest/java/com/shod/client/RoutingModeInteractionTest.kt` | `a502804e95ca3ab8165eaf51b837a1f56c94993f` | ТОЧНОЕ СОВПАДЕНИЕ |
| 13 | `app/src/androidTest/java/com/shod/client/SettingsStoreSshPortTest.kt` | `198eda51d2caf7790c6314f3fc637b0d82aee643` | ТОЧНОЕ СОВПАДЕНИЕ |
| 14 | `app/src/androidTest/java/com/shod/client/SshConnectionSmokeTest.kt` | `f18560f5e152aa8cced27ff0ddc59f54d97f842e` | ТОЧНОЕ СОВПАДЕНИЕ |
| 15 | `app/src/androidTest/java/com/shod/client/SwitchRestorationTest.kt` | `5c1b7beccfc0cfc848f5066dcf7a363ef8228b02` | ТОЧНОЕ СОВПАДЕНИЕ |
| 16 | `app/src/androidTest/java/com/shod/client/VkAccountUiTest.kt` | `8060c95de1cb7bf661402d48dda4af59dc4cfa50` | ТОЧНОЕ СОВПАДЕНИЕ |
| 17 | `app/src/debug/AndroidManifest.xml` | `323e7fc8e4637d243200422c643843ecc92fca68` | НЕ ПРОВЕРЕН |
| 18 | `app/src/debug/res/values/strings.xml` | `1eccaa5a274c5d9339b5a25f02bc70756ac9b456` | НЕ ПРОВЕРЕН |
| 19 | `app/src/main/AndroidManifest.xml` | `3c4c005a2dd478f113a026dbcdc2c00163aca5ca` | НЕ ПРОВЕРЕН |
| 20 | `app/src/main/assets/deploy.sh` | `c1753efc9ff4a6e4efac05df14b2ec615e05a9d1` | ТОЧНОЕ СОВПАДЕНИЕ |
| 21 | `app/src/main/assets/domains.txt` | `d52d29657e34ffa13544323087ed7f20b344737a` | ТОЧНОЕ СОВПАДЕНИЕ |
| 22 | `app/src/main/assets/licenses/primp-LICENSE` | `8454fa7eea7ba29de11efb579da5c754c0b597db` | НЕ ПРОВЕРЕН |
| 23 | `app/src/main/assets/licenses/shod-LICENSE` | `5cc8cea945d6c9b51c89ba7253c279204df4f998` | НЕ ПРОВЕРЕН |
| 24 | `app/src/main/assets/server-downloads.json` | `f344ddbbb985c9d0b57b46a0c25fcdbdb93e7ad3` | ТОЧНОЕ СОВПАДЕНИЕ |
| 25 | `app/src/main/assets/shod.server-provenance.json` | `a72cf16c05cbb743c25d4468b410d86afc258f59` | ТОЧНОЕ СОВПАДЕНИЕ |
| 26 | `app/src/main/java/com/shod/client/AppRoutingPresets.kt` | `80a92ba959f972d9e09928a9732956711c110965` | НЕ ПРОВЕРЕН |
| 27 | `app/src/main/java/com/shod/client/AppUpdate.kt` | `7ec92efaa5c4fb6b3677683470a0395fbd56d751` | НЕ ПРОВЕРЕН |
| 28 | `app/src/main/java/com/shod/client/BatteryOptimizationPromptPolicy.kt` | `c2910c4f9c962d78db76a71fb2a007227cd50888` | НЕ ПРОВЕРЕН |
| 29 | `app/src/main/java/com/shod/client/BatterySaver.kt` | `3a875f510f7b6c895fd4d362eaa1e4727b4324a8` | НЕ ПРОВЕРЕН |
| 30 | `app/src/main/java/com/shod/client/CaptchaCancelReceiver.kt` | `279d5d8b3ad6b6d4d6769a3114351e61dbcd95a4` | НЕ ПРОВЕРЕН |
| 31 | `app/src/main/java/com/shod/client/CaptchaUriPolicy.kt` | `17c1fd46219c4fc5794e6a8fa00d03a0b1c89b60` | НЕ ПРОВЕРЕН |
| 32 | `app/src/main/java/com/shod/client/CaptchaWebViewManager.kt` | `de0d31c12c7dd761f1280059dacafeaf458d2260` | НЕ ПРОВЕРЕН |
| 33 | `app/src/main/java/com/shod/client/ConnectionSource.kt` | `1cf7931eb0613df1850c4d4ab593baf25187d688` | НЕ ПРОВЕРЕН |
| 34 | `app/src/main/java/com/shod/client/ConnectionStartPolicy.kt` | `641b42952ea9e60f995f8af067f012be629606f6` | НЕ ПРОВЕРЕН |
| 35 | `app/src/main/java/com/shod/client/Constants.kt` | `ecf7f3c3f7eb2744e45ce0552554378b30a900a5` | ТОЧНОЕ СОВПАДЕНИЕ |
| 36 | `app/src/main/java/com/shod/client/DeployManager.kt` | `379011635522e28c5b45fa33d6d6752cba8d852e` | НЕ ПРОВЕРЕН |
| 37 | `app/src/main/java/com/shod/client/DeployScopedDataStore.kt` | `e128165879de9e22ee4d8694e2a4947d6778d0f7` | НЕ ПРОВЕРЕН |
| 38 | `app/src/main/java/com/shod/client/DtlsLogParser.kt` | `0712903af4f9096d4442ee76a6e08a1e8079ec6b` | НЕ ПРОВЕРЕН |
| 39 | `app/src/main/java/com/shod/client/MainActivity.kt` | `4d49e89ef91fda4d6edb4e03d44f8a77119f4ceb` | НЕ ПРОВЕРЕН |
| 40 | `app/src/main/java/com/shod/client/ManlCaptchaActivity.kt` | `8c6e4cf70d1415817099b0bc8334c1d9afd255cb` | НЕ ПРОВЕРЕН |
| 41 | `app/src/main/java/com/shod/client/ManlCaptchaWebViewManager.kt` | `3071d5d18f49d63fbaeaec4d5de620ff5f3f9518` | НЕ ПРОВЕРЕН |
| 42 | `app/src/main/java/com/shod/client/NativeTunnelLogParser.kt` | `d064982325ab89fec247f9e94266fd8fd8dcd9f9` | НЕ ПРОВЕРЕН |
| 43 | `app/src/main/java/com/shod/client/NetworkPauseGate.kt` | `2aa5ecdaa79d278e29a623428eb04f27df25fe31` | НЕ ПРОВЕРЕН |
| 44 | `app/src/main/java/com/shod/client/PhysicalNetworkTracker.kt` | `d352e0cbc49d578b7c31eb424d990a9c4413d05c` | НЕ ПРОВЕРЕН |
| 45 | `app/src/main/java/com/shod/client/PingResultsCache.kt` | `727d5b65403c6e582f26353149e11b473bc5dd6e` | НЕ ПРОВЕРЕН |
| 46 | `app/src/main/java/com/shod/client/ProbeOutputReader.kt` | `bb36113d6f5b4d6caf4c8f8871dd87a55b357643` | НЕ ПРОВЕРЕН |
| 47 | `app/src/main/java/com/shod/client/ProbeTunTransport.kt` | `d592de041440bf26165e1d3c2ad18d424e830fa2` | НЕ ПРОВЕРЕН |
| 48 | `app/src/main/java/com/shod/client/ProfilePingController.kt` | `df8bf5175266f1b308d5e8440737808b123b363d` | НЕ ПРОВЕРЕН |
| 49 | `app/src/main/java/com/shod/client/ProfileTunnelProbe.kt` | `73f5549efba3fc52bcd1289be9e214fb7543cd6d` | НЕ ПРОВЕРЕН |
| 50 | `app/src/main/java/com/shod/client/QuickToggleTileService.kt` | `3479ca76c65e00694b02773573eeb65e9dc7f92d` | НЕ ПРОВЕРЕН |
| 51 | `app/src/main/java/com/shod/client/ReadOnlyProfileDataStore.kt` | `966894fd65e7bd3ab0471dfe9211a103d6aeb611` | НЕ ПРОВЕРЕН |
| 52 | `app/src/main/java/com/shod/client/SavedVkAccounts.kt` | `c80ec9920106c8d1fafea071050209b515e3a307` | НЕ ПРОВЕРЕН |
| 53 | `app/src/main/java/com/shod/client/SecureStringStore.kt` | `3701c11b8287aece109dd9efc8286ea5eec0ad7d` | НЕ ПРОВЕРЕН |
| 54 | `app/src/main/java/com/shod/client/ServerCoreCache.kt` | `9e8e4611cdee37cadbbd69f8dba7fa17306b1fce` | НЕ ПРОВЕРЕН |
| 55 | `app/src/main/java/com/shod/client/ServerRoutingRule.kt` | `448ed41e659d651cbd6c7be7d17320c0eb5783e4` | НЕ ПРОВЕРЕН |
| 56 | `app/src/main/java/com/shod/client/SettingsStore.kt` | `35268e2ab850ed3e478a76b3bab55e911f165885` | НЕ ПРОВЕРЕН |
| 57 | `app/src/main/java/com/shod/client/ShodApplication.kt` | `9cd1e6fa259784faad130dea967689a56273127e` | НЕ ПРОВЕРЕН |
| 58 | `app/src/main/java/com/shod/client/SshHostKeyStore.kt` | `e56ba7f7d8880bfa81b48f1caa605d04ef64c0c6` | НЕ ПРОВЕРЕН |
| 59 | `app/src/main/java/com/shod/client/StoredSecretState.kt` | `4bee276f46da4d67e510b2a796c5b158e76e2e86` | НЕ ПРОВЕРЕН |
| 60 | `app/src/main/java/com/shod/client/StreamDiagnosticParser.kt` | `2737a7b002cb6117c5835ce25af7f59a8d2928dc` | НЕ ПРОВЕРЕН |
| 61 | `app/src/main/java/com/shod/client/SubscriptionImporter.kt` | `6069515899dc4bb459373657e45b47505ce8a61b` | НЕ ПРОВЕРЕН |
| 62 | `app/src/main/java/com/shod/client/SubscriptionSync.kt` | `de912f9117f30af281cf2df136ae1ca8e7a50235` | НЕ ПРОВЕРЕН |
| 63 | `app/src/main/java/com/shod/client/Theme.kt` | `04a76c3198c668e2cf91f30f5384fea12df57196` | НЕ ПРОВЕРЕН |
| 64 | `app/src/main/java/com/shod/client/ToastExt.kt` | `5201e2009def30071b52650fe0bf65afdbce830f` | НЕ ПРОВЕРЕН |
| 65 | `app/src/main/java/com/shod/client/TrafficDiagnosticParser.kt` | `0e6566c7a109413a7c803d8477326d5053eaabef` | НЕ ПРОВЕРЕН |
| 66 | `app/src/main/java/com/shod/client/TunnelEventParser.kt` | `158b7aacd82a5b83d8447c29afb93a0a034be2e7` | НЕ ПРОВЕРЕН |
| 67 | `app/src/main/java/com/shod/client/TunnelLifecycleState.kt` | `7052cb982e502861c8dfe630eaf47b5ae267993b` | НЕ ПРОВЕРЕН |
| 68 | `app/src/main/java/com/shod/client/TunnelLogPolicy.kt` | `7bfb8657f3676b02ce2962940a53fb1c2840dd1f` | НЕ ПРОВЕРЕН |
| 69 | `app/src/main/java/com/shod/client/TunnelManager.kt` | `bf504505072bc03ee46930bd0ae63ab2799e3d98` | НЕ ПРОВЕРЕН |
| 70 | `app/src/main/java/com/shod/client/TunnelNotificationText.kt` | `0c6244738f64cf0efd69a83850b886a3bcf64e42` | НЕ ПРОВЕРЕН |
| 71 | `app/src/main/java/com/shod/client/TunnelPasswordStorage.kt` | `e4d53ccd686a61d791c7b6c9439441c06cb3cf60` | НЕ ПРОВЕРЕН |
| 72 | `app/src/main/java/com/shod/client/TunnelProbePacket.kt` | `09968dec6ce1de90259836237653739bc7b6cb02` | НЕ ПРОВЕРЕН |
| 73 | `app/src/main/java/com/shod/client/TunnelQuickActions.kt` | `7613ee5c293ccf2c413ab46e0f2c80c50afa9a0e` | НЕ ПРОВЕРЕН |
| 74 | `app/src/main/java/com/shod/client/TunnelScreenSnapshot.kt` | `83242d315b165ed39dc626a0d5f0f967dc5c0ccd` | НЕ ПРОВЕРЕН |
| 75 | `app/src/main/java/com/shod/client/TunnelService.kt` | `3212e645cf4c90ed3ad25120226f26c976163fa4` | НЕ ПРОВЕРЕН |
| 76 | `app/src/main/java/com/shod/client/TunnelServicePolicy.kt` | `9d3b31ae999bb8572ad0b405e9536511c700fb74` | НЕ ПРОВЕРЕН |
| 77 | `app/src/main/java/com/shod/client/TunnelSessionId.kt` | `829689d198ff8a43fe5c784277984d4843aa874d` | НЕ ПРОВЕРЕН |
| 78 | `app/src/main/java/com/shod/client/TunnelStartupProgress.kt` | `08ec1f94b979e4f9060522698694b525aeed802a` | НЕ ПРОВЕРЕН |
| 79 | `app/src/main/java/com/shod/client/TunnelTraffic.kt` | `4ee00258be3a071c6345733aa7cbc840daf4692a` | НЕ ПРОВЕРЕН |
| 80 | `app/src/main/java/com/shod/client/TunRecoveryPolicy.kt` | `e47edc58e394c7a5b62d9b7c7586aba103b0178a` | НЕ ПРОВЕРЕН |
| 81 | `app/src/main/java/com/shod/client/TunVpnService.kt` | `a392d65dd48e7545a313506a99f753e0140f4b66` | НЕ ПРОВЕРЕН |
| 82 | `app/src/main/java/com/shod/client/TurnSubnetRegistry.kt` | `d3da334a85488e5710ac982887a66a5025cce078` | НЕ ПРОВЕРЕН |
| 83 | `app/src/main/java/com/shod/client/TurnTransportPolicy.kt` | `4321e965c2ac0f136dde43f47cbbd18784f4fe96` | НЕ ПРОВЕРЕН |
| 84 | `app/src/main/java/com/shod/client/TvDevicePolicy.kt` | `72778950dc775bdb761d27c45329c6107483c5f3` | НЕ ПРОВЕРЕН |
| 85 | `app/src/main/java/com/shod/client/ui/AppSelectionOrdering.kt` | `9afed32923a2e896d89896b263b7b74f6d244712` | НЕ ПРОВЕРЕН |
| 86 | `app/src/main/java/com/shod/client/ui/background/AppBackdrop.kt` | `2cf5eacc9fb1f5c9f8db8ba8b44333cb0aa45a13` | ТОЧНОЕ СОВПАДЕНИЕ |
| 87 | `app/src/main/java/com/shod/client/ui/components/ApplicationRow.kt` | `fe536b58208eb7844bed9a7804a0575bbb28bac1` | НЕ ПРОВЕРЕН |
| 88 | `app/src/main/java/com/shod/client/ui/components/AppSectionCard.kt` | `8014117b8206535d0fad5214757cec9065dcec77` | НЕ ПРОВЕРЕН |
| 89 | `app/src/main/java/com/shod/client/ui/components/ContentScreen.kt` | `ffa6a402cd741499b24ae2bc35e1ea41896bcb8b` | ТОЧНОЕ СОВПАДЕНИЕ |
| 90 | `app/src/main/java/com/shod/client/ui/components/HeaderActions.kt` | `d0a411047b334146761f2962134e5094d9e72352` | НЕ ПРОВЕРЕН |
| 91 | `app/src/main/java/com/shod/client/ui/components/SegmentedControl.kt` | `e70cba5114b80549bf908f08f452cfa2919da18d` | ТОЧНОЕ СОВПАДЕНИЕ |
| 92 | `app/src/main/java/com/shod/client/ui/components/SettingRow.kt` | `7a57a540df7b9485dfbcf07a3632a008a2c7fa30` | ТОЧНОЕ СОВПАДЕНИЕ |
| 93 | `app/src/main/java/com/shod/client/ui/components/States.kt` | `0a8572de0a71fc1620ceb57a98f59aa4d5ceae4c` | ТОЧНОЕ СОВПАДЕНИЕ |
| 94 | `app/src/main/java/com/shod/client/ui/components/TunnelDropdownSetting.kt` | `55cb7adde6ad2f821c402eefcf06ef92dc30d22f` | ТОЧНОЕ СОВПАДЕНИЕ |
| 95 | `app/src/main/java/com/shod/client/ui/components/TunnelProtocolChip.kt` | `d24cb37384c16831d9def9c81e03664da16d6b84` | ТОЧНОЕ СОВПАДЕНИЕ |
| 96 | `app/src/main/java/com/shod/client/ui/components/TunnelWorkersSlider.kt` | `0f0a75f85c460ca20f2408f04ba596b66a846185` | ТОЧНОЕ СОВПАДЕНИЕ |
| 97 | `app/src/main/java/com/shod/client/ui/controllers/ConnectionController.kt` | `da6f8488358ad843b6141b43b268d4ab23d5a2bb` | НЕ ПРОВЕРЕН |
| 98 | `app/src/main/java/com/shod/client/ui/deploy/DeployCheckState.kt` | `341ac357fd175b06711bcda0e02c127c362e64a4` | НЕ ПРОВЕРЕН |
| 99 | `app/src/main/java/com/shod/client/ui/deploy/DeployController.kt` | `4dd1207fe17f0d49893039bd5f4998e7a440d4bf` | НЕ ПРОВЕРЕН |
| 100 | `app/src/main/java/com/shod/client/ui/deploy/DeployPlan.kt` | `3ba88c38ff013673bb84ed252eac233af0a42d54` | НЕ ПРОВЕРЕН |
| 101 | `app/src/main/java/com/shod/client/ui/deploy/DeployScreen.kt` | `fce80a175a2b4d27fdeb6c6a980d865d968e7cc4` | НЕ ПРОВЕРЕН |
| 102 | `app/src/main/java/com/shod/client/ui/deploy/operations/DeployOperations.kt` | `af164fb2b77d7ab6fa2f17653812e7e0f55d8654` | НЕ ПРОВЕРЕН |
| 103 | `app/src/main/java/com/shod/client/ui/deploy/operations/DeployScript.kt` | `0dc9d35716345b4ec3e5fb6acf92f86789a7f96d` | НЕ ПРОВЕРЕН |
| 104 | `app/src/main/java/com/shod/client/ui/deploy/operations/SshTransportConnection.kt` | `e3f3d0f0a7d26e4611f8c9cbb74c913c1facb76c` | НЕ ПРОВЕРЕН |
| 105 | `app/src/main/java/com/shod/client/ui/deploy/ServerInventory.kt` | `8f929d3eec4a1372bce654375573ed2109fb2275` | НЕ ПРОВЕРЕН |
| 106 | `app/src/main/java/com/shod/client/ui/design/ExtendedColors.kt` | `59410f63afd4f36260a69705052ecbce129688c2` | ТОЧНОЕ СОВПАДЕНИЕ |
| 107 | `app/src/main/java/com/shod/client/ui/design/Motion.kt` | `0875bf164dd298058a4d10dfa5e82e81b4356fdc` | ТОЧНОЕ СОВПАДЕНИЕ |
| 108 | `app/src/main/java/com/shod/client/ui/design/Shapes.kt` | `f53fcf5a99c4e95fa227e888408dc0ddca8c15bc` | ТОЧНОЕ СОВПАДЕНИЕ |
| 109 | `app/src/main/java/com/shod/client/ui/design/Sizes.kt` | `44d309fa393960bbebf6b8fa35457f0a7e4bb145` | ТОЧНОЕ СОВПАДЕНИЕ |
| 110 | `app/src/main/java/com/shod/client/ui/design/Spacing.kt` | `615c3a45e3b67e40cf4357473b42e3b78e166cd2` | ТОЧНОЕ СОВПАДЕНИЕ |
| 111 | `app/src/main/java/com/shod/client/ui/dialogs/AppUpdateDialog.kt` | `8362c2dbbbdb207edaca3a7ad244df2a55886160` | НЕ ПРОВЕРЕН |
| 112 | `app/src/main/java/com/shod/client/ui/dialogs/AutoVkRiskDialog.kt` | `0073035064465c1ad24e77d1f5bcdd1c01b14aaa` | ТОЧНОЕ СОВПАДЕНИЕ |
| 113 | `app/src/main/java/com/shod/client/ui/dialogs/DeploySecretsDialog.kt` | `12376ea4b5da119158e897a5999910ec1e33b6f4` | НЕ ПРОВЕРЕН |
| 114 | `app/src/main/java/com/shod/client/ui/dialogs/DockerInstallInfoDialog.kt` | `114c49b2b67c895263c7f78dceebc77b3304b2f7` | ТОЧНОЕ СОВПАДЕНИЕ |
| 115 | `app/src/main/java/com/shod/client/ui/dialogs/HashesDialog.kt` | `f17c7997ac137d5b8911b47a79c75afd15fa0099` | НЕ ПРОВЕРЕН |
| 116 | `app/src/main/java/com/shod/client/ui/dialogs/HashModeInfoDialog.kt` | `ab5a130a41958528ec9d9cdce503721c5d5a5af8` | ТОЧНОЕ СОВПАДЕНИЕ |
| 117 | `app/src/main/java/com/shod/client/ui/dialogs/HostKeyChangedDialog.kt` | `0d2719805d9c5f3c6cd5687ec7c44e2018c9b4f7` | НЕ ПРОВЕРЕН |
| 118 | `app/src/main/java/com/shod/client/ui/dialogs/ImportExportDialog.kt` | `f0102a1e2d8edb6ef1f185a4a2d86f2de6dbfeff` | ТОЧНОЕ СОВПАДЕНИЕ |
| 119 | `app/src/main/java/com/shod/client/ui/dialogs/QrScannerDialog.kt` | `8283595e084f5d5e31159ff9662e62c989436718` | ТОЧНОЕ СОВПАДЕНИЕ |
| 120 | `app/src/main/java/com/shod/client/ui/dialogs/SecretsDialog.kt` | `3c38476ea344d6ae623cba5d0f778f9d771d2e2e` | НЕ ПРОВЕРЕН |
| 121 | `app/src/main/java/com/shod/client/ui/dialogs/ServerCheckDialog.kt` | `7a67e01bac15ee2c078d5f2391b2461f04000cba` | ТОЧНОЕ СОВПАДЕНИЕ |
| 122 | `app/src/main/java/com/shod/client/ui/dialogs/ServerRemovalDialogs.kt` | `ebed9bb3efdd90d938f8b09269dd4daad7aca920` | НЕ ПРОВЕРЕН |
| 123 | `app/src/main/java/com/shod/client/ui/dialogs/SshKeysDialog.kt` | `4a8b6cfbe755a2726c9cc9b31ce9582b80e4beb5` | НЕ ПРОВЕРЕН |
| 124 | `app/src/main/java/com/shod/client/ui/dialogs/TurnTransportInfoDialog.kt` | `90cdcf9ecba80f3c790104dbd01d2a3f1eb20b81` | ТОЧНОЕ СОВПАДЕНИЕ |
| 125 | `app/src/main/java/com/shod/client/ui/dialogs/UninstallConfirmDialog.kt` | `9c4b6b4291572619881d6ca2a78d83a6823cb475` | ТОЧНОЕ СОВПАДЕНИЕ |
| 126 | `app/src/main/java/com/shod/client/ui/dialogs/VkAuthDialog.kt` | `92b226724113ab5ad034fead5e9c5876cbd05edc` | НЕ ПРОВЕРЕН |
| 127 | `app/src/main/java/com/shod/client/ui/dialogs/VkTokenRevokeDialog.kt` | `fba57d9996301b7b1deec764bb1abc026a4f426c` | ТОЧНОЕ СОВПАДЕНИЕ |
| 128 | `app/src/main/java/com/shod/client/ui/dialogs/WebPanelInfoDialog.kt` | `6a08bff6e1203d0ab8f81bd157730e66c1f9978b` | ТОЧНОЕ СОВПАДЕНИЕ |
| 129 | `app/src/main/java/com/shod/client/ui/dialogs/WorkersInfoDialog.kt` | `74c22e2571b092e9941c5b5ecc287697c322e4f7` | ТОЧНОЕ СОВПАДЕНИЕ |
| 130 | `app/src/main/java/com/shod/client/ui/dialogs/WorkModeInfoDialog.kt` | `1816dce8f8ac706cd0a58a55752deea9eccd7565` | ТОЧНОЕ СОВПАДЕНИЕ |
| 131 | `app/src/main/java/com/shod/client/ui/screens/CredentialsScreen.kt` | `c2d820e4028a6d40783e9a1c16f97318b83766ca` | НЕ ПРОВЕРЕН |
| 132 | `app/src/main/java/com/shod/client/ui/screens/HomeScreen.kt` | `9468673afcf75bf09b2beaadc8b9d759449e5cf3` | НЕ ПРОВЕРЕН |
| 133 | `app/src/main/java/com/shod/client/ui/screens/LogsScreen.kt` | `876d2642b3901a87821d4c60bf82c1cc80590fd0` | НЕ ПРОВЕРЕН |
| 134 | `app/src/main/java/com/shod/client/ui/screens/RoutingScreen.kt` | `c91bdb62f3b944d40d2f276380e3ce8622d57f49` | НЕ ПРОВЕРЕН |
| 135 | `app/src/main/java/com/shod/client/ui/screens/SettingsScreen.kt` | `8af16b384e8eee7ae7fc2c89091970cfd3fa1c93` | НЕ ПРОВЕРЕН |
| 136 | `app/src/main/java/com/shod/client/ui/TunnelTrafficSummary.kt` | `06c56b05bc4b1da3095ad4644b632faa59fd0e49` | НЕ ПРОВЕРЕН |
| 137 | `app/src/main/java/com/shod/client/ui/utils/LinkUtils.kt` | `a26bf65525f98477fa4a5912a053368627809642` | НЕ ПРОВЕРЕН |
| 138 | `app/src/main/java/com/shod/client/ui/WifiAutoPauseUi.kt` | `d07b12fee713fe6b5475e7f7f8b44cc9670058cb` | НЕ ПРОВЕРЕН |
| 139 | `app/src/main/java/com/shod/client/UiDesignPresets.kt` | `508622d5c0f2edd4438475ef2117b920370b9f7b` | НЕ ПРОВЕРЕН |
| 140 | `app/src/main/java/com/shod/client/UiScale.kt` | `60b67d5551a30dd1ea0757a52e70927c6de33fb7` | НЕ ПРОВЕРЕН |
| 141 | `app/src/main/java/com/shod/client/UpdateLaunchGuard.kt` | `79b9ca5f6dcab1cacd16ce19daf8d7768b424fe1` | НЕ ПРОВЕРЕН |
| 142 | `app/src/main/java/com/shod/client/VkApi.kt` | `8154e630a396c2ebe97917e31c2ec77e87145050` | НЕ ПРОВЕРЕН |
| 143 | `app/src/main/java/com/shod/client/VkAuthWebViewManager.kt` | `57eb07a0114702b683361ef59098d4047fc79238` | НЕ ПРОВЕРЕН |
| 144 | `app/src/main/java/com/shod/client/VkAutoCallsManager.kt` | `6e0a2c11e26e79caaf942f97204b769238fa6530` | НЕ ПРОВЕРЕН |
| 145 | `app/src/main/java/com/shod/client/VkCallPolicies.kt` | `0ef24b011934e9b8761b266fcaf6ecc6fd43a03d` | НЕ ПРОВЕРЕН |
| 146 | `app/src/main/java/com/shod/client/VkCookieScope.kt` | `2b6293de18a5ac01cdd3aa950f82522cf6e29ecd` | НЕ ПРОВЕРЕН |
| 147 | `app/src/main/java/com/shod/client/VkHashValidator.kt` | `805ffe3360f01ee2e216ec2e3daca173bb1eecde` | НЕ ПРОВЕРЕН |
| 148 | `app/src/main/java/com/shod/client/VkModePolicy.kt` | `51ddd37534dd2b7e10954c895f3a1eac6675b9a2` | НЕ ПРОВЕРЕН |
| 149 | `app/src/main/java/com/shod/client/VkNetworkProbePolicy.kt` | `9fc1504d243b2a658bfd7bcab5561d2ab89dc256` | НЕ ПРОВЕРЕН |
| 150 | `app/src/main/java/com/shod/client/VkTokenScraper.kt` | `dec523e3f9afb17b5a10d3a0a3b14718d33b3845` | НЕ ПРОВЕРЕН |
| 151 | `app/src/main/java/com/shod/client/VpnRecoveryPolicy.kt` | `955ff2e1d27edc6289bdadaa147051c410191e73` | НЕ ПРОВЕРЕН |
| 152 | `app/src/main/java/com/shod/client/VpnWidgetProvider.kt` | `daf966e4b23b3ea356b28d0e109556f73ccedb07` | НЕ ПРОВЕРЕН |
| 153 | `app/src/main/java/com/shod/client/VpnWidgetToggleReceiver.kt` | `99193189bd005ab179ffd26afe633de8516cf4db` | НЕ ПРОВЕРЕН |
| 154 | `app/src/main/java/com/shod/client/WifiAutoPausePolicy.kt` | `c07439b800139923cf527c8c88ec7ac5a1fd4ce1` | НЕ ПРОВЕРЕН |
| 155 | `app/src/main/java/com/shod/client/WifiDiscovery.kt` | `30e4d8a6ec0304cc586c467e03852bd2c274fa8b` | НЕ ПРОВЕРЕН |
| 156 | `app/src/main/java/com/shod/client/WifiNetworkListPolicy.kt` | `501c518c5fdaf5020be190396bcf16ca0b8af5cf` | НЕ ПРОВЕРЕН |
| 157 | `app/src/main/java/com/shod/client/WifiPreferences.kt` | `111197177994621d984976eb08739c1f2a16a837` | НЕ ПРОВЕРЕН |
| 158 | `app/src/main/java/com/shod/client/WifiSsidResolver.kt` | `6a955f509f01c581fe8897be1c8f2d8d75b9c77f` | НЕ ПРОВЕРЕН |
| 159 | `app/src/main/java/com/shod/client/WorkerCountPolicy.kt` | `37bcdd93b090faf6ab2cdfb2c0fce2a939b9ff69` | НЕ ПРОВЕРЕН |
| 160 | `app/src/main/java/com/shod/client/WorkerRecoveryPolicy.kt` | `e0d62e7a63d5d52177aa86abdea7684d1c67bebc` | НЕ ПРОВЕРЕН |
| 161 | `app/src/main/libclient.provenance.json` | `0e00a45e990132f58a6f3d0fc9ba02f943d2b7f1` | НЕ ПРОВЕРЕН |
| 162 | `app/src/main/res/drawable-nodpi/tv_banner.png` | `619f72fe238af730096dc2a3e9f3ce8e8b3aa08e` | НЕ ПРОВЕРЕН |
| 163 | `app/src/main/res/drawable/bg_widget_button_active.xml` | `05e38f4f609f66ed96c5fd6cabf8793bedf77ea9` | НЕ ПРОВЕРЕН |
| 164 | `app/src/main/res/drawable/bg_widget_button_inactive.xml` | `b3dc34b9aedf89121fde71c6e466a1dcd2d3bfa3` | НЕ ПРОВЕРЕН |
| 165 | `app/src/main/res/drawable/bg_widget_card.xml` | `dd143120ba3a53a95ce2f8b89baf41086a1b8d6d` | НЕ ПРОВЕРЕН |
| 166 | `app/src/main/res/drawable/ic_c_logo.xml` | `8e6a75027886c8519a482f4500c539820b167b96` | НЕ ПРОВЕРЕН |
| 167 | `app/src/main/res/drawable/ic_donate_heart.xml` | `f14523ac59512958faabcbe5b8f4b2c7f0b9559b` | НЕ ПРОВЕРЕН |
| 168 | `app/src/main/res/drawable/ic_export.xml` | `131be7072aebdb8b9f2e545e63c6768764c2630c` | НЕ ПРОВЕРЕН |
| 169 | `app/src/main/res/drawable/ic_github.xml` | `0688bf9675eed7c593b09dcb72c48b2010296a18` | НЕ ПРОВЕРЕН |
| 170 | `app/src/main/res/drawable/ic_import_export.xml` | `1df0210393cb616c444196836bfdacd957c3f4bd` | ТОЧНОЕ СОВПАДЕНИЕ |
| 171 | `app/src/main/res/drawable/ic_import.xml` | `167d6e9261cfe4175633ebd3fde90eb0b17912cf` | НЕ ПРОВЕРЕН |
| 172 | `app/src/main/res/drawable/ic_ip.xml` | `934cdeb151dfa470e406a6d90fdb357fe6301a01` | НЕ ПРОВЕРЕН |
| 173 | `app/src/main/res/drawable/ic_launcher_foreground.xml` | `be17fbc911f2396b6e85b6634f4d139db7cd9439` | НЕ ПРОВЕРЕН |
| 174 | `app/src/main/res/drawable/ic_launcher_monochrome.xml` | `4585f7079965b845ada2bbc10dc2799da45cf972` | НЕ ПРОВЕРЕН |
| 175 | `app/src/main/res/drawable/ic_more_smooth.xml` | `a9a78c76478fcd5209eb5bff6c14f14679546f1b` | НЕ ПРОВЕРЕН |
| 176 | `app/src/main/res/drawable/ic_qr_scan.xml` | `069ff94665db084ada8e8ceec430f193f7096ffa` | ТОЧНОЕ СОВПАДЕНИЕ |
| 177 | `app/src/main/res/drawable/ic_settings_gear.xml` | `59b954c37b40fc16f1b2abb8ee6419825dd688f9` | ТОЧНОЕ СОВПАДЕНИЕ |
| 178 | `app/src/main/res/drawable/ic_shod_logo.xml` | `dd489aabaf6b1f941086210f9a0dcd5c7844a8ab` | НЕ ПРОВЕРЕН |
| 179 | `app/src/main/res/drawable/ic_stat_c.xml` | `e478750027c21ae3e06ecc0f104b7504bdc8c915` | НЕ ПРОВЕРЕН |
| 180 | `app/src/main/res/drawable/ic_stop.xml` | `220ddeadffeebf1894d3cf1fcee22b3b4781099d` | НЕ ПРОВЕРЕН |
| 181 | `app/src/main/res/drawable/ic_telegram.xml` | `b4b07d15a5f43c0d041b72c8150477b922ef1eb7` | НЕ ПРОВЕРЕН |
| 182 | `app/src/main/res/font/manrope_variable.ttf` | `75274da58537d6123b14f2cd0c355ad4681fc2b3` | ТОЧНОЕ СОВПАДЕНИЕ |
| 183 | `app/src/main/res/layout/vpn_widget.xml` | `a8883f939f0027728cd22660993a66002ce74903` | НЕ ПРОВЕРЕН |
| 184 | `app/src/main/res/mipmap-anydpi/ic_launcher_round.xml` | `67112077d80d56c64e41657300b4df449a34fbbd` | НЕ ПРОВЕРЕН |
| 185 | `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | `67112077d80d56c64e41657300b4df449a34fbbd` | НЕ ПРОВЕРЕН |
| 186 | `app/src/main/res/raw/manrope_ofl_license.txt` | `e271172a9ed0cddc895aabb6509f1c7d880b492d` | НЕ ПРОВЕРЕН |
| 187 | `app/src/main/res/values-night-v31/colors.xml` | `39ec10f55a97973b58820eece5a045282876c737` | НЕ ПРОВЕРЕН |
| 188 | `app/src/main/res/values-night/colors.xml` | `22829b9572279c02ac8b33174355c9b132eee139` | НЕ ПРОВЕРЕН |
| 189 | `app/src/main/res/values-v31/colors.xml` | `729312322b6c5786166126d96acf462e8a59a97b` | НЕ ПРОВЕРЕН |
| 190 | `app/src/main/res/values-v31/themes.xml` | `6a8e2b0b8303c1299b6d4bdb815c2617ba0b91e5` | НЕ ПРОВЕРЕН |
| 191 | `app/src/main/res/values/colors.xml` | `21f3530a460513b4f02756901e45ef33aa79227e` | НЕ ПРОВЕРЕН |
| 192 | `app/src/main/res/values/strings.xml` | `1a3559c05a7a81718229ca1793c63af306453b77` | НЕ ПРОВЕРЕН |
| 193 | `app/src/main/res/values/themes.xml` | `883d95308ccb6a3e6a716fdf8bf14f6f8620d416` | НЕ ПРОВЕРЕН |
| 194 | `app/src/main/res/xml/data_extraction_rules.xml` | `4b443b259e524b432aa1459b5a966c084e99f44e` | НЕ ПРОВЕРЕН |
| 195 | `app/src/main/res/xml/vpn_widget_info.xml` | `9ce70546fc9a632957d4655756ed9d6cd94bcb7a` | НЕ ПРОВЕРЕН |
| 196 | `app/src/test/java/com/shod/client/AppRoutingPresetsTest.kt` | `c5eb7551296cb4eaae7983bab88878605a210cc1` | НЕ ПРОВЕРЕН |
| 197 | `app/src/test/java/com/shod/client/AppUpdateTest.kt` | `041c7d6d0140274745e87a818ef881de27990eeb` | НЕ ПРОВЕРЕН |
| 198 | `app/src/test/java/com/shod/client/BatteryOptimizationPromptPolicyTest.kt` | `6ec947c10cbcb18118316f94ecf4e89a0d5e175e` | НЕ ПРОВЕРЕН |
| 199 | `app/src/test/java/com/shod/client/BatterySaverTest.kt` | `8febe015c1830501d0843c504b53040e071199c1` | НЕ ПРОВЕРЕН |
| 200 | `app/src/test/java/com/shod/client/CaptchaUriPolicyTest.kt` | `bce855f90bcd9fe4be50b239187a29360bc7e880` | НЕ ПРОВЕРЕН |
| 201 | `app/src/test/java/com/shod/client/ConnectionStartPolicyTest.kt` | `7034b282c102a39b25bb38f440787efb709cc54f` | НЕ ПРОВЕРЕН |
| 202 | `app/src/test/java/com/shod/client/DeployScopedDataStoreTest.kt` | `38f85265603b32ecc818e6307f7a21c7567339fa` | НЕ ПРОВЕРЕН |
| 203 | `app/src/test/java/com/shod/client/DnsProfileNameTest.kt` | `3b915d884baecd2849fc9204f4eeffd225a7f6a3` | НЕ ПРОВЕРЕН |
| 204 | `app/src/test/java/com/shod/client/DtlsLogParserTest.kt` | `6452e0201459448a9a915d54ea29d6f1c9247235` | НЕ ПРОВЕРЕН |
| 205 | `app/src/test/java/com/shod/client/MainNavigationSwipeTest.kt` | `4703a86efca307381b29891d303688212fbddd40` | НЕ ПРОВЕРЕН |
| 206 | `app/src/test/java/com/shod/client/MaskOwnerHashesTest.kt` | `e67cfb79858755c68a57a80e2748d14740a85d1e` | НЕ ПРОВЕРЕН |
| 207 | `app/src/test/java/com/shod/client/NativeTunnelLogParserTest.kt` | `52144291619e3414b81851a9652ac1439c0c0f7d` | НЕ ПРОВЕРЕН |
| 208 | `app/src/test/java/com/shod/client/NetworkPauseGateTest.kt` | `c3648a7c56512e819612d681bac179a35bdf7b40` | НЕ ПРОВЕРЕН |
| 209 | `app/src/test/java/com/shod/client/ParticipantHashesTest.kt` | `d9c39d1a0ec7c41e2c7ab4bd676e27cf97564ba2` | НЕ ПРОВЕРЕН |
| 210 | `app/src/test/java/com/shod/client/PhysicalNetworkTrackerTest.kt` | `b381f7a823efcef94f0e9c4e4f65b90a4a096e61` | НЕ ПРОВЕРЕН |
| 211 | `app/src/test/java/com/shod/client/ProbeOutputReaderTest.kt` | `0182b36cd7662366db3efa09bbddd5190cf786cc` | НЕ ПРОВЕРЕН |
| 212 | `app/src/test/java/com/shod/client/ProfilePingControllerTest.kt` | `872cb27d5677177601cc2c6bf77e513b2428a0b6` | НЕ ПРОВЕРЕН |
| 213 | `app/src/test/java/com/shod/client/ReadOnlyProfileDataStoreTest.kt` | `d6e153b52e2ebca4a597e12270b88825523df47b` | НЕ ПРОВЕРЕН |
| 214 | `app/src/test/java/com/shod/client/SavedVkAccountsTest.kt` | `9f211ddffbe259996b0e6317bf9ad66261bafc0c` | НЕ ПРОВЕРЕН |
| 215 | `app/src/test/java/com/shod/client/ServerCoreCacheTest.kt` | `a096594adfc607988d8c3000f56e92d59cbe4cc9` | НЕ ПРОВЕРЕН |
| 216 | `app/src/test/java/com/shod/client/ServerRoutingRuleTest.kt` | `bc8b0bdfae03943b944d458e2df9bd15fb1b7335` | НЕ ПРОВЕРЕН |
| 217 | `app/src/test/java/com/shod/client/SettingsStoreInitOrderTest.kt` | `2dfa539aa73e82a4b3f89466f69c81c789855e4a` | НЕ ПРОВЕРЕН |
| 218 | `app/src/test/java/com/shod/client/SshHostKeyStoreTest.kt` | `a8260c77aa9fa515df4f9d3caa4f0e15b9d4a8b6` | НЕ ПРОВЕРЕН |
| 219 | `app/src/test/java/com/shod/client/StoredSecretStateTest.kt` | `ecee577790270088276ccd8f80662ab9de17bf3c` | НЕ ПРОВЕРЕН |
| 220 | `app/src/test/java/com/shod/client/StreamDiagnosticParserTest.kt` | `83e90e68604613f44cde7abe7905159c2777bada` | НЕ ПРОВЕРЕН |
| 221 | `app/src/test/java/com/shod/client/SubscriptionImporterTest.kt` | `75e9b6cb31c35b00808f9fb50358f9321a6f1d53` | НЕ ПРОВЕРЕН |
| 222 | `app/src/test/java/com/shod/client/TrafficDiagnosticParserTest.kt` | `3b0fa509c6bc330dc8b2c4aa7c8f4c73ca083d9b` | НЕ ПРОВЕРЕН |
| 223 | `app/src/test/java/com/shod/client/TunnelAuthSnapshotTest.kt` | `9271ab55a1ef35b2467b44fd1b51269aaa459cd1` | НЕ ПРОВЕРЕН |
| 224 | `app/src/test/java/com/shod/client/TunnelEventParserTest.kt` | `a6b14c9122b3dff302ca6c3d05cec6eccc6cd85a` | НЕ ПРОВЕРЕН |
| 225 | `app/src/test/java/com/shod/client/TunnelLifecycleStateChaosTest.kt` | `f28a970a345cf690675e72fcf93c2e37c6968986` | НЕ ПРОВЕРЕН |
| 226 | `app/src/test/java/com/shod/client/TunnelLifecycleStateTest.kt` | `b43e6c5d74d169e2e1185e989066dafcc6c1e2ad` | НЕ ПРОВЕРЕН |
| 227 | `app/src/test/java/com/shod/client/TunnelLogPolicyTest.kt` | `2ec41077912cecc8995b978d37d762120a4b3672` | НЕ ПРОВЕРЕН |
| 228 | `app/src/test/java/com/shod/client/TunnelPasswordStorageTest.kt` | `83580f4b1aefe44bc9cd267bc36cef0d60e301f9` | НЕ ПРОВЕРЕН |
| 229 | `app/src/test/java/com/shod/client/TunnelProbePacketTest.kt` | `c3aa7d091e1a761901ddcd6d2f86c1758b6c398a` | НЕ ПРОВЕРЕН |
| 230 | `app/src/test/java/com/shod/client/TunnelServicePolicyTest.kt` | `448099b9d7c48b110cc4e1cd918768596214bd18` | НЕ ПРОВЕРЕН |
| 231 | `app/src/test/java/com/shod/client/TunnelSessionIdTest.kt` | `ee23d0c9bbed8c0145a165747b7653945961e863` | НЕ ПРОВЕРЕН |
| 232 | `app/src/test/java/com/shod/client/TunnelStartupProgressTest.kt` | `eea917cf526f36f70ba0294a1b0b0fada36c267c` | НЕ ПРОВЕРЕН |
| 233 | `app/src/test/java/com/shod/client/TunnelTrafficTest.kt` | `290730e394d2d7261853ba3ae86c09d7eb3de10f` | НЕ ПРОВЕРЕН |
| 234 | `app/src/test/java/com/shod/client/TunRecoveryPolicyTest.kt` | `523e80737f708a10b19cb8c288bd1f9904704223` | НЕ ПРОВЕРЕН |
| 235 | `app/src/test/java/com/shod/client/TurnTransportPolicyTest.kt` | `020d402adfc5dba0f5d286e728a76aca4cec88bd` | НЕ ПРОВЕРЕН |
| 236 | `app/src/test/java/com/shod/client/TvManifestTest.kt` | `cc02c50cb7be7985a69b125a76a39e20910b9a5b` | НЕ ПРОВЕРЕН |
| 237 | `app/src/test/java/com/shod/client/ui/AppSelectionOrderingTest.kt` | `edc5505ddda731be5a9d1d2de34afc0dbc11ef87` | НЕ ПРОВЕРЕН |
| 238 | `app/src/test/java/com/shod/client/ui/deploy/operations/DeployResultPolicyTest.kt` | `cbf731b248a41cd1f1e987fae6ddd33f108f8df3` | НЕ ПРОВЕРЕН |
| 239 | `app/src/test/java/com/shod/client/ui/deploy/operations/DeployScriptLineEndingsTest.kt` | `8de8dfb8caaa1adebb1c657bdd5c614a83f5806e` | НЕ ПРОВЕРЕН |
| 240 | `app/src/test/java/com/shod/client/ui/deploy/operations/DeployScriptPreflightTest.kt` | `53a643f660d35d7ac8182346d58a1d6e3005e052` | НЕ ПРОВЕРЕН |
| 241 | `app/src/test/java/com/shod/client/ui/deploy/operations/SshTransportConnectionTest.kt` | `fdb57f28fd2745e5acad7c3afede84b6eff79479` | НЕ ПРОВЕРЕН |
| 242 | `app/src/test/java/com/shod/client/ui/DeployUiStateTest.kt` | `259e713e4bb0712ed1f25a834f34062596eae608` | НЕ ПРОВЕРЕН |
| 243 | `app/src/test/java/com/shod/client/ui/legacy/LinkTest.kt` | `5b6f6433c15f0bf00eb80e4b6156fd4bf2ec5e5a` | НЕ ПРОВЕРЕН |
| 244 | `app/src/test/java/com/shod/client/ui/ServerCheckTest.kt` | `c86c97b4222039536bd0f1c7638c464528b34d1d` | НЕ ПРОВЕРЕН |
| 245 | `app/src/test/java/com/shod/client/ui/SshKeyRuntimeDependencyTest.kt` | `82384eaf5361d96af1e980bc495b3d54b0f522ce` | НЕ ПРОВЕРЕН |
| 246 | `app/src/test/java/com/shod/client/UpdateLaunchGuardTest.kt` | `c795af83f3ffb5fa66ab4bffb2cd714ff581cf7b` | НЕ ПРОВЕРЕН |
| 247 | `app/src/test/java/com/shod/client/VkCallPoliciesTest.kt` | `d8a62070e2d9801df3c63cc14e423174b97ad37b` | НЕ ПРОВЕРЕН |
| 248 | `app/src/test/java/com/shod/client/VkCookieScopeTest.kt` | `05403999a6af3f278561b4722e9adf934f4093ee` | НЕ ПРОВЕРЕН |
| 249 | `app/src/test/java/com/shod/client/VkHashValidatorTest.kt` | `e6e22921b7c39268ff8437629a067bd4de1211dc` | НЕ ПРОВЕРЕН |
| 250 | `app/src/test/java/com/shod/client/VkModePolicyTest.kt` | `d8d6cf099b0cee790d4d499dfc276ed0f0377fce` | НЕ ПРОВЕРЕН |
| 251 | `app/src/test/java/com/shod/client/VkNetworkProbePolicyTest.kt` | `33b347e50bedc55061fbc90f9eb7588236ff8c11` | НЕ ПРОВЕРЕН |
| 252 | `app/src/test/java/com/shod/client/VpnConfigurationTest.kt` | `86535eac173bed893a24ff715a9e60193a8da9a2` | НЕ ПРОВЕРЕН |
| 253 | `app/src/test/java/com/shod/client/VpnRecoveryPolicyTest.kt` | `27afdd1e2a782fafac4ffe442c70fdc79b4f445c` | НЕ ПРОВЕРЕН |
| 254 | `app/src/test/java/com/shod/client/WifiAutoPausePolicyTest.kt` | `7cde5822b73d5cab292cb78d4048da223badf92f` | НЕ ПРОВЕРЕН |
| 255 | `app/src/test/java/com/shod/client/WifiNetworkListPolicyTest.kt` | `16ed8339a9957ab3d5aa9816021adea8fa64300b` | НЕ ПРОВЕРЕН |
| 256 | `app/src/test/java/com/shod/client/WifiPreferencesTest.kt` | `3702d2933fcb82eb6aadddb08d2d62b6e7946bdf` | НЕ ПРОВЕРЕН |
| 257 | `app/src/test/java/com/shod/client/WorkerCountPolicyTest.kt` | `4d489af37380c1f7bb40047c22e6f761974d725a` | НЕ ПРОВЕРЕН |
| 258 | `app/src/test/java/com/shod/client/WorkerRecoveryPolicyTest.kt` | `0f3312eab4d3c750e12f5f66b37384eca55691df` | НЕ ПРОВЕРЕН |
| 259 | `build.gradle.kts` | `e53574b954d280ed7e9b6450d11759681841baa1` | ТОЧНОЕ СОВПАДЕНИЕ |
| 260 | `gradle.properties` | `140fbbbba0e7adccc34e7a915c2059a5187ad6bd` | ТОЧНОЕ СОВПАДЕНИЕ |
| 261 | `gradle/wrapper/gradle-wrapper.jar` | `2c3521197d7c4586c843d1d3e9090525f1898cde` | ТОЧНОЕ СОВПАДЕНИЕ |
| 262 | `gradle/wrapper/gradle-wrapper.properties` | `c61a118f7ddb21223f1a2fdd05f6aec1b9863aec` | ТОЧНОЕ СОВПАДЕНИЕ |
| 263 | `gradlew` | `727d6af8d7e8074828a48b143b83bf7ddefa9d66` | ТОЧНОЕ СОВПАДЕНИЕ |
| 264 | `gradlew.bat` | `22fdb19759a5057da49087fd4216984baacab123` | ТОЧНОЕ СОВПАДЕНИЕ |
| 265 | `KNOWN_ISSUES.md` | `0ef93169ba83d56cc77c09bd7cb55b6bc0f40a81` | НЕ ПРОВЕРЕН |
| 266 | `LICENSE` | `5cc8cea945d6c9b51c89ba7253c279204df4f998` | НЕ ПРОВЕРЕН |
| 267 | `rust-client/auth.rs` | `0505a22fb173ae9aebcd828a2876a4d1b5055efd` | ТОЧНОЕ СОВПАДЕНИЕ |
| 268 | `rust-client/build_android_abis.ps1` | `29e821d302ccaa469a72946333766c4249ed8efd` | НЕ ПРОВЕРЕН |
| 269 | `rust-client/build_android_wsl.sh` | `0d142af9668f1ece3649f07c3c3b35b195e98b41` | НЕ ПРОВЕРЕН |
| 270 | `rust-client/build_so.bat` | `29184a50f56fbe7aa31616a9b0cb7961f4a64c9e` | НЕ ПРОВЕРЕН |
| 271 | `rust-client/captcha_slider.rs` | `ebfe770153564f1e45fa05efc6c3b57ed5fd91d1` | ТОЧНОЕ СОВПАДЕНИЕ |
| 272 | `rust-client/captcha.rs` | `a23c4342ffe113f3e709299f190efee2c8d5493a` | ТОЧНОЕ СОВПАДЕНИЕ |
| 273 | `rust-client/Cargo.lock` | `e3a20d5de6ca055f3a68467a16b99e5175bc3a08` | НЕ ПРОВЕРЕН |
| 274 | `rust-client/Cargo.toml` | `dbc971f0c2cd5fa765ed35f090cbfbaa34afe163` | НЕ ПРОВЕРЕН |
| 275 | `rust-client/check_no_runtime_panics.ps1` | `c6408ca920015d10788d8e92d3201b2944569267` | НЕ ПРОВЕРЕН |
| 276 | `rust-client/cpu_task.rs` | `6aca64aecbfa5c2699b851287a34d81052921705` | ТОЧНОЕ СОВПАДЕНИЕ |
| 277 | `rust-client/direct_relay.rs` | `de5829ff4b0c7a8af1c71d27939ed89c800b5b3c` | ТОЧНОЕ СОВПАДЕНИЕ |
| 278 | `rust-client/dispatcher.rs` | `f52070e96b772386863b6584d225f0e2bd9285dd` | ТОЧНОЕ СОВПАДЕНИЕ |
| 279 | `rust-client/dns.rs` | `44fcd0d386cc832c0969df94a405a0bbc85ac51b` | ТОЧНОЕ СОВПАДЕНИЕ |
| 280 | `rust-client/events.rs` | `2d3b38817740fff22fdbdfe8de9d7be756d42e6d` | ТОЧНОЕ СОВПАДЕНИЕ |
| 281 | `rust-client/logging.rs` | `c6bb13005c09788c3efebcafd88280fd9c947ebe` | ТОЧНОЕ СОВПАДЕНИЕ |
| 282 | `rust-client/main.rs` | `03f2d01988ec37128de72e2598977b42863e569d` | НЕ ПРОВЕРЕН |
| 283 | `rust-client/namegen.rs` | `74f3d6302f4d8888ffaf45e910ea33d162f5124a` | ТОЧНОЕ СОВПАДЕНИЕ |
| 284 | `rust-client/obfs.rs` | `44e2423ad11a6b8142578639b65f74bc81ec02a3` | ТОЧНОЕ СОВПАДЕНИЕ |
| 285 | `rust-client/packet.rs` | `51245ebf87e44cbc4469c91473eccb0160d62850` | ТОЧНОЕ СОВПАДЕНИЕ |
| 286 | `rust-client/profiles.rs` | `25c5c01a1f3713b2ad7e8dfe6a386edaf2b694e2` | ТОЧНОЕ СОВПАДЕНИЕ |
| 287 | `rust-client/proptest-regressions/session.txt` | `37bb44576e341826894f3002b4ebb5ccbaa35a57` | НЕ ПРОВЕРЕН |
| 288 | `rust-client/protocol.rs` | `4b50656fa6f9b18b0ccf13250b61602a4b3d78dd` | ТОЧНОЕ СОВПАДЕНИЕ |
| 289 | `rust-client/repair.rs` | `89b2bffcb555be01fbaaf52a7f1b56afbff81e48` | ТОЧНОЕ СОВПАДЕНИЕ |
| 290 | `rust-client/session.rs` | `32cd4a6222ded98664b7358b507c36e78f92f3f7` | ТОЧНОЕ СОВПАДЕНИЕ |
| 291 | `rust-client/sni.rs` | `d4846a8a581aa9a0ee7fcd4482ff52325e6f41ec` | ТОЧНОЕ СОВПАДЕНИЕ |
| 292 | `rust-client/srtp_mode.rs` | `50d34f064d8c6180c9687b9c559b2bf3c1ef40fe` | ТОЧНОЕ СОВПАДЕНИЕ |
| 293 | `rust-client/stats.rs` | `01d17436a22d2db75834f43c281862efac3a1988` | ТОЧНОЕ СОВПАДЕНИЕ |
| 294 | `rust-client/stream_diagnostics.rs` | `9b82c8f58dafd96958cce010395febf3d843ed39` | ТОЧНОЕ СОВПАДЕНИЕ |
| 295 | `rust-client/stun_codec.rs` | `fc9b9e34b745a7099ba20adbf3f09c0e0902fb78` | ТОЧНОЕ СОВПАДЕНИЕ |
| 296 | `rust-client/traffic_diagnostics.rs` | `b5001809ea694cff9da2ee4b2671c9a0a44b8e04` | ТОЧНОЕ СОВПАДЕНИЕ |
| 297 | `rust-client/tun.rs` | `cd27762493ddb3be297f3af3a8dce2c90ccedb2c` | ТОЧНОЕ СОВПАДЕНИЕ |
| 298 | `rust-client/turn_core.rs` | `504c01b01c3ec8f56648ca736824a3fd500576e2` | ТОЧНОЕ СОВПАДЕНИЕ |
| 299 | `rust-client/turn_endpoint.rs` | `d8dda43273f6e841cc6d3219de9c89611588cfa0` | ТОЧНОЕ СОВПАДЕНИЕ |
| 300 | `rust-client/turn_integration_tests.rs` | `97b014dabf4f18a4c6003060ad141bc59c963a27` | ТОЧНОЕ СОВПАДЕНИЕ |
| 301 | `rust-client/turn_ip_pool.rs` | `07c29a6edbe7143d2d1856b853dd261ecce7a6ed` | ТОЧНОЕ СОВПАДЕНИЕ |
| 302 | `rust-client/turn_stream.rs` | `4c57e8a1e93ba2cdc46c9b626ff6bbf2e129165f` | ТОЧНОЕ СОВПАДЕНИЕ |
| 303 | `rust-client/turn.rs` | `31787f837bb4121dc9a6c9535243ea72abc3dee9` | НЕ ПРОВЕРЕН |
| 304 | `rust-client/vendor/netstack-smoltcp/Cargo.toml` | `be3d5026dee23d247648f75d17d3e64a3a0e8ebb` | ТОЧНОЕ СОВПАДЕНИЕ |
| 305 | `rust-client/vendor/netstack-smoltcp/LICENSE-APACHE` | `f49a4e16e68b128803cc2dcea614603632b04eac` | ТОЧНОЕ СОВПАДЕНИЕ |
| 306 | `rust-client/vendor/netstack-smoltcp/LICENSE-MIT` | `ede7ee90b0e7231f88b2fadcdd3854c4ffcd87b5` | ТОЧНОЕ СОВПАДЕНИЕ |
| 307 | `rust-client/vendor/netstack-smoltcp/src/device.rs` | `941cb93c1ae4662f4bc4135543ca9c34c39ef114` | ТОЧНОЕ СОВПАДЕНИЕ |
| 308 | `rust-client/vendor/netstack-smoltcp/src/filter.rs` | `daced192058ea16936f2798664dd6b6d6f014b79` | ТОЧНОЕ СОВПАДЕНИЕ |
| 309 | `rust-client/vendor/netstack-smoltcp/src/lib.rs` | `dc8fcc6f6062ba27d3b978f85b1fd8aa262b84a6` | ТОЧНОЕ СОВПАДЕНИЕ |
| 310 | `rust-client/vendor/netstack-smoltcp/src/packet.rs` | `fd1937ec82848a350129393c388ca6f3e16fb077` | ТОЧНОЕ СОВПАДЕНИЕ |
| 311 | `rust-client/vendor/netstack-smoltcp/src/runner.rs` | `c17957927ebfb6d75927867038f1507eae987472` | ТОЧНОЕ СОВПАДЕНИЕ |
| 312 | `rust-client/vendor/netstack-smoltcp/src/stack.rs` | `ecc9b39aa97f82a687b06706efa4be8c2bd75e19` | ТОЧНОЕ СОВПАДЕНИЕ |
| 313 | `rust-client/vendor/netstack-smoltcp/src/tcp.rs` | `ce23053a20efa4433e3ed301f2d66be6ad30d5ee` | ТОЧНОЕ СОВПАДЕНИЕ |
| 314 | `rust-client/vendor/netstack-smoltcp/src/udp.rs` | `aeda9ee3ec53c794a5d082c361e28578da89c894` | ТОЧНОЕ СОВПАДЕНИЕ |
| 315 | `rust-client/vendor/primp/Cargo.toml` | `22d2d714ff298f9fab97ad23382c10ce4b61b48a` | ТОЧНОЕ СОВПАДЕНИЕ |
| 316 | `rust-client/vendor/primp/src/imp/chrome/mod.rs` | `affd4ddff9c9a570b488cfe188a638b74e8f7630` | ТОЧНОЕ СОВПАДЕНИЕ |
| 317 | `rust-client/vendor/primp/src/imp/edge/mod.rs` | `053a82e80dfc99b5de84937209bc2f9126e4c978` | ТОЧНОЕ СОВПАДЕНИЕ |
| 318 | `rust-client/vendor/primp/src/imp/firefox/mod.rs` | `33fc006bb5965643dab65cb417e3ce16b3e7bcd9` | ТОЧНОЕ СОВПАДЕНИЕ |
| 319 | `rust-client/vendor/primp/src/imp/mod.rs` | `f266b6ac8c741f885cd99a8e3683ee35630e6856` | ТОЧНОЕ СОВПАДЕНИЕ |
| 320 | `rust-client/vendor/primp/src/imp/opera/mod.rs` | `fb81b2a4a5c21b6d3ed56ef69651330ff6cc4f81` | ТОЧНОЕ СОВПАДЕНИЕ |
| 321 | `rust-client/vendor/primp/src/imp/safari/mod.rs` | `bdd6cc0ad668347b4cc80168d66535fab4c5089a` | ТОЧНОЕ СОВПАДЕНИЕ |
| 322 | `rust-client/vendor/primp/src/lib.rs` | `613f49022fafb16ab87140a4660982451b496199` | ТОЧНОЕ СОВПАДЕНИЕ |
| 323 | `rust-client/vk_js_calls.rs` | `04f2a6a315c6392b47fe560e2f8ecdedefb39773` | ТОЧНОЕ СОВПАДЕНИЕ |
| 324 | `rust-client/worker.rs` | `f8e41e3ff249300d1ecabfde5990164f4a290768` | ТОЧНОЕ СОВПАДЕНИЕ |
| 325 | `rust-client/wrap.rs` | `2429d6278870382a631acaf7e46e30d642d9ea41` | ТОЧНОЕ СОВПАДЕНИЕ |
| 326 | `rust-server/.cargo/config.toml` | `3d032011640d70c3825e1fe16f7b3e93f09ff537` | НЕ ПРОВЕРЕН |
| 327 | `rust-server/build_linux_variants.ps1` | `9b7f9e740dc17fd2e024a4723dbdd029e17b23ec` | НЕ ПРОВЕРЕН |
| 328 | `rust-server/build_linux.bat` | `c514bc21d5587fc0efc46d5ebc4a829da92d6ad0` | НЕ ПРОВЕРЕН |
| 329 | `rust-server/build_linux.sh` | `530ed45bb9dd7418cfaa48aedb47ce9085dd83a1` | НЕ ПРОВЕРЕН |
| 330 | `rust-server/Cargo.lock` | `d3bf1cc21c19a0b036e00bda239b64a4106ae306` | НЕ ПРОВЕРЕН |
| 331 | `rust-server/Cargo.toml` | `8041c62b55879012a73d6d5d14814a555944cabb` | НЕ ПРОВЕРЕН |
| 332 | `rust-server/dataplane.rs` | `88037b2794433cc359e99a9afe3758fcc1586693` | ТОЧНОЕ СОВПАДЕНИЕ |
| 333 | `rust-server/main.rs` | `cca694f812d3f8b81783bface6372633bb4cbbfb` | НЕ ПРОВЕРЕН |
| 334 | `rust-server/model.rs` | `148302fad86f016a03dc249b149b1a666e7bc360` | ТОЧНОЕ СОВПАДЕНИЕ |
| 335 | `rust-server/net_setup.rs` | `1e06a2cc6752d1af9577309116747fe84ba20b92` | ТОЧНОЕ СОВПАДЕНИЕ |
| 336 | `rust-server/packet.rs` | `808a6b6491d90bd75d9772bc233cba54dbdce934` | ТОЧНОЕ СОВПАДЕНИЕ |
| 337 | `rust-server/path_probe.rs` | `d01883136a5bb86318453ccd8c2ad97f20c47694` | ТОЧНОЕ СОВПАДЕНИЕ |
| 338 | `rust-server/protocol.rs` | `246ed612560238b9994ef28e8c0342c3d76e2375` | ТОЧНОЕ СОВПАДЕНИЕ |
| 339 | `rust-server/proxy_route.rs` | `622cc271c2f77ffe4ec84b4fbd6d4dc0b796bef2` | ТОЧНОЕ СОВПАДЕНИЕ |
| 340 | `rust-server/rust-toolchain.toml` | `362cd953eb82ca72ef9bd71da83a5858d8ba708a` | НЕ ПРОВЕРЕН |
| 341 | `rust-server/server_config.rs` | `20a3f528d7e654add7e59791362f38fc241fedf5` | ТОЧНОЕ СОВПАДЕНИЕ |
| 342 | `rust-server/tokio_io.rs` | `b7973a4502650452650b390ded1d0b4e4b64980c` | ТОЧНОЕ СОВПАДЕНИЕ |
| 343 | `rust-server/tproxy.rs` | `caf86809f858bb015eecb9b8db2fe9e6118e5f8d` | ТОЧНОЕ СОВПАДЕНИЕ |
| 344 | `rust-server/tun_device.rs` | `ee3aac50a949e4948a0a49c3d6875229181587dc` | ТОЧНОЕ СОВПАДЕНИЕ |
| 345 | `rust-server/web_panel.rs` | `3e974fe72094d1bb2e802c8008ba967244de24cd` | ТОЧНОЕ СОВПАДЕНИЕ |
| 346 | `scripts/build_apk.bat` | `23e1ed753faa2d9281164a719ab82abc1e2cdd1c` | НЕ ПРОВЕРЕН |
| 347 | `scripts/build_both_parallel.ps1` | `c7f32d2a29c740f82d5a0e818681a8e519aa9dfe` | НЕ ПРОВЕРЕН |
| 348 | `scripts/find_android_sdk_ndk.bat` | `a1f3d57b6b0be9ab1b670c2298759cdee8412e18` | НЕ ПРОВЕРЕН |
| 349 | `scripts/find_android_sdk.bat` | `96b08542f9e3a393b8b25f92914e89b1958082d6` | НЕ ПРОВЕРЕН |
| 350 | `scripts/native_client_provenance.ps1` | `02361f4499c99f1e7e05b1f527df15605be07a6f` | НЕ ПРОВЕРЕН |
| 351 | `scripts/run_wsl_checks.sh` | `b5528f7b68f058f7ee0b0158be6b0eb861033d89` | НЕ ПРОВЕРЕН |
| 352 | `scripts/server_asset_provenance.ps1` | `cbd9e4b2b4aaef093ba3d676834e4ffe5b596062` | НЕ ПРОВЕРЕН |
| 353 | `scripts/test_wsl_unified.bat` | `b07ff882960365d69cf173f4413780d757a26a27` | НЕ ПРОВЕРЕН |
| 354 | `scripts/test_wsl_unified.ps1` | `23e7e0e23fe59d4d97901184da58c41ab7284033` | НЕ ПРОВЕРЕН |
| 355 | `settings.gradle.kts` | `cf1c1d2712ce45903bb273c0445ec098a366df9c` | НЕ ПРОВЕРЕН |
| 356 | `shared/dtls.rs` | `407915f519923b70473d48a936c663b98109f222` | ТОЧНОЕ СОВПАДЕНИЕ |
| 357 | `shared/spsc_ring.rs` | `ac4d6b6a0b1add12d7247842e472ddb5f81e3bc6` | ТОЧНОЕ СОВПАДЕНИЕ |
| 358 | `shared/wire_protocol.rs` | `2ac9ed2474995531fe6b3ec6a3bd68e09a5916e8` | ТОЧНОЕ СОВПАДЕНИЕ |
| 359 | `VERSIONING.md` | `ad5f8da834804d8c7590ffa4b5e6ae3a3aca8f42` | НЕ ПРОВЕРЕН |

## Сводка по типам файлов

- kt: 208
- rs: 64
- xml: 35
- bat: 7
- ps1: 7
- [без расширения]: 6
- toml: 6
- sh: 4
- kts: 3
- txt: 3
- json: 3
- properties: 2
- md: 2
- lock: 2
- gitattributes: 1
- yml: 1
- gitignore: 1
- pro: 1
- png: 1
- ttf: 1
- jar: 1

## Журнал проверок

| Дата | Диапазон / файлы | Результат | Изменение реестра |
|---|---|---|---|
| 2026-11-22 | Первичная инвентаризация всех 359 файлов | Создан полный перечень; точные blob-совпадения отмечены отдельно, содержимое ещё не признано перенесённым | Инициализация реестра |
