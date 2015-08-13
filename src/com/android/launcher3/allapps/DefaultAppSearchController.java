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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Thunk;

import java.util.List;


/**
 * The default search controller.
 */
final class DefaultAppSearchController extends AllAppsSearchBarController
        implements TextWatcher, TextView.OnEditorActionListener, View.OnClickListener {

    private static final boolean ALLOW_SINGLE_APP_LAUNCH = true;

    private static final int FADE_IN_DURATION = 175;
    private static final int FADE_OUT_DURATION = 100;
    private static final int SEARCH_TRANSLATION_X_DP = 18;

    private final Context mContext;
    @Thunk final InputMethodManager mInputMethodManager;

    private DefaultAppSearchAlgorithm mSearchManager;

    private ViewGroup mContainerView;
    private View mSearchView;
    @Thunk View mSearchBarContainerView;
    private View mSearchButtonView;
    private View mDismissSearchButtonView;
    @Thunk
    ExtendedEditText mSearchBarEditView;
    @Thunk AllAppsRecyclerView mAppsRecyclerView;
    @Thunk Runnable mFocusRecyclerViewRunnable = new Runnable() {
        @Override
        public void run() {
            mAppsRecyclerView.requestFocus();
        }
    };

    public DefaultAppSearchController(Context context, ViewGroup containerView,
            AllAppsRecyclerView appsRecyclerView) {
        mContext = context;
        mInputMethodManager = (InputMethodManager)
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        mContainerView = containerView;
        mAppsRecyclerView = appsRecyclerView;
    }

    @Override
    public View getView(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        mSearchView = inflater.inflate(R.layout.all_apps_search_bar, parent, false);
        mSearchView.setOnClickListener(this);

        mSearchButtonView = mSearchView.findViewById(R.id.search_button);
        mSearchBarContainerView = mSearchView.findViewById(R.id.search_container);
        mDismissSearchButtonView = mSearchBarContainerView.findViewById(R.id.dismiss_search_button);
        mDismissSearchButtonView.setOnClickListener(this);
        mSearchBarEditView = (ExtendedEditText)
                mSearchBarContainerView.findViewById(R.id.search_box_input);
        mSearchBarEditView.addTextChangedListener(this);
        mSearchBarEditView.setOnEditorActionListener(this);
        mSearchBarEditView.setOnBackKeyListener(
                new ExtendedEditText.OnBackKeyListener() {
                    @Override
                    public boolean onBackKey() {
                        // Only hide the search field if there is no query, or if there
                        // are no filtered results
                        String query = Utilities.trim(
                                mSearchBarEditView.getEditableText().toString());
                        if (query.isEmpty() || mApps.hasNoFilteredResults()) {
                            hideSearchField(true, mFocusRecyclerViewRunnable);
                            return true;
                        }
                        return false;
                    }
                });
        return mSearchView;
    }

    @Override
    public void focusSearchField() {
        mSearchBarEditView.requestFocus();
        showSearchField();
    }

    @Override
    public boolean isSearchFieldFocused() {
        return mSearchBarEditView.isFocused();
    }

    @Override
    protected void onInitialize() {
        mSearchManager = new DefaultAppSearchAlgorithm(mApps.getApps());
    }

    @Override
    public void reset() {
        hideSearchField(false, null);
    }

    @Override
    public boolean shouldShowPredictionBar() {
        return false;
    }

    @Override
    public void onClick(View v) {
        if (v == mSearchView) {
            showSearchField();
        } else if (v == mDismissSearchButtonView) {
            hideSearchField(true, mFocusRecyclerViewRunnable);
        }
    }

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
            mSearchManager.cancel(true);
            mCb.clearSearchResult();
        } else {
            mSearchManager.cancel(false);
            mSearchManager.doSearch(query, mCb);
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        // Skip if we disallow app-launch-on-enter
        if (!ALLOW_SINGLE_APP_LAUNCH) {
            return false;
        }
        // Skip if it's not the right action
        if (actionId != EditorInfo.IME_ACTION_SEARCH) {
            return false;
        }
        // Skip if there are more than one icon
        if (mApps.getNumFilteredApps() > 1) {
            return false;
        }
        // Otherwise, find the first icon, or fallback to the search-market-view and launch it
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
        for (int i = 0; i < items.size(); i++) {
            AlphabeticalAppsList.AdapterItem item = items.get(i);
            switch (item.viewType) {
                case AllAppsGridAdapter.ICON_VIEW_TYPE:
                case AllAppsGridAdapter.SEARCH_MARKET_VIEW_TYPE:
                    mAppsRecyclerView.getChildAt(i).performClick();
                    mInputMethodManager.hideSoftInputFromWindow(
                            mContainerView.getWindowToken(), 0);
                    return true;
            }
        }
        return false;
    }

    /**
     * Focuses the search field.
     */
    private void showSearchField() {
        // Show the search bar and focus the search
        final int translationX = Utilities.pxFromDp(SEARCH_TRANSLATION_X_DP,
                mContext.getResources().getDisplayMetrics());
        mSearchBarContainerView.setVisibility(View.VISIBLE);
        mSearchBarContainerView.setAlpha(0f);
        mSearchBarContainerView.setTranslationX(translationX);
        mSearchBarContainerView.animate()
                .alpha(1f)
                .translationX(0)
                .setDuration(FADE_IN_DURATION)
                .withLayer()
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mSearchBarEditView.requestFocus();
                        mInputMethodManager.showSoftInput(mSearchBarEditView,
                                InputMethodManager.SHOW_IMPLICIT);
                    }
                });
        mSearchButtonView.animate()
                .alpha(0f)
                .translationX(-translationX)
                .setDuration(FADE_OUT_DURATION)
                .withLayer();
    }

    /**
     * Unfocuses the search field.
     */
    @Thunk void hideSearchField(boolean animated, final Runnable postAnimationRunnable) {
        mSearchManager.cancel(true);

        final boolean resetTextField = mSearchBarEditView.getText().toString().length() > 0;
        final int translationX = Utilities.pxFromDp(SEARCH_TRANSLATION_X_DP,
                mContext.getResources().getDisplayMetrics());
        if (animated) {
            // Hide the search bar and focus the recycler view
            mSearchBarContainerView.animate()
                    .alpha(0f)
                    .translationX(0)
                    .setDuration(FADE_IN_DURATION)
                    .withLayer()
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            mSearchBarContainerView.setVisibility(View.INVISIBLE);
                            if (resetTextField) {
                                mSearchBarEditView.setText("");
                            }
                            mCb.clearSearchResult();
                            if (postAnimationRunnable != null) {
                                postAnimationRunnable.run();
                            }
                        }
                    });
            mSearchButtonView.setTranslationX(-translationX);
            mSearchButtonView.animate()
                    .alpha(1f)
                    .translationX(0)
                    .setDuration(FADE_OUT_DURATION)
                    .withLayer();
        } else {
            mSearchBarContainerView.setVisibility(View.INVISIBLE);
            if (resetTextField) {
                mSearchBarEditView.setText("");
            }
            mCb.clearSearchResult();
            mSearchButtonView.setAlpha(1f);
            mSearchButtonView.setTranslationX(0f);
            if (postAnimationRunnable != null) {
                postAnimationRunnable.run();
            }
        }
        mInputMethodManager.hideSoftInputFromWindow(mContainerView.getWindowToken(), 0);
    }
}
