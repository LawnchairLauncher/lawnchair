/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.os.Handler;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;

/**
 * The default search implementation.
 */
public class DefaultAppSearchAlgorithm implements SearchAlgorithm<AdapterItem> {

    protected final Handler mResultHandler;
    private final AppsSearchPipeline mAppsSearchPipeline;

    public DefaultAppSearchAlgorithm(Context context, LauncherAppState launcherAppState) {
        mResultHandler = new Handler();
        mAppsSearchPipeline = new AppsSearchPipeline(context, launcherAppState);
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void doSearch(final String query,
            final SearchCallback<AdapterItem> callback) {
        mAppsSearchPipeline.query(query,
                results -> mResultHandler.post(
                        () -> callback.onSearchResult(query, results)),
                null);
    }
}
