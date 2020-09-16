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

package com.android.systemui.plugins;

import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Implement this plugin interface to fetch search result data from the plugin side.
 */
@ProvidesInterface(action = AllAppsSearchPlugin.ACTION, version = AllAppsSearchPlugin.VERSION)
public interface AllAppsSearchPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_ALL_APPS_SEARCH_ACTIONS";
    int VERSION = 5;


    /**
     * Send signal when user enters all apps.
     */
    void startAllAppsSession();

    /**
     * Send signal when user starts typing.
     */
    void startedSearchSession();

    /**
     * Send over the query and get the search results.
     */
    void performSearch(String query, Consumer<List<SearchTarget>> results);

    /**
     * Send over search target interaction events to Plugin
     */
    void notifySearchTargetEvent(SearchTargetEvent event);

    /**
     * Send signal when user exits all apps.
     */
    void endAllAppsSession();
}