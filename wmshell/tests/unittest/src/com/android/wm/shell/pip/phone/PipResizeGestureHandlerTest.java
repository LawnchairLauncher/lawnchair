/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.pip.phone;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Rect;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PhoneSizeSpecSource;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipKeepClearAlgorithmInterface;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.SizeSpecSource;
import com.android.wm.shell.pip.PipTaskOrganizer;
import com.android.wm.shell.pip.PipTransitionController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/**
 * Unit tests against {@link PipResizeGestureHandler}
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class PipResizeGestureHandlerTest extends ShellTestCase {
    private static final float DEFAULT_SNAP_FRACTION = 2.0f;
    private static final int STEP_SIZE = 40;
    private final MotionEvent.PointerProperties[] mPp = new MotionEvent.PointerProperties[2];

    @Mock
    private PhonePipMenuController mPhonePipMenuController;

    @Mock
    private PipTaskOrganizer mPipTaskOrganizer;

    @Mock
    private PipDismissTargetHandler mPipDismissTargetHandler;

    @Mock
    private PipTransitionController mMockPipTransitionController;

    @Mock
    private FloatingContentCoordinator mFloatingContentCoordinator;

    @Mock
    private PipUiEventLogger mPipUiEventLogger;

    @Mock
    private ShellExecutor mMainExecutor;

    private PipResizeGestureHandler mPipResizeGestureHandler;

    private PipBoundsState mPipBoundsState;

    private SizeSpecSource mSizeSpecSource;

    private PipDisplayLayoutState mPipDisplayLayoutState;

    private PipTouchState mPipTouchState;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final TestableResources res = mContext.getOrCreateTestableResources();
        res.addOverride(R.bool.config_pipEnablePinchResize, true);

        mPipDisplayLayoutState = new PipDisplayLayoutState(mContext);
        mSizeSpecSource = new PhoneSizeSpecSource(mContext, mPipDisplayLayoutState);
        mPipBoundsState = new PipBoundsState(mContext, mSizeSpecSource, mPipDisplayLayoutState);
        final PipSnapAlgorithm pipSnapAlgorithm = new PipSnapAlgorithm();
        final PipKeepClearAlgorithmInterface pipKeepClearAlgorithm =
                new PipKeepClearAlgorithmInterface() {};
        final PipBoundsAlgorithm pipBoundsAlgorithm = new PipBoundsAlgorithm(mContext,
                mPipBoundsState, pipSnapAlgorithm, pipKeepClearAlgorithm, mPipDisplayLayoutState,
                mSizeSpecSource);
        final PipMotionHelper motionHelper = new PipMotionHelper(mContext, mPipBoundsState,
                mPipTaskOrganizer, mPhonePipMenuController, pipSnapAlgorithm,
                mMockPipTransitionController, mFloatingContentCoordinator,
                Optional.empty() /* pipPerfHintControllerOptional */);

        mPipTouchState = new PipTouchState(ViewConfiguration.get(mContext),
                () -> {}, () -> {}, mMainExecutor);
        mPipResizeGestureHandler = new PipResizeGestureHandler(mContext, pipBoundsAlgorithm,
                mPipBoundsState, motionHelper, mPipTouchState, mPipTaskOrganizer,
                mPipDismissTargetHandler,
                () -> {}, mPipUiEventLogger, mPhonePipMenuController,
                mMainExecutor, null /* pipPerfHintController */) {
            @Override
            public void pilferPointers() {
                // Overridden just to avoid calling into InputMonitor.
            }
        };

        for (int i = 0; i < 2; i++) {
            MotionEvent.PointerProperties pointerProperty = new MotionEvent.PointerProperties();
            pointerProperty.id = i;
            pointerProperty.toolType = MotionEvent.TOOL_TYPE_FINGER;
            mPp[i] = pointerProperty;
        }

        mPipResizeGestureHandler.init();
        mPipResizeGestureHandler.onSystemUiStateChanged(true);
    }

    @Test
    public void twoInput_triggersPinchResize_getBigger() {
        assertTrue(mPipResizeGestureHandler.isUsingPinchToZoom());

        int topLeft = 200;
        int bottomRight = 500;
        mPipBoundsState.setBounds(new Rect(topLeft, topLeft, bottomRight, bottomRight));

        // Start inside the PiP bounds first.
        topLeft += STEP_SIZE;
        bottomRight -= STEP_SIZE;
        MotionEvent downEvent =
                obtainMotionEvent(MotionEvent.ACTION_POINTER_DOWN, topLeft, bottomRight);
        assertTrue(mPipResizeGestureHandler.willStartResizeGesture(downEvent));

        // Slowly move outward.
        topLeft -= STEP_SIZE;
        bottomRight += STEP_SIZE;
        MotionEvent moveEvent1 = obtainMotionEvent(MotionEvent.ACTION_MOVE, topLeft, bottomRight);
        mPipResizeGestureHandler.onPinchResize(moveEvent1);

        // Move outward more.
        topLeft -= STEP_SIZE;
        bottomRight += STEP_SIZE;
        MotionEvent moveEvent2 = obtainMotionEvent(MotionEvent.ACTION_MOVE, topLeft, bottomRight);
        mPipResizeGestureHandler.onPinchResize(moveEvent2);

        verify(mPipTaskOrganizer, times(2))
                .scheduleUserResizePip(any(), any(), anyFloat(), any());

        MotionEvent upEvent = obtainMotionEvent(MotionEvent.ACTION_UP, topLeft, bottomRight);
        mPipResizeGestureHandler.onPinchResize(upEvent);

        verify(mPipTaskOrganizer, times(1))
                .scheduleAnimateResizePip(any(), any(), anyInt(), anyFloat(), any(), any());

        assertTrue("The new size should be bigger than the original PiP size.",
                mPipResizeGestureHandler.getLastResizeBounds().width()
                        > mPipBoundsState.getBounds().width());
    }

    @Test
    public void twoInput_triggersPinchResize_getSmaller() {
        assertTrue(mPipResizeGestureHandler.isUsingPinchToZoom());

        int topLeft = 200;
        int bottomRight = 500;
        mPipBoundsState.setBounds(new Rect(topLeft, topLeft, bottomRight, bottomRight));


        topLeft += STEP_SIZE;
        bottomRight -= STEP_SIZE;
        MotionEvent downEvent =
                obtainMotionEvent(MotionEvent.ACTION_POINTER_DOWN, topLeft, bottomRight);
        assertTrue(mPipResizeGestureHandler.willStartResizeGesture(downEvent));

        topLeft += STEP_SIZE;
        bottomRight -= STEP_SIZE;
        MotionEvent moveEvent1 = obtainMotionEvent(MotionEvent.ACTION_MOVE, topLeft, bottomRight);
        mPipResizeGestureHandler.onPinchResize(moveEvent1);

        topLeft += STEP_SIZE;
        bottomRight -= STEP_SIZE;
        MotionEvent moveEvent2 = obtainMotionEvent(MotionEvent.ACTION_MOVE, topLeft, bottomRight);
        mPipResizeGestureHandler.onPinchResize(moveEvent2);

        verify(mPipTaskOrganizer, times(2))
                .scheduleUserResizePip(any(), any(), anyFloat(), any());

        MotionEvent upEvent = obtainMotionEvent(MotionEvent.ACTION_UP, topLeft, bottomRight);
        mPipResizeGestureHandler.onPinchResize(upEvent);

        verify(mPipTaskOrganizer, times(1))
                .scheduleAnimateResizePip(any(), any(), anyInt(), anyFloat(), any(), any());

        assertTrue("The new size should be smaller than the original PiP size.",
                mPipResizeGestureHandler.getLastResizeBounds().width()
                        < mPipBoundsState.getBounds().width());
    }

    @Test
    public void testUserResizeTo() {
        // resizing the bounds to normal bounds at first
        mPipResizeGestureHandler.userResizeTo(mPipBoundsState.getNormalBounds(),
                DEFAULT_SNAP_FRACTION);

        assertPipBoundsUserResizedTo(mPipBoundsState.getNormalBounds());

        verify(mPipTaskOrganizer, times(1))
                .scheduleUserResizePip(any(), any(), any());

        verify(mPipTaskOrganizer, times(1))
                .scheduleFinishResizePip(any(), any());

        // bounds with max size
        final Rect maxBounds = new Rect(0, 0, mPipBoundsState.getMaxSize().x,
                mPipBoundsState.getMaxSize().y);

        // resizing the bounds to maximum bounds the second time
        mPipResizeGestureHandler.userResizeTo(maxBounds, DEFAULT_SNAP_FRACTION);

        assertPipBoundsUserResizedTo(maxBounds);

        // another call to scheduleUserResizePip() and scheduleFinishResizePip() makes
        // the total number of invocations 2 for each method
        verify(mPipTaskOrganizer, times(2))
                .scheduleUserResizePip(any(), any(), any());

        verify(mPipTaskOrganizer, times(2))
                .scheduleFinishResizePip(any(), any());
    }

    private void assertPipBoundsUserResizedTo(Rect bounds) {
        // check user-resized bounds
        assertEquals(mPipResizeGestureHandler.getUserResizeBounds().width(), bounds.width());
        assertEquals(mPipResizeGestureHandler.getUserResizeBounds().height(), bounds.height());

        // check if the bounds are the same
        assertEquals(mPipBoundsState.getBounds().width(), bounds.width());
        assertEquals(mPipBoundsState.getBounds().height(), bounds.height());

        // a flag should be set to indicate pip has been resized by the user
        assertTrue(mPipBoundsState.hasUserResizedPip());
    }

    private MotionEvent obtainMotionEvent(int action, int topLeft, int bottomRight) {
        final MotionEvent.PointerCoords[] pc = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            MotionEvent.PointerCoords pointerCoord = new MotionEvent.PointerCoords();
            if (i == 0) {
                pointerCoord.x = topLeft;
                pointerCoord.y = topLeft;
            } else {
                pointerCoord.x = bottomRight;
                pointerCoord.y = bottomRight;
            }
            pc[i] = pointerCoord;
        }
        return MotionEvent.obtain(0 /* downTime */,
                System.currentTimeMillis(),
                action,
                2 /* pointerCount */,
                mPp,
                pc,
                0 /* metaState */,
                0 /* buttonState */,
                0 /* xPrecision */,
                0 /* yPrecision */,
                0 /* deviceId */,
                0 /* edgeFlags */,
                0 /* source */,
                0 /* flags */);
    }
}
