package com.nefarioussecurity.sentinel.controller.ui

/**
 * Controller routes are local UI state, not Android Activities.
 *
 * System Back must resolve to the same destination as the visible
 * top-left Back arrow on the currently displayed child screen.
 */
internal enum class ControllerScreen {
    DASHBOARD,
    BETA_LIFECYCLE,
    WIPE_BEHAVIOUR,
    TAMPER,
    USB,
    DEADMAN,
    MOTION,
    SMS,
    ABOUT,
    LICENSE,
    REMOVE,
}

/**
 * Null means system Back is not intercepted (the dashboard).
 * LICENSE is nested under ABOUT; all other child pages have DASHBOARD
 * as their parent.
 */
internal fun ControllerScreen.systemBackDestination():
    ControllerScreen? =
    when (this) {
        ControllerScreen.DASHBOARD ->
            null

        ControllerScreen.LICENSE ->
            ControllerScreen.ABOUT

        else ->
            ControllerScreen.DASHBOARD
    }
