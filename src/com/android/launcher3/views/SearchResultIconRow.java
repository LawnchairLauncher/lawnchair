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
package com.android.launcher3.views;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.RemoteAction;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItemWithPayload;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.RemoteActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTarget.ItemType;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * A view representing a stand alone shortcut search result
 */
public class SearchResultIconRow extends DoubleShadowBubbleTextView implements
        AllAppsSearchBarController.PayloadResultHandler<SearchTarget> {

    private final Object[] mTargetInfo = createTargetInfo();
    private final int mCustomIconResId;
    private final boolean mMatchesInset;

    private ShortcutInfo mShortcutInfo;
    private AllAppsSearchPlugin mPlugin;
    private AdapterItemWithPayload<SearchTarget> mAdapterItem;


    public SearchResultIconRow(@NonNull Context context) {
        this(context, null, 0);
    }

    public SearchResultIconRow(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultIconRow(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SearchResultIconRow, defStyleAttr, 0);
        mCustomIconResId = a.getResourceId(R.styleable.SearchResultIconRow_customIcon, 0);
        mMatchesInset = a.getBoolean(R.styleable.SearchResultIconRow_matchTextInsetWithQuery,
                false);

        a.recycle();
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Launcher launcher = Launcher.getLauncher(getContext());
        if (mMatchesInset && launcher.getAppsView() != null && getParent() != null) {
            EditText editText = launcher.getAppsView().getSearchUiManager().getEditText();
            if (editText != null) {
                int counterOffset = getIconSize() + getCompoundDrawablePadding() / 2;
                setPadding(editText.getLeft() - counterOffset, getPaddingTop(),
                        getPaddingRight(), getPaddingBottom());
            }
        }
    }


    @Override
    public void applyAdapterInfo(AdapterItemWithPayload<SearchTarget> adapterItemWithPayload) {
        if (mAdapterItem != null) {
            mAdapterItem.setSelectionHandler(null);
        }
        mAdapterItem = adapterItemWithPayload;
        SearchTarget payload = adapterItemWithPayload.getPayload();
        mPlugin = adapterItemWithPayload.getPlugin();

        if (payload.mRemoteAction != null) {
            prepareUsingRemoteAction(payload.mRemoteAction,
                    payload.bundle.getString(SearchTarget.REMOTE_ACTION_TOKEN),
                    payload.bundle.getBoolean(SearchTarget.REMOTE_ACTION_SHOULD_START),
                    payload.type == ItemType.ACTION);
        } else {
            prepareUsingShortcutInfo(payload.shortcuts.get(0));
        }
        setOnClickListener(v -> handleSelection(SearchTargetEvent.SELECT));
        adapterItemWithPayload.setSelectionHandler(this::handleSelection);
    }

    private void prepareUsingShortcutInfo(ShortcutInfo shortcutInfo) {
        mShortcutInfo = shortcutInfo;
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(mShortcutInfo, getContext());
        applyFromWorkspaceItem(workspaceItemInfo);
        LauncherAppState launcherAppState = LauncherAppState.getInstance(getContext());
        if (!loadIconFromResource()) {
            MODEL_EXECUTOR.execute(() -> {
                launcherAppState.getIconCache().getShortcutIcon(workspaceItemInfo, mShortcutInfo);
                reapplyItemInfoAsync(workspaceItemInfo);
            });
        }
    }

    private void prepareUsingRemoteAction(RemoteAction remoteAction, String token, boolean start,
            boolean useIconToBadge) {
        RemoteActionItemInfo itemInfo = new RemoteActionItemInfo(remoteAction, token, start);

        applyFromRemoteActionInfo(itemInfo);
        if (itemInfo.isEscapeHatch() || !loadIconFromResource()) {
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
                    reapplyItemInfoAsync(itemInfo);
                }
            });
        }

    }

    private boolean loadIconFromResource() {
        if (mCustomIconResId == 0) return false;
        setIcon(Launcher.getLauncher(getContext()).getDrawable(mCustomIconResId));
        return true;
    }

    void reapplyItemInfoAsync(ItemInfoWithIcon itemInfoWithIcon) {
        MAIN_EXECUTOR.post(() -> reapplyItemInfo(itemInfoWithIcon));
    }

    @Override
    public Object[] getTargetInfo() {
        return mTargetInfo;
    }

    private void handleSelection(int eventType) {
        ItemInfo itemInfo = (ItemInfo) getTag();
        Launcher launcher = Launcher.getLauncher(getContext());
        final SearchTargetEvent searchTargetEvent;
        if (itemInfo instanceof WorkspaceItemInfo) {
            ItemClickHandler.onClickAppShortcut(this, (WorkspaceItemInfo) itemInfo, launcher);
            searchTargetEvent = getSearchTargetEvent(SearchTarget.ItemType.SHORTCUT,
                    eventType);
            searchTargetEvent.shortcut = mShortcutInfo;
        } else {
            RemoteActionItemInfo remoteItemInfo = (RemoteActionItemInfo) itemInfo;
            ItemClickHandler.onClickRemoteAction(launcher, remoteItemInfo);
            searchTargetEvent = getSearchTargetEvent(ItemType.ACTION,
                    eventType);
            searchTargetEvent.bundle = new Bundle();
            searchTargetEvent.remoteAction = remoteItemInfo.getRemoteAction();
            searchTargetEvent.bundle.putBoolean(SearchTarget.REMOTE_ACTION_SHOULD_START,
                    remoteItemInfo.shouldStartInLauncher());
            searchTargetEvent.bundle.putString(SearchTarget.REMOTE_ACTION_TOKEN,
                    remoteItemInfo.getToken());
        }
        if (mPlugin != null) {
            mPlugin.notifySearchTargetEvent(searchTargetEvent);
        }
    }
}
