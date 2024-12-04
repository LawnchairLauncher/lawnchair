/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.IWindowContainerToken;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests of {@link FreeformTaskTransitionObserver}
 */
@SmallTest
public class FreeformTaskTransitionObserverTest {

    @Mock
    private ShellInit mShellInit;
    @Mock
    private Transitions mTransitions;
    @Mock
    private WindowDecorViewModel mWindowDecorViewModel;

    private FreeformTaskTransitionObserver mTransitionObserver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        PackageManager pm = mock(PackageManager.class);
        doReturn(true).when(pm).hasSystemFeature(
                PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT);
        final Context context = mock(Context.class);
        doReturn(pm).when(context).getPackageManager();

        mTransitionObserver = new FreeformTaskTransitionObserver(
                context, mShellInit, mTransitions, mWindowDecorViewModel);
        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            final ArgumentCaptor<Runnable> initRunnableCaptor = ArgumentCaptor.forClass(
                    Runnable.class);
            verify(mShellInit).addInitCallback(initRunnableCaptor.capture(),
                    same(mTransitionObserver));
            initRunnableCaptor.getValue().run();
        } else {
            mTransitionObserver.onInit();
        }
    }

    @Test
    public void testRegistersObserverAtInit() {
        verify(mTransitions).registerObserver(same(mTransitionObserver));
    }

    @Test
    public void testCreatesWindowDecorOnOpenTransition_freeform() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mWindowDecorViewModel).onTaskOpening(
                change.getTaskInfo(), change.getLeash(), startT, finishT);
    }

    @Test
    public void testPreparesWindowDecorOnCloseTransition_freeform() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mWindowDecorViewModel).onTaskClosing(
                change.getTaskInfo(), startT, finishT);
    }

    @Test
    public void testDoesntCloseWindowDecorDuringCloseTransition() throws Exception {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mWindowDecorViewModel, never()).destroyWindowDecoration(change.getTaskInfo());
    }

    @Test
    public void testClosesWindowDecorAfterCloseTransition() throws Exception {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(change).build();

        final AutoCloseable windowDecor = mock(AutoCloseable.class);

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);
        mTransitionObserver.onTransitionFinished(transition, false);

        verify(mWindowDecorViewModel).destroyWindowDecoration(change.getTaskInfo());
    }

    @Test
    public void testClosesMergedWindowDecorationAfterTransitionFinishes() throws Exception {
        // The playing transition
        final TransitionInfo.Change change1 =
                createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info1 = new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(change1).build();

        final IBinder transition1 = mock(IBinder.class);
        final SurfaceControl.Transaction startT1 = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT1 = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition1, info1, startT1, finishT1);
        mTransitionObserver.onTransitionStarting(transition1);

        // The merged transition
        final TransitionInfo.Change change2 =
                createChange(TRANSIT_CLOSE, 2, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(change2).build();

        final IBinder transition2 = mock(IBinder.class);
        final SurfaceControl.Transaction startT2 = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT2 = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition2, info2, startT2, finishT2);
        mTransitionObserver.onTransitionMerged(transition2, transition1);

        mTransitionObserver.onTransitionFinished(transition1, false);

        verify(mWindowDecorViewModel).destroyWindowDecoration(change2.getTaskInfo());
    }

    @Test
    public void testClosesAllWindowDecorsOnTransitionMergeAfterCloseTransitions() throws Exception {
        // The playing transition
        final TransitionInfo.Change change1 =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info1 = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(change1).build();

        final IBinder transition1 = mock(IBinder.class);
        final SurfaceControl.Transaction startT1 = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT1 = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition1, info1, startT1, finishT1);
        mTransitionObserver.onTransitionStarting(transition1);

        // The merged transition
        final TransitionInfo.Change change2 =
                createChange(TRANSIT_CLOSE, 2, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE, 0)
                .addChange(change2).build();

        final IBinder transition2 = mock(IBinder.class);
        final SurfaceControl.Transaction startT2 = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT2 = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition2, info2, startT2, finishT2);
        mTransitionObserver.onTransitionMerged(transition2, transition1);

        mTransitionObserver.onTransitionFinished(transition1, false);

        verify(mWindowDecorViewModel).destroyWindowDecoration(change1.getTaskInfo());
        verify(mWindowDecorViewModel).destroyWindowDecoration(change2.getTaskInfo());
    }

    private static TransitionInfo.Change createChange(int mode, int taskId, int windowingMode) {
        final ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);

        final TransitionInfo.Change change = new TransitionInfo.Change(
                new WindowContainerToken(mock(IWindowContainerToken.class)),
                mock(SurfaceControl.class));
        change.setMode(mode);
        change.setTaskInfo(taskInfo);
        return change;
    }
}
