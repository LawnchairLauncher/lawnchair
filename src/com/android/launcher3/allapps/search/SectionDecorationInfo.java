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

import com.android.launcher3.allapps.AllAppsSectionDecorator.SectionDecorationHandler;

/**
 * Info class for a search section that is primarily used for decoration.
 */
public class SectionDecorationInfo {

    public static final int QUICK_LAUNCH = 1 << 0;
    public static final int GROUPING = 1 << 1;

    private String mSectionId;
    private boolean mFocused;
    private SectionDecorationHandler mDecorationHandler;

    public boolean isFocusedView() {
        return mFocused;
    }

    public void setFocusedView(boolean focused) {
        mFocused = focused;
    }

    public SectionDecorationInfo() {
        this(null);
    }

    public SectionDecorationInfo(String sectionId) {
        mSectionId = sectionId;
    }

    public void setDecorationHandler(SectionDecorationHandler sectionDecorationHandler) {
        mDecorationHandler = sectionDecorationHandler;
    }

    public SectionDecorationHandler getDecorationHandler() {
        return mDecorationHandler;
    }

    /**
     * Returns the section's ID
     */
    public String getSectionId() {
        return mSectionId == null ? "" : mSectionId;
    }
}
