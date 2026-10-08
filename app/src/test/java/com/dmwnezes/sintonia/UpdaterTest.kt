package com.dmwnezes.sintonia

import com.dmwnezes.sintonia.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdaterTest {
    @Test
    fun parsesTagsAndNotes() {
        assertEquals(12, Updater.numberFromTag("v1.0.12"))
        assertEquals(6, Updater.numberFromTag("v1.0.6"))
        assertEquals(0, Updater.numberFromTag("sem-numero"))
        val notes = Updater.cleanNotes("Título\n\n- item\n\nCo-Authored-By: X\nClaude-Session: y")
        assertEquals("Título\n\n- item", notes)
    }
}
