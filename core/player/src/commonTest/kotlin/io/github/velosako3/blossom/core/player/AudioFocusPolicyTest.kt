// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioFocusPolicyTest {
    @Test fun canDuckDucksTo20Percent() =
        assertEquals(FocusAction.Duck(0.2f), AudioFocusPolicy().onFocusChange(FocusChange.LOSS_TRANSIENT_CAN_DUCK, wasPlaying = true))

    @Test fun transientLossPausesAndResumesIfPlaying() {
        val p = AudioFocusPolicy()
        assertEquals(FocusAction.Pause(resumeOnGain = true), p.onFocusChange(FocusChange.LOSS_TRANSIENT, wasPlaying = true))
        assertEquals(FocusAction.Resume, p.onFocusChange(FocusChange.GAIN, wasPlaying = false))
    }

    @Test fun transientLossWhilePausedDoesNotResume() {
        val p = AudioFocusPolicy()
        assertEquals(FocusAction.Pause(resumeOnGain = false), p.onFocusChange(FocusChange.LOSS_TRANSIENT, wasPlaying = false))
        assertEquals(FocusAction.Unduck, p.onFocusChange(FocusChange.GAIN, wasPlaying = false))
    }

    @Test fun permanentLossNeverResumes() {
        val p = AudioFocusPolicy()
        assertEquals(FocusAction.Pause(resumeOnGain = false), p.onFocusChange(FocusChange.LOSS, wasPlaying = true))
        assertEquals(FocusAction.Unduck, p.onFocusChange(FocusChange.GAIN, wasPlaying = false))
    }

    @Test fun userPlayDuringLossCancelsAutoResume() {
        val p = AudioFocusPolicy()
        p.onFocusChange(FocusChange.LOSS_TRANSIENT, wasPlaying = true)
        p.onUserPlay()
        assertEquals(FocusAction.Unduck, p.onFocusChange(FocusChange.GAIN, wasPlaying = false))
    }

    @Test fun gainAfterDuckUnducks() {
        val p = AudioFocusPolicy()
        p.onFocusChange(FocusChange.LOSS_TRANSIENT_CAN_DUCK, wasPlaying = true)
        assertEquals(FocusAction.Unduck, p.onFocusChange(FocusChange.GAIN, wasPlaying = true))
    }
}
