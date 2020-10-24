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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchEventTracker;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.RemoteActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.Themes;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * A view representing a stand alone shortcut search result
 */
public class SearchResultIconRow extends DoubleShadowBubbleTextView implements
        AllAppsSearchBarController.SearchTargetHandler {


    public static final String TARGET_TYPE_REMOTE_ACTION = "remote_action";
    public static final String TARGET_TYPE_SUGGEST = "suggest";
    public static final String TARGET_TYPE_SHORTCUT = "shortcut";


    public static final String REMOTE_ACTION_SHOULD_START = "should_start_for_result";
    public static final String REMOTE_ACTION_TOKEN = "action_token";

    private final int mCustomIconResId;
    private final boolean mMatchesInset;

    private SearchTarget mSearchTarget;


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
    protected void drawFocusHighlight(Canvas canvas) {
        mHighlightPaint.setColor(mHighlightColor);
        float r = Themes.getDialogCornerRadius(getContext());
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, mHighlightPaint);
    }


    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        mSearchTarget = searchTarget;
        String type = searchTarget.getItemType();
        if (type.equals(TARGET_TYPE_REMOTE_ACTION) || type.equals(TARGET_TYPE_SUGGEST)) {
            prepareUsingRemoteAction(searchTarget.getRemoteAction(),
                    searchTarget.getExtras().getString(REMOTE_ACTION_TOKEN),
                    searchTarget.getExtras().getBoolean(REMOTE_ACTION_SHOULD_START),
                    type.equals(TARGET_TYPE_REMOTE_ACTION));

        } else if (type.equals(TARGET_TYPE_SHORTCUT)) {
            prepareUsingShortcutInfo(searchTarget.getShortcutInfos().get(0));
        }
        setOnClickListener(v -> handleSelection(SearchTargetEvent.SELECT));
        SearchEventTracker.INSTANCE.get(getContext()).registerWeakHandler(searchTarget, this);
    }

    private void prepareUsingShortcutInfo(ShortcutInfo shortcutInfo) {
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(shortcutInfo, getContext());
        applyFromWorkspaceItem(workspaceItemInfo);
        LauncherAppState launcherAppState = LauncherAppState.getInstance(getContext());
        if (!loadIconFromResource()) {
            MODEL_EXECUTOR.execute(() -> {
                launcherAppState.getIconCache().getShortcutIcon(workspaceItemInfo, shortcutInfo);
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
    public void handleSelection(int eventType) {
        ItemInfo itemInfo = (ItemInfo) getTag();
        Launcher launcher = Launcher.getLauncher(getContext());
        if (itemInfo instanceof WorkspaceItemInfo) {
            ItemClickHandler.onClickAppShortcut(this, (WorkspaceItemInfo) itemInfo, launcher);
        } else {
            ItemClickHandler.onClickRemoteAction(launcher, (RemoteActionItemInfo) itemInfo);
        }
        SearchEventTracker.INSTANCE.get(getContext()).notifySearchTargetEvent(
                new SearchTargetEvent.Builder(mSearchTarget, eventType).build());
    }
}
