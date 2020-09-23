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
package com.android.systemui.plugins.shared;

import android.content.pm.ShortcutInfo;
import android.os.Bundle;

import java.util.List;

/**
 * Used to return all apps search targets.
 */
public class SearchTarget implements Comparable<SearchTarget> {

    public enum ViewType {

        /**
         * Consists of N number of icons. (N: launcher column count)
         */
        TOP_HIT(0),

        /**
         * Consists of 1 icon and two subsidiary icons.
         */
        HERO(1),

        /**
         * Main/sub/breadcrumb texts are rendered.
         */
        DETAIL(2),

        /**
         * Consists of an icon, three detail strings.
         */
        ROW(3),

        /**
         * Consists of an icon, three detail strings and a button.
         */
        ROW_WITH_BUTTON(4),

        /**
         * Consists of a single slice view
         */
        SLICE(5),

        /**
         * Similar to hero section.
         */
        SHORTCUT(6),

        /**
         * Person icon and handling app icons are rendered.
         */
        PEOPLE(7),

        /**
         * N number of 1x1 ratio thumbnail is rendered.
         * (current N = 3)
         */
        THUMBNAIL(8);

        private final int mId;
        ViewType(int id) {
            mId = id;
        }

        public int get() {
            return mId;
        }
    }

    public enum ItemType {
        PLAY_RESULTS(0, "Play Store", ViewType.DETAIL),
        SETTINGS_ROW(1, "Settings", ViewType.ROW),
        SETTINGS_SLICE(2, "Settings", ViewType.SLICE),
        APP(3, "", ViewType.TOP_HIT),
        APP_HERO(4, "", ViewType.HERO),
        SHORTCUT(5, "Shortcuts", ViewType.SHORTCUT),
        PEOPLE(6, "People", ViewType.PEOPLE),
        SCREENSHOT(7, "Screenshots", ViewType.THUMBNAIL);

        private final int mId;

        /** Used to render section title. */
        private final String mTitle;
        private final ViewType mViewType;

        ItemType(int id, String title, ViewType type) {
            mId = id;
            mTitle = title;
            mViewType = type;
        }

        public ViewType getViewType() {
            return mViewType;
        }

        public String getTitle() {
            return mTitle;
        }

        public int getId() {
            return mId;
        }
    }

    public ItemType type;
    public List<ShortcutInfo> shortcuts;
    public Bundle bundle;
    public float score;
    public String mSessionId;

    /**
     * Constructor to create the search target. Bundle is currently temporary to hold
     * search target primitives that cannot be expressed as java primitive objects
     * or AOSP native objects.
     *
     */
    public SearchTarget(ItemType itemType, List<ShortcutInfo> shortcuts,
            Bundle bundle, float score, String sessionId) {
        this.type = itemType;
        this.shortcuts = shortcuts;
        this.bundle = bundle;
        this.score = score;
        this.mSessionId = sessionId;
    }

    @Override
    public int compareTo(SearchTarget o) {
        return Float.compare(o.score, score);
    }
}
