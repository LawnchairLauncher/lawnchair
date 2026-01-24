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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;
import android.view.Surface;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.Flags;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;

/**
 * Unit test against {@link PipExpandAnimator}.
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(ParameterizedAndroidJunit4.class)
public class PipExpandAnimatorTest {

    @Mock private Context mMockContext;

    @Mock private Resources mMockResources;

    @Mock private PipSurfaceTransactionHelper.SurfaceControlTransactionFactory mMockFactory;

    @Mock private SurfaceControl.Transaction mMockTransaction;

    @Mock private SurfaceControl.Transaction mMockStartTransaction;

    @Mock private SurfaceControl.Transaction mMockFinishTransaction;

    @Mock private Runnable mMockStartCallback;

    @Mock private Runnable mMockEndCallback;

    private PipExpandAnimator mPipExpandAnimator;
    private Rect mBaseBounds;
    private Rect mStartBounds;
    private Rect mEndBounds;
    private Rect mSourceRectHint;
    @Surface.Rotation private int mRotation;
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

    public PipExpandAnimatorTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getInteger(anyInt())).thenReturn(0);
        when(mMockFactory.getTransaction()).thenReturn(mMockTransaction);
        when(mPipSurfaceTransactionHelper.shadow(any(), any(), anyBoolean())).thenReturn(
                mPipSurfaceTransactionHelper);
        when(mPipSurfaceTransactionHelper.round(any(), any(), anyBoolean())).thenReturn(
                mPipSurfaceTransactionHelper);

        mTestLeash = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName("PipExpandAnimatorTest")
                .setCallsite("PipExpandAnimatorTest")
                .build();
    }

    @Test
    public void setAnimationStartCallback_expand_callbackStartCallback() {
        mRotation = Surface.ROTATION_0;
        mBaseBounds = new Rect(0, 0, 1_000, 2_000);
        mStartBounds = new Rect(500, 1_000, 1_000, 2_000);
        mEndBounds = new Rect(mBaseBounds);
        mPipExpandAnimator = new PipExpandAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds, mSourceRectHint,
                mRotation, false /* isPipInDesktopMode */);
        mPipExpandAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipExpandAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipExpandAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipExpandAnimator.start();
            mPipExpandAnimator.pause();
        });

        verify(mMockStartCallback).run();
        verifyNoMoreInteractions(mMockEndCallback);
    }

    @Test
    public void setAnimationEndCallback_expand_callbackStartAndEndCallback() {
        mRotation = Surface.ROTATION_0;
        mBaseBounds = new Rect(0, 0, 1_000, 2_000);
        mStartBounds = new Rect(500, 1_000, 1_000, 2_000);
        mEndBounds = new Rect(mBaseBounds);
        mPipExpandAnimator = new PipExpandAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds, mSourceRectHint,
                mRotation, false /* isPipInDesktopMode */);
        mPipExpandAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipExpandAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipExpandAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipExpandAnimator.start();
            mPipExpandAnimator.end();
        });

        verify(mMockStartCallback).run();
        verify(mMockEndCallback).run();
    }

    @Test
    public void onAnimationStart_withSourceRectHint_cropToHint() {
        mRotation = Surface.ROTATION_0;
        mBaseBounds = new Rect(0, 0, 1_000, 2_000);
        mSourceRectHint = new Rect(0, 0, 1_000, 1_000);
        mStartBounds = new Rect(500, 1_000, 1_000, 2_000);
        mEndBounds = new Rect(mBaseBounds);
        mPipExpandAnimator = new PipExpandAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds, mSourceRectHint,
                mRotation, false /* isPipInDesktopMode */);
        mPipExpandAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipExpandAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipExpandAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            // animator is not started intentionally to avoid double invocation
            clearInvocations(mMockTransaction);
            mPipExpandAnimator.setCurrentFraction(0f);
        });

        verify(mPipSurfaceTransactionHelper).scaleAndCrop(
                eq(mMockTransaction),
                eq(mTestLeash),
                eq(mSourceRectHint),
                eq(mBaseBounds),
                eq(mStartBounds), any(), anyBoolean(), eq(0.0f));
    }

    @Test
    public void onAnimationStart_withoutSourceRectHint_cropToPseudoHint() {
        mRotation = Surface.ROTATION_0;
        mBaseBounds = new Rect(0, 0, 1_000, 2_000);
        mSourceRectHint = null;
        // Set the aspect ratio to be 1:1, pseudo bounds would be (0, 0 - 1000, 1000)
        mStartBounds = new Rect(500, 1_000, 1_000, 1_500);
        final Rect pseudoSourceRectHint = new Rect(0, 0, 1_000, 1_000);
        mEndBounds = new Rect(mBaseBounds);
        mPipExpandAnimator = new PipExpandAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds, mSourceRectHint,
                mRotation, false /* isPipInDesktopMode */);
        mPipExpandAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipExpandAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipExpandAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            // animator is not started intentionally to avoid double invocation
            clearInvocations(mMockTransaction);
            mPipExpandAnimator.setCurrentFraction(0f);
        });

        verify(mPipSurfaceTransactionHelper).scaleAndCrop(
                eq(mMockTransaction),
                eq(mTestLeash),
                eq(pseudoSourceRectHint),
                eq(mBaseBounds),
                eq(mStartBounds), any(), anyBoolean(), eq(0.0f));
    }

    @Test
    public void onAnimationUpdate_expand_setRoundCornersWithoutShadow() {
        mRotation = Surface.ROTATION_0;
        mBaseBounds = new Rect(0, 0, 1_000, 2_000);
        mStartBounds = new Rect(500, 1_000, 1_000, 2_000);
        mEndBounds = new Rect(mBaseBounds);
        mPipExpandAnimator = new PipExpandAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds, mSourceRectHint,
                mRotation, false /* isPipInDesktopMode */);
        mPipExpandAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipExpandAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipExpandAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            // animator is not started intentionally to avoid double invocation
            mPipExpandAnimator.setCurrentFraction(0.5f);
        });

        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .round(eq(mMockTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .shadow(eq(mMockTransaction), eq(mTestLeash), eq(false));
    }

    @Test
    public void onAnimationEnd_expand_leashIsFullscreen() {
        mRotation = Surface.ROTATION_0;
        mBaseBounds = new Rect(0, 0, 1_000, 2_000);
        mStartBounds = new Rect(500, 1_000, 1_000, 2_000);
        mEndBounds = new Rect(mBaseBounds);
        mPipExpandAnimator = new PipExpandAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds, mSourceRectHint,
                mRotation, false /* isPipInDesktopMode */);
        mPipExpandAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipExpandAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipExpandAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipExpandAnimator.start();
            clearInvocations(mMockTransaction);
            clearInvocations(mMockFinishTransaction);
            mPipExpandAnimator.end();
        });

        verify(mPipSurfaceTransactionHelper, atLeastOnce()).scaleAndCrop(
                eq(mMockTransaction),
                eq(mTestLeash),
                any(),
                eq(mBaseBounds),
                eq(mEndBounds), any(), anyBoolean(), eq(1.0f));

        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .round(eq(mMockTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .shadow(eq(mMockTransaction), eq(mTestLeash), eq(false));
        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .round(eq(mMockFinishTransaction), eq(mTestLeash), eq(false));

    }

    @Test
    public void onAnimationEnd_expandInDesktopMode_setRoundedCorners() {
        mRotation = Surface.ROTATION_0;
        mBaseBounds = new Rect(0, 0, 1_000, 2_000);
        mStartBounds = new Rect(500, 1_000, 1_000, 2_000);
        mEndBounds = new Rect(mBaseBounds);
        mPipExpandAnimator = new PipExpandAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash,
                mMockStartTransaction, mMockFinishTransaction,
                mBaseBounds, mStartBounds, mEndBounds, mSourceRectHint,
                mRotation, true /* isPipInDesktopMode */);
        mPipExpandAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        mPipExpandAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipExpandAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipExpandAnimator.start();
            clearInvocations(mMockTransaction);
            clearInvocations(mMockFinishTransaction);
            mPipExpandAnimator.end();
        });


        verify(mPipSurfaceTransactionHelper, atLeastOnce()).scaleAndCrop(
                eq(mMockTransaction),
                eq(mTestLeash),
                any(),
                eq(mBaseBounds),
                eq(mEndBounds), any(), anyBoolean(), eq(1.0f));

        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .round(eq(mMockTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .shadow(eq(mMockTransaction), eq(mTestLeash), eq(false));
        verify(mPipSurfaceTransactionHelper, atLeastOnce())
                .round(eq(mMockFinishTransaction), eq(mTestLeash), eq(true));
    }
}
