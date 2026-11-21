// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

/** Platform-neutral audio focus changes (Android maps its AUDIOFOCUS_* codes onto these). */
enum class FocusChange { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

sealed interface FocusAction {
    data class Duck(val volume: Float) : FocusAction
    data class Pause(val resumeOnGain: Boolean) : FocusAction
    data object Resume : FocusAction
    data object Unduck : FocusAction
}

/**
 * What to do when another app takes or returns audio focus. Remembers whether a transient loss interrupted
 * playback, so regaining focus resumes only then.
 */
class AudioFocusPolicy {
    private var resumeOnGain = false

    fun onFocusChange(change: FocusChange, wasPlaying: Boolean): FocusAction = when (change) {
        FocusChange.LOSS_TRANSIENT_CAN_DUCK -> FocusAction.Duck(0.2f)
        FocusChange.LOSS_TRANSIENT -> { resumeOnGain = wasPlaying; FocusAction.Pause(resumeOnGain = wasPlaying) }
        FocusChange.LOSS -> { resumeOnGain = false; FocusAction.Pause(resumeOnGain = false) }
        FocusChange.GAIN -> if (resumeOnGain) { resumeOnGain = false; FocusAction.Resume } else FocusAction.Unduck
    }
}
