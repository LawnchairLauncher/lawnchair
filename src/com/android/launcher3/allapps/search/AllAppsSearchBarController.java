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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.PluginListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An interface to a search box that AllApps can command.
 */
public class AllAppsSearchBarController
        implements TextWatcher, OnEditorActionListener, ExtendedEditText.OnBackKeyListener,
        OnFocusChangeListener, PluginListener<AllAppsSearchPlugin> {

    private static final String TAG = "AllAppsSearchBarContoller";
    protected BaseDraggingActivity mLauncher;
    protected Callbacks mCb;
    protected ExtendedEditText mInput;
    protected String mQuery;

    protected SearchAlgorithm mSearchAlgorithm;
    private AllAppsSearchPlugin mPlugin;
    private Consumer mPlubinCb;

    public void setVisibility(int visibility) {
        mInput.setVisibility(visibility);
    }

    /**
     * Sets the references to the apps model and the search result callback.
     */
    public final void initialize(
            SearchAlgorithm searchAlgorithm, ExtendedEditText input,
            BaseDraggingActivity launcher, Callbacks cb, Consumer<List<Bundle>> secondaryCb) {
        mCb = cb;
        mLauncher = launcher;

        mInput = input;
        mInput.addTextChangedListener(this);
        mInput.setOnEditorActionListener(this);
        mInput.setOnBackKeyListener(this);
        mInput.setOnFocusChangeListener(this);
        mSearchAlgorithm = searchAlgorithm;

        PluginManagerWrapper.INSTANCE.get(launcher).addPluginListener(this,
                AllAppsSearchPlugin.class);
        mPlubinCb = secondaryCb;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (mPlugin != null) {
            if (s.length() == 0) {
                mPlugin.startedTyping();
            }
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Do nothing
    }

    @Override
    public void afterTextChanged(final Editable s) {
        mQuery = s.toString();
        if (mQuery.isEmpty()) {
            mSearchAlgorithm.cancel(true);
            mCb.clearSearchResult();
        } else {
            mSearchAlgorithm.cancel(false);
            mSearchAlgorithm.doSearch(mQuery, mCb);
            if (mPlugin != null) {
                mPlugin.performSearch(mQuery, mPlubinCb);
            }
        }
    }

    public void refreshSearchResult() {
        if (TextUtils.isEmpty(mQuery)) {
            return;
        }
        // If play store continues auto updating an app, we want to show partial result.
        mSearchAlgorithm.cancel(false);
        mSearchAlgorithm.doSearch(mQuery, mCb);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                ItemInfo info = Launcher.getLauncher(mLauncher).getAppsView()
                        .getHighlightedItemInfo();
                if (info != null) {
                    return mLauncher.startActivitySafely(v, info.getIntent(), info);
                }
            }
        }

        // Skip if it's not the right action
        if (actionId != EditorInfo.IME_ACTION_SEARCH) {
            return false;
        }

        // Skip if the query is empty
        String query = v.getText().toString();
        if (query.isEmpty()) {
            return false;
        }
        return mLauncher.startActivitySafely(v,
                PackageManagerHelper.getMarketSearchIntent(mLauncher, query), null
        );
    }

    @Override
    public boolean onBackKey() {
        // Only hide the search field if there is no query
        String query = Utilities.trim(mInput.getEditableText().toString());
        if (query.isEmpty()) {
            reset();
            return true;
        }
        return false;
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (!hasFocus) {
            mInput.hideKeyboard();
        }
    }

    /**
     * Resets the search bar state.
     */
    public void reset() {
        mCb.clearSearchResult();
        mInput.reset();
        mQuery = null;
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

    @Override
    public void onPluginConnected(AllAppsSearchPlugin allAppsSearchPlugin, Context context) {
        if (FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
            mPlugin = allAppsSearchPlugin;
            checkCallPermission();
        }
    }

    /**
     * Check call permissions.
     */
    public void checkCallPermission() {
        final String[] permission = {"android.permission.CALL_PHONE",
                "android.permission.READ_CONTACTS"};
        boolean request = false;
        for (String p : permission) {
            int permissionCheck = ContextCompat.checkSelfPermission(mLauncher, p);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                request = true;
            }
        }

        if (!request) return;
        boolean rationale = false;
        for (String p : permission) {
            if (mLauncher.shouldShowRequestPermissionRationale(p)) {
                rationale = true;
            }
            if (rationale) {
                Log.e(TAG, p + " Show rationale");
                Toast.makeText(mLauncher, "Requesting Permissions", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(mLauncher,  permission,  123);
                Log.e(TAG, p + " request permission");
            }
        }

    }

    /**
     * Callback for getting search results.
     */
    public interface Callbacks {

        /**
         * Called when the search from primary source is complete.
         *
         * @param items sorted list of search result adapter items.
         */
        void onSearchResult(String query, ArrayList<AlphabeticalAppsList.AdapterItem> items);

        /**
         * Called when the search results should be cleared.
         */
        void clearSearchResult();
    }
}