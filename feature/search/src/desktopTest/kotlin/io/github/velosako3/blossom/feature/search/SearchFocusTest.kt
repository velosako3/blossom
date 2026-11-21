// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.search

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.runComposeUiTest
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.platform.MapPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SearchFocusTest {
    @Test fun focusTriggerFocusesField() = runComposeUiTest {
        val trigger = mutableIntStateOf(0)
        val vm = SearchViewModel(RecordingSource(), RecentSearchStore(MapPreferenceStore()), CoroutineScope(Dispatchers.Unconfined))
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL) {
                SearchScreen(vm, { _, _ -> }, {}, {}, {}, focusTrigger = trigger.intValue)
            }
        }
        onNode(hasSetTextAction()).assertIsNotFocused()
        trigger.intValue = 1
        waitForIdle()
        onNode(hasSetTextAction()).assertIsFocused()
    }
}
