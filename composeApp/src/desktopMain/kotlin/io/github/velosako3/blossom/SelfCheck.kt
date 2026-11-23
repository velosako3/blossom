// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.net.ssl.SSLContext
import kotlin.system.exitProcess

/**
 * `blossom --self-check`: starts the app graph in a temporary data folder and touches the JDK features the bundled
 * runtime must include (SQLite over JDBC, EC crypto for LAN pairing and HTTPS, AES-GCM), then exits without a window.
 * Installer builds run it so a runtime missing a module fails CI instead of crashing on the user's first launch.
 */
fun selfCheck(): Nothing {
    System.setProperty("blossom.dataDir", Files.createTempDirectory("blossom-self-check").toString())
    val result = runCatching {
        createDesktopGraph()
        val ec = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val a = ec.generateKeyPair()
        val b = ec.generateKeyPair()
        KeyAgreement.getInstance("ECDH").apply { init(a.private); doPhase(b.public, true) }.generateSecret()
        Cipher.getInstance("AES/GCM/NoPadding")
        val suites = SSLContext.getDefault().createSSLEngine().supportedCipherSuites
        check(suites.any { "ECDHE" in it }) { "no ECDHE cipher suites" }
    }
    result.exceptionOrNull()?.printStackTrace()
    println(if (result.isSuccess) "self-check ok" else "self-check FAILED")
    exitProcess(if (result.isSuccess) 0 else 1)
}
