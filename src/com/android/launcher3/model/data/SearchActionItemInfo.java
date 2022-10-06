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

import static com.android.launcher3.LauncherSettings.Favorites.EXTENDED_CONTAINERS;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.logger.LauncherAtom.ItemInfo;
import com.android.launcher3.logger.LauncherAtom.SearchActionItem;

/**
 * Represents a SearchAction with in launcher
 */
public class SearchActionItemInfo extends ItemInfoWithIcon implements WorkspaceItemFactory {

    public static final int FLAG_SHOULD_START = 1 << 1;
    public static final int FLAG_SHOULD_START_FOR_RESULT = FLAG_SHOULD_START | 1 << 2;
    public static final int FLAG_BADGE_WITH_PACKAGE = 1 << 3;
    public static final int FLAG_PRIMARY_ICON_FROM_TITLE = 1 << 4;
    public static final int FLAG_BADGE_WITH_COMPONENT_NAME = 1 << 5;
    public static final int FLAG_ALLOW_PINNING = 1 << 6;
    public static final int FLAG_SEARCH_IN_APP = 1 << 7;

    private String mFallbackPackageName;
    private int mFlags = 0;
    private Icon mIcon;

    // If true title does not contain any personal info and eligible for logging.
    private boolean mIsPersonalTitle;
    private Intent mIntent;

    private PendingIntent mPendingIntent;

    public SearchActionItemInfo(Icon icon, String packageName, UserHandle user,
            CharSequence title, boolean isPersonalTitle) {
        mIsPersonalTitle = isPersonalTitle;
        this.itemType = LauncherSettings.Favorites.ITEM_TYPE_SEARCH_ACTION;
        this.user = user == null ? Process.myUserHandle() : user;
        this.title = title;
        this.container = EXTENDED_CONTAINERS;
        mFallbackPackageName = packageName;
        mIcon = icon;
    }

    private SearchActionItemInfo(SearchActionItemInfo info) {
        super(info);
    }

    @Override
    public void copyFrom(@NonNull com.android.launcher3.model.data.ItemInfo info) {
        super.copyFrom(info);
        SearchActionItemInfo itemInfo = (SearchActionItemInfo) info;
        this.mFallbackPackageName = itemInfo.mFallbackPackageName;
        this.mIcon = itemInfo.mIcon;
        this.mFlags = itemInfo.mFlags;
        this.mIsPersonalTitle = itemInfo.mIsPersonalTitle;
    }

    /**
     * Returns if multiple flags are all available.
     */
    public boolean hasFlags(int flags) {
        return (mFlags & flags) != 0;
    }

    public void setFlags(int flags) {
        mFlags |= flags;
    }

    @Override
    @Nullable
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

    @Nullable
    public Icon getIcon() {
        return mIcon;
    }

    @Override
    public ItemInfoWithIcon clone() {
        return new SearchActionItemInfo(this);
    }

    @NonNull
    @Override
    public ItemInfo buildProto(@Nullable FolderInfo fInfo) {
        SearchActionItem.Builder itemBuilder = SearchActionItem.newBuilder()
                .setPackageName(mFallbackPackageName);

        if (!mIsPersonalTitle) {
            itemBuilder.setTitle(title.toString());
        }
        return getDefaultItemInfoBuilder()
                .setSearchActionItem(itemBuilder)
                .setContainerInfo(getContainerInfo())
                .build();
    }

    /**
     * Returns true if result supports drag/drop to home screen
     */
    public boolean supportsPinning() {
        return hasFlags(FLAG_ALLOW_PINNING) && getIntentPackageName() != null;
    }

    /**
     * Creates a {@link WorkspaceItemInfo} coorsponding to search action to be stored in launcher db
     */
    @Override
    public WorkspaceItemInfo makeWorkspaceItem(Context context) {
        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.title = title;
        info.bitmap = bitmap;
        info.intent = mIntent;

        if (hasFlags(FLAG_SHOULD_START_FOR_RESULT)) {
            info.options |= WorkspaceItemInfo.FLAG_START_FOR_RESULT;
        }
        LauncherAppState app = LauncherAppState.getInstance(context);
        app.getModel().updateAndBindWorkspaceItem(() -> {
            PackageItemInfo pkgInfo = new PackageItemInfo(getIntentPackageName(), user);
            app.getIconCache().getTitleAndIconForApp(pkgInfo, false);
            info.bitmap = info.bitmap.withBadgeInfo(pkgInfo.bitmap);
            return info;
        });
        return info;
    }

    @Nullable
    private String getIntentPackageName() {
        if (mIntent != null) {
            if (mIntent.getPackage() != null) return mIntent.getPackage();
            return mFallbackPackageName;
        }
        return null;
    }
}
