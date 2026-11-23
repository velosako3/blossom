// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.haze)
            // Liquid glass (lens refraction, blur, highlight), as BitChord uses it. Apache-2.0.
            api(libs.kyant.backdrop)
            implementation(libs.haze.blur)
            implementation(libs.haze.glass)
            api(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.ktor.client.okhttp)
        }
    }
}
