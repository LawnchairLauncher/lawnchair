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
package com.android.launcher3.allapps;

import android.support.animation.SpringAnimation;
import android.support.annotation.NonNull;
import android.view.KeyEvent;

/**
 * Interface for controlling the Apps search UI.
 */
public interface SearchUiManager {

    /**
     * Initializes the search manager.
     */
    void initialize(AlphabeticalAppsList appsList, AllAppsRecyclerView recyclerView);

    /**
     * A {@link SpringAnimation} that will be used when the user flings.
     */
    @NonNull SpringAnimation getSpringForFling();

    /**
     * Notifies the search manager that the apps-list has changed and the search UI should be
     * updated accordingly.
     */
    void refreshSearchResult();

    /**
     * Notifies the search manager to close any active search session.
     */
    void reset();

    /**
     * Called before dispatching a key event, in case the search manager wants to initialize
     * some UI beforehand.
     */
    void preDispatchKeyEvent(KeyEvent keyEvent);

    void addOnScrollRangeChangeListener(OnScrollRangeChangeListener listener);

    /**
     * Callback for listening to changes in the vertical scroll range when opening all-apps.
     */
    interface OnScrollRangeChangeListener {

        void onScrollRangeChanged(int scrollRange);
    }
}
