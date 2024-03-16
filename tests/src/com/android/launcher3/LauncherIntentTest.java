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

package com.android.launcher3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.platform.test.annotations.LargeTest;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.SearchRecyclerView;
import com.android.launcher3.ui.AbstractLauncherUiTest;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class LauncherIntentTest extends AbstractLauncherUiTest {

    public final Intent allAppsIntent = new Intent(Intent.ACTION_ALL_APPS);

    @Test
    @Ignore("b/329152799")
    public void testAllAppsIntent() {
        // setup by moving to home
        mLauncher.goHome();
        assertTrue("Launcher internal state is not Home", isInState(() -> LauncherState.NORMAL));

        // Try executing ALL_APPS intent
        executeOnLauncher(launcher -> launcher.onNewIntent(allAppsIntent));
        // A-Z view with Main adapter should be loaded
        assertOnMainAdapterAToZView();


        // Try Moving to search view now
        moveToSearchView();
        // Try executing ALL_APPS intent
        executeOnLauncher(launcher -> launcher.onNewIntent(allAppsIntent));
        // A-Z view with Main adapter should be loaded
        assertOnMainAdapterAToZView();

        // finish
        mLauncher.goHome();
        assertTrue("Launcher internal state is not Home", isInState(() -> LauncherState.NORMAL));
    }

    // Highlights the search bar, then fills text to display the SearchView.
    private void moveToSearchView() {
        mLauncher.goHome().switchToAllApps();

        // All Apps view should be loaded
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));
        executeOnLauncher(launcher -> launcher.getAppsView().getSearchView().requestFocus());
        // Search view should be in focus
        waitForLauncherCondition("Search view is not in focus.",
                launcher -> launcher.getAppsView().getSearchView().hasFocus());
        mLauncher.pressAndHoldKeyCode(KeyEvent.KEYCODE_C, 0);
        // Upon key press, search recycler view should be loaded
        waitForLauncherCondition("Search view not active.",
                launcher -> launcher.getAppsView().getActiveRecyclerView()
                        instanceof SearchRecyclerView);
        mLauncher.unpressKeyCode(KeyEvent.KEYCODE_C, 0);
    }

    // Checks if main adapter view is selected, search bar is out of focus and scroller is at start.
    private void assertOnMainAdapterAToZView() {
        // All Apps State should be loaded
        assertTrue("Launcher internal state is not All Apps",
                isInState(() -> LauncherState.ALL_APPS));

        // A-Z recycler view should be active.
        waitForLauncherCondition("A-Z view not active.",
                launcher -> !(launcher.getAppsView().getActiveRecyclerView()
                        instanceof SearchRecyclerView));
        // Personal Adapter should be selected.
        waitForLauncherCondition("Not on Main Adapter View",
                launcher -> launcher.getAppsView().getCurrentPage()
                        == ActivityAllAppsContainerView.AdapterHolder.MAIN);
        // Search view should not be in focus
        waitForLauncherCondition("Search view has focus.",
                launcher -> !launcher.getAppsView().getSearchView().hasFocus());
        // Scroller should be at top
        executeOnLauncher(launcher -> assertEquals(
                "All Apps started in already scrolled state", 0,
                getAllAppsScroll(launcher)));
    }
}
