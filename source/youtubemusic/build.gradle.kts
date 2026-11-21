// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
    id("blossom.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.source.api)
            implementation(projects.core.platform)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.okhttp)
            implementation(libs.innertubex)
            implementation(libs.newpipe.extractor)
        }
    }
}
