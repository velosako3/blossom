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
        }
        androidMain.dependencies {
            implementation(libs.androidx.work)
        }
        commonTest.dependencies {
            implementation(libs.okhttp.mockwebserver)
        }
    }
}
