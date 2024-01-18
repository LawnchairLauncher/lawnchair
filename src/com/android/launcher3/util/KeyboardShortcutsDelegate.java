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
package com.android.launcher3.util;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.getSupportedActions;

import android.util.Log;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.Menu;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.BaseAccessibilityDelegate;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.views.OptionsPopupView;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate to define the keyboard shortcuts.
 */
public class KeyboardShortcutsDelegate {

    Launcher mLauncher;

    public KeyboardShortcutsDelegate(Launcher launcher) {
        mLauncher = launcher;
    }

    /**
     * Populates the list of shortcuts.
     */
    public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {
        ArrayList<KeyboardShortcutInfo> shortcutInfos = new ArrayList<>();
        if (mLauncher.isInState(NORMAL)) {
            shortcutInfos.add(
                    new KeyboardShortcutInfo(mLauncher.getString(R.string.all_apps_button_label),
                            KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON));
            shortcutInfos.add(
                    new KeyboardShortcutInfo(mLauncher.getString(R.string.widget_button_text),
                            KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON));
        }
        getSupportedActions(mLauncher, mLauncher.getCurrentFocus()).forEach(la ->
                shortcutInfos.add(new KeyboardShortcutInfo(
                        la.accessibilityAction.getLabel(), la.keyCode, KeyEvent.META_CTRL_ON)));
        if (!shortcutInfos.isEmpty()) {
            data.add(new KeyboardShortcutGroup(mLauncher.getString(R.string.home_screen),
                    shortcutInfos));
        }
    }

    /**
     * Handles combinations of keys like ctrl+s or ctrl+c and runs before onKeyDown.
     * @param keyCode code of the key being pressed.
     * @see android.view.KeyEvent
     * @return weather the event is already handled and if it should be passed to other components.
     */
    public Boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    if (mLauncher.isInState(NORMAL)) {
                        mLauncher.getStateManager().goToState(ALL_APPS);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_W:
                    if (mLauncher.isInState(NORMAL)) {
                        OptionsPopupView.openWidgets(mLauncher);
                        return true;
                    }
                    break;
                default:
                    for (BaseAccessibilityDelegate.LauncherAction la : getSupportedActions(
                            mLauncher, mLauncher.getCurrentFocus())) {
                        if (la.keyCode == keyCode) {
                            return la.invokeFromKeyboard(mLauncher.getCurrentFocus());
                        }
                    }
            }
        }
        return null;
    }

    /**
     * Handle key down event.
     * @param keyCode code of the key being pressed.
     * @see android.view.KeyEvent
     */
    public Boolean onKeyDown(int keyCode, KeyEvent event) {
        // Ignore escape if pressed in conjunction with any modifier keys.
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.hasNoModifiers()) {
            AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mLauncher);
            if (topView != null) {
                // Close each floating view one at a time for each key press.
                topView.close(/* animate= */ true);
                return true;
            } else if (mLauncher.getAppsView().isInAllApps()) {
                // Close all apps if there are no open floating views.
                closeAllApps();
                return true;
            }
        }
        return null;
    }

    private void closeAllApps() {
        mLauncher.getStateManager().goToState(NORMAL, true);
    }

    /**
     * Handle key up event.
     * @param keyCode code of the key being pressed.
     * @see android.view.KeyEvent
     */
    public Boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // KEYCODE_MENU is sent by some tests, for example
            // LauncherJankTests#testWidgetsContainerFling. Don't just remove its handling.
            if (!mLauncher.getDragController().isDragging()
                    && !mLauncher.getWorkspace().isSwitchingState()
                    && mLauncher.isInState(NORMAL)) {
                // Close any open floating views.
                mLauncher.closeOpenViews();

                // Setting the touch point to (-1, -1) will show the options popup in the center of
                // the screen.
                if (Utilities.isRunningInTestHarness()) {
                    Log.d(TestProtocol.PERMANENT_DIAG_TAG, "Opening options popup on key up");
                }
                mLauncher.showDefaultOptions(-1, -1);
            }
            return true;
        }
        return null;
    }
}
