/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

import java.util.regex.Pattern;

public class AddToHomeScreenPrompt {
    private static final Pattern ADD_AUTOMATICALLY =
            Pattern.compile("^Add automatically$", CASE_INSENSITIVE);
    private final LauncherInstrumentation mLauncher;
    private final UiObject2 mWidgetCell;

    AddToHomeScreenPrompt(LauncherInstrumentation launcher) {
        mLauncher = launcher;
        mWidgetCell = launcher.waitForLauncherObject(By.clazz(
                "com.android.launcher3.widget.WidgetCell"));
        mLauncher.assertNotNull("Can't find widget cell object", mWidgetCell);
    }

    public void addAutomatically() {
        mLauncher.waitForObjectInContainer(
                mWidgetCell.getParent().getParent().getParent().getParent(),
                By.text(ADD_AUTOMATICALLY)).click();
    }
}
