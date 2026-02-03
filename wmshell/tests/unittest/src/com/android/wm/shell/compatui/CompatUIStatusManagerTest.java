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

package com.android.wm.shell.compatui;

import static com.android.wm.shell.compatui.CompatUIStatusManager.COMPAT_UI_EDUCATION_HIDDEN;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Tests for {@link CompatUILayout}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:CompatUIStatusManagerTest
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class CompatUIStatusManagerTest extends ShellTestCase {

    private FakeCompatUIStatusManagerTest mTestState;
    private CompatUIStatusManager mStatusManager;

    @Before
    public void setUp() {
        mTestState = new FakeCompatUIStatusManagerTest();
        mStatusManager = new CompatUIStatusManager(mTestState.mWriter, mTestState.mReader);
    }

    @Test
    public void isEducationShown() {
        assertFalse(mStatusManager.isEducationVisible());

        mStatusManager.onEducationShown();
        assertTrue(mStatusManager.isEducationVisible());

        mStatusManager.onEducationHidden();
        assertFalse(mStatusManager.isEducationVisible());
    }

    @Test
    public void valuesAreCached() {
        // At the beginning the value is not read or written because
        // we access the reader in lazy way.
        mTestState.assertReaderInvocations(0);
        mTestState.assertWriterInvocations(0);

        // We read the value when we start. Initial value is hidden.
        assertFalse(mStatusManager.isEducationVisible());
        mTestState.assertReaderInvocations(1);
        mTestState.assertWriterInvocations(0);

        // We send the event for the same state which is not written.
        mStatusManager.onEducationHidden();
        assertFalse(mStatusManager.isEducationVisible());
        mTestState.assertReaderInvocations(1);
        mTestState.assertWriterInvocations(0);

        // We send the event for the different state which is written but
        // not read again.
        mStatusManager.onEducationShown();
        assertTrue(mStatusManager.isEducationVisible());
        mTestState.assertReaderInvocations(1);
        mTestState.assertWriterInvocations(1);

        // We read multiple times and we don't read the value again
        mStatusManager.isEducationVisible();
        mStatusManager.isEducationVisible();
        mStatusManager.isEducationVisible();
        mTestState.assertReaderInvocations(1);
        mTestState.assertWriterInvocations(1);

        // We write different values. Writer  is only accessed when
        // the value changes.
        mStatusManager.onEducationHidden(); // change
        mStatusManager.onEducationHidden();
        mStatusManager.onEducationShown(); // change
        mStatusManager.onEducationShown();
        mStatusManager.onEducationHidden(); // change
        mStatusManager.onEducationShown(); // change
        mTestState.assertReaderInvocations(1);
        mTestState.assertWriterInvocations(5);
    }

    static class FakeCompatUIStatusManagerTest {

        int mCurrentStatus = COMPAT_UI_EDUCATION_HIDDEN;

        int mReaderInvocations;

        int mWriterInvocations;

        final IntSupplier mReader = () -> {
            mReaderInvocations++;
            return mCurrentStatus;
        };

        final IntConsumer mWriter = newStatus -> {
            mWriterInvocations++;
            mCurrentStatus = newStatus;
        };

        void assertWriterInvocations(int expected) {
            assertEquals(expected, mWriterInvocations);
        }

        void assertReaderInvocations(int expected) {
            assertEquals(expected, mReaderInvocations);
        }

    }
}
