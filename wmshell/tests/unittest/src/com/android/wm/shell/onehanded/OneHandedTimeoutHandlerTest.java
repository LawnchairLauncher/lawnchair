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

import static com.android.wm.shell.onehanded.OneHandedSettingsUtil.ONE_HANDED_TIMEOUT_LONG_IN_SECONDS;
import static com.android.wm.shell.onehanded.OneHandedSettingsUtil.ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS;
import static com.android.wm.shell.onehanded.OneHandedSettingsUtil.ONE_HANDED_TIMEOUT_NEVER;
import static com.android.wm.shell.onehanded.OneHandedSettingsUtil.ONE_HANDED_TIMEOUT_SHORT_IN_SECONDS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.TestShellExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
public class OneHandedTimeoutHandlerTest extends OneHandedTestCase {
    private OneHandedTimeoutHandler mTimeoutHandler;
    private TestShellExecutor mMainExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMainExecutor = new TestShellExecutor();
        mTimeoutHandler = Mockito.spy(new OneHandedTimeoutHandler(mMainExecutor));
    }

    @Test
    public void testTimeoutHandler_getTimeout_defaultMedium() {
        assertThat(mTimeoutHandler.getTimeout()).isEqualTo(
                ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS);
    }

    @Test
    public void testTimeoutHandler_setNewTime_resetTimer() {
        mTimeoutHandler.setTimeout(ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS);
        verify(mTimeoutHandler).resetTimer();
        assertTrue(mTimeoutHandler.hasScheduledTimeout());
    }

    @Test
    public void testSetTimeoutNever_neverResetTimer() {
        mTimeoutHandler.setTimeout(ONE_HANDED_TIMEOUT_NEVER);
        assertFalse(mTimeoutHandler.hasScheduledTimeout());
    }

    @Test
    public void testSetTimeoutShort() {
        mTimeoutHandler.setTimeout(ONE_HANDED_TIMEOUT_SHORT_IN_SECONDS);
        verify(mTimeoutHandler).resetTimer();
        assertThat(mTimeoutHandler.getTimeout()).isEqualTo(ONE_HANDED_TIMEOUT_SHORT_IN_SECONDS);
        assertTrue(mTimeoutHandler.hasScheduledTimeout());
    }

    @Test
    public void testSetTimeoutMedium() {
        mTimeoutHandler.setTimeout(ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS);
        verify(mTimeoutHandler).resetTimer();
        assertThat(mTimeoutHandler.getTimeout()).isEqualTo(ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS);
        assertTrue(mTimeoutHandler.hasScheduledTimeout());
    }

    @Test
    public void testSetTimeoutLong() {
        mTimeoutHandler.setTimeout(ONE_HANDED_TIMEOUT_LONG_IN_SECONDS);
        assertThat(mTimeoutHandler.getTimeout()).isEqualTo(ONE_HANDED_TIMEOUT_LONG_IN_SECONDS);
    }

    @Test
    public void testDragging_shouldRemoveAndSendEmptyMessageDelay() {
        mTimeoutHandler.setTimeout(ONE_HANDED_TIMEOUT_LONG_IN_SECONDS);
        mTimeoutHandler.resetTimer();
        assertTrue(mTimeoutHandler.hasScheduledTimeout());
    }
}
