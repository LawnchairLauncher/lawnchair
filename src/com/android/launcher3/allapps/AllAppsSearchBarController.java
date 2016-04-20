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

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;

/**
 * An interface to a search box that AllApps can command.
 */
public abstract class AllAppsSearchBarController
        implements TextWatcher, OnEditorActionListener, ExtendedEditText.OnBackKeyListener {

    protected Launcher mLauncher;
    protected AlphabeticalAppsList mApps;
    protected Callbacks mCb;
    protected ExtendedEditText mInput;

    protected DefaultAppSearchAlgorithm mSearchAlgorithm;
    protected InputMethodManager mInputMethodManager;

    /**
     * Sets the references to the apps model and the search result callback.
     */
    public final void initialize(
            AlphabeticalAppsList apps, ExtendedEditText input,
            Launcher launcher, Callbacks cb) {
        mApps = apps;
        mCb = cb;
        mLauncher = launcher;

        mInput = input;
        mInput.addTextChangedListener(this);
        mInput.setOnEditorActionListener(this);
        mInput.setOnBackKeyListener(this);

        mInputMethodManager = (InputMethodManager)
                mInput.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        mSearchAlgorithm = onInitializeSearch();
    }

    /**
     * To be implemented by subclasses. This method will get called when the controller is set.
     */
    protected abstract DefaultAppSearchAlgorithm onInitializeSearch();

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Do nothing
    }

    @Override
    public void afterTextChanged(final Editable s) {
        String query = s.toString();
        if (query.isEmpty()) {
            mSearchAlgorithm.cancel(true);
            mCb.clearSearchResult();
        } else {
            mSearchAlgorithm.cancel(false);
            mSearchAlgorithm.doSearch(query, mCb);
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        // Skip if it's not the right action
        if (actionId != EditorInfo.IME_ACTION_SEARCH) {
            return false;
        }
        // Skip if the query is empty
        String query = v.getText().toString();
        if (query.isEmpty()) {
            return false;
        }
        return mLauncher.startActivitySafely(v, createMarketSearchIntent(query), null);
    }

    @Override
    public boolean onBackKey() {
        // Only hide the search field if there is no query, or if there
        // are no filtered results
        String query = Utilities.trim(mInput.getEditableText().toString());
        if (query.isEmpty() || mApps.hasNoFilteredResults()) {
            reset();
            return true;
        }
        return false;
    }

    /**
     * Resets the search bar state.
     */
    public void reset() {
        unfocusSearchField();
        mCb.clearSearchResult();
        mInput.setText("");
        mInputMethodManager.hideSoftInputFromWindow(mInput.getWindowToken(), 0);
    }

    protected void unfocusSearchField() {
        View nextFocus = mInput.focusSearch(View.FOCUS_DOWN);
        if (nextFocus != null) {
            nextFocus.requestFocus();
        }
    }

    /**
     * Focuses the search field to handle key events.
     */
    public void focusSearchField() {
        mInput.requestFocus();
        mInputMethodManager.showSoftInput(mInput, InputMethodManager.SHOW_IMPLICIT);
    }

    /**
     * Returns whether the search field is focused.
     */
    public boolean isSearchFieldFocused() {
        return mInput.isFocused();
    }

    /**
     * Creates a new market search intent.
     */
    public Intent createMarketSearchIntent(String query) {
        Uri marketSearchUri = Uri.parse("market://search")
                .buildUpon()
                .appendQueryParameter("c", "apps")
                .appendQueryParameter("q", query)
                .build();
        return new Intent(Intent.ACTION_VIEW).setData(marketSearchUri);
    }

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