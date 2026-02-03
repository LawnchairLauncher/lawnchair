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

package com.android.launcher3.taskbar.overlay

import android.app.ActivityManager.RunningTaskInfo
import android.view.MotionEvent
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_OPTIONS_POPUP
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_ALL_APPS
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_OVERLAY_PROXY
import com.android.launcher3.AbstractFloatingView.hasOpenView
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllerTestUtil.runOnMainSync
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule
import com.android.launcher3.taskbar.rules.TaskbarUnitTestRule.InjectController
import com.android.launcher3.taskbar.rules.TaskbarWindowSandboxContext
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.util.TestUtil.getOnUiThread
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023"])
class TaskbarOverlayControllerTest {

    @get:Rule(order = 0) val context = TaskbarWindowSandboxContext.create()
    @get:Rule(order = 1) val taskbarUnitTestRule = TaskbarUnitTestRule(this, context)
    @InjectController lateinit var overlayController: TaskbarOverlayController

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    @Test
    fun testRequestWindow_twice_reusesWindow() {
        val (context1, context2) =
            getOnUiThread {
                Pair(overlayController.requestWindow(), overlayController.requestWindow())
            }
        assertThat(context1).isSameInstanceAs(context2)
    }

    @Test
    fun testRequestWindow_afterHidingExistingWindow_createsNewWindow() {
        val context1 = getOnUiThread { overlayController.requestWindow() }
        runOnMainSync { overlayController.hideWindow() }

        val context2 = getOnUiThread { overlayController.requestWindow() }
        assertThat(context1).isNotSameInstanceAs(context2)
    }

    @Test
    fun testRequestWindow_afterHidingOverlay_createsNewWindow() {
        val context1 = getOnUiThread { overlayController.requestWindow() }
        runOnMainSync {
            TestOverlayView.show(context1)
            overlayController.hideWindow()
        }

        val context2 = getOnUiThread { overlayController.requestWindow() }
        assertThat(context1).isNotSameInstanceAs(context2)
    }

    @Test
    fun testRequestWindow_addsProxyView() {
        runOnMainSync { TestOverlayView.show(overlayController.requestWindow()) }
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isTrue()
    }

    @Test
    fun testRequestWindow_closeProxyView_closesOverlay() {
        val overlay = getOnUiThread { TestOverlayView.show(overlayController.requestWindow()) }
        runOnMainSync {
            AbstractFloatingView.closeOpenContainer(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)
        }
        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    fun testRequestWindow_attachesDragLayer() {
        val dragLayer = getOnUiThread { overlayController.requestWindow().dragLayer }
        // Allow drag layer to attach before checking.
        runOnMainSync { assertThat(dragLayer.isAttachedToWindow).isTrue() }
    }

    @Test
    fun testHideWindow_closesOverlay() {
        val overlay = getOnUiThread { TestOverlayView.show(overlayController.requestWindow()) }
        runOnMainSync { overlayController.hideWindow() }
        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    fun testHideWindow_detachesDragLayer() {
        val dragLayer = getOnUiThread { overlayController.requestWindow().dragLayer }

        // Wait for drag layer to be attached to window before hiding.
        runOnMainSync {
            overlayController.hideWindow()
            assertThat(dragLayer.isAttachedToWindow).isFalse()
        }
    }

    @Test
    fun testTwoOverlays_closeOne_windowStaysOpen() {
        val (overlay1, overlay2) =
            getOnUiThread {
                val context = overlayController.requestWindow()
                Pair(TestOverlayView.show(context), TestOverlayView.show(context))
            }

        runOnMainSync { overlay1.close(false) }
        assertThat(overlay2.isOpen).isTrue()
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isTrue()
    }

    @Test
    fun testTwoOverlays_closeAll_closesWindow() {
        val (overlay1, overlay2) =
            getOnUiThread {
                val context = overlayController.requestWindow()
                Pair(TestOverlayView.show(context), TestOverlayView.show(context))
            }

        runOnMainSync {
            overlay1.close(false)
            overlay2.close(false)
        }
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isFalse()
    }

    @Test
    fun testRecreateTaskbar_closesWindow() {
        runOnMainSync { TestOverlayView.show(overlayController.requestWindow()) }
        taskbarUnitTestRule.recreateTaskbar()
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isFalse()
    }

    @Test
    fun testTaskMovedToFront_closesOverlay() {
        val overlay = getOnUiThread { TestOverlayView.show(overlayController.requestWindow()) }
        TaskStackChangeListeners.getInstance().listenerImpl.onTaskMovedToFront(RunningTaskInfo())
        // Make sure TaskStackChangeListeners' Handler posts the callback before checking state.
        runOnMainSync { assertThat(overlay.isOpen).isFalse() }
    }

    @Test
    fun testTaskStackChanged_allAppsClosed_overlayStaysOpen() {
        val overlay = getOnUiThread { TestOverlayView.show(overlayController.requestWindow()) }
        runOnMainSync { taskbarContext.controllers.sharedState?.allAppsVisible = false }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskStackChanged()
        runOnMainSync { assertThat(overlay.isOpen).isTrue() }
    }

    @Test
    fun testTaskStackChanged_allAppsOpen_closesOverlay() {
        val overlay = getOnUiThread { TestOverlayView.show(overlayController.requestWindow()) }
        runOnMainSync { taskbarContext.controllers.sharedState?.allAppsVisible = true }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskStackChanged()
        runOnMainSync { assertThat(overlay.isOpen).isFalse() }
    }

    @Test
    fun testUpdateLauncherDeviceProfile_overlayNotRebindSafe_closesOverlay() {
        val context = getOnUiThread { overlayController.requestWindow() }
        val overlay = getOnUiThread {
            TestOverlayView.show(context).apply { type = TYPE_OPTIONS_POPUP }
        }

        runOnMainSync {
            overlayController.updateLauncherDeviceProfile(
                overlayController.launcherDeviceProfile
                    .toBuilder(context)
                    .setGestureMode(false)
                    .build()
            )
        }

        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    fun testUpdateLauncherDeviceProfile_overlayRebindSafe_overlayStaysOpen() {
        val context = getOnUiThread { overlayController.requestWindow() }
        val overlay = getOnUiThread {
            TestOverlayView.show(context).apply { type = TYPE_TASKBAR_ALL_APPS }
        }

        runOnMainSync {
            overlayController.updateLauncherDeviceProfile(
                overlayController.launcherDeviceProfile
                    .toBuilder(context)
                    .setGestureMode(false)
                    .build()
            )
        }

        assertThat(overlay.isOpen).isTrue()
    }

    private class TestOverlayView
    private constructor(private val overlayContext: TaskbarOverlayContext) :
        AbstractFloatingView(overlayContext, null) {

        var type = TYPE_OPTIONS_POPUP

        private fun show() {
            mIsOpen = true
            overlayContext.dragLayer.addView(this)
        }

        override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean = false

        override fun handleClose(animate: Boolean) = overlayContext.dragLayer.removeView(this)

        override fun isOfType(type: Int): Boolean = (type and this.type) != 0

        companion object {
            /** Adds a generic View to the Overlay window for testing. */
            fun show(context: TaskbarOverlayContext): TestOverlayView {
                return TestOverlayView(context).apply { show() }
            }
        }
    }
}
