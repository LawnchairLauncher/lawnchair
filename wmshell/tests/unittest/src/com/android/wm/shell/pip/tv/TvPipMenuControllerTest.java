/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.pip.tv;

import static android.view.KeyEvent.KEYCODE_DPAD_UP;

import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_ALL_ACTIONS_MENU;
import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_MOVE_MENU;
import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_NO_MENU;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceControl;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnWindowFocusChangeListener;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.SystemWindows;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TvPipMenuControllerTest extends ShellTestCase {
    private static final int TEST_MOVE_KEYCODE = KEYCODE_DPAD_UP;

    @Mock
    private TvPipMenuController.Delegate mMockDelegate;
    @Mock
    private TvPipBoundsState mMockTvPipBoundsState;
    @Mock
    private SystemWindows mMockSystemWindows;
    @Mock
    private TvPipMenuView mMockTvPipMenuView;
    @Mock
    private TvPipBackgroundView mMockTvPipBackgroundView;

    private Handler mMainHandler;
    private TvPipMenuController mTvPipMenuController;
    private OnWindowFocusChangeListener mFocusChangeListener;

    @Before
    public void setUp() {
        assumeTrue(isTelevision());

        MockitoAnnotations.initMocks(this);
        mMainHandler = new Handler(Looper.getMainLooper());

        final ViewTreeObserver mockMenuTreeObserver = mock(ViewTreeObserver.class);
        doReturn(mockMenuTreeObserver).when(mMockTvPipMenuView).getViewTreeObserver();

        mTvPipMenuController = new TestTvPipMenuController();
        mTvPipMenuController.setDelegate(mMockDelegate);
        mTvPipMenuController.setTvPipActionsProvider(mock(TvPipActionsProvider.class));
        mTvPipMenuController.attach(mock(SurfaceControl.class));
        mFocusChangeListener = captureFocusChangeListener(mockMenuTreeObserver);
    }

    private OnWindowFocusChangeListener captureFocusChangeListener(
            ViewTreeObserver mockTreeObserver) {
        final ArgumentCaptor<OnWindowFocusChangeListener> focusChangeListenerCaptor =
                ArgumentCaptor.forClass(OnWindowFocusChangeListener.class);
        verify(mockTreeObserver).addOnWindowFocusChangeListener(
                focusChangeListenerCaptor.capture());
        return focusChangeListenerCaptor.getValue();
    }

    @Test
    public void testMenuNotOpenByDefault() {
        assertMenuIsOpen(false);
    }

    @Test
    public void testSwitch_FromNoMenuMode_ToMoveMode() {
        showAndAssertMoveMenu(true);
    }

    @Test
    public void testSwitch_FromNoMenuMode_ToAllActionsMode() {
        showAndAssertAllActionsMenu(true);
    }

    @Test
    public void testSwitch_FromMoveMode_ToAllActionsMode() {
        showAndAssertMoveMenu(true);
        showAndAssertAllActionsMenu(false);
        verify(mMockDelegate, times(2)).onInMoveModeChanged();
    }

    @Test
    public void testSwitch_FromAllActionsMode_ToMoveMode() {
        showAndAssertAllActionsMenu(true);
        showAndAssertMoveMenu(false);
    }

    @Test
    public void testCloseMenu_NoMenuMode() {
        mTvPipMenuController.closeMenu();
        assertMenuIsOpen(false);
        verify(mMockDelegate, never()).onMenuClosed();
    }

    @Test
    public void testCloseMenu_MoveMode() {
        showAndAssertMoveMenu(true);

        closeMenuAndAssertMenuClosed(true);
        verify(mMockDelegate, times(2)).onInMoveModeChanged();
    }

    @Test
    public void testCloseMenu_AllActionsMode() {
        showAndAssertAllActionsMenu(true);

        closeMenuAndAssertMenuClosed(true);
    }

    @Test
    public void testCloseMenu_MoveModeFollowedByMoveMode() {
        showAndAssertMoveMenu(true);
        showAndAssertMoveMenu(false);

        closeMenuAndAssertMenuClosed(true);
        verify(mMockDelegate, times(2)).onInMoveModeChanged();
    }

    @Test
    public void testCloseMenu_MoveModeFollowedByAllActionsMode() {
        showAndAssertMoveMenu(true);
        showAndAssertAllActionsMenu(false);
        verify(mMockDelegate, times(2)).onInMoveModeChanged();

        closeMenuAndAssertMenuClosed(true);
    }

    @Test
    public void testCloseMenu_AllActionsModeFollowedByMoveMode() {
        showAndAssertAllActionsMenu(true);
        showAndAssertMoveMenu(false);

        closeMenuAndAssertMenuClosed(true);
        verify(mMockDelegate, times(2)).onInMoveModeChanged();
    }

    @Test
    public void testCloseMenu_AllActionsModeFollowedByAllActionsMode() {
        showAndAssertAllActionsMenu(true);
        showAndAssertAllActionsMenu(false);

        closeMenuAndAssertMenuClosed(true);
        verify(mMockDelegate, never()).onInMoveModeChanged();
    }

    @Test
    public void testExitMenuMode_NoMenuMode() {
        mTvPipMenuController.onExitCurrentMenuMode();
        assertMenuIsOpen(false);
        verify(mMockDelegate, never()).onMenuClosed();
        verify(mMockDelegate, never()).onInMoveModeChanged();
    }

    @Test
    public void testExitMenuMode_MoveMode() {
        showAndAssertMoveMenu(true);

        mTvPipMenuController.onExitCurrentMenuMode();
        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed();
        verify(mMockDelegate, times(2)).onInMoveModeChanged();
    }

    @Test
    public void testExitMenuMode_AllActionsMode() {
        showAndAssertAllActionsMenu(true);

        mTvPipMenuController.onExitCurrentMenuMode();
        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed();
    }

    @Test
    public void testExitMenuMode_AllActionsModeFollowedByMoveMode() {
        showAndAssertAllActionsMenu(true);
        showAndAssertMoveMenu(false);

        mTvPipMenuController.onExitCurrentMenuMode();
        assertSwitchedToAllActionsMode(2);
        verify(mMockDelegate, times(2)).onInMoveModeChanged();

        mTvPipMenuController.onExitCurrentMenuMode();
        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed();
    }

    @Test
    public void testExitMenuMode_AllActionsModeFollowedByAllActionsMode() {
        showAndAssertAllActionsMenu(true);
        showAndAssertAllActionsMenu(false);

        mTvPipMenuController.onExitCurrentMenuMode();
        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed();
        verify(mMockDelegate, never()).onInMoveModeChanged();
    }

    @Test
    public void testExitMenuMode_MoveModeFollowedByAllActionsMode() {
        showAndAssertMoveMenu(true);

        showAndAssertAllActionsMenu(false);
        verify(mMockDelegate, times(2)).onInMoveModeChanged();

        mTvPipMenuController.onExitCurrentMenuMode();
        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed();
    }

    @Test
    public void testExitMenuMode_MoveModeFollowedByMoveMode() {
        showAndAssertMoveMenu(true);
        showAndAssertMoveMenu(false);

        mTvPipMenuController.onExitCurrentMenuMode();
        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed();
        verify(mMockDelegate, times(2)).onInMoveModeChanged();
    }

    @Test
    public void testOnPipMovement_NoMenuMode() {
        moveAndAssertMoveSuccessful(false);
    }

    @Test
    public void testOnPipMovement_MoveMode() {
        showAndAssertMoveMenu(true);
        moveAndAssertMoveSuccessful(true);
    }

    @Test
    public void testOnPipMovement_AllActionsMode() {
        showAndAssertAllActionsMenu(true);
        moveAndAssertMoveSuccessful(false);
    }

    @Test
    public void testUnexpectedFocusChanges() {
        mFocusChangeListener.onWindowFocusChanged(true);
        assertSwitchedToAllActionsMode(1);

        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed();

        showAndAssertMoveMenu(true);
        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed(2);
        verify(mMockDelegate, times(2)).onInMoveModeChanged();
    }

    @Test
    public void testAsyncScenario_AllActionsModeRequestFollowedByAsyncMoveModeRequest() {
        mTvPipMenuController.showMenu();
        // Artificially delaying the focus change update and adding a move request to simulate an
        // async problematic situation.
        mTvPipMenuController.showMovementMenu();
        // The first focus change update arrives
        mFocusChangeListener.onWindowFocusChanged(true);

        // We expect that the TvPipMenuController will directly switch to the "pending" menu mode
        // - MODE_MOVE_MENU, because no change of focus is needed.
        assertSwitchedToMoveMode();
    }

    @Test
    public void testAsyncScenario_MoveModeRequestFollowedByAsyncAllActionsModeRequest() {
        mTvPipMenuController.showMovementMenu();
        mTvPipMenuController.showMenu();

        mFocusChangeListener.onWindowFocusChanged(true);
        assertSwitchedToAllActionsMode(1);
        verify(mMockDelegate, never()).onInMoveModeChanged();
    }

    @Test
    public void testAsyncScenario_DropObsoleteIntermediateModeSwitchRequests() {
        mTvPipMenuController.showMovementMenu();
        mTvPipMenuController.closeMenu();

        // Focus change from showMovementMenu() call.
        mFocusChangeListener.onWindowFocusChanged(true);
        assertSwitchedToMoveMode();
        verify(mMockDelegate).onInMoveModeChanged();

        // Focus change from closeMenu() call.
        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed();
        verify(mMockDelegate, times(2)).onInMoveModeChanged();

        // Unexpected focus gain should open MODE_ALL_ACTIONS_MENU.
        mFocusChangeListener.onWindowFocusChanged(true);
        assertSwitchedToAllActionsMode(1);

        mTvPipMenuController.closeMenu();
        mTvPipMenuController.showMovementMenu();

        assertSwitchedToMoveMode(2);

        mFocusChangeListener.onWindowFocusChanged(false);
        assertMenuClosed(2);

        // Closing the menu resets the default menu mode, so the next focus gain opens the menu in
        // the default mode - MODE_ALL_ACTIONS_MENU.
        mFocusChangeListener.onWindowFocusChanged(true);
        assertSwitchedToAllActionsMode(2);
        verify(mMockDelegate, times(4)).onInMoveModeChanged();

    }

    private void showAndAssertMoveMenu(boolean focusChange) {
        mTvPipMenuController.showMovementMenu();
        if (focusChange) {
            mFocusChangeListener.onWindowFocusChanged(true);
        }
        assertSwitchedToMoveMode();
    }

    private void assertSwitchedToMoveMode() {
        assertSwitchedToMoveMode(1);
    }

    private void assertSwitchedToMoveMode(int times) {
        assertMenuIsInMoveMode();
        verify(mMockDelegate, times(2 * times - 1)).onInMoveModeChanged();
        verify(mMockTvPipMenuView, times(times)).transitionToMenuMode(eq(MODE_MOVE_MENU));
        verify(mMockTvPipBackgroundView, times(times)).transitionToMenuMode(eq(MODE_MOVE_MENU));
    }

    private void showAndAssertAllActionsMenu(boolean focusChange) {
        showAndAssertAllActionsMenu(focusChange, 1);
    }

    private void showAndAssertAllActionsMenu(boolean focusChange, int times) {
        mTvPipMenuController.showMenu();
        if (focusChange) {
            mFocusChangeListener.onWindowFocusChanged(true);
        }

        assertSwitchedToAllActionsMode(times);
    }

    private void assertSwitchedToAllActionsMode(int times) {
        assertMenuIsInAllActionsMode();
        verify(mMockTvPipMenuView, times(times))
                .transitionToMenuMode(eq(MODE_ALL_ACTIONS_MENU));
        verify(mMockTvPipBackgroundView, times(times))
                .transitionToMenuMode(eq(MODE_ALL_ACTIONS_MENU));
    }

    private void closeMenuAndAssertMenuClosed(boolean focusChange) {
        mTvPipMenuController.closeMenu();
        if (focusChange) {
            mFocusChangeListener.onWindowFocusChanged(false);
        }
        assertMenuClosed();
    }

    private void moveAndAssertMoveSuccessful(boolean expectedSuccess) {
        mTvPipMenuController.onPipMovement(TEST_MOVE_KEYCODE);
        verify(mMockDelegate, times(expectedSuccess ? 1 : 0)).movePip(eq(TEST_MOVE_KEYCODE));
    }

    private void assertMenuClosed() {
        assertMenuClosed(1);
    }

    private void assertMenuClosed(int times) {
        assertMenuIsOpen(false);
        verify(mMockDelegate, times(times)).onMenuClosed();
        verify(mMockTvPipMenuView, times(times)).transitionToMenuMode(eq(MODE_NO_MENU));
        verify(mMockTvPipBackgroundView, times(times)).transitionToMenuMode(eq(MODE_NO_MENU));
    }

    private void assertMenuIsOpen(boolean open) {
        assertTrue("The TV PiP menu should " + (open ? "" : "not ") + "be open, but it"
                + " is in mode " + mTvPipMenuController.getMenuModeString(),
                mTvPipMenuController.isMenuOpen() == open);
    }

    private void assertMenuIsInMoveMode() {
        assertTrue("Expected MODE_MOVE_MENU, but got " + mTvPipMenuController.getMenuModeString(),
                mTvPipMenuController.isInMoveMode());
        assertMenuIsOpen(true);
    }

    private void assertMenuIsInAllActionsMode() {
        assertTrue("Expected MODE_ALL_ACTIONS_MENU, but got "
                + mTvPipMenuController.getMenuModeString(),
                mTvPipMenuController.isInAllActionsMode());
        assertMenuIsOpen(true);
    }

    private class TestTvPipMenuController extends TvPipMenuController {

        TestTvPipMenuController() {
            super(mContext, mMockTvPipBoundsState, mMockSystemWindows, mMainHandler);
        }

        @Override
        TvPipMenuView createTvPipMenuView() {
            return mMockTvPipMenuView;
        }

        @Override
        TvPipBackgroundView createTvPipBackgroundView() {
            return mMockTvPipBackgroundView;
        }
    }
}
