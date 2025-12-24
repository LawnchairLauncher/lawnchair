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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.VerificationKt.clearInvocations;
import static org.mockito.kotlin.VerificationKt.never;
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
    public void onTransitionReady_withDisplayChange_cachesTransition() {
        final IBinder transition = new Binder();
        final TransitionInfo info = createPipBoundsChangingWithDisplayInfo();

        assertTrue("Map should be empty before test",
                mPipDisplayChangeObserver.getDisplayChangeTransitions().isEmpty());

        mPipDisplayChangeObserver.onTransitionReady(transition, info, mStartTx, mFinishTx);

        assertNotNull("Display change transition should be cached",
                mPipDisplayChangeObserver.getDisplayChangeTransitions().get(transition));
    }

    @Test
    public void onTransitionReady_withoutDisplayChange_doesNotCacheTransition() {
        final IBinder transition = new Binder();
        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0); // No FLAG_IS_DISPLAY

        mPipDisplayChangeObserver.onTransitionReady(transition, info, mStartTx, mFinishTx);

        assertTrue("Map should remain empty for non-display-change transitions",
                mPipDisplayChangeObserver.getDisplayChangeTransitions().isEmpty());
    }

    @Test
    public void onTransitionStarting_withPipChange_updatesPipState() {
        when(mMockPipTransitionState.isPipStateIdle()).thenReturn(true);

        final IBinder transition = new Binder();
        final TransitionInfo info = createPipBoundsChangingWithDisplayInfo();
        mPipDisplayChangeObserver.onTransitionReady(transition, info, mStartTx, mFinishTx);

        mPipDisplayChangeObserver.onTransitionStarting(transition);

        verify(mMockPipTransitionState).setIsDisplayChangeScheduled(false);
        verify(mMockPipTransitionState).setState(
                eq(PipTransitionState.SCHEDULED_BOUNDS_CHANGE), any());

        assertNotNull("Transition should remain cached during animation",
                mPipDisplayChangeObserver.getDisplayChangeTransitions().get(transition));
    }

    @Test
    public void onTransitionStarting_withNonIdlePipChange_updatesPipState() {
        when(mMockPipTransitionState.isPipStateIdle()).thenReturn(false);

        final IBinder transition = new Binder();
        final TransitionInfo info = createPipBoundsChangingWithDisplayInfo();
        mPipDisplayChangeObserver.onTransitionReady(transition, info, mStartTx, mFinishTx);

        mPipDisplayChangeObserver.onTransitionStarting(transition);

        verify(mMockPipTransitionState).setIsDisplayChangeScheduled(false);

        // Can't have state advancements when in a non-idle PiP state.
        verify(mMockPipTransitionState, never()).setState(anyInt(), any());

        assertNotNull("Transition should remain cached during animation",
                mPipDisplayChangeObserver.getDisplayChangeTransitions().get(transition));
    }

    @Test
    public void onTransitionFinished_withPipChange_updatesPipStateAndCleansUp() {
        when(mMockPipTransitionState.isPipStateIdle()).thenReturn(true);
        when(mMockPipTransitionState.isInPip()).thenReturn(true);

        final IBinder transition = new Binder();
        final TransitionInfo info = createPipBoundsChangingWithDisplayInfo();
        mPipDisplayChangeObserver.onTransitionReady(transition, info, mStartTx, mFinishTx);
        mPipDisplayChangeObserver.onTransitionStarting(transition);
        clearInvocations(mMockPipTransitionState);

        mPipDisplayChangeObserver.onTransitionFinished(transition, false /* aborted */);

        verify(mMockPipBoundsState).setBounds(eq(mPipEndBounds));
        verify(mMockPipTransitionState).setState(PipTransitionState.CHANGED_PIP_BOUNDS);
        assertTrue("Transition should be removed from cache after finishing",
                mPipDisplayChangeObserver.getDisplayChangeTransitions().isEmpty());
    }

    @Test
    public void onTransitionMerged_withPipChange_updatesPipStateAndCleansUp() {
        when(mMockPipTransitionState.isPipStateIdle()).thenReturn(true);
        when(mMockPipTransitionState.isInPip()).thenReturn(true);

        final IBinder mergedTransition = new Binder();
        final IBinder playingTransition = new Binder();
        final TransitionInfo info = createPipBoundsChangingWithDisplayInfo();
        mPipDisplayChangeObserver.onTransitionReady(mergedTransition, info, mStartTx, mFinishTx);
        mPipDisplayChangeObserver.onTransitionStarting(mergedTransition);
        clearInvocations(mMockPipTransitionState);

        mPipDisplayChangeObserver.onTransitionMerged(mergedTransition, playingTransition);

        verify(mMockPipBoundsState).setBounds(eq(mPipEndBounds));
        verify(mMockPipTransitionState).setState(PipTransitionState.CHANGED_PIP_BOUNDS);
        assertTrue("Transition should be removed from cache after merging",
                mPipDisplayChangeObserver.getDisplayChangeTransitions().isEmpty());
    }

    @Test
    public void onTransitionFinished_displayChangeOnly_noPipStateUpdateAndCleansUp() {
        final IBinder transition = new Binder();
        final TransitionInfo info = createDisplayChangeOnlyInfo();
        mPipDisplayChangeObserver.onTransitionReady(transition, info, mStartTx, mFinishTx);

        mPipDisplayChangeObserver.onTransitionStarting(transition);
        verify(mMockPipTransitionState, never()).setState(anyInt());

        clearInvocations(mMockPipTransitionState);

        mPipDisplayChangeObserver.onTransitionFinished(transition, false /* aborted */);

        verify(mMockPipBoundsState, never()).setBounds(any());
        verify(mMockPipTransitionState, never()).setState(anyInt());
        assertTrue("Transition should be removed from cache after finishing",
                mPipDisplayChangeObserver.getDisplayChangeTransitions().isEmpty());
    }

    private TransitionInfo createPipBoundsChangingWithDisplayInfo() {
        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0 /* flags */);
        // In this case it doesn't really make a difference, but top children should be added first.
        info.addChange(createPipChange());
        info.addChange(createDisplayChange());
        return info;
    }

    private TransitionInfo createDisplayChangeOnlyInfo() {
        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0 /* flags */);
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
