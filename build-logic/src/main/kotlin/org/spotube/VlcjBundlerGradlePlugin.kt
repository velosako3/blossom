// SPDX-License-Identifier: Apache-2.0
// From team-spotube/vlcj-bundler-gradle-plugin (commit 132740edaa, published as 0.2.1), Apache License 2.0: see LICENSE here.
// Modified for blossom, 2026.
package org.spotube

import org.gradle.work.DisableCachingByDefault
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

class VlcjBundlerGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension =
            project.extensions.create("vlcjBundler", VlcjBundlerExtension::class.java).apply {
                packageName.convention("org.spotube.core.audioplayer")
                objectName.convention("VLCBundleLoader")
                bundleVlc.convention(true)
                linuxUbuntuRelease.convention("noble")
            }

        val generateLoaderTask =
            project.tasks.register("generateVlcBundleLoader", GenerateVlcLoaderTask::class.java) {
                packageName.set(extension.packageName)
                objectName.set(extension.objectName)
                outputDirectory.set(project.layout.buildDirectory.dir("generated/vlcjBundler/vlcjOut"))
            }

        // 1. Register the download task and explicitly give it its output target
        val downloadTask =
            project.tasks.register("downloadVlcNatives", DownloadVlcNativesTask::class.java) {
                vlcNativesDirectory.set(project.layout.buildDirectory.dir("vlc-natives"))
                bundleVlc.set(extension.bundleVlc)
                linuxUbuntuRelease.set(extension.linuxUbuntuRelease)
            }

        // Expose the download location so the user can wire it into their own Compose config
        extension.vlcNativesDirectory.set(downloadTask.flatMap { it.vlcNativesDirectory })

        project.afterEvaluate {
            val isMultiplatform = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
            val isJvm = plugins.hasPlugin("org.jetbrains.kotlin.jvm")

            if (isMultiplatform) {
                val kmpExtension = extensions.getByType(KotlinMultiplatformExtension::class.java)
                kmpExtension.sourceSets.findByName("jvmMain")?.kotlin?.srcDir(generateLoaderTask)
            } else if (isJvm) {
                val kotlinExtension = extensions.getByType(KotlinProjectExtension::class.java)
                kotlinExtension.sourceSets.findByName("main")?.kotlin?.srcDir(generateLoaderTask)
            }
        }
    }
}

// --------------------------------------------------------------------
// TASK: Download & extract VLC natives for Windows, macOS and Linux
// --------------------------------------------------------------------
@DisableCachingByDefault(because = "Downloads from the network")
abstract class DownloadVlcNativesTask : DefaultTask() {
    private companion object {
        /** vlc-3.0.23-win64.zip, from VideoLAN's .sha256 file and checked against the file itself (2026-11-23). */
        const val VLC_WIN64_SHA256 = "992d19dbd0b8a7cde9167d2f7780b1ef6f92acc8a71acfa736101a21f35181e1"
    }

    @get:OutputDirectory
    abstract val vlcNativesDirectory: DirectoryProperty

    @get:Input
    abstract val bundleVlc: Property<Boolean>

    @get:Input
    abstract val linuxUbuntuRelease: Property<String>

    init {
        group = "dependencies"
        description = "Downloads and extracts lightweight VLC audio binaries for the current OS."
    }

    @TaskAction
    fun run() {
        if (!bundleVlc.get()) {
            // Drop anything an earlier bundling run left behind so it can't end up in the package
            vlcNativesDirectory.get().asFile.apply { deleteRecursively(); mkdirs() }
            println("VLC bundling is disabled (vlcjBundler.bundleVlc = false). The system VLC will be used.")
            return
        }

        val vlcVersion = "3.0.23"
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val (osDir, downloadUrl) = when {
            os.contains("win") -> {
                if (arch.contains("aarch64") || arch.contains("arm64")) throw GradleException("blossom bundles VLC for x64 Windows only")
                "windows-x64" to "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
            }

            os.contains("linux") -> {
                val isArm = arch.contains("aarch64") || arch.contains("arm64")
                val osDir = if (isArm) "linux-arm64" else "linux-x64"
                val targetDir = vlcNativesDirectory.dir(osDir).get().asFile
                if (File(targetDir, ".complete").exists()) {
                    println("VLC binaries already present in build folder cache. Skipping download.")
                } else {
                    LinuxVlcBundler.bundle(targetDir, isArm, linuxUbuntuRelease.get(), File(temporaryDir, "work"))
                }
                return
            }

            else -> "unsupported" to ""
        }

        if (downloadUrl.isEmpty()) {
            logger.warn("VLC native auto-download is not configured for this operating system.")
            return
        }

        val targetDir = vlcNativesDirectory.dir(osDir).get().asFile
        if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            println("VLC binaries already present in build folder cache. Skipping download.")
            return
        }

        println("Downloading stripped VLC assets to build sandbox target: $targetDir")
        targetDir.mkdirs()

        downloadAndStripWindows(downloadUrl, targetDir)
    }

    private fun downloadAndStripWindows(urlStr: String, targetDir: File) {
        val allowedFiles = setOf(
            "libvlc.dll",
            "libvlccore.dll",

            // access
            "plugins/access/libfilesystem_plugin.dll",
            "plugins/access/libhttp_plugin.dll",
            "plugins/access/libhttps_plugin.dll",

            // audio_filter
            "plugins/audio_filter/libaudio_format_plugin.dll",
            "plugins/audio_filter/libmad_plugin.dll",
            "plugins/audio_filter/libsamplerate_plugin.dll",
            "plugins/audio_filter/libsimple_channel_mixer_plugin.dll",
            "plugins/audio_filter/libspeex_resampler_plugin.dll",
            "plugins/audio_filter/libtrivial_channel_mixer_plugin.dll",

            // audio_mixer
            "plugins/audio_mixer/libfloat_mixer_plugin.dll",
            "plugins/audio_mixer/libinteger_mixer_plugin.dll",

            // audio_output
            "plugins/audio_output/libdirectsound_plugin.dll",
            "plugins/audio_output/libmmdevice_plugin.dll",
            "plugins/audio_output/libwasapi_plugin.dll",
            "plugins/audio_output/libwaveout_plugin.dll",

            // codec
            "plugins/codec/libadpcm_plugin.dll",
            "plugins/codec/libaes3_plugin.dll",
            "plugins/codec/libaraw_plugin.dll",
            "plugins/codec/libfaad_plugin.dll",
            "plugins/codec/libflac_plugin.dll",
            "plugins/codec/libg711_plugin.dll",
            "plugins/codec/liblpcm_plugin.dll",
            "plugins/codec/libmpg123_plugin.dll",
            "plugins/codec/libopus_plugin.dll",
            "plugins/codec/libspeex_plugin.dll",
            "plugins/codec/libvorbis_plugin.dll",

            // demux
            "plugins/demux/libaiff_plugin.dll",
            "plugins/demux/libau_plugin.dll",
            "plugins/demux/libcaf_plugin.dll",
            "plugins/demux/libflacsys_plugin.dll",
            "plugins/demux/libmkv_plugin.dll", // Matroska/WebM, the container of YouTube's opus streams
            "plugins/demux/libmp4_plugin.dll",
            "plugins/demux/libmpc_plugin.dll",
            "plugins/demux/libogg_plugin.dll",
            "plugins/demux/libplaylist_plugin.dll",
            "plugins/demux/librawaud_plugin.dll",
            "plugins/demux/libvoc_plugin.dll",
            "plugins/demux/libwav_plugin.dll",

            // misc
            "plugins/misc/libgnutls_plugin.dll",
            "plugins/misc/libxml_plugin.dll",

            // packetizer
            "plugins/packetizer/libpacketizer_copy_plugin.dll",
            "plugins/packetizer/libpacketizer_flac_plugin.dll",
            "plugins/packetizer/libpacketizer_mlp_plugin.dll",
            "plugins/packetizer/libpacketizer_mpeg4audio_plugin.dll",
            "plugins/packetizer/libpacketizer_mpegaudio_plugin.dll",

            // stream_filter
            "plugins/stream_filter/libcache_block_plugin.dll",
            "plugins/stream_filter/libcache_read_plugin.dll",
            "plugins/stream_filter/libinflate_plugin.dll",
            "plugins/stream_filter/libprefetch_plugin.dll",

            // blossom: imem (vlcj callback media), es (plain MP3/AAC streams), skiptags (ID3v2/APE tags before the audio)
            "plugins/access/libimem_plugin.dll",
            "plugins/demux/libes_plugin.dll",
            "plugins/stream_filter/libskiptags_plugin.dll",
        )

        // blossom: download to a file and check it against VideoLAN's published SHA256 before unpacking anything.
        val archive = File(temporaryDir, "vlc-win64.zip")
        URL(urlStr).openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        archive.inputStream().use { input -> val buf = ByteArray(1 shl 16); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        if (md.digest().joinToString("") { "%02x".format(it) } != VLC_WIN64_SHA256) {
            archive.delete()
            throw GradleException("$urlStr: SHA256 mismatch, refusing to bundle it")
        }
        archive.inputStream().use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val relativePath = name.substringAfter("/")
                    if (relativePath.isEmpty()) {
                        entry = zip.nextEntry
                        continue
                    }
                    if (relativePath in allowedFiles) {
                        val destFile = File(targetDir, relativePath)
                        if (entry.isDirectory) {
                            destFile.mkdirs()
                        } else {
                            destFile.parentFile.mkdirs()
                            FileOutputStream(destFile).use { out -> zip.copyTo(out) }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }
        archive.delete()
        println("Windows streaming audio binaries successfully filtered and unpacked.")
    }

}