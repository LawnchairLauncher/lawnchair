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

import static com.android.wm.shell.onehanded.OneHandedState.STATE_ACTIVE;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_ENTERING;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_EXITING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Rect;
import android.os.Handler;
import android.testing.AndroidTestingRunner;
import android.util.ArrayMap;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TaskStackListenerImpl;
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
public class OneHandedStateTest extends OneHandedTestCase {

    Display mDisplay;
    DisplayLayout mDisplayLayout;
    OneHandedAccessibilityUtil mOneHandedAccessibilityUtil;
    OneHandedController mSpiedOneHandedController;
    OneHandedTimeoutHandler mSpiedTimeoutHandler;
    OneHandedState mSpiedState;

    @Mock
    ShellInit mMockShellInit;
    @Mock
    ShellCommandHandler mMockShellCommandHandler;
    @Mock
    ShellController mMockShellController;
    @Mock
    DisplayController mMockDisplayController;
    @Mock
    OneHandedDisplayAreaOrganizer mMockDisplayAreaOrganizer;
    @Mock
    OneHandedTouchHandler mMockTouchHandler;
    @Mock
    OneHandedTutorialHandler mMockTutorialHandler;
    @Mock
    OneHandedSettingsUtil mMockSettingsUitl;
    @Mock
    OneHandedUiEventLogger mMockUiEventLogger;
    @Mock
    TaskStackListenerImpl mMockTaskStackListener;
    @Mock
    ShellExecutor mMockShellMainExecutor;
    @Mock
    Handler mMockShellMainHandler;

    final boolean mDefaultEnabled = true;
    final boolean mDefaultSwipeToNotificationEnabled = false;
    final boolean mDefaultTapAppToExitEnabled = true;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDisplay = mContext.getDisplay();
        mDisplayLayout = new DisplayLayout(mContext, mDisplay);
        mSpiedTimeoutHandler = spy(new OneHandedTimeoutHandler(mMockShellMainExecutor));
        mSpiedState = spy(new OneHandedState());

        when(mMockDisplayController.getDisplay(anyInt())).thenReturn(mDisplay);
        when(mMockDisplayAreaOrganizer.getDisplayAreaTokenMap()).thenReturn(new ArrayMap<>());
        when(mMockSettingsUitl.getSettingsOneHandedModeEnabled(any(), anyInt())).thenReturn(
                mDefaultEnabled);
        when(mMockSettingsUitl.getSettingsOneHandedModeTimeout(any(), anyInt())).thenReturn(
                OneHandedSettingsUtil.ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS);
        when(mMockSettingsUitl.getSettingsTapsAppToExit(any(), anyInt())).thenReturn(
                mDefaultTapAppToExitEnabled);
        when(mMockSettingsUitl.getSettingsSwipeToNotificationEnabled(any(), anyInt())).thenReturn(
                mDefaultSwipeToNotificationEnabled);

        when(mMockDisplayAreaOrganizer.getLastDisplayBounds()).thenReturn(
                new Rect(0, 0, mDisplayLayout.width(), mDisplayLayout.height()));
        when(mMockDisplayAreaOrganizer.getDisplayLayout()).thenReturn(mDisplayLayout);

        mOneHandedAccessibilityUtil = new OneHandedAccessibilityUtil(mContext);
        mSpiedOneHandedController = spy(new OneHandedController(
                mContext,
                mMockShellInit,
                mMockShellCommandHandler,
                mMockShellController,
                mMockDisplayController,
                mMockDisplayAreaOrganizer,
                mMockTouchHandler,
                mMockTutorialHandler,
                mMockSettingsUitl,
                mOneHandedAccessibilityUtil,
                mSpiedTimeoutHandler,
                mSpiedState,
                mMockUiEventLogger,
                mMockTaskStackListener,
                mMockShellMainExecutor,
                mMockShellMainHandler)
        );
    }

    @Test
    public void testState_stateEntering_isTransitioning() {
        mSpiedState.setState(STATE_ENTERING);

        assertThat(mSpiedState.isTransitioning()).isTrue();
    }

    @Test
    public void testState_stateExiting_isTransitioning() {
        mSpiedState.setState(STATE_EXITING);

        assertThat(mSpiedState.isTransitioning()).isTrue();
    }

    @Test
    public void testInEnteringState_shouldSkipDupTrigger() {
        when(mSpiedState.getState()).thenReturn(STATE_ENTERING);
        when(mSpiedState.isTransitioning()).thenReturn(true);
        when(mSpiedState.isInOneHanded()).thenReturn(false);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testInActiveState_shouldSkipDupTrigger() {
        when(mSpiedState.getState()).thenReturn(STATE_ACTIVE);
        when(mSpiedState.isTransitioning()).thenReturn(false);
        when(mSpiedState.isInOneHanded()).thenReturn(true);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testInActiveState_canExit() {
        when(mSpiedState.getState()).thenReturn(STATE_ACTIVE);
        when(mSpiedState.isTransitioning()).thenReturn(false);
        mSpiedOneHandedController.stopOneHanded();

        verify(mSpiedState).setState(STATE_EXITING);
    }

    @Test
    public void testInEnteringState_shouldSkipExitAction() {
        when(mSpiedState.getState()).thenReturn(STATE_ENTERING);
        when(mSpiedState.isTransitioning()).thenReturn(true);
        mSpiedOneHandedController.stopOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testInExitingState_shouldSkipStartAction() {
        when(mSpiedState.getState()).thenReturn(STATE_EXITING);
        when(mSpiedState.isTransitioning()).thenReturn(true);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testInExitingState_shouldSkipStopAction() {
        when(mSpiedState.getState()).thenReturn(STATE_EXITING);
        when(mSpiedState.isTransitioning()).thenReturn(true);
        mSpiedOneHandedController.stopOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testInActiveState_disableOHM_shouldStopOHM() {
        when(mSpiedState.getState()).thenReturn(STATE_ACTIVE);
        when(mSpiedState.isTransitioning()).thenReturn(false);
        when(mSpiedState.isInOneHanded()).thenReturn(true);
        mSpiedOneHandedController.setOneHandedEnabled(false);

        verify(mMockShellMainExecutor).execute(any());
    }
}
