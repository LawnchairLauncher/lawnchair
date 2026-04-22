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

package com.android.launcher3.statehandlers

import android.content.res.Resources
import android.platform.test.annotations.EnableFlags
import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.uioverrides.QuickstepLauncher
import java.util.Collections
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.same
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class DepthControllerTest {

    private lateinit var underTest: DepthController
    @Mock private lateinit var launcher: QuickstepLauncher
    @Mock private lateinit var stateManager: StateManager<LauncherState, Launcher>
    @Mock private lateinit var resource: Resources
    @Mock private lateinit var dragLayer: DragLayer
    @Mock private lateinit var viewTreeObserver: ViewTreeObserver

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        `when`(launcher.resources).thenReturn(resource)
        `when`(resource.getInteger(R.integer.max_depth_blur_radius)).thenReturn(30)
        `when`(launcher.dragLayer).thenReturn(dragLayer)
        `when`(dragLayer.viewTreeObserver).thenReturn(viewTreeObserver)
        `when`(launcher.stateManager).thenReturn(stateManager)
        `when`(launcher.depthBlurTargets).thenReturn(Collections.emptyList())

        underTest = DepthController(launcher)
    }

    @Test
    fun setActivityStarted_add_onDrawListener() {
        underTest.setActivityStarted(true)

        verify(viewTreeObserver).addOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun setActivityStopped_not_remove_onDrawListener() {
        underTest.setActivityStarted(false)

        // Because underTest.mOnDrawListener is never added
        verifyNoMoreInteractions(viewTreeObserver)
    }

    @Test
    fun setActivityStared_then_stopped_remove_onDrawListener() {
        underTest.setActivityStarted(true)
        reset(viewTreeObserver)

        underTest.setActivityStarted(false)

        verify(viewTreeObserver).removeOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun setActivityStared_then_stopped_multiple_times_remove_onDrawListener_once() {
        underTest.setActivityStarted(true)
        reset(viewTreeObserver)

        underTest.setActivityStarted(false)
        underTest.setActivityStarted(false)
        underTest.setActivityStarted(false)

        // Should just remove mOnDrawListener once
        verify(viewTreeObserver).removeOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun test_onInvalidSurface_multiple_times_add_onDrawListener_once() {
        underTest.onInvalidSurface()
        underTest.onInvalidSurface()
        underTest.onInvalidSurface()

        // We should only call addOnDrawListener 1 time
        verify(viewTreeObserver).addOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    @EnableFlags(Flags.FLAG_ALL_APPS_BLUR)
    fun test_blurWorkspaceDepthTargets() {
        // Transitioning to ALL_APPS from any state should blur the workspace depth targets.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.NORMAL)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.SPRING_LOADED)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.EDIT_MODE)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.BACKGROUND_APP)
        `when`(stateManager.state).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        // Returning from ALL_APPS to NORMAL should continue blurring the workspace depth targets.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.NORMAL)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        // Exiting ALL_APPS to other states such as drag-and-drop should not blur the workspace.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.SPRING_LOADED)
        assertFalse(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.EDIT_MODE)
        assertFalse(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.state).thenReturn(LauncherState.OVERVIEW)
        assertFalse(underTest.blurWorkspaceDepthTargets())
    }

    @Test
    @EnableFlags(Flags.FLAG_ALL_APPS_BLUR)
    fun test_blurWorkspaceDepthTargets_withTargetState() {
        // Transitioning to ALL_APPS from any state should blur the workspace depth targets.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.NORMAL)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.SPRING_LOADED)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.EDIT_MODE)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.BACKGROUND_APP)
        `when`(stateManager.targetState).thenReturn(LauncherState.ALL_APPS)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        // Returning from ALL_APPS to NORMAL should continue blurring the workspace depth targets.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.NORMAL)
        assertTrue(underTest.blurWorkspaceDepthTargets())

        // Exiting ALL_APPS to other states such as drag-and-drop should not blur the workspace.

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.SPRING_LOADED)
        assertFalse(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.EDIT_MODE)
        assertFalse(underTest.blurWorkspaceDepthTargets())

        `when`(stateManager.currentStableState).thenReturn(LauncherState.ALL_APPS)
        `when`(stateManager.targetState).thenReturn(LauncherState.OVERVIEW)
        assertFalse(underTest.blurWorkspaceDepthTargets())
    }
}
