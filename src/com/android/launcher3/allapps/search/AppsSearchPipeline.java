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
package com.android.launcher3.allapps.search;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_SHORTCUTS;
import static com.android.launcher3.allapps.AllAppsGridAdapter.VIEW_TYPE_SEARCH_HERO_APP;

import android.content.Context;
import android.content.pm.ShortcutInfo;

import androidx.annotation.WorkerThread;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem;
import com.android.launcher3.allapps.AllAppsSectionDecorator.SectionDecorationHandler;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupPopulator;
import com.android.launcher3.shortcuts.ShortcutRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A device search section for handling app searches
 */
public class AppsSearchPipeline implements SearchPipeline {

    private static final int MAX_RESULTS_COUNT = 5;
    private static final int MAX_HERO_SECTION_COUNT = 2;
    private static final int MAX_SHORTCUTS_COUNT = 2;

    private final SearchSectionInfo mSearchSectionInfo;
    private final LauncherAppState mLauncherAppState;
    private final boolean mHeroSectionSupported;

    public AppsSearchPipeline(Context context, LauncherAppState launcherAppState) {
        this(context, launcherAppState, true);
    }

    public AppsSearchPipeline(Context context, LauncherAppState launcherAppState,
            boolean supportsHeroView) {
        mLauncherAppState = launcherAppState;
        mSearchSectionInfo = new SearchSectionInfo();
        mSearchSectionInfo.setDecorationHandler(
                new SectionDecorationHandler(context, true));
        mHeroSectionSupported = supportsHeroView;
    }

    @Override
    @WorkerThread
    public void performSearch(String query, Consumer<ArrayList<AdapterItem>> callback) {
        mLauncherAppState.getModel().enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                List<AppInfo> matchingResults = getTitleMatchResult(apps.data, query);
                if (mHeroSectionSupported && matchingResults.size() <= MAX_HERO_SECTION_COUNT) {
                    callback.accept(getHeroAdapterItems(app.getContext(), matchingResults));
                } else {
                    callback.accept(getAdapterItems(matchingResults));
                }
            }
        });
    }

    /**
     * Returns MAX_SHORTCUTS_COUNT shortcuts from local cache
     * TODO: Shortcuts should be ranked based on relevancy
     */
    private ArrayList<WorkspaceItemInfo> getShortcutInfos(Context context, AppInfo appInfo) {
        List<ShortcutInfo> shortcuts = new ShortcutRequest(context, appInfo.user)
                .withContainer(appInfo.getTargetComponent())
                .query(ShortcutRequest.PUBLISHED);
        shortcuts = PopupPopulator.sortAndFilterShortcuts(shortcuts, null);
        IconCache cache = LauncherAppState.getInstance(context).getIconCache();
        ArrayList<WorkspaceItemInfo> shortcutItems = new ArrayList<>();
        for (int i = 0; i < shortcuts.size() && i < MAX_SHORTCUTS_COUNT; i++) {
            final ShortcutInfo shortcut = shortcuts.get(i);
            final WorkspaceItemInfo si = new WorkspaceItemInfo(shortcut, context);
            cache.getUnbadgedShortcutIcon(si, shortcut);
            si.rank = i;
            si.container = CONTAINER_SHORTCUTS;
            shortcutItems.add(si);
        }
        return shortcutItems;
    }

    /**
     * Filters {@link AppInfo}s matching specified query
     */
    public static ArrayList<AppInfo> getTitleMatchResult(List<AppInfo> apps, String query) {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        final String queryTextLower = query.toLowerCase();
        final ArrayList<AppInfo> result = new ArrayList<>();
        DefaultAppSearchAlgorithm.StringMatcher matcher =
                DefaultAppSearchAlgorithm.StringMatcher.getInstance();
        for (AppInfo info : apps) {
            if (DefaultAppSearchAlgorithm.matches(info, queryTextLower, matcher)) {
                result.add(info);
            }
        }
        return result;
    }

    private ArrayList<AdapterItem> getHeroAdapterItems(Context context, List<AppInfo> apps) {
        ArrayList<AdapterItem> adapterItems = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            //hero app
            AppInfo appInfo = apps.get(i);
            ArrayList<WorkspaceItemInfo> shortcuts = getShortcutInfos(context, appInfo);
            AdapterItem adapterItem = new AllAppsGridAdapter.AdapterItemWithPayload(shortcuts,
                    VIEW_TYPE_SEARCH_HERO_APP);
            adapterItem.appInfo = appInfo;
            adapterItem.searchSectionInfo = mSearchSectionInfo;
            adapterItems.add(adapterItem);
        }
        return adapterItems;
    }

    private ArrayList<AdapterItem> getAdapterItems(List<AppInfo> matchingApps) {
        ArrayList<AdapterItem> items = new ArrayList<>();
        for (int i = 0; i < matchingApps.size() && i < MAX_RESULTS_COUNT; i++) {
            AdapterItem appItem = AdapterItem.asApp(i, "", matchingApps.get(i), i);
            appItem.searchSectionInfo = mSearchSectionInfo;
            items.add(appItem);
        }

        return items;
    }
}
