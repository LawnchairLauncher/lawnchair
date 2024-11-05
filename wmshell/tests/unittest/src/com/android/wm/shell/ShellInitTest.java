/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class ShellInitTest extends ShellTestCase {

    @Mock private ShellExecutor mMainExecutor;

    private ShellInit mImpl;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mImpl = new ShellInit(mMainExecutor);
    }

    @Test
    public void testAddInitCallbacks_expectCalledInOrder() {
        ArrayList<Integer> results = new ArrayList<>();
        mImpl.addInitCallback(() -> {
            results.add(1);
        }, new Object());
        mImpl.addInitCallback(() -> {
            results.add(2);
        }, new Object());
        mImpl.addInitCallback(() -> {
            results.add(3);
        }, new Object());
        mImpl.init();
        assertTrue(results.get(0) == 1);
        assertTrue(results.get(1) == 2);
        assertTrue(results.get(2) == 3);
    }

    @Test
    public void testNoInitCallbacksAfterInit_expectException() {
        mImpl.init();
        try {
            mImpl.addInitCallback(() -> {}, new Object());
            fail("Expected exception when adding callback after init");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testDoubleInit_expectNoOp() {
        ArrayList<Integer> results = new ArrayList<>();
        mImpl.addInitCallback(() -> {
            results.add(1);
        }, new Object());
        mImpl.init();
        assertTrue(results.size() == 1);
        mImpl.init();
        assertTrue(results.size() == 1);
    }
}
