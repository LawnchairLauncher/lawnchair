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

package com.android.wm.shell.onehanded;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.DisplayAreaOrganizer.FEATURE_ONE_HANDED;

import static com.android.wm.shell.onehanded.OneHandedAnimationController.TRANSITION_DIRECTION_EXIT;
import static com.android.wm.shell.onehanded.OneHandedAnimationController.TRANSITION_DIRECTION_TRIGGER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Binder;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.DisplayAreaAppearedInfo;
import android.window.DisplayAreaInfo;
import android.window.IWindowContainerToken;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidTestingRunner.class)
public class OneHandedDisplayAreaOrganizerTest extends OneHandedTestCase {
    static final int DISPLAYAREA_INFO_COUNT = 3;
    static final int DISPLAY_WIDTH = 1000;
    static final int DISPLAY_HEIGHT = 1000;

    DisplayAreaInfo mDisplayAreaInfo;
    Display mDisplay;
    DisplayLayout mDisplayLayout;
    OneHandedDisplayAreaOrganizer mSpiedDisplayAreaOrganizer;
    OneHandedTutorialHandler mTutorialHandler;
    OneHandedAnimationController.OneHandedTransitionAnimator mFakeAnimator;
    WindowContainerToken mToken;
    SurfaceControl mLeash;
    TestableLooper mTestableLooper;
    @Mock
    IWindowContainerToken mMockRealToken;
    @Mock
    OneHandedAnimationController mMockAnimationController;
    @Mock
    OneHandedAnimationController.OneHandedTransitionAnimator mMockAnimator;
    @Mock
    OneHandedSurfaceTransactionHelper mMockSurfaceTransactionHelper;
    @Mock
    DisplayController mMockDisplayController;
    @Mock
    SurfaceControl mMockLeash;
    @Mock
    WindowContainerTransaction mMockWindowContainerTransaction;
    @Mock
    ShellExecutor mMockShellMainExecutor;
    @Mock
    OneHandedSettingsUtil mMockSettingsUitl;
    @Mock
    InteractionJankMonitor mJankMonitor;

    List<DisplayAreaAppearedInfo> mDisplayAreaAppearedInfoList = new ArrayList<>();

    final boolean mDefaultEnabled = true;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestableLooper = TestableLooper.get(this);
        Binder binder = new Binder();
        doReturn(binder).when(mMockRealToken).asBinder();
        mToken = new WindowContainerToken(mMockRealToken);
        mLeash = new SurfaceControl();
        mDisplay = mContext.getDisplay();
        mDisplayLayout = new DisplayLayout(mContext, mDisplay);
        mDisplayAreaInfo = new DisplayAreaInfo(mToken, DEFAULT_DISPLAY, FEATURE_ONE_HANDED);
        mDisplayAreaInfo.configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        when(mMockAnimationController.getAnimator(any(), any(), anyFloat(), anyFloat(),
                any())).thenReturn(
                null);
        when(mMockDisplayController.getDisplay(anyInt())).thenReturn(mDisplay);
        when(mMockSurfaceTransactionHelper.translate(any(), any(), anyFloat())).thenReturn(
                mMockSurfaceTransactionHelper);
        when(mMockSurfaceTransactionHelper.crop(any(), any(), any())).thenReturn(
                mMockSurfaceTransactionHelper);
        when(mMockSurfaceTransactionHelper.round(any(), any())).thenReturn(
                mMockSurfaceTransactionHelper);
        when(mMockAnimator.isRunning()).thenReturn(true);
        when(mMockAnimator.setDuration(anyInt())).thenReturn(mFakeAnimator);
        when(mMockAnimator.addOneHandedAnimationCallback(any())).thenReturn(mFakeAnimator);
        when(mMockAnimator.setTransitionDirection(anyInt())).thenReturn(mFakeAnimator);
        when(mMockLeash.getWidth()).thenReturn(DISPLAY_WIDTH);
        when(mMockLeash.getHeight()).thenReturn(DISPLAY_HEIGHT);
        when(mMockSettingsUitl.getSettingsOneHandedModeEnabled(any(), anyInt())).thenReturn(
                mDefaultEnabled);

        mSpiedDisplayAreaOrganizer = spy(new OneHandedDisplayAreaOrganizer(mContext,
                mDisplayLayout,
                mMockSettingsUitl,
                mMockAnimationController,
                mTutorialHandler,
                mJankMonitor,
                mMockShellMainExecutor));

        for (int i = 0; i < DISPLAYAREA_INFO_COUNT; i++) {
            mDisplayAreaAppearedInfoList.add(getDummyDisplayAreaInfo());
        }
        doReturn(mDisplayAreaAppearedInfoList).when(mSpiedDisplayAreaOrganizer).registerOrganizer(
                FEATURE_ONE_HANDED);
    }

    private DisplayAreaAppearedInfo getDummyDisplayAreaInfo() {
        return new DisplayAreaAppearedInfo(mDisplayAreaInfo, mMockLeash);
    }

    @Test
    public void testRegisterDisplayAreaOrganizer() {
        assertThat(mSpiedDisplayAreaOrganizer.registerOrganizer(FEATURE_ONE_HANDED)).isNotNull();
    }

    @Test
    public void testOnDisplayAreaAppeared() {
        mDisplayAreaAppearedInfoList.forEach(
                (info) -> mSpiedDisplayAreaOrganizer.onDisplayAreaAppeared(
                        info.getDisplayAreaInfo(),
                        info.getLeash()));

        verify(mMockAnimationController, never()).getAnimator(any(), any(), anyFloat(), anyFloat(),
                any());
    }

    @Test
    public void testOnDisplayAreaVanished() {
        mDisplayAreaAppearedInfoList.forEach(
                (info) -> mSpiedDisplayAreaOrganizer.onDisplayAreaAppeared(
                        info.getDisplayAreaInfo(),
                        info.getLeash()));

        mDisplayAreaAppearedInfoList.forEach(
                (info) -> mSpiedDisplayAreaOrganizer.onDisplayAreaVanished(
                        info.getDisplayAreaInfo()));

        verify(mSpiedDisplayAreaOrganizer, times(DISPLAYAREA_INFO_COUNT)).onDisplayAreaVanished(
                any());
    }

    @Test
    public void testRotation_portrait_0_to_landscape_90() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 0 -> 90
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_90,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_portrait_0_to_seascape_270() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 0 -> 270
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_270,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_portrait_180_to_landscape_90() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 180 -> 90
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_180);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_90,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_portrait_180_to_seascape_270() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 180 -> 270
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_180);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_270,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_landscape_90_to_portrait_0() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 90 -> 0
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_90);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_0,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_landscape_90_to_portrait_180() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 90 -> 180
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_90);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_180,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_seascape_270_to_portrait_0() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 270 -> 0
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_270);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_0,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_seascape_270_to_portrait_180() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 270 -> 180
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_270);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_180,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_portrait_0_to_portrait_0() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 0 -> 0
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_0,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer, never()).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer, never()).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_portrait_0_to_portrait_180() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 0 -> 180
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_180,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_portrait_180_to_portrait_180() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 180 -> 180
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_180);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_180,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer, never()).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer, never()).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_portrait_180_to_portrait_0() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 180 -> 0
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_90);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_0,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_landscape_90_to_landscape_90() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 90 -> 90
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_90);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_90,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer, never()).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer, never()).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_landscape_90_to_seascape_270() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 90 -> 270
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_90);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_270,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_seascape_270_to_seascape_270() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 270 -> 270
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_270);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_270,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer, never()).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer, never()).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation_seascape_270_to_landscape_90() {
        when(mMockLeash.isValid()).thenReturn(false);
        // Rotate 270 -> 90
        mDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_270);
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);
        mSpiedDisplayAreaOrganizer.onRotateDisplay(mContext, Surface.ROTATION_90,
                mMockWindowContainerTransaction);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
        verify(mSpiedDisplayAreaOrganizer).finishOffset(anyInt(), anyInt());
    }

    @Test
    public void testTriggerOffset() {
        final Rect testBounds = mSpiedDisplayAreaOrganizer.getLastDisplayBounds();
        final int offset = 100;
        testBounds.offsetTo(0, offset);
        mSpiedDisplayAreaOrganizer.finishOffset(offset, TRANSITION_DIRECTION_TRIGGER);

        assertThat(mSpiedDisplayAreaOrganizer.getLastDisplayBounds()).isEqualTo(testBounds);
    }

    @Test
    public void testExitOffsetToZero() {
        final Rect testBounds = mSpiedDisplayAreaOrganizer.getLastDisplayBounds();
        final int offset = 100;
        mSpiedDisplayAreaOrganizer.finishOffset(offset, TRANSITION_DIRECTION_TRIGGER);
        mSpiedDisplayAreaOrganizer.finishOffset(0, TRANSITION_DIRECTION_EXIT);

        assertThat(mSpiedDisplayAreaOrganizer.getLastDisplayBounds()).isEqualTo(testBounds);
    }

    @Test
    public void testExit_must_resetWindowsOffset() {
        mSpiedDisplayAreaOrganizer.finishOffset(0, TRANSITION_DIRECTION_EXIT);

        verify(mSpiedDisplayAreaOrganizer).resetWindowsOffset();
    }

    @Test
    public void testTrigger_not_resetWindowsOffset() {
        mSpiedDisplayAreaOrganizer.finishOffset(0, TRANSITION_DIRECTION_TRIGGER);

        verify(mSpiedDisplayAreaOrganizer, never()).resetWindowsOffset();
    }

    @Test
    public void testDisplayArea_notReadyForTransition() {
        OneHandedDisplayAreaOrganizer testSpiedDisplayAreaOrganizer = spy(
                new OneHandedDisplayAreaOrganizer(mContext,
                        mDisplayLayout,
                        mMockSettingsUitl,
                        mMockAnimationController,
                        mTutorialHandler,
                        mJankMonitor,
                        mMockShellMainExecutor));

        assertThat(testSpiedDisplayAreaOrganizer.isReady()).isFalse();
    }

    @Test
    public void testDisplayArea_setDisplayLayout_should_updateDisplayBounds() {
        mSpiedDisplayAreaOrganizer.setDisplayLayout(mDisplayLayout);

        verify(mSpiedDisplayAreaOrganizer).updateDisplayBounds();
    }
}
