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

package com.android.quickstep.views;

import android.animation.TimeInterpolator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.quickstep.RecentsAnimationInterpolator;
import com.android.quickstep.TaskSystemShortcut;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.views.RecentsView.PageCallbacks;
import com.android.quickstep.views.RecentsView.ScrollState;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskCallbacks;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.function.Consumer;

/**
 * A task in the Recents view.
 */
public class TaskView extends FrameLayout implements TaskCallbacks, PageCallbacks {

    /** A curve of x from 0 to 1, where 0 is the center of the screen and 1 is the edge. */
    private static final TimeInterpolator CURVE_INTERPOLATOR
            = x -> (float) -Math.cos(x * Math.PI) / 2f + .5f;

    /**
     * The alpha of a black scrim on a page in the carousel as it leaves the screen.
     * In the resting position of the carousel, the adjacent pages have about half this scrim.
     */
    private static final float MAX_PAGE_SCRIM_ALPHA = 0.4f;

    /**
     * How much to scale down pages near the edge of the screen.
     */
    private static final float EDGE_SCALE_DOWN_FACTOR = 0.03f;

    private static final long SCALE_ICON_DURATION = 120;

    private Task mTask;
    private TaskThumbnailView mSnapshotView;
    private ImageView mIconView;
    private float mCurveScale;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnClickListener((view) -> {
            if (mTask != null) {
                launchTask(true /* animate */);
                BaseActivity.fromContext(context).getUserEventDispatcher().logTaskLaunchOrDismiss(
                        Touch.TAP, Direction.NONE, TaskUtils.getComponentKeyForTask(mTask.key));
            }
        });
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
        setContentDescription(task.titleDescription);
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
                opts = BaseDraggingActivity.fromContext(getContext())
                        .getActivityLaunchOptions(this, false);
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
        setTranslationZ(0);
        setAlpha(1f);
    }

    @Override
    public void onPageScroll(ScrollState scrollState) {
        float curveInterpolation =
                CURVE_INTERPOLATOR.getInterpolation(scrollState.linearInterpolation);

        mSnapshotView.setDimAlpha(1 - curveInterpolation * MAX_PAGE_SCRIM_ALPHA);

        mCurveScale = 1 - curveInterpolation * EDGE_SCALE_DOWN_FACTOR;
        setScaleX(mCurveScale);
        setScaleY(mCurveScale);
    }

    public float getCurveScale() {
        return mCurveScale;
    }

    @Override
    public boolean hasOverlappingRendering() {
        // TODO: Clip-out the icon region from the thumbnail, since they are overlapping.
        return false;
    }

    public RecentsAnimationInterpolator getRecentsInterpolator() {
        Rect taskViewBounds = new Rect();
        BaseDraggingActivity activity = BaseDraggingActivity.fromContext(getContext());
        DeviceProfile dp = activity.getDeviceProfile();
        activity.getDragLayer().getDescendantRectRelativeToSelf(this, taskViewBounds);

        // TODO: Use the actual target insets instead of the current thumbnail insets in case the
        // device state has changed
        return new RecentsAnimationInterpolator(
                new Rect(0, 0, dp.widthPx, dp.heightPx),
                getThumbnail().getInsets(),
                taskViewBounds,
                new Rect(0, getThumbnail().getTop(), 0, 0),
                getScaleX(),
                getTranslationX());
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

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.addAction(
                new AccessibilityNodeInfo.AccessibilityAction(R.string.accessibility_close_task,
                        getContext().getText(R.string.accessibility_close_task)));

        final Context context = getContext();
        final BaseDraggingActivity activity = BaseDraggingActivity.fromContext(context);
        for (TaskSystemShortcut menuOption : TaskMenuView.MENU_OPTIONS) {
            OnClickListener onClickListener = menuOption.getOnClickListener(activity, this);
            if (onClickListener != null) {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(menuOption.labelResId,
                        context.getText(menuOption.labelResId)));
            }
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.string.accessibility_close_task) {
            ((RecentsView) getParent()).dismissTask(this, true /*animateTaskView*/,
                    true /*removeTask*/);
            return true;
        }

        for (TaskSystemShortcut menuOption : TaskMenuView.MENU_OPTIONS) {
            if (action == menuOption.labelResId) {
                OnClickListener onClickListener = menuOption.getOnClickListener(
                        BaseDraggingActivity.fromContext(getContext()), this);
                if (onClickListener != null) {
                    onClickListener.onClick(this);
                }
                return true;
            }
        }

        return super.performAccessibilityAction(action, arguments);
    }
}
