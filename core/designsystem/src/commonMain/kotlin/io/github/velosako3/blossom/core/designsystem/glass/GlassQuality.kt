// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

/** How much of the glass effect is drawn. Ordered from cheapest to richest. */
enum class GlassQuality { OFF, REDUCED, FULL }

/** The user's setting. [AUTO] lets the device and the frame-budget monitor decide. */
enum class GlassPreference { AUTO, FULL, REDUCED, OFF }

/** The richest glass this platform can draw. */
expect object GlassCapabilities {
    fun maxSupported(): GlassQuality
}

/** The lowest of what the device supports, what the user chose and what the monitor allows. */
fun resolveGlassQuality(max: GlassQuality, pref: GlassPreference, monitorCap: GlassQuality?): GlassQuality {
    val prefCap = when (pref) {
        GlassPreference.AUTO, GlassPreference.FULL -> GlassQuality.FULL
        GlassPreference.REDUCED -> GlassQuality.REDUCED
        GlassPreference.OFF -> GlassQuality.OFF
    }
    return minOf(max, prefCap, monitorCap ?: GlassQuality.FULL)
}
