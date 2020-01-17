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

package com.android.launcher3.shortcuts;

import static com.android.launcher3.model.WidgetsModel.GO_DISABLE_WIDGETS;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility class to streamline Shortcut query
 */
public class ShortcutRequest {

    private static final String TAG = "ShortcutRequest";

    public static final int ALL = ShortcutQuery.FLAG_MATCH_DYNAMIC
            | ShortcutQuery.FLAG_MATCH_MANIFEST | ShortcutQuery.FLAG_MATCH_PINNED;
    public static final int PUBLISHED = ShortcutQuery.FLAG_MATCH_DYNAMIC
            | ShortcutQuery.FLAG_MATCH_MANIFEST;
    public static final int PINNED = ShortcutQuery.FLAG_MATCH_PINNED;

    private final ShortcutQuery mQuery = GO_DISABLE_WIDGETS ? null : new ShortcutQuery();

    private final Context mContext;
    private final UserHandle mUserHandle;

    boolean mFailed = false;

    public ShortcutRequest(Context context, UserHandle userHandle) {
        mContext = context;
        mUserHandle = userHandle;
    }

    /** @see #forPackage(String, List) */
    public ShortcutRequest forPackage(String packageName) {
        return forPackage(packageName, (List<String>) null);
    }

    /** @see #forPackage(String, List) */
    public ShortcutRequest forPackage(String packageName, String... shortcutIds) {
        return forPackage(packageName, Arrays.asList(shortcutIds));
    }

    /**
     * @param shortcutIds If null, match all shortcuts, otherwise only match the given id's.
     * @return A list of ShortcutInfo's associated with the given package.
     */
    public ShortcutRequest forPackage(String packageName, @Nullable List<String> shortcutIds) {
        if (!GO_DISABLE_WIDGETS && packageName != null) {
            mQuery.setPackage(packageName);
            mQuery.setShortcutIds(shortcutIds);
        }
        return this;
    }

    public ShortcutRequest withContainer(@Nullable ComponentName activity) {
        if (!GO_DISABLE_WIDGETS) {
            if (activity == null) {
                mFailed = true;
            } else {
                mQuery.setActivity(activity);
            }
        }
        return this;
    }

    public QueryResult query(int flags) {
        if (GO_DISABLE_WIDGETS || mFailed) {
            return QueryResult.DEFAULT;
        }
        mQuery.setQueryFlags(flags);

        try {
            return new QueryResult(mContext.getSystemService(LauncherApps.class)
                    .getShortcuts(mQuery, mUserHandle));
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Failed to query for shortcuts", e);
            return QueryResult.DEFAULT;
        }
    }

    public static class QueryResult extends ArrayList<ShortcutInfo> {

        static final QueryResult DEFAULT = new QueryResult(GO_DISABLE_WIDGETS);

        private final boolean mWasSuccess;

        QueryResult(List<ShortcutInfo> result) {
            super(result == null ? Collections.emptyList() : result);
            mWasSuccess = true;
        }

        QueryResult(boolean wasSuccess) {
            mWasSuccess = wasSuccess;
        }


        public boolean wasSuccess() {
            return mWasSuccess;
        }
    }
}
