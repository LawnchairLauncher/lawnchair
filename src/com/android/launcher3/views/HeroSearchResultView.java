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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;

import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItemWithPayload;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.touch.ItemLongClickListener;

import java.util.List;

/**
 * A view representing a high confidence app search result that includes shortcuts
 */
public class HeroSearchResultView extends LinearLayout implements DragSource,
        AllAppsSearchBarController.PayloadResultHandler<List<ItemInfoWithIcon>> {

    public static final int MAX_SHORTCUTS_COUNT = 2;
    BubbleTextView mBubbleTextView;
    View mIconView;
    BubbleTextView[] mDeepShortcutTextViews = new BubbleTextView[2];

    public HeroSearchResultView(Context context) {
        super(context);
    }

    public HeroSearchResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HeroSearchResultView(Context context, AttributeSet attrs, int defStyleAttr) {
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
        mBubbleTextView.setOnClickListener(launcher.getItemOnClickListener());
        mBubbleTextView.setOnLongClickListener(new HeroItemDragHandler(getContext(), this));
        setLayoutParams(
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, grid.allAppsCellHeightPx));


        mDeepShortcutTextViews[0] = findViewById(R.id.shortcut_0);
        mDeepShortcutTextViews[1] = findViewById(R.id.shortcut_1);
        for (BubbleTextView bubbleTextView : mDeepShortcutTextViews) {
            bubbleTextView.setLayoutParams(
                    new LinearLayout.LayoutParams(grid.allAppsIconSizePx,
                            grid.allAppsIconSizePx));
            bubbleTextView.setOnClickListener(launcher.getItemOnClickListener());
        }
    }

    /**
     * Apply {@link ItemInfo} for appIcon and shortcut Icons
     */
    @Override
    public void applyAdapterInfo(AdapterItemWithPayload<List<ItemInfoWithIcon>> adapterItem) {
        mBubbleTextView.applyFromApplicationInfo(adapterItem.appInfo);
        mIconView.setBackground(mBubbleTextView.getIcon());
        mIconView.setTag(adapterItem.appInfo);
        List<ItemInfoWithIcon> shorcutInfos = adapterItem.getPayload();
        for (int i = 0; i < mDeepShortcutTextViews.length; i++) {
            mDeepShortcutTextViews[i].setVisibility(shorcutInfos.size() > i ? VISIBLE : GONE);
            if (i < shorcutInfos.size()) {
                mDeepShortcutTextViews[i].applyFromItemInfoWithIcon(shorcutInfos.get(i));
            }
        }
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean success) {
        mBubbleTextView.setVisibility(VISIBLE);
        mBubbleTextView.setIconVisible(true);
    }

    private void setWillDrawIcon(boolean willDraw) {
        mIconView.setVisibility(willDraw ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Drag and drop handler for popup items in Launcher activity
     */
    public static class HeroItemDragHandler implements OnLongClickListener {
        private final Launcher mLauncher;
        private final HeroSearchResultView mContainer;

        HeroItemDragHandler(Context context, HeroSearchResultView container) {
            mLauncher = Launcher.getLauncher(context);
            mContainer = container;
        }

        @Override
        public boolean onLongClick(View v) {
            if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;
            mContainer.setWillDrawIcon(false);

            DraggableView draggableView = DraggableView.ofType(DraggableView.DRAGGABLE_ICON);
            WorkspaceItemInfo itemInfo = new WorkspaceItemInfo((AppInfo) v.getTag());
            itemInfo.container = CONTAINER_ALL_APPS;
            DragPreviewProvider previewProvider = new ShortcutDragPreviewProvider(
                    mContainer.mIconView, new Point());
            mLauncher.getWorkspace().beginDragShared(mContainer.mBubbleTextView,
                    draggableView, mContainer, itemInfo, previewProvider, new DragOptions());

            return false;
        }
    }
}
