// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.Hlc
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class OpLogCodecTest {
    private val track = Track(TrackId.of(SourceKind.UNTITLED, "proj/trk"), "Kanye West – Song (Yedit) ✦", listOf(ArtistRef("a1", "Uploader")), AlbumRef(null, "Proj"), 105_290, "https://x/y.jpg", explicit = true)
    private val p = PlaylistId("p1")
    private val ops: List<Operation> = listOf(
        Operation.PlaylistCreated(p, "Road trip", "Summer 2026", PlaylistOrigin(SourceKind.YOUTUBE_MUSIC, "YouTube Music", "https://music.youtube.com/playlist?list=PL")),
        Operation.PlaylistRenamed(p, "Road trip 2"),
        Operation.PlaylistDescriptionSet(p, null),
        Operation.PlaylistDeleted(p),
        Operation.EntryAdded(p, "e1", track, EntryStatus.UNCERTAIN, "Song", "Uploader", "V", 0.72f),
        Operation.EntryAdded(p, "e2", null, EntryStatus.UNAVAILABLE, "Gone", "Someone", "W", null),
        Operation.EntryMoved(p, "e1", "Va"),
        Operation.EntryRemoved(p, "e2"),
        Operation.EntryTrackReplaced(p, "e1", track, EntryStatus.MATCHED),
        Operation.TrackLiked(track),
        Operation.TrackUnliked(track.id),
        Operation.PlayRecorded(PlayEvent("pe", track, Instant.fromEpochMilliseconds(1_800_000_000_123), 31_000, true, "dev")),
        Operation.SettingChanged("theme", "\"dark\""),
    )

    @Test fun everyOperationRoundTrips() {
        ops.forEachIndexed { i, op ->
            val env = OpEnvelope("op$i", Hlc(1_800_000_000_000 + i, i, "dev"), "dev", i.toLong(), op)
            val line = OpLogCodec.encode(env)
            assertTrue('\n' !in line, "one line per op")
            assertTrue(line.contains("\"v\":1"))
            assertEquals(env, assertIs<DecodedLine.Known>(OpLogCodec.decode(line)).op)
        }
        // Every subtype is covered.
        assertEquals(OpLogCodec.knownTypes, ops.map { OpLogCodec.encode(OpEnvelope("x", Hlc(1, 0, "d"), "d", 0, it)).let { l -> Regex("\"type\":\"(\\w+)\"").find(l)!!.groupValues[1] } }.toSet())
    }

    @Test fun futureTypesAreKeptAsUnknown() {
        val line = """{"v":1,"opId":"z","hlc":{"wallMs":1,"counter":0,"deviceId":"d"},"deviceId":"d","seq":3,"op":{"type":"FutureOp","x":42}}"""
        assertEquals(line, assertIs<DecodedLine.Unknown>(OpLogCodec.decode(line)).raw)
        val newer = """{"v":2,"opId":"z"}"""
        assertIs<DecodedLine.Unknown>(OpLogCodec.decode(newer))
    }

    @Test fun truncatedIsCorrupt() {
        val line = OpLogCodec.encode(OpEnvelope("a", Hlc(1, 0, "d"), "d", 0, ops[1]))
        assertIs<DecodedLine.Corrupt>(OpLogCodec.decode(line.dropLast(10)))
        assertIs<DecodedLine.Corrupt>(OpLogCodec.decode("""{"v":1,"opId":"a","op":{"type":"PlaylistRenamed"}}"""))
    }

    @Test fun manifestRoundTrips() {
        val m = DeviceManifest("dev", "Desktop", listOf(SegmentInfo(0, 5_000_000, 0, 9_999), SegmentInfo(1, 1234, 10_000, 10_020)), 10_020, Instant.fromEpochMilliseconds(1_800_000_000_000))
        assertEquals(m, DeviceManifest.decode(m.encode()))
        assertEquals("blossom/v1/devices/dev/manifest.json", SyncLayout.manifest("dev"))
        assertEquals("blossom/v1/devices/dev/ops-1.jsonl", SyncLayout.segment("dev", 1))
    }
}
