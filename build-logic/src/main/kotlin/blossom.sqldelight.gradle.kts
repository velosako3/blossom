// SPDX-License-Identifier: AGPL-3.0-or-later
// SQLDelight with the right driver per target.
plugins {
    id("app.cash.sqldelight")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
    sourceSets.getByName("commonMain").dependencies {
        api(lib("sqldelight-runtime"))
        implementation(lib("sqldelight-coroutines"))
    }
    sourceSets.getByName("desktopMain").dependencies { implementation(lib("sqldelight-sqlite-driver")) }
    sourceSets.getByName("androidMain").dependencies { implementation(lib("sqldelight-android-driver")) }
}
