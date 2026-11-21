package com.naos.pikminbee.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateInputEditorStateTest {
    @Test fun initialMapDisplayNeverCreatesEditableInput() {
        val initial = CoordinateInputEditorState()
        assertFalse(initial.showField(teleportMode = true, blocked = false))
        assertFalse(initial.showField(teleportMode = false, blocked = false))
    }

    @Test fun onlyExplicitTeleportEditMayOpenField() {
        val initial = CoordinateInputEditorState()
        assertFalse(initial.begin(teleportMode = false, blocked = false).editing)
        assertFalse(initial.begin(teleportMode = true, blocked = true).editing)
        assertTrue(initial.begin(teleportMode = true, blocked = false)
            .showField(teleportMode = true, blocked = false))
    }

    @Test fun switchingModesOrOpeningSheetDisablesEditing() {
        val editing = CoordinateInputEditorState().begin(true, false)
        assertFalse(editing.showField(teleportMode = false, blocked = false))
        assertFalse(editing.showField(teleportMode = true, blocked = true))
    }

    @Test fun mapPickCanDismissEditorWithoutChangingCoordinateText() {
        val editing = CoordinateInputEditorState().begin(true, false)
        assertFalse(editing.dismiss().showField(true, false))
        assertFalse(editing.dismiss().dismiss().editing)
    }
}
