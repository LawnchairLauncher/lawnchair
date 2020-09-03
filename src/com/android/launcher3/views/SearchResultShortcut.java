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

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.systemui.plugins.shared.SearchTarget;

/**
 * A view representing a stand alone shortcut search result
 */
public class SearchResultShortcut extends FrameLayout implements
        AllAppsSearchBarController.PayloadResultHandler<SearchTarget> {

    BubbleTextView mBubbleTextView;
    View mIconView;

    public SearchResultShortcut(@NonNull Context context) {
        super(context);
    }

    public SearchResultShortcut(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchResultShortcut(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Launcher launcher = Launcher.getLauncher(getContext());
        DeviceProfile grid = launcher.getDeviceProfile();
        mIconView = findViewById(R.id.icon);
        ViewGroup.LayoutParams iconParams = mIconView.getLayoutParams();
        iconParams.height = grid.allAppsIconSizePx;
        iconParams.width = grid.allAppsIconSizePx;
        mBubbleTextView = findViewById(R.id.bubble_text);
        setOnClickListener(v -> handleSelection());
    }

    @Override
    public void applyAdapterInfo(
            AllAppsGridAdapter.AdapterItemWithPayload<SearchTarget> adapterItemWithPayload) {
        SearchTarget payload = adapterItemWithPayload.getPayload();
        ShortcutInfo si = payload.shortcuts.get(0);
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(si, getContext());
        mBubbleTextView.applyFromWorkspaceItem(workspaceItemInfo);
        mIconView.setBackground(mBubbleTextView.getIcon());
        LauncherAppState launcherAppState = LauncherAppState.getInstance(getContext());
        MODEL_EXECUTOR.execute(() -> {
            launcherAppState.getIconCache().getShortcutIcon(workspaceItemInfo, si);
            mBubbleTextView.applyFromWorkspaceItem(workspaceItemInfo);
            mIconView.setBackground(mBubbleTextView.getIcon());
        });
        adapterItemWithPayload.setSelectionHandler(this::handleSelection);
    }

    private void handleSelection() {
        ItemClickHandler.onClickAppShortcut(this, (WorkspaceItemInfo) mBubbleTextView.getTag(),
                Launcher.getLauncher(getContext()));
    }
}
