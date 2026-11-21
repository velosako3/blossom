// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.providers

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The shared HTTP client the ported lyrics providers use (BitChord's `Http.client`). */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
}
