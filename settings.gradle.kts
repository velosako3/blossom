// SPDX-License-Identifier: AGPL-3.0-or-later
rootProject.name = "blossom"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // InnerTubeX, QuackJS and NewPipe Extractor are published only on JitPack.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.MetrolistGroup.innertubex")
                includeGroup("com.github.MetrolistGroup.quackjs")
                includeGroup("com.github.TeamNewPipe")
                includeGroupByRegex("com\\.github\\.TeamNewPipe.*")
            }
        }
    }
}

include(":composeApp", ":androidApp")
include(":core:designsystem", ":core:model", ":core:platform", ":core:player", ":core:database", ":core:offline")
include(":source:api", ":source:youtubemusic", ":source:spotify", ":source:soundcloud", ":source:untitled")
include(":feature:home", ":feature:search", ":feature:album", ":feature:artist", ":feature:nowplaying", ":feature:library", ":feature:lyrics", ":feature:settings", ":feature:importer", ":feature:stats", ":feature:sync")
