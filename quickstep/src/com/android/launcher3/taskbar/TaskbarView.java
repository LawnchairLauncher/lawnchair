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
package com.android.launcher3.taskbar;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends LinearLayout {

    private final ColorDrawable mBackgroundDrawable;
    private final int mItemMarginLeftRight;

    // Initialized in init().
    private int mHotseatStartIndex;
    private int mHotseatEndIndex;

    private TaskbarController.TaskbarViewCallbacks mControllerCallbacks;

    public TaskbarView(@NonNull Context context) {
        this(context, null);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        Resources resources = getResources();
        mBackgroundDrawable = (ColorDrawable) getBackground();
        mItemMarginLeftRight = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
    }

    protected void setCallbacks(TaskbarController.TaskbarViewCallbacks taskbarViewCallbacks) {
        mControllerCallbacks = taskbarViewCallbacks;
    }

    protected void init(int numHotseatIcons) {
        mHotseatStartIndex = 0;
        mHotseatEndIndex = mHotseatStartIndex + numHotseatIcons - 1;
        updateHotseatItems(new ItemInfo[numHotseatIcons]);
    }

    protected void cleanup() {
        removeAllViews();
    }

    /**
     * Sets the alpha of the background color behind all the Taskbar contents.
     * @param alpha 0 is fully transparent, 1 is fully opaque.
     */
    public void setBackgroundAlpha(float alpha) {
        mBackgroundDrawable.setAlpha((int) (alpha * 255));
    }

    /**
     * Inflates/binds the Hotseat views to show in the Taskbar given their ItemInfos.
     */
    protected void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
        for (int i = 0; i < hotseatItemInfos.length; i++) {
            ItemInfo hotseatItemInfo = hotseatItemInfos[i];
            int hotseatIndex = mHotseatStartIndex + i;
            View hotseatView = getChildAt(hotseatIndex);

            // Replace any Hotseat views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            if (hotseatItemInfo != null && hotseatItemInfo.isPredictedItem()) {
                expectedLayoutResId = R.layout.taskbar_predicted_app_icon;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }
            if (hotseatView == null || hotseatView.getSourceLayoutResId() != expectedLayoutResId) {
                removeView(hotseatView);
                BubbleTextView btv = (BubbleTextView) inflate(expectedLayoutResId);
                LayoutParams lp = new LayoutParams(btv.getIconSize(), btv.getIconSize());
                lp.setMargins(mItemMarginLeftRight, 0, mItemMarginLeftRight, 0);
                hotseatView = btv;
                addView(hotseatView, hotseatIndex, lp);
            }

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView instanceof BubbleTextView
                    && hotseatItemInfo instanceof WorkspaceItemInfo) {
                ((BubbleTextView) hotseatView).applyFromWorkspaceItem(
                        (WorkspaceItemInfo) hotseatItemInfo);
                hotseatView.setVisibility(VISIBLE);
                hotseatView.setOnClickListener(mControllerCallbacks.getItemOnClickListener());
            } else {
                hotseatView.setVisibility(GONE);
                hotseatView.setOnClickListener(null);
            }
        }
    }

    private View inflate(@LayoutRes int layoutResId) {
        return LayoutInflater.from(getContext()).inflate(layoutResId, this, false);
    }
}
