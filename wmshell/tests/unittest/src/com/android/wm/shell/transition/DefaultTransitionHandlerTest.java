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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_SLEEP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.window.TransitionInfo.FLAG_SYNC;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.animation.AlphaAnimation;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Tests for the default animation handler that is used if no other special-purpose handler picks
 * up an animation request.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:DefaultTransitionHandlerTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DefaultTransitionHandlerTest extends ShellTestCase {

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private final DisplayController mDisplayController = mock(DisplayController.class);
    private final DisplayInsetsController mDisplayInsetsController =
            mock(DisplayInsetsController.class);
    private final TransactionPool mTransactionPool = new MockTransactionPool();
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();
    private final TestShellExecutor mAnimExecutor = new TestShellExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private ShellInit mShellInit;
    private RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private DefaultTransitionHandler mTransitionHandler;

    static final String TAG = "DefaultHandlerTest";

    @Before
    public void setUp() {
        mShellInit = new ShellInit(mMainExecutor);
        mRootTaskDisplayAreaOrganizer = new RootTaskDisplayAreaOrganizer(
                mMainExecutor,
                mContext,
                mShellInit);
        mTransitionHandler = new DefaultTransitionHandler(
                mContext, mShellInit, mDisplayController, mDisplayInsetsController,
                mTransactionPool, mMainExecutor, mMainHandler, mAnimExecutor,
                mRootTaskDisplayAreaOrganizer, mock(InteractionJankMonitor.class));
        mShellInit.init();
    }

    @After
    public void tearDown() {
        flushHandlers();
    }

    private void flushHandlers() {
        mMainHandler.runWithScissors(() -> {
            mAnimExecutor.flushAll();
            mMainExecutor.flushAll();
        }, 1000L);
    }

    @Test
    public void testAnimationBackgroundCreatedForTaskTransition() {
        final TransitionInfo.Change openTask = new ChangeBuilder(TRANSIT_OPEN)
                .setTask(createTaskInfo(1))
                .build();
        final TransitionInfo.Change closeTask = new ChangeBuilder(TRANSIT_TO_BACK)
                .setTask(createTaskInfo(2))
                .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openTask)
                .addChange(closeTask)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        mergeSync(mTransitionHandler, token);
        flushHandlers();

        verify(startT).setColor(any(), any());
    }

    @Test
    public void testNoAnimationBackgroundForTranslucentTasks() {
        final TransitionInfo.Change openTask = new ChangeBuilder(TRANSIT_OPEN)
                .setTask(createTaskInfo(1))
                .setFlags(FLAG_TRANSLUCENT)
                .build();
        final TransitionInfo.Change closeTask = new ChangeBuilder(TRANSIT_TO_BACK)
                .setTask(createTaskInfo(2))
                .setFlags(FLAG_TRANSLUCENT)
                .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openTask)
                .addChange(closeTask)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        mergeSync(mTransitionHandler, token);
        flushHandlers();

        verify(startT, never()).setColor(any(), any());
    }

    @Test
    public void testNoAnimationBackgroundForWallpapers() {
        final TransitionInfo.Change openWallpaper = new ChangeBuilder(TRANSIT_OPEN)
                .setFlags(TransitionInfo.FLAG_IS_WALLPAPER)
                .build();
        final TransitionInfo.Change closeWallpaper = new ChangeBuilder(TRANSIT_TO_BACK)
                .setFlags(TransitionInfo.FLAG_IS_WALLPAPER)
                .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openWallpaper)
                .addChange(closeWallpaper)
                .build();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        mergeSync(mTransitionHandler, token);
        flushHandlers();

        verify(startT, never()).setColor(any(), any());
    }

    @Test
    public void testBuildSurfaceAnimation() {
        final ArrayList<Animator> animators = new ArrayList<>();
        final AlphaAnimation animation = new AlphaAnimation(0, 1);
        final long durationMs = 500;
        animation.setDuration(durationMs);
        final long[] lastCurrentPlayTime = new long[1];
        final int[] finishCount = new int[1];
        final Runnable finishCallback = () -> finishCount[0]++;
        DefaultSurfaceAnimator.buildSurfaceAnimation(animators, animation, finishCallback,
                mTransactionPool, mMainExecutor,
                new DefaultSurfaceAnimator.AnimationAdapter(mock(SurfaceControl.class)) {
                    @Override
                    void applyTransformation(ValueAnimator animator, long currentPlayTime) {
                        lastCurrentPlayTime[0] = currentPlayTime;
                    }
                });
        final ValueAnimator animator = (ValueAnimator) animators.get(0);
        mAnimExecutor.execute(() -> {
            animator.start();
            animator.end();
        });
        flushHandlers();
        assertEquals(durationMs, lastCurrentPlayTime[0]);
        assertEquals(1f, animator.getAnimatedFraction(), 0f /* delta */);
        assertEquals(1, finishCount[0]);
    }

    @Test
    public void startAnimation_freeformOpenChange_doesntReparentTask() {
        final TransitionInfo.Change openChange = new ChangeBuilder(TRANSIT_OPEN)
                .setTask(createTaskInfo(
                        /* taskId= */ 1, /* windowingMode= */ WINDOWING_MODE_FULLSCREEN))
                .build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openChange)
                .build();
        final IBinder token = new Binder();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        verify(startT, never()).reparent(any(), any());
    }

    @Test
    public void startAnimation_freeformMinimizeChange_underFullscreenChange_doesntReparentTask() {
        final TransitionInfo.Change openChange = new ChangeBuilder(TRANSIT_OPEN)
                .setTask(createTaskInfo(
                        /* taskId= */ 1, /* windowingMode= */ WINDOWING_MODE_FULLSCREEN))
                .build();
        final TransitionInfo.Change toBackChange = new ChangeBuilder(TRANSIT_TO_BACK)
                .setTask(createTaskInfo(
                        /* taskId= */ 2, /* windowingMode= */ WINDOWING_MODE_FREEFORM))
                .build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openChange)
                .addChange(toBackChange)
                .build();
        final IBinder token = new Binder();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        verify(startT, never()).reparent(any(), any());
    }

    @Test
    public void startAnimation_neverFindsErrors_animationMode() {
        final TransitionInfo.Change open = new ChangeBuilder(TRANSIT_OPEN).build();
        final TransitionInfo.Change close = new ChangeBuilder(TRANSIT_TO_BACK).build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(open)
                .addChange(close)
                .build();
        final IBinder token = mock(Binder.class);

        TransitionDispatchState dispatchState = new TransitionDispatchState(token, info);

        boolean hasPlayed = mTransitionHandler
                .startAnimation(token, info, dispatchState, MockTransactionPool.create(),
                        MockTransactionPool.create(),
                        mock(Transitions.TransitionFinishCallback.class));

        Log.v(TAG, "dispatchState: \n" + dispatchState.getDebugInfo());
        assertTrue(hasPlayed);
        assertFalse(dispatchState.hasErrors(mTransitionHandler));
    }

    @Test
    public void startAnimation_neverFindsErrors_dataCollectionMode() {
        final TransitionInfo.Change open = new ChangeBuilder(TRANSIT_OPEN).build();
        final TransitionInfo.Change close = new ChangeBuilder(TRANSIT_TO_BACK).build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(open)
                .addChange(close)
                .build();
        final IBinder token = mock(Binder.class);

        TransitionDispatchState dispatchState = new TransitionDispatchState(token, info);

        boolean hasPlayed = mTransitionHandler
                .startAnimation(token, null, dispatchState, MockTransactionPool.create(),
                        MockTransactionPool.create(),
                        mock(Transitions.TransitionFinishCallback.class));

        Log.v(TAG, "dispatchState: \n" + dispatchState.getDebugInfo());
        assertFalse(hasPlayed);
        assertFalse(dispatchState.hasErrors(mTransitionHandler));
    }

    @Test
    public void startAnimation_freeform_minimizeAnimation_reparentsTask() {
        final TransitionInfo.Change openChange = new ChangeBuilder(TRANSIT_OPEN)
                .setTask(createTaskInfo(
                        /* taskId= */ 1, /* windowingMode= */ WINDOWING_MODE_FREEFORM))
                .build();
        final TransitionInfo.Change toBackChange = new ChangeBuilder(TRANSIT_TO_BACK)
                .setTask(createTaskInfo(
                        /* taskId= */ 2, /* windowingMode= */ WINDOWING_MODE_FREEFORM))
                .build();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openChange)
                .addChange(toBackChange)
                .build();
        final IBinder token = new Binder();
        final SurfaceControl.Transaction startT = MockTransactionPool.create();
        final SurfaceControl.Transaction finishT = MockTransactionPool.create();

        mTransitionHandler.startAnimation(token, info, startT, finishT,
                mock(Transitions.TransitionFinishCallback.class));

        verify(startT).reparent(any(), any());
    }

    private static void mergeSync(Transitions.TransitionHandler handler, IBinder token) {
        handler.mergeAnimation(
                new Binder(),
                new TransitionInfoBuilder(TRANSIT_SLEEP, FLAG_SYNC).build(),
                MockTransactionPool.create(),
                MockTransactionPool.create(),
                token,
                mock(Transitions.TransitionFinishCallback.class));
    }

    private static RunningTaskInfo createTaskInfo(int taskId) {
        return createTaskInfo(taskId, WINDOWING_MODE_FULLSCREEN);
    }

    private static RunningTaskInfo createTaskInfo(int taskId, int windowingMode) {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.topActivityType = ACTIVITY_TYPE_STANDARD;
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);
        taskInfo.configuration.windowConfiguration.setActivityType(taskInfo.topActivityType);
        taskInfo.token = mock(WindowContainerToken.class);
        return taskInfo;
    }
}
