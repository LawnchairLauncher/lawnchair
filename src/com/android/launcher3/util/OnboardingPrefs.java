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
package com.android.launcher3.util;

import android.content.SharedPreferences;
import android.util.ArrayMap;

import androidx.annotation.StringDef;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherStateManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Map;

/**
 * Stores and retrieves onboarding-related data via SharedPreferences.
 */
public class OnboardingPrefs<T extends Launcher> {

    public static final String HOME_BOUNCE_SEEN = "launcher.apps_view_shown";
    public static final String SHELF_BOUNCE_SEEN = "launcher.shelf_bounce_seen";
    public static final String HOME_BOUNCE_COUNT = "launcher.home_bounce_count";
    public static final String SHELF_BOUNCE_COUNT = "launcher.shelf_bounce_count";

    /**
     * Events that either have happened or have not (booleans).
     */
    @StringDef(value = {
            HOME_BOUNCE_SEEN,
            SHELF_BOUNCE_SEEN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EventBoolKey {}

    /**
     * Events that occur multiple times, which we count up to a max defined in {@link #MAX_COUNTS}.
     */
    @StringDef(value = {
            HOME_BOUNCE_COUNT,
            SHELF_BOUNCE_COUNT,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EventCountKey {}

    private static final Map<String, Integer> MAX_COUNTS;
    static {
        Map<String, Integer> maxCounts = new ArrayMap<>(2);
        maxCounts.put(HOME_BOUNCE_COUNT, 3);
        maxCounts.put(SHELF_BOUNCE_COUNT, 3);
        MAX_COUNTS = Collections.unmodifiableMap(maxCounts);
    }

    protected final T mLauncher;
    protected final SharedPreferences mSharedPrefs;
    protected final LauncherStateManager mStateManager;

    public OnboardingPrefs(T launcher, SharedPreferences sharedPrefs,
            LauncherStateManager stateManager) {
        mLauncher = launcher;
        mSharedPrefs = sharedPrefs;
        mStateManager = stateManager;
    }

    /** @return The number of times we have seen the given event. */
    public int getCount(@EventCountKey String key) {
        return mSharedPrefs.getInt(key, 0);
    }

    /** @return Whether we have seen this event enough times, as defined by {@link #MAX_COUNTS}. */
    public boolean hasReachedMaxCount(@EventCountKey String eventKey) {
        return hasReachedMaxCount(getCount(eventKey), eventKey);
    }

    private boolean hasReachedMaxCount(int count, @EventCountKey String eventKey) {
        return count >= MAX_COUNTS.get(eventKey);
    }

    /** @return Whether we have seen the given event. */
    public boolean getBoolean(@EventBoolKey String key) {
        return mSharedPrefs.getBoolean(key, false);
    }

    /**
     * Add 1 to the given event count, if we haven't already reached the max count.
     */
    public void incrementEventCount(@EventCountKey String eventKey) {
        int count = getCount(eventKey);
        if (hasReachedMaxCount(count, eventKey)) {
            return;
        }
        count++;
        mSharedPrefs.edit().putInt(eventKey, count).apply();
    }
}
