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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.util.ArrayList;

/**
 * Controller for a search bar with an edit text and a cancel button.
 */
public class WidgetsSearchBarController implements TextWatcher,
        SearchCallback<WidgetsListBaseEntry>,  ExtendedEditText.OnBackKeyListener,
        View.OnKeyListener {
    private static final String TAG = "WidgetsSearchBarController";
    private static final boolean DEBUG = false;

    protected SearchAlgorithm<WidgetsListBaseEntry> mSearchAlgorithm;
    protected ExtendedEditText mInput;
    protected ImageButton mCancelButton;
    protected SearchModeListener mSearchModeListener;
    protected String mQuery;

    public WidgetsSearchBarController(
            SearchAlgorithm<WidgetsListBaseEntry> algo, ExtendedEditText editText,
            ImageButton cancelButton, SearchModeListener searchModeListener) {
        mSearchAlgorithm = algo;
        mInput = editText;
        mInput.addTextChangedListener(this);
        mInput.setOnBackKeyListener(this);
        mInput.setOnKeyListener(this);
        mCancelButton = cancelButton;
        mCancelButton.setOnClickListener(v -> clearSearchResult());
        mSearchModeListener = searchModeListener;
    }

    @Override
    public void afterTextChanged(final Editable s) {
        mQuery = s.toString();
        if (mQuery.isEmpty()) {
            mSearchAlgorithm.cancel(/* interruptActiveRequests= */ true);
            mSearchModeListener.exitSearchMode();
            mCancelButton.setVisibility(GONE);
        } else {
            mSearchAlgorithm.cancel(/* interruptActiveRequests= */ false);
            mSearchModeListener.enterSearchMode(true);
            mSearchAlgorithm.doSearch(mQuery, this);
            mCancelButton.setVisibility(VISIBLE);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        // Do nothing.
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        // Do nothing.
    }

    @Override
    public void onSearchResult(String query, ArrayList<WidgetsListBaseEntry> items) {
        if (DEBUG) {
            Log.d(TAG, "onSearchResult query: " + query + " items: " + items);
        }
        mSearchModeListener.onSearchResults(items);
    }

    @Override
    public void clearSearchResult() {
        // Any existing search session will be cancelled by setting text to empty.
        mInput.setText("");
    }

    /**
     * Cleans up after search is no longer needed.
     */
    public void onDestroy() {
        mSearchAlgorithm.destroy();
    }

    @Override
    public boolean onBackKey() {
        clearFocus();
        return true;
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
            clearFocus();
            return true;
        }
        return false;
    }

    /**
     * Clears focus from edit text.
     */
    public void clearFocus() {
        mInput.clearFocus();
        mInput.hideKeyboard();
    }
}
