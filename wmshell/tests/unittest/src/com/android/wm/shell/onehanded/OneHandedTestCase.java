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

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.onehanded.OneHandedController.SUPPORT_ONE_HANDED_MODE;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemProperties;
import android.testing.TestableContext;
import android.view.WindowManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.junit.Rule;
import org.mockito.Answers;
import org.mockito.Mock;

/**
 * Base class that does One Handed specific setup.
 */
public abstract class OneHandedTestCase extends ShellTestCase {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    protected Context mContext;

    @Rule
    public TestableContext mTestContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), null);

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    protected WindowManager mWindowManager;

    @Before
    public void setUpContext() {
        assumeTrue(SystemProperties.getBoolean(SUPPORT_ONE_HANDED_MODE, false));

        final DisplayManager dm = getTestContext().getSystemService(DisplayManager.class);
        mContext = getTestContext().createDisplayContext(dm.getDisplay(DEFAULT_DISPLAY));
    }

    @Before
    public void setUpWindowManager() {
        assumeTrue(SystemProperties.getBoolean(SUPPORT_ONE_HANDED_MODE, false));
        mWindowManager = getTestContext().getSystemService(WindowManager.class);
    }

    /** return testable context */
    protected TestableContext getTestContext() {
        return mTestContext;
    }

    /** return display context */
    protected Context getContext() {
        return mContext;
    }
}
