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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.TRANSIT_CHANGE;

import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP_TO_SPLIT;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.VerificationKt.times;
import static org.mockito.kotlin.VerificationKt.verify;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.IBinder;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.Flags;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipExpandAnimator;
import com.android.wm.shell.pip2.phone.PipInteractionHandler;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.util.StubTransaction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;
import java.util.Optional;

/**
 * Unit test against {@link PipExpandHandler}
 */

@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(ParameterizedAndroidJunit4.class)
public class PipExpandHandlerTest {
    @Mock private Context mMockContext;
    @Mock private PipBoundsState mMockPipBoundsState;
    @Mock private PipBoundsAlgorithm mMockPipBoundsAlgorithm;
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private PipDisplayLayoutState mMockPipDisplayLayoutState;
    @Mock private PipDesktopState mMockPipDesktopState;
    @Mock private PipInteractionHandler mMockPipInteractionHandler;
    @Mock private SplitScreenController mMockSplitScreenController;

    @Mock private IBinder mMockTransitionToken;
    @Mock private TransitionRequestInfo mMockRequestInfo;
    @Mock private StubTransaction mStartT;
    @Mock private StubTransaction mFinishT;
    @Mock private SurfaceControl mPipLeash;

    @Mock private PipExpandAnimator mMockPipExpandAnimator;

    @Captor private ArgumentCaptor<Runnable> mAnimatorCallbackArgumentCaptor;

    @Surface.Rotation
    private static final int DISPLAY_ROTATION = Surface.ROTATION_0;

    private static final float SNAP_FRACTION = 1.5f;
    private static final Rect PIP_BOUNDS = new Rect(0, 0, 100, 100);
    private static final Rect DISPLAY_BOUNDS = new Rect(0, 0, 1000, 1000);
    private static final Rect RIGHT_HALF_DISPLAY_BOUNDS = new Rect(500, 0, 1000, 1000);

    private PipExpandHandler mPipExpandHandler;

    @Mock
    private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_PIP_BOX_SHADOWS);
    }

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    public PipExpandHandlerTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockPipBoundsState.getBounds()).thenReturn(PIP_BOUNDS);
        when(mMockPipBoundsAlgorithm.getSnapFraction(eq(PIP_BOUNDS))).thenReturn(SNAP_FRACTION);
        when(mMockPipDisplayLayoutState.getRotation()).thenReturn(DISPLAY_ROTATION);
        when(mMockContext.getResources()).thenReturn(mock(Resources.class));

        mPipExpandHandler = new PipExpandHandler(mMockContext,
                mPipSurfaceTransactionHelper,
                mMockPipBoundsState,
                mMockPipBoundsAlgorithm, mMockPipTransitionState, mMockPipDisplayLayoutState,
                mMockPipDesktopState, mMockPipInteractionHandler,
                Optional.of(mMockSplitScreenController));
        mPipExpandHandler.setPipExpandAnimatorSupplier(
                (context, pipSurfaceTransactionHelper, leash, startTransaction,
                finishTransaction, baseBounds, startBounds, endBounds,
                sourceRectHint, rotation, isPipInDesktopMode) -> mMockPipExpandAnimator);
    }

    @Test
    public void handleRequest_returnNull() {
        // All expand from PiP transitions are started in Shell, so handleRequest shouldn't be
        // returning any non-null WCT
        WindowContainerTransaction wct = mPipExpandHandler.handleRequest(
                mMockTransitionToken, mMockRequestInfo);
        assertNull(wct);
    }

    @Test
    public void startAnimation_transitExit_startExpandAnimator() {
        final ActivityManager.RunningTaskInfo pipTaskInfo = createPipTaskInfo(
                1, WINDOWING_MODE_FULLSCREEN, new PictureInPictureParams.Builder().build());

        final TransitionInfo info = getExpandFromPipTransitionInfo(
                TRANSIT_EXIT_PIP, pipTaskInfo, null /* lastParent */, false /* toSplit */);
        final WindowContainerToken pipToken = pipTaskInfo.getToken();
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(pipToken);

        mPipExpandHandler.startAnimation(mMockTransitionToken, info, mStartT, mFinishT,
                (wct) -> {});

        verify(mMockPipExpandAnimator, times(1)).start();
        verify(mMockPipBoundsState, times(1)).saveReentryState(SNAP_FRACTION);

        verify(mMockPipExpandAnimator, times(1))
                .setAnimationStartCallback(mAnimatorCallbackArgumentCaptor.capture());
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(mAnimatorCallbackArgumentCaptor.getValue());
        verify(mMockPipInteractionHandler, times(1)).begin(any(),
                eq(PipInteractionHandler.INTERACTION_EXIT_PIP));
    }

    @Test
    public void startAnimation_transitExitToSplit_startExpandAnimator() {
        // The task info of the task that was pinned while we were in PiP.
        final WindowContainerToken pipToken = createPipTaskInfo(1, WINDOWING_MODE_FULLSCREEN,
                new PictureInPictureParams.Builder().build()).getToken();
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(pipToken);

        // Change representing the ActivityRecord we are animating in the multi-activity PiP case;
        // make sure change's taskInfo=null as this is an activity, but let lastParent be PiP token.
        final TransitionInfo info = getExpandFromPipTransitionInfo(
                TRANSIT_EXIT_PIP_TO_SPLIT, null /* taskInfo */, pipToken, true /* toSplit */);

        mPipExpandHandler.startAnimation(mMockTransitionToken, info, mStartT, mFinishT,
                (wct) -> {});

        verify(mMockSplitScreenController, times(1)).finishEnterSplitScreen(eq(mFinishT));
        verify(mMockPipExpandAnimator, times(1)).start();
        verify(mMockPipBoundsState, times(1)).saveReentryState(SNAP_FRACTION);

        verify(mMockPipExpandAnimator, times(1))
                .setAnimationStartCallback(mAnimatorCallbackArgumentCaptor.capture());
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(mAnimatorCallbackArgumentCaptor.getValue());
        verify(mMockPipInteractionHandler, times(1)).begin(any(),
                eq(PipInteractionHandler.INTERACTION_EXIT_PIP_TO_SPLIT));
    }

    private TransitionInfo getExpandFromPipTransitionInfo(@WindowManager.TransitionType int type,
            @Nullable ActivityManager.RunningTaskInfo pipTaskInfo,
            @Nullable WindowContainerToken lastParent, boolean toSplit) {
        final TransitionInfo info = new TransitionInfoBuilder(type)
                .addChange(TRANSIT_CHANGE, pipTaskInfo).build();
        final TransitionInfo.Change pipChange = info.getChanges().getFirst();
        pipChange.setRotation(DISPLAY_ROTATION,
                WindowConfiguration.ROTATION_UNDEFINED);
        pipChange.setStartAbsBounds(PIP_BOUNDS);
        pipChange.setEndAbsBounds(toSplit ? RIGHT_HALF_DISPLAY_BOUNDS : DISPLAY_BOUNDS);
        pipChange.setLeash(mPipLeash);
        pipChange.setLastParent(lastParent);
        return info;
    }

    private static ActivityManager.RunningTaskInfo createPipTaskInfo(int taskId,
            int windowingMode, PictureInPictureParams params) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);
        taskInfo.token = mock(WindowContainerToken.class);
        taskInfo.baseIntent = mock(Intent.class);
        taskInfo.pictureInPictureParams = params;
        return taskInfo;
    }
}
