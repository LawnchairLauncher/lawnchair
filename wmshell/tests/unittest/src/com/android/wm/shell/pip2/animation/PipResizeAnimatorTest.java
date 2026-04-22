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

package com.android.wm.shell.pip2.animation;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.MatchersKt.eq;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.Flags;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;

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

/**
 * Unit test against {@link PipResizeAnimator}.
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(ParameterizedAndroidJunit4.class)
public class PipResizeAnimatorTest {

    private static final float FLOAT_COMPARISON_DELTA = 0.001f;
    private static final float TEST_CORNER_RADIUS = 1f;
    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private PipSurfaceTransactionHelper.SurfaceControlTransactionFactory mMockFactory;
    @Mock private SurfaceControl.Transaction mMockTransaction;
    @Mock private SurfaceControl.Transaction mMockStartTransaction;
    @Mock private SurfaceControl.Transaction mMockFinishTransaction;
    @Mock private Runnable mMockStartCallback;
    @Mock private Runnable mMockEndCallback;

    @Captor private ArgumentCaptor<Matrix> mArgumentCaptor;

    private PipResizeAnimator mPipResizeAnimator;
    private Rect mBaseBounds;
    private Rect mStartBounds;
    private Rect mEndBounds;
    private SurfaceControl mTestLeash;

    @Mock
    private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_PIP_BOX_SHADOWS);
    }

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    public PipResizeAnimatorTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockFactory.getTransaction()).thenReturn(mMockTransaction);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mPipSurfaceTransactionHelper.getCornerRadius()).thenReturn((int) TEST_CORNER_RADIUS);

        prepareTransaction(mMockTransaction);
        prepareTransaction(mMockStartTransaction);
        prepareTransaction(mMockFinishTransaction);

        mTestLeash = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName("PipResizeAnimatorTest")
                .setCallsite("PipResizeAnimatorTest")
                .build();
    }

    @Test
    public void setAnimationStartCallback_resize_callbackStartCallback() {
        mBaseBounds = new Rect(100, 100, 500, 500);
        mStartBounds = new Rect(200, 200, 1_000, 1_000);
        mEndBounds = new Rect(mBaseBounds);
        final int duration = 10;
        final float delta = 0;
        mPipResizeAnimator = new PipResizeAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds,
                duration, delta);

        mPipResizeAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipResizeAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipResizeAnimator.start();
            mPipResizeAnimator.pause();
        });

        verify(mMockStartCallback).run();
        verifyNoMoreInteractions(mMockEndCallback);
    }

    @Test
    public void setAnimationEndCallback_resize_callbackStartAndEndCallback() {
        mBaseBounds = new Rect(100, 100, 500, 500);
        mStartBounds = new Rect(200, 200, 1_000, 1_000);
        mEndBounds = new Rect(mBaseBounds);
        final int duration = 10;
        final float delta = 0;
        mPipResizeAnimator = new PipResizeAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds,
                duration, delta);

        mPipResizeAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipResizeAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipResizeAnimator.start();
            mPipResizeAnimator.end();
        });

        verify(mMockStartCallback).run();
        verify(mMockEndCallback).run();
    }

    @Test
    public void onAnimationEnd_resizeDown_sizeChanged() {
        // Resize from 800x800 to 400x400, eg. resize down
        mBaseBounds = new Rect(100, 100, 500, 500);
        mStartBounds = new Rect(200, 200, 1_000, 1_000);
        mEndBounds = new Rect(mBaseBounds);
        final int duration = 10;
        final float delta = 0;
        final float[] matrix = new float[9];
        mPipResizeAnimator = new PipResizeAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds,
                duration, delta);
        mPipResizeAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipResizeAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipResizeAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipResizeAnimator.start();
            clearInvocations(mMockTransaction);
            mPipResizeAnimator.end();
        });

        // Start transaction scales down from its final state
        verify(mMockStartTransaction).setMatrix(eq(mTestLeash), mArgumentCaptor.capture(), any());
        mArgumentCaptor.getValue().getValues(matrix);
        assertEquals(matrix[Matrix.MSCALE_X],
                mStartBounds.width() / (float) mEndBounds.width(), FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MSCALE_Y],
                mStartBounds.height() / (float) mEndBounds.height(), FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_X], mStartBounds.left, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_Y], mStartBounds.top, FLOAT_COMPARISON_DELTA);

        // Final animation transaction scales to 1 and puts the leash at final position
        verify(mMockTransaction).setMatrix(eq(mTestLeash), mArgumentCaptor.capture(), any());
        mArgumentCaptor.getValue().getValues(matrix);
        assertEquals(matrix[Matrix.MSCALE_X], 1f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MSCALE_Y], 1f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_X], mEndBounds.left, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_Y], mEndBounds.top, FLOAT_COMPARISON_DELTA);

        // Finish transaction resets scale and puts the leash at final position
        verify(mMockFinishTransaction).setMatrix(eq(mTestLeash), mArgumentCaptor.capture(), any());
        mArgumentCaptor.getValue().getValues(matrix);
        assertEquals(matrix[Matrix.MSCALE_X], 1f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MSCALE_Y], 1f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_X], mEndBounds.left, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_Y], mEndBounds.top, FLOAT_COMPARISON_DELTA);

        // Check corner and shadow radii were set
        verify(mMockTransaction, atLeastOnce())
                .setCornerRadius(eq(mTestLeash), eq(TEST_CORNER_RADIUS));

        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .shadow(eq(mMockTransaction), eq(mTestLeash), eq(true));
    }

    @Test
    public void onAnimationEnd_resizeUp_sizeChanged() {
        // Resize from 400x400 to 800x800, eg. resize up
        mBaseBounds = new Rect(200, 200, 1_000, 1_000);
        mStartBounds = new Rect(100, 100, 500, 500);
        mEndBounds = new Rect(mBaseBounds);
        final int duration = 10;
        final float delta = 0;
        final float[] matrix = new float[9];
        mPipResizeAnimator = new PipResizeAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds,
                duration, delta);
        mPipResizeAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipResizeAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipResizeAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipResizeAnimator.start();
            clearInvocations(mMockTransaction);
            mPipResizeAnimator.end();
        });

        // Start transaction scales up from its final state
        verify(mMockStartTransaction).setMatrix(eq(mTestLeash), mArgumentCaptor.capture(), any());
        mArgumentCaptor.getValue().getValues(matrix);
        assertEquals(matrix[Matrix.MSCALE_X],
                mStartBounds.width() / (float) mEndBounds.width(), FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MSCALE_Y],
                mStartBounds.height() / (float) mEndBounds.height(), FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_X], mStartBounds.left, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_Y], mStartBounds.top, FLOAT_COMPARISON_DELTA);

        // Final animation transaction scales to 1 and puts the leash at final position
        verify(mMockTransaction).setMatrix(eq(mTestLeash), mArgumentCaptor.capture(), any());
        mArgumentCaptor.getValue().getValues(matrix);
        assertEquals(matrix[Matrix.MSCALE_X], 1f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MSCALE_Y], 1f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_X], mEndBounds.left, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_Y], mEndBounds.top, FLOAT_COMPARISON_DELTA);

        // Finish transaction resets scale and puts the leash at final position
        verify(mMockFinishTransaction).setMatrix(eq(mTestLeash), mArgumentCaptor.capture(), any());
        mArgumentCaptor.getValue().getValues(matrix);
        assertEquals(matrix[Matrix.MSCALE_X], 1f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MSCALE_Y], 1f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_X], mEndBounds.left, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MTRANS_Y], mEndBounds.top, FLOAT_COMPARISON_DELTA);

        // Check corner and shadow radii were set
        verify(mMockTransaction, atLeastOnce())
                .setCornerRadius(eq(mTestLeash), eq(TEST_CORNER_RADIUS));

        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .shadow(eq(mMockTransaction), eq(mTestLeash), eq(true));
    }

    @Test
    public void onAnimationEnd_withInitialDelta_rotateToZeroDegree() {
        mBaseBounds = new Rect(200, 200, 1_000, 1_000);
        mStartBounds = new Rect(100, 100, 500, 500);
        mEndBounds = new Rect(mBaseBounds);
        final int duration = 10;
        final float delta = 45;
        final float[] matrix = new float[9];
        mPipResizeAnimator = new PipResizeAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds,
                duration, delta);
        mPipResizeAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipResizeAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipResizeAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipResizeAnimator.start();
            clearInvocations(mMockTransaction);
            mPipResizeAnimator.end();
        });

        // Final animation transaction sets skew to zero
        verify(mMockTransaction).setMatrix(eq(mTestLeash), mArgumentCaptor.capture(), any());
        mArgumentCaptor.getValue().getValues(matrix);
        assertEquals(matrix[Matrix.MSKEW_X], 0f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MSKEW_Y], 0f, FLOAT_COMPARISON_DELTA);

        // Finish transaction sets skew to zero
        verify(mMockFinishTransaction).setMatrix(eq(mTestLeash), mArgumentCaptor.capture(), any());
        mArgumentCaptor.getValue().getValues(matrix);
        assertEquals(matrix[Matrix.MSKEW_X], 0f, FLOAT_COMPARISON_DELTA);
        assertEquals(matrix[Matrix.MSKEW_Y], 0f, FLOAT_COMPARISON_DELTA);

        // Check corner and shadow radii were set
        verify(mMockTransaction, atLeastOnce())
                .setCornerRadius(eq(mTestLeash), eq(TEST_CORNER_RADIUS));

        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .shadow(eq(mMockTransaction), eq(mTestLeash), eq(true));
    }

    // set up transaction chaining
    private void prepareTransaction(SurfaceControl.Transaction tx) {
        when(tx.setMatrix(any(SurfaceControl.class), any(Matrix.class), any()))
                .thenReturn(tx);
        when(tx.setCornerRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(tx);
        when(tx.setShadowRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(tx);
    }
}
