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

package com.android.quickstep.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Workspace
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class FloatingDesktopTaskViewTest {

    @get:Rule val context = SandboxApplication()
    @get:Rule val uiContext = TestActivityContext(context)

    private lateinit var deviceProfile: DeviceProfile
    private val launcher: QuickstepLauncher = mock()
    private val workspace: Workspace<*> = mock()
    private val dragLayer: DragLayer = mock()

    @Before
    fun setUp() {
        deviceProfile = InvariantDeviceProfile.INSTANCE[context].getDeviceProfile(context).copy()
        `when`(launcher.workspace).thenReturn(workspace)
        `when`(launcher.dragLayer).thenReturn(dragLayer)
        `when`(launcher.deviceProfile).thenReturn(deviceProfile)
        `when`(dragLayer.generateLayoutParams(any(), any())).thenAnswer { invocation ->
            BaseDragLayer.LayoutParams(
                invocation.arguments[0] as Context,
                invocation.arguments[1] as AttributeSet,
            )
        }
        `when`(launcher.layoutInflater).thenReturn(LayoutInflater.from(uiContext))
        `when`(dragLayer.indexOfChild(null)).thenReturn(-1)
    }

    @Test
    fun create_marginsSameAsStartPos() {
        val startBounds = RectF(10f, 20f, 110f, 220f)
        val taskView = FloatingDesktopTaskView.create(launcher, startBounds, BITMAP)

        val layoutParams = taskView.layoutParams as InsettableFrameLayout.LayoutParams
        assertThat(layoutParams.leftMargin).isEqualTo(10)
        assertThat(layoutParams.topMargin).isEqualTo(20)
    }

    @Test
    fun create_addsViewOnTopOfExistingViews() {
        `when`(dragLayer.childCount).thenReturn(2)
        val taskView = FloatingDesktopTaskView.create(launcher, START_BOUNDS, BITMAP)

        verify(dragLayer).addView(taskView, 2)
    }

    @Test
    fun addClosingAnimation_pivotsWithWorkspace() {
        val taskView = FloatingDesktopTaskView.create(launcher, START_BOUNDS, BITMAP)
        val animation = PendingAnimation(/* duration= */ 100)

        taskView.addClosingAnimation(launcher, animation)

        verify(launcher.workspace).setPivotToScaleWithSelf(taskView)
    }

    companion object {
        private val START_BOUNDS = RectF(0f, 1f, 2f, 3f)
        private val BITMAP = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    }
}
