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

import static com.android.wm.shell.onehanded.OneHandedState.STATE_ENTERING;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_NONE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
public class OneHandedTutorialHandlerTest extends OneHandedTestCase {
    Display mDisplay;
    DisplayLayout mDisplayLayout;
    OneHandedTimeoutHandler mTimeoutHandler;
    OneHandedState mSpiedTransitionState;
    OneHandedTutorialHandler mSpiedTutorialHandler;

    @Mock
    ShellExecutor mMockShellMainExecutor;
    @Mock
    OneHandedSettingsUtil mMockSettingsUtil;
    @Mock
    WindowManager mMockWindowManager;
    @Mock
    BackgroundWindowManager mMockBackgroundWindowManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockSettingsUtil.getTutorialShownCounts(any(), anyInt())).thenReturn(0);

        mDisplay = mContext.getDisplay();
        mDisplayLayout = new DisplayLayout(getTestContext().getApplicationContext(), mDisplay);
        mSpiedTransitionState = spy(new OneHandedState());
        mSpiedTutorialHandler = spy(
                new OneHandedTutorialHandler(mContext, mMockSettingsUtil, mMockWindowManager,
                        mMockBackgroundWindowManager));
        mTimeoutHandler = new OneHandedTimeoutHandler(mMockShellMainExecutor);
    }

    @Test
    public void testDefaultZeroShownCounts_doNotAttachWindow() {
        verify(mMockShellMainExecutor, never()).execute(any());
    }

    @Test
    public void testOnStateChangedEntering_createViewAndAttachToWindow() {
        when(mSpiedTutorialHandler.isAttached()).thenReturn(true);
        try {
            mSpiedTutorialHandler.onStateChanged(STATE_ENTERING);
        } catch (ClassCastException e) {
            // no-op, just assert createViewAndAttachToWindow() to be called
        }

        verify(mSpiedTutorialHandler).createViewAndAttachToWindow(any());
    }

    @Test
    public void testOnStateChangedNone_removeViewAndAttachToWindow() {
        when(mSpiedTutorialHandler.isAttached()).thenReturn(true);
        try {
            mSpiedTutorialHandler.onStateChanged(STATE_NONE);
        } catch (ClassCastException e) {
            // no-op, just assert removeTutorialFromWindowManager() to be called
        } catch (NullPointerException e) {
            // no-op, just assert removeTutorialFromWindowManager() to be called
        }

        verify(mSpiedTutorialHandler).removeTutorialFromWindowManager();
    }

    @Test
    public void testOnStateChangedNone_shouldNotAttachWindow() {
        when(mSpiedTutorialHandler.isAttached()).thenReturn(true);
        try {
            mSpiedTutorialHandler.onStateChanged(STATE_NONE);
        } catch (ClassCastException e) {
            // no-op, just assert setTutorialShownCountIncrement() never be called
        } catch (NullPointerException e) {
            // no-op, just assert setTutorialShownCountIncrement() never be called
        }

        verify(mSpiedTutorialHandler, never()).createViewAndAttachToWindow(any());
    }

    @Test
    public void testOnConfigurationChanged_shouldUpdateViewContent() {
        when(mSpiedTutorialHandler.isAttached()).thenReturn(true);
        try {
            mSpiedTutorialHandler.onStateChanged(STATE_ENTERING);
        } catch (ClassCastException e) {
            // no-op, set current state for test onConfigurationChanged()
        }
        try {
            mSpiedTutorialHandler.onConfigurationChanged();
        } catch (ClassCastException e) {
        } catch (NullPointerException e) {
            // no-op, just assert removeTutorialFromWindowManager() be called,
            // and createViewAndAttachToWindow() be called twice
        }

        verify(mSpiedTutorialHandler).createViewAndAttachToWindow(any());
        verify(mSpiedTutorialHandler).removeTutorialFromWindowManager();
    }
}
