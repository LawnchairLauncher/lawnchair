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

import android.content.Context;

/**
 * Info class for a search section
 */
public class SearchSectionInfo {
    private final int mTitleResId;

    public SearchSectionInfo(int titleResId) {
        mTitleResId = titleResId;
    }

    /**
     * Returns the section's title
     */
    public String getTitle(Context context) {
        return context.getString(mTitleResId);
    }
}
