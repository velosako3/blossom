// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
    id("blossom.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.source.api)
            implementation(libs.okhttp)
        }
        commonTest.dependencies {
            implementation(libs.okhttp.mockwebserver)
        }
    }
}
