/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.launcher3;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.KeyEvent;

import com.android.launcher3.util.FocusLogic;

/**
 * Tests the {@link FocusLogic} class that handles key event based focus handling.
 */
@SmallTest
public final class FocusLogicTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Nothing to set up as this class only tests static methods.
    }

    @Override
    protected void tearDown() throws Exception {
        // Nothing to tear down as this class only tests static methods.
    }

    public void testShouldConsume() {
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DPAD_LEFT));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DPAD_RIGHT));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DPAD_UP));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DPAD_DOWN));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_MOVE_HOME));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_MOVE_END));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_PAGE_UP));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_PAGE_DOWN));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_DEL));
         assertTrue(FocusLogic.shouldConsume(KeyEvent.KEYCODE_FORWARD_DEL));
    }

    public void testCreateSparseMatrix() {
         // Either, 1) create a helper method to generate/instantiate all possible cell layout that
         // may get created in real world to test this method. OR 2) Move all the matrix
         // management routine to celllayout and write tests for them.
    }
}
