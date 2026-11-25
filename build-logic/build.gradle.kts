// SPDX-License-Identifier: AGPL-3.0-or-later
plugins { `kotlin-dsl` }

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.roborazzi.gradle.plugin)
    implementation(libs.sqldelight.gradle.plugin)
    // Loaded here with the other plugins: on its own (with a version in composeApp) it brought a second copy of the
    // Kotlin Gradle plugin, and the iOS framework's shared build services clashed between the two.
    implementation("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:15.3.0")
}

