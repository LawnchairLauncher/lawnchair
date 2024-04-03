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

/**
 * Container that can be used to input a search query and retrieve a {@link SearchResultFromQsb}
 * instance.
 */
interface SearchInputSource {
    String INPUT_RES = "input";

    /** Set the already focused search input edit text and update search results. */
    default SearchResultFromQsb searchForInput(String input) {
        LauncherInstrumentation launcher = getLauncher();
        try (LauncherInstrumentation.Closable c = launcher.addContextLayer(
                "want to search for result with an input");
             LauncherInstrumentation.Closable e = launcher.eventsCheck()) {
            launcher.executeAndWaitForLauncherEvent(
                    () -> {
                        UiObject2 editText = launcher.waitForLauncherObject(INPUT_RES);
                        launcher.waitForObjectFocused(editText, "search input");
                        editText.setText(input);
                    },
                    event -> TestProtocol.SEARCH_RESULT_COMPLETE.equals(event.getClassName()),
                    () -> "Didn't receive a search result completed message", "searching");
            return getSearchResultForInput();
        }
    }

    /** This method requires public access, however should not be called in tests. */
    LauncherInstrumentation getLauncher();

    /** This method requires public access, however should not be called in tests. */
    SearchResultFromQsb getSearchResultForInput();
}
