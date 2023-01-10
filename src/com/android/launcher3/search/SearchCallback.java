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

import java.util.ArrayList;

/**
 * An interface for receiving search results.
 *
 * @param <T> Search Result type
 */
public interface SearchCallback<T> {

    // Search Result Codes
    int UNKNOWN = 0;
    int INTERMEDIATE = 1;
    int FINAL = 2;

    /**
     * Called when the search from primary source is complete.
     *
     * @param items list of search results
     */
    void onSearchResult(String query, ArrayList<T> items);

    /**
     * Called when the search from primary source is complete.
     *
     * @param items            list of search results
     * @param searchResultCode indicates if the result is final or intermediate for a given query
     *                         since we can get search results from multiple sources.
     */
    default void onSearchResult(String query, ArrayList<T> items, int searchResultCode) {
        onSearchResult(query, items);
    }

    /**
     * Called when the search results should be cleared.
     */
    void clearSearchResult();
}

