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

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Property;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.R;
import com.android.quickstep.RecentsView.PageCallbacks;
import com.android.quickstep.RecentsView.ScrollState;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskCallbacks;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.ArrayList;
import java.util.List;

import static com.android.quickstep.RecentsView.SCROLL_TYPE_TASK;
import static com.android.quickstep.RecentsView.SCROLL_TYPE_WORKSPACE;

/**
 * A task in the Recents view.
 */
public class TaskView extends FrameLayout implements TaskCallbacks, PageCallbacks {

    /** Designates how "curvy" the carousel is from 0 to 1, where 0 is a straight line. */
    private static final float CURVE_FACTOR = 0.25f;
    /** A circular curve of x from 0 to 1, where 0 is the center of the screen and 1 is the edge. */
    private static final TimeInterpolator CURVE_INTERPOLATOR
            = x -> (float) (1 - Math.sqrt(1 - Math.pow(x, 2)));

    /**
     * The alpha of a black scrim on a page in the carousel as it leaves the screen.
     * In the resting position of the carousel, the adjacent pages have about half this scrim.
     */
    private static final float MAX_PAGE_SCRIM_ALPHA = 0.8f;

    private static final long SCALE_ICON_DURATION = 120;

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
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView = findViewById(R.id.snapshot);
        mIconView = findViewById(R.id.icon);
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
        mIconView.setOnLongClickListener(icon -> TaskMenuView.showForTask(this));
    }

    @Override
    public void onTaskDataUnloaded() {
        mSnapshotView.setThumbnail(null);
        mIconView.setImageDrawable(null);
        mIconView.setOnLongClickListener(null);
    }

    @Override
    public void onTaskWindowingModeChanged() {
        // Do nothing
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

    @Override
    public int onPageScroll(ScrollState scrollState) {
        float curveInterpolation =
                CURVE_INTERPOLATOR.getInterpolation(scrollState.linearInterpolation);
        float scale = 1 - curveInterpolation * CURVE_FACTOR;
        setScaleX(scale);
        setScaleY(scale);

        // Make sure the biggest card (i.e. the one in front) shows on top of the adjacent ones.
        setTranslationZ(scale);

        mSnapshotView.setDimAlpha(1 - curveInterpolation * MAX_PAGE_SCRIM_ALPHA);

        float translation =
                scrollState.distanceFromScreenCenter * curveInterpolation * CURVE_FACTOR;
        setTranslationX(translation);

        if (scrollState.lastScrollType == SCROLL_TYPE_WORKSPACE) {
            // Make sure that the task cards do not overlap with the workspace card
            float min = scrollState.halfPageWidth * (1 - scale);
            if (scrollState.isRtl) {
                setTranslationX(Math.min(translation, min));
            } else {
                setTranslationX(Math.max(translation, -min));
            }
        } else {
            setTranslationX(translation);
        }
        return SCROLL_TYPE_TASK;
    }
}
