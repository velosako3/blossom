// SPDX-License-Identifier: AGPL-3.0-or-later
// kotlinx.serialization for a blossom.kmp.library module.
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}

val libs = the<VersionCatalogsExtension>().named("libs")

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.getByName("commonMain").dependencies {
        api(libs.findLibrary("kotlinx-serialization-json").get())
    }
}
