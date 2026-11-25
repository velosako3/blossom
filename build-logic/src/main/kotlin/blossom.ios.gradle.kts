// SPDX-License-Identifier: AGPL-3.0-or-later
// iOS for a shared module whose commonMain is portable (no JVM-only code). Apple klibs compile on any machine; only
// linking the app needs macOS (CI). jvmSharedMain holds what Android and desktop share but iOS can't use.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
            }
        }
    }
    // The Android library plugin's target isn't one the template can name; join it to jvmShared by hand.
    sourceSets.named("androidMain") { dependsOn(sourceSets.getByName("jvmSharedMain")) }
}
