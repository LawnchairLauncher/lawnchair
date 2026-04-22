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

package com.android.wm.shell.pip2.phone.transition;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.WindowManager.TRANSIT_OPEN;

import static com.android.wm.shell.pip2.phone.PipTransitionState.ENTERED_PIP;
import static com.android.wm.shell.pip2.phone.PipTransitionState.ENTERING_PIP;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.VerificationKt.never;
import static org.mockito.kotlin.VerificationKt.times;

import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.R;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipResizeAnimator;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.transition.Transitions;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test against {@link ContentPipHandler}
 */

@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner.class)
public class ContentPipHandlerTest {
    @Mock private Context mContext;
    @Mock private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    @Mock private PipTransitionState mPipTransitionState;
    @Mock private SurfaceControl mPipLeash;
    @Mock private PipResizeAnimator mPipResizeAnimator;

    @Mock private SurfaceControl.Transaction mStartTx;
    @Mock private SurfaceControl.Transaction mFinishTx;

    private static final Rect APP_BOUNDS = new Rect(0, 0, 500, 1000);
    private static final Rect SOURCE_RECT = new Rect(0, 0, 500, 500);
    private static final Rect END_BOUNDS = new Rect(0, 0, 100, 100);
    private static final int ENTER_DURATION = 100;

    private ContentPipHandler mContentPipHandler;
    @Captor
    private ArgumentCaptor<Runnable> mAnimatorCallbackArgumentCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        final Resources res = mock(Resources.class);
        when(res.getInteger(eq(R.integer.config_pipEnterAnimationDuration)))
                .thenReturn(ENTER_DURATION);
        when(mContext.getResources()).thenReturn(res);

        mContentPipHandler = new ContentPipHandler(mContext, mPipSurfaceTransactionHelper,
                mPipTransitionState);
        mContentPipHandler.setPipResizeAnimatorSupplier((context, pipSurfaceTransactionHelper,
                leash, startTransaction, finishTransaction, baseBounds, startBounds, endBounds,
                duration, delta) -> mPipResizeAnimator);

        when(mStartTx.setWindowCrop(any(SurfaceControl.class),
                any(Rect.class))).thenReturn(mStartTx);
        when(mFinishTx.setWindowCrop(any(SurfaceControl.class),
                any(Rect.class))).thenReturn(mFinishTx);
    }

    @Test
    public void startAnimation_enterContentPip_resizeIntoPipBounds() {
        // set the state to ENTERING_PIP
        when(mPipTransitionState.getState()).thenReturn(ENTERING_PIP);

        // create transition info with a content PiP change that has a valid src-rect-hint
        final PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setSourceRectHint(SOURCE_RECT).build();
        final ActivityManager.RunningTaskInfo taskInfo =
                createPipTaskInfo(1, 2, params);
        final TransitionInfo info = getEnterPipTransitionInfo(taskInfo, APP_BOUNDS, END_BOUNDS);

        final IBinder transitionToken = new Binder();
        final Transitions.TransitionFinishCallback finishCallback =
                mock(Transitions.TransitionFinishCallback.class);
        mContentPipHandler.startAnimation(transitionToken, info, mStartTx, mFinishTx,
                finishCallback);

        verify(mStartTx, times(1)).setWindowCrop(eq(mPipLeash),
                eq(END_BOUNDS.width()), eq(END_BOUNDS.height()));
        verify(mFinishTx, times(1)).setWindowCrop(eq(mPipLeash),
                eq(END_BOUNDS.width()), eq(END_BOUNDS.height()));

        // check whether finish callback is scheduled to run at the end.
        verify(mPipResizeAnimator, times(1))
                .setAnimationEndCallback(mAnimatorCallbackArgumentCaptor.capture());
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(mAnimatorCallbackArgumentCaptor.getValue());

        verify(mPipTransitionState, times(1)).setState(ENTERED_PIP);
        verify(finishCallback, times(1)).onTransitionFinished(isNull());

        // verify that the animator actually starts
        verify(mPipResizeAnimator, times(1)).start();
    }

    @Test
    public void startAnimation_enterNonContentPip_returnFalse() {
        // set the state to ENTERING_PIP
        when(mPipTransitionState.getState()).thenReturn(ENTERING_PIP);

        // create transition info with a content PiP change that has a valid src-rect-hint
        final PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setSourceRectHint(SOURCE_RECT).build();
        final ActivityManager.RunningTaskInfo taskInfo =
                createPipTaskInfo(1, -1, params);
        final TransitionInfo info = getEnterPipTransitionInfo(taskInfo, APP_BOUNDS, END_BOUNDS);

        final IBinder transitionToken = new Binder();
        final Transitions.TransitionFinishCallback finishCallback =
                mock(Transitions.TransitionFinishCallback.class);
        final boolean didHandle = mContentPipHandler.startAnimation(transitionToken,
                info, mStartTx, mFinishTx, finishCallback);

        Assert.assertFalse(didHandle);

        verify(mPipTransitionState, never()).setState(ENTERED_PIP);
        verify(finishCallback, never()).onTransitionFinished(isNull());

        // verify that the animator actually starts
        verify(mPipResizeAnimator, never()).start();
    }

    private TransitionInfo getEnterPipTransitionInfo(
            ActivityManager.RunningTaskInfo pipTaskInfo, Rect startBounds, Rect endBounds) {
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, pipTaskInfo).build();
        final TransitionInfo.Change pipChange = info.getChanges().getFirst();
        pipChange.setStartAbsBounds(startBounds);
        pipChange.setEndAbsBounds(endBounds);
        pipChange.setLeash(mPipLeash);
        return info;
    }

    private static ActivityManager.RunningTaskInfo createPipTaskInfo(int taskId,
            int launchIntoPipHostTaskId,
            PictureInPictureParams params) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.launchIntoPipHostTaskId = launchIntoPipHostTaskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_PINNED);
        taskInfo.pictureInPictureParams = params;
        return taskInfo;
    }
}
