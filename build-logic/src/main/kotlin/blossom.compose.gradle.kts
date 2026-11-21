// SPDX-License-Identifier: AGPL-3.0-or-later
// Compose Multiplatform on top of blossom.kmp.library, plus desktop UI and snapshot tests.
import org.jetbrains.compose.ComposePlugin

plugins {
    id("blossom.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

kotlin {
    android { androidResources { enable = true } }
    sourceSets {
        commonMain.dependencies {
            implementation(lib("compose-runtime"))
            implementation(lib("compose-foundation"))
            implementation(lib("compose-ui"))
            implementation(lib("compose-ui-backhandler"))
            implementation(lib("compose-material3"))
            implementation(lib("compose-material-icons"))
            implementation(lib("compose-components-resources"))
        }
        getByName("desktopTest").dependencies {
            implementation(ComposePlugin.DesktopDependencies.currentOs)
            implementation(lib("compose-ui-test"))
            implementation(lib("roborazzi-compose-desktop"))
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.velosako3.blossom." +
        project.path.removePrefix(":").replace(':', '.').replace('-', '_') + ".resources"
}

roborazzi { outputDir.set(file("src/desktopTest/snapshots")) }
