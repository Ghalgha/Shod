package com.shod.client.vpn.routing.apps

/**
 * Explicit package-ID presets for the two local split-tunnel modes.
 *
 * These are app-routing defaults only; they do not decide how VK transport
 * traffic is carried by the VPN. The VK apps remain in the direct preset so
 * the VPN's VK-based transport is not recursively routed into its own tunnel.
 */
internal object AppRoutingPresets {
    val direct = """
        ru.rostel ru.gosuslugi.goskey ru.gosuslugi.dom com.avito.android
        com.vkontakte.android com.vk.im com.vk.vkvideo com.vk.music com.vk.calls
        ru.yandex.searchplugin ru.yandex.yandexmaps ru.yandex.yandexnavi ru.yandex.taxi
        ru.yandex.music ru.kinopoisk ru.beru.android ru.foodfox.client com.yandex.lavka
        ru.yandex.mail ru.yandex.disk ru.yandex.metro com.yandex.iot
        com.idamob.tinkoff.android ru.alfabank.mobile.android ru.vtb24.mobilebanking.android
        ru.gazprombank.android.mobilebank.home ru.psbank.mobile com.rshb.dbo ru.sovcomcard.halva.v1
        ru.mkb.mobile ru.letobank.Prometheus ru.bankuralsib.mb.android ru.akbars.mobile
        ru.ozon.app.android ru.ozon.fintech.finance com.wildberries.ru ru.wildberries.bank
        ru.mail.mailapp ru.mail.cloud ru.dublgis.dgismobile ru.rutube.app ru.vk.store
        ru.oneme.app com.bitrix24.android
        ru.mts.mymts ru.megafon.mlk ru.beeline.services ru.tele2.mytele2 ru.rt.lk
        com.octopod.russianpost com.logistic.sdek ru.cian.main com.domclick.mortgage ru.drom ru.auto.ara
        ru.litres.android ru.instamart ru.sbcs.store ru.pyaterochka.app ru.tander.magnit com.icemobile.lenta.prod
    """.trimIndent().split(Regex("\\s+")).toSet()

    val vpn = """
        com.android.chrome com.yandex.browser org.mozilla.firefox com.microsoft.emmx
        com.opera.browser com.opera.mini.native com.brave.browser com.sec.android.app.sbrowser
        org.telegram.messenger org.telegram.messenger.web com.google.android.youtube
        com.google.android.apps.youtube.music com.openai.chatgpt com.anthropic.claude
        com.google.android.apps.bard com.instagram.android com.whatsapp com.whatsapp.w4b
        com.supercell.clashroyale com.roblox.client org.zwanoo.android.speedtest
        com.discord org.thoughtcrime.securesms com.facebook.katana com.instagram.barcelona
        com.twitter.android com.reddit.frontpage tv.twitch.android.app com.spotify.music
        com.zhiliaoapp.musically com.ss.android.ugc.trill ai.perplexity.app com.microsoft.copilot
        notion.id medium.reader com.patreon.android com.supercell.brawlstars com.supercell.clashofclans
    """.trimIndent().split(Regex("\\s+")).toSet()

    /** Selected entries retain insertion order; remaining installed apps stay alphabetical. */
    fun orderedPackages(alphabetical: List<String>, selection: Set<String>): List<String> {
        val available = alphabetical.toSet()
        return selection.filter { it in available } + alphabetical.filterNot { it in selection }
    }

    fun merge(existing: Set<String>, installed: Set<String>, whitelist: Boolean): Set<String> =
        existing + (if (whitelist) vpn else direct).intersect(installed)

    /** Existing and deliberately empty selections are never silently reseeded. */
    fun initial(
        existing: Set<String>,
        initialized: Boolean,
        installed: Set<String>,
        whitelist: Boolean,
    ): Set<String> =
        if (initialized || existing.isNotEmpty()) existing
        else (if (whitelist) vpn else direct).intersect(installed)
}
