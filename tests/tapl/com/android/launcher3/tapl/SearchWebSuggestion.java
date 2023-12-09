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
package com.android.launcher3.tapl;

import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.regex.Pattern;

/**
 * Operations on a search web suggestion from a qsb.
 */
public class SearchWebSuggestion extends Launchable {

    private static final Pattern LONG_CLICK_EVENT = Pattern.compile("onAllAppsItemLongClick");

    SearchWebSuggestion(LauncherInstrumentation launcher, UiObject2 object) {
        super(launcher, object);
    }

    @Override
    protected void expectActivityStartEvents() {
    }

    @Override
    protected String launchableType() {
        return "search web suggestion";
    }

    @Override
    protected void waitForLongPressConfirmation() {
        mLauncher.waitForLauncherObject("popup_container");
    }

    @Override
    protected void addExpectedEventsForLongClick() {
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, getLongClickEvent());
    }

    protected Pattern getLongClickEvent() {
        return LONG_CLICK_EVENT;
    }
}
