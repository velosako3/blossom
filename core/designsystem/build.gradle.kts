// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.compose")
    id("blossom.ios")
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
        }
        // Coil's network engine: OkHttp on the JVM, Apple's URLSession on iOS.
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        getByName("desktopMain").dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    }
}
