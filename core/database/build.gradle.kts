// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
    id("blossom.serialization")
    id("blossom.sqldelight")
    id("blossom.ios")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
        }
    }
}

sqldelight {
    databases {
        create("BlossomDatabase") {
            packageName.set("io.github.velosako3.blossom.core.database")
        }
    }
}
