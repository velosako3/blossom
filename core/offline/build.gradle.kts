// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
    id("blossom.ios")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.platform)
            api(projects.core.database)
            api(projects.source.api)
            implementation(libs.sqldelight.coroutines)
        }
        // Downloading and the encrypted cache (OkHttp, javax.crypto): Android and desktop only for now.
        getByName("jvmSharedMain").dependencies { implementation(libs.okhttp) }
        androidMain.dependencies {
            implementation(libs.androidx.work)
            implementation(libs.media3.exoplayer)
        }
    }
}
