// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import android.os.Build

actual object GlassCapabilities {
    /**
     * Android 13+ blurs and refracts (AGSL), Android 12 blurs only, older versions get a tint. The emulator renders on
     * the CPU: compiling the glass shaders there takes seconds per frame and starts the app into an ANR, so it gets
     * the tint.
     */
    actual fun maxSupported(): GlassQuality = when {
        isEmulator -> GlassQuality.OFF
        Build.VERSION.SDK_INT >= 33 -> GlassQuality.FULL
        Build.VERSION.SDK_INT >= 31 -> GlassQuality.REDUCED
        else -> GlassQuality.OFF
    }

    private val isEmulator: Boolean =
        Build.HARDWARE in setOf("ranchu", "goldfish") || Build.PRODUCT.startsWith("sdk_gphone") || Build.FINGERPRINT.startsWith("generic")
}
