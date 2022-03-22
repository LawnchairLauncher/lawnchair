/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.search.SearchSession;
import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.UiThread;

import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ResourceBasedOverride;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/** Manages an all apps search session. */
public class SearchSessionManager implements ResourceBasedOverride {

    /** Entry state for the search session (e.g. from all apps). */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {ZERO_ALLAPPS, ZERO_QSB})
    public @interface ZeroEntryState {}
    public static final int ZERO_ALLAPPS = 1;
    public static final int ZERO_QSB = 2;

    /** Creates a {@link SearchSessionManager} instance. */
    public static SearchSessionManager newInstance(Context context) {
        return Overrides.getObject(
                SearchSessionManager.class, context, R.string.search_session_manager_class);
    }

    /** The current {@link SearchSession}. */
    @UiThread
    public void setSearchSession(SearchSession session) {}

    /** {@code true} if IME is shown. */
    public void setIsImeShown(boolean value) {}

    /** Returns {@code true} if IME is enabled. */
    public boolean getIsImeEnabled() {
        return false;
    }

    /** The current entry state for search. */
    public @ZeroEntryState int getEntryState() {
        return ZERO_ALLAPPS;
    }

    /**
     * When user enters all apps surface via tap on home widget, set the state to
     * {@code #ZERO_QSB}. When user exits, reset to {@code #ZERO_ALLAPPS}
     */
    public void setEntryState(@ZeroEntryState int state) {}

    /** This will be called before opening all apps, to prepare zero state suggestions. */
    public void prepareZeroState() {}

    /** Apply predicted items for the search zero state. */
    public void setZeroStatePredictedItems(List<ItemInfo> items) {}

    /** Returns {@code true} if the session is valid and should be enabled. */
    public boolean isValidSession() {
        return false;
    }

    /** Called when the search session is destroyed. */
    public void onDestroy() {}
}
