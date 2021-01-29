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
package com.android.launcher3.search;

import static com.android.launcher3.model.data.SearchActionItemInfo.FLAG_BADGE_FROM_ICON;
import static com.android.launcher3.model.data.SearchActionItemInfo.FLAG_PRIMARY_ICON_FROM_TITLE;
import static com.android.launcher3.search.SearchTargetUtil.BUNDLE_EXTRA_BADGE_FROM_ICON;
import static com.android.launcher3.search.SearchTargetUtil.BUNDLE_EXTRA_PRIMARY_ICON_FROM_TITLE;
import static com.android.launcher3.search.SearchTargetUtil.BUNDLE_EXTRA_SHOULD_START;
import static com.android.launcher3.search.SearchTargetUtil.BUNDLE_EXTRA_SHOULD_START_FOR_RESULT;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.app.search.SearchAction;
import android.app.search.SearchTarget;
import android.app.search.SearchTargetEvent;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.app.search.ResultType;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.SearchActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.ComponentKey;

import java.util.List;
import java.util.function.Consumer;

/**
 * A {@link BubbleTextView} representing a single cell result in AllApps
 */
public class SearchResultIcon extends BubbleTextView implements SearchTargetHandler {

    private final Launcher mLauncher;

    private String mTargetId;
    private Consumer<ItemInfoWithIcon> mOnItemInfoChanged;


    public SearchResultIcon(Context context) {
        this(context, null, 0);
    }

    public SearchResultIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(getContext());
    }

    private boolean mLongPressSupported;

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setLongPressTimeoutFactor(1f);
        setOnFocusChangeListener(mLauncher.getFocusHandler());
        setOnClickListener(this);
        setOnLongClickListener(this);
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                mLauncher.getDeviceProfile().allAppsCellHeightPx));
    }

    /**
     * Applies {@link SearchTarget} to view. registers a consumer after a corresponding
     * {@link ItemInfoWithIcon} is created
     */
    public void applySearchTarget(SearchTarget searchTarget, List<SearchTarget> inlineItems,
            Consumer<ItemInfoWithIcon> cb) {
        mOnItemInfoChanged = cb;
        apply(searchTarget, inlineItems);
    }

    @Override
    public void apply(SearchTarget parentTarget, List<SearchTarget> children) {
        mTargetId = parentTarget.getId();
        if (parentTarget.getShortcutInfo() != null) {
            prepareUsingShortcutInfo(parentTarget.getShortcutInfo());
            mLongPressSupported = true;
        } else if (parentTarget.getSearchAction() != null) {
            prepareUsingSearchAction(parentTarget);
            mLongPressSupported = false;
        } else {
            prepareUsingApp(new ComponentName(parentTarget.getPackageName(),
                    parentTarget.getExtras().getString(SearchTargetUtil.EXTRA_CLASS)),
                    parentTarget.getUserHandle());
            mLongPressSupported = true;
        }
    }

    private void prepareUsingSearchAction(SearchTarget searchTarget) {
        SearchAction searchAction = searchTarget.getSearchAction();
        Bundle extras = searchAction.getExtras();

        SearchActionItemInfo itemInfo = new SearchActionItemInfo(searchAction.getIcon(),
                searchTarget.getPackageName(), searchTarget.getUserHandle(),
                searchAction.getTitle()
        );
        itemInfo.setIntent(searchAction.getIntent());
        itemInfo.setPendingIntent(searchAction.getPendingIntent());

        //TODO: remove this after flags are introduced in SearchAction. Settings results require
        // startActivityForResult
        boolean isSettingsResult = searchTarget.getResultType() == ResultType.SETTING;
        if ((extras != null && extras.getBoolean(BUNDLE_EXTRA_SHOULD_START_FOR_RESULT))
                || isSettingsResult) {
            itemInfo.setFlags(SearchActionItemInfo.FLAG_SHOULD_START_FOR_RESULT);
        } else if (extras != null && extras.getBoolean(BUNDLE_EXTRA_SHOULD_START)) {
            itemInfo.setFlags(SearchActionItemInfo.FLAG_SHOULD_START);
        }
        if (extras != null && extras.getBoolean(BUNDLE_EXTRA_BADGE_FROM_ICON)) {
            itemInfo.setFlags(FLAG_BADGE_FROM_ICON);
        }
        if (extras != null && extras.getBoolean(BUNDLE_EXTRA_PRIMARY_ICON_FROM_TITLE)) {
            itemInfo.setFlags(FLAG_PRIMARY_ICON_FROM_TITLE);
        }

        notifyItemInfoChanged(itemInfo);
        LauncherAppState appState = LauncherAppState.getInstance(mLauncher);
        MODEL_EXECUTOR.post(() -> {
            try (LauncherIcons li = LauncherIcons.obtain(getContext())) {
                Icon icon = searchTarget.getSearchAction().getIcon();
                Drawable d;
                // This bitmapInfo can be used as main icon or as a badge
                BitmapInfo bitmapInfo;
                if (icon == null) {
                    PackageItemInfo pkgInfo = new PackageItemInfo(searchTarget.getPackageName());
                    pkgInfo.user = searchTarget.getUserHandle();
                    appState.getIconCache().getTitleAndIconForApp(pkgInfo, false);
                    bitmapInfo = pkgInfo.bitmap;
                } else {
                    d = itemInfo.getIcon().loadDrawable(getContext());
                    bitmapInfo = li.createBadgedIconBitmap(d, itemInfo.user,
                            Build.VERSION.SDK_INT);
                }

                BitmapInfo bitmapMainIcon;
                if (itemInfo.hasFlags(FLAG_PRIMARY_ICON_FROM_TITLE)) {
                    bitmapMainIcon = li.createIconBitmap(
                            String.valueOf(itemInfo.title.charAt(0)),
                            bitmapInfo.color);
                } else {
                    bitmapMainIcon = bitmapInfo;
                }
                if (itemInfo.hasFlags(FLAG_BADGE_FROM_ICON)) {
                    itemInfo.bitmap = li.badgeBitmap(bitmapMainIcon.icon, bitmapInfo);
                } else {
                    itemInfo.bitmap = bitmapInfo;
                }

            }
            MAIN_EXECUTOR.post(() -> applyFromSearchActionItemInfo(itemInfo));
        });
    }

    private void prepareUsingApp(ComponentName componentName, UserHandle userHandle) {
        AllAppsStore appsStore = mLauncher.getAppsView().getAppsStore();
        AppInfo appInfo = appsStore.getApp(new ComponentKey(componentName, userHandle));

        if (appInfo == null) {
            setVisibility(GONE);
            return;
        }
        applyFromApplicationInfo(appInfo);
        notifyItemInfoChanged(appInfo);
    }


    private void prepareUsingShortcutInfo(ShortcutInfo shortcutInfo) {
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(shortcutInfo, getContext());
        notifyItemInfoChanged(workspaceItemInfo);
        LauncherAppState launcherAppState = LauncherAppState.getInstance(getContext());
        MODEL_EXECUTOR.execute(() -> {
            launcherAppState.getIconCache().getShortcutIcon(workspaceItemInfo, shortcutInfo);
            MAIN_EXECUTOR.post(() -> applyFromWorkspaceItem(workspaceItemInfo));
        });
    }

    @Override
    public boolean quickSelect() {
        this.performClick();
        notifyEvent(mLauncher, mTargetId, SearchTargetEvent.ACTION_LAUNCH_KEYBOARD_FOCUS);
        return true;
    }

    @Override
    public void onClick(View view) {
        ItemClickHandler.INSTANCE.onClick(view);
        notifyEvent(mLauncher, mTargetId, SearchTargetEvent.ACTION_LAUNCH_TOUCH);
    }

    @Override
    public boolean onLongClick(View view) {
        if (!mLongPressSupported) {
            return false;
        }
        notifyEvent(mLauncher, mTargetId, SearchTargetEvent.ACTION_LONGPRESS);
        return ItemLongClickListener.INSTANCE_ALL_APPS.onLongClick(this);
    }



    private void notifyItemInfoChanged(ItemInfoWithIcon itemInfoWithIcon) {
        if (mOnItemInfoChanged != null) {
            mOnItemInfoChanged.accept(itemInfoWithIcon);
            mOnItemInfoChanged = null;
        }
    }
}
