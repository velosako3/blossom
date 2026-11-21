// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    id("blossom.android.app")
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
}
