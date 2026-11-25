// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

/** The device's language ("en") and region ("US"), for services that localise results. Blank when unknown. */
expect fun deviceLanguage(): String
expect fun deviceRegion(): String
