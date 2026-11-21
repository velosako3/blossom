// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import android.app.Application

class BlossomApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createAndroidGraph(this)
    }
}
