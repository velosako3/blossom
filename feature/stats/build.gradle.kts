// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
    id("blossom.ios")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            implementation(projects.core.platform)
            api(projects.core.database)
            api(libs.kotlinx.datetime)
        }
    }
}
