// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.platform)
            api(projects.core.database)
            api(projects.source.api)
            implementation(libs.okhttp)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.androidx.work)
        }
    }
}
