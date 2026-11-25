// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.designsystem.components.ScrollbarLazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.licenses_title
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

/** One dependency as the generated `aboutlibraries.json` describes it. */
data class LicensedLibrary(val name: String, val version: String?, val licenses: List<String>, val url: String?)

/** Parses the AboutLibraries export: libraries, with license IDs resolved to names. */
fun parseLicenses(json: String): List<LicensedLibrary> {
    val root = Json.parseToJsonElement(json) as JsonObject
    val names = (root["licenses"] as? JsonObject).orEmpty().mapValues { (id, v) -> ((v as? JsonObject)?.get("name")?.jsonPrimitive?.content) ?: id }
    return (root["libraries"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        fun str(k: String) = o[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        LicensedLibrary(
            name = str("name") ?: str("uniqueId") ?: return@mapNotNull null,
            version = str("artifactVersion"),
            licenses = (o["licenses"] as? JsonArray).orEmpty().map { id -> id.jsonPrimitive.content.let { names[it] ?: it } },
            url = str("website") ?: (o["scm"] as? JsonObject)?.get("url")?.jsonPrimitive?.content,
        )
    }.sortedBy { it.name.lowercase() }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LicensesScreen(modifier: Modifier = Modifier, topBar: @Composable () -> Unit = {}) {
    var libraries by remember { mutableStateOf<List<LicensedLibrary>>(emptyList()) }
    LaunchedEffect(Unit) { libraries = runCatching { parseLicenses(Res.readBytes("files/aboutlibraries.json").decodeToString()) }.getOrDefault(emptyList()) }
    val uri = LocalUriHandler.current
    val muted = BlossomTheme.colors.onSurfaceVariant
    Column(modifier.fillMaxSize()) {
        topBar()
        ScrollbarLazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current)) {
            item { Text(stringResource(Res.string.licenses_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 12.dp)) }
            items(libraries, key = { it.name + it.version }) { lib ->
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (lib.url != null) Modifier.clickable(role = Role.Button) { runCatching { uri.openUri(lib.url) } } else Modifier)
                        .padding(vertical = 10.dp),
                ) {
                    Text(lib.name + (lib.version?.let { " $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    if (lib.licenses.isNotEmpty()) Text(lib.licenses.joinToString(", "), color = muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
