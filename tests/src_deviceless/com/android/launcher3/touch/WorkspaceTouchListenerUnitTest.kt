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

package com.android.launcher3.touch

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.Workspace
import com.android.launcher3.dragndrop.DragLayer
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.annotation.LooperMode

@LooperMode(LooperMode.Mode.PAUSED)
@RunWith(AndroidJUnit4::class)
class WorkspaceTouchListenerUnitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val dragLayer =
        mock<DragLayer> {
            // Ensure a drag layer can be used for simulated touch events.
            on { width } doReturn 200
            on { height } doReturn 200
        }
    private val mockLauncher =
        mock<Launcher> {
            on { dragLayer } doReturn dragLayer

            // Provide real Resources for ViewConfiguration initialization
            on { resources } doReturn context.resources
            on { isInState(LauncherState.NORMAL) } doReturn true
            on { deviceProfile } doReturn
                InvariantDeviceProfile.INSTANCE[context].getDeviceProfile(context)
        }
    private val mockWorkspace = mock<Workspace<*>>()
    private val workspaceTouchListener = WorkspaceTouchListener(mockLauncher, mockWorkspace)

    @Test
    fun onWorkspaceTouch_whenHomeBehindDesktop_launchesHomeIntent() {
        mockLauncher.stub { on { shouldShowHomeBehindDesktop() } doReturn true }

        // Simulate a tap event in the workspace.
        val downTime = SystemClock.uptimeMillis()
        val downEvent =
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        workspaceTouchListener.onTouch(null, downEvent)
        val upEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_UP, 100f, 100f, 0)
        workspaceTouchListener.onTouch(null, upEvent)

        // Verify startActivity was called with the correct Intent
        val capturedIntent =
            argumentCaptor<Intent>().let { intentCaptor ->
                verify(mockLauncher).startActivity(intentCaptor.capture())
                intentCaptor.lastValue
            }
        assertWithMessage("Intent action should be ACTION_MAIN")
            .that(capturedIntent.action)
            .isEqualTo(Intent.ACTION_MAIN)
        assertWithMessage("Intent should have CATEGORY_HOME")
            .that(capturedIntent.hasCategory(Intent.CATEGORY_HOME))
            .isTrue()
        assertWithMessage("Intent should have FLAG_ACTIVITY_NEW_TASK")
            .that(capturedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            .isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun onWorkspaceTouch_doesNotLaunchHomeIntent() {
        mockLauncher.stub { on { shouldShowHomeBehindDesktop() } doReturn false }

        // Simulate a tap event in the workspace.
        val downTime = SystemClock.uptimeMillis()
        val downEvent =
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        workspaceTouchListener.onTouch(null, downEvent)
        val upEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_UP, 100f, 100f, 0)
        workspaceTouchListener.onTouch(null, upEvent)

        // Verify that no Intent is called.
        verify(mockLauncher, never()).startActivity(any<Intent>())
    }
}
