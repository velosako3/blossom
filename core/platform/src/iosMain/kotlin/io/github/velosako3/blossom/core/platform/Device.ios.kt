// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun deviceLanguage(): String = NSLocale.currentLocale.languageCode
actual fun deviceRegion(): String = NSLocale.currentLocale.countryCode.orEmpty()
