package com.shod.client.updates

import android.util.Log
import com.shod.client.BuildConfig
import com.shod.client.ShodConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val UPDATE_LOG_TAG = ShodConstants.General.APP_NAME
private const val GITHUB_RELEASES_URL = ShodConstants.Update.GITHUB_RELEASES_URL
private const val GITHUB_LATEST_RELEASE_URL = ShodConstants.Update.GITHUB_LATEST_RELEASE_URL
private const val GITHUB_LATEST_RELEASE_WEB_URL = ShodConstants.Update.GITHUB_LATEST_RELEASE_WEB_URL
private const val GITHUB_RELEASE_TAG_URL_PREFIX = ShodConstants.Update.GITHUB_RELEASE_TAG_URL_PREFIX
private const val GITHUB_TAGS_URL = ShodConstants.Update.GITHUB_TAGS_URL
private const val GITHUB_TAG_TREE_URL_PREFIX = ShodConstants.Update.GITHUB_TAG_TREE_URL_PREFIX
private const val GITHUB_API_RATE_LIMIT_FALLBACK_MS = ShodConstants.Timeouts.UPDATE_RATE_LIMIT_COOLDOWN_MS
private val VERSION_NUMBER_REGEX = ShodConstants.Patterns.VERSION_NUMBER

@Volatile
private var githubApiCooldownUntilMs = 0L

suspend fun fetchLatestReleaseInfo(localVersion: String? = null): AppReleaseInfo? = withContext(Dispatchers.IO) {
    val latestRelease = fetchReleaseFromLatestWebRedirect()
        ?: fetchReleaseFromLatestEndpoint()
        ?: fetchLatestStableReleaseFromList()
    val latestTag = fetchLatestTagFromList()

    when {
        latestRelease == null -> latestTag
        latestTag == null -> latestRelease
        isNewerVersion(latestRelease.versionTag, latestTag.versionTag) -> latestTag
        else -> latestRelease
    }
}

private fun fetchLatestStableReleaseFromList(): AppReleaseInfo? {
    val response = fetchGitHubApi(GITHUB_RELEASES_URL) ?: return null
    val releases = try {
        JSONArray(response)
    } catch (e: Exception) {
        Log.w(UPDATE_LOG_TAG, "[WARN] Update check: failed to parse releases list", e)
        return null
    }

    var bestRelease: AppReleaseInfo? = null
    for (i in 0 until releases.length()) {
        val json = releases.optJSONObject(i) ?: continue
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) continue
        val release = json.toAppReleaseInfo() ?: continue
        if (bestRelease == null || isNewerVersion(bestRelease.versionTag, release.versionTag)) {
            bestRelease = release
        }
    }
    return bestRelease
}

private fun fetchLatestTagFromList(): AppReleaseInfo? {
    val response = fetchGitHubApi(GITHUB_TAGS_URL) ?: return null
    val tags = try {
        JSONArray(response)
    } catch (e: Exception) {
        Log.w(UPDATE_LOG_TAG, "[WARN] Update check: failed to parse tags list", e)
        return null
    }

    var bestTag: AppReleaseInfo? = null
    for (i in 0 until tags.length()) {
        val json = tags.optJSONObject(i) ?: continue
        val tagName = normalizeVersionTag(json.optString("name"))
        if (tagName.isBlank()) continue
        val tag = AppReleaseInfo(
            versionTag = tagName,
            releaseUrl = "$GITHUB_TAG_TREE_URL_PREFIX$tagName",
            source = RemoteVersionSource.Tag
        )
        if (bestTag == null || isNewerVersion(bestTag.versionTag, tag.versionTag)) {
            bestTag = tag
        }
    }
    return bestTag
}

private fun fetchReleaseFromLatestEndpoint(): AppReleaseInfo? {
    val response = fetchGitHubApi(GITHUB_LATEST_RELEASE_URL) ?: return null
    val json = try {
        JSONObject(response)
    } catch (e: Exception) {
        Log.w(UPDATE_LOG_TAG, "[WARN] Update check: failed to parse latest release", e)
        return null
    }
    return json.toAppReleaseInfo()
}

private fun fetchReleaseFromLatestWebRedirect(): AppReleaseInfo? {
    var conn: HttpURLConnection? = null
    return try {
        conn = URL(GITHUB_LATEST_RELEASE_WEB_URL).openConnection() as HttpURLConnection
        applyNoCacheHeaders(conn)
        conn.instanceFollowRedirects = false
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "text/html,*/*")
        conn.setRequestProperty("User-Agent", "ShodAndroid/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000

        val responseCode = conn.responseCode
        val location = conn.getHeaderField("Location")
        if (!location.isNullOrBlank()) {
            val releaseUrl = URL(URL(GITHUB_LATEST_RELEASE_WEB_URL), location).toString()
            val versionTag = extractTagFromReleaseUrl(releaseUrl)
            if (!versionTag.isNullOrBlank()) {
                return AppReleaseInfo(versionTag, releaseUrl, RemoteVersionSource.Release)
            }
        }

        if (responseCode in 200..299) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val versionTag = Regex("/releases/tag/([^\"?#<]+)").find(response)?.groupValues?.getOrNull(1)
            if (!versionTag.isNullOrBlank()) {
                return AppReleaseInfo(versionTag, "$GITHUB_RELEASE_TAG_URL_PREFIX$versionTag", RemoteVersionSource.Release)
            }
        }

        Log.w(UPDATE_LOG_TAG, "[WARN] Update check: GitHub web fallback returned $responseCode")
        null
    } catch (e: Exception) {
        Log.w(UPDATE_LOG_TAG, "[WARN] Update check: GitHub web fallback failed", e)
        null
    } finally {
        conn?.disconnect()
    }
}

private fun fetchGitHubApi(url: String): String? {
    val now = System.currentTimeMillis()
    if (now < githubApiCooldownUntilMs) return null
    return fetchHttpText(
        url = url,
        sourceLabel = "GitHub API",
        accept = "application/vnd.github+json",
        isGitHubApi = true
    )
}

private fun fetchHttpText(
    url: String,
    sourceLabel: String,
    accept: String,
    isGitHubApi: Boolean = false
): String? {
    var conn: HttpURLConnection? = null
    return try {
        conn = URL(url).openConnection() as HttpURLConnection
        applyNoCacheHeaders(conn)
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", accept)
        if (isGitHubApi) {
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        conn.setRequestProperty("User-Agent", "ShodAndroid/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (responseCode in 200..299) {
            if (isGitHubApi) githubApiCooldownUntilMs = 0L
            response
        } else {
            if (isGitHubApi) noteGitHubApiCooldown(conn, responseCode, response)
            Log.w(
                UPDATE_LOG_TAG,
                "[WARN] Update check: $sourceLabel returned $responseCode ${response.take(300)}"
            )
            null
        }
    } catch (e: Exception) {
        Log.w(UPDATE_LOG_TAG, "[WARN] Update check: $sourceLabel request failed", e)
        null
    } finally {
        conn?.disconnect()
    }
}

private fun applyNoCacheHeaders(conn: HttpURLConnection) {
    conn.useCaches = false
    conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
    conn.setRequestProperty("Pragma", "no-cache")
    conn.setRequestProperty("Expires", "0")
}

private fun noteGitHubApiCooldown(conn: HttpURLConnection, responseCode: Int, response: String) {
    if (responseCode != HttpURLConnection.HTTP_FORBIDDEN && responseCode != 429) return
    val now = System.currentTimeMillis()
    val retryAfterUntil = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let { now + it * 1000L }
    val rateLimitResetUntil = conn.getHeaderField("X-RateLimit-Reset")?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let { it * 1000L }
    val fallbackUntil = now + if (response.contains("rate limit", ignoreCase = true)) GITHUB_API_RATE_LIMIT_FALLBACK_MS else 5L * 60L * 1000L
    val cooldownUntil = listOfNotNull(retryAfterUntil, rateLimitResetUntil).filter { it > now }.minOrNull() ?: fallbackUntil
    if (cooldownUntil > githubApiCooldownUntilMs) {
        githubApiCooldownUntilMs = cooldownUntil
        Log.w(
            UPDATE_LOG_TAG,
            "[WARN] Update check: GitHub API cooldown ${(cooldownUntil - now) / 1000}s after HTTP $responseCode"
        )
    }
}

private fun JSONObject.toAppReleaseInfo(): AppReleaseInfo? {
    val versionTag = normalizeVersionTag(optString("tag_name"))
    val releaseUrl = optString("html_url").trim()
    if (versionTag.isBlank() || releaseUrl.isBlank()) return null
    return AppReleaseInfo(versionTag, releaseUrl, RemoteVersionSource.Release)
}

private fun extractTagFromReleaseUrl(releaseUrl: String): String? {
    val marker = "/releases/tag/"
    val index = releaseUrl.indexOf(marker)
    if (index < 0) return null
    return releaseUrl.substring(index + marker.length)
        .substringBefore("?")
        .substringBefore("#")
        .substringBefore("/")
        .takeIf { it.isNotBlank() }
        ?.let(::normalizeVersionTag)
}
