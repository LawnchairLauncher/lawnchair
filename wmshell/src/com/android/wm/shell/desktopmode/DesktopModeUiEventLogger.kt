/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.content.pm.PackageManager
import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger
import com.android.internal.logging.UiEventLogger.UiEventEnum
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/** Log Aster UIEvents for desktop windowing mode. */
class DesktopModeUiEventLogger(
    private val uiEventLogger: UiEventLogger,
    private val packageManager: PackageManager,
) {
    private val instanceIdSequence = InstanceIdSequence(Integer.MAX_VALUE)

    /**
     * Logs an event for a CUI, on a particular package.
     *
     * @param uid The user id associated with the package the user is interacting with
     * @param packageName The name of the package the user is interacting with
     * @param event The event type to generate
     */
    fun log(uid: Int, packageName: String, event: DesktopUiEventEnum) {
        if (packageName.isEmpty() || uid < 0) {
            logD("Skip logging since package name is empty or bad uid")
            return
        }
        uiEventLogger.log(event, uid, packageName)
    }

    /** Logs an event for a CUI on a particular task. */
    fun log(taskInfo: RunningTaskInfo, event: DesktopUiEventEnum) {
        val packageName = taskInfo.baseActivity?.packageName
        if (packageName == null) {
            logD("Skip logging due to null base activity")
            return
        }
        val uid = getUid(packageName, taskInfo.userId)
        log(uid, packageName, event)
    }

    /** Retrieves a new instance id for a new interaction. */
    fun getNewInstanceId(): InstanceId = instanceIdSequence.newInstanceId()

    /**
     * Logs an event as part of a particular CUI, on a particular package.
     *
     * @param instanceId The id identifying an interaction, potentially taking place across multiple
     *   surfaces. There should be a new id generated for each distinct CUI.
     * @param uid The user id associated with the package the user is interacting with
     * @param packageName The name of the package the user is interacting with
     * @param event The event type to generate
     */
    fun logWithInstanceId(
        instanceId: InstanceId,
        uid: Int,
        packageName: String,
        event: DesktopUiEventEnum,
    ) {
        if (packageName.isEmpty() || uid < 0) {
            logD("Skip logging since package name is empty or bad uid")
            return
        }
        uiEventLogger.logWithInstanceId(event, uid, packageName, instanceId)
    }

    private fun getUid(packageName: String, userId: Int): Int =
        try {
            packageManager.getApplicationInfoAsUser(packageName, /* flags= */ 0, userId).uid
        } catch (e: PackageManager.NameNotFoundException) {
            INVALID_PACKAGE_UID
        }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    /** Enums for logging desktop windowing mode UiEvents. */
    enum class DesktopUiEventEnum(private val mId: Int) : UiEventEnum {

        @UiEvent(doc = "Resize the window in desktop windowing mode by dragging the edge")
        DESKTOP_WINDOW_EDGE_DRAG_RESIZE(1721),
        @UiEvent(doc = "Resize the window in desktop windowing mode by dragging the corner")
        DESKTOP_WINDOW_CORNER_DRAG_RESIZE(1722),
        @UiEvent(doc = "Tap on the window header maximize button in desktop windowing mode")
        DESKTOP_WINDOW_MAXIMIZE_BUTTON_TAP(1723),
        @UiEvent(doc = "Tap on the window header restore button in desktop windowing mode")
        DESKTOP_WINDOW_RESTORE_BUTTON_TAP(2017),
        @UiEvent(doc = "Double tap on window header to maximize it in desktop windowing mode")
        DESKTOP_WINDOW_HEADER_DOUBLE_TAP_TO_MAXIMIZE(1724),
        @UiEvent(doc = "Double tap on window header to restore from maximize in desktop windowing")
        DESKTOP_WINDOW_HEADER_DOUBLE_TAP_TO_RESTORE(2018),
        @UiEvent(doc = "Tap on the window Handle to open the Handle Menu")
        DESKTOP_WINDOW_APP_HANDLE_TAP(1998),
        @UiEvent(doc = "Tap on the desktop mode option under app handle menu")
        DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_DESKTOP_MODE(1999),
        @UiEvent(doc = "Tap on the split screen option under app handle menu")
        DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_SPLIT_SCREEN(2000),
        @UiEvent(doc = "Tap on the full screen option under app handle menu")
        DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_FULL_SCREEN(2001),
        @UiEvent(doc = "When user successfully drags the app handle to desktop mode")
        DESKTOP_WINDOW_APP_HANDLE_DRAG_TO_DESKTOP_MODE(2002),
        @UiEvent(doc = "When user successfully drags the app handle to split screen")
        DESKTOP_WINDOW_APP_HANDLE_DRAG_TO_SPLIT_SCREEN(2003),
        @UiEvent(doc = "When user successfully drags the app handle to full screen")
        DESKTOP_WINDOW_APP_HANDLE_DRAG_TO_FULL_SCREEN(2004),
        @UiEvent(doc = "Drag the window header to the top to switch to full screen mode")
        DESKTOP_WINDOW_APP_HEADER_DRAG_TO_FULL_SCREEN(2005),
        @UiEvent(doc = "Drag the window header to an edge to tile it to the left side")
        DESKTOP_WINDOW_APP_HEADER_DRAG_TO_TILE_TO_LEFT(2006),
        @UiEvent(doc = "Drag the window header to an edge to tile it to the right side")
        DESKTOP_WINDOW_APP_HEADER_DRAG_TO_TILE_TO_RIGHT(2007),
        @UiEvent(doc = "Hover or long press the maximize button to reveal the menu")
        DESKTOP_WINDOW_MAXIMIZE_BUTTON_REVEAL_MENU(2015),
        @UiEvent(doc = "Tap on the maximize option in the maximize button menu")
        DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_MAXIMIZE(2009),
        @UiEvent(doc = "Tap on the immersive option in the maximize button menu")
        DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_IMMERSIVE(2010),
        @UiEvent(doc = "Tap on the restore option in the maximize button menu")
        DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_RESTORE(2011),
        @UiEvent(doc = "Tap on the tile to left option in the maximize button menu")
        DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_TILE_TO_LEFT(2012),
        @UiEvent(doc = "Tap on the tile to right option in the maximize button menu")
        DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_TILE_TO_RIGHT(2013),
        @UiEvent(doc = "Moving the desktop window by dragging the header")
        DESKTOP_WINDOW_MOVE_BY_HEADER_DRAG(2021),
        @UiEvent(doc = "Double tap on the window header to refocus a desktop window")
        DESKTOP_WINDOW_HEADER_TAP_TO_REFOCUS(2022),
        @UiEvent(doc = "Enter multi-instance by using the New Window button")
        DESKTOP_WINDOW_MULTI_INSTANCE_NEW_WINDOW_CLICK(2069),
        @UiEvent(doc = "Enter multi-instance by clicking an icon in the Manage Windows menu")
        DESKTOP_WINDOW_MULTI_INSTANCE_MANAGE_WINDOWS_ICON_CLICK(2070),
        @UiEvent(doc = "Education tooltip on the app handle is shown")
        APP_HANDLE_EDUCATION_TOOLTIP_SHOWN(2097),
        @UiEvent(doc = "Education tooltip on the app handle is clicked")
        APP_HANDLE_EDUCATION_TOOLTIP_CLICKED(2098),
        @UiEvent(doc = "Education tooltip on the app handle is dismissed by the user")
        APP_HANDLE_EDUCATION_TOOLTIP_DISMISSED(2099),
        @UiEvent(doc = "Enter desktop mode education tooltip on the app handle menu is shown")
        ENTER_DESKTOP_MODE_EDUCATION_TOOLTIP_SHOWN(2100),
        @UiEvent(doc = "Enter desktop mode education tooltip on the app handle menu is clicked")
        ENTER_DESKTOP_MODE_EDUCATION_TOOLTIP_CLICKED(2101),
        @UiEvent(doc = "Enter desktop mode education tooltip is dismissed by the user")
        ENTER_DESKTOP_MODE_EDUCATION_TOOLTIP_DISMISSED(2102),
        @UiEvent(doc = "Exit desktop mode education tooltip on the app header menu is shown")
        EXIT_DESKTOP_MODE_EDUCATION_TOOLTIP_SHOWN(2103),
        @UiEvent(doc = "Exit desktop mode education tooltip on the app header menu is clicked")
        EXIT_DESKTOP_MODE_EDUCATION_TOOLTIP_CLICKED(2104),
        @UiEvent(doc = "Exit desktop mode education tooltip is dismissed by the user")
        EXIT_DESKTOP_MODE_EDUCATION_TOOLTIP_DISMISSED(2105),
        @UiEvent(doc = "A11y service opened app handle menu by selecting handle from fullscreen")
        A11Y_APP_HANDLE_MENU_OPENED(2156),
        @UiEvent(doc = "A11y service opened app handle menu through Switch Access actions menu ")
        A11Y_SYSTEM_ACTION_APP_HANDLE_MENU(2157),
        @UiEvent(doc = "A11y service selected desktop mode from app handle menu")
        A11Y_APP_HANDLE_MENU_DESKTOP_VIEW(2158),
        @UiEvent(doc = "A11y service selected fullscreen mode from app handle menu")
        A11Y_APP_HANDLE_MENU_FULLSCREEN(2159),
        @UiEvent(doc = "A11y service selected split screen mode from app handle menu")
        A11Y_APP_HANDLE_MENU_SPLIT_SCREEN(2160),
        @UiEvent(doc = "A11y service selected maximize/restore button from app header")
        A11Y_APP_WINDOW_MAXIMIZE_RESTORE_BUTTON(2161),
        @UiEvent(doc = "A11y service selected minimize button from app header")
        A11Y_APP_WINDOW_MINIMIZE_BUTTON(2162),
        @UiEvent(doc = "A11y service selected close button from app header")
        A11Y_APP_WINDOW_CLOSE_BUTTON(2163),
        @UiEvent(doc = "A11y service selected maximize button from app header maximize menu")
        A11Y_MAXIMIZE_MENU_MAXIMIZE(2164),
        @UiEvent(doc = "A11y service selected resize left button from app header maximize menu")
        A11Y_MAXIMIZE_MENU_RESIZE_LEFT(2165),
        @UiEvent(doc = "A11y service selected resize right button from app header maximize menu")
        A11Y_MAXIMIZE_MENU_RESIZE_RIGHT(2166),
        @UiEvent(doc = "A11y service triggered a11y action to maximize/restore app window")
        A11Y_ACTION_MAXIMIZE_RESTORE(2167),
        @UiEvent(doc = "A11y service triggered a11y action to resize app window left")
        A11Y_ACTION_RESIZE_LEFT(2168),
        @UiEvent(doc = "A11y service triggered a11y action to resize app window right")
        A11Y_ACTION_RESIZE_RIGHT(2169);

        override fun getId(): Int = mId
    }

    companion object {
        private const val TAG = "DesktopModeUiEventLogger"
        private const val INVALID_PACKAGE_UID = -1
    }
}
