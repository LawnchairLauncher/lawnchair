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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.app.search.SearchAction;
import android.app.search.SearchTarget;
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
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.SearchActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.ComponentKey;

import java.util.List;
import java.util.function.Consumer;

/**
 * A {@link BubbleTextView} representing a single cell result in AllApps
 */
public class SearchResultIcon extends BubbleTextView implements
        SearchTargetHandler, View.OnClickListener,
        View.OnLongClickListener {

    private static final String BUNDLE_EXTRA_SHOULD_START = "should_start";
    private static final String BUNDLE_EXTRA_SHOULD_START_FOR_RESULT = "should_start_for_result";

    private final Launcher mLauncher;

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
        applySearchTarget(searchTarget, inlineItems);
    }

    @Override
    public void applySearchTarget(SearchTarget parentTarget, List<SearchTarget> children) {
        switch (parentTarget.getResultType()) {
            case ResultType.APPLICATION:
                prepareUsingApp(new ComponentName(parentTarget.getPackageName(),
                        parentTarget.getExtras().getString("class")), parentTarget.getUserHandle());
                mLongPressSupported = true;
                break;
            case ResultType.SHORTCUT:
                prepareUsingShortcutInfo(parentTarget.getShortcutInfo());
                mLongPressSupported = true;
                break;
            default:
                prepareUsingSearchAction(parentTarget);
                mLongPressSupported = false;
                break;
        }
    }

    private void prepareUsingSearchAction(SearchTarget searchTarget) {
        SearchAction searchAction = searchTarget.getSearchAction();
        Bundle extras = searchAction.getExtras();
        SearchActionItemInfo itemInfo = new SearchActionItemInfo(searchAction.getIcon(),
                searchTarget.getPackageName(), searchTarget.getUserHandle(),
                searchAction.getTitle());
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


        notifyItemInfoChanged(itemInfo);
        LauncherAppState appState = LauncherAppState.getInstance(mLauncher);
        MODEL_EXECUTOR.post(() -> {
            try (LauncherIcons li = LauncherIcons.obtain(getContext())) {
                Icon icon = searchTarget.getSearchAction().getIcon();
                Drawable d;
                if (icon == null) {
                    PackageItemInfo pkgInfo = new PackageItemInfo(searchTarget.getPackageName());
                    pkgInfo.user = searchTarget.getUserHandle();
                    appState.getIconCache().getTitleAndIconForApp(pkgInfo, false);
                    itemInfo.bitmap = pkgInfo.bitmap;
                } else {
                    d = itemInfo.getIcon().loadDrawable(getContext());
                    itemInfo.bitmap = li.createBadgedIconBitmap(d, itemInfo.user,
                            Build.VERSION.SDK_INT);
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
        //TODO: event reporting
        this.performClick();
        return true;
    }

    @Override
    public void onClick(View view) {
        //TODO: event reporting
        mLauncher.getItemOnClickListener().onClick(this);
    }

    @Override
    public boolean onLongClick(View view) {
        //TODO: event reporting
        if (!mLongPressSupported) {
            return false;
        }
        return ItemLongClickListener.INSTANCE_ALL_APPS.onLongClick(this);
    }

    private void notifyItemInfoChanged(ItemInfoWithIcon itemInfoWithIcon) {
        if (mOnItemInfoChanged != null) {
            mOnItemInfoChanged.accept(itemInfoWithIcon);
            mOnItemInfoChanged = null;
        }
    }
}
