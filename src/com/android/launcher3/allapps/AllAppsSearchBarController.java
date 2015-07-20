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
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;

/**
 * An interface to a search box that AllApps can command.
 */
public abstract class AllAppsSearchBarController {

    protected AlphabeticalAppsList mApps;
    protected Callbacks mCb;

    /**
     * Sets the references to the apps model and the search result callback.
     */
    public final void initialize(AlphabeticalAppsList apps, Callbacks cb) {
        mApps = apps;
        mCb = cb;
        onInitialize();
    }

    /**
     * To be overridden by subclasses.  This method will get called when the controller is set,
     * before getView().
     */
    protected abstract void onInitialize();

    /**
     * Returns the search bar view.
     * @param parent the parent to attach the search bar view to.
     */
    public abstract View getView(ViewGroup parent);

    /**
     * Focuses the search field to handle key events.
     */
    public abstract void focusSearchField();

    /**
     * Returns whether the search field is focused.
     */
    public abstract boolean isSearchFieldFocused();

    /**
     * Resets the search bar state.
     */
    public abstract void reset();

    /**
     * Returns whether the prediction bar should currently be visible depending on the state of
     * the search bar.
     */
    @Deprecated
    public abstract boolean shouldShowPredictionBar();

    /**
     * Callback for getting search results.
     */
    public interface Callbacks {

        /**
         * Called when the bounds of the search bar has changed.
         */
        void onBoundsChanged(Rect newBounds);

        /**
         * Called when the search is complete.
         *
         * @param apps sorted list of matching components or null if in case of failure.
         */
        void onSearchResult(String query, ArrayList<ComponentKey> apps);

        /**
         * Called when the search results should be cleared.
         */
        void clearSearchResult();
    }
}