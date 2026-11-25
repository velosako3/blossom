// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.kmp.library")
    id("blossom.serialization")
    id("blossom.ios")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.source.api)
            implementation(projects.core.platform)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }
        // Streams on Android and desktop: NewPipe and InnerTubeX (JVM only). iOS asks YouTube's iOS client instead.
        getByName("jvmSharedMain").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
            implementation(libs.innertubex)
            implementation(libs.newpipe.extractor)
        }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        getByName("desktopTest").dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
