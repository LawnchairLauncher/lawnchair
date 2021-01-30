/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.search.Query;
import android.app.search.SearchContext;
import android.app.search.SearchSession;
import android.app.search.SearchTarget;
import android.app.search.SearchUiManager;
import android.content.Context;
import android.os.CancellationSignal;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.android.app.search.ResultType;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.AllAppsSectionDecorator;
import com.android.launcher3.allapps.search.SearchPipeline;
import com.android.launcher3.allapps.search.SearchSectionInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Search pipeline utilizing {@link android.app.search.SearchUiManager}
 */
public class SearchServicePipeline implements SearchPipeline {
    private static final int SUPPORTED_RESULT_TYPES =
            ResultType.APPLICATION | ResultType.SHORTCUT | ResultType.PLAY | ResultType.PEOPLE
                    | ResultType.SETTING;

    private static final boolean DEBUG = true;
    private static final int REQUEST_TIMEOUT = 200;
    private static final String TAG = "SearchServicePipeline";

    private final Context mContext;
    private final SearchSession mSession;
    private final DeviceSearchAdapterProvider mAdapterProvider;

    private boolean mCanceled = false;


    public SearchServicePipeline(Context context, DeviceSearchAdapterProvider adapterProvider) {
        mContext = context;
        mAdapterProvider = adapterProvider;
        SearchUiManager manager = context.getSystemService(SearchUiManager.class);
        mSession = manager.createSearchSession(
                new SearchContext(SUPPORTED_RESULT_TYPES, REQUEST_TIMEOUT, null));
        SearchSessionTracker.getInstance(context).setSearchSession(mSession);
    }

    @WorkerThread
    @Override
    public void query(String input, Consumer<ArrayList<AllAppsGridAdapter.AdapterItem>> callback,
            CancellationSignal cancellationSignal) {
        mCanceled = false;
        Query query = new Query(input, System.currentTimeMillis(), null);
        mSession.query(query, UI_HELPER_EXECUTOR, targets -> {
            if (!mCanceled) {
                if (DEBUG) {
                    printSearchTargets(input, targets);
                }
                SearchSessionTracker.getInstance(mContext).setQuery(query);
                callback.accept(this.onResult(targets));
            }
            Log.w(TAG, "Ignoring results due to cancel signal");
        });
    }

    /**
     * Given A list of search Targets, pairs a group of search targets to a AdapterItem that can
     * be inflated in AllAppsRecyclerView
     */
    private ArrayList<AllAppsGridAdapter.AdapterItem> onResult(List<SearchTarget> searchTargets) {
        HashMap<String, SearchAdapterItem> adapterMap = new LinkedHashMap<>();
        List<SearchTarget> unmappedChildren = new ArrayList<>();
        SearchSectionInfo section = new SearchSectionInfo();
        section.setDecorationHandler(
                new AllAppsSectionDecorator.SectionDecorationHandler(mContext, true));
        for (SearchTarget target : searchTargets) {
            if (!TextUtils.isEmpty(target.getParentId())) {
                if (!addChildToParent(target, adapterMap)) {
                    unmappedChildren.add(target);
                }
                continue;
            }
            int viewType = mAdapterProvider.getViewTypeForSearchTarget(target);
            if (viewType != -1) {
                SearchAdapterItem adapterItem = new SearchAdapterItem(target, viewType);
                adapterItem.searchSectionInfo = section;
                adapterMap.put(target.getId(), adapterItem);
            }
        }
        for (SearchTarget s : unmappedChildren) {
            if (!addChildToParent(s, adapterMap)) {
                Log.w(TAG,
                        "Unable to pair child " + s.getId() + " to parent " + s.getParentId());
            }
        }
        return new ArrayList<>(adapterMap.values());
    }

    private void printSearchTargets(String query, List<SearchTarget> results) {
        Log.d(TAG, " query=" + query + " size=" + results.size());
        for (SearchTarget s : results) {
            Log.d(TAG, "layoutType=" + s.getLayoutType() + " resultType=" + s.getResultType());
        }
    }

    /**
     * Adds a child SearchTarget to a collection of searchTarget children with a shared parentId.
     * Returns false if no parent searchTarget with id=$parentId does not exists.
     */
    private boolean addChildToParent(SearchTarget target, HashMap<String, SearchAdapterItem> map) {
        if (!map.containsKey(target.getParentId())) return false;
        map.get(target.getParentId()).getInlineItems().add(target);
        return true;
    }

    /**
     * Unregister callbacks and destroy search session
     */
    public void destroy() {
        mSession.destroy();
    }

    /**
     * Cancels current ongoing search request.
     */
    public void cancel() {
        mCanceled = true;
    }
}
