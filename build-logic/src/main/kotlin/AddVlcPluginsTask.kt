// SPDX-License-Identifier: AGPL-3.0-or-later
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URI
import java.util.zip.GZIPInputStream

/**
 * Adds what vlcj-bundler leaves out but blossom needs: on Linux libebml/libmatroska for the mkv (WebM) demuxer, and `imem` (imem-access.c), the byte-stream access module behind vlcj's
 * callback media (blossom feeds every remote and encrypted stream to libVLC through it).
 * Linux: taken from the same Ubuntu archive/suite as the bundled libVLC. (On Windows the vendored bundler unpacks them from the VLC zip.)
 */
@DisableCachingByDefault(because = "Downloads from the network")
abstract class AddVlcPluginsTask : DefaultTask() {
    @get:Internal
    abstract val nativesDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val os = System.getProperty("os.name").lowercase()
        val root = nativesDirectory.get().asFile
        when {
            os.contains("linux") -> linux(File(root, "linux-x64"))
            else -> logger.warn("No extra VLC plugins for $os")
        }
    }

    private fun linux(target: File) {
        val plugin = File(target, "plugins/access/libimem_plugin.so")
        // es: the demuxer for plain MP3/AAC/AC-3 streams (untitled, SoundCloud progressive, local .mp3 files).
        val es = File(target, "plugins/demux/libes_plugin.so")
        // skiptags: skips ID3v2/APE tags in front of the audio, which es can't probe past (most MP3s have them).
        val skiptags = File(target, "plugins/stream_filter/libskiptags_plugin.so")
        val libs = listOf("libebml.so.5", "libmatroska.so.7")
        if (!target.isDirectory || (plugin.isFile && es.isFile && skiptags.isFile && libs.all { File(target, it).isFile })) return
        val mirror = "https://archive.ubuntu.com/ubuntu"
        val wanted = setOf("vlc-plugin-base", "libebml5", "libmatroska7")
        // Package -> (pool file, SHA256), from the index over HTTPS; every download is checked against it.
        val index = HashMap<String, Pair<String, String>>()
        for (suite in listOf("noble-updates", "noble")) for (component in listOf("main", "universe")) {
            var name: String? = null
            var file: String? = null
            GZIPInputStream(URI("$mirror/dists/$suite/$component/binary-amd64/Packages.gz").toURL().openStream()).bufferedReader().useLines { lines ->
                for (line in lines) {
                    when {
                        line.startsWith("Package: ") -> { name = line.removePrefix("Package: "); file = null }
                        line.startsWith("Filename: ") -> file = line.removePrefix("Filename: ")
                        line.startsWith("SHA256: ") && name in wanted && file != null -> index.putIfAbsent(name!!, file!! to line.removePrefix("SHA256: ").trim())
                    }
                }
            }
        }
        val work = File(temporaryDir, "debs").apply { deleteRecursively(); mkdirs() }
        for (pkg in wanted) {
            val (filename, sha256) = index[pkg] ?: throw GradleException("$pkg not found in the noble index")
            val dir = File(work, pkg).apply { mkdirs() }
            val deb = File(dir, "p.deb")
            URI("$mirror/$filename").toURL().openStream().use { input -> deb.outputStream().use { input.copyTo(it) } }
            if (sha256(deb) != sha256.lowercase()) throw GradleException("$filename: SHA256 mismatch, refusing to bundle it")
            exec(dir, "ar", "x", deb.absolutePath)
            val data = dir.listFiles { f -> f.name.startsWith("data.tar") }!!.single()
            exec(dir, "tar", "-xf", data.absolutePath, "-C", dir.absolutePath)
            logger.lifecycle("Fetched $filename")
        }
        // imem (imem-access.c): the byte-stream access behind vlcj callback media.
        val src = work.walkTopDown().first { it.name == "libimem_plugin.so" && it.path.contains("plugins/access") }
        plugin.parentFile.mkdirs()
        src.copyTo(plugin, overwrite = true)
        exec(plugin.parentFile, "patchelf", "--set-rpath", "\$ORIGIN/../..", plugin.absolutePath)
        for ((dest, dir) in listOf(es to "plugins/demux", skiptags to "plugins/stream_filter")) {
            val src = work.walkTopDown().first { it.name == dest.name && it.path.contains(dir) }
            dest.parentFile.mkdirs()
            src.copyTo(dest, overwrite = true)
            exec(dest.parentFile, "patchelf", "--set-rpath", "\$ORIGIN/../..", dest.absolutePath)
        }
        // libebml/libmatroska: needed by the bundled mkv demuxer (YouTube's Opus streams are WebM).
        for (lib in libs) {
            val file = work.walkTopDown().first { it.name == lib }
            val real = file.canonicalFile
            val dest = File(target, lib)
            real.copyTo(dest, overwrite = true)
            exec(target, "patchelf", "--set-rpath", "\$ORIGIN", dest.absolutePath)
        }
    }

    private fun exec(dir: File, vararg command: String) {
        val p = ProcessBuilder(*command).directory(dir).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) throw GradleException("`${command.joinToString(" ")}` failed:\n$out")
    }

    private fun sha256(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(1 shl 16); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
