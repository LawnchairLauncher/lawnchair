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
package com.android.launcher3.allapps;

import android.content.ComponentName;

import java.util.ArrayList;

/**
 * Interface for handling app search.
 */
public interface AppSearchManager {

    /**
     * Called when the search is about to be used. This method is optional for making a query but
     * calling this appropriately can improve the initial response time.
     */
    void connect();

    /**
     * Cancels all pending search requests.
     *
     * @param interruptActiveRequests if true, any active requests which are already executing will
     * be invalidated, and the corresponding results will not be sent. The client should usually
     * set this to true, before beginning a new search session.
     */
    void cancel(boolean interruptActiveRequests);

    /**
     * Performs a search
     */
    void doSearch(String query, AppSearchResultCallback callback);

    /**
     * Callback for getting search results.
     */
    public interface AppSearchResultCallback {

        /**
         * Called when the search is complete.
         *
         * @param apps sorted list of matching components or null if in case of failure.
         */
        void onSearchResult(ArrayList<ComponentName> apps);
    }
}
