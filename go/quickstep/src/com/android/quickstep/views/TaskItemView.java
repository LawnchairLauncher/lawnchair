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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.R;

/**
 * View representing an individual task item with the icon + thumbnail adjacent to the task label.
 */
public final class TaskItemView extends LinearLayout {

    private TextView mLabelView;
    private ImageView mIconView;
    private ImageView mThumbnailView;

    public TaskItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLabelView = findViewById(R.id.task_label);
        mThumbnailView = findViewById(R.id.task_thumbnail);
        mIconView = findViewById(R.id.task_icon);
    }

    /**
     * Set the label for the task item.
     *
     * @param label task label
     */
    public void setLabel(String label) {
        mLabelView.setText(label);
    }

    /**
     * Set the icon for the task item.
     *
     * @param icon task icon
     */
    public void setIcon(Drawable icon) {
        // TODO: Scale the icon up based off the padding on the side
        // The icon proper is actually smaller than the drawable and has "padding" on the side for
        // the purpose of drawing the shadow, allowing the icon to pop up, so we need to scale the
        // view if we want the icon to be flush with the bottom of the thumbnail.
        mIconView.setImageDrawable(icon);
    }

    /**
     * Set the task thumbnail for the task.
     *
     * @param thumbnail task thumbnail for the task
     */
    public void setThumbnail(Bitmap thumbnail) {
        mThumbnailView.setImageBitmap(thumbnail);
    }

    public View getThumbnailView() {
        return mThumbnailView;
    }
}
