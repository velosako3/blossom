// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) at 21356206,
// shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsRepository.kt (GPL-3.0).
// Modified for blossom, 2026: package; a deadline on waiting for priority in [pick]; timed lyrics that run past the
// end of the track are rejected ([outlastsTrack]); name-matched timed lyrics are checked against Genius ([sameWords]).
package io.github.velosako3.blossom.feature.lyrics.providers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Where the player gets its lyrics.
 *
 * Sources tried in [order] — the user's own priority list in Settings,
 * defaulting to [LyricsSource.offered]; a [hidden][LyricsSource.hidden] one is
 * never asked, whatever the saved settings say:
 *
 *  - [LrcRed] — Apple Music TTML filed by ISRC, and the catalogue
 *    [BiniLyrics] itself answers from.
 *  - [BetterLyrics], [PaxSenix] and [BiniLyrics] — Apple Music TTML,
 *    per-syllable, from three independent hosts so one having a bad day
 *    doesn't cost the timing.
 *  - [LyricsPlus] — the YouLy+ backend; finest timing of the lot, flakiest hosting.
 *  - [SimpMusicLyrics] — keyed on the video id, so it can't fetch the wrong edit.
 *  - [Unison] — contributed rather than licensed; thin, and occasionally the
 *    only place a track exists.
 *  - [LrcLib], [Musixmatch], [KuGou] — line-synced only, but between them
 *    almost always up, and [KuGou] carries a lot that the others don't.
 *
 * Every enabled source is asked *at the same time*, but their answers are
 * taken in [order]: the loop awaits them one at a time in that sequence, so a
 * lower-priority source finishing first never preempts one still pending
 * ahead of it. Asked one after another instead, a miss on each source would
 * cost its own round trip before the next was even tried, and a track with no
 * lyrics anywhere would spend the best part of a minute finding that out with
 * eight of them. Run together, a miss costs whatever the slowest one needed
 * to still be waited on took.
 *
 * A word-timed answer wins outright. Failing that, a line-timed one is taken
 * from the highest-priority source that had it — better a whole line lighting
 * up in sync than the right animation on lyrics that don't exist.
 *
 * ### Matching on the recording
 *
 * Everything above matches a *name*, and a name is ambiguous in a way that
 * matters: a single and its album cut share a title, an artist and very nearly
 * a length, and routinely differ in the words. An ISRC names one recording and
 * settles it. Two sources accept one — [BiniLyrics] and [LyricsPlus] — and
 * [BiniLyrics] is also the one that hands them out, reporting the ISRC of
 * whatever its own search matched.
 *
 * So the recording is settled *before* anybody is asked for words: one small
 * search against [BiniLyrics], whose answer is a name every other source can
 * use. It costs a round trip at the head of the lookup, which is why it is
 * capped at [IDENTIFY_TIMEOUT_MS] and why its result is kept against the video
 * id — a track asked about twice pays for this once. A downloaded or local file
 * can skip it entirely: its own tags name the recording, which is what the
 * [isrc] parameter is for.
 *
 * Only run when the user has [LyricsSource.BINI_LYRICS] enabled. It is a
 * request to a third party like any other, and a source somebody has turned off
 * is a source this app does not contact — not even for something it would only
 * use to help the sources they left on.
 */
object LyricsRepository {

    /** Lyrics, and which source they turned out to come from. */
    data class Result(val source: LyricsSource, val lines: List<LyricLine>)

    /**
     * [sources] is the user's pick from Settings; anything not in it is not
     * contacted at all. An empty set means no lyrics, which is the same answer
     * as switching the feature off. [order] is tried first-to-last; a source
     * missing from it (an upgrade that added one after the order was last
     * saved) falls in after everything named, in [LyricsSource]'s own order.
     *
     * [prioritizeSyllableSync] decides what happens once *something* has come
     * back: off, the highest-priority source's own answer is taken as-is,
     * word-synced or not — priority is priority, and second-guessing it with
     * more network calls after it has already answered is not what "first"
     * was supposed to mean. On, a merely line-synced answer is kept only as a
     * fallback, and the search keeps going through the rest of [order] for a
     * word-synced one, taking the top-priority source that has one.
     */
    suspend fun lyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        sources: Set<LyricsSource> = LyricsSource.entries.toSet(),
        order: List<LyricsSource> = LyricsSource.entries,
        prioritizeSyllableSync: Boolean = false,
        isrc: String? = null,
        /** Called from the provider job itself, including for lazily-started providers. */
        onSourceStarted: ((LyricsSource) -> Unit)? = null,
        /**
         * Reports a completed provider attempt. A null result is a genuine miss;
         * a cancelled race loser is deliberately not reported as one.
         */
        onSourceResult: ((LyricsSource, Result?) -> Unit)? = null,
        /** Lets callers turn a cancelled race loser back into "not fetched". */
        onSourceCancelled: ((LyricsSource) -> Unit)? = null,
    ): Result? = coroutineScope {
        val sequence = (order + LyricsSource.offered)
            .distinct()
            .filter { it in sources && !it.hidden }

        // Every source but [SimpMusicLyrics] is asked for a name, and
        // YouTube's is not the name anyone catalogued. Cleaned once, here,
        // rather than by whichever source thought to do it for itself.
        val searchTitle = title.forLyricsSearch()
        val searchArtist = artist.artistForLyricsSearch()

        // Settled before anyone is asked for words, so every source that can
        // name the recording does. What the caller knows beats what we worked
        // out last time, and both beat asking again.
        val known = isrc?.takeIf { it.isNotBlank() } ?: isrcs[videoId]
        val hit = if (known == null) {
            identify(videoId, searchTitle, searchArtist, durationMs, album, sequence)
        } else {
            null
        }
        val recording = known ?: hit?.isrc?.takeIf { it.isNotBlank() }
        val documents = SharedDocuments(this)

        // Genius is a plain text web scraper. To preserve bandwidth and avoid rate-limiting,
        // it starts lazily and is only contacted if all higher-priority synced sources miss.
        val racing: List<Pair<LyricsSource, Deferred<Result?>>> = sequence.map { source ->
            val startMode = if (source == LyricsSource.GENIUS) kotlinx.coroutines.CoroutineStart.LAZY else kotlinx.coroutines.CoroutineStart.DEFAULT
            source to async(Dispatchers.IO, start = startMode) {
                onSourceStarted?.invoke(source)
                try {
                    val found = fetch(
                        source,
                        videoId,
                        searchTitle,
                        searchArtist,
                        durationMs,
                        album,
                        recording,
                        hit,
                        documents::get,
                    )?.let { result(source, it) }
                    onSourceResult?.invoke(source, found)
                    found
                } catch (cancelled: CancellationException) {
                    onSourceCancelled?.invoke(source)
                    throw cancelled
                } catch (_: Exception) {
                    onSourceResult?.invoke(source, null)
                    null
                }
            }
        }

        // blossom: a recording no catalogue knows will be checked against Genius ([sameWords]), so Genius starts
        // with everyone else instead of after them.
        if (recording == null) racing.firstOrNull { it.first == LyricsSource.GENIUS }?.second?.start()

        try {
            val picked = pick(racing.map { (source, job) -> source to suspend { job.await() } }, prioritizeSyllableSync)
            // blossom: a track no catalogue knows by its recording (most unreleased songs) was matched on its name
            // alone, and name matches are where another song's lyrics come from. Genius, which must match title and
            // artist, has the words for most of them: timed lyrics that don't share those words are another song.
            val genius = racing.firstOrNull { it.first == LyricsSource.GENIUS }?.second
            if (picked != null && recording == null && picked.source != LyricsSource.GENIUS && genius != null) {
                val reference = runCatching { withTimeoutOrNull(GENIUS_CHECK_MS) { genius.await() } }.getOrNull()
                if (reference != null && !sameWords(picked.lines, reference.lines)) return@coroutineScope reference
                // Nothing to check against: name-matched timed lyrics that never sing the title are taken for another
                // song's (Joji's "Nitrous" for Ye's, which sings it in its first line). No lyrics beats wrong ones.
                // LRCLIB answers only for the same title by the same artist (LrcLib.sameSong), so it is no name guess.
                if (reference == null && picked.source != LyricsSource.LRCLIB && picked.lines.any { it.timeMs > 0 } && !mentionsTitle(picked.lines, searchTitle)) {
                    // That source guessed wrong, but LRCLIB may still have the song itself (WW3 never sings "WW3").
                    val lrclib = racing.firstOrNull { it.first == LyricsSource.LRCLIB }?.second
                    return@coroutineScope runCatching { withTimeoutOrNull(GENIUS_CHECK_MS) { lrclib?.await() } }.getOrNull()
                        ?.takeIf { r -> r.lines.any { it.timeMs > 0 } }
                }
            }
            picked
        } finally {
            // Whoever lost the race is no longer worth waiting on, and
            // coroutineScope will not return while they are still running.
            racing.forEach { it.second.cancel() }
            documents.cancel()
        }
    }

    /**
     * The first word-synced result in [racing] order; failing that, the first
     * line-synced one; failing that, the first plain one. Without
     * [prioritizeSyllableSync], the first timed result wins outright.
     *
     * Plain and line-synced are kept apart: a plain result that happened to
     * arrive first must not shadow a line-synced one further down the order.
     */
    internal suspend fun pick(
        racing: List<Pair<LyricsSource, suspend () -> Result?>>,
        prioritizeSyllableSync: Boolean,
        /**
         * blossom: how long priority order is waited on. A source still pending after this no longer holds up an
         * answer that is already in: a track the top sources don't have (an unreleased song) would otherwise wait
         * out every one of their timeouts before a lower source's synced lyrics could show.
         */
        priorityWaitMs: Long = PRIORITY_WAIT_MS,
    ): Result? {
        var lineSynced: Result? = null
        var plain: Result? = null
        val left = racing.toMutableList()

        // Takes one source's answer; true when it settles the pick.
        fun take(found: Result?): Boolean {
            if (found == null) return false
            if (found.lines.any { it.isWordSynced }) { lineSynced = found; plain = null; return true }
            if (found.lines.any { it.timeMs > 0 }) {
                if (lineSynced == null) lineSynced = found
                return !prioritizeSyllableSync
            }
            if (plain == null) plain = found
            return false
        }
        fun best() = if (lineSynced?.lines?.any { it.isWordSynced } == true) lineSynced else lineSynced ?: plain

        // 1. In priority order, for as long as [priorityWaitMs] allows.
        val settled = withTimeoutOrNull(priorityWaitMs) {
            while (left.isNotEmpty()) {
                val (source, await) = left.first()
                // If we already found a line-synced or better result, skip Genius completely
                if ((lineSynced ?: plain) != null && source == LyricsSource.GENIUS) { left.removeAt(0); continue }
                val found = answer(await)
                left.removeAt(0)
                if (take(found)) return@withTimeoutOrNull true
            }
            true
        }
        if (settled != null) return best()

        // 2. Past the deadline: whatever has already answered, still in priority order.
        val pending = mutableListOf<Pair<LyricsSource, suspend () -> Result?>>()
        for (entry in left) {
            if (entry.first == LyricsSource.GENIUS) { pending += entry; continue }
            val done = withTimeoutOrNull(1) { answer(entry.second) ?: NONE }
            when {
                done == null -> pending += entry
                done !== NONE && take(done) -> return best()
            }
        }
        if (lineSynced != null) return best()

        // 3. Nothing timed yet: wait for the rest in order, as BitChord does.
        for ((source, await) in pending) {
            if ((lineSynced ?: plain) != null && source == LyricsSource.GENIUS) continue
            if (take(answer(await))) return best()
        }
        return best()
    }

    /**
     * A source's answer, null for a miss or a failure. Never swallows cancellation: the deadline above cancels by
     * throwing, and catching that here dropped every source still on its way (and with them, the lyrics).
     */
    private suspend fun answer(await: suspend () -> Result?): Result? =
        try { await() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /** Marks a source that answered "none" in the sweep, apart from one that is still running. */
    private val NONE = Result(LyricsSource.GENIUS, emptyList())

    /** blossom: every word of [title] (3+ letters, brackets ignored) is sung somewhere in [lines]. */
    internal fun mentionsTitle(lines: List<LyricLine>, title: String): Boolean {
        fun words(t: String) = t.lowercase().replace(Regex("""[(\[].*?[)\]]"""), " ").split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 3 }
        val wanted = words(title).toSet()
        if (wanted.isEmpty()) return true
        val sung = lines.flatMap { words(it.text) }.toSet()
        return sung.containsAll(wanted)
    }

    /** blossom: how long the Genius cross-check may add to a lookup. */
    private const val GENIUS_CHECK_MS = 4_000L

    /** blossom: whether [lines] share most of their words with [reference] (the same song, maybe transcribed differently). */
    internal fun sameWords(lines: List<LyricLine>, reference: List<LyricLine>): Boolean {
        fun words(l: List<LyricLine>) = l.flatMap { line -> line.text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")) }
            .filter { it.length >= 3 }.toSet()
        val a = words(lines); val b = words(reference)
        if (a.isEmpty() || b.isEmpty()) return true
        return (a intersect b).size.toDouble() / minOf(a.size, b.size) >= 0.35
    }

    /** blossom: see [pick]'s `priorityWaitMs`. */
    private const val PRIORITY_WAIT_MS = 2_500L

    /**
     * blossom: timed lyrics whose lines run more than 15 s past the end of the track are another song's (a single
     * against its extended cut, or a name match on an unreleased track). Unknown durations prove nothing.
     */
    internal fun outlastsTrack(lines: List<LyricLine>, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        val last = lines.filter { it.timeMs > 0 }.maxOfOrNull { it.timeMs } ?: return false
        return last > durationMs + 15_000
    }

    private suspend fun fetch(
        source: LyricsSource,
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        isrc: String?,
        /** What [identify] already found, where it ran; saves a second search. */
        hit: BiniLyrics.Hit?,
        /** Fetches a document once per lookup, however many sources want it. */
        get: suspend (String) -> String?,
    ): List<LyricLine>? {
        val found = when (source) {
            LyricsSource.LRC_RED -> LrcRed.lyrics(title, artist, durationMs, isrc, get)
            LyricsSource.BETTER_LYRICS -> BetterLyrics.lyrics(title, artist, durationMs, album)
            LyricsSource.BETTER_LYRICS_PORTATO -> BetterLyrics.portato(title, artist, durationMs, album)
            LyricsSource.LYRICS_PLUS -> LyricsPlus.lyrics(title, artist, durationMs, album, isrc)
            LyricsSource.BINI_LYRICS ->
                (hit?.let { BiniLyrics.lyricsFor(it, get) }
                    ?: BiniLyrics.lyrics(title, artist, durationMs, album, isrc, get))
                    ?.also { remember(videoId, it.isrc) }
                    ?.lines
            LyricsSource.UNISON -> Unison.lyrics(title, artist, durationMs, album)
            LyricsSource.SIMP_MUSIC -> SimpMusicLyrics.lyrics(videoId, durationMs)
            LyricsSource.YOUTUBE_TRANSCRIPT -> YouTubeTranscriptLyrics.lyrics(videoId)
            LyricsSource.YOUTUBE_MUSIC -> YouTubeMusicLyrics.lyrics(videoId)
            LyricsSource.LRCLIB -> LrcLib.lyrics(title, artist, durationMs)
            LyricsSource.MUSIXMATCH -> Musixmatch.lyrics(title, artist, durationMs)
            LyricsSource.PAXSENIX -> PaxSenix.lyrics(title, artist, durationMs, album)
            LyricsSource.PAXSENIX_SPOTIFY -> PaxSenix.spotifyLyrics(title, artist, durationMs)
            LyricsSource.PAXSENIX_MUSIXMATCH -> PaxSenix.musixmatchLyrics(title, artist, durationMs)
            LyricsSource.KUGOU -> KuGou.lyrics(title, artist, durationMs, album)
            LyricsSource.MEGALOBIZ -> Megalobiz.lyrics(title, artist)
            LyricsSource.GENIUS -> Genius.lyrics(title, artist)
        }
        // An instrumental's "pure music, please enjoy" is no lyrics, whoever relays it.
        return found?.takeUnless(KuGou::isInstrumentalNotice)?.takeUnless { outlastsTrack(it, durationMs) }
    }

    /**
     * Whichever source won, its lines get the same last pass: the answering
     * vocal split off the lead so it can be drawn under it. Done here rather
     * than in each parser because most of them write it as a bracket and only
     * [TtmlLyrics] knows it structurally — [withBackgroundVocals] leaves that
     * one's own split alone.
     */
    private fun result(source: LyricsSource, lines: List<LyricLine>) =
        Result(source, lines.withBackgroundVocals())

    /**
     * Longest the lookup will wait to find out which recording this is.
     *
     * Short on purpose. Knowing the recording makes every match better, but not
     * knowing it only puts things back where they were a release ago, and a
     * lyrics panel sitting empty because one host is having a slow morning is a
     * worse failure than a fuzzy match.
     */
    private const val IDENTIFY_TIMEOUT_MS = 2_500L

    /**
     * The one request made before the race: which recording is this?
     *
     * Skipped entirely when the source that answers it is switched off, and
     * given up on rather than waited out — see [IDENTIFY_TIMEOUT_MS]. What it
     * finds is remembered, so a track pays for this once rather than once per
     * lookup, and the hit is handed back so [BiniLyrics] need not search twice.
     */
    private suspend fun identify(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        sequence: List<LyricsSource>,
    ): BiniLyrics.Hit? {
        if (LyricsSource.BINI_LYRICS !in sequence) return null
        val hit = withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            runCatching { BiniLyrics.identify(title, artist, durationMs, album) }.getOrNull()
        }
        if (hit == null) return null
        remember(videoId, hit.isrc)
        return hit
    }

    /** How many recordings to keep in hand; see [isrcs]. */
    private const val REMEMBERED = 100

    /**
     * The recording behind a video id, once something has worked it out.
     *
     * Bounded, least-recently-used, and in memory only. This is a shortcut,
     * not a store: losing it costs one fuzzy match, which is what every lookup
     * did before any of this, and keeping it on disk would mean keeping a
     * wrong answer on disk too.
     */
    private val isrcs: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(REMEMBERED, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > REMEMBERED
        },
    )

    private fun remember(videoId: String, isrc: String?) {
        if (isrc.isNullOrBlank() || videoId.isEmpty()) return
        isrcs.put(videoId, isrc)
    }

    /**
     * Documents fetched during one lookup, by URL, so two sources after the
     * same file make one request between them. That is the ordinary case, not
     * an edge: [BiniLyrics]' search answers with an [LrcRed] URL, and [LrcRed],
     * handed the ISRC that search found, asks for exactly that URL too.
     *
     * Each download runs in the lookup's own scope rather than the asking
     * source's, so the source that started it losing the race doesn't take
     * the other one's copy down with it; [cancel] ends whatever is left.
     */
    private class SharedDocuments(private val scope: CoroutineScope) {
        private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()

        suspend fun get(url: String): String? =
            inFlight.computeIfAbsent(url) { scope.async(Dispatchers.IO) { lyricsGet(url) } }.await()

        fun cancel() = inFlight.values.forEach { it.cancel() }
    }
}
