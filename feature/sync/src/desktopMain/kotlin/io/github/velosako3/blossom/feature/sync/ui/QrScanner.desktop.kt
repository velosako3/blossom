// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop pairs by showing the code or typing six words; it doesn't scan. */
@Composable
actual fun QrScanner(onCode: (String) -> Unit, modifier: Modifier) = Unit

actual val hasQrScanner: Boolean = false
