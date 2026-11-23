// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.discord

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.UUID

/**
 * Discord's local RPC: a Unix socket (`discord-ipc-N` under the runtime/temp dirs, including Flatpak and Snap
 * installs) or a Windows named pipe. Frames are opcode + length (little-endian int32) + JSON. Every failure (Discord
 * not running, a closed pipe) just disconnects; callers try again later.
 */
class DiscordIpc(
    private val applicationId: String,
    /** Where to look for `discord-ipc-N` on Linux/macOS. */
    private val searchDirs: List<File> = listOfNotNull(System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp").distinct().map(::File),
) {
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var closer: AutoCloseable? = null

    val connected get() = output != null

    fun connect(): Boolean {
        if (connected) return true
        for (n in 0..9) {
            val ok = if (System.getProperty("os.name").lowercase().contains("win")) openPipe("\\\\.\\pipe\\discord-ipc-$n") else unixPaths(n).any { openSocket(it) }
            if (ok && runCatching { handshake() }.isSuccess) return true
            close()
        }
        return false
    }

    private fun unixPaths(n: Int): List<File> {
        return searchDirs.flatMap { b -> listOf("discord-ipc-$n", "app/com.discordapp.Discord/discord-ipc-$n", "snap.discord/discord-ipc-$n").map { File(b, it) } }.filter { it.exists() }
    }

    private fun openSocket(f: File): Boolean = runCatching {
        val ch = SocketChannel.open(StandardProtocolFamily.UNIX).apply { connect(UnixDomainSocketAddress.of(f.toPath())) }
        input = Channels.newInputStream(ch); output = Channels.newOutputStream(ch); closer = ch
    }.isSuccess

    private fun openPipe(path: String): Boolean = runCatching {
        val pipe = RandomAccessFile(path, "rw")
        input = object : InputStream() {
            override fun read() = pipe.read()
            override fun read(b: ByteArray, off: Int, len: Int) = pipe.read(b, off, len)
        }
        output = object : OutputStream() {
            override fun write(b: Int) = pipe.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = pipe.write(b, off, len)
        }
        closer = pipe
    }.isSuccess

    private fun handshake() {
        send(OP_HANDSHAKE, buildJsonObject { put("v", 1); put("client_id", applicationId) })
        val (op, _) = readFrame(input!!)
        check(op == OP_FRAME) { "handshake refused" }
    }

    private fun send(op: Int, body: JsonObject) = writeFrame(output!!, op, body.toString())

    /** Shows [activity], or clears the presence when null. */
    fun setActivity(activity: JsonObject?): Boolean = runCatching {
        send(OP_FRAME, buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("args", buildJsonObject { put("pid", ProcessHandle.current().pid()); activity?.let { put("activity", it) } })
            put("nonce", UUID.randomUUID().toString())
        })
        readFrame(input!!) // the answer (or an error) keeps the pipe drained
        Unit
    }.onFailure { close() }.isSuccess

    fun close() {
        runCatching { closer?.close() }
        input = null; output = null; closer = null
    }

    companion object {
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1

        fun writeFrame(out: OutputStream, op: Int, json: String) {
            val body = json.toByteArray()
            out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).array())
            out.write(body); out.flush()
        }

        fun readFrame(input: InputStream): Pair<Int, String> {
            val head = input.readNBytes(8).also { check(it.size == 8) { "closed" } }
            val b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
            val op = b.int; val len = b.int
            check(len in 0..(1 shl 20)) { "bad frame" }
            return op to String(input.readNBytes(len))
        }
    }
}
