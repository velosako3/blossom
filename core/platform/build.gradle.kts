// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
    id("blossom.ios")
}

kotlin {
    sourceSets {
        getByName("desktopMain").dependencies {
            implementation(libs.java.keyring)
        }
    }
}
