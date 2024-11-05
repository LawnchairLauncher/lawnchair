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

package com.android.wm.shell.pip;

import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_TO_PIP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.RemoteException;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Rational;
import android.util.Size;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.MockSurfaceControlHelper;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.pip.PhoneSizeSpecSource;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipKeepClearAlgorithmInterface;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.SizeSpecSource;
import com.android.wm.shell.pip.phone.PhonePipMenuController;
import com.android.wm.shell.splitscreen.SplitScreenController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/**
 * Unit tests for {@link PipTaskOrganizer}
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class PipTaskOrganizerTest extends ShellTestCase {
    private PipTaskOrganizer mPipTaskOrganizer;

    @Mock private DisplayController mMockDisplayController;
    @Mock private SyncTransactionQueue mMockSyncTransactionQueue;
    @Mock private PhonePipMenuController mMockPhonePipMenuController;
    @Mock private PipAnimationController mMockPipAnimationController;
    @Mock private PipTransitionController mMockPipTransitionController;
    @Mock private PipSurfaceTransactionHelper mMockPipSurfaceTransactionHelper;
    @Mock private PipUiEventLogger mMockPipUiEventLogger;
    @Mock private Optional<SplitScreenController> mMockOptionalSplitScreen;
    @Mock private ShellTaskOrganizer mMockShellTaskOrganizer;
    @Mock private PipParamsChangedForwarder mMockPipParamsChangedForwarder;
    private TestShellExecutor mMainExecutor;
    private PipBoundsState mPipBoundsState;
    private PipTransitionState mPipTransitionState;
    private PipBoundsAlgorithm mPipBoundsAlgorithm;
    private SizeSpecSource mSizeSpecSource;
    private PipDisplayLayoutState mPipDisplayLayoutState;

    private ComponentName mComponent1;
    private ComponentName mComponent2;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        mComponent1 = new ComponentName(mContext, "component1");
        mComponent2 = new ComponentName(mContext, "component2");
        mPipDisplayLayoutState = new PipDisplayLayoutState(mContext);
        mSizeSpecSource = new PhoneSizeSpecSource(mContext, mPipDisplayLayoutState);
        mPipBoundsState = new PipBoundsState(mContext, mSizeSpecSource, mPipDisplayLayoutState);
        mPipTransitionState = new PipTransitionState();
        mPipBoundsAlgorithm = new PipBoundsAlgorithm(mContext, mPipBoundsState,
                new PipSnapAlgorithm(), new PipKeepClearAlgorithmInterface() {},
                mPipDisplayLayoutState, mSizeSpecSource);
        mMainExecutor = new TestShellExecutor();
        mPipTaskOrganizer = new PipTaskOrganizer(mContext, mMockSyncTransactionQueue,
                mPipTransitionState, mPipBoundsState, mPipDisplayLayoutState,
                mPipBoundsAlgorithm, mMockPhonePipMenuController, mMockPipAnimationController,
                mMockPipSurfaceTransactionHelper, mMockPipTransitionController,
                mMockPipParamsChangedForwarder, mMockOptionalSplitScreen,
                Optional.empty() /* pipPerfHintControllerOptional */, mMockDisplayController,
                mMockPipUiEventLogger, mMockShellTaskOrganizer, mMainExecutor);
        mMainExecutor.flushAll();
        preparePipTaskOrg();
        preparePipSurfaceTransactionHelper();
    }

    @Test
    public void instantiatePipTaskOrganizer_addsTaskListener() {
        verify(mMockShellTaskOrganizer).addListenerForType(any(), anyInt());
    }

    @Test
    public void instantiatePipTaskOrganizer_addsDisplayWindowListener() {
        verify(mMockDisplayController).addDisplayWindowListener(any());
    }

    @Test
    public void startSwipePipToHome_updatesAspectRatio() {
        final Rational aspectRatio = new Rational(2, 1);

        mPipTaskOrganizer.startSwipePipToHome(mComponent1, null, createPipParams(aspectRatio));

        assertEquals(aspectRatio.floatValue(), mPipBoundsState.getAspectRatio(), 0.01f);
    }

    @Test
    public void startSwipePipToHome_updatesLastPipComponentName() {
        mPipTaskOrganizer.startSwipePipToHome(mComponent1, null, createPipParams(null));

        assertEquals(mComponent1, mPipBoundsState.getLastPipComponentName());
    }

    @Test
    public void startSwipePipToHome_updatesOverrideMinSize() {
        final Size minSize = new Size(400, 320);

        mPipTaskOrganizer.startSwipePipToHome(mComponent1, createActivityInfo(minSize),
                createPipParams(null));

        assertEquals(minSize, mPipBoundsState.getOverrideMinSize());
    }

    @Test
    public void onTaskAppeared_updatesAspectRatio() {
        final Rational aspectRatio = new Rational(2, 1);

        mPipTaskOrganizer.onTaskAppeared(createTaskInfo(mComponent1,
                createPipParams(aspectRatio)), mock(SurfaceControl.class));

        assertEquals(aspectRatio.floatValue(), mPipBoundsState.getAspectRatio(), 0.01f);
    }

    @Test
    public void onTaskAppeared_updatesLastPipComponentName() {
        mPipTaskOrganizer.onTaskAppeared(createTaskInfo(mComponent1, createPipParams(null)),
                mock(SurfaceControl.class));

        assertEquals(mComponent1, mPipBoundsState.getLastPipComponentName());
    }

    @Test
    public void onTaskAppeared_updatesOverrideMinSize() {
        final Size minSize = new Size(400, 320);

        mPipTaskOrganizer.onTaskAppeared(
                createTaskInfo(mComponent1, createPipParams(null), minSize),
                mock(SurfaceControl.class));

        assertEquals(minSize, mPipBoundsState.getOverrideMinSize());
    }

    @Test
    public void onTaskInfoChanged_notInPip_deferUpdatesAspectRatio() {
        final Rational startAspectRatio = new Rational(2, 1);
        final Rational newAspectRatio = new Rational(1, 2);
        mPipTaskOrganizer.onTaskAppeared(createTaskInfo(mComponent1,
                createPipParams(startAspectRatio)), mock(SurfaceControl.class));

        // It is in entering transition, should defer onTaskInfoChanged callback in this case.
        mPipTaskOrganizer.onTaskInfoChanged(createTaskInfo(mComponent1,
                createPipParams(newAspectRatio)));
        verify(mMockPipParamsChangedForwarder, never()).notifyAspectRatioChanged(anyFloat());

        // Once the entering transition finishes, the new aspect ratio applies in a deferred manner
        sendOnPipTransitionFinished(TRANSITION_DIRECTION_TO_PIP);
        verify(mMockPipParamsChangedForwarder)
                .notifyAspectRatioChanged(newAspectRatio.floatValue());
    }

    @Test
    public void onTaskInfoChanged_inPip_updatesAspectRatioIfChanged() {
        final Rational startAspectRatio = new Rational(2, 1);
        final Rational newAspectRatio = new Rational(1, 2);
        mPipTaskOrganizer.onTaskAppeared(createTaskInfo(mComponent1,
                createPipParams(startAspectRatio)), mock(SurfaceControl.class));
        sendOnPipTransitionFinished(TRANSITION_DIRECTION_TO_PIP);

        mPipTaskOrganizer.onTaskInfoChanged(createTaskInfo(mComponent1,
                createPipParams(newAspectRatio)));

        verify(mMockPipParamsChangedForwarder)
                .notifyAspectRatioChanged(newAspectRatio.floatValue());
    }

    @Test
    public void onTaskInfoChanged_inPip_updatesLastPipComponentName() {
        mPipTaskOrganizer.onTaskAppeared(createTaskInfo(mComponent1,
                createPipParams(null)), mock(SurfaceControl.class));
        sendOnPipTransitionFinished(TRANSITION_DIRECTION_TO_PIP);

        mPipTaskOrganizer.onTaskInfoChanged(createTaskInfo(mComponent2,
                createPipParams(null)));

        assertEquals(mComponent2, mPipBoundsState.getLastPipComponentName());
    }

    @Test
    public void onTaskInfoChanged_inPip_updatesOverrideMinSize() {
        mPipTaskOrganizer.onTaskAppeared(createTaskInfo(mComponent1,
                createPipParams(null)), mock(SurfaceControl.class));
        sendOnPipTransitionFinished(TRANSITION_DIRECTION_TO_PIP);

        final Size minSize = new Size(400, 320);
        mPipTaskOrganizer.onTaskInfoChanged(createTaskInfo(mComponent2,
                createPipParams(null), minSize));

        assertEquals(minSize, mPipBoundsState.getOverrideMinSize());
    }

    @Test
    public void onTaskVanished_clearsPipBounds() {
        mPipTaskOrganizer.onTaskAppeared(createTaskInfo(mComponent1,
                createPipParams(null)), mock(SurfaceControl.class));
        mPipBoundsState.setBounds(new Rect(100, 100, 200, 150));

        mPipTaskOrganizer.onTaskVanished(createTaskInfo(mComponent1, createPipParams(null)));
        assertTrue(mPipBoundsState.getBounds().isEmpty());
    }

    private void sendOnPipTransitionFinished(
            @PipAnimationController.TransitionDirection int direction) {
        mPipTaskOrganizer.sendOnPipTransitionFinished(direction);
        // PipTransitionController will call back into PipTaskOrganizer.
        mPipTaskOrganizer.mPipTransitionCallback.onPipTransitionFinished(direction);
    }

    private void preparePipTaskOrg() {
        final DisplayInfo info = new DisplayInfo();
        DisplayLayout layout = new DisplayLayout(info,
                mContext.getResources(), true, true);
        mPipDisplayLayoutState.setDisplayLayout(layout);
        doReturn(PipAnimationController.ANIM_TYPE_ALPHA).when(mMockPipAnimationController)
                .takeOneShotEnterAnimationType();
        mPipTaskOrganizer.setSurfaceControlTransactionFactory(
                MockSurfaceControlHelper::createMockSurfaceControlTransaction);
    }

    private void preparePipSurfaceTransactionHelper() {
        doReturn(mMockPipSurfaceTransactionHelper).when(mMockPipSurfaceTransactionHelper)
                .crop(any(), any(), any());
        doReturn(mMockPipSurfaceTransactionHelper).when(mMockPipSurfaceTransactionHelper)
                .resetScale(any(), any(), any());
        doReturn(mMockPipSurfaceTransactionHelper).when(mMockPipSurfaceTransactionHelper)
                .round(any(), any(), anyBoolean());
        doReturn(mMockPipSurfaceTransactionHelper).when(mMockPipSurfaceTransactionHelper)
                .scale(any(), any(), any(), ArgumentMatchers.<Rect>any(), anyFloat());
        doReturn(mMockPipSurfaceTransactionHelper).when(mMockPipSurfaceTransactionHelper)
                .alpha(any(), any(), anyFloat());
        doNothing().when(mMockPipSurfaceTransactionHelper).onDensityOrFontScaleChanged(any());
    }

    private static ActivityManager.RunningTaskInfo createTaskInfo(
            ComponentName componentName, PictureInPictureParams params) {
        return createTaskInfo(componentName, params, null /* minSize */);
    }

    private static ActivityManager.RunningTaskInfo createTaskInfo(
            ComponentName componentName, PictureInPictureParams params, Size minSize) {
        final ActivityManager.RunningTaskInfo info = new ActivityManager.RunningTaskInfo();
        info.token = mock(WindowContainerToken.class);
        info.pictureInPictureParams = params;
        info.topActivity = componentName;
        if (minSize != null) {
            info.topActivityInfo = createActivityInfo(minSize);
        }
        return info;
    }

    private static ActivityInfo createActivityInfo(Size minSize) {
        final ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.windowLayout = new ActivityInfo.WindowLayout(
                0, 0, 0, 0, 0, minSize.getWidth(), minSize.getHeight());
        return activityInfo;
    }

    private static PictureInPictureParams createPipParams(Rational aspectRatio) {
        return new PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build();
    }
}
