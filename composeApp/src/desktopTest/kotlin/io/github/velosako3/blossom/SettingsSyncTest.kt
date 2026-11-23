// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.core.model.sync.Hlc
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Allowlisted settings travel as operations: changes go out, and changes from other devices come in. */
class SettingsSyncTest {
    @Test fun aLocalChangeBecomesAnOperation(): Unit = runBlocking {
        val t = TestGraph()
        t.graph.preferences.theme.set(ThemeMode.LIGHT)
        t.graph.preferences.glass.set(io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.OFF)
        val deadline = System.currentTimeMillis() + 5_000
        while (t.graph.db.blossomQueries.settingValue("ui.theme").executeAsOneOrNull().isNullOrEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals("\"LIGHT\"", t.graph.db.blossomQueries.settingValue("ui.theme").executeAsOneOrNull())
        assertTrue(t.graph.opLog.all().none { (it.op as? Operation.SettingChanged)?.key == "ui.glass" }, "glass is device-specific")
    }

    @Test fun anIncomingChangeIsApplied(): Unit = runBlocking {
        val t = TestGraph()
        val op = OpEnvelope("remote-1", Hlc(System.currentTimeMillis() + 60_000, 0, "phone"), "phone", 1, Operation.SettingChanged(CROSSFADE_KEY, "\"6\""))
        io.github.velosako3.blossom.feature.sync.applyRemote(t.graph.db, t.graph.opLog, t.graph.projector, listOf(op))
        val deadline = System.currentTimeMillis() + 5_000
        while (t.graph.preferences.crossfade.value != 6 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(6, t.graph.preferences.crossfade.value)
        assertEquals("6", t.prefs.get(CROSSFADE_KEY))
        // Applying it didn't echo a new operation of this device's own.
        assertTrue(t.graph.opLog.all().none { it.deviceId == "test-device" && it.op is Operation.SettingChanged })
    }
}
