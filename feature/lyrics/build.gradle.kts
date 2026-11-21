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
            implementation(projects.core.player)
            implementation(projects.core.database)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
            implementation(libs.jsoup)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.jaudiotagger)
        }
    }
}
