// SPDX-License-Identifier: AGPL-3.0-or-later
// The Android application module (AGP 9 keeps it separate from the KMP modules).
import java.util.Properties

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
        // Dropbox's sign-in redirect scheme (db-<app key>); "db-none" when no key is configured.
        val secrets = Properties().apply { rootProject.file("secrets.properties").takeIf { it.isFile }?.reader()?.use(::load) }
        val dropboxKey = (secrets.getProperty("DROPBOX_APP_KEY") ?: System.getenv("DROPBOX_APP_KEY")).orEmpty().trim()
        manifestPlaceholders["dropboxScheme"] = if (dropboxKey.isEmpty()) "db-none" else "db-$dropboxKey"
    }
    // Release signing: the keystore from CI secrets (ANDROID_KEYSTORE_FILE, _PASSWORD, ANDROID_KEY_ALIAS, _PASSWORD);
    // without them the release build is signed with the debug key ("test build").
    val keystore = System.getenv("ANDROID_KEYSTORE_FILE")?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = keystore
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            // ponytail: no R8 yet; reflection-heavy libraries (NewPipe, Media3 extensions) need keep rules first.
            isMinifyEnabled = false
            signingConfig = if (keystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    // One APK per ABI plus a universal one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
