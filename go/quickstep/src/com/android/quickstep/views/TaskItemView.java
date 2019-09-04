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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.quickstep.ThumbnailDrawable;
import com.android.systemui.shared.recents.model.ThumbnailData;

/**
 * View representing an individual task item with the icon + thumbnail adjacent to the task label.
 */
public final class TaskItemView extends LinearLayout {

    private static final String EMPTY_LABEL = "";
    private static final String DEFAULT_LABEL = "...";
    private final Drawable mDefaultIcon;
    private final Drawable mDefaultThumbnail;
    private final TaskLayerDrawable mIconDrawable;
    private final TaskLayerDrawable mThumbnailDrawable;
    private View mTaskIconThumbnailView;
    private TextView mLabelView;
    private ImageView mIconView;
    private ImageView mThumbnailView;
    private float mContentTransitionProgress;
    private int mDisplayedOrientation;

    /**
     * Property representing the content transition progress of the view. 1.0f represents that the
     * currently bound icon, thumbnail, and label are fully animated in and visible.
     */
    public static FloatProperty CONTENT_TRANSITION_PROGRESS =
            new FloatProperty<TaskItemView>("taskContentTransitionProgress") {
                @Override
                public void setValue(TaskItemView view, float progress) {
                    view.setContentTransitionProgress(progress);
                }

                @Override
                public Float get(TaskItemView view) {
                    return view.mContentTransitionProgress;
                }
            };

    public TaskItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources res = context.getResources();
        mDefaultIcon = res.getDrawable(android.R.drawable.sym_def_app_icon, context.getTheme());
        mDefaultThumbnail = res.getDrawable(R.drawable.default_thumbnail, context.getTheme());
        mIconDrawable = new TaskLayerDrawable(context);
        mThumbnailDrawable = new TaskLayerDrawable(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLabelView = findViewById(R.id.task_label);
        mTaskIconThumbnailView = findViewById(R.id.task_icon_and_thumbnail);
        mThumbnailView = findViewById(R.id.task_thumbnail);
        mIconView = findViewById(R.id.task_icon);

        mThumbnailView.setImageDrawable(mThumbnailDrawable);
        mIconView.setImageDrawable(mIconDrawable);

        resetToEmptyUi();
        CONTENT_TRANSITION_PROGRESS.setValue(this, 1.0f);
    }

    /**
     * Resets task item view to empty, loading UI.
     */
    public void resetToEmptyUi() {
        mIconDrawable.resetDrawable();
        mThumbnailDrawable.resetDrawable();
        setLabel(EMPTY_LABEL);
    }

    /**
     * Set the label for the task item. Sets to a default label if null.
     *
     * @param label task label
     */
    public void setLabel(@Nullable String label) {
        mLabelView.setText(getSafeLabel(label));
        // TODO: Animation for label
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
        mIconDrawable.setCurrentDrawable(getSafeIcon(icon));
    }

    /**
     * Set the task thumbnail for the task. Sets to a default thumbnail if null.
     *
     * @param thumbnailData task thumbnail data for the task
     */
    public void setThumbnail(@Nullable ThumbnailData thumbnailData) {
        mThumbnailDrawable.setCurrentDrawable(getSafeThumbnail(thumbnailData));
    }

    public View getThumbnailView() {
        return mThumbnailView;
    }

    public View getIconView() {
        return mIconView;
    }

    public View getLabelView() {
        return mLabelView;
    }

    /**
     * Start a new animation from the current task content to the specified new content. The caller
     * is responsible for the actual animation control via the property
     * {@link #CONTENT_TRANSITION_PROGRESS}.
     *
     * @param endIcon the icon to animate to
     * @param endThumbnail the thumbnail to animate to
     * @param endLabel the label to animate to
     */
    public void startContentAnimation(@Nullable Drawable endIcon,
            @Nullable ThumbnailData endThumbnail, @Nullable String endLabel) {
        mIconDrawable.startNewTransition(getSafeIcon(endIcon));
        mThumbnailDrawable.startNewTransition(getSafeThumbnail(endThumbnail));
        // TODO: Animation for label

        setContentTransitionProgress(0.0f);
    }

    private void setContentTransitionProgress(float progress) {
        mContentTransitionProgress = progress;
        mIconDrawable.setTransitionProgress(progress);
        mThumbnailDrawable.setTransitionProgress(progress);
        // TODO: Animation for label
    }

    private @NonNull Drawable getSafeIcon(@Nullable Drawable icon) {
        return (icon != null) ? icon : mDefaultIcon;
    }

    private @NonNull Drawable getSafeThumbnail(@Nullable ThumbnailData thumbnailData) {
        if (thumbnailData == null || thumbnailData.thumbnail == null) {
            return mDefaultThumbnail;
        }
        int orientation = getResources().getConfiguration().orientation;
        return new ThumbnailDrawable(getResources(), thumbnailData,
                orientation /* requestedOrientation */);
    }

    private @NonNull String getSafeLabel(@Nullable String label) {
        return (label != null) ? label : DEFAULT_LABEL;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        onOrientationChanged(getResources().getConfiguration().orientation);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onOrientationChanged(newConfig.orientation);
    }

    private void onOrientationChanged(int newOrientation) {
        if (mDisplayedOrientation == newOrientation) {
            return;
        }
        mDisplayedOrientation = newOrientation;
        int layerCount = mThumbnailDrawable.getNumberOfLayers();
        for (int i = 0; i < layerCount; i++) {
            Drawable drawable = mThumbnailDrawable.getDrawable(i);
            if (drawable instanceof ThumbnailDrawable) {
                ((ThumbnailDrawable) drawable).setRequestedOrientation(newOrientation);
            }
        }
        mTaskIconThumbnailView.forceLayout();
    }
}
