package com.naos.pikminbee.ui

/**
 * Keep the map's coordinate display non-editable until the user explicitly
 * chooses to edit it in Teleport mode. Background map/location updates never
 * open the keyboard or request input focus.
 */
internal data class CoordinateInputEditorState(val editing: Boolean = false) {
    fun begin(teleportMode: Boolean, blocked: Boolean): CoordinateInputEditorState =
        if (teleportMode && !blocked) copy(editing = true) else dismiss()

    fun dismiss(): CoordinateInputEditorState = if (editing) copy(editing = false) else this

    fun showField(teleportMode: Boolean, blocked: Boolean): Boolean =
        editing && teleportMode && !blocked
}
