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

package com.android.wm.shell.pip2.phone;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.MatchersKt.eq;
import static org.mockito.kotlin.VerificationKt.times;
import static org.mockito.kotlin.VerificationKt.verify;

import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.window.DisplayAreaInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.Flags;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.desktopmode.DesktopPipTransitionController;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.util.StubTransaction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;
import java.util.Optional;


/**
 * Unit test against {@link PipScheduler}
 */

@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(ParameterizedAndroidJunit4.class)
public class PipSchedulerTest {
    private static final int TEST_BOUNDS_CHANGE_DURATION = 1;
    private static final Rect TEST_STARTING_BOUNDS = new Rect(0, 0, 10, 10);
    private static final Rect TEST_BOUNDS = new Rect(0, 0, 20, 20);
    private static final int DEFAULT_DISPLAY_ID = 0;
    private static final int EXTERNAL_DISPLAY_ID = 0;
    private static final int SECONDARY_DISPLAY_ID = 2;
    private static final int DEFAULT_DPI = 250;
    private final SurfaceControl mTestLeash = new SurfaceControl.Builder()
            .setContainerLayer()
            .setName("PipSchedulerTest")
            .setCallsite("PipSchedulerTest")
            .build();

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private PipBoundsState mMockPipBoundsState;
    @Mock private ShellExecutor mMockMainExecutor;
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private PipDesktopState mMockPipDesktopState;
    @Mock private DisplayController mDisplayController;
    @Mock private PipTransitionController mMockPipTransitionController;
    @Mock private RootTaskDisplayAreaOrganizer mMockRootTaskDisplayAreaOrganizer;
    @Mock private Runnable mMockUpdateMovementBoundsRunnable;
    @Mock private WindowContainerToken mMockPipTaskToken;
    @Mock private PipSurfaceTransactionHelper.SurfaceControlTransactionFactory mMockFactory;
    @Mock private SurfaceControl.Transaction mMockTransaction;
    @Mock private PipAlphaAnimator mMockAlphaAnimator;
    @Mock private SplitScreenController mMockSplitScreenController;
    @Mock private DesktopPipTransitionController mMockDesktopPipTransitionController;
    @Mock private SurfaceControl mMockLeash;
    @Mock private DisplayLayout mMockDisplayLayout;
    @Mock private PipDisplayLayoutState mMockDisplayLayoutState;

    @Captor private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;
    @Captor private ArgumentCaptor<WindowContainerTransaction> mWctArgumentCaptor;

    private PipScheduler mPipScheduler;
    private DisplayAreaInfo mDisplayAreaInfo;

    @Mock
    private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;


    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_PIP_BOX_SHADOWS);
    }

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    public PipSchedulerTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDisplayAreaInfo = new DisplayAreaInfo(mMockPipTaskToken,
                DEFAULT_DISPLAY_ID, /* featureId= */ 0);

        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getInteger(anyInt())).thenReturn(0);
        when(mMockPipBoundsState.getBounds()).thenReturn(TEST_STARTING_BOUNDS);
        when(mMockFactory.getTransaction()).thenReturn(mMockTransaction);
        when(mMockTransaction.setMatrix(any(SurfaceControl.class), any(Matrix.class), any()))
                .thenReturn(mMockTransaction);
        when(mMockRootTaskDisplayAreaOrganizer.getDisplayAreaInfo(anyInt())).thenReturn(
                mDisplayAreaInfo);
        when(mMockPipDesktopState.getRootTaskDisplayAreaOrganizer()).thenReturn(
                mMockRootTaskDisplayAreaOrganizer);
        when(mMockDisplayLayout.densityDpi()).thenReturn(DEFAULT_DPI);
        when(mDisplayController.getDisplayLayout(anyInt())).thenReturn(mMockDisplayLayout);
        mPipScheduler = new PipScheduler(mMockContext,
                mPipSurfaceTransactionHelper, mMockPipBoundsState,
                mMockMainExecutor,
                mMockPipTransitionState, Optional.of(mMockSplitScreenController),
                Optional.of(mMockDesktopPipTransitionController), mMockPipDesktopState,
                mDisplayController, mMockDisplayLayoutState);
        mPipScheduler.setPipTransitionController(mMockPipTransitionController);
        mPipScheduler.setSurfaceControlTransactionFactory(mMockFactory);
        mPipScheduler.setPipAlphaAnimatorSupplier(
                (context, pipSurfaceTransactionHelper, leash, startTx, finishTx, direction) ->
                mMockAlphaAnimator);
        final PictureInPictureParams params = new PictureInPictureParams.Builder().build();
        mPipScheduler.setPipParamsSupplier(() -> params);
        when(mMockPipTransitionState.getPinnedTaskLeash()).thenReturn(mTestLeash);
        // PiP is in a valid state by default.
        when(mMockPipTransitionState.isInPip()).thenReturn(true);
        when(mMockDisplayLayoutState.getDisplayId()).thenReturn(DEFAULT_DISPLAY_ID);
    }

    @Test
    public void scheduleExitPipViaExpand_nullTaskToken_noop() {
        setNullPipTaskToken();

        mPipScheduler.scheduleExitPipViaExpand();

        verify(mMockMainExecutor, times(1)).execute(mRunnableArgumentCaptor.capture());
        assertNotNull(mRunnableArgumentCaptor.getValue());
        mRunnableArgumentCaptor.getValue().run();

        verify(mMockPipTransitionController, never()).startExpandTransition(any(), anyBoolean());
    }

    @Test
    public void scheduleExitPipViaExpand_noSplit_expandTransitionCalled() {
        setMockPipTaskToken();
        ActivityManager.RunningTaskInfo pipTaskInfo = getTaskInfoWithLastParentBeforePip(1);
        when(mMockPipTransitionState.getPipTaskInfo()).thenReturn(pipTaskInfo);

        // Make sure task with the id = 1 isn't in split-screen.
        when(mMockSplitScreenController.isTaskInSplitScreen(
                ArgumentMatchers.eq(1))).thenReturn(false);

        mPipScheduler.scheduleExitPipViaExpand();

        verify(mMockMainExecutor, times(1)).execute(mRunnableArgumentCaptor.capture());
        assertNotNull(mRunnableArgumentCaptor.getValue());
        mRunnableArgumentCaptor.getValue().run();

        verify(mMockPipTransitionController, times(1)).startExpandTransition(any(), anyBoolean());
    }

    @Test
    public void scheduleExitPipViaExpand_lastParentInSplit_prepareSplitAndExpand() {
        setMockPipTaskToken();
        ActivityManager.RunningTaskInfo pipTaskInfo = getTaskInfoWithLastParentBeforePip(1);
        when(mMockPipTransitionState.getPipTaskInfo()).thenReturn(pipTaskInfo);

        // Make sure task with the id = 1 is in split-screen.
        when(mMockSplitScreenController.isTaskInSplitScreen(
                ArgumentMatchers.eq(1))).thenReturn(true);

        mPipScheduler.scheduleExitPipViaExpand();

        verify(mMockMainExecutor, times(1)).execute(mRunnableArgumentCaptor.capture());
        assertNotNull(mRunnableArgumentCaptor.getValue());
        mRunnableArgumentCaptor.getValue().run();

        // We need to both prepare the split screen with the last parent and start expanding.
        verify(mMockSplitScreenController,
                times(1)).prepareEnterSplitScreen(any(), any(), anyInt());
        verify(mMockPipTransitionController, times(1)).startExpandTransition(any(), anyBoolean());
    }

    @Test
    public void removePipAfterAnimation() {
        setMockPipTaskToken();
        ActivityManager.RunningTaskInfo pipTaskInfo = getTaskInfoWithLastParentBeforePip(1);
        when(mMockPipTransitionState.getPipTaskInfo()).thenReturn(pipTaskInfo);

        mPipScheduler.scheduleRemovePip(true /* withFadeout */);

        verify(mMockMainExecutor, times(1)).execute(mRunnableArgumentCaptor.capture());
        assertNotNull(mRunnableArgumentCaptor.getValue());
        mRunnableArgumentCaptor.getValue().run();

        verify(mMockPipTransitionController, times(1)).startRemoveTransition(
                any(WindowContainerTransaction.class), eq(true) /* withFadeout */);
    }

    @Test
    public void scheduleAnimateResizePip_bounds_nullTaskToken_noop() {
        setNullPipTaskToken();

        mPipScheduler.scheduleAnimateResizePip(TEST_BOUNDS);

        verify(mMockPipTransitionController, never()).startPipBoundsChangeTransition(any(),
                anyInt());
    }

    @Test
    public void scheduleAnimateResizePip_boundsConfig_nullTaskToken_noop() {
        setNullPipTaskToken();

        mPipScheduler.scheduleAnimateResizePip(TEST_BOUNDS, true);

        verify(mMockPipTransitionController, never()).startPipBoundsChangeTransition(any(),
                anyInt());
    }

    @Test
    public void scheduleAnimateResizePip_boundsConfig_setsConfigAtEnd() {
        setMockPipTaskToken();

        mPipScheduler.scheduleAnimateResizePip(TEST_BOUNDS, true);

        verify(mMockPipTransitionController, times(1))
                .startPipBoundsChangeTransition(mWctArgumentCaptor.capture(), anyInt());
        assertNotNull(mWctArgumentCaptor.getValue());
        assertNotNull(mWctArgumentCaptor.getValue().getChanges());
        boolean hasConfigAtEndChange = false;
        for (WindowContainerTransaction.Change change :
                mWctArgumentCaptor.getValue().getChanges().values()) {
            if (change.getConfigAtTransitionEnd()) {
                hasConfigAtEndChange = true;
                break;
            }
        }
        assertTrue(hasConfigAtEndChange);
    }

    @Test
    public void scheduleAnimateResizePip_boundsConfigDuration_nullTaskToken_noop() {
        setNullPipTaskToken();

        mPipScheduler.scheduleAnimateResizePip(TEST_BOUNDS, true, TEST_BOUNDS_CHANGE_DURATION);

        verify(mMockPipTransitionController, never()).startPipBoundsChangeTransition(any(),
                anyInt());
    }

    @Test
    public void scheduleAnimateResizePip_notInPip_noop() {
        setMockPipTaskToken();
        when(mMockPipTransitionState.isInPip()).thenReturn(false);

        mPipScheduler.scheduleAnimateResizePip(TEST_BOUNDS, true, TEST_BOUNDS_CHANGE_DURATION);

        verify(mMockPipTransitionController, never()).startPipBoundsChangeTransition(any(),
                anyInt());
    }

    @Test
    public void scheduleAnimateResizePip_resizeTransition() {
        setMockPipTaskToken();

        mPipScheduler.scheduleAnimateResizePip(TEST_BOUNDS, true, TEST_BOUNDS_CHANGE_DURATION);

        verify(mMockPipTransitionController, times(1))
                .startPipBoundsChangeTransition(any(), eq(TEST_BOUNDS_CHANGE_DURATION));
    }

    @Test
    public void scheduleUserResizePip_emptyBounds_noop() {
        setMockPipTaskToken();

        mPipScheduler.scheduleUserResizePip(new Rect());

        verify(mMockTransaction, never()).apply();
    }

    @Test
    public void scheduleUserResizePip_rotation_emptyBounds_noop() {
        setMockPipTaskToken();

        mPipScheduler.scheduleUserResizePip(new Rect(), 90);

        verify(mMockTransaction, never()).apply();
    }

    @Test
    public void scheduleUserResizePip_applyTransaction() {
        setMockPipTaskToken();

        mPipScheduler.scheduleUserResizePip(TEST_BOUNDS, 90);

        verify(mMockTransaction, times(1)).apply();
    }

    @Test
    public void scheduleUserResizePip_differentFocusDisplayId_reparentsLeashToDisplay() {
        setMockPipTaskToken();
        when(mMockPipDesktopState.isDraggingPipAcrossDisplaysEnabled()).thenReturn(true);

        mPipScheduler.scheduleUserResizePip(TEST_BOUNDS, SECONDARY_DISPLAY_ID);

        verify(mMockRootTaskDisplayAreaOrganizer, times(1)).reparentToDisplayArea(
                eq(SECONDARY_DISPLAY_ID), eq(mTestLeash), eq(mMockTransaction));
    }

    @Test
    public void scheduleMoveToDisplay_startsResizeTransition() {
        setMockPipTaskToken();

        mPipScheduler.scheduleMoveToDisplay(EXTERNAL_DISPLAY_ID, TEST_BOUNDS);

        verify(mMockPipTransitionController, times(1))
                .startPipBoundsChangeTransition(mWctArgumentCaptor.capture(), anyInt());
        assertNotNull(mWctArgumentCaptor.getValue());
        assertNotNull(mWctArgumentCaptor.getValue().getChanges());
    }

    @Test
    public void finishResize_movementBoundsRunnableCalled() {
        mPipScheduler.setUpdateMovementBoundsRunnable(mMockUpdateMovementBoundsRunnable);
        mPipScheduler.scheduleFinishPipBoundsChange(TEST_BOUNDS);

        verify(mMockUpdateMovementBoundsRunnable, times(1)).run();
    }

    @Test
    public void finishResize_nonSeamless_alphaAnimatorStarted() {
        final PictureInPictureParams params =
                new PictureInPictureParams.Builder().setSeamlessResizeEnabled(false).build();
        mPipScheduler.setPipParamsSupplier(() -> params);
        when(mMockFactory.getTransaction()).thenReturn(new StubTransaction());

        mPipScheduler.scheduleFinishPipBoundsChange(TEST_BOUNDS);

        verify(mMockAlphaAnimator, times(1)).start();
    }

    @Test
    public void finishResize_seamless_animatorNotStarted() {
        final PictureInPictureParams params =
                new PictureInPictureParams.Builder().setSeamlessResizeEnabled(true).build();
        mPipScheduler.setPipParamsSupplier(() -> params);

        mPipScheduler.scheduleFinishPipBoundsChange(TEST_BOUNDS);
        verify(mMockAlphaAnimator, never()).start();
    }

    @Test
    public void onPipTransitionStateChanged_exiting_endAnimation() {
        mPipScheduler.setOverlayFadeoutAnimator(mMockAlphaAnimator);
        when(mMockAlphaAnimator.isStarted()).thenReturn(true);
        mPipScheduler.onPipTransitionStateChanged(PipTransitionState.ENTERED_PIP,
                PipTransitionState.EXITING_PIP, null);

        verify(mMockAlphaAnimator, times(1)).end();
        assertNull("mOverlayFadeoutAnimator should be reset to null",
                mPipScheduler.getOverlayFadeoutAnimator());
    }

    @Test
    public void onPipTransitionStateChanged_scheduledBoundsChange_endAnimation() {
        mPipScheduler.setOverlayFadeoutAnimator(mMockAlphaAnimator);
        when(mMockAlphaAnimator.isStarted()).thenReturn(true);
        mPipScheduler.onPipTransitionStateChanged(PipTransitionState.ENTERED_PIP,
                PipTransitionState.SCHEDULED_BOUNDS_CHANGE, null);

        verify(mMockAlphaAnimator, times(1)).end();
        assertNull("mOverlayFadeoutAnimator should be reset to null",
                mPipScheduler.getOverlayFadeoutAnimator());
    }

    private void setNullPipTaskToken() {
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(null);
    }

    private void setMockPipTaskToken() {
        when(mMockPipTransitionState.getPipTaskToken()).thenReturn(mMockPipTaskToken);
    }

    private ActivityManager.RunningTaskInfo getTaskInfoWithLastParentBeforePip(int lastParentId) {
        final ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.lastParentTaskIdBeforePip = lastParentId;
        // pick an invalid host task id by default
        taskInfo.launchIntoPipHostTaskId = -1;
        return taskInfo;
    }
}
