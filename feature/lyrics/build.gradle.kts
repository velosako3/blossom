// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
    id("blossom.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            api(projects.core.model)
            implementation(projects.core.database)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
        }
    }
}
