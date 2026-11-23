// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.update

import io.github.velosako3.blossom.core.platform.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** A newer build: its release page, and the installer for this platform when there is one ([assetUrl]). */
@Serializable
data class Update(val label: String, val pageUrl: String, val assetUrl: String? = null, val assetName: String? = null)

/**
 * Asks GitHub for the newest release at most once an hour; test builds ([testBuild]) also follow pre-releases, public
 * builds only public releases. A release is newer when it was
 * built from another commit than this build ([currentCommit]) and published after this build was made ([builtAtMs]).
 * Version numbers aren't compared: test builds share one. Builds made outside CI (no commit) never check.
 * Sends nothing but the request. Off when the user turns it off ([ENABLED_KEY]).
 *
 * [pickAsset] chooses this platform's installer from the release's file names (null: just the page).
 */
class UpdateChecker(
    private val currentCommit: String,
    private val builtAtMs: Long,
    private val pickAsset: (List<String>) -> String?,
    private val prefs: PreferenceStore,
    private val clock: Clock = Clock.System,
    private val http: OkHttpClient = OkHttpClient(),
    private val url: String = "https://api.github.com/repos/velosako3/blossom/releases?per_page=10",
    private val testBuild: Boolean = true,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): Update? {
        if (prefs.get(ENABLED_KEY) == "false" || currentCommit.isBlank()) return null
        val last = prefs.get(CHECKED_KEY)?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
        if (last != null && clock.now() - last < 1.hours) {
            return prefs.get(ANSWER_KEY)?.let { runCatching { json.decodeFromString(Update.serializer(), it) }.getOrNull() }
        }
        val found = fetch()
        prefs.put(CHECKED_KEY, clock.now().toEpochMilliseconds().toString())
        prefs.put(ANSWER_KEY, found?.let { json.encodeToString(Update.serializer(), it) })
        return found
    }

    private suspend fun fetch(): Update? {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("User-Agent", "blossom").build()
        val body = runInterruptible(Dispatchers.IO) { http.newCall(request).execute().use { check(it.isSuccessful) { "HTTP ${it.code}" }; it.body.string() } }
        val newest = json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
            .filterNot { it.bool("draft") || (!testBuild && it.bool("prerelease")) }
            .maxByOrNull { it.str("published_at").orEmpty() } ?: return null
        val commit = newest.str("target_commitish").orEmpty()
        val published = newest.str("published_at")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        if (commit.equals(currentCommit, ignoreCase = true) || published.toEpochMilliseconds() <= builtAtMs) return null
        val assets = newest["assets"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val chosen = pickAsset(assets.mapNotNull { it.str("name") })?.let { name -> assets.firstOrNull { it.str("name") == name } }
        return Update(
            label = newest.str("name")?.takeIf { it.isNotBlank() } ?: newest.str("tag_name").orEmpty(),
            pageUrl = newest.str("html_url").orEmpty(),
            assetUrl = chosen?.str("browser_download_url"),
            assetName = chosen?.str("name"),
        )
    }

    private fun JsonObject.str(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
    private fun JsonObject.bool(key: String) = this[key]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } == true

    companion object {
        const val ENABLED_KEY = "update.check"
        private const val CHECKED_KEY = "update.checkedAt"
        private const val ANSWER_KEY = "update.answer"

        /** Android: this device's ABI build, else the universal one. */
        fun androidApk(abi: String): (List<String>) -> String? = { names ->
            names.firstOrNull { it.endsWith(".apk") && it.contains(abi) } ?: names.firstOrNull { it.endsWith(".apk") && it.contains("universal") }
        }
    }
}
