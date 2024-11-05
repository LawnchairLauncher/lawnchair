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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.desktopmode.DesktopModeTaskRepository;
import com.android.wm.shell.shared.DesktopModeStatus;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.Optional;

/**
 * Tests for {@link FreeformTaskListener}
 * Build/Install/Run:
 * atest WMShellUnitTests:FreeformTaskListenerTests
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
    private DesktopModeTaskRepository mDesktopModeTaskRepository;
    private FreeformTaskListener mFreeformTaskListener;
    private StaticMockitoSession mMockitoSession;

    @Before
    public void setup() {
        mMockitoSession = mockitoSession().initMocks(this)
                .strictness(Strictness.LENIENT).mockStatic(DesktopModeStatus.class).startMocking();
        doReturn(true).when(() -> DesktopModeStatus.canEnterDesktopMode(any()));

        mFreeformTaskListener = new FreeformTaskListener(
                mContext,
                mShellInit,
                mTaskOrganizer,
                Optional.of(mDesktopModeTaskRepository),
                mWindowDecorViewModel);
    }

    @Test
    public void testFocusTaskChanged_freeformTaskIsAddedToRepo() {
        ActivityManager.RunningTaskInfo task = new TestRunningTaskInfoBuilder()
                .setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        task.isFocused = true;

        mFreeformTaskListener.onFocusTaskChanged(task);

        verify(mDesktopModeTaskRepository)
            .addOrMoveFreeformTaskToTop(task.displayId, task.taskId);
    }

    @Test
    public void testFocusTaskChanged_fullscreenTaskIsNotAddedToRepo() {
        ActivityManager.RunningTaskInfo fullscreenTask = new TestRunningTaskInfoBuilder()
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN).build();
        fullscreenTask.isFocused = true;

        mFreeformTaskListener.onFocusTaskChanged(fullscreenTask);

        verify(mDesktopModeTaskRepository, never())
                .addOrMoveFreeformTaskToTop(fullscreenTask.displayId, fullscreenTask.taskId);
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }
}
