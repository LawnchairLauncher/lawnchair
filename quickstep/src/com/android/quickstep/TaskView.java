/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.quickstep;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.SwipeDetector;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskCallbacks;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * A task in the Recents view.
 */
public class TaskView extends FrameLayout implements TaskCallbacks, SwipeDetector.Listener {

    private static final int SWIPE_DIRECTIONS = SwipeDetector.DIRECTION_POSITIVE;

    /**
     * The task will appear fully dismissed when the distance swiped
     * reaches this percentage of the card height.
     */
    private static final float SWIPE_DISTANCE_HEIGHT_PERCENTAGE = 0.38f;

    private static final long SCALE_ICON_DURATION = 120;

    private static final Property<TaskView, Float> PROPERTY_SWIPE_PROGRESS =
            new Property<TaskView, Float>(Float.class, "swipe_progress") {

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mSwipeProgress;
                }

                @Override
                public void set(TaskView taskView, Float progress) {
                    taskView.setSwipeProgress(progress);
                }
            };

    private static final Property<TaskView, Float> SCALE_ICON_PROPERTY =
            new Property<TaskView, Float>(Float.TYPE, "scale_icon") {
                @Override
                public Float get(TaskView taskView) {
                    return taskView.mIconScale;
                }

                @Override
                public void set(TaskView taskView, Float iconScale) {
                    taskView.setIconScale(iconScale);
                }
            };

    private Task mTask;
    private TaskThumbnailView mSnapshotView;
    private ImageView mIconView;
    private SwipeDetector mSwipeDetector;
    private float mSwipeDistance;
    private float mSwipeProgress;
    private Interpolator mAlphaInterpolator;
    private Interpolator mSwipeAnimInterpolator;
    private float mIconScale = 1f;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnClickListener((view) -> {
            launchTask(true /* animate */);
        });

        mSwipeDetector = new SwipeDetector(getContext(), this, SwipeDetector.VERTICAL);
        mSwipeDetector.setDetectableScrollConditions(SWIPE_DIRECTIONS, false);
        mAlphaInterpolator = Interpolators.ACCEL_1_5;
        mSwipeAnimInterpolator = Interpolators.SCROLL_CUBIC;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView = findViewById(R.id.snapshot);
        mIconView = findViewById(R.id.icon);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        View p = (View) getParent();
        mSwipeDistance = (getMeasuredHeight() - p.getPaddingTop() - p.getPaddingBottom())
                * SWIPE_DISTANCE_HEIGHT_PERCENTAGE;
    }

    /**
     * Updates this task view to the given {@param task}.
     */
    public void bind(Task task) {
        if (mTask != null) {
            mTask.removeCallback(this);
        }
        mTask = task;
        task.addCallback(this);
    }

    public Task getTask() {
        return mTask;
    }

    public TaskThumbnailView getThumbnail() {
        return mSnapshotView;
    }

    public void launchTask(boolean animate) {
        if (mTask != null) {
            final ActivityOptions opts;
            if (animate) {
                // Calculate the bounds of the thumbnail to animate from
                final Rect bounds = new Rect();
                final int[] pos = new int[2];
                mSnapshotView.getLocationInWindow(pos);
                bounds.set(pos[0], pos[1],
                        pos[0] + mSnapshotView.getWidth(),
                        pos[1] + mSnapshotView.getHeight());
                AppTransitionAnimationSpecsFuture animFuture =
                        new AppTransitionAnimationSpecsFuture(getHandler()) {
                            @Override
                            public List<AppTransitionAnimationSpecCompat> composeSpecs() {
                                ArrayList<AppTransitionAnimationSpecCompat> specs =
                                        new ArrayList<>();
                                specs.add(new AppTransitionAnimationSpecCompat(mTask.key.id, null,
                                        bounds));
                                return specs;
                            }
                        };
                opts = RecentsTransition.createAspectScaleAnimation(
                        getContext(), getHandler(), true /* scaleUp */, animFuture, null);
            } else {
                opts = ActivityOptions.makeCustomAnimation(getContext(), 0, 0);
            }
            ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(mTask.key,
                    opts, null, null);
        }
    }

    @Override
    public void onTaskDataLoaded(Task task, ThumbnailData thumbnailData) {
        mSnapshotView.setThumbnail(thumbnailData);
        mIconView.setImageDrawable(task.icon);
    }

    @Override
    public void onTaskDataUnloaded() {
        mSnapshotView.setThumbnail(null);
        mIconView.setImageDrawable(null);
    }

    @Override
    public void onTaskWindowingModeChanged() {
        // Do nothing
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        mSwipeDetector.onTouchEvent(ev);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mSwipeDetector.onTouchEvent(event);
        return mSwipeDetector.isDraggingOrSettling() || super.onTouchEvent(event);
    }

    // Swipe detector methods

    @Override
    public void onDragStart(boolean start) {
        getParent().requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        setSwipeProgress(Utilities.boundToRange(displacement / mSwipeDistance,
                allowsSwipeUp() ? -1 : 0, allowsSwipeDown() ? 1 : 0));
        return true;
    }

    /**
     * Indicates the page is being removed.
     * @param progress Ranges from -1 (fading upwards) to 1 (fading downwards).
     */
    private void setSwipeProgress(float progress) {
        mSwipeProgress = progress;
        float translationY = mSwipeProgress * mSwipeDistance;
        float alpha = 1f - mAlphaInterpolator.getInterpolation(Math.abs(mSwipeProgress));
        // Only change children to avoid changing our properties while dragging.
        mIconView.setTranslationY(translationY);
        mSnapshotView.setTranslationY(translationY);
        mIconView.setAlpha(alpha);
        mSnapshotView.setAlpha(alpha);
    }

    private boolean allowsSwipeUp() {
        return (SWIPE_DIRECTIONS & SwipeDetector.DIRECTION_POSITIVE) != 0;
    }

    private boolean allowsSwipeDown() {
        return (SWIPE_DIRECTIONS & SwipeDetector.DIRECTION_NEGATIVE) != 0;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        boolean movingAwayFromCenter = velocity < 0 == mSwipeProgress < 0;
        boolean flingAway = fling && movingAwayFromCenter
                && (allowsSwipeUp() && velocity < 0 || allowsSwipeDown() && velocity > 0);
        final boolean shouldRemove = flingAway || (!fling && Math.abs(mSwipeProgress) > 0.5f);
        float fromProgress = mSwipeProgress;
        float toProgress = !shouldRemove ? 0f : mSwipeProgress < 0 ? -1f : 1f;
        ValueAnimator swipeAnimator = ObjectAnimator.ofFloat(this, PROPERTY_SWIPE_PROGRESS,
                fromProgress, toProgress);
        swipeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (shouldRemove) {
                    ((RecentsView) getParent()).onTaskDismissed(TaskView.this);
                }
                mSwipeDetector.finishedScrolling();
            }
        });
        swipeAnimator.setDuration(SwipeDetector.calculateDuration(velocity,
                Math.abs(toProgress - fromProgress)));
        swipeAnimator.setInterpolator(mSwipeAnimInterpolator);
        swipeAnimator.start();
    }

    public void animateIconToScale(float scale) {
        ObjectAnimator.ofFloat(this, SCALE_ICON_PROPERTY, scale)
                .setDuration(SCALE_ICON_DURATION).start();
    }

    protected void setIconScale(float iconScale) {
        mIconScale = iconScale;
        if (mIconView != null) {
            mIconView.setScaleX(mIconScale);
            mIconView.setScaleY(mIconScale);
        }
    }
}
