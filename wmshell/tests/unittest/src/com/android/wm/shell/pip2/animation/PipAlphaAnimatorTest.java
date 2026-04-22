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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;

/**
 * Unit test against {@link PipAlphaAnimator}.
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(ParameterizedAndroidJunit4.class)
public class PipAlphaAnimatorTest {
    @Mock private Context mMockContext;

    @Mock private Resources mMockResources;

    @Mock private PipSurfaceTransactionHelper.SurfaceControlTransactionFactory mMockFactory;

    @Mock private SurfaceControl.Transaction mMockAnimateTransaction;
    @Mock private SurfaceControl.Transaction mMockStartTransaction;
    @Mock private SurfaceControl.Transaction mMockFinishTransaction;
    @Mock
    private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    @Mock private Runnable mMockStartCallback;

    @Mock private Runnable mMockEndCallback;

    private PipAlphaAnimator mPipAlphaAnimator;
    private SurfaceControl mTestLeash;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_PIP_BOX_SHADOWS);
    }

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    public PipAlphaAnimatorTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getInteger(anyInt())).thenReturn(0);
        when(mMockFactory.getTransaction()).thenReturn(mMockAnimateTransaction);

        prepareTransaction(mMockAnimateTransaction);
        prepareTransaction(mMockStartTransaction);
        prepareTransaction(mMockFinishTransaction);
        mTestLeash = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName("PipAlphaAnimatorTest")
                .setCallsite("PipAlphaAnimatorTest")
                .build();
    }

    @Test
    public void setAnimationStartCallback_fadeInAnimator_callbackStartCallback() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_IN);

        mPipAlphaAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipAlphaAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            mPipAlphaAnimator.pause();
        });

        verify(mMockStartCallback).run();
        verifyNoMoreInteractions(mMockEndCallback);
    }

    @Test
    public void setAnimationEndCallback_fadeInAnimator_callbackStartAndEndCallback() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_IN);

        mPipAlphaAnimator.setAnimationStartCallback(mMockStartCallback);
        mPipAlphaAnimator.setAnimationEndCallback(mMockEndCallback);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            mPipAlphaAnimator.end();
        });

        verify(mMockStartCallback).run();
        verify(mMockEndCallback).run();
    }

    @Test
    public void onAnimationStart_fadeInAnimator_setCornerAndShadowRadii() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_IN);
        mPipAlphaAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            mPipAlphaAnimator.pause();
        });

        verify(mPipSurfaceTransactionHelper)
                .round(eq(mMockStartTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper)
                .shadow(eq(mMockStartTransaction), eq(mTestLeash), eq(true));
    }

    @Test
    public void onAnimationStart_fadeOutAnimator_setCornerNoShadowRadii() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_OUT);
        mPipAlphaAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            mPipAlphaAnimator.pause();
        });

        verify(mPipSurfaceTransactionHelper)
                .round(eq(mMockStartTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper, never())
                .shadow(eq(mMockStartTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper)
                .shadow(eq(mMockStartTransaction), eq(mTestLeash), eq(false));
    }

    @Test
    public void onAnimationUpdate_fadeInAnimator_setCornerAndShadowRadii() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_IN);
        mPipAlphaAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            mPipAlphaAnimator.pause();
        });

        verify(mPipSurfaceTransactionHelper)
                .round(eq(mMockAnimateTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper)
                .shadow(eq(mMockAnimateTransaction), eq(mTestLeash), eq(true));
    }

    @Test
    public void onAnimationUpdate_fadeOutAnimator_setCornerNoShadowRadii() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_OUT);
        mPipAlphaAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            mPipAlphaAnimator.pause();
        });

        verify(mPipSurfaceTransactionHelper)
                .round(eq(mMockAnimateTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper, never())
                .shadow(eq(mMockAnimateTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper)
                .shadow(eq(mMockAnimateTransaction), eq(mTestLeash), eq(false));
    }

    @Test
    public void onAnimationEnd_fadeInAnimator_setCornerAndShadowRadii() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_IN);
        mPipAlphaAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            mPipAlphaAnimator.end();
        });

        verify(mPipSurfaceTransactionHelper)
                .round(eq(mMockFinishTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper)
                .shadow(eq(mMockFinishTransaction), eq(mTestLeash), eq(true));
    }

    @Test
    public void onAnimationEnd_fadeOutAnimator_setCornerNoShadowRadii() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_OUT);
        mPipAlphaAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            mPipAlphaAnimator.end();
        });

        verify(mPipSurfaceTransactionHelper)
                .round(eq(mMockFinishTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper, never())
                .shadow(eq(mMockFinishTransaction), eq(mTestLeash), eq(true));
        verify(mPipSurfaceTransactionHelper)
                .shadow(eq(mMockFinishTransaction), eq(mTestLeash), eq(false));
    }

    @Test
    public void onAnimationEnd_fadeInAnimator_leashVisibleAtEnd() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_IN);
        mPipAlphaAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            clearInvocations(mMockAnimateTransaction);
            mPipAlphaAnimator.end();
        });

        verify(mMockAnimateTransaction).setAlpha(mTestLeash, 1.0f);
    }

    @Test
    public void onAnimationEnd_fadeOutAnimator_leashInvisibleAtEnd() {
        mPipAlphaAnimator = new PipAlphaAnimator(mMockContext,
                mPipSurfaceTransactionHelper, mTestLeash, mMockStartTransaction,
                mMockFinishTransaction, PipAlphaAnimator.FADE_OUT);
        mPipAlphaAnimator.setSurfaceControlTransactionFactory(mMockFactory);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPipAlphaAnimator.start();
            clearInvocations(mMockAnimateTransaction);
            mPipAlphaAnimator.end();
        });

        verify(mMockAnimateTransaction).setAlpha(mTestLeash, 0f);
    }


    // set up transaction chaining
    private void prepareTransaction(SurfaceControl.Transaction tx) {
        when(tx.setAlpha(any(SurfaceControl.class), anyFloat()))
                .thenReturn(tx);
        when(tx.setCornerRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(tx);
        when(tx.setShadowRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(tx);
    }
}
