// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/InnertubeParser.kt (GPL-3.0).
// Modified for blossom, 2026: returns blossom models; library, podcast and account parsing removed.
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AlbumSummary
import io.github.velosako3.blossom.source.api.ArtistSummary
import io.github.velosako3.blossom.source.api.PlaylistSummary
import io.github.velosako3.blossom.source.api.SearchResults
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** YouTube Music (WEB_REMIX) JSON → blossom models. */
object YtmParser {
    private data class Credits(
        val artists: List<ArtistRef> = emptyList(),
        val albumId: String? = null,
        val albumName: String? = null,
    )

    /** A parsed list row: the track plus whether YouTube considers it a video rather than a song. */
    private data class Row(val track: Track, val isVideo: Boolean)

    fun searchSongs(response: JsonObject): List<Track> =
        collectRenderers(response, "musicResponsiveListItemRenderer")
            .mapNotNull { parseRow(it) }
            .filterNot { it.isVideo }
            .map { it.track }
            .distinctBy { it.id }

    /** Songs, albums, artists and playlists from an unfiltered search. */
    fun search(response: JsonObject): SearchResults {
        val songs = mutableListOf<Track>()
        val albums = mutableListOf<AlbumSummary>()
        val artists = mutableListOf<ArtistSummary>()
        val playlists = mutableListOf<PlaylistSummary>()
        val seen = HashSet<String>()
        collectRenderers(response, "musicResponsiveListItemRenderer").forEach { r ->
            val browse = r.o("navigationEndpoint").o("browseEndpoint")
            if (browse != null) {
                val id = browse.s("browseId") ?: return@forEach
                if (!seen.add(id)) return@forEach
                val cols = r.a("flexColumns").orEmpty()
                val title = cols.getOrNull(0).o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
                val subtitleRuns = cols.getOrNull(1).o("musicResponsiveListItemFlexColumnRenderer").o("text").a("runs").orEmpty()
                val subtitle = subtitleRuns.joinToString("") { it.s("text").orEmpty() }
                val art = r.o("thumbnail").o("musicThumbnailRenderer").o("thumbnail").a("thumbnails").best()
                if (title.isBlank()) return@forEach
                when {
                    "ALBUM" in pageType(browse) -> albums += AlbumSummary(
                        id, title, creditsOf(subtitleRuns).artists.ifEmpty { listOf(ArtistRef(null, artistFromSubtitle(subtitle))) },
                        YEAR.findAll(subtitle).lastOrNull()?.value?.toIntOrNull(), art,
                    )
                    "ARTIST" in pageType(browse) -> artists += ArtistSummary(id, title, art)
                    "PLAYLIST" in pageType(browse) -> playlists += PlaylistSummary(
                        id.removePrefix("VL"), title, artistFromSubtitle(subtitle), art, null,
                    )
                }
            } else {
                parseRow(r)?.takeUnless { it.isVideo }?.let { if (seen.add(it.track.id.value)) songs += it.track }
            }
        }
        return SearchResults(songs, albums, artists, playlists)
    }

    fun home(response: JsonObject): List<HomeSection> {
        val sections = response.o("contents").o("singleColumnBrowseResultsRenderer").a("tabs")?.firstOrNull()
            .o("tabRenderer").o("content").o("sectionListRenderer").a("contents").orEmpty()
        return sections.mapNotNull { section -> section.o("musicCarouselShelfRenderer")?.let(::carousel) }
    }

    private fun carousel(carousel: JsonObject): HomeSection? {
        val header = carousel.o("header").o("musicCarouselShelfBasicHeaderRenderer")
        val title = header.o("title").runs()
        val items = carousel.a("contents").orEmpty().mapNotNull { item ->
            parseTwoRowItem(item.o("musicTwoRowItemRenderer"))
                ?: item.o("musicResponsiveListItemRenderer")?.let { r -> parseRow(r)?.takeUnless { it.isVideo }?.let { HomeItem.Song(it.track) } }
        }
        if (title.isBlank() || items.isEmpty()) return null
        return HomeSection(title, items, header.o("strapline").runs().ifBlank { null })
    }

    private fun parseTwoRowItem(r: JsonObject?): HomeItem? {
        if (r == null) return null
        val title = r.o("title").runs()
        if (title.isBlank()) return null
        val endpoint = r.o("navigationEndpoint")
        val browse = endpoint.o("browseEndpoint")
        val browseId = browse.s("browseId")
        val videoId = endpoint.o("watchEndpoint").s("videoId")
        val thumbs = r.o("thumbnailRenderer").o("musicThumbnailRenderer").o("thumbnail").a("thumbnails")
        val subtitleRuns = r.o("subtitle").a("runs").orEmpty()
        val subtitle = r.o("subtitle").runs()
        val art = thumbs.best()
        val type = browse?.let(::pageType).orEmpty()
        return when {
            videoId != null && browseId == null -> {
                if (thumbs.isNotSquare()) return null // music video card
                val credits = creditsOf(subtitleRuns)
                HomeItem.Song(
                    Track(
                        id = TrackId.of(SourceKind.YOUTUBE_MUSIC, videoId),
                        title = title,
                        artists = credits.artists.ifEmpty { listOf(ArtistRef(null, artistFromSubtitle(subtitle))) },
                        album = credits.albumId?.let { AlbumRef(it, credits.albumName ?: "") },
                        durationMs = 0,
                        artworkUrl = art,
                    ),
                )
            }
            browseId == null -> null
            "ALBUM" in type -> HomeItem.Album(
                AlbumSummary(browseId, title, creditsOf(subtitleRuns).artists, YEAR.findAll(subtitle).lastOrNull()?.value?.toIntOrNull(), art),
            )
            "ARTIST" in type -> HomeItem.Artist(ArtistSummary(browseId, title, art))
            "PLAYLIST" in type || browseId.startsWith("VL") -> HomeItem.Playlist(
                PlaylistSummary(browseId.removePrefix("VL"), title, artistFromSubtitle(subtitle).ifBlank { null }, art, null),
            )
            else -> null
        }
    }

    fun album(browseId: String, response: JsonObject): AlbumPage {
        val header = HEADER_RENDERERS.firstNotNullOfOrNull { collectRenderers(response, it).firstOrNull() }
        val title = header.o("title").runs()
        val creditRuns = HEADER_CREDIT_LINES.flatMap { header.o(it).a("runs").orEmpty() }
        val credits = creditsOf(creditRuns)
        val creditText = HEADER_CREDIT_LINES.joinToString(" • ") { header.o(it).runs() }
        val art = header?.let { collectRenderers(it, "musicThumbnailRenderer").firstOrNull() }.o("thumbnail").a("thumbnails").best()
        val albumArtists = credits.artists.ifEmpty { listOf(ArtistRef(null, artistFromSubtitle(creditText))) }
        val ref = AlbumRef(browseId, title)
        val tracks = collectRenderers(response, "musicResponsiveListItemRenderer").mapNotNull { r ->
            parseRow(r)?.track?.let { t ->
                t.copy(
                    album = ref,
                    artists = t.artists.takeUnless { it.size == 1 && it[0].name == UNKNOWN } ?: albumArtists,
                    artworkUrl = t.artworkUrl ?: art,
                )
            }
        }.distinctBy { it.id }
        return AlbumPage(
            id = browseId,
            title = title,
            artists = albumArtists,
            year = YEAR.findAll(creditText).lastOrNull()?.value?.toIntOrNull(),
            artworkUrl = art,
            tracks = tracks.map { if (it.artworkUrl == null) it.copy(artworkUrl = art) else it },
            playlistId = albumPlaylistId(response),
        )
    }

    fun artist(browseId: String, response: JsonObject): ArtistPage {
        val sections = response.o("contents").o("singleColumnBrowseResultsRenderer").a("tabs")?.firstOrNull()
            .o("tabRenderer").o("content").o("sectionListRenderer").a("contents").orEmpty()
        val header = response["header"]
        val headerRenderer = header.o("musicImmersiveHeaderRenderer") ?: header.o("musicVisualHeaderRenderer")
        val name = headerRenderer.o("title").runs()
        val self = listOf(ArtistRef(browseId, name))
        val topSongs = mutableListOf<Track>()
        val shelves = mutableListOf<HomeSection>()
        sections.forEach { section ->
            section.o("musicShelfRenderer")?.let { shelf ->
                shelf.a("contents").orEmpty().forEach { row ->
                    parseRow(row.o("musicResponsiveListItemRenderer"))?.takeUnless { it.isVideo }?.let { r ->
                        topSongs += r.track.let { t -> if (t.artists.size == 1 && t.artists[0].name == UNKNOWN) t.copy(artists = self) else t }
                    }
                }
            }
            section.o("musicCarouselShelfRenderer")?.let { carousel(it) }?.let(shelves::add)
        }
        val thumb = (headerRenderer.o("thumbnail") ?: headerRenderer.o("foregroundThumbnail"))
            .o("musicThumbnailRenderer").o("thumbnail").a("thumbnails").best()
        val description = headerRenderer.o("description").runs().ifBlank { null }
        return ArtistPage(browseId, name, thumb, description, topSongs.distinctBy { it.id }, shelves)
    }

    fun playlist(id: String, response: JsonObject): PlaylistPage {
        val header = HEADER_RENDERERS.firstNotNullOfOrNull { collectRenderers(response, it).firstOrNull() }
        val tracks = collectRenderers(response, "musicResponsiveListItemRenderer").mapNotNull { parseRow(it)?.track }.distinctBy { it.id }
        return PlaylistPage(
            id = id,
            title = header.o("title").runs(),
            author = header.o("straplineTextOne").runs().ifBlank { header.o("subtitle").runs() }.ifBlank { null },
            artworkUrl = header?.let { collectRenderers(it, "musicThumbnailRenderer").firstOrNull() }.o("thumbnail").a("thumbnails").best(),
            tracks = tracks,
            continuation = continuationToken(response),
        )
    }

    /** Tracks of a continuation page (playlists longer than one page). */
    fun continuationTracks(response: JsonObject): Pair<List<Track>, String?> =
        collectRenderers(response, "musicResponsiveListItemRenderer").mapNotNull { parseRow(it)?.track } to continuationToken(response)

    /** The "up next" radio queue from a `next` response. */
    fun watchQueue(response: JsonElement): List<Track> =
        collectRenderers(response, "playlistPanelVideoRenderer").mapNotNull { r ->
            val videoId = r.s("videoId") ?: return@mapNotNull null
            val title = r.o("title").runs().ifBlank { return@mapNotNull null }
            val bylineRuns = r.o("longBylineText").a("runs").orEmpty()
            val byline = bylineRuns.map { it.s("text").orEmpty() }
            val artist = byline.takeWhile { !it.contains("•") }.joinToString("").trim()
            val credits = creditsOf(bylineRuns)
            Track(
                id = TrackId.of(SourceKind.YOUTUBE_MUSIC, videoId),
                title = title,
                artists = credits.artists.ifEmpty { listOf(ArtistRef(null, artist.ifBlank { UNKNOWN })) },
                album = credits.albumId?.let { AlbumRef(it, credits.albumName.orEmpty()) },
                durationMs = parseDurationMs(r.o("lengthText").runs()) ?: 0,
                artworkUrl = r.o("thumbnail").a("thumbnails").best(),
                explicit = r.hasExplicitBadge(),
            )
        }.distinctBy { it.id }

    fun continuationToken(root: JsonElement): String? {
        val endpoint = collectRenderers(root, "continuationItemRenderer").firstOrNull().o("continuationEndpoint")
        (endpoint.o("continuationCommand") ?: endpoint.o("commandExecutorCommand").a("commands")?.firstNotNullOfOrNull { it.o("continuationCommand") })
            .s("token")?.let { return it }
        return collectRenderers(root, "nextContinuationData").firstOrNull().s("continuation")
    }

    private fun parseRow(r: JsonObject?): Row? {
        if (r == null) return null
        val videoId = r.o("playlistItemData").s("videoId")
            ?: r.o("overlay").o("musicItemThumbnailOverlayRenderer").o("content").o("musicPlayButtonRenderer")
                .o("playNavigationEndpoint").o("watchEndpoint").s("videoId")
            ?: return null
        val columns = r.a("flexColumns").orEmpty()
        val title = columns.getOrNull(0).o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        if (title.isBlank()) return null
        val subtitleRuns = columns.getOrNull(1).o("musicResponsiveListItemFlexColumnRenderer").o("text").a("runs").orEmpty()
        val subtitle = subtitleRuns.joinToString("") { it.s("text").orEmpty() }
        val parts = subtitle.split(" • ").filter { it.isNotBlank() }
        val duration = parts.lastOrNull()?.takeIf { it.matches(DURATION) }
            ?: r.a("fixedColumns").orEmpty().firstNotNullOfOrNull { c ->
                c.o("musicResponsiveListItemFixedColumnRenderer").o("text").runs().takeIf { it.matches(DURATION) }
            }
        val rowType = parts.firstOrNull { it.lowercase() in TYPE_WORDS }?.lowercase()
        val plainArtist = parts.firstOrNull { !it.matches(DURATION) && it.lowercase() !in TYPE_WORDS && !it.matches(TALLY) }
        val credits = creditsOf(columns.flatMap { it.o("musicResponsiveListItemFlexColumnRenderer").o("text").a("runs").orEmpty() })
        val thumbs = r.o("thumbnail").o("musicThumbnailRenderer").o("thumbnail").a("thumbnails")
        val episode = rowType == "episode" || r.findString("musicVideoType") == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
        if (episode) return null
        val artists = credits.artists.ifEmpty {
            artistNamesFromRuns(subtitleRuns)?.split(", ")?.map { ArtistRef(null, it) }
                ?: plainArtist?.let { listOf(ArtistRef(null, it)) }
                ?: listOf(ArtistRef(null, UNKNOWN))
        }
        return Row(
            Track(
                id = TrackId.of(SourceKind.YOUTUBE_MUSIC, videoId),
                title = title,
                artists = artists,
                album = credits.albumId?.let { AlbumRef(it, credits.albumName.orEmpty()) },
                durationMs = parseDurationMs(duration) ?: 0,
                artworkUrl = thumbs.best(),
                explicit = r.hasExplicitBadge(),
            ),
            isVideo = rowType == "video" || thumbs.isNotSquare(),
        )
    }

    private fun pageType(browse: JsonObject): String =
        browse.o("browseEndpointContextSupportedConfigs").o("browseEndpointContextMusicConfig").s("pageType").orEmpty()

    private fun creditsOf(runs: List<JsonElement>): Credits {
        val artists = mutableListOf<ArtistRef>()
        var albumId: String? = null
        var albumName: String? = null
        runs.forEach { run ->
            val browse = run.o("navigationEndpoint").o("browseEndpoint") ?: return@forEach
            val id = browse.s("browseId") ?: return@forEach
            val type = pageType(browse)
            val name = run.s("text").orEmpty()
            when {
                "ARTIST" in type || "USER_CHANNEL" in type -> if (name.isNotBlank() && artists.none { it.id == id }) artists += ArtistRef(id, name)
                "ALBUM" in type && albumId == null -> { albumId = id; albumName = name }
            }
        }
        return Credits(artists, albumId, albumName)
    }

    /** Names in the credit group of a "Song • Artist & Artist • Album • 3:45" line. */
    private fun artistNamesFromRuns(runs: List<JsonElement>): String? {
        val groups = mutableListOf<MutableList<JsonElement>>(mutableListOf())
        runs.forEach { run -> if (run.s("text")?.trim() == "•") groups += mutableListOf<JsonElement>() else groups.last() += run }
        val artistGroup = groups.firstOrNull { g -> g.any { "ARTIST" in (it.o("navigationEndpoint").o("browseEndpoint")?.let(::pageType) ?: "") } }
            ?: return null
        return artistGroup.mapIndexedNotNull { i, run -> if (i % 2 != 0) null else run.s("text")?.trim()?.takeIf { it.isNotBlank() } }
            .distinct().joinToString(", ").ifBlank { null }
    }

    private fun albumPlaylistId(root: JsonElement): String? {
        collectRenderers(root, "musicPlayButtonRenderer").firstNotNullOfOrNull {
            it.o("playNavigationEndpoint").o("watchEndpoint").s("playlistId")
                ?: it.o("playNavigationEndpoint").o("watchPlaylistEndpoint").s("playlistId")
        }?.let { return it }
        val canonical = root.o("microformat").o("microformatDataRenderer").s("urlCanonical") ?: return null
        return canonical.substringAfter("list=", "").substringBefore('&').ifBlank { null }
    }

    fun artistFromSubtitle(subtitle: String): String =
        subtitle.split(" • ").map(String::trim).filter { it.isNotBlank() }
            .firstOrNull { it.lowercase() !in TYPE_WORDS && !it.matches(TALLY) && !it.matches(DURATION) && !it.matches(YEAR) } ?: subtitle

    private fun JsonElement?.hasExplicitBadge(): Boolean = when (this) {
        is JsonPrimitive -> contentOrNull == "MUSIC_EXPLICIT_BADGE"
        is JsonArray -> any { it.hasExplicitBadge() }
        is JsonObject -> values.any { it.hasExplicitBadge() }
        null -> false
    }

    const val UNKNOWN = "Unknown artist"
    private val DURATION = Regex("""\d+(:\d{2}){1,2}""")
    private val YEAR = Regex("""\b\d{4}\b""")
    private val TALLY = Regex("""[\d.,]+\s*[KMB]?\s+(plays|views|likes|songs|tracks|subscribers|hours?|minutes?|seconds?)\b.*""", RegexOption.IGNORE_CASE)
    private val HEADER_RENDERERS = listOf("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer", "musicEditablePlaylistDetailHeaderRenderer")
    private val HEADER_CREDIT_LINES = listOf("straplineTextOne", "subtitle")
    private val TYPE_WORDS = setOf("song", "video", "album", "single", "ep", "artist", "playlist", "podcast", "episode")
}
