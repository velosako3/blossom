// SPDX-License-Identifier: AGPL-3.0-or-later
rootProject.name = "build-logic"
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
