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
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.kotlin.VerificationKt.clearInvocations;
import static org.mockito.kotlin.VerificationKt.never;
import static org.mockito.kotlin.VerificationKt.times;
import static org.mockito.kotlin.VerificationKt.verify;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.pip2.phone.PipTransitionState;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test against {@link PipDisplayChangeObserver}
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner.class)
public class PipDisplayChangeObserverTest {
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private PipBoundsState mMockPipBoundsState;
    private PipDisplayChangeObserver mPipDisplayChangeObserver;

    @Mock private SurfaceControl.Transaction mStartTx;
    @Mock private SurfaceControl.Transaction mFinishTx;
    @Mock private WindowContainerToken mPipToken;
    @Mock private WindowContainerToken mDisplayToken;
    @Mock private SurfaceControl mPipLeash;
    @Mock private SurfaceControl mDisplayLeash;

    private final Rect mPipStartBounds = new Rect(0, 0, 100, 100);
    private final Rect mPipEndBounds = new Rect(0, 0, 200, 200);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mPipDisplayChangeObserver = new PipDisplayChangeObserver(mMockPipTransitionState,
                mMockPipBoundsState);
    }

    @Test
    public void onTransitionReady_withPipAndDisplayChange_cachesDisplayChangeTransition() {
        Assert.assertNull(mPipDisplayChangeObserver.getDisplayChangeTransition());

        final IBinder transitionToken = new Binder();
        mPipDisplayChangeObserver.onTransitionReady(transitionToken,
                createPipBoundsChangingWithDisplayInfo(), mStartTx, mFinishTx);

        Assert.assertNotNull(mPipDisplayChangeObserver.getDisplayChangeTransition());
        Assert.assertSame(transitionToken,
                mPipDisplayChangeObserver.getDisplayChangeTransition().first);
    }

    @Test
    public void onTransitionFinished_withPipAndDisplayChange_updatePipState() {
        final IBinder transitionToken = new Binder();
        final TransitionInfo info = createPipBoundsChangingWithDisplayInfo();

        mPipDisplayChangeObserver.onTransitionReady(transitionToken, info, mStartTx, mFinishTx);
        clearInvocations(mMockPipTransitionState);
        mPipDisplayChangeObserver.onTransitionFinished(transitionToken, false /* aborted */);

        verify(mMockPipTransitionState, times(1)).setIsPipBoundsChangingWithDisplay(eq(false));
        verify(mMockPipBoundsState, times(1)).setBounds(eq(mPipEndBounds));
        Assert.assertNull(mPipDisplayChangeObserver.getDisplayChangeTransition());
    }

    @Test
    public void onTransitionMerged_withPipAndDisplayChange_updatePipState() {
        final IBinder transitionToken = new Binder();
        final IBinder playingTransitionToken = new Binder();
        final TransitionInfo info = createPipBoundsChangingWithDisplayInfo();

        mPipDisplayChangeObserver.onTransitionReady(transitionToken, info, mStartTx, mFinishTx);
        clearInvocations(mMockPipTransitionState);
        mPipDisplayChangeObserver.onTransitionMerged(transitionToken, playingTransitionToken);

        verify(mMockPipTransitionState, times(1)).setIsPipBoundsChangingWithDisplay(eq(false));
        verify(mMockPipBoundsState, times(1)).setBounds(eq(mPipEndBounds));
        Assert.assertNull(mPipDisplayChangeObserver.getDisplayChangeTransition());
    }

    @Test
    public void onTransitionFinished_withDisplayChangeOnly_noPipStateUpdate() {
        final IBinder transitionToken = new Binder();
        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0 /* flags */);
        info.addChange(createDisplayChange());

        mPipDisplayChangeObserver.onTransitionReady(transitionToken, info, mStartTx, mFinishTx);
        clearInvocations(mMockPipTransitionState);
        mPipDisplayChangeObserver.onTransitionFinished(transitionToken, false /* aborted */);

        verify(mMockPipTransitionState, times(1)).setIsPipBoundsChangingWithDisplay(eq(false));
        verify(mMockPipBoundsState, never()).setBounds(any());
        Assert.assertNull(mPipDisplayChangeObserver.getDisplayChangeTransition());
    }

    @Test
    public void onTransitionMerged_withDisplayChangeOnly_noPipStateUpdate() {
        final IBinder transitionToken = new Binder();
        final IBinder playingTransitionToken = new Binder();
        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0 /* flags */);
        info.addChange(createDisplayChange());

        mPipDisplayChangeObserver.onTransitionReady(transitionToken, info, mStartTx, mFinishTx);
        clearInvocations(mMockPipTransitionState);
        mPipDisplayChangeObserver.onTransitionMerged(transitionToken, playingTransitionToken);

        verify(mMockPipTransitionState, times(1)).setIsPipBoundsChangingWithDisplay(eq(false));
        verify(mMockPipBoundsState, never()).setBounds(eq(mPipEndBounds));
        Assert.assertNull(mPipDisplayChangeObserver.getDisplayChangeTransition());
    }

    private TransitionInfo createPipBoundsChangingWithDisplayInfo() {
        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0 /* flags */);
        // In this case it doesn't really make a difference, but top children should be added first.
        info.addChange(createPipChange());
        info.addChange(createDisplayChange());
        return info;
    }

    private TransitionInfo.Change createPipChange() {
        final TransitionInfo.Change pipChange = new TransitionInfo.Change(mPipToken, mPipLeash);
        final ActivityManager.RunningTaskInfo pipTaskInfo = new ActivityManager.RunningTaskInfo();
        pipTaskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_PINNED);
        pipChange.setTaskInfo(pipTaskInfo);
        pipChange.setStartAbsBounds(mPipStartBounds);
        pipChange.setEndAbsBounds(mPipEndBounds);
        return pipChange;
    }

    private TransitionInfo.Change createDisplayChange() {
        final TransitionInfo.Change displayChange =
                new TransitionInfo.Change(mDisplayToken, mDisplayLeash);
        displayChange.setMode(TRANSIT_CHANGE);
        displayChange.setFlags(FLAG_IS_DISPLAY);
        return displayChange;
    }
}
