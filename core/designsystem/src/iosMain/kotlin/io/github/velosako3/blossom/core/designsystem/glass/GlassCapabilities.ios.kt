// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

actual object GlassCapabilities {
    /** Skia on iOS (as on desktop) supports blur and SkSL runtime effects everywhere. */
    actual fun maxSupported(): GlassQuality = GlassQuality.FULL
}
