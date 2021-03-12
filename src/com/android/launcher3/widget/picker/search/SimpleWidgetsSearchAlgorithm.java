/*
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

package com.android.launcher3.widget.picker.search;

import android.os.Handler;
import android.util.Log;

import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.util.ArrayList;

/**
 * Implementation of {@link SearchAlgorithm} that posts a task to query on the main thread.
 */
public final class SimpleWidgetsSearchAlgorithm implements SearchAlgorithm<WidgetsListBaseEntry> {

    private static final boolean DEBUG = false;
    private static final String TAG = "SimpleWidgetsSearchAlgo";
    private static final String DELIM = "\t";

    private final Handler mResultHandler;
    private final WidgetsPickerSearchPipeline mSearchPipeline;

    public SimpleWidgetsSearchAlgorithm(WidgetsPickerSearchPipeline searchPipeline) {
        mResultHandler = new Handler();
        mSearchPipeline = searchPipeline;
    }

    @Override
    public void doSearch(String query, SearchCallback<WidgetsListBaseEntry> callback) {
        long startTime = System.currentTimeMillis();
        String queryToken = query + DELIM + startTime;
        if (DEBUG) {
            Log.d(TAG, "doSearch queryToken:" + queryToken);
        }
        mSearchPipeline.query(query,
                results -> mResultHandler.post(
                        () -> callback.onSearchResult(queryToken, new ArrayList(results))));
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(/*token= */null);
        }
    }
}
