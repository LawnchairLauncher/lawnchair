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
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.RemoteAction;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.RemoteActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.ComponentKey;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.function.Consumer;

/**
 * A {@link BubbleTextView} representing a single cell result in AllApps
 */
public class SearchResultIcon extends BubbleTextView implements
        SearchTargetHandler, View.OnClickListener,
        View.OnLongClickListener {


    public static final String TARGET_TYPE_APP = "app";
    public static final String TARGET_TYPE_HERO_APP = "hero_app";
    public static final String TARGET_TYPE_SHORTCUT = "shortcut";
    public static final String TARGET_TYPE_REMOTE_ACTION = "remote_action";

    public static final String REMOTE_ACTION_SHOULD_START = "should_start_for_result";
    public static final String REMOTE_ACTION_TOKEN = "action_token";


    private static final String[] LONG_PRESS_SUPPORTED_TYPES =
            new String[]{TARGET_TYPE_APP, TARGET_TYPE_SHORTCUT, TARGET_TYPE_HERO_APP};

    private final Launcher mLauncher;

    private SearchTarget mSearchTarget;
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
     * Applies search target with a ItemInfoWithIcon consumer to be called after itemInfo is
     * constructed
     */
    public void applySearchTarget(SearchTarget searchTarget, Consumer<ItemInfoWithIcon> cb) {
        mOnItemInfoChanged = cb;
        applySearchTarget(searchTarget);
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        mSearchTarget = searchTarget;
        SearchEventTracker.getInstance(getContext()).registerWeakHandler(mSearchTarget, this);
        setVisibility(VISIBLE);
        switch (searchTarget.getItemType()) {
            case TARGET_TYPE_APP:
            case TARGET_TYPE_HERO_APP:
                prepareUsingApp(searchTarget.getComponentName(), searchTarget.getUserHandle());
                break;
            case TARGET_TYPE_SHORTCUT:
                prepareUsingShortcutInfo(searchTarget.getShortcutInfos().get(0));
                break;
            case TARGET_TYPE_REMOTE_ACTION:
                prepareUsingRemoteAction(searchTarget.getRemoteAction(),
                        searchTarget.getExtras().getString(REMOTE_ACTION_TOKEN),
                        searchTarget.getExtras().getBoolean(REMOTE_ACTION_SHOULD_START),
                        searchTarget.getItemType().equals(TARGET_TYPE_REMOTE_ACTION));
                break;
        }
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

    private void prepareUsingRemoteAction(RemoteAction remoteAction, String token, boolean start,
            boolean useIconToBadge) {
        RemoteActionItemInfo itemInfo = new RemoteActionItemInfo(remoteAction, token, start);
        notifyItemInfoChanged(itemInfo);
        UI_HELPER_EXECUTOR.post(() -> {
            // If the Drawable from the remote action is not AdaptiveBitmap, styling will not
            // work.
            try (LauncherIcons li = LauncherIcons.obtain(getContext())) {
                Drawable d = itemInfo.getRemoteAction().getIcon().loadDrawable(getContext());
                BitmapInfo bitmap = li.createBadgedIconBitmap(d, itemInfo.user,
                        Build.VERSION.SDK_INT);

                if (useIconToBadge) {
                    BitmapInfo placeholder = li.createIconBitmap(
                            itemInfo.getRemoteAction().getTitle().toString().substring(0, 1),
                            bitmap.color);
                    itemInfo.bitmap = li.badgeBitmap(placeholder.icon, bitmap);
                } else {
                    itemInfo.bitmap = bitmap;
                }
            }
            MAIN_EXECUTOR.post(() -> applyFromRemoteActionInfo(itemInfo));
        });
    }

    @Override
    public void handleSelection(int eventType) {
        mLauncher.getItemOnClickListener().onClick(this);
        reportEvent(eventType);
    }

    private void reportEvent(int eventType) {
        SearchTargetEvent.Builder b = new SearchTargetEvent.Builder(mSearchTarget, eventType);
        if (mSearchTarget.getItemType().equals(TARGET_TYPE_SHORTCUT)) {
            b.setShortcutPosition(0);
        }
        SearchEventTracker.INSTANCE.get(mLauncher).notifySearchTargetEvent(b.build());

    }

    @Override
    public void onClick(View view) {
        handleSelection(SearchTargetEvent.SELECT);
    }

    @Override
    public boolean onLongClick(View view) {
        if (!supportsLongPress(mSearchTarget.getItemType())) {
            return false;
        }
        reportEvent(SearchTargetEvent.LONG_PRESS);
        return ItemLongClickListener.INSTANCE_ALL_APPS.onLongClick(view);

    }

    private boolean supportsLongPress(String type) {
        for (String t : LONG_PRESS_SUPPORTED_TYPES) {
            if (t.equals(type)) return true;
        }
        return false;
    }

    private void notifyItemInfoChanged(ItemInfoWithIcon itemInfoWithIcon) {
        if (mOnItemInfoChanged != null) {
            mOnItemInfoChanged.accept(itemInfoWithIcon);
            mOnItemInfoChanged = null;
        }
    }
}
