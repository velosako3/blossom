// SPDX-License-Identifier: AGPL-3.0-or-later
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Adds what vlcj-bundler leaves out but blossom needs: on Linux libebml/libmatroska for the mkv (WebM) demuxer, and `imem` (imem-access.c), the byte-stream access module behind vlcj's
 * callback media (blossom feeds every remote and encrypted stream to libVLC through it).
 * Linux: taken from the same Ubuntu archive/suite as the bundled libVLC. Windows: from the same VLC release zip.
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
            os.contains("win") -> windows(File(root, "windows-x64"))
            else -> logger.warn("No extra VLC plugins for $os")
        }
    }

    private fun linux(target: File) {
        val plugin = File(target, "plugins/access/libimem_plugin.so")
        val libs = listOf("libebml.so.5", "libmatroska.so.7")
        if (!target.isDirectory || (plugin.isFile && libs.all { File(target, it).isFile })) return
        val mirror = "http://archive.ubuntu.com/ubuntu"
        val wanted = setOf("vlc-plugin-base", "libebml5", "libmatroska7")
        val index = HashMap<String, String>()
        for (suite in listOf("noble-updates", "noble")) for (component in listOf("main", "universe")) {
            var name: String? = null
            GZIPInputStream(URI("$mirror/dists/$suite/$component/binary-amd64/Packages.gz").toURL().openStream()).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("Package: ")) name = line.removePrefix("Package: ")
                    if (line.startsWith("Filename: ") && name in wanted) index.putIfAbsent(name!!, line.removePrefix("Filename: "))
                }
            }
        }
        val work = File(temporaryDir, "debs").apply { deleteRecursively(); mkdirs() }
        for (pkg in wanted) {
            val filename = index[pkg] ?: throw GradleException("$pkg not found in the noble index")
            val dir = File(work, pkg).apply { mkdirs() }
            val deb = File(dir, "p.deb")
            URI("$mirror/$filename").toURL().openStream().use { input -> deb.outputStream().use { input.copyTo(it) } }
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
        // libebml/libmatroska: needed by the bundled mkv demuxer (YouTube's Opus streams are WebM).
        for (lib in libs) {
            val file = work.walkTopDown().first { it.name == lib }
            val real = file.canonicalFile
            val dest = File(target, lib)
            real.copyTo(dest, overwrite = true)
            exec(target, "patchelf", "--set-rpath", "\$ORIGIN", dest.absolutePath)
        }
    }

    private fun windows(target: File) {
        val wanted = setOf("libimem_plugin.dll")
        if (!target.isDirectory || wanted.all { File(target, "plugins/access/$it").isFile }) return
        var added = 0
        val url = "https://download.videolan.org/pub/videolan/vlc/3.0.23/win64/vlc-3.0.23-win64.zip"
        ZipInputStream(URI(url).toURL().openStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name.substringAfterLast('/')
                if (e.name.contains("plugins/access/") && name in wanted) {
                    val dest = File(target, "plugins/access/$name").apply { parentFile.mkdirs() }
                    FileOutputStream(dest).use { zip.copyTo(it) }
                    if (++added == wanted.size) { logger.lifecycle("Added ${wanted.joinToString()}"); return }
                }
                e = zip.nextEntry
            }
        }
        throw GradleException("imem plugins not found in $url")
    }

    private fun exec(dir: File, vararg command: String) {
        val p = ProcessBuilder(*command).directory(dir).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) throw GradleException("`${command.joinToString(" ")}` failed:\n$out")
    }
}
