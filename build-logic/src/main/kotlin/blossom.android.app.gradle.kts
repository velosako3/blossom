// SPDX-License-Identifier: AGPL-3.0-or-later
// The Android application module (AGP 9 keeps it separate from the KMP modules).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun ver(alias: String) = libs.findVersion(alias).get().requiredVersion

android {
    namespace = "io.github.velosako3.blossom"
    compileSdk = ver("android-compileSdk").toInt()
    defaultConfig {
        applicationId = "io.github.velosako3.blossom"
        minSdk = ver("android-minSdk").toInt()
        targetSdk = ver("android-targetSdk").toInt()
        versionName = providers.gradleProperty("blossom.version").get()
        versionCode = versionName!!.split('.', '-').take(3).fold(0) { acc, s -> acc * 1000 + (s.toIntOrNull() ?: 0) }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
