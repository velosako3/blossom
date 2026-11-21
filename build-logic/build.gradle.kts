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
    implementation(libs.vlcj.bundler.gradle.plugin)
}
