// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.source.api)
            api(projects.core.platform)
        }
        androidMain.dependencies {
            api(libs.media3.exoplayer)
            api(libs.media3.session)
            implementation(libs.media3.datasource.okhttp)
            implementation(libs.okhttp)
        }
    }
}
