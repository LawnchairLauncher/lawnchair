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
package com.android.wm.shell.draganddrop

import android.app.ActivityManager.RunningTaskInfo
import android.os.RemoteException
import android.view.DragEvent
import android.view.DragEvent.ACTION_DROP
import android.view.IWindowManager
import android.view.SurfaceControl
import android.window.IUnhandledDragCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.draganddrop.GlobalDragListener.GlobalDragListenerCallback
import java.util.function.Consumer
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

/**
 * Tests for the unhandled drag controller.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class UnhandledDragControllerTest : ShellTestCase() {
    private val mIWindowManager = mock<IWindowManager>()
    private val mMainExecutor = mock<ShellExecutor>()

    private lateinit var mController: GlobalDragListener

    @Before
    @Throws(RemoteException::class)
    fun setUp() {
        mController = GlobalDragListener(mIWindowManager, mMainExecutor)
    }

    @Test
    fun setListener_registersUnregistersWithWM() {
        mController.setListener(object : GlobalDragListenerCallback {})
        mController.setListener(object : GlobalDragListenerCallback {})
        mController.setListener(object : GlobalDragListenerCallback {})
        verify(mIWindowManager, Mockito.times(1))
                .setGlobalDragListener(ArgumentMatchers.any())

        reset(mIWindowManager)
        mController.setListener(null)
        mController.setListener(null)
        mController.setListener(null)
        verify(mIWindowManager, Mockito.times(1))
                .setGlobalDragListener(ArgumentMatchers.isNull())
    }

    @Test
    fun onUnhandledDrop_noListener_expectNotifyUnhandled() {
        // Simulate an unhandled drop
        val dropEvent = DragEvent.obtain(ACTION_DROP, 0f, 0f, 0f, 0f, 0, null, null, null,
            null, null, false)
        val wmCallback = mock<IUnhandledDragCallback>()
        mController.onUnhandledDrop(dropEvent, wmCallback)

        verify(wmCallback).notifyUnhandledDropComplete(ArgumentMatchers.eq(false))
    }

    @Test
    fun onUnhandledDrop_withListener_expectNotifyHandled() {
        val lastDragEvent = arrayOfNulls<DragEvent>(1)

        // Set a listener to listen for unhandled drops
        mController.setListener(object : GlobalDragListenerCallback {
            override fun onUnhandledDrop(dragEvent: DragEvent,
                onFinishedCallback: Consumer<Boolean>) {
                lastDragEvent[0] = dragEvent
                onFinishedCallback.accept(true)
                dragEvent.dragSurface.release()
            }
        })

        // Simulate an unhandled drop
        val dragSurface = mock<SurfaceControl>()
        val dropEvent = DragEvent.obtain(ACTION_DROP, 0f, 0f, 0f, 0f, 0, null, null, null,
            dragSurface, null, false)
        val wmCallback = mock<IUnhandledDragCallback>()
        mController.onUnhandledDrop(dropEvent, wmCallback)

        verify(wmCallback).notifyUnhandledDropComplete(ArgumentMatchers.eq(true))
        verify(dragSurface).release()
        assertEquals(lastDragEvent.get(0), dropEvent)
    }

    @Test
    fun onCrossWindowDrop() {
        val lastTaskInfo = arrayOfNulls<RunningTaskInfo>(1)

        // Set a listener to listen for unhandled drops
        mController.setListener(object : GlobalDragListenerCallback {
            override fun onCrossWindowDrop(taskInfo: RunningTaskInfo) {
                lastTaskInfo[0] = taskInfo
            }
        })

        // Simulate a cross-window drop
        val taskInfo = mock<RunningTaskInfo>()
        mController.onCrossWindowDrop(taskInfo)
        assertEquals(lastTaskInfo.get(0), taskInfo)
    }
}
