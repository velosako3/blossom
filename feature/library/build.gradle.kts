// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
    id("blossom.ios")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            api(projects.core.database)
            implementation(projects.core.player)
            implementation(libs.reorderable)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.jaudiotagger)
        }
    }
}
