// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import java.io.File

/** Empties the test data folder, but only one inside this build's output, never a real library. */
fun resetTestDataDir() {
    val dir = System.getProperty("blossom.dataDir")?.let { File(it).canonicalFile } ?: return
    val build = File("build").canonicalFile
    require(dir.path.startsWith(build.path + File.separator)) { "refusing to delete $dir: not under $build" }
    dir.deleteRecursively()
}
