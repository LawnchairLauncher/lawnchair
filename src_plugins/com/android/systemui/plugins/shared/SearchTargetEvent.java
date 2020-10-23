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

import android.os.Bundle;

/**
 * Event used for the feedback loop to the plugin. (and future aiai)
 */
public class SearchTargetEvent {
    public static final int POSITION_NONE = -1;

    public static final int SELECT = 0;
    public static final int QUICK_SELECT = 1;
    public static final int LONG_PRESS = 2;
    public static final int CHILD_SELECT = 3;

    private final SearchTarget mSearchTarget;
    private final int mEventType;
    private final int mShortcutPosition;
    private final Bundle mExtras;

    public SearchTargetEvent(SearchTarget searchTarget, int eventType, int shortcutPosition,
            Bundle extras) {
        mSearchTarget = searchTarget;
        mEventType = eventType;
        mShortcutPosition = shortcutPosition;
        mExtras = extras;
    }


    public SearchTarget getSearchTarget() {
        return mSearchTarget;
    }

    public int getShortcutPosition() {
        return mShortcutPosition;
    }

    public int getEventType() {
        return mEventType;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    /**
     * A builder for {@link SearchTarget}
     */
    public static final class Builder {
        private final SearchTarget mSearchTarget;
        private final int mEventType;
        private int mShortcutPosition = POSITION_NONE;
        private Bundle mExtras;

        public Builder(SearchTarget searchTarget, int eventType) {
            mSearchTarget = searchTarget;
            mEventType = eventType;
        }

        public Builder setShortcutPosition(int shortcutPosition) {
            mShortcutPosition = shortcutPosition;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        public SearchTargetEvent build() {
            return new SearchTargetEvent(mSearchTarget, mEventType, mShortcutPosition, mExtras);
        }
    }

}
