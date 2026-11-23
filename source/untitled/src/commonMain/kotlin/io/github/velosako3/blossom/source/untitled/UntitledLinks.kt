// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.untitled

import java.net.URI

sealed interface UntitledLink {
    data class Project(val slug: String) : UntitledLink
    /** [project] is set for a track inside a project, which has no page of its own. */
    data class Track(val slug: String, val project: String? = null) : UntitledLink
    /** Only resolvable by following its redirect. */
    data class Invite(val code: String) : UntitledLink
}

/** untitled.stream links (see docs/research/untitled.md): projects, tracks (also inside a project), embeds, invites. */
object UntitledLinks {
    private val PROJECT_TRACK = Regex("""^/library/project/([A-Za-z0-9]{10,40})/track/([A-Za-z0-9]{10,40})(?:/embed)?/?$""")
    private val PROJECT = Regex("""^/library/project/([A-Za-z0-9]{10,40})(?:/embed)?/?$""")
    private val TRACK = Regex("""^/library/track/([A-Za-z0-9]{10,40})(?:/embed)?/?$""")
    private val EMBED = Regex("""^/embed/([A-Za-z0-9]{10,40})/?$""")
    private val INVITE = Regex("""^/invite/([A-Za-z0-9_-]+)/?$""")

    fun parse(url: String): UntitledLink? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host != "untitled.stream") return null
        val path = uri.path.orEmpty()
        PROJECT_TRACK.matchEntire(path)?.let { return UntitledLink.Track(it.groupValues[2], project = it.groupValues[1]) }
        PROJECT.matchEntire(path)?.let { return UntitledLink.Project(it.groupValues[1]) }
        TRACK.matchEntire(path)?.let { return UntitledLink.Track(it.groupValues[1]) }
        EMBED.matchEntire(path)?.let { return UntitledLink.Track(it.groupValues[1]) }
        INVITE.matchEntire(path)?.let { return UntitledLink.Invite(it.groupValues[1]) }
        return null
    }
}
