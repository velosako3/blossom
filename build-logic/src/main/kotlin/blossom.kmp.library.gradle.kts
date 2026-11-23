// SPDX-License-Identifier: AGPL-3.0-or-later
// Every shared module: Android library + desktop JVM, configured the same way.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val libs = the<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()
fun ver(alias: String) = libs.findVersion(alias).get().requiredVersion

kotlin {
    android {
        namespace = "io.github.velosako3.blossom." +
            project.path.removePrefix(":").replace(':', '.').replace('-', '_')
        compileSdk = ver("android-compileSdk").toInt()
        minSdk = ver("android-minSdk").toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
        optIn.addAll("kotlin.time.ExperimentalTime", "androidx.compose.ui.ExperimentalComposeUiApi")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(lib("kotlinx-coroutines-core"))
        }
        commonTest.dependencies {
            implementation(lib("kotlin-test"))
            implementation(lib("kotlinx-coroutines-test"))
        }
        // Shared test fixtures live in src/commonTest/resources and are read from the desktop test classpath.
        getByName("desktopTest").resources.srcDir("src/commonTest/resources")
    }
}

// Live-network tests are named *LiveTest and only run with -PliveTests=true.
val liveTests = providers.gradleProperty("liveTests").map { it.toBoolean() }.getOrElse(false)
tasks.withType<Test>().configureEach {
    if (!liveTests) filter {
        excludeTestsMatching("*LiveTest")
        isFailOnNoMatchingTests = false
    }
    maxHeapSize = "2g"
}
