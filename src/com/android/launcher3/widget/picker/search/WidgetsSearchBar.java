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

import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.util.List;

/**
 * Interface for a widgets picker search bar.
 */
public interface WidgetsSearchBar {
    /**
     * Attaches a controller to the search bar which interacts with {@code searchModeListener}.
     */
    void initialize(WidgetsSearchDataProvider dataProvider, SearchModeListener searchModeListener);

    /**
     * Clears search bar.
     */
    void reset();

    /** Returns {@code true} if the search bar is in focus. */
    boolean isSearchBarFocused();

    /**
     * Clears focus from search bar.
     */
    void clearSearchBarFocus();

    /**
     * Sets the vertical location, in pixels, of this search bar relative to its top position.
     */
    void setTranslationY(float translationY);


    /**
     * Provides corpus from which search results must be returned.
     */
    interface WidgetsSearchDataProvider {
        /**
         * Returns the widgets from which the search should return the results.
         */
        List<WidgetsListBaseEntry> getWidgets();
    }
}
