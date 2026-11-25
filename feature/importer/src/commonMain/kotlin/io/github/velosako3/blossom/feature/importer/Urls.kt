// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

/** The parts of a link the router needs (what java.net.URI gave, on every platform). Null when it isn't a URL. */
internal class LinkParts(val scheme: String, val host: String, val rawPath: String, val rawQuery: String?)

internal fun linkParts(url: String): LinkParts? {
    val m = URL.matchEntire(url.trim()) ?: return null
    val authority = m.groupValues[2].substringAfterLast('@')
    val host = if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')
    if (host.isEmpty() || host.any { it.isWhitespace() }) return null
    return LinkParts(m.groupValues[1], host, m.groupValues[3], m.groups[4]?.value)
}

/** `%XX` escapes and `+` as in a query component; malformed escapes throw. */
internal fun decodeQueryComponent(s: String): String {
    val bytes = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '+' -> { bytes += ' '.code.toByte(); i++ }
            c == '%' -> { require(i + 2 < s.length) { "bad escape" }; bytes += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3 }
            else -> { bytes.addAll(c.toString().encodeToByteArray().toList()); i++ }
        }
    }
    return bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
}

private val URL = Regex("""^([A-Za-z][A-Za-z0-9+.-]*)://([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#.*)?$""")
