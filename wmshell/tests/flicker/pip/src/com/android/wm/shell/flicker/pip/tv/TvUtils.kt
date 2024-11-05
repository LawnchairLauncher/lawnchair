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

import android.view.KeyEvent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.android.wm.shell.flicker.utils.SYSTEM_UI_PACKAGE_NAME

/** Id of the root view in the com.android.wm.shell.pip.tv.PipMenuActivity */
private const val TV_PIP_MENU_ROOT_ID = "tv_pip_menu"
private const val TV_PIP_MENU_BUTTONS_CONTAINER_ID = "tv_pip_menu_action_buttons"
private const val TV_PIP_MENU_CLOSE_BUTTON_ID = "tv_pip_menu_close_button"
private const val TV_PIP_MENU_FULLSCREEN_BUTTON_ID = "tv_pip_menu_fullscreen_button"

private const val FOCUS_ATTEMPTS = 10
private const val WAIT_TIME_MS = 3_000L

private val TV_PIP_MENU_SELECTOR = By.res(SYSTEM_UI_PACKAGE_NAME, TV_PIP_MENU_ROOT_ID)
private val TV_PIP_MENU_BUTTONS_CONTAINER_SELECTOR =
    By.res(SYSTEM_UI_PACKAGE_NAME, TV_PIP_MENU_BUTTONS_CONTAINER_ID)
private val TV_PIP_MENU_CLOSE_BUTTON_SELECTOR =
    By.res(SYSTEM_UI_PACKAGE_NAME, TV_PIP_MENU_CLOSE_BUTTON_ID)
private val TV_PIP_MENU_FULLSCREEN_BUTTON_SELECTOR =
    By.res(SYSTEM_UI_PACKAGE_NAME, TV_PIP_MENU_FULLSCREEN_BUTTON_ID)

fun UiDevice.waitForTvPipMenu(): UiObject2? =
    wait(Until.findObject(TV_PIP_MENU_SELECTOR), WAIT_TIME_MS)

fun UiDevice.waitForTvPipMenuToClose(): Boolean =
    wait(Until.gone(TV_PIP_MENU_SELECTOR), WAIT_TIME_MS)

fun UiDevice.findTvPipMenuControls(): UiObject2? =
    findTvPipMenuElement(TV_PIP_MENU_BUTTONS_CONTAINER_SELECTOR)

fun UiDevice.findTvPipMenuCloseButton(): UiObject2? =
    findTvPipMenuElement(TV_PIP_MENU_CLOSE_BUTTON_SELECTOR)

fun UiDevice.findTvPipMenuFullscreenButton(): UiObject2? =
    findTvPipMenuElement(TV_PIP_MENU_FULLSCREEN_BUTTON_SELECTOR)

fun UiDevice.findTvPipMenuElementWithDescription(desc: String): UiObject2? =
    findTvPipMenuElement(By.desc(desc))

private fun UiDevice.findTvPipMenuElement(selector: BySelector): UiObject2? =
    findObject(TV_PIP_MENU_SELECTOR)?.findObject(selector)

fun UiDevice.waitForTvPipMenuElementWithDescription(desc: String): UiObject2? {
    // Ideally, we'd want to wait for an element with the given description that has the Pip Menu as
    // its parent, but the API does not allow us to construct a query exactly that way.
    // So instead we'll wait for a Pip Menu that has the element, which we are looking for, as a
    // descendant and then retrieve the element from the menu and return to the caller of this
    // method.
    val elementSelector = By.desc(desc)
    val menuContainingElementSelector = By.copy(TV_PIP_MENU_SELECTOR).hasDescendant(elementSelector)

    return wait(Until.findObject(menuContainingElementSelector), WAIT_TIME_MS)
        ?.findObject(elementSelector)
}

fun UiDevice.waitUntilTvPipMenuElementWithDescriptionIsGone(desc: String): Boolean? {
    val elementSelector = By.desc(desc)
    val menuContainingElementSelector = By.copy(TV_PIP_MENU_SELECTOR).hasDescendant(elementSelector)

    return wait(Until.gone(menuContainingElementSelector), WAIT_TIME_MS)
}

fun UiDevice.clickTvPipMenuCloseButton() {
    focusOnAndClickTvPipMenuElement(TV_PIP_MENU_CLOSE_BUTTON_SELECTOR) ||
        error("Could not focus on the Close button")
}

fun UiDevice.clickTvPipMenuFullscreenButton() {
    focusOnAndClickTvPipMenuElement(TV_PIP_MENU_FULLSCREEN_BUTTON_SELECTOR) ||
        error("Could not focus on the Fullscreen button")
}

fun UiDevice.clickTvPipMenuElementWithDescription(desc: String) {
    focusOnAndClickTvPipMenuElement(By.desc(desc).pkg(SYSTEM_UI_PACKAGE_NAME)) ||
        error("Could not focus on the Pip menu object with \"$desc\" description")
    // So apparently Accessibility framework on TV is not very reliable and sometimes the state of
    // the tree of accessibility nodes as seen by the accessibility clients kind of lags behind of
    // the "real" state of the "UI tree". It seems, however, that moving focus around the tree
    // forces the AccessibilityNodeInfo tree to get properly updated.
    // So since we suspect that clicking on a Pip Menu element may cause some UI changes and we want
    // those changes to be seen by the UiAutomator, which is using Accessibility framework under the
    // hood for inspecting UI, we'll move the focus around a little.
    moveFocus()
}

private fun UiDevice.focusOnAndClickTvPipMenuElement(selector: BySelector): Boolean {
    repeat(FOCUS_ATTEMPTS) {
        val element =
            findTvPipMenuElement(selector)
                ?: error("The Pip Menu element we try to focus on is gone.")

        if (element.isFocusedOrHasFocusedChild) {
            pressDPadCenter()
            return true
        }

        findTvPipMenuElement(By.focused(true))?.let { focused ->
            if (element.visibleCenter.x < focused.visibleCenter.x) pressDPadLeft()
            else pressDPadRight()
            waitForIdle()
        }
            ?: error("Pip menu does not contain a focused element")
    }

    return false
}

fun UiDevice.closeTvPipWindow() {
    // Check if Pip menu is Open. If it's not, open it.
    if (findObject(TV_PIP_MENU_SELECTOR) == null) {
        pressWindowKey()
        waitForTvPipMenu() ?: error("Could not open Pip menu")
    }

    clickTvPipMenuCloseButton()
    waitForTvPipMenuToClose()
}

/**
 * Simply presses the D-Pad Left and Right buttons once, which should move the focus on the screen,
 * which should cause Accessibility events to be fired, which should, hopefully, properly update
 * AccessibilityNodeInfo tree dispatched by the platform to the Accessibility services, one of which
 * is the UiAutomator.
 */
private fun UiDevice.moveFocus() {
    waitForIdle()
    pressDPadLeft()
    waitForIdle()
    pressDPadRight()
    waitForIdle()
}

fun UiDevice.pressWindowKey() = pressKeyCode(KeyEvent.KEYCODE_WINDOW)

fun UiObject2.isFullscreen(uiDevice: UiDevice): Boolean =
    visibleBounds.run { height() == uiDevice.displayHeight && width() == uiDevice.displayWidth }

val UiObject2.isFocusedOrHasFocusedChild: Boolean
    get() = isFocused || findObject(By.focused(true)) != null
