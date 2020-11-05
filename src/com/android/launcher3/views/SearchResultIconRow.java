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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchEventTracker;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A full width representation of {@link SearchResultIcon} with a secondary label and inline
 * shortcuts
 */
public class SearchResultIconRow extends LinearLayout implements
        AllAppsSearchBarController.SearchTargetHandler, View.OnClickListener,
        View.OnLongClickListener {
    public static final int MAX_SHORTCUTS_COUNT = 2;


    private final Launcher mLauncher;
    private final LauncherAppState mLauncherAppState;
    private SearchResultIcon mResultIcon;
    private TextView mTitleView;
    private TextView mDescriptionView;
    private BubbleTextView[] mShortcutViews = new BubbleTextView[2];

    private SearchTarget mSearchTarget;
    private PackageItemInfo mProviderInfo;


    public SearchResultIconRow(Context context) {
        this(context, null, 0);
    }

    public SearchResultIconRow(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultIconRow(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(getContext());
        mLauncherAppState = LauncherAppState.getInstance(getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        int iconSize = mLauncher.getDeviceProfile().allAppsIconSizePx;

        mResultIcon = findViewById(R.id.icon);
        mTitleView = findViewById(R.id.title);
        mDescriptionView = findViewById(R.id.desc);
        mShortcutViews[0] = findViewById(R.id.shortcut_0);
        mShortcutViews[1] = findViewById(R.id.shortcut_1);
        mResultIcon.getLayoutParams().height = iconSize;
        mResultIcon.getLayoutParams().width = iconSize;
        for (BubbleTextView bubbleTextView : mShortcutViews) {
            ViewGroup.LayoutParams lp = bubbleTextView.getLayoutParams();
            lp.width = iconSize;
            bubbleTextView.setOnClickListener(view -> {
                WorkspaceItemInfo itemInfo = (WorkspaceItemInfo) bubbleTextView.getTag();
                SearchTargetEvent event = new SearchTargetEvent.Builder(mSearchTarget,
                        SearchTargetEvent.CHILD_SELECT).setShortcutPosition(itemInfo.rank).build();
                SearchEventTracker.getInstance(getContext()).notifySearchTargetEvent(event);
                mLauncher.getItemOnClickListener().onClick(view);
            });
        }
        setOnClickListener(this);
        setOnLongClickListener(this);
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        mSearchTarget = searchTarget;
        mResultIcon.applySearchTarget(searchTarget);
        mResultIcon.setTextVisibility(false);
        mTitleView.setText(mResultIcon.getText());
        String itemType = searchTarget.getItemType();
        boolean showDesc = itemType.equals(SearchResultIcon.TARGET_TYPE_SHORTCUT);
        mDescriptionView.setVisibility(showDesc ? VISIBLE : GONE);

        if (itemType.equals(SearchResultIcon.TARGET_TYPE_SHORTCUT)) {
            ShortcutInfo shortcutInfo = searchTarget.getShortcutInfos().get(0);
            setProviderDetails(new ComponentName(shortcutInfo.getPackage(), ""),
                    shortcutInfo.getUserHandle());
        } else if (itemType.equals(SearchResultIcon.TARGET_TYPE_HERO_APP)) {
            showInlineShortcuts(mSearchTarget.getShortcutInfos());
        }
        if (!itemType.equals(SearchResultIcon.TARGET_TYPE_HERO_APP)) {
            showInlineShortcuts(new ArrayList<>());
        }
    }

    private void showInlineShortcuts(List<ShortcutInfo> infos) {
        if (infos == null) return;
        ArrayList<Pair<ShortcutInfo, ItemInfoWithIcon>> shortcuts = new ArrayList<>();
        for (int i = 0; infos != null && i < infos.size() && i < MAX_SHORTCUTS_COUNT; i++) {
            ShortcutInfo shortcutInfo = infos.get(i);
            ItemInfoWithIcon si = new WorkspaceItemInfo(shortcutInfo, getContext());
            si.rank = i;
            shortcuts.add(new Pair<>(shortcutInfo, si));
        }

        for (int i = 0; i < mShortcutViews.length; i++) {
            BubbleTextView shortcutView = mShortcutViews[i];
            mShortcutViews[i].setVisibility(shortcuts.size() > i ? VISIBLE : GONE);
            if (i < shortcuts.size()) {
                Pair<ShortcutInfo, ItemInfoWithIcon> p = shortcuts.get(i);
                //apply ItemInfo and prepare view
                shortcutView.applyFromWorkspaceItem((WorkspaceItemInfo) p.second);
                MODEL_EXECUTOR.execute(() -> {
                    // load unbadged shortcut in background and update view when icon ready
                    mLauncherAppState.getIconCache().getUnbadgedShortcutIcon(p.second, p.first);
                    MAIN_EXECUTOR.post(() -> shortcutView.reapplyItemInfo(p.second));
                });
            }
        }
    }


    private void setProviderDetails(ComponentName componentName, UserHandle userHandle) {
        PackageItemInfo packageItemInfo = new PackageItemInfo(componentName.getPackageName());
        if (mProviderInfo == packageItemInfo) return;
        MODEL_EXECUTOR.post(() -> {
            packageItemInfo.user = userHandle;
            mLauncherAppState.getIconCache().getTitleAndIconForApp(packageItemInfo, true);
            MAIN_EXECUTOR.post(() -> {
                mDescriptionView.setText(packageItemInfo.title);
                mProviderInfo = packageItemInfo;
            });
        });
    }

    @Override
    public void handleSelection(int eventType) {
        mResultIcon.handleSelection(eventType);
    }

    @Override
    public void onClick(View view) {
        mResultIcon.performClick();
    }

    @Override
    public boolean onLongClick(View view) {
        mResultIcon.performLongClick();
        return false;
    }
}
