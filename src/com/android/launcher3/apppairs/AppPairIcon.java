/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.apppairs;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.Collections;
import java.util.Comparator;

/**
 * A {@link android.widget.FrameLayout} used to represent an app pair icon on the workspace.
 */
public class AppPairIcon extends FrameLayout implements DraggableView {

    private ActivityContext mActivity;
    private BubbleTextView mAppPairName;
    private FolderInfo mInfo;

    public AppPairIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppPairIcon(Context context) {
        super(context);
    }

    /**
     * Builds an AppPairIcon to be added to the Launcher
     */
    public static AppPairIcon inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group, FolderInfo appPairInfo) {

        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        AppPairIcon icon = (AppPairIcon) inflater.inflate(resId, group, false);

        // Sort contents, so that left-hand app comes first
        Collections.sort(appPairInfo.contents, Comparator.comparingInt(a -> a.rank));

        icon.setClipToPadding(false);
        icon.mAppPairName = icon.findViewById(R.id.app_pair_icon_name);

        // TODO (jeremysim b/274189428): Replace this placeholder icon
        WorkspaceItemInfo placeholder = new WorkspaceItemInfo();
        placeholder.newIcon(icon.getContext());
        icon.mAppPairName.applyFromWorkspaceItem(placeholder);

        icon.mAppPairName.setText(appPairInfo.title);

        icon.setTag(appPairInfo);
        icon.setOnClickListener(activity.getItemOnClickListener());
        icon.mInfo = appPairInfo;
        icon.mActivity = activity;

        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        return icon;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        mAppPairName.getIconBounds(bounds);
    }

    public FolderInfo getInfo() {
        return mInfo;
    }
}
