// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.source.api.ImportedTrack
import kotlin.math.abs

data class MatchResult(val track: Track?, val confidence: Float, val status: EntryStatus)

/**
 * Score = 0.5 × title similarity (normalized Levenshtein) + 0.35 × artist overlap (Jaccard) + 0.15 × duration.
 * Duration: 1 within 3 s, falling to 0 at 10 s, 0.5 when unknown. MATCHED ≥ 0.80 and within 3 s; UNCERTAIN
 * 0.55–0.80 (or ≥ 0.80 but further apart); UNAVAILABLE below. A matching ISRC is MATCHED at 1.0.
 */
object TrackMatcher {
    private val SUFFIXES = listOf(
        // Only a version tail ("- Remastered 2011", "- Mono", "- Radio Edit"), never words that merely contain those.
        Regex("""\s+-\s+(\d{4}\s+)?(remaster(ed)?(\s+\d{4})?(\s+version)?|mono|stereo(\s+version)?|single version|radio edit|\d{4} (re)?mix)\s*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*[(\[][^)\]]*\b(remaster(ed)?|official (music )?(audio|video)|lyrics?( video)?|audio|visuali[sz]er|hd|hq)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
    )
    // "(with X)" only after a title, so "(With or Without You)" stays a title.
    private val FEAT_BRACKET = Regex("""(?<=\S)\s*[(\[]\s*(?:feat\.?|ft\.?|featuring|with)\s+([^)\]]+)[)\]]""", RegexOption.IGNORE_CASE)
    private val FEAT_TAIL = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val SPLIT_ARTISTS = Regex("""\s*(?:,|&| x | and |\+)\s*""", RegexOption.IGNORE_CASE)
    // Keeps combining marks (Devanagari vowel signs, decomposed accents).
    private val PUNCTUATION = Regex("""[^\p{L}\p{M}\p{N}\s]""")
    private val SPACES = Regex("""\s+""")

    private fun normalize(s: String) =
        PUNCTUATION.replace(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC).lowercase(), " ").let { SPACES.replace(it, " ").trim() }

    /** Title without decorations, plus artists taken out of "feat." parts. */
    private fun clean(title: String, artists: List<String>): Pair<String, Set<String>> {
        var t = title
        val extra = mutableListOf<String>()
        FEAT_BRACKET.findAll(t).forEach { extra += it.groupValues[1] }
        t = FEAT_BRACKET.replace(t, "")
        FEAT_TAIL.find(t)?.let { extra += it.groupValues[1]; t = t.substring(0, it.range.first) }
        SUFFIXES.forEach { t = it.replace(t, "") }
        val names = (artists + extra).flatMap { it.split(SPLIT_ARTISTS) }.map(::normalize).filter { it.isNotEmpty() }.toSet()
        return normalize(t) to names
    }

    private fun similarity(a: String, b: String): Float {
        // Titles that are only symbols ("!!!") say nothing; never let artist + duration match them alone.
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            cur.copyInto(prev)
        }
        return 1f - prev[b.length].toFloat() / maxOf(a.length, b.length)
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Float =
        if (a.isEmpty() || b.isEmpty()) 0f else a.intersect(b).size.toFloat() / a.union(b).size

    private fun durationDiffMs(imported: ImportedTrack, c: Track): Long? =
        imported.durationMs?.takeIf { it > 0 && c.durationMs > 0 }?.let { abs(it - c.durationMs) }

    private fun durationScore(diff: Long?): Float = when {
        diff == null -> 0.5f
        diff <= 3_000 -> 1f
        diff >= 10_000 -> 0f
        else -> 1f - (diff - 3_000) / 7_000f
    }

    fun score(imported: ImportedTrack, candidate: Track): Float {
        val (it1, ia) = clean(imported.title, imported.artists)
        val (ct, ca) = clean(candidate.title, candidate.artists.map { it.name })
        return 0.5f * similarity(it1, ct) + 0.35f * jaccard(ia, ca) + 0.15f * durationScore(durationDiffMs(imported, candidate))
    }

    fun match(imported: ImportedTrack, candidates: List<Track>, isrcOf: (Track) -> String? = { null }): MatchResult {
        imported.isrc?.let { isrc -> candidates.firstOrNull { isrcOf(it).equals(isrc, ignoreCase = true) }?.let { return MatchResult(it, 1f, EntryStatus.MATCHED) } }
        val best = candidates.map { it to score(imported, it) }.maxByOrNull { it.second } ?: return MatchResult(null, 0f, EntryStatus.UNAVAILABLE)
        val (track, s) = best
        val diff = durationDiffMs(imported, track)
        val status = when {
            // ✓ needs the duration checked: unknown on either side is "check this" at best.
            s >= 0.80f && diff != null && diff <= 3_000 -> EntryStatus.MATCHED
            s >= 0.55f -> EntryStatus.UNCERTAIN
            else -> EntryStatus.UNAVAILABLE
        }
        return MatchResult(track.takeIf { status != EntryStatus.UNAVAILABLE }, s, status)
    }
}
