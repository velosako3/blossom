// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamSelectionTest {
    private val formats = listOf(
        AudioFormat("opus", 160, "u1"),
        AudioFormat("aac", 128, "u2"),
        AudioFormat("opus", 70, "u3"),
        AudioFormat("opus", 50, "u4"),
    )

    @Test fun highPicksHighestBitrate() = assertEquals(formats[0], selectAudioFormat(formats, AudioQuality.HIGH))
    @Test fun normalPicksClosestTo128() = assertEquals(formats[1], selectAudioFormat(formats, AudioQuality.NORMAL))
    @Test fun lowPicksLowestAtLeast48() = assertEquals(formats[3], selectAudioFormat(formats, AudioQuality.LOW))

    @Test fun highPrefersOpusAtEqualBitrate() {
        val tie = listOf(AudioFormat("aac", 128, "a"), AudioFormat("opus", 128, "o"))
        assertEquals("opus", selectAudioFormat(tie, AudioQuality.HIGH).codec)
    }

    @Test fun emptyThrows() {
        assertFailsWith<TrackUnavailableException> { selectAudioFormat(emptyList(), AudioQuality.HIGH) }
    }
}
