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

package com.android.wm.shell;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableContext;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.protolog.ProtoLog;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.mockito.MockitoAnnotations;

/**
 * Base class that does shell test case setup.
 */
public abstract class ShellTestCase {

    @ClassRule
    public static final SetFlagsRule.ClassRule mClassRule = new SetFlagsRule.ClassRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = mClassRule.createSetFlagsRule();

    protected TestableContext mContext;
    private PackageManager mPm;

    @Before
    public void shellSetup() {
        // Disable protolog tool when running the tests from studio
        ProtoLog.REQUIRE_PROTOLOGTOOL = false;

        // Make sure ProtoLog is initialized before any logging occurs.
        ProtoLog.init();

        MockitoAnnotations.initMocks(this);
        final Context context =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        mPm = context.getPackageManager();
        mContext = new TestableContext(
                context.createDisplayContext(dm.getDisplay(DEFAULT_DISPLAY)));

        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();
    }

    @After
    public void shellTearDown() {
        // In case someone set up a TestableLooper during the test, get rid of it because
        // TestableLooper intrinsically leaks all past test method member fields in a
        // big static list.
        //
        // TODO (b/261039202): don't use TestableLooper in WmShell tests at all.
        if (TestableLooper.get(this) != null) {
            TestableLooper.get(this).processAllMessages();
            TestableLooper.remove(this);
        }

        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    protected Context getContext() {
        return mContext;
    }

    /**
     * Makes an assumption that the test device is a TV device, used to guard tests that should
     * only be run on TVs.
     */
    protected void assumeTelevision() {
        assumeTrue(isTelevision());
    }

    /**
     * Returns whether this test device is a TV device.
     */
    protected boolean isTelevision() {
        return mPm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || mPm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY);
    }
}
