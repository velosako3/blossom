// SPDX-License-Identifier: AGPL-3.0-or-later
// iOS for a shared module whose commonMain is portable (no JVM-only code). Apple targets compile to klibs on any
// machine; only linking the app needs macOS (CI).
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
}
