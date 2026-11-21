// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.source.api)
            api(projects.core.platform)
        }
    }
}
