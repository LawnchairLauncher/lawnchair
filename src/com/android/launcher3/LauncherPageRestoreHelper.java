/**
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.util.IntSet;

import static androidx.annotation.VisibleForTesting.PACKAGE_PRIVATE;

/**
 * There's a logic which prioritizes the binding for the current page and defers the other pages'
 * binding. If two panel home is enabled, we want to bind both pages together.
 * LauncherPageRestoreHelper's purpose is to contain the logic for persisting, restoring and
 * calculating which pages to load immediately.
 */
public class LauncherPageRestoreHelper {

    public static final String TAG = "LauncherPageRestoreHelper";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN_COUNT =
            "launcher.current_screen_count";

    private Workspace mWorkspace;

    public LauncherPageRestoreHelper(Workspace workspace) {
        this.mWorkspace = workspace;
    }

    /**
     * Some configuration changes trigger Launcher to recreate itself, and we want to give more
     * priority to the currently active pages in the restoration process.
     */
    @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
    public IntSet getPagesToRestore(Bundle savedInstanceState) {
        IntSet pagesToRestore = new IntSet();

        if (savedInstanceState == null) {
            return pagesToRestore;
        }

        int currentPage = savedInstanceState.getInt(RUNTIME_STATE_CURRENT_SCREEN, -1);
        int totalPageCount = savedInstanceState.getInt(RUNTIME_STATE_CURRENT_SCREEN_COUNT, -1);
        int panelCount = mWorkspace.getPanelCount();

        if (totalPageCount <= 0 || currentPage < 0) {
            Log.e(TAG, "getPagesToRestore: Invalid input: " + totalPageCount + ", " + currentPage);
            return pagesToRestore;
        }

        int newCurrentPage = mWorkspace.getLeftmostVisiblePageForIndex(currentPage);
        for (int page = newCurrentPage; page < newCurrentPage + panelCount
                && page < totalPageCount; page++) {
            pagesToRestore.add(page);
        }

        return pagesToRestore;
    }

    /**
     * This should be called from Launcher's onSaveInstanceState method to persist everything that
     * is necessary to calculate later which pages need to be initialized first after a
     * configuration change.
     */
    @VisibleForTesting(otherwise = PACKAGE_PRIVATE)
    public void savePagesToRestore(Bundle outState) {
        int pageCount = mWorkspace.getChildCount();
        if (pageCount > 0) {
            outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getCurrentPage());
            outState.putInt(RUNTIME_STATE_CURRENT_SCREEN_COUNT, pageCount);
        }
    }
}
