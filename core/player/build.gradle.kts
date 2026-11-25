// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
    id("blossom.ios")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.source.api)
            api(projects.core.platform)
        }
        // Saved (encrypted) music plays through the JVM engines; iOS streams only, for now.
        androidMain.dependencies { api(projects.core.offline) }
        getByName("desktopMain").dependencies {
            api(projects.core.offline)
            implementation(libs.vlcj)
            implementation(libs.okhttp)
            implementation(libs.nucleus.media.control)
        }
        androidMain.dependencies {
            api(libs.media3.exoplayer)
            api(libs.media3.session)
            implementation(libs.media3.datasource.okhttp)
            implementation(libs.media3.exoplayer.hls)
            implementation(libs.okhttp)
        }
    }
}
