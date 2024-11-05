/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.desktopmode;


import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread;

import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.IWindowContainerToken;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.function.Supplier;

/** Tests of {@link com.android.wm.shell.desktopmode.ExitDesktopTaskTransitionHandler} */
@SmallTest
public class ExitDesktopTaskTransitionHandlerTest extends ShellTestCase {

    @Mock
    private Transitions mTransitions;
    @Mock
    IBinder mToken;
    @Mock
    Supplier<SurfaceControl.Transaction> mTransactionFactory;
    @Mock
    Context mContext;
    @Mock
    DisplayMetrics mDisplayMetrics;
    @Mock
    Resources mResources;
    @Mock
    Transitions.TransitionFinishCallback mTransitionFinishCallback;
    @Mock
    ShellExecutor mExecutor;

    private Point mPoint;
    private ExitDesktopTaskTransitionHandler mExitDesktopTaskTransitionHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        doReturn(mExecutor).when(mTransitions).getMainExecutor();
        doReturn(new SurfaceControl.Transaction()).when(mTransactionFactory).get();
        doReturn(mResources).when(mContext).getResources();
        doReturn(mDisplayMetrics).when(mResources).getDisplayMetrics();
        when(mResources.getDisplayMetrics())
                .thenReturn(getContext().getResources().getDisplayMetrics());

        mExitDesktopTaskTransitionHandler = new ExitDesktopTaskTransitionHandler(mTransitions,
                mContext);
        mPoint = new Point(0, 0);
    }

    @Test
    public void testTransitExitDesktopModeAnimation() throws Throwable {
        final int transitionType = TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN;
        final int taskId = 1;
        WindowContainerTransaction wct = new WindowContainerTransaction();
        doReturn(mToken).when(mTransitions)
                .startTransition(transitionType, wct, mExitDesktopTaskTransitionHandler);

        mExitDesktopTaskTransitionHandler.startTransition(DesktopModeTransitionSource.UNKNOWN,
                wct, mPoint, null);

        TransitionInfo.Change change =
                createChange(WindowManager.TRANSIT_CHANGE, taskId, WINDOWING_MODE_FULLSCREEN);
        TransitionInfo info = createTransitionInfo(TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN, change);
        ArrayList<Exception> exceptions = new ArrayList<>();
        runOnUiThread(() -> {
            try {
                assertTrue(mExitDesktopTaskTransitionHandler
                        .startAnimation(mToken, info,
                                new SurfaceControl.Transaction(),
                                new SurfaceControl.Transaction(),
                                mTransitionFinishCallback));
            } catch (Exception e) {
                exceptions.add(e);
            }
        });
        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }
    }

    private TransitionInfo.Change createChange(@WindowManager.TransitionType int type, int taskId,
            @WindowConfiguration.WindowingMode int windowingMode) {
        final ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.token = new WindowContainerToken(mock(IWindowContainerToken.class));
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);
        SurfaceControl.Builder b = new SurfaceControl.Builder()
                .setName("test task");
        final TransitionInfo.Change change = new TransitionInfo.Change(
                taskInfo.token, b.build());
        change.setMode(type);
        change.setTaskInfo(taskInfo);
        return change;
    }

    private static TransitionInfo createTransitionInfo(
            @WindowManager.TransitionType int type, @NonNull TransitionInfo.Change change) {
        TransitionInfo info = new TransitionInfo(type, 0);
        info.addChange(change);
        return info;
    }

}
