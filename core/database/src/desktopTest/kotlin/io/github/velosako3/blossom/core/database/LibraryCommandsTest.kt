// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryCommandsTest {
    @Test fun addMoveRemove() = runTest {
        val d = Device("A")
        val id = d.commands.createPlaylist("Gym")
        val ids = d.commands.addEntries(id, listOf(newEntry(1), newEntry(2), newEntry(3)))
        assertEquals(ids, d.repo.playlist(id).first()!!.entries.map { it.entryId })
        d.commands.moveEntry(id, ids[2], 0)
        assertEquals(listOf(ids[2], ids[0], ids[1]), d.repo.playlist(id).first()!!.entries.map { it.entryId })
        d.commands.removeEntry(id, ids[2])
        assertEquals(listOf(ids[0], ids[1]), d.repo.playlist(id).first()!!.entries.map { it.entryId })
    }

    @Test fun insertAtIndexKeepsOrder() = runTest {
        val d = Device("A")
        val id = d.commands.createPlaylist("P")
        val first = d.commands.addEntries(id, listOf(newEntry(1), newEntry(4)))
        val middle = d.commands.addEntries(id, listOf(newEntry(2), newEntry(3)), atIndex = 1)
        assertEquals(listOf(first[0], middle[0], middle[1], first[1]), d.repo.playlist(id).first()!!.entries.map { it.entryId })
    }

    @Test fun likeThenUnlike() = runTest {
        val d = Device("A")
        val t = sampleTrack(1)
        d.commands.like(t)
        assertTrue(d.repo.isLiked(t.id).first())
        d.commands.unlike(t.id)
        assertFalse(d.repo.isLiked(t.id).first())
    }

    @Test fun renameAndDescribe() = runTest {
        val d = Device("A")
        val id = d.commands.createPlaylist("Old")
        d.commands.renamePlaylist(id, "New")
        d.commands.setDescription(id, "desc")
        val p = d.repo.playlist(id).first()!!
        assertEquals("New", p.name); assertEquals("desc", p.description)
        d.commands.deletePlaylist(id)
        assertNull(d.repo.playlist(id).first())
    }
}
