/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.taskbar

import android.view.MotionEvent
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_HOVER_EXIT
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatAppPairsItem
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatFolderItem
import com.android.launcher3.taskbar.TaskbarViewTestUtil.createHotseatWorkspaceItem
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023", "pixelTablet2023"])
class TaskbarHoverToolTipControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)

    @InjectController lateinit var autohideSuspendController: TaskbarAutohideSuspendController
    @InjectController lateinit var popupController: TaskbarPopupController

    private val taskbarContext: TaskbarActivityContext by taskbarUnitTestRule::activityContext

    private lateinit var taskbarView: TaskbarView
    private lateinit var iconView: BubbleTextView
    private lateinit var appPairIcon: AppPairIcon
    private lateinit var folderIcon: FolderIcon

    private val isHoverToolTipOpen: Boolean
        get() {
            // TaskbarHoverToolTip uses ArrowTipView which is type TYPE_ON_BOARD_POPUP.
            return AbstractFloatingView.hasOpenView(
                taskbarContext,
                AbstractFloatingView.TYPE_ON_BOARD_POPUP,
            )
        }

    @Before
    fun setup() {
        runOnMainSync { taskbarView = taskbarContext.dragLayer.findViewById(R.id.taskbar_view) }

        val hotseatItems =
            arrayOf(
                createHotseatWorkspaceItem(),
                createHotseatAppPairsItem(),
                createHotseatFolderItem(),
            )
        runOnMainSync {
            taskbarView.updateItems(hotseatItems, emptyList())
            iconView =
                taskbarView.iconViews.filterIsInstance<BubbleTextView>().first {
                    it.tag is WorkspaceItemInfo
                }
            appPairIcon = taskbarView.iconViews.filterIsInstance<AppPairIcon>().first()
            folderIcon = taskbarView.iconViews.filterIsInstance<FolderIcon>().first()
        }
    }

    @Test
    fun onHover_hoverEnterIcon_revealToolTip_hoverExitIcon_closeToolTip() {
        runOnMainSync { iconView.dispatchGenericMotionEvent(HOVER_ENTER) }
        assertThat(isHoverToolTipOpen).isTrue()
        assertThat(autohideSuspendController.isTransientTaskbarStashingSuspended).isTrue()
        runOnMainSync { iconView.dispatchGenericMotionEvent(HOVER_EXIT) }
        assertThat(isHoverToolTipOpen).isFalse()
        assertThat(autohideSuspendController.isTransientTaskbarStashingSuspended).isFalse()
    }

    @Test
    fun onHover_hoverEnterFolderIcon_revealToolTip_hoverExitFolderIcon_closeToolTip() {
        runOnMainSync { folderIcon.dispatchGenericMotionEvent(HOVER_ENTER) }
        assertThat(isHoverToolTipOpen).isTrue()
        assertThat(autohideSuspendController.isTransientTaskbarStashingSuspended).isTrue()
        runOnMainSync { folderIcon.dispatchGenericMotionEvent(HOVER_EXIT) }
        assertThat(isHoverToolTipOpen).isFalse()
        assertThat(autohideSuspendController.isTransientTaskbarStashingSuspended).isFalse()
    }

    @Test
    fun onHover_hoverEnterAppPair_revealToolTip_hoverExitAppPair_closeToolTip() {
        runOnMainSync { appPairIcon.dispatchGenericMotionEvent(HOVER_ENTER) }
        assertThat(isHoverToolTipOpen).isTrue()
        assertThat(autohideSuspendController.isTransientTaskbarStashingSuspended).isTrue()
        runOnMainSync { appPairIcon.dispatchGenericMotionEvent(HOVER_EXIT) }
        assertThat(isHoverToolTipOpen).isFalse()
        assertThat(autohideSuspendController.isTransientTaskbarStashingSuspended).isFalse()
    }

    @Test
    fun onHover_hoverEnterIconAlignedWithHotseat_noToolTip() {
        taskbarContext.setUIController(
            object : TaskbarUIController() {
                override fun isIconAlignedWithHotseat(): Boolean = true
            }
        )

        runOnMainSync { iconView.dispatchGenericMotionEvent(HOVER_ENTER) }
        assertThat(isHoverToolTipOpen).isFalse()
    }

    @Test
    fun onHover_hoverEnterFolderOpen_noToolTip() {
        runOnMainSync {
            folderIcon.folder.animateOpen()
            iconView.dispatchGenericMotionEvent(HOVER_ENTER)
        }
        assertThat(isHoverToolTipOpen).isFalse()
    }

    @Test
    fun onHover_hoverEnterPopupOpen_noToolTip() {
        runOnMainSync {
            popupController.showForIcon(iconView)
            iconView.dispatchGenericMotionEvent(HOVER_ENTER)
        }
        assertThat(isHoverToolTipOpen).isFalse()
    }

    @Test
    fun onHover_emptyTitle_noTooltip() {
        runOnMainSync {
            iconView.text = ""
            iconView.dispatchGenericMotionEvent(HOVER_ENTER)
        }
        assertThat(isHoverToolTipOpen).isFalse()
    }

    companion object {
        private val HOVER_EXIT = MotionEvent.obtain(0, 0, ACTION_HOVER_EXIT, 0f, 0f, 0)
        private val HOVER_ENTER = MotionEvent.obtain(0, 0, ACTION_HOVER_ENTER, 0f, 0f, 0)
    }
}
