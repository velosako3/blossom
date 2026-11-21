// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.navigation

/** Width classes: bottom bar below 600 dp, navigation rail up to 839 dp, sidebar from 840 dp. */
enum class WindowLayout {
    COMPACT, MEDIUM, EXPANDED;

    companion object {
        fun from(widthDp: Float): WindowLayout = when {
            widthDp < 600f -> COMPACT
            widthDp < 840f -> MEDIUM
            else -> EXPANDED
        }
    }
}
