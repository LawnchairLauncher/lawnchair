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
package com.android.launcher3.tapl;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.testing.TestProtocol;

public class OptionsPopupMenuItem {

    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mObject;

    OptionsPopupMenuItem(@NonNull LauncherInstrumentation launcher, @NonNull UiObject2 shortcut) {
        mLauncher = launcher;
        mObject = shortcut;
    }

    /**
     * Clicks the option.
     */
    @NonNull
    public void launch(@NonNull String expectedPackageName) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            LauncherInstrumentation.log("OptionsPopupMenuItem before click "
                    + mObject.getVisibleCenter() + " in " + mLauncher.getVisibleBounds(mObject));
            mLauncher.clickLauncherObject(mObject);
            mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LauncherInstrumentation.EVENT_START);
            mLauncher.assertTrue(
                    "App didn't start: " + By.pkg(expectedPackageName),
                    mLauncher.getDevice().wait(Until.hasObject(By.pkg(expectedPackageName)),
                            LauncherInstrumentation.WAIT_TIME_MS));
        }
    }
}
