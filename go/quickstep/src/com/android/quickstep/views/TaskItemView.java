/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.R;

/**
 * View representing an individual task item with the icon + thumbnail adjacent to the task label.
 */
public final class TaskItemView extends LinearLayout {

    private static final String DEFAULT_LABEL = "...";
    private final Drawable mDefaultIcon;
    private TextView mLabelView;
    private ImageView mIconView;
    private ImageView mThumbnailView;

    public TaskItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDefaultIcon = context.getResources().getDrawable(
                android.R.drawable.sym_def_app_icon, context.getTheme());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLabelView = findViewById(R.id.task_label);
        mThumbnailView = findViewById(R.id.task_thumbnail);
        mIconView = findViewById(R.id.task_icon);
    }

    /**
     * Resets task item view to default values.
     */
    public void resetTaskItemView() {
        setLabel(DEFAULT_LABEL);
        setIcon(null);
        setThumbnail(null);
    }

    /**
     * Set the label for the task item. Sets to a default label if null.
     *
     * @param label task label
     */
    public void setLabel(@Nullable String label) {
        if (label == null) {
            mLabelView.setText(DEFAULT_LABEL);
            return;
        }
        mLabelView.setText(label);
    }

    /**
     * Set the icon for the task item. Sets to a default icon if null.
     *
     * @param icon task icon
     */
    public void setIcon(@Nullable Drawable icon) {
        // TODO: Scale the icon up based off the padding on the side
        // The icon proper is actually smaller than the drawable and has "padding" on the side for
        // the purpose of drawing the shadow, allowing the icon to pop up, so we need to scale the
        // view if we want the icon to be flush with the bottom of the thumbnail.
        if (icon == null) {
            mIconView.setImageDrawable(mDefaultIcon);
            return;
        }
        mIconView.setImageDrawable(icon);
    }

    /**
     * Set the task thumbnail for the task. Sets to a default thumbnail if null.
     *
     * @param thumbnail task thumbnail for the task
     */
    public void setThumbnail(@Nullable Bitmap thumbnail) {
        if (thumbnail == null) {
            mThumbnailView.setImageBitmap(null);
            mThumbnailView.setBackgroundColor(Color.GRAY);
            return;
        }
        mThumbnailView.setBackgroundColor(Color.TRANSPARENT);
        mThumbnailView.setImageBitmap(thumbnail);
    }

    public View getThumbnailView() {
        return mThumbnailView;
    }
}
