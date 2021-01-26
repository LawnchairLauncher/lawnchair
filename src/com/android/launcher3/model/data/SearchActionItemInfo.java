/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.model.data;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Process;
import android.os.UserHandle;

/**
 * Represents a SearchAction with in launcher
 */
public class SearchActionItemInfo extends ItemInfoWithIcon {

    public static final int FLAG_SHOULD_START = 1 << 1;
    public static final int FLAG_SHOULD_START_FOR_RESULT = FLAG_SHOULD_START | 1 << 2;

    private final String mFallbackPackageName;

    private int mFlags = 0;
    private final Icon mIcon;

    private Intent mIntent;

    private PendingIntent mPendingIntent;

    public SearchActionItemInfo(Icon icon, String packageName, UserHandle user,
            CharSequence title) {
        this.user = user == null ? Process.myUserHandle() : user;
        this.title = title;
        mFallbackPackageName = packageName;
        mIcon = icon;
    }

    public SearchActionItemInfo(SearchActionItemInfo info) {
        super(info);
        mIcon = info.mIcon;
        mFallbackPackageName = info.mFallbackPackageName;
        mFlags = info.mFlags;
        title = info.title;
    }

    public void setFlags(int flags) {
        mFlags = flags;
    }

    public int getFlags() {
        return mFlags;
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    /**
     * Setter for mIntent with assertion for null value mPendingIntent
     */
    public void setIntent(Intent intent) {
        if (mPendingIntent != null && intent != null) {
            throw new RuntimeException(
                    "SearchActionItemInfo can only have either an Intent or a PendingIntent");
        }
        mIntent = intent;
    }

    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    /**
     * Setter of mPendingIntent with assertion for null value mIntent
     */
    public void setPendingIntent(PendingIntent pendingIntent) {
        if (mIntent != null && pendingIntent != null) {
            throw new RuntimeException(
                    "SearchActionItemInfo can only have either an Intent or a PendingIntent");
        }
        mPendingIntent = pendingIntent;
    }


    /**
     * Returns if a flag is set on instance
     */
    public boolean hasFlag(int flag) {
        return (mFlags & flag) == flag;
    }

    @Override
    public ItemInfoWithIcon clone() {
        return new SearchActionItemInfo(this);
    }

    public Icon getIcon() {
        return mIcon;
    }
}
