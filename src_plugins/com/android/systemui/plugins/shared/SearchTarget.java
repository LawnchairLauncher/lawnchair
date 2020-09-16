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
        TOP_HIT(0),
        HERO(1),
        DETAIL(2),
        ROW(3),
        ROW_WITH_BUTTON(4),
        SLICE(5),
        SHORTCUT(6),
        PEOPLE(7);

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
        PEOPLE(6, "People", ViewType.PEOPLE);

        private final int mId;
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

    /**
     * Constructor to create the search target. Bundle is currently temporary to hold
     * search target primitives that cannot be expressed as java primitive objects
     * or AOSP native objects.
     *
     */
    public SearchTarget(ItemType itemType, List<ShortcutInfo> shortcuts,
            Bundle bundle, float score) {
        this.type = itemType;
        this.shortcuts = shortcuts;
        this.bundle = bundle;
        this.score = score;
    }

    @Override
    public int compareTo(SearchTarget o) {
        return Float.compare(o.score, score);
    }
}
