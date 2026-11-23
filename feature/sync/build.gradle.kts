// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
    id("blossom.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            api(projects.core.database)
            api(projects.core.platform)
            implementation(libs.okhttp)
            implementation(libs.zxing.core)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.jmdns)
        }
        androidMain.dependencies {
            implementation(libs.androidx.work)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.androidx.activity.compose)
            // Google Drive sign-in on Android (Google no longer allows browser redirects for Android apps).
            implementation(libs.play.services.auth)
        }
        commonTest.dependencies {
            implementation(libs.okhttp.mockwebserver)
        }
    }
}
