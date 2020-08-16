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

import com.android.launcher3.allapps.AllAppsSectionDecorator.SectionDecorationHandler;

/**
 * Info class for a search section
 */
public class SearchSectionInfo {

    private final int mTitleResId;
    private SectionDecorationHandler mDecorationHandler;

    public int getPosStart() {
        return mPosStart;
    }

    public void setPosStart(int posStart) {
        mPosStart = posStart;
    }

    public int getPosEnd() {
        return mPosEnd;
    }

    public void setPosEnd(int posEnd) {
        mPosEnd = posEnd;
    }

    private int mPosStart;
    private int mPosEnd;

    public SearchSectionInfo() {
        this(-1);
    }

    public SearchSectionInfo(int titleResId) {
        mTitleResId = titleResId;
    }

    public void setDecorationHandler(SectionDecorationHandler sectionDecorationHandler) {
        mDecorationHandler = sectionDecorationHandler;
    }


    public SectionDecorationHandler getDecorationHandler() {
        return mDecorationHandler;
    }

    /**
     * Returns the section's title
     */
    public String getTitle(Context context) {
        if (mTitleResId == -1) {
            return "";
        }
        return context.getString(mTitleResId);
    }
}
