// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            implementation(projects.core.player)
            implementation(projects.source.youtubemusic)
        }
    }
}
