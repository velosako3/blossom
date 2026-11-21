// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

internal actual fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder = allowHardware(false)
internal actual fun toImageBitmap(image: coil3.Image): ImageBitmap = image.toBitmap().asImageBitmap()
