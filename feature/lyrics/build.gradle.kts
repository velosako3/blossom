// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
    id("blossom.serialization")
    id("blossom.ios")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            api(projects.core.model)
            implementation(projects.core.player)
            implementation(projects.core.database)
            implementation(libs.ktor.client.core)
            implementation(libs.lifecycle.runtime.compose)
        }
        // BitChord's sources (OkHttp, jsoup) run on Android and desktop; iOS asks LRCLIB through Ktor.
        getByName("jvmSharedMain").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
            implementation(libs.jsoup)
        }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        getByName("desktopMain").dependencies {
            implementation(libs.jaudiotagger)
        }
        // BitChord's panel collects with lifecycle, which needs a Main dispatcher (the app has Swing's).
        getByName("desktopTest").dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
