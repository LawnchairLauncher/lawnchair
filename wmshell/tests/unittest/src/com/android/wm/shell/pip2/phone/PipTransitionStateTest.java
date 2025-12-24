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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.testing.AndroidTestingRunner;
import android.view.SurfaceControl;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.PipDesktopState;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * Unit test against {@link PipTransitionState}.
 *
 * This test mocks the PiP2 flag to be true.
 */
@RunWith(AndroidTestingRunner.class)
public class PipTransitionStateTest extends ShellTestCase {
    private static final String EXTRA_ENTRY_KEY = "extra_entry_key";
    private PipTransitionState mPipTransitionState;
    private PipTransitionState.PipTransitionStateChangedListener mStateChangedListener;
    private Parcelable mEmptyParcelable;

    @Mock
    private Handler mMainHandler;

    @Mock
    private PipDesktopState mMockPipDesktopState;

    @Before
    public void setUp() {
        mPipTransitionState = new PipTransitionState(mMainHandler, mMockPipDesktopState);
        mPipTransitionState.setState(PipTransitionState.UNDEFINED);
        mEmptyParcelable = new Bundle();
    }

    @Test
    public void testEnteredState_withoutExtra() {
        mStateChangedListener = (oldState, newState, extra) -> {
            Assert.assertEquals(PipTransitionState.ENTERED_PIP, newState);
            Assert.assertNull(extra);
        };
        mPipTransitionState.addPipTransitionStateChangedListener(mStateChangedListener);
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        mPipTransitionState.removePipTransitionStateChangedListener(mStateChangedListener);
    }

    @Test
    public void testEnteredState_withExtra() {
        mStateChangedListener = (oldState, newState, extra) -> {
            Assert.assertEquals(PipTransitionState.ENTERED_PIP, newState);
            Assert.assertNotNull(extra);
            Assert.assertEquals(mEmptyParcelable, extra.getParcelable(EXTRA_ENTRY_KEY));
        };
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_ENTRY_KEY, mEmptyParcelable);

        mPipTransitionState.addPipTransitionStateChangedListener(mStateChangedListener);
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP, extra);
        mPipTransitionState.removePipTransitionStateChangedListener(mStateChangedListener);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnteringState_withoutExtra() {
        mPipTransitionState.setState(PipTransitionState.ENTERING_PIP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSwipingToPipState_withoutExtra() {
        mPipTransitionState.setState(PipTransitionState.SWIPING_TO_PIP);
    }

    @Test
    public void testCustomState_withExtra_thenEntered_withoutExtra() {
        final int customState = mPipTransitionState.getCustomState();
        mStateChangedListener = (oldState, newState, extra) -> {
            if (newState == customState) {
                Assert.assertNotNull(extra);
                Assert.assertEquals(mEmptyParcelable, extra.getParcelable(EXTRA_ENTRY_KEY));
                return;
            } else if (newState == PipTransitionState.ENTERED_PIP) {
                Assert.assertNull(extra);
                return;
            }
            Assert.fail("Neither custom not ENTERED_PIP state is received.");
        };
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_ENTRY_KEY, mEmptyParcelable);

        mPipTransitionState.addPipTransitionStateChangedListener(mStateChangedListener);
        mPipTransitionState.setState(customState, extra);
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        mPipTransitionState.removePipTransitionStateChangedListener(mStateChangedListener);
    }

    @Test
    public void testBoundsChangeState_notInPip() {
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_ENTRY_KEY, mEmptyParcelable);

        mPipTransitionState.setState(PipTransitionState.UNDEFINED);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
        Assert.assertEquals(PipTransitionState.UNDEFINED, mPipTransitionState.getState());

        mPipTransitionState.setState(PipTransitionState.ENTERING_PIP, extra);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
        Assert.assertEquals(PipTransitionState.ENTERING_PIP, mPipTransitionState.getState());

        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
        Assert.assertEquals(PipTransitionState.EXITING_PIP, mPipTransitionState.getState());
    }

    @Test
    public void testShouldTransitionToState_scheduledBoundsChange_inPip_returnsTrue() {
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);

        Assert.assertTrue(mPipTransitionState.shouldTransitionToState(
                PipTransitionState.SCHEDULED_BOUNDS_CHANGE));
    }

    @Test
    public void testShouldTransitionToState_scheduledBoundsChange_notInPip_returnsFalse() {
        mPipTransitionState.setState(PipTransitionState.EXITED_PIP);

        Assert.assertFalse(mPipTransitionState.shouldTransitionToState(
                PipTransitionState.SCHEDULED_BOUNDS_CHANGE));
    }

    @Test
    public void testShouldTransitionToState_scheduledBoundsChange_dragToDesktop_returnsFalse() {
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        when(mMockPipDesktopState.isDragToDesktopInProgress()).thenReturn(true);

        Assert.assertFalse(mPipTransitionState.shouldTransitionToState(
                PipTransitionState.SCHEDULED_BOUNDS_CHANGE));
    }

    @Test
    public void testShouldTransitionToState_changingPipBounds_afterScheduling_returnsTrue() {
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_ENTRY_KEY, mEmptyParcelable);
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);

        Assert.assertTrue(mPipTransitionState.shouldTransitionToState(
                PipTransitionState.CHANGING_PIP_BOUNDS));
    }

    @Test
    public void shouldTransitionToState_scheduledBoundsChangeWhileChangingBounds_returnsFalse() {
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_ENTRY_KEY, mEmptyParcelable);
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
        mPipTransitionState.setState(PipTransitionState.CHANGING_PIP_BOUNDS, extra);

        Assert.assertFalse(mPipTransitionState.shouldTransitionToState(
                PipTransitionState.SCHEDULED_BOUNDS_CHANGE));
    }

    @Test
    public void testResetSameState_scheduledBoundsChange_doNotDispatchStateChanged() {
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_ENTRY_KEY, mEmptyParcelable);
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);

        mStateChangedListener = mock(PipTransitionState.PipTransitionStateChangedListener.class);
        verify(mStateChangedListener, never())
                .onPipTransitionStateChanged(anyInt(), anyInt(), any());

        mPipTransitionState.addPipTransitionStateChangedListener(mStateChangedListener);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
    }

    @Test
    public void testAddSameStateChangedListener_changeState_receiveOneCallback() {
        // Choose an initial state.
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_ENTRY_KEY, mEmptyParcelable);
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
        mPipTransitionState.setState(PipTransitionState.CHANGING_PIP_BOUNDS, extra);

        // Add the same PiP transition state changed listener twice.
        mStateChangedListener = mock(PipTransitionState.PipTransitionStateChangedListener.class);
        mPipTransitionState.addPipTransitionStateChangedListener(mStateChangedListener);
        mPipTransitionState.addPipTransitionStateChangedListener(mStateChangedListener);

        // Transition to a different valid state.
        mPipTransitionState.setState(PipTransitionState.CHANGED_PIP_BOUNDS);
        verify(mStateChangedListener, times(1)).onPipTransitionStateChanged(
                eq(PipTransitionState.CHANGING_PIP_BOUNDS),
                eq(PipTransitionState.CHANGED_PIP_BOUNDS), isNull());
    }

    @Test
    public void testResetOnIdlePipTransitionStateRunnable_whileIdle_removePrevRunnable() {
        when(mMainHandler.obtainMessage(anyInt())).thenAnswer(invocation ->
                new Message().setWhat(invocation.getArgument(0)));

        // pick an idle ENTERED_PIP state
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);

        int what = PipTransitionState.class.hashCode();

        final Runnable firstOnIdleRunnable = () -> {};

        mPipTransitionState.setOnIdlePipTransitionStateRunnable(firstOnIdleRunnable);
        verify(mMainHandler, times(1)).removeMessages(eq(what));
        verify(mMainHandler, times(1))
                .sendMessage(argThat(msg -> msg.getCallback() == firstOnIdleRunnable));

        clearInvocations(mMainHandler);

        final Runnable secondOnIdleRunnable = () -> {};
        mPipTransitionState.setOnIdlePipTransitionStateRunnable(secondOnIdleRunnable);
        verify(mMainHandler, times(1)).removeMessages(eq(what));
        verify(mMainHandler, times(1))
                .sendMessage(argThat(msg -> msg.getCallback() == secondOnIdleRunnable));
    }

    @Test
    public void testSetOnIdlePipTransitionStateRunnable_notIdle_postAndClearRunnableOnceIdle() {
        when(mMainHandler.obtainMessage(anyInt())).thenAnswer(invocation ->
                new Message().setWhat(invocation.getArgument(0)));

        // pick a non-idle state
        Bundle extra = new Bundle();
        extra.putParcelable(EXTRA_ENTRY_KEY, mEmptyParcelable);
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
        mPipTransitionState.setState(PipTransitionState.CHANGING_PIP_BOUNDS, extra);

        final Runnable onIdleRunnable = () -> {};
        mPipTransitionState.setOnIdlePipTransitionStateRunnable(onIdleRunnable);

        verify(mMainHandler, never()).sendMessage(any());

        // advance to an idle state
        mPipTransitionState.setState(PipTransitionState.CHANGED_PIP_BOUNDS);

        verify(mMainHandler, times(1))
                .sendMessage(argThat(msg -> msg.getCallback() == onIdleRunnable));

        Assert.assertNull("onIdle runnable not cleared",
                mPipTransitionState.getOnIdlePipTransitionStateRunnable());
    }

    @Test
    public void testSetIsDisplayChangeScheduled_toFalse_thenIdleWithoutRunnableSent() {
        when(mMainHandler.obtainMessage(anyInt())).thenAnswer(invocation ->
                new Message().setWhat(invocation.getArgument(0)));

        // Pick an initially idle ENTERED_PIP state
        mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);

        // Enter an non-idle state as PiP bounds change with the display
        mPipTransitionState.setIsDisplayChangeScheduled(true);
        Assert.assertFalse("PiP should not be idle", mPipTransitionState.isPipStateIdle());

        final Runnable onIdleRunnable = mock(Runnable.class);
        mPipTransitionState.setOnIdlePipTransitionStateRunnable(onIdleRunnable);

        // We are supposed to be in a non-idle state, so the runnable should not be posted yet.
        verify(mMainHandler, never()).sendMessage(any());

        mPipTransitionState.setIsDisplayChangeScheduled(false);
        // We aren't supposed to post the on-idle runnable upon reset of display change scheduled
        // flag, as we are expected to be put back into a non-idle state while display change plays.
        verify(mMainHandler, never()).sendMessage(any());
    }

    @Test
    public void testPostState_noImmediateStateChange_postedOnHandler() {
        mPipTransitionState.setState(PipTransitionState.UNDEFINED);
        mPipTransitionState.postState(PipTransitionState.SCHEDULED_ENTER_PIP);
        Assert.assertEquals(PipTransitionState.UNDEFINED, mPipTransitionState.getState());
        verify(mMainHandler, times(1)).post(any());
    }

    @Test
    public void testSetSwipePipToHomeState_thenResetSwipePipToHomeState() {
        final SurfaceControl overlay = mock(SurfaceControl.class);
        final Rect appBounds = new Rect(0, 0, 100, 100);
        mPipTransitionState.setSwipePipToHomeState(overlay, appBounds);

        Assert.assertTrue("Not in swipe PiP to home transition",
                mPipTransitionState.isInSwipePipToHomeTransition());
        Assert.assertEquals(overlay, mPipTransitionState.getSwipePipToHomeOverlay());
        Assert.assertEquals(appBounds, mPipTransitionState.getSwipePipToHomeAppBounds());

        mPipTransitionState.resetSwipePipToHomeState();
        Assert.assertFalse("swipe PiP to home transition flag not cleared",
                mPipTransitionState.isInSwipePipToHomeTransition());
        Assert.assertNull("swipe PiP to home overlay not cleared",
                mPipTransitionState.getSwipePipToHomeOverlay());
        Assert.assertEquals(new Rect(), mPipTransitionState.getSwipePipToHomeAppBounds());
    }
}
