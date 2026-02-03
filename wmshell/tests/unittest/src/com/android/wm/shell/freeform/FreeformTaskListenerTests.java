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

package com.android.wm.shell.freeform;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.window.flags2.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION;
import static com.android.window.flags2.Flags.FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS;
import static com.android.window.flags2.Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.window.flags2.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.desktopmode.DesktopModeLoggerTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopRepository;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/**
 * Tests for {@link FreeformTaskListener} Build/Install/Run: atest
 * WMShellUnitTests:FreeformTaskListenerTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class FreeformTaskListenerTests extends ShellTestCase {

    @Mock
    private ShellTaskOrganizer mTaskOrganizer;
    @Mock
    private ShellInit mShellInit;
    @Mock
    private WindowDecorViewModel mWindowDecorViewModel;
    @Mock
    private SurfaceControl mMockSurfaceControl;
    @Mock
    private DesktopUserRepositories mDesktopUserRepositories;
    @Mock
    private DesktopRepository mDesktopRepository;
    @Mock
    private DesktopTasksController mDesktopTasksController;
    @Mock
    private DesktopModeLoggerTransitionObserver mDesktopModeLoggerTransitionObserver;
    @Mock
    private LaunchAdjacentController mLaunchAdjacentController;
    @Mock
    private TaskChangeListener mTaskChangeListener;

    private FreeformTaskListener mFreeformTaskListener;
    private AutoCloseable mMocksInit = null;

    @Before
    public void setup() {
        mMocksInit = MockitoAnnotations.openMocks(this);

        var desktopState = new FakeDesktopState();
        desktopState.setCanEnterDesktopMode(true);
        desktopState.setFreeformEnabled(true);

        when(mDesktopUserRepositories.getCurrent()).thenReturn(mDesktopRepository);
        when(mDesktopUserRepositories.getProfile(anyInt())).thenReturn(mDesktopRepository);
        mFreeformTaskListener =
                new FreeformTaskListener(
                        mContext,
                        mShellInit,
                        mTaskOrganizer,
                        Optional.of(mDesktopUserRepositories),
                        Optional.of(mDesktopTasksController),
                        mDesktopModeLoggerTransitionObserver,
                        mLaunchAdjacentController,
                        mWindowDecorViewModel,
                        Optional.of(mTaskChangeListener),
                        desktopState);
    }

    @After
    public void tearDown() throws Exception {
        if (mMocksInit != null) {
            mMocksInit.close();
            mMocksInit = null;
        }
    }

    @Test
    @DisableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    public void onTaskAppeared_noTransitionObservers_visibleTask_addsTaskToRepo() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;

        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        verify(mDesktopUserRepositories.getCurrent())
                .addTask(task.displayId, task.taskId, task.isVisible = true,
                        task.configuration.windowConfiguration.getBounds()
                );
    }

    @Test
    @DisableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    public void onTaskAppeared_noTransitionObservers_nonVisibleTask_addsTaskToRepo() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = false;

        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        verify(mDesktopUserRepositories.getCurrent())
                .addTask(task.displayId, task.taskId, task.isVisible,
                        task.configuration.windowConfiguration.getBounds());
    }

    @Test
    @EnableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    public void onTaskAppeared_useTransitionObserver_noopInRepository() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;

        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        verify(mDesktopUserRepositories.getCurrent(), never())
                .addTask(task.displayId, task.taskId, task.isVisible,
                        task.configuration.windowConfiguration.getBounds());
    }

    @Test
    @DisableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    public void focusTaskChanged_noTransitionObserversFlag_addsFreeformTaskToRepo() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isFocused = true;

        mFreeformTaskListener.onFocusTaskChanged(task);

        verify(mDesktopUserRepositories.getCurrent())
                .addTask(task.displayId, task.taskId, task.isVisible,
                        task.configuration.windowConfiguration.getBounds());
    }

    @Test
    @EnableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    public void focusTaskChanged_enableTransitionObservers_freeformTaskNotAddedToRepo() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isFocused = true;

        mFreeformTaskListener.onFocusTaskChanged(task);

        verify(mDesktopUserRepositories.getCurrent(), never())
                .addTask(task.displayId, task.taskId, task.isVisible,
                        task.configuration.windowConfiguration.getBounds());
    }

    @Test
    public void focusTaskChanged_fullscreenTaskNotAddedToRepo() {
        ActivityManager.RunningTaskInfo fullscreenTask =
                new TestRunningTaskInfoBuilder()
                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                        .build();
        fullscreenTask.isFocused = true;

        mFreeformTaskListener.onFocusTaskChanged(fullscreenTask);

        verify(mDesktopUserRepositories.getCurrent(), never())
                .addTask(fullscreenTask.displayId, fullscreenTask.taskId, fullscreenTask.isVisible,
                        fullscreenTask.configuration.windowConfiguration.getBounds());
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void visibilityTaskChanged_visible_setLaunchAdjacentDisabled() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;

        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        verify(mLaunchAdjacentController).setLaunchAdjacentEnabled(false);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void visibilityTaskChanged_notVisible_setLaunchAdjacentEnabled() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;

        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        task.isVisible = false;
        mFreeformTaskListener.onTaskInfoChanged(task);

        verify(mLaunchAdjacentController).setLaunchAdjacentEnabled(true);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    @DisableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    public void onTaskVanished_minimizedTask_noTransitionObservers_isNotRemoved() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;
        when(mDesktopRepository.isMinimizedTask(task.taskId)).thenReturn(true);

        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        task.isVisible = false;
        task.displayId = INVALID_DISPLAY;
        mFreeformTaskListener.onTaskVanished(task);

        verify(mDesktopUserRepositories.getCurrent(), never()).removeTask(task.taskId);
    }

    @Test
    @DisableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    public void onTaskVanished_closingTask_noTransitionObservers_isNotMinimized() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;

        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        when(mDesktopUserRepositories.getCurrent()
                .isClosingTask(task.taskId)).thenReturn(true);
        task.isVisible = false;
        task.displayId = INVALID_DISPLAY;
        mFreeformTaskListener.onTaskVanished(task);

        verify(mDesktopUserRepositories.getCurrent(), never())
                .minimizeTask(task.displayId, task.taskId);
        verify(mDesktopUserRepositories.getCurrent()).removeClosingTask(task.taskId);
        verify(mDesktopUserRepositories.getCurrent()).removeTask(task.taskId);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    public void onTaskVanished_usesTransitionObservers_noopInRepo() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        mFreeformTaskListener.onTaskVanished(task);

        verify(mDesktopUserRepositories.getCurrent(), never())
                .minimizeTask(task.displayId, task.taskId);
        verify(mDesktopUserRepositories.getCurrent(), never())
                .removeClosingTask(task.taskId);
        verify(mDesktopUserRepositories.getCurrent(), never()).removeTask(task.taskId);
    }

    @Test
    public void onTaskVanished_withDesktopModeLogger_forwards() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;
        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        mFreeformTaskListener.onTaskVanished(task);

        verify(mDesktopModeLoggerTransitionObserver).onTaskVanished(task);
    }


    @Test
    public void onTaskInfoChanged_withDesktopController_forwards() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;
        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        mFreeformTaskListener.onTaskInfoChanged(task);

        verify(mDesktopTasksController).onTaskInfoChanged(task);
    }

    @Test
    @DisableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    public void onTaskInfoChanged_noTransitionObservers_updatesTask() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;
        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        mFreeformTaskListener.onTaskInfoChanged(task);

        verify(mTaskChangeListener, never()).onTaskChanging(any());
        verify(mDesktopUserRepositories.getCurrent())
                .updateTask(task.displayId, task.taskId, task.isVisible,
                        task.configuration.windowConfiguration.getBounds()
                );
    }

    @Test
    @EnableFlags(FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS)
    @DisableFlags(FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION)
    public void onTaskInfoChanged_useTransitionObserver_noopInRepository() {
        ActivityManager.RunningTaskInfo task =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isVisible = true;
        mFreeformTaskListener.onTaskAppeared(task, mMockSurfaceControl);

        mFreeformTaskListener.onTaskInfoChanged(task);

        verify(mTaskChangeListener).onNonTransitionTaskChanging(any());
        verify(mDesktopUserRepositories.getCurrent(), never())
                .updateTask(task.displayId, task.taskId, task.isVisible,
                        task.configuration.windowConfiguration.getBounds()
                );
    }
}
