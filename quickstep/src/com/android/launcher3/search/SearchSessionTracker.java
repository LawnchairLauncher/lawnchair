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
package com.android.launcher3.search;

import android.app.search.Query;
import android.app.search.SearchSession;
import android.app.search.SearchTarget;
import android.app.search.SearchTargetEvent;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.util.MainThreadInitializedObject;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

/**
 * A singleton class to track and report search events back to SearchSession
 */
public class SearchSessionTracker {

    private static final String TAG = "SearchSessionTracker";

    @Nullable
    private SearchSession mSession;
    private Query mQuery;

    public static final MainThreadInitializedObject<SearchSessionTracker> INSTANCE =
            new MainThreadInitializedObject<>(SearchSessionTracker::new);

    private SearchSessionTracker(Context context) {
    }

    /**
     * Returns instance of SearchSessionTracker
     */
    public static SearchSessionTracker getInstance(Context context) {
        return SearchSessionTracker.INSTANCE.get(context);
    }

    @WorkerThread
    public void setSearchSession(SearchSession session) {
        mSession = session;
    }

    @WorkerThread
    public void setQuery(Query query) {
        mQuery = query;
    }

    /**
     * Send the user event handling back to the {@link SearchSession} object.
     */
    public void notifyEvent(SearchTargetEvent event) {
        if (mSession == null) {
            Log.d(TAG, "Dropping event " + event.getTargetId());
        }
        Log.d(TAG, "notifyEvent:" + mQuery.getInput() + ":" + event.getTargetId());
        UI_HELPER_EXECUTOR.post(() -> mSession.notifyEvent(mQuery, event));
    }
}
