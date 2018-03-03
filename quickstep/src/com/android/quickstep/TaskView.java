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

import static com.android.quickstep.RecentsView.SCROLL_TYPE_TASK;
import static com.android.quickstep.RecentsView.SCROLL_TYPE_WORKSPACE;

import android.animation.TimeInterpolator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.quickstep.RecentsView.PageCallbacks;
import com.android.quickstep.RecentsView.ScrollState;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskCallbacks;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.function.Consumer;

/**
 * A task in the Recents view.
 */
public class TaskView extends FrameLayout implements TaskCallbacks, PageCallbacks {

    // TODO: can remove
    /** Designates how "curvy" the carousel is from 0 to 1, where 0 is a straight line. */
    public static final float CURVE_FACTOR = 0;

    /** A curve of x from 0 to 1, where 0 is the center of the screen and 1 is the edge. */
    public static final TimeInterpolator CURVE_INTERPOLATOR
            = x -> (float) -Math.cos(x * Math.PI) / 2f + .5f;

    /**
     * The alpha of a black scrim on a page in the carousel as it leaves the screen.
     * In the resting position of the carousel, the adjacent pages have about half this scrim.
     */
    private static final float MAX_PAGE_SCRIM_ALPHA = 0.4f;

    private static final long SCALE_ICON_DURATION = 120;

    private Task mTask;
    private TaskThumbnailView mSnapshotView;
    private ImageView mIconView;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnClickListener((view) -> launchTask(true /* animate */));
        setOutlineProvider(new TaskOutlineProvider(getResources()));
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
        mSnapshotView.bind();
        task.addCallback(this);
    }

    public Task getTask() {
        return mTask;
    }

    public TaskThumbnailView getThumbnail() {
        return mSnapshotView;
    }

    public void launchTask(boolean animate) {
        launchTask(animate, null, null);
    }

    public void launchTask(boolean animate, Consumer<Boolean> resultCallback,
            Handler resultCallbackHandler) {
        if (mTask != null) {
            final ActivityOptions opts;
            if (animate) {
                opts = Launcher.getLauncher(getContext()).getActivityLaunchOptions(this, false);
            } else {
                opts = ActivityOptions.makeCustomAnimation(getContext(), 0, 0);
            }
            ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(mTask.key,
                    opts, resultCallback, resultCallbackHandler);
        }
    }

    @Override
    public void onTaskDataLoaded(Task task, ThumbnailData thumbnailData) {
        mSnapshotView.setThumbnail(task, thumbnailData);
        mIconView.setImageDrawable(task.icon);
        mIconView.setOnClickListener(icon -> TaskMenuView.showForTask(this));
        mIconView.setOnLongClickListener(icon -> TaskMenuView.showForTask(this));
    }

    @Override
    public void onTaskDataUnloaded() {
        mSnapshotView.setThumbnail(null, null);
        mIconView.setImageDrawable(null);
        mIconView.setOnLongClickListener(null);
    }

    @Override
    public void onTaskWindowingModeChanged() {
        // Do nothing
    }

    public void animateIconToScale(float scale) {
        mIconView.animate().scaleX(scale).scaleY(scale).setDuration(SCALE_ICON_DURATION).start();
    }

    protected void setIconScale(float iconScale) {
        mIconView.animate().cancel();
        mIconView.setScaleX(iconScale);
        mIconView.setScaleY(iconScale);
    }

    public void resetVisualProperties() {
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
        setAlpha(1f);
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

        if (scrollState.lastScrollType == SCROLL_TYPE_WORKSPACE) {
            // Make sure that the task cards do not overlap with the workspace card
            float min = scrollState.halfPageWidth * (1 - scale);
            if (scrollState.isRtl) {
                setTranslationX(Math.min(translation, min) - scrollState.prevPageExtraWidth);
            } else {
                setTranslationX(Math.max(translation, -min) + scrollState.prevPageExtraWidth);
            }
        } else {
            setTranslationX(translation);
        }
        scrollState.prevPageExtraWidth = 0;
        return SCROLL_TYPE_TASK;
    }

    private static final class TaskOutlineProvider extends ViewOutlineProvider {

        private final int mMarginTop;
        private final float mRadius;

        TaskOutlineProvider(Resources res) {
            mMarginTop = res.getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);
            mRadius = res.getDimension(R.dimen.task_corner_radius);
        }

        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, mMarginTop, view.getWidth(),
                    view.getHeight(), mRadius);
        }
    }
}
