/*
 *  Copyright (C) 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.launcher3.taskbar

import androidx.test.runner.AndroidJUnit4
import com.android.launcher3.statemanager.StateManager
import com.android.quickstep.RecentsActivity
import com.android.quickstep.fallback.RecentsState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever

@RunWith(AndroidJUnit4::class)
class FallbackTaskbarUIControllerTest : TaskbarBaseTestCase() {

    lateinit var fallbackTaskbarUIController: FallbackTaskbarUIController
    lateinit var stateListener: StateManager.StateListener<RecentsState>

    @Mock lateinit var recentsActivity: RecentsActivity
    @Mock lateinit var stateManager: StateManager<RecentsState>

    @Before
    override fun setup() {
        super.setup()
        whenever(recentsActivity.stateManager).thenReturn(stateManager)
        fallbackTaskbarUIController = FallbackTaskbarUIController(recentsActivity)

        // Capture registered state listener to send events to in our tests
        val captor = ArgumentCaptor.forClass(StateManager.StateListener::class.java)
        fallbackTaskbarUIController.init(taskbarControllers)
        verify(stateManager).addStateListener(captor.capture())
        stateListener = captor.value as StateManager.StateListener<RecentsState>
    }

    @Test
    fun stateTransitionComplete_stateDefault() {
        stateListener.onStateTransitionComplete(RecentsState.DEFAULT)
        // verify dragging disabled
        verify(taskbarDragController, times(1)).setDisallowGlobalDrag(true)
        verify(taskbarAllAppsController, times(1)).setDisallowGlobalDrag(true)
        // verify long click enabled
        verify(taskbarDragController, times(1)).setDisallowLongClick(false)
        verify(taskbarAllAppsController, times(1)).setDisallowLongClick(false)
        // verify split selection enabled
        verify(taskbarPopupController, times(1)).setAllowInitialSplitSelection(true)
    }

    @Test
    fun stateTransitionComplete_stateSplitSelect() {
        stateListener.onStateTransitionComplete(RecentsState.OVERVIEW_SPLIT_SELECT)
        // verify dragging disabled
        verify(taskbarDragController, times(1)).setDisallowGlobalDrag(false)
        verify(taskbarAllAppsController, times(1)).setDisallowGlobalDrag(false)
        // verify long click enabled
        verify(taskbarDragController, times(1)).setDisallowLongClick(true)
        verify(taskbarAllAppsController, times(1)).setDisallowLongClick(true)
        // verify split selection enabled
        verify(taskbarPopupController, times(1)).setAllowInitialSplitSelection(false)
    }
}
