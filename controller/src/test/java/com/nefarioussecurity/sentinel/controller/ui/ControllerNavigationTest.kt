package com.nefarioussecurity.sentinel.controller.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerNavigationTest {

    @Test
    fun androidBackFromAllDashboardChildrenReturnsToDashboard() {
        val children =
            listOf(
                ControllerScreen.BETA_LIFECYCLE,
                ControllerScreen.WIPE_BEHAVIOUR,
                ControllerScreen.TAMPER,
                ControllerScreen.USB,
                ControllerScreen.DEADMAN,
                ControllerScreen.MOTION,
                ControllerScreen.SMS,
                ControllerScreen.ABOUT,
                ControllerScreen.REMOVE,
            )

        children.forEach { child ->
            assertEquals(
                "Back destination for $child",
                ControllerScreen.DASHBOARD,
                child.systemBackDestination(),
            )
        }
    }

    @Test
    fun androidBackFromLicenceReturnsToAbout() {
        assertEquals(
            ControllerScreen.ABOUT,
            ControllerScreen.LICENSE.systemBackDestination(),
        )
    }

    @Test
    fun androidBackOnDashboardUsesNormalAndroidBehaviour() {
        assertNull(
            ControllerScreen.DASHBOARD.systemBackDestination(),
        )
    }
}
