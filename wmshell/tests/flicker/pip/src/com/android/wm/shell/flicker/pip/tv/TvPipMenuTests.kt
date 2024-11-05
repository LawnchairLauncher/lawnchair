/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.flicker.pip.tv

import android.graphics.Rect
import androidx.test.filters.RequiresDevice
import androidx.test.uiautomator.UiObject2
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.wm.shell.flicker.utils.SYSTEM_UI_PACKAGE_NAME
import com.android.wm.shell.flicker.utils.wait
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Test Pip Menu on TV. To run this test: `atest WMShellFlickerTests:TvPipMenuTests` */
@RequiresDevice
class TvPipMenuTests : TvPipTestBase() {

    private val systemUiResources by lazy {
        packageManager.getResourcesForApplication(SYSTEM_UI_PACKAGE_NAME)
    }
    private val pipBoundsWhileInMenu: Rect by lazy {
        systemUiResources.run {
            val bounds =
                getString(getIdentifier("pip_menu_bounds", "string", SYSTEM_UI_PACKAGE_NAME))
            Rect.unflattenFromString(bounds) ?: error("Could not retrieve PiP menu bounds")
        }
    }
    private val playButtonDescription by lazy {
        systemUiResources.run {
            getString(getIdentifier("pip_play", "string", SYSTEM_UI_PACKAGE_NAME))
        }
    }
    private val pauseButtonDescription by lazy {
        systemUiResources.run {
            getString(getIdentifier("pip_pause", "string", SYSTEM_UI_PACKAGE_NAME))
        }
    }

    @Before
    fun tvPipMenuTestsTestUp() {
        // Launch the app and go to PiP
        testApp.launchViaIntent()
    }

    @Test
    fun pipMenu_correctPosition() {
        enterPip_openMenu_assertShown()

        // Make sure the PiP task is positioned where it should be.
        val activityBounds: Rect =
            testApp.ui?.visibleBounds ?: error("Could not retrieve Pip Activity bounds")
        assertTrue(
            "Pip Activity is positioned correctly while Pip menu is shown",
            pipBoundsWhileInMenu == activityBounds
        )

        // Make sure the Pip Menu Actions are positioned correctly.
        uiDevice.findTvPipMenuControls()?.visibleBounds?.run {
            assertTrue(
                "Pip Menu Actions should be positioned below the Activity in Pip",
                top >= activityBounds.bottom
            )
            assertTrue(
                "Pip Menu Actions should be positioned central horizontally",
                centerX() == uiDevice.displayWidth / 2
            )
            assertTrue(
                "Pip Menu Actions should be fully shown on the screen",
                left >= 0 && right <= uiDevice.displayWidth && bottom <= uiDevice.displayHeight
            )
        }
            ?: error("Could not retrieve Pip Menu Actions bounds")

        testApp.closePipWindow()
    }

    @Test
    fun pipMenu_backButton() {
        enterPip_openMenu_assertShown()

        // Pressing the Back key should close the Pip menu
        uiDevice.pressBack()
        assertTrue("Pip menu should have closed", uiDevice.waitForTvPipMenuToClose())

        testApp.closePipWindow()
    }

    @Test
    fun pipMenu_homeButton() {
        enterPip_openMenu_assertShown()

        // Pressing the Home key should close the Pip menu
        uiDevice.pressHome()
        assertTrue("Pip menu should have closed", uiDevice.waitForTvPipMenuToClose())

        testApp.closePipWindow()
    }

    @Test
    fun pipMenu_closeButton() {
        enterPip_openMenu_assertShown()

        // PiP menu should contain the Close button
        uiDevice.findTvPipMenuCloseButton()
            ?: fail("\"Close PIP\" button should be shown in Pip menu")

        // Clicking on the Close button should close the app
        uiDevice.clickTvPipMenuCloseButton()
        assertTrue("\"Close PIP\" button should close the PiP", testApp.waitUntilClosed())
    }

    @Test
    fun pipMenu_fullscreenButton() {
        enterPip_openMenu_assertShown()

        // PiP menu should contain the Fullscreen button
        uiDevice.findTvPipMenuFullscreenButton()
            ?: fail("\"Full screen\" button should be shown in Pip menu")

        // Clicking on the fullscreen button should return app to the fullscreen mode.
        // Click, wait for the app to go fullscreen
        uiDevice.clickTvPipMenuFullscreenButton()
        assertTrue(
            "\"Full screen\" button should open the app fullscreen",
            wait { testApp.ui?.isFullscreen(uiDevice) ?: false }
        )

        // Close the app
        uiDevice.pressBack()
        testApp.waitUntilClosed()
    }

    @Test
    fun pipMenu_mediaPlayPauseButtons() {
        // Start media session before entering PiP
        testApp.clickStartMediaSessionButton()

        enterPip_openMenu_assertShown()
        assertFullscreenAndCloseButtonsAreShown()

        // PiP menu should contain the Pause button
        uiDevice.findTvPipMenuElementWithDescription(pauseButtonDescription)
            ?: fail(
                "\"Pause\" button should be shown in Pip menu if there is an active " +
                    "playing media session."
            )

        // When we pause media, the button should change from Pause to Play
        uiDevice.clickTvPipMenuElementWithDescription(pauseButtonDescription)

        assertFullscreenAndCloseButtonsAreShown()
        // PiP menu should contain the Play button now
        uiDevice.waitForTvPipMenuElementWithDescription(playButtonDescription)
            ?: fail(
                "\"Play\" button should be shown in Pip menu if there is an active " +
                    "paused media session."
            )

        testApp.closePipWindow()
    }

    @Test
    fun pipMenu_withCustomActions() {
        // Enter PiP with custom actions.
        testApp.checkWithCustomActionsCheckbox()
        enterPip_openMenu_assertShown()

        // PiP menu should contain "No-Op", "Off" and "Clear" buttons...
        uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_NO_OP)
            ?: fail("\"No-Op\" button should be shown in Pip menu")
        uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_OFF)
            ?: fail("\"Off\" button should be shown in Pip menu")
        uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_CLEAR)
            ?: fail("\"Clear\" button should be shown in Pip menu")
        // ... and should also contain the "Full screen" and "Close" buttons.
        assertFullscreenAndCloseButtonsAreShown()

        uiDevice.clickTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_OFF)
        // Invoking the "Off" action should replace it with the "On" action/button and should
        // remove the "No-Op" action/button. "Clear" action/button should remain in the menu ...
        uiDevice.waitForTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_ON)
            ?: fail("\"On\" button should be shown in Pip for a corresponding custom action")
        assertNull(
            "\"No-Op\" button should not be shown in Pip menu",
            uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_NO_OP)
        )
        uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_CLEAR)
            ?: fail("\"Clear\" button should be shown in Pip menu")
        // ... as well as the "Full screen" and "Close" buttons.
        assertFullscreenAndCloseButtonsAreShown()

        uiDevice.clickTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_CLEAR)
        // Invoking the "Clear" action should remove all the custom actions and their corresponding
        // buttons, ...
        uiDevice
            .waitUntilTvPipMenuElementWithDescriptionIsGone(ActivityOptions.Pip.MENU_ACTION_ON)
            ?.also { isGone -> if (!isGone) fail("\"On\" button should not be shown in Pip menu") }
        assertNull(
            "\"Off\" button should not be shown in Pip menu",
            uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_OFF)
        )
        assertNull(
            "\"Clear\" button should not be shown in Pip menu",
            uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_CLEAR)
        )
        assertNull(
            "\"No-Op\" button should not be shown in Pip menu",
            uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_NO_OP)
        )
        // ... but the menu should still contain the "Full screen" and "Close" buttons.
        assertFullscreenAndCloseButtonsAreShown()

        testApp.closePipWindow()
    }

    @Test
    fun pipMenu_customActions_override_mediaControls() {
        // Start media session before entering PiP with custom actions.
        testApp.checkWithCustomActionsCheckbox()
        testApp.clickStartMediaSessionButton()
        enterPip_openMenu_assertShown()

        // PiP menu should contain "No-Op", "Off" and "Clear" buttons for the custom actions...
        uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_NO_OP)
            ?: fail("\"No-Op\" button should be shown in Pip menu")
        uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_OFF)
            ?: fail("\"Off\" button should be shown in Pip menu")
        uiDevice.findTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_CLEAR)
            ?: fail("\"Clear\" button should be shown in Pip menu")
        // ... should also contain the "Full screen" and "Close" buttons, ...
        assertFullscreenAndCloseButtonsAreShown()
        // ... but should not contain media buttons.
        assertNull(
            "\"Play\" button should not be shown in menu when there are custom actions",
            uiDevice.findTvPipMenuElementWithDescription(playButtonDescription)
        )
        assertNull(
            "\"Pause\" button should not be shown in menu when there are custom actions",
            uiDevice.findTvPipMenuElementWithDescription(pauseButtonDescription)
        )

        uiDevice.clickTvPipMenuElementWithDescription(ActivityOptions.Pip.MENU_ACTION_CLEAR)
        // Invoking the "Clear" action should remove all the custom actions, which should bring up
        // media buttons...
        uiDevice.waitForTvPipMenuElementWithDescription(pauseButtonDescription)
            ?: fail(
                "\"Pause\" button should be shown in Pip menu if there is an active " +
                    "playing media session."
            )
        // ... while the "Full screen" and "Close" buttons should remain in the menu.
        assertFullscreenAndCloseButtonsAreShown()

        testApp.closePipWindow()
    }

    private fun enterPip_openMenu_assertShown(): UiObject2 {
        testApp.clickEnterPipButton(wmHelper)
        // Pressing the Window key should bring up Pip menu
        uiDevice.pressWindowKey()
        return uiDevice.waitForTvPipMenu() ?: fail("Pip menu should have been shown")
    }

    private fun assertFullscreenAndCloseButtonsAreShown() {
        uiDevice.findTvPipMenuCloseButton()
            ?: fail("\"Close PIP\" button should be shown in Pip menu")
        uiDevice.findTvPipMenuFullscreenButton()
            ?: fail("\"Full screen\" button should be shown in Pip menu")
    }
}
