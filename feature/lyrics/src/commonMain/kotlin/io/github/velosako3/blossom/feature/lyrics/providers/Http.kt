// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.providers

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The shared HTTP client the ported lyrics providers use (BitChord's `Http.client`). */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        // Close to BitChord's own client (20 s connect, 30 s read): a phone on mobile data needs the time, and the
        // lookup takes answers in priority order, so a slow source costs only itself. The call cap stops a dead host
        // from holding the whole lookup.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
}
