/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.tapl;

import androidx.test.uiautomator.UiObject2;

/**
 * View containing Private Space Header.
 */
public class PrivateSpaceHeader {
    private static final String PS_HEADER_TEXT_RES_ID = "ps_container_header";
    private static final String SETTINGS_BUTTON_RES_ID = "ps_settings_button";
    private static final String UNLOCK_BUTTON_VIEW_RES_ID = "ps_lock_unlock_button";
    private static final String LOCK_ICON_RES_ID = "lock_icon";
    private static final String LOCK_TEXT_RES_ID = "lock_text";

    private final UiObject2 mPrivateSpaceHeader;
    private final boolean mPrivateSpaceEnabled;
    private final LauncherInstrumentation mLauncher;

    PrivateSpaceHeader(LauncherInstrumentation launcherInstrumentation,
            UiObject2 privateSpaceHeader, boolean privateSpaceEnabled) {
        mLauncher = launcherInstrumentation;
        mPrivateSpaceHeader = privateSpaceHeader;
        mPrivateSpaceEnabled =  privateSpaceEnabled;
        verifyPrivateSpaceHeaderState();
    }

    /** Verify elements in Private Space Header as per state */
    private void verifyPrivateSpaceHeaderState() {
        if (mPrivateSpaceEnabled) {
            verifyUnlockedState();
        } else {
            mLauncher.fail("Private Space found in non enabled state");
        }
    }

    /** Verify Unlocked State elements in Private Space Header */
    private void verifyUnlockedState() {
        UiObject2 headerText = mLauncher.waitForObjectInContainer(mPrivateSpaceHeader,
                PS_HEADER_TEXT_RES_ID);
        mLauncher.assertEquals("PS Header Text is incorrect ",
                "Private", headerText.getText());

        UiObject2 settingsButton = mLauncher.waitForObjectInContainer(mPrivateSpaceHeader,
                SETTINGS_BUTTON_RES_ID);
        mLauncher.waitForObjectEnabled(settingsButton, "Private Space Settings Button");
        mLauncher.assertTrue("PS Settings button is non-clickable", settingsButton.isClickable());

        UiObject2 unLockButtonView = mLauncher.waitForObjectInContainer(mPrivateSpaceHeader,
                UNLOCK_BUTTON_VIEW_RES_ID);
        mLauncher.waitForObjectEnabled(unLockButtonView, "Private Space Unlock Button");
        mLauncher.assertTrue("PS Unlock Button is non-clickable", unLockButtonView.isClickable());

        mLauncher.waitForObjectInContainer(mPrivateSpaceHeader,
                LOCK_ICON_RES_ID);

        UiObject2 lockText = mLauncher.waitForObjectInContainer(mPrivateSpaceHeader,
                LOCK_TEXT_RES_ID);
        mLauncher.assertEquals("PS lock text is incorrect", "Lock", lockText.getText());

    }
}
