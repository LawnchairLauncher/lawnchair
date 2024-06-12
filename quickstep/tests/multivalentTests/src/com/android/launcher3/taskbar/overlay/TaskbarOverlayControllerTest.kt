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
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_OPTIONS_POPUP
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_ALL_APPS
import com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_OVERLAY_PROXY
import com.android.launcher3.AbstractFloatingView.hasOpenView
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarUnitTestRule
import com.android.launcher3.taskbar.TaskbarUnitTestRule.InjectController
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.android.launcher3.views.BaseDragLayer
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelFoldable2023"])
class TaskbarOverlayControllerTest {

    @get:Rule val taskbarUnitTestRule = TaskbarUnitTestRule()
    @InjectController lateinit var overlayController: TaskbarOverlayController

    private val taskbarContext: TaskbarActivityContext
        get() = taskbarUnitTestRule.activityContext

    @Test
    @UiThreadTest
    fun testRequestWindow_twice_reusesWindow() {
        val context1 = overlayController.requestWindow()
        val context2 = overlayController.requestWindow()
        assertThat(context1).isSameInstanceAs(context2)
    }

    @Test
    @UiThreadTest
    fun testRequestWindow_afterHidingExistingWindow_createsNewWindow() {
        val context1 = overlayController.requestWindow()
        overlayController.hideWindow()

        val context2 = overlayController.requestWindow()
        assertThat(context1).isNotSameInstanceAs(context2)
    }

    @Test
    @UiThreadTest
    fun testRequestWindow_afterHidingOverlay_createsNewWindow() {
        val context1 = overlayController.requestWindow()
        TestOverlayView.show(context1)
        overlayController.hideWindow()

        val context2 = overlayController.requestWindow()
        assertThat(context1).isNotSameInstanceAs(context2)
    }

    @Test
    @UiThreadTest
    fun testRequestWindow_addsProxyView() {
        TestOverlayView.show(overlayController.requestWindow())
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isTrue()
    }

    @Test
    @UiThreadTest
    fun testRequestWindow_closeProxyView_closesOverlay() {
        val overlay = TestOverlayView.show(overlayController.requestWindow())
        AbstractFloatingView.closeOpenContainer(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)
        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    fun testRequestWindow_attachesDragLayer() {
        lateinit var dragLayer: BaseDragLayer<*>
        getInstrumentation().runOnMainSync {
            dragLayer = overlayController.requestWindow().dragLayer
        }

        // Allow drag layer to attach before checking.
        getInstrumentation().runOnMainSync { assertThat(dragLayer.isAttachedToWindow).isTrue() }
    }

    @Test
    @UiThreadTest
    fun testHideWindow_closesOverlay() {
        val overlay = TestOverlayView.show(overlayController.requestWindow())
        overlayController.hideWindow()
        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    fun testHideWindow_detachesDragLayer() {
        lateinit var dragLayer: BaseDragLayer<*>
        getInstrumentation().runOnMainSync {
            dragLayer = overlayController.requestWindow().dragLayer
        }

        // Wait for drag layer to be attached to window before hiding.
        getInstrumentation().runOnMainSync {
            overlayController.hideWindow()
            assertThat(dragLayer.isAttachedToWindow).isFalse()
        }
    }

    @Test
    @UiThreadTest
    fun testTwoOverlays_closeOne_windowStaysOpen() {
        val context = overlayController.requestWindow()
        val overlay1 = TestOverlayView.show(context)
        val overlay2 = TestOverlayView.show(context)

        overlay1.close(false)
        assertThat(overlay2.isOpen).isTrue()
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isTrue()
    }

    @Test
    @UiThreadTest
    fun testTwoOverlays_closeAll_closesWindow() {
        val context = overlayController.requestWindow()
        val overlay1 = TestOverlayView.show(context)
        val overlay2 = TestOverlayView.show(context)

        overlay1.close(false)
        overlay2.close(false)
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isFalse()
    }

    @Test
    @UiThreadTest
    fun testRecreateTaskbar_closesWindow() {
        TestOverlayView.show(overlayController.requestWindow())
        taskbarUnitTestRule.recreateTaskbar()
        assertThat(hasOpenView(taskbarContext, TYPE_TASKBAR_OVERLAY_PROXY)).isFalse()
    }

    @Test
    fun testTaskMovedToFront_closesOverlay() {
        lateinit var overlay: TestOverlayView
        getInstrumentation().runOnMainSync {
            overlay = TestOverlayView.show(overlayController.requestWindow())
        }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskMovedToFront(RunningTaskInfo())
        // Make sure TaskStackChangeListeners' Handler posts the callback before checking state.
        getInstrumentation().runOnMainSync { assertThat(overlay.isOpen).isFalse() }
    }

    @Test
    fun testTaskStackChanged_allAppsClosed_overlayStaysOpen() {
        lateinit var overlay: TestOverlayView
        getInstrumentation().runOnMainSync {
            overlay = TestOverlayView.show(overlayController.requestWindow())
            taskbarContext.controllers.sharedState?.allAppsVisible = false
        }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskStackChanged()
        getInstrumentation().runOnMainSync { assertThat(overlay.isOpen).isTrue() }
    }

    @Test
    fun testTaskStackChanged_allAppsOpen_closesOverlay() {
        lateinit var overlay: TestOverlayView
        getInstrumentation().runOnMainSync {
            overlay = TestOverlayView.show(overlayController.requestWindow())
            taskbarContext.controllers.sharedState?.allAppsVisible = true
        }

        TaskStackChangeListeners.getInstance().listenerImpl.onTaskStackChanged()
        getInstrumentation().runOnMainSync { assertThat(overlay.isOpen).isFalse() }
    }

    @Test
    @UiThreadTest
    fun testUpdateLauncherDeviceProfile_overlayNotRebindSafe_closesOverlay() {
        val overlayContext = overlayController.requestWindow()
        val overlay = TestOverlayView.show(overlayContext).apply { type = TYPE_OPTIONS_POPUP }

        overlayController.updateLauncherDeviceProfile(
            overlayController.launcherDeviceProfile
                .toBuilder(overlayContext)
                .setGestureMode(false)
                .build()
        )

        assertThat(overlay.isOpen).isFalse()
    }

    @Test
    @UiThreadTest
    fun testUpdateLauncherDeviceProfile_overlayRebindSafe_overlayStaysOpen() {
        val overlayContext = overlayController.requestWindow()
        val overlay = TestOverlayView.show(overlayContext).apply { type = TYPE_TASKBAR_ALL_APPS }

        overlayController.updateLauncherDeviceProfile(
            overlayController.launcherDeviceProfile
                .toBuilder(overlayContext)
                .setGestureMode(false)
                .build()
        )

        assertThat(overlay.isOpen).isTrue()
    }

    private class TestOverlayView
    private constructor(
        private val overlayContext: TaskbarOverlayContext,
    ) : AbstractFloatingView(overlayContext, null) {

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
