/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.allapps.search;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.allapps.search.AllAppsSearchBarController.SearchTargetHandler;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.WeakHashMap;

/**
 * A singleton class to track and report search events to search provider
 */
public class SearchEventTracker {
    @Nullable
    private AllAppsSearchPlugin mPlugin;
    private final WeakHashMap<SearchTarget, SearchTargetHandler>
            mCallbacks = new WeakHashMap<>();

    public static final MainThreadInitializedObject<SearchEventTracker> INSTANCE =
            new MainThreadInitializedObject<>(SearchEventTracker::new);

    private SearchEventTracker(Context context) {
    }

    /**
     * Returns instance of SearchEventTracker
     */
    public static SearchEventTracker getInstance(Context context) {
        return SearchEventTracker.INSTANCE.get(context);
    }

    /**
     * Sets current connected plugin for event reporting
     */
    public void setPlugin(@Nullable AllAppsSearchPlugin plugin) {
        mPlugin = plugin;
    }

    /**
     * Sends SearchTargetEvent to search provider
     */
    public void notifySearchTargetEvent(SearchTargetEvent searchTargetEvent) {
        if (mPlugin != null) {
            mPlugin.notifySearchTargetEvent(searchTargetEvent);
        }
    }

    /**
     * Registers a {@link SearchTargetHandler} to handle quick launch for specified SearchTarget.
     */
    public void registerWeakHandler(SearchTarget searchTarget, SearchTargetHandler targetHandler) {
        mCallbacks.put(searchTarget, targetHandler);
    }

    /**
     * Handles quick select for SearchTarget
     */
    public void quickSelect(SearchTarget searchTarget) {
        SearchTargetHandler searchTargetHandler = mCallbacks.get(searchTarget);
        if (searchTargetHandler != null) {
            searchTargetHandler.handleSelection(SearchTargetEvent.QUICK_SELECT);
        }
    }

    /**
     * flushes all registered quick select handlers
     */
    public void clearHandlers() {
        mCallbacks.clear();
    }
}
