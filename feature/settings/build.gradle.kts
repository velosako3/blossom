// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.designsystem)
            api(projects.core.offline)
        }
    }
}
