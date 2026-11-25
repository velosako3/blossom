// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import java.util.Locale

actual fun deviceLanguage(): String = Locale.getDefault().language.orEmpty()
actual fun deviceRegion(): String = Locale.getDefault().country.orEmpty()
