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

import android.os.Handler;

import com.android.launcher3.AppInfo;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The default search implementation.
 */
public class DefaultAppSearchAlgorithm {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s|\\p{javaSpaceChar}]+");

    private final List<AppInfo> mApps;
    protected final Handler mResultHandler;

    public DefaultAppSearchAlgorithm(List<AppInfo> apps) {
        mApps = apps;
        mResultHandler = new Handler();
    }

    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(null);
        }
    }

    public void doSearch(final String query,
            final AllAppsSearchBarController.Callbacks callback) {
        final ArrayList<ComponentKey> result = getTitleMatchResult(query);
        mResultHandler.post(new Runnable() {

            @Override
            public void run() {
                callback.onSearchResult(query, result);
            }
        });
    }

    protected ArrayList<ComponentKey> getTitleMatchResult(String query) {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        final String queryTextLower = query.toLowerCase();
        final String[] queryWords = SPLIT_PATTERN.split(queryTextLower);

        final ArrayList<ComponentKey> result = new ArrayList<>();
        for (AppInfo info : mApps) {
            if (matches(info, queryWords)) {
                result.add(info.toComponentKey());
            }
        }
        return result;
    }

    protected boolean matches(AppInfo info, String[] queryWords) {
        String title = info.title.toString();
        String[] words = SPLIT_PATTERN.split(title.toLowerCase());
        for (int qi = 0; qi < queryWords.length; qi++) {
            boolean foundMatch = false;
            for (int i = 0; i < words.length; i++) {
                if (words[i].startsWith(queryWords[qi])) {
                    foundMatch = true;
                    break;
                }
            }
            if (!foundMatch) {
                // If there is a word in the query that does not match any words in this
                // title, so skip it.
                return false;
            }
        }
        return true;
    }
}
