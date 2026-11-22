// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.update

import io.github.velosako3.blossom.core.platform.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A newer release and where to get it (the release page, or the APK on Android). Never installed automatically. */
data class Update(val version: SemVer, val url: String)

/**
 * Asks GitHub for the latest release at most once a day (the answer is remembered until then). Sends nothing but
 * the request. Off when the user turns it off ([ENABLED_KEY]).
 */
class UpdateChecker(
    private val currentVersion: String,
    private val android: Boolean,
    private val prefs: PreferenceStore,
    private val clock: Clock = Clock.System,
    private val http: OkHttpClient = OkHttpClient(),
    private val url: String = "https://api.github.com/repos/velosako3/blossom/releases/latest",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): Update? {
        if (prefs.get(ENABLED_KEY) == "false") return null
        val current = SemVer.parse(currentVersion) ?: return null
        val last = prefs.get(CHECKED_KEY)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
        val (tag, link) = if (last != null && clock.now() - last < 1.days) {
            (prefs.get(TAG_KEY) ?: return null) to (prefs.get(URL_KEY) ?: return null)
        } else {
            val latest = runCatching { fetch() }.getOrNull() ?: return null
            prefs.put(CHECKED_KEY, clock.now().toEpochMilliseconds().toString()); prefs.put(TAG_KEY, latest.first); prefs.put(URL_KEY, latest.second)
            latest
        }
        val version = SemVer.parse(tag) ?: return null
        return Update(version, link).takeIf { version > current }
    }

    private suspend fun fetch(): Pair<String, String> {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("User-Agent", "blossom").build()
        val body = runInterruptible(Dispatchers.IO) { http.newCall(request).execute().use { check(it.isSuccessful); it.body.string() } }
        val release = json.parseToJsonElement(body).jsonObject
        val tag = release["tag_name"]!!.jsonPrimitive.content
        val apk = release["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?.get("browser_download_url")?.jsonPrimitive?.content
        return tag to ((if (android) apk else null) ?: release["html_url"]!!.jsonPrimitive.content)
    }

    companion object {
        const val ENABLED_KEY = "update.check"
        private const val CHECKED_KEY = "update.checkedAt"
        private const val TAG_KEY = "update.tag"
        private const val URL_KEY = "update.url"
    }
}
