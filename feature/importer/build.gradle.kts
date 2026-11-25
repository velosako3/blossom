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
            api(projects.core.database)
            api(projects.source.api)
            implementation(libs.sqldelight.coroutines)
            implementation(projects.core.platform)
            implementation(libs.kotlinx.io.core)
        }
    }
}
