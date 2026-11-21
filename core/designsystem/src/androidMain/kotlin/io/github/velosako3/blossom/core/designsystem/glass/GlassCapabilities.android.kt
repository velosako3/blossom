// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import android.os.Build

actual object GlassCapabilities {
    /** Android 13+ blurs and refracts (AGSL), Android 12 blurs only, older versions get a tint. */
    actual fun maxSupported(): GlassQuality = when {
        Build.VERSION.SDK_INT >= 33 -> GlassQuality.FULL
        Build.VERSION.SDK_INT >= 31 -> GlassQuality.REDUCED
        else -> GlassQuality.OFF
    }
}
