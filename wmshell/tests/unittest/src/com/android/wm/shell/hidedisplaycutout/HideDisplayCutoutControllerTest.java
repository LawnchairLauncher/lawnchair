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

package com.android.wm.shell.hidedisplaycutout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class HideDisplayCutoutControllerTest extends ShellTestCase {
    private TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), null);

    @Mock
    private ShellCommandHandler mShellCommandHandler;
    @Mock
    private ShellController mShellController;
    @Mock
    private HideDisplayCutoutOrganizer mMockDisplayAreaOrganizer;

    private HideDisplayCutoutController mHideDisplayCutoutController;
    private ShellInit mShellInit;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mShellInit = spy(new ShellInit(mock(ShellExecutor.class)));
        mHideDisplayCutoutController = new HideDisplayCutoutController(mContext, mShellInit,
                mShellCommandHandler, mShellController, mMockDisplayAreaOrganizer);
        mShellInit.init();
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void instantiateController_registerDumpCallback() {
        verify(mShellCommandHandler, times(1)).addDumpCallback(any(), any());
    }

    @Test
    public void instantiateController_registerConfigChangeListener() {
        verify(mShellController, times(1)).addConfigurationChangeListener(any());
    }

    @Test
    public void testToggleHideDisplayCutout_On() {
        mHideDisplayCutoutController.mEnabled = false;
        mContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.bool.config_hideDisplayCutoutWithDisplayArea, true);
        reset(mMockDisplayAreaOrganizer);
        mHideDisplayCutoutController.updateStatus();
        verify(mMockDisplayAreaOrganizer).enableHideDisplayCutout();
    }

    @Test
    public void testToggleHideDisplayCutout_Off() {
        mHideDisplayCutoutController.mEnabled = true;
        mContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.bool.config_hideDisplayCutoutWithDisplayArea, false);
        mHideDisplayCutoutController.updateStatus();
        verify(mMockDisplayAreaOrganizer).disableHideDisplayCutout();
    }
}
