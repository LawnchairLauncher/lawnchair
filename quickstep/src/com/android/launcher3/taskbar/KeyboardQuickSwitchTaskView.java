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
package com.android.launcher3.taskbar;

import static com.android.quickstep.util.BorderAnimator.DEFAULT_BORDER_COLOR;

import android.animation.Animator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.R;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.quickstep.util.BorderAnimator;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.wm.shell.shared.TypefaceUtils;
import com.android.wm.shell.shared.TypefaceUtils.FontFamily;

import kotlin.Unit;

import java.util.function.Consumer;

/**
 * A view that displays a recent task during a keyboard quick switch.
 */
public class KeyboardQuickSwitchTaskView extends ConstraintLayout {

    private static final float THUMBNAIL_BLUR_RADIUS = 1f;
    private static final int INVALID_BORDER_RADIUS = -1;

    @ColorInt private final int mBorderColor;
    @ColorInt private final int mBorderRadius;

    @Nullable private BorderAnimator mBorderAnimator;

    @Nullable private ImageView mThumbnailView1;
    @Nullable private ImageView mThumbnailView2;
    @Nullable private ImageView mIcon1;
    @Nullable private ImageView mIcon2;
    @Nullable private View mContent;

    // Describe the task position in the parent container. Used to add information about the task's
    // position in a task list to the task view's content description.
    private int mIndexInParent = -1;
    private int mTotalTasksInParent = -1;

    public KeyboardQuickSwitchTaskView(@NonNull Context context) {
        this(context, null);
    }

    public KeyboardQuickSwitchTaskView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyboardQuickSwitchTaskView(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public KeyboardQuickSwitchTaskView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray ta = context.obtainStyledAttributes(
                attrs, R.styleable.TaskView, defStyleAttr, defStyleRes);

        setWillNotDraw(false);

        mBorderColor = ta.getColor(
                R.styleable.TaskView_focusBorderColor, DEFAULT_BORDER_COLOR);
        mBorderRadius = ta.getDimensionPixelSize(
                R.styleable.TaskView_focusBorderRadius, INVALID_BORDER_RADIUS);
        ta.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mThumbnailView1 = findViewById(R.id.thumbnail_1);
        mThumbnailView2 = findViewById(R.id.thumbnail_2);
        mIcon1 = findViewById(R.id.icon_1);
        mIcon2 = findViewById(R.id.icon_2);
        mContent = findViewById(R.id.content);

        Preconditions.assertNotNull(mContent);

        TypefaceUtils.setTypeface(
                mContent.findViewById(R.id.large_text),
                FontFamily.GSF_HEADLINE_LARGE_EMPHASIZED
        );
        TypefaceUtils.setTypeface(
                mContent.findViewById(R.id.small_text),
                FontFamily.GSF_LABEL_LARGE
        );

        Resources resources = mContext.getResources();
        mBorderAnimator = BorderAnimator.createScalingBorderAnimator(
                /* borderRadiusPx= */ mBorderRadius != INVALID_BORDER_RADIUS
                        ? mBorderRadius
                        : resources.getDimensionPixelSize(
                                R.dimen.keyboard_quick_switch_task_view_radius),
                /* borderWidthPx= */ resources.getDimensionPixelSize(
                        R.dimen.keyboard_quick_switch_border_width),
                /* borderStrokePx= */ resources.getDimensionPixelSize(
                        R.dimen.keyboard_quick_switch_border_stroke),
                /* boundsBuilder= */ bounds -> {
                    bounds.set(0, 0, getWidth(), getHeight());
                    return Unit.INSTANCE;
                },
                /* targetView= */ this,
                /* contentView= */ mContent,
                /* borderColor= */ mBorderColor);
    }

    @Nullable
    protected Animator getFocusAnimator(boolean focused) {
        return mBorderAnimator == null ? null : mBorderAnimator.buildAnimator(focused);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mBorderAnimator != null) {
            mBorderAnimator.drawBorder(canvas);
        }
    }

    protected void setThumbnails(
            @NonNull Task task1,
            @Nullable Task task2,
            @Nullable ThumbnailUpdateFunction thumbnailUpdateFunction,
            @Nullable IconUpdateFunction iconUpdateFunction) {
        applyThumbnail(mThumbnailView1, task1, thumbnailUpdateFunction);
        applyThumbnail(mThumbnailView2, task2, thumbnailUpdateFunction);

        // Update content description, even in cases task icons, and content descriptions need to be
        // loaded asynchronously to ensure that the task has non empty description (assuming task
        // position information was set), as KeyboardQuickSwitch view may request accessibility
        // focus to be moved to the task when the quick switch UI gets shown. The description will
        // be updated once the task metadata has been loaded - the delay should be very short, and
        // the content description when task titles are not available still gives some useful
        // information to the user (the task's position in the list).
        updateContentDesctiptionForTasks(task1, task2);

        if (iconUpdateFunction == null) {
            applyIcon(mIcon1, task1);
            applyIcon(mIcon2, task2);
            return;
        }

        iconUpdateFunction.updateIconInBackground(task1, t -> {
            applyIcon(mIcon1, task1);
            if (task2 != null) {
                return;
            }
            updateContentDesctiptionForTasks(task1, null);
        });

        if (task2 == null) {
            return;
        }
        iconUpdateFunction.updateIconInBackground(task2, t -> {
            applyIcon(mIcon2, task2);
            updateContentDesctiptionForTasks(task1, task2);
        });
    }

    /**
     * Initializes information about the task's position within the parent container context - used
     * to add position information to the view's content description.
     * Should be called before associating the view with tasks.
     *
     * @param index The view's 0-based index within the parent task container.
     * @param totalTasks The total number of tasks in the parent task container.
     */
    protected void setPositionInformation(int index, int totalTasks) {
        mIndexInParent = index;
        mTotalTasksInParent = totalTasks;
    }

    protected void setThumbnailsForSplitTasks(
            @NonNull Task task1,
            @Nullable Task task2,
            @Nullable ThumbnailUpdateFunction thumbnailUpdateFunction,
            @Nullable IconUpdateFunction iconUpdateFunction,
            @Nullable SplitBounds splitBounds) {
        setThumbnails(task1, task2, thumbnailUpdateFunction, iconUpdateFunction);

        if (splitBounds == null) {
            return;
        }


        final boolean isLeftRightSplit = !splitBounds.appsStackedVertically;
        final float leftOrTopTaskPercent = splitBounds.getLeftTopTaskPercent();

        ConstraintLayout.LayoutParams leftTopParams = (ConstraintLayout.LayoutParams)
                mThumbnailView1.getLayoutParams();
        ConstraintLayout.LayoutParams rightBottomParams = (ConstraintLayout.LayoutParams)
                mThumbnailView2.getLayoutParams();

        if (isLeftRightSplit) {
            // Set thumbnail view ratio in left right split mode.
            leftTopParams.width = 0; // Set width to 0dp, so it uses the constraint dimension ratio.
            leftTopParams.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
            leftTopParams.matchConstraintPercentWidth = leftOrTopTaskPercent;
            leftTopParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
            leftTopParams.rightToLeft = R.id.thumbnail_2;
            mThumbnailView1.setLayoutParams(leftTopParams);

            rightBottomParams.width = 0;
            rightBottomParams.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
            rightBottomParams.matchConstraintPercentWidth = 1 - leftOrTopTaskPercent;
            rightBottomParams.leftToRight = R.id.thumbnail_1;
            rightBottomParams.rightToRight = ConstraintLayout.LayoutParams.PARENT_ID;
            mThumbnailView2.setLayoutParams(rightBottomParams);
        } else {
            // Set thumbnail view ratio in top bottom split mode.
            leftTopParams.height = 0;
            leftTopParams.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            leftTopParams.matchConstraintPercentHeight = leftOrTopTaskPercent;
            leftTopParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            leftTopParams.bottomToTop = R.id.thumbnail_2;
            mThumbnailView1.setLayoutParams(leftTopParams);

            rightBottomParams.height = 0;
            rightBottomParams.width = ConstraintLayout.LayoutParams.MATCH_PARENT;
            rightBottomParams.matchConstraintPercentHeight = 1 - leftOrTopTaskPercent;
            rightBottomParams.topToBottom = R.id.thumbnail_1;
            rightBottomParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            mThumbnailView2.setLayoutParams(rightBottomParams);
        }
    }

    private void applyThumbnail(
            @Nullable ImageView thumbnailView,
            @Nullable Task task,
            @Nullable ThumbnailUpdateFunction updateFunction) {
        if (thumbnailView == null || task == null) {
            return;
        }
        if (updateFunction == null) {
            applyThumbnail(thumbnailView, task.colorBackground, task.thumbnail);
            return;
        }
        updateFunction.updateThumbnailInBackground(task, thumbnailData ->
                applyThumbnail(thumbnailView, task.colorBackground, thumbnailData));
    }

    private void applyThumbnail(
            @NonNull ImageView thumbnailView,
            @ColorInt int backgroundColor,
            @Nullable ThumbnailData thumbnailData) {
        Bitmap bm = thumbnailData == null ? null : thumbnailData.getThumbnail();

        if (thumbnailView.getVisibility() != VISIBLE) {
            thumbnailView.setVisibility(VISIBLE);
        }
        thumbnailView.getBackground().setTint(bm == null ? backgroundColor : Color.TRANSPARENT);
        thumbnailView.setImageDrawable(new BlurredBitmapDrawable(bm, THUMBNAIL_BLUR_RADIUS));
    }

    private void applyIcon(@Nullable ImageView iconView, @Nullable Task task) {
        if (iconView == null || task == null || task.icon == null) {
            return;
        }
        Drawable.ConstantState constantState = task.icon.getConstantState();
        if (constantState == null) {
            return;
        }
        if (iconView.getVisibility() != VISIBLE) {
            iconView.setVisibility(VISIBLE);
        }
        // Use the bitmap directly since the drawable's scale can change
        iconView.setImageDrawable(
                constantState.newDrawable(getResources(), getContext().getTheme()));
    }

    /**
     * Updates the task view's content description to reflect tasks represented by the view.
     */
    private void updateContentDesctiptionForTasks(@NonNull Task task1, @Nullable Task task2) {
        String tasksDescription = task1.titleDescription == null || task2 == null
                ? task1.titleDescription
                : getContext().getString(
                        R.string.quick_switch_split_task,
                        task1.titleDescription,
                        task2.titleDescription);
        if (mIndexInParent < 0) {
            setContentDescription(tasksDescription);
            return;
        }

        setContentDescription(
                getContext().getString(R.string.quick_switch_task_with_position_in_parent,
                        tasksDescription != null ? tasksDescription : "",
                        mIndexInParent + 1,
                        mTotalTasksInParent));
    }

    protected interface ThumbnailUpdateFunction {

        void updateThumbnailInBackground(Task task, Consumer<ThumbnailData> callback);
    }

    protected interface IconUpdateFunction {

        void updateIconInBackground(Task task, Consumer<Task> callback);
    }
}
