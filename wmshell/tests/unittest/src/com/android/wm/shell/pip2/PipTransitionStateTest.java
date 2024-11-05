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

package com.android.wm.shell.pip2;

import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.testing.AndroidTestingRunner;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.pip.PhoneSizeSpecSource;
import com.android.wm.shell.pip2.phone.PipTransitionState;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * Unit test against {@link PhoneSizeSpecSource}.
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

    @Before
    public void setUp() {
        mPipTransitionState = new PipTransitionState(mMainHandler);
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
}
