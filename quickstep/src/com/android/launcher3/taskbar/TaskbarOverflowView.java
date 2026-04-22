/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * View used as overflow icon within task bar, when the list of recent/running apps overflows the
 * available display bounds - if display is not wide enough to show all running apps in the taskbar,
 * this icon is added to the taskbar as an entry point to open UI that surfaces all running apps.
 * The icon contains icon representations of up to 4 more recent tasks in overflow, stacked on top
 * each other in counter clockwise manner (icons of tasks partially overlapping with each other).
 */
public class TaskbarOverflowView extends FrameLayout implements Reorderable {
    private static final int ALPHA_TRANSPARENT = 0;
    private static final int ALPHA_OPAQUE = 255;
    private static final long ANIMATION_DURATION_APPS_TO_LEAVE_BEHIND = 300L;
    private static final long ANIMATION_DURATION_LEAVE_BEHIND_TO_APPS = 500L;
    private static final long ANIMATION_SET_DURATION = 1000L;
    private static final long ITEM_ICON_CENTER_OFFSET_ANIMATION_DURATION = 500L;
    private static final long ITEM_ICON_COLOR_FILTER_OPACITY_ANIMATION_DURATION = 600L;
    private static final long ITEM_ICON_SIZE_ANIMATION_DURATION = 500L;
    private static final long ITEM_ICON_STROKE_WIDTH_ANIMATION_DURATION = 500L;
    private static final long LEAVE_BEHIND_ANIMATIONS_DELAY = 500L;
    private static final long LEAVE_BEHIND_OPACITY_ANIMATION_DURATION = 100L;
    private static final long LEAVE_BEHIND_SIZE_ANIMATION_DURATION = 500L;
    private static final float LEAVE_BEHIND_SIZE_SCALE_DOWN_MULTIPLIER = 0.83f;
    private static final int MAX_ITEMS_IN_PREVIEW = 4;

    // The height divided by the width of the horizontal box containing two overlapping app icons.
    // According to the spec, this ratio is constant for different sizes of taskbar app icons.
    // Assuming the width of this box = taskbar app icon size - 2 paddings - 2 stroke widths, and
    // the height = width * 0.61, which is also equal to the height of a single item in the preview.
    private static final float TWO_ITEM_ICONS_BOX_ASPECT_RATIO = 0.61f;

    private static final FloatProperty<TaskbarOverflowView> ITEM_ICON_CENTER_OFFSET =
            new FloatProperty<>("itemIconCenterOffset") {
                @Override
                public Float get(TaskbarOverflowView view) {
                    return view.mItemIconCenterOffset;
                }

                @Override
                public void setValue(TaskbarOverflowView view, float value) {
                    view.mItemIconCenterOffset = value;
                    view.invalidate();
                }
            };

    private static final IntProperty<TaskbarOverflowView> ITEM_ICON_COLOR_FILTER_OPACITY =
            new IntProperty<>("itemIconColorFilterOpacity") {
                @Override
                public Integer get(TaskbarOverflowView view) {
                    return view.mItemIconColorFilterOpacity;
                }

                @Override
                public void setValue(TaskbarOverflowView view, int value) {
                    view.mItemIconColorFilterOpacity = value;
                    view.invalidate();
                }
            };

    private static final FloatProperty<TaskbarOverflowView> ITEM_ICON_SIZE =
            new FloatProperty<>("itemIconSize") {
                @Override
                public Float get(TaskbarOverflowView view) {
                    return view.mItemIconSize;
                }

                @Override
                public void setValue(TaskbarOverflowView view, float value) {
                    view.mItemIconSize = value;
                    view.invalidate();
                }
            };

    private static final FloatProperty<TaskbarOverflowView> ITEM_ICON_STROKE_WIDTH =
            new FloatProperty<>("itemIconStrokeWidth") {
                @Override
                public Float get(TaskbarOverflowView view) {
                    return view.mItemIconStrokeWidth;
                }

                @Override
                public void setValue(TaskbarOverflowView view, float value) {
                    view.mItemIconStrokeWidth = value;
                    view.invalidate();
                }
            };

    private static final IntProperty<TaskbarOverflowView> LEAVE_BEHIND_OPACITY =
            new IntProperty<>("leaveBehindOpacity") {
                @Override
                public Integer get(TaskbarOverflowView view) {
                    return view.mLeaveBehindOpacity;
                }

                @Override
                public void setValue(TaskbarOverflowView view, int value) {
                    view.mLeaveBehindOpacity = value;
                    view.invalidate();
                }
            };

    private static final FloatProperty<TaskbarOverflowView> LEAVE_BEHIND_SIZE =
            new FloatProperty<>("leaveBehindSize") {
                @Override
                public Float get(TaskbarOverflowView view) {
                    return view.mLeaveBehindSize;
                }

                @Override
                public void setValue(TaskbarOverflowView view, float value) {
                    view.mLeaveBehindSize = value;
                    view.invalidate();
                }
            };

    private boolean mIsRtlLayout;
    private final List<Task> mItems = new ArrayList<Task>();
    private int mIconSize;
    private Paint mItemBackgroundPaint;
    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private float mScaleForReorderBounce = 1f;
    private int mItemBackgroundColor;
    private int mLeaveBehindColor;

    // Active means the overflow icon has been pressed, which replaces the app icons with the
    // leave-behind circle and shows the KQS UI.
    private boolean mIsActive = false;
    private ValueAnimator mStateTransitionAnimationWrapper;

    private float mItemIconCenterOffsetDefault;
    private float mItemIconCenterOffset;  // [0..mItemIconCenterOffsetDefault]
    private int mItemIconColorFilterOpacity;  // [ALPHA_TRANSPARENT..ALPHA_OPAQUE]
    private float mItemIconSizeDefault;
    private float mItemIconSizeScaledDown;
    private float mItemIconSize;  // [mItemIconSizeScaledDown..mItemIconSizeDefault]
    private float mItemIconStrokeWidthDefault;
    private float mItemIconStrokeWidth;  // [0..mItemIconStrokeWidthDefault]
    private int mLeaveBehindOpacity;  // [ALPHA_TRANSPARENT..ALPHA_OPAQUE]
    private float mLeaveBehindSizeScaledDown;
    private float mLeaveBehindSizeDefault;
    private float mLeaveBehindSize;  // [mLeaveBehindSizeScaledDown..mLeaveBehindSizeDefault]

    public TaskbarOverflowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TaskbarOverflowView(Context context) {
        super(context);
        init();
    }

    /**
     * Inflates the taskbar overflow button view.
     * @param resId The resource to inflate the view from.
     * @param group The parent view.
     * @param iconSize The size of the overflow button icon.
     * @param padding The internal padding of the overflow view.
     * @return A taskbar overflow button.
     */
    public static TaskbarOverflowView inflateIcon(int resId, ViewGroup group, int iconSize,
            int padding) {
        LayoutInflater inflater = LayoutInflater.from(group.getContext());
        TaskbarOverflowView icon = (TaskbarOverflowView) inflater.inflate(resId, group, false);

        icon.mIconSize = iconSize;

        final float taskbarIconRadius =
                (iconSize - padding * 2f) * IconNormalizer.ICON_VISIBLE_AREA_FACTOR / 2f;

        icon.mLeaveBehindSizeDefault = taskbarIconRadius;  // 1/2 of taskbar app icon size
        icon.mLeaveBehindSizeScaledDown =
                icon.mLeaveBehindSizeDefault * LEAVE_BEHIND_SIZE_SCALE_DOWN_MULTIPLIER;
        icon.mLeaveBehindSize = icon.mLeaveBehindSizeScaledDown;

        icon.mItemIconStrokeWidthDefault =
                taskbarIconRadius / 10f;  // 1/20 of taskbar app icon size
        icon.mItemIconStrokeWidth = icon.mItemIconStrokeWidthDefault;

        icon.mItemIconSizeDefault = 2f * taskbarIconRadius * TWO_ITEM_ICONS_BOX_ASPECT_RATIO;
        icon.mItemIconSizeScaledDown = icon.mLeaveBehindSizeScaledDown;
        icon.mItemIconSize = icon.mItemIconSizeDefault;

        icon.mItemIconCenterOffsetDefault = taskbarIconRadius
                - icon.mItemIconSizeDefault * IconNormalizer.ICON_VISIBLE_AREA_FACTOR / 2f
                - icon.mItemIconStrokeWidthDefault;
        icon.mItemIconCenterOffset = icon.mItemIconCenterOffsetDefault;

        return icon;
    }

    private void init() {
        mIsRtlLayout = Utilities.isRtl(getResources());
        mItemBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mItemBackgroundColor = getContext().getColor(
                com.android.internal.R.color.materialColorInverseOnSurface);
        mLeaveBehindColor = Themes.getAttrColor(getContext(), android.R.attr.textColorTertiary);

        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        drawAppIcons(canvas);
        drawLeaveBehindCircle(canvas);
    }

    private void drawAppIcons(@NonNull Canvas canvas) {
        mItemBackgroundPaint.setColor(mItemBackgroundColor);
        float canvasCenterXY = mIconSize / 2f;
        int adjustedItemIconSize = Math.round(mItemIconSize);
        float itemIconRadius = adjustedItemIconSize / 2f;

        int itemsToShow = Math.min(mItems.size(), MAX_ITEMS_IN_PREVIEW);
        for (int i = itemsToShow - 1; i >= 0; --i) {
            Drawable icon = mItems.get(mItems.size() - i - 1).icon;
            if (icon == null) {
                continue;
            }

            float itemCenterX = getItemXOffset(mItemIconCenterOffset, mIsRtlLayout, i, itemsToShow);
            float itemCenterY = getItemYOffset(mItemIconCenterOffset, i, itemsToShow);

            Drawable iconCopy = icon.getConstantState().newDrawable().mutate();
            iconCopy.setBounds(0, 0, adjustedItemIconSize, adjustedItemIconSize);
            iconCopy.setColorFilter(new BlendModeColorFilter(
                    ColorUtils.setAlphaComponent(mLeaveBehindColor, mItemIconColorFilterOpacity),
                    BlendMode.SRC_ATOP));

            canvas.save();
            canvas.translate(
                    canvasCenterXY + itemCenterX - itemIconRadius,
                    canvasCenterXY + itemCenterY - itemIconRadius);
            canvas.drawCircle(itemIconRadius, itemIconRadius,
                    itemIconRadius * IconNormalizer.ICON_VISIBLE_AREA_FACTOR + mItemIconStrokeWidth,
                    mItemBackgroundPaint);
            iconCopy.draw(canvas);
            canvas.restore();
        }
    }

    private void drawLeaveBehindCircle(@NonNull Canvas canvas) {
        mItemBackgroundPaint.setColor(
                ColorUtils.setAlphaComponent(mLeaveBehindColor, mLeaveBehindOpacity));

        final float xyCenter = mIconSize / 2f;
        canvas.drawCircle(xyCenter, xyCenter, mLeaveBehindSize / 2f, mItemBackgroundPaint);
    }

    /**
     * Clears the list of tasks tracked by the view.
     */
    public void clearItems() {
        mItems.clear();
        invalidate();
    }

    /**
     * Update the view to represent a new list of recent tasks.
     * @param items Items to be shown in the view.
     */
    public void setItems(List<Task> items) {
        mItems.clear();
        mItems.addAll(items);
        invalidate();
    }

    @VisibleForTesting
    public List<Integer> getItemIds() {
        return mItems.stream().map(task -> task.key.id).toList();
    }

    /**
     * Called when a task is updated. If the task is contained within the view, it's cached value
     * gets updated. If the task is shown within the icon, invalidates the view, so the task icon
     * gets updated.
     * @param task The updated task.
     */
    public void updateTaskIsShown(Task task) {
        for (int i = 0; i < mItems.size(); ++i) {
            if (mItems.get(i).key.id == task.key.id) {
                mItems.set(i, task);
                if (i >= mItems.size() - MAX_ITEMS_IN_PREVIEW) {
                    invalidate();
                }
                break;
            }
        }
    }

    /**
     * @return Tooltip to be used for the taskbar overflow view - returns null if the view should
     *         not have a tooltip.
     */
    public String getTextForTooltipPopup() {
        if (mIsActive) {
            return null;
        }
        return getResources().getString(R.string.taskbar_overflow_a11y_title);
    }

    /**
     * Returns the view's state (whether it shows a set of app icons or a leave-behind circle).
     */
    public boolean getIsActive() {
        return mIsActive;
    }

    /**
     * Updates the view's state to draw either a set of app icons or a leave-behind circle.
     * @param isActive The next state of the view.
     */
    public void setIsActive(boolean isActive) {
        if (mIsActive == isActive) {
            return;
        }
        mIsActive = isActive;

        if (mStateTransitionAnimationWrapper != null
                && mStateTransitionAnimationWrapper.isRunning()) {
            mStateTransitionAnimationWrapper.reverse();
            return;
        }

        final AnimatorSet stateTransitionAnimation = getStateTransitionAnimation();
        mStateTransitionAnimationWrapper = ValueAnimator.ofFloat(0, 1f);
        mStateTransitionAnimationWrapper.setDuration(mIsActive
                ? ANIMATION_DURATION_APPS_TO_LEAVE_BEHIND
                : ANIMATION_DURATION_LEAVE_BEHIND_TO_APPS);
        mStateTransitionAnimationWrapper.setInterpolator(
                mIsActive ? Interpolators.STANDARD : Interpolators.EMPHASIZED);
        mStateTransitionAnimationWrapper.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStateTransitionAnimationWrapper = null;
            }
        });
        mStateTransitionAnimationWrapper.addUpdateListener(
                new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animator) {
                        stateTransitionAnimation.setCurrentPlayTime(
                                (long) (ANIMATION_SET_DURATION * animator.getAnimatedFraction()));
                    }
                });
        mStateTransitionAnimationWrapper.start();
    }

    private AnimatorSet getStateTransitionAnimation() {
        final AnimatorSet animation = new AnimatorSet();
        animation.setInterpolator(Interpolators.LINEAR);
        animation.playTogether(
                buildAnimator(ITEM_ICON_CENTER_OFFSET, 0f, mItemIconCenterOffsetDefault,
                        ITEM_ICON_CENTER_OFFSET_ANIMATION_DURATION, 0L,
                        ITEM_ICON_CENTER_OFFSET_ANIMATION_DURATION),
                buildAnimator(ITEM_ICON_COLOR_FILTER_OPACITY, ALPHA_OPAQUE, ALPHA_TRANSPARENT,
                        ITEM_ICON_COLOR_FILTER_OPACITY_ANIMATION_DURATION, 0L,
                        ANIMATION_SET_DURATION - ITEM_ICON_COLOR_FILTER_OPACITY_ANIMATION_DURATION),
                buildAnimator(ITEM_ICON_SIZE, mItemIconSizeScaledDown, mItemIconSizeDefault,
                        ITEM_ICON_SIZE_ANIMATION_DURATION, 0L,
                        ITEM_ICON_SIZE_ANIMATION_DURATION),
                buildAnimator(ITEM_ICON_STROKE_WIDTH, 0f, mItemIconStrokeWidthDefault,
                        ITEM_ICON_STROKE_WIDTH_ANIMATION_DURATION, 0L,
                        ITEM_ICON_STROKE_WIDTH_ANIMATION_DURATION),
                buildAnimator(LEAVE_BEHIND_OPACITY, ALPHA_OPAQUE, ALPHA_TRANSPARENT,
                        LEAVE_BEHIND_OPACITY_ANIMATION_DURATION, LEAVE_BEHIND_ANIMATIONS_DELAY,
                        ANIMATION_SET_DURATION - LEAVE_BEHIND_ANIMATIONS_DELAY
                                - LEAVE_BEHIND_OPACITY_ANIMATION_DURATION),
                buildAnimator(LEAVE_BEHIND_SIZE, mLeaveBehindSizeDefault,
                        mLeaveBehindSizeScaledDown, LEAVE_BEHIND_SIZE_ANIMATION_DURATION,
                        LEAVE_BEHIND_ANIMATIONS_DELAY, 0L)
        );
        return animation;
    }

    private ObjectAnimator buildAnimator(IntProperty<TaskbarOverflowView> property,
            int finalValueWhenAnimatingToLeaveBehind, int finalValueWhenAnimatingToAppIcons,
            long duration, long delayWhenAnimatingToLeaveBehind,
            long delayWhenAnimatingToAppIcons) {
        final ObjectAnimator animator = ObjectAnimator.ofInt(this, property,
                mIsActive ? finalValueWhenAnimatingToLeaveBehind
                        : finalValueWhenAnimatingToAppIcons);
        applyTiming(animator, duration, delayWhenAnimatingToLeaveBehind,
                delayWhenAnimatingToAppIcons);
        return animator;
    }

    private ObjectAnimator buildAnimator(FloatProperty<TaskbarOverflowView> property,
            float finalValueWhenAnimatingToLeaveBehind, float finalValueWhenAnimatingToAppIcons,
            long duration, long delayWhenAnimatingToLeaveBehind,
            long delayWhenAnimatingToAppIcons) {
        final ObjectAnimator animator = ObjectAnimator.ofFloat(this, property,
                mIsActive ? finalValueWhenAnimatingToLeaveBehind
                        : finalValueWhenAnimatingToAppIcons);
        applyTiming(animator, duration, delayWhenAnimatingToLeaveBehind,
                delayWhenAnimatingToAppIcons);
        return animator;
    }

    private void applyTiming(ObjectAnimator animator, long duration,
            long delayWhenAnimatingToLeaveBehind,
            long delayWhenAnimatingToAppIcons) {
        animator.setDuration(duration);
        animator.setStartDelay(
                mIsActive ? delayWhenAnimatingToLeaveBehind : delayWhenAnimatingToAppIcons);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    private float getItemXOffset(float baseOffset, boolean isRtl, int itemIndex, int itemCount) {
        // Item with index 1 is on the left in all cases.
        if (itemIndex == 1) {
            return (isRtl ? 1 : -1) * baseOffset;
        }

        // First item is centered if total number of items shown is 3, on the right otherwise.
        if (itemIndex == 0) {
            if (itemCount == 3) {
                return 0;
            }
            return (isRtl ? -1 : 1) * baseOffset;
        }

        // Last item is on the right when there are more than 2 items (case which is already handled
        // as `itemIndex == 1`).
        if (itemIndex == itemCount - 1) {
            return (isRtl ? -1 : 1) * baseOffset;
        }

        return (isRtl ? 1 : -1) * baseOffset;
    }

    private float getItemYOffset(float baseOffset, int itemIndex, int itemCount) {
        // If icon contains two items, they are both centered vertically.
        if (itemCount == 2) {
            return 0;
        }
        // First half of items is on top, later half is on bottom.
        return (itemIndex + 1 <= itemCount / 2 ? -1 : 1) * baseOffset;
    }
}
