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

package com.android.wm.shell.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.IWindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the display controller.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:DisplayControllerTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayControllerTests extends ShellTestCase {

    private @Mock Context mContext;
    private @Mock IWindowManager mWM;
    private @Mock ShellInit mShellInit;
    private @Mock ShellExecutor mMainExecutor;
    private DisplayController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mController = new DisplayController(mContext, mWM, mShellInit, mMainExecutor);
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), eq(mController));
    }
}
