// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import kotlin.test.Test
import kotlin.test.assertEquals

class GlassQualityResolveTest {
    @Test fun autoNoCap() = assertEquals(GlassQuality.FULL, resolveGlassQuality(GlassQuality.FULL, GlassPreference.AUTO, null))
    @Test fun deviceCaps() = assertEquals(GlassQuality.REDUCED, resolveGlassQuality(GlassQuality.REDUCED, GlassPreference.FULL, null))
    @Test fun preferenceCaps() = assertEquals(GlassQuality.OFF, resolveGlassQuality(GlassQuality.FULL, GlassPreference.OFF, null))
    @Test fun monitorCaps() = assertEquals(GlassQuality.REDUCED, resolveGlassQuality(GlassQuality.FULL, GlassPreference.AUTO, GlassQuality.REDUCED))
}
