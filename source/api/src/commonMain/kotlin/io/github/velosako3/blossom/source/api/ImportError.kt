// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.api

/** Why an import can't read its source; the UI turns each into a message or an action. */
sealed class ImportError(message: String) : Exception(message) {
    /** Private (or the account's own) content: offer to sign in to [service]. */
    class NeedsLogin(val service: String) : ImportError("$service sign-in needed")
    class NotFound(url: String) : ImportError("Nothing found at $url")
}
