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
import static com.android.wm.shell.onehanded.OneHandedState.STATE_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Rect;
import android.os.Handler;
import android.testing.AndroidTestingRunner;
import android.util.ArrayMap;
import android.view.Display;
import android.view.Surface;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.sysui.ShellSharedConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
public class OneHandedControllerTest extends OneHandedTestCase {

    Display mDisplay;
    OneHandedAccessibilityUtil mOneHandedAccessibilityUtil;
    OneHandedController mSpiedOneHandedController;
    OneHandedTimeoutHandler mSpiedTimeoutHandler;
    OneHandedState mSpiedTransitionState;
    ShellInit mShellInit;

    @Mock
    ShellCommandHandler mMockShellCommandHandler;
    @Mock
    ShellController mMockShellController;
    @Mock
    DisplayLayout mMockDisplayLayout;
    @Mock
    DisplayController mMockDisplayController;
    @Mock
    OneHandedDisplayAreaOrganizer mMockDisplayAreaOrganizer;
    @Mock
    OneHandedEventCallback mMockEventCallback;
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
        mMockDisplayLayout = Mockito.mock(DisplayLayout.class);
        mSpiedTimeoutHandler = spy(new OneHandedTimeoutHandler(mMockShellMainExecutor));
        mSpiedTransitionState = spy(new OneHandedState());

        when(mMockDisplayController.getDisplay(anyInt())).thenReturn(mDisplay);
        when(mMockDisplayController.getDisplayLayout(anyInt())).thenReturn(null);
        when(mMockDisplayAreaOrganizer.getDisplayAreaTokenMap()).thenReturn(new ArrayMap<>());
        when(mMockDisplayAreaOrganizer.isReady()).thenReturn(true);
        when(mMockSettingsUitl.getSettingsOneHandedModeEnabled(any(), anyInt())).thenReturn(
                mDefaultEnabled);
        when(mMockSettingsUitl.getSettingsOneHandedModeTimeout(any(), anyInt())).thenReturn(
                OneHandedSettingsUtil.ONE_HANDED_TIMEOUT_MEDIUM_IN_SECONDS);
        when(mMockSettingsUitl.getSettingsTapsAppToExit(any(), anyInt())).thenReturn(
                mDefaultTapAppToExitEnabled);
        when(mMockSettingsUitl.getSettingsSwipeToNotificationEnabled(any(), anyInt())).thenReturn(
                mDefaultSwipeToNotificationEnabled);
        when(mMockSettingsUitl.getShortcutEnabled(any(), anyInt())).thenReturn(false);

        when(mMockDisplayAreaOrganizer.getLastDisplayBounds()).thenReturn(
                new Rect(0, 0, 1080, 2400));
        when(mMockDisplayAreaOrganizer.getDisplayLayout()).thenReturn(mMockDisplayLayout);

        mShellInit = spy(new ShellInit(mMockShellMainExecutor));
        mOneHandedAccessibilityUtil = new OneHandedAccessibilityUtil(mContext);
        mSpiedOneHandedController = spy(new OneHandedController(
                mContext,
                mShellInit,
                mMockShellCommandHandler,
                mMockShellController,
                mMockDisplayController,
                mMockDisplayAreaOrganizer,
                mMockTouchHandler,
                mMockTutorialHandler,
                mMockSettingsUitl,
                mOneHandedAccessibilityUtil,
                mSpiedTimeoutHandler,
                mSpiedTransitionState,
                mMockUiEventLogger,
                mMockTaskStackListener,
                mMockShellMainExecutor,
                mMockShellMainHandler)
        );
        mShellInit.init();
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void instantiateController_registerDumpCallback() {
        verify(mMockShellCommandHandler, times(1)).addDumpCallback(any(), any());
    }

    @Test
    public void testControllerRegistersConfigChangeListener() {
        verify(mMockShellController, times(1)).addConfigurationChangeListener(any());
    }

    @Test
    public void testControllerRegistersKeyguardChangeListener() {
        verify(mMockShellController, times(1)).addKeyguardChangeListener(any());
    }

    @Test
    public void testControllerRegistersUserChangeListener() {
        verify(mMockShellController, times(1)).addUserChangeListener(any());
    }

    @Test
    public void testControllerRegisteresExternalInterface() {
        verify(mMockShellController, times(1)).addExternalInterface(
                eq(ShellSharedConstants.KEY_EXTRA_SHELL_ONE_HANDED), any(), any());
    }

    @Test
    public void testDefaultShouldNotInOneHanded() {
        // Assert default transition state is STATE_NONE
        assertThat(mSpiedTransitionState.getState()).isEqualTo(STATE_NONE);
    }

    @Test
    public void testNullDisplayLayout() {
        mSpiedOneHandedController.updateDisplayLayout(0);

        verify(mMockDisplayAreaOrganizer, never()).setDisplayLayout(any());
    }

    @Test
    public void testStartOneHandedShouldTriggerScheduleOffset() {
        mSpiedTransitionState.setState(STATE_NONE);
        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testStartOneHandedShouldNotTriggerScheduleOffset() {
        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedTransitionState.setState(STATE_ENTERING);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testStopOneHanded() {
        mSpiedTransitionState.setState(STATE_NONE);
        mSpiedOneHandedController.stopOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testRegisterTransitionCallbackAfterInit() {
        verify(mMockDisplayAreaOrganizer).registerTransitionCallback(mMockTouchHandler);
        verify(mMockDisplayAreaOrganizer).registerTransitionCallback(mMockTutorialHandler);
    }

    @Test
    public void testRegisterTransitionCallback() {
        OneHandedTransitionCallback callback = new OneHandedTransitionCallback() {
        };
        mSpiedOneHandedController.registerTransitionCallback(callback);

        verify(mMockDisplayAreaOrganizer).registerTransitionCallback(callback);
    }

    @Test
    public void testStopOneHandedShouldRemoveTimer() {
        mSpiedTransitionState.setState(STATE_ENTERING);
        mSpiedOneHandedController.stopOneHanded();

        verify(mSpiedTimeoutHandler, atLeastOnce()).removeTimer();
    }

    @Test
    public void testUpdateEnabled() {
        mSpiedOneHandedController.setOneHandedEnabled(true);

        verify(mMockTouchHandler, atLeastOnce()).onOneHandedEnabled(anyBoolean());
    }

    @Test
    public void testUpdateSwipeToNotification() {
        mSpiedOneHandedController.setSwipeToNotificationEnabled(mDefaultSwipeToNotificationEnabled);

        verify(mMockTouchHandler, atLeastOnce()).onOneHandedEnabled(anyBoolean());
    }

    @Test
    public void testTapAppToExitEnabledAddListener() {
        mSpiedOneHandedController.setTaskChangeToExit(mDefaultTapAppToExitEnabled);

        // If device settings default ON, then addListener() will be trigger 1 time at init
        verify(mMockTaskStackListener, atLeastOnce()).addListener(any());
    }

    @Test
    public void testTapAppToExitDisabledRemoveListener() {
        mSpiedOneHandedController.setTaskChangeToExit(!mDefaultTapAppToExitEnabled);

        // If device settings default ON, then removeListener() will be trigger 1 time at init
        verify(mMockTaskStackListener, atLeastOnce()).removeListener(any());
    }

    @Test
    public void testSettingsObserverUpdateEnabled() {
        mSpiedOneHandedController.onEnabledSettingChanged();

        verify(mSpiedOneHandedController).setOneHandedEnabled(anyBoolean());
    }

    @Test
    public void testSettingsObserverUpdateSwipeToNotification() {
        mSpiedOneHandedController.onSwipeToNotificationEnabledChanged();

        verify(mSpiedOneHandedController).setSwipeToNotificationEnabled(anyBoolean());
    }

    @Test
    public void testLockedOneHandedDisabled() {
        // Default mLockDisabled is false
        assertThat(mSpiedOneHandedController.isLockedDisabled()).isFalse();

        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedOneHandedController.setLockedDisabled(false /* locked */, true /* enabled */);

        // If mOneHandedEnabled == enabled, then keep unlocked
        assertThat(mSpiedOneHandedController.isLockedDisabled()).isFalse();

        // If prefer locked enabled state and 'mOneHandedEnabled == enabled', then unlocked
        mSpiedOneHandedController.setLockedDisabled(true /* locked */, true /* enabled */);

        assertThat(mSpiedOneHandedController.isLockedDisabled()).isFalse();

        // If prefer locked disabled state and 'mOneHandedEnabled != enabled', then locked disabled
        mSpiedOneHandedController.setLockedDisabled(true /* locked */, false /* enabled */);

        assertThat(mSpiedOneHandedController.isLockedDisabled()).isTrue();

        // If prefer unlock disabled state and 'mOneHandedEnabled != enabled', then unlocked
        mSpiedOneHandedController.setLockedDisabled(false /* locked */, false /* enabled */);

        assertThat(mSpiedOneHandedController.isLockedDisabled()).isFalse();
    }

    @Test
    public void testKeyguardShowingLockOneHandedDisabled() {
        mSpiedTransitionState.setState(STATE_NONE);
        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedOneHandedController.setLockedDisabled(true /* locked */, false /* enabled */);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testResetKeyguardShowingLockOneHandedDisabled() {
        mSpiedTransitionState.setState(STATE_NONE);
        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedOneHandedController.setLockedDisabled(false /* locked */, false /* enabled */);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation90CanNotStartOneHanded() {
        mMockDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_90);
        mSpiedTransitionState.setState(STATE_NONE);
        when(mMockDisplayLayout.isLandscape()).thenReturn(true);
        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedOneHandedController.setLockedDisabled(false /* locked */, false /* enabled */);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation180CanStartOneHanded() {
        mMockDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_180);
        mSpiedTransitionState.setState(STATE_NONE);
        when(mMockDisplayAreaOrganizer.isReady()).thenReturn(true);
        when(mMockDisplayLayout.isLandscape()).thenReturn(false);
        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedOneHandedController.setLockedDisabled(false /* locked */, false /* enabled */);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testRotation270CanNotStartOneHanded() {
        mMockDisplayLayout.rotateTo(mContext.getResources(), Surface.ROTATION_270);
        mSpiedTransitionState.setState(STATE_NONE);
        when(mMockDisplayLayout.isLandscape()).thenReturn(true);
        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedOneHandedController.setLockedDisabled(false /* locked */, false /* enabled */);
        mSpiedOneHandedController.startOneHanded();

        verify(mMockDisplayAreaOrganizer, never()).scheduleOffset(anyInt(), anyInt());
    }

    @Test
    public void testOneHandedEnabledRotation90ShouldHandleRotate() {
        when(mMockSettingsUitl.getSettingsOneHandedModeEnabled(any(), anyInt())).thenReturn(true);
        when(mMockSettingsUitl.getSettingsSwipeToNotificationEnabled(any(), anyInt())).thenReturn(
                false);
        final WindowContainerTransaction handlerWCT = new WindowContainerTransaction();
        mSpiedOneHandedController.onDisplayChange(mDisplay.getDisplayId(), Surface.ROTATION_0,
                Surface.ROTATION_90, null /* newDisplayAreaInfo */, handlerWCT);

        verify(mMockDisplayAreaOrganizer, atLeastOnce()).onRotateDisplay(eq(mContext),
                eq(Surface.ROTATION_90), any(WindowContainerTransaction.class));
    }

    @Test
    public void testOneHandedDisabledRotation90ShouldNotHandleRotate() {
        when(mMockSettingsUitl.getSettingsOneHandedModeEnabled(any(), anyInt())).thenReturn(false);
        when(mMockSettingsUitl.getSettingsSwipeToNotificationEnabled(any(), anyInt())).thenReturn(
                false);
        final WindowContainerTransaction handlerWCT = new WindowContainerTransaction();
        mSpiedOneHandedController.onDisplayChange(mDisplay.getDisplayId(), Surface.ROTATION_0,
                Surface.ROTATION_90, null /* newDisplayAreaInfo */, handlerWCT);

        verify(mMockDisplayAreaOrganizer, never()).onRotateDisplay(eq(mContext),
                eq(Surface.ROTATION_90), any(WindowContainerTransaction.class));
    }

    @Test
    public void testSwipeToNotificationEnabledRotation90ShouldNotHandleRotate() {
        when(mMockSettingsUitl.getSettingsOneHandedModeEnabled(any(), anyInt())).thenReturn(true);
        when(mMockSettingsUitl.getSettingsSwipeToNotificationEnabled(any(), anyInt())).thenReturn(
                true);
        final WindowContainerTransaction handlerWCT = new WindowContainerTransaction();
        mSpiedOneHandedController.onDisplayChange(mDisplay.getDisplayId(), Surface.ROTATION_0,
                Surface.ROTATION_90, null /* newDisplayAreaInfo */, handlerWCT);

        verify(mMockDisplayAreaOrganizer, never()).onRotateDisplay(eq(mContext),
                eq(Surface.ROTATION_90), any(WindowContainerTransaction.class));
    }

    @Test
    public void testSwipeToNotificationDisabledRotation90ShouldHandleRotate() {
        when(mMockSettingsUitl.getSettingsOneHandedModeEnabled(any(), anyInt())).thenReturn(true);
        when(mMockSettingsUitl.getSettingsSwipeToNotificationEnabled(any(), anyInt())).thenReturn(
                false);
        final WindowContainerTransaction handlerWCT = new WindowContainerTransaction();
        mSpiedOneHandedController.onDisplayChange(mDisplay.getDisplayId(), Surface.ROTATION_0,
                Surface.ROTATION_90, null /* newDisplayAreaInfo */, handlerWCT);

        verify(mMockDisplayAreaOrganizer, atLeastOnce()).onRotateDisplay(eq(mContext),
                eq(Surface.ROTATION_90), any(WindowContainerTransaction.class));
    }

    @Test
    public void testStateActive_shortcutRequestActivate_skipActions() {
        when(mSpiedTransitionState.getState()).thenReturn(STATE_ACTIVE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mMockSettingsUitl.getOneHandedModeActivated(any(), anyInt())).thenReturn(true);
        when(mSpiedOneHandedController.isShortcutEnabled()).thenReturn(true);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mSpiedOneHandedController, never()).startOneHanded();
        verify(mSpiedOneHandedController, never()).stopOneHanded();
    }

    @Test
    public void testStateNotActive_shortcutRequestInActivate_skipAction() {
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mMockSettingsUitl.getOneHandedModeActivated(any(), anyInt())).thenReturn(false);
        when(mSpiedOneHandedController.isShortcutEnabled()).thenReturn(true);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mSpiedOneHandedController, never()).startOneHanded();
        verify(mSpiedOneHandedController, never()).stopOneHanded();
    }

    @Test
    public void testStateNotActive_shortcutRequestActivate_doAction() {
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mMockSettingsUitl.getOneHandedModeActivated(any(), anyInt())).thenReturn(true);
        when(mSpiedOneHandedController.isShortcutEnabled()).thenReturn(true);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mSpiedOneHandedController).startOneHanded();
        verify(mSpiedOneHandedController, never()).stopOneHanded();
    }

    @Test
    public void testEnteringTransition_shortcutRequestActivate_skipActions() {
        when(mSpiedTransitionState.getState()).thenReturn(STATE_ENTERING);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(true);
        when(mMockSettingsUitl.getOneHandedModeActivated(any(), anyInt())).thenReturn(true);
        when(mSpiedOneHandedController.isShortcutEnabled()).thenReturn(true);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mSpiedTransitionState, never()).setState(STATE_EXITING);
    }

    @Test
    public void testExitingTransition_shortcutRequestActivate_skipActions() {
        when(mSpiedTransitionState.getState()).thenReturn(STATE_EXITING);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(true);
        when(mMockSettingsUitl.getOneHandedModeActivated(any(), anyInt())).thenReturn(true);
        when(mSpiedOneHandedController.isShortcutEnabled()).thenReturn(true);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mSpiedTransitionState, never()).setState(STATE_ENTERING);
    }

    @Test
    public void testOneHandedDisabled_shortcutTrigger_thenAutoEnabled() {
        when(mSpiedOneHandedController.isOneHandedEnabled()).thenReturn(false);
        when(mSpiedOneHandedController.isShortcutEnabled()).thenReturn(true);
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mMockSettingsUitl.getOneHandedModeActivated(any(), anyInt())).thenReturn(false);
        when(mMockSettingsUitl.setOneHandedModeEnabled(any(), anyInt(), anyInt())).thenReturn(
                false);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mMockSettingsUitl).setOneHandedModeEnabled(any(), eq(1), anyInt());
    }

    @Test
    public void testControllerInit_tutorialAddStateChangeListener() {
        when(mSpiedOneHandedController.isOneHandedEnabled()).thenReturn(true);
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mMockSettingsUitl.getOneHandedModeActivated(any(), anyInt())).thenReturn(false);

        verify(mSpiedTransitionState).addSListeners(mMockTutorialHandler);
    }

    @Test
    public void testNotifyEventCallbackWithMainExecutor() {
        when(mSpiedOneHandedController.isOneHandedEnabled()).thenReturn(true);
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mSpiedOneHandedController.isShortcutEnabled()).thenReturn(true);
        when(mSpiedOneHandedController.isSwipeToNotificationEnabled()).thenReturn(true);
        mSpiedOneHandedController.registerEventCallback(mMockEventCallback);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mMockShellMainExecutor).execute(any());
    }

    @Test
    public void testNotifyShortcutState_whenSetOneHandedEnabled() {
        when(mSpiedOneHandedController.isOneHandedEnabled()).thenReturn(true);
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mSpiedOneHandedController.isSwipeToNotificationEnabled()).thenReturn(false);
        mSpiedOneHandedController.registerEventCallback(mMockEventCallback);
        mSpiedOneHandedController.setOneHandedEnabled(true);

        verify(mSpiedOneHandedController).notifyShortcutStateChanged(anyInt());
    }

    @Test
    public void testNotifyExpandNotification_withNullCheckProtection() {
        when(mSpiedOneHandedController.isOneHandedEnabled()).thenReturn(false);
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mSpiedOneHandedController.isSwipeToNotificationEnabled()).thenReturn(true);
        mSpiedOneHandedController.setOneHandedEnabled(true);
        mSpiedOneHandedController.notifyExpandNotification();

        // Verify no NPE crash and mMockShellMainExecutor never be execute.
        verify(mMockShellMainExecutor, never()).execute(any());
    }

    @Test
    public void testShortcutEnable_ableToAutoEnableOneHandedMode() {
        when(mSpiedOneHandedController.isOneHandedEnabled()).thenReturn(false);
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mSpiedOneHandedController.isShortcutEnabled()).thenReturn(true);
        when(mSpiedOneHandedController.isSwipeToNotificationEnabled()).thenReturn(false);
        when(mMockSettingsUitl.setOneHandedModeEnabled(any(), anyInt(), anyInt())).thenReturn(
                false /* To avoid test runner create Toast */);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mMockSettingsUitl).setOneHandedModeEnabled(any(), eq(1), anyInt());
    }

    @Test
    public void testShortcutDisable_shouldNotAutoEnableOneHandedMode() {
        when(mSpiedOneHandedController.isOneHandedEnabled()).thenReturn(false);
        when(mSpiedTransitionState.getState()).thenReturn(STATE_NONE);
        when(mSpiedTransitionState.isTransitioning()).thenReturn(false);
        when(mSpiedOneHandedController.isSwipeToNotificationEnabled()).thenReturn(false);
        when(mMockSettingsUitl.setOneHandedModeEnabled(any(), anyInt(), anyInt())).thenReturn(true);
        mSpiedOneHandedController.onActivatedActionChanged();

        verify(mMockSettingsUitl, never()).setOneHandedModeEnabled(any(), anyInt(), anyInt());
    }
}
