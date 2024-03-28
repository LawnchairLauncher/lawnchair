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

import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.SuggestionSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.views.ActivityContext;

/**
 * An interface to a search box that AllApps can command.
 */
public class AllAppsSearchBarController
        implements TextWatcher, OnEditorActionListener, ExtendedEditText.OnBackKeyListener,
        OnFocusChangeListener {

    protected ActivityContext mLauncher;
    protected SearchCallback<AdapterItem> mCallback;
    protected ExtendedEditText mInput;
    protected String mQuery;
    private String[] mTextConversions;

    protected SearchAlgorithm<AdapterItem> mSearchAlgorithm;

    public void setVisibility(int visibility) {
        mInput.setVisibility(visibility);
    }

    /**
     * Sets the references to the apps model and the search result callback.
     */
    public final void initialize(
            SearchAlgorithm<AdapterItem> searchAlgorithm, ExtendedEditText input,
            ActivityContext launcher, SearchCallback<AdapterItem> callback) {
        mCallback = callback;
        mLauncher = launcher;

        mInput = input;
        mInput.addTextChangedListener(this);
        mInput.setOnEditorActionListener(this);
        mInput.setOnBackKeyListener(this);
        mInput.addOnFocusChangeListener(this);
        mSearchAlgorithm = searchAlgorithm;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        // Do nothing
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mTextConversions = extractTextConversions(s);
    }

    /**
     * Extract text conversions from composing text and send them for search.
     */
    public static String[] extractTextConversions(CharSequence text) {
        if (text instanceof SpannableStringBuilder) {
            SpannableStringBuilder spanned = (SpannableStringBuilder) text;
            SuggestionSpan[] suggestionSpans =
                spanned.getSpans(0, text.length(), SuggestionSpan.class);
            if (suggestionSpans != null && suggestionSpans.length > 0) {
                spanned.removeSpan(suggestionSpans[0]);
                return suggestionSpans[0].getSuggestions();
            }
        }
        return null;
    }

    @Override
    public void afterTextChanged(final Editable s) {
        mQuery = s.toString();
        if (mQuery.isEmpty()) {
            mSearchAlgorithm.cancel(true);
            mCallback.clearSearchResult();
        } else {
            mSearchAlgorithm.cancel(false);
            mSearchAlgorithm.doSearch(mQuery, mTextConversions, mCallback);
        }
    }

    public void refreshSearchResult() {
        if (TextUtils.isEmpty(mQuery)) {
            return;
        }
        // If play store continues auto updating an app, we want to show partial result.
        mSearchAlgorithm.cancel(false);
        mSearchAlgorithm.doSearch(mQuery, mCallback);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {

        if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
            // selectFocusedView should return SearchTargetEvent that is passed onto onClick
            return mLauncher.getAppsView().getMainAdapterProvider().launchHighlightedItem();
        }
        return false;
    }

    @Override
    public boolean onBackKey() {
        // Only hide the search field if there is no query
        String query = Utilities.trim(mInput.getEditableText().toString());
        if (!query.isEmpty()) {
            reset();
            return true;
        }
        return false;
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (!hasFocus && !FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
            mInput.hideKeyboard();
        }
    }

    /**
     * Resets the search bar state.
     */
    public void reset() {
        mCallback.clearSearchResult();
        mInput.reset();
        mQuery = null;
        mInput.removeOnFocusChangeListener(this);
    }

    /**
     * Focuses the search field to handle key events.
     */
    public void focusSearchField() {
        mInput.showKeyboard();
    }

    /**
     * Returns whether the search field is focused.
     */
    public boolean isSearchFieldFocused() {
        return mInput.isFocused();
    }
}
