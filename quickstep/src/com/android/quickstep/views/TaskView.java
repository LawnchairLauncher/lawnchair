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

import static android.widget.Toast.LENGTH_SHORT;

import static com.android.quickstep.views.TaskThumbnailView.DIM_ALPHA_MULTIPLIER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Property;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
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

    private static final String TAG = TaskView.class.getSimpleName();

    /** A curve of x from 0 to 1, where 0 is the center of the screen and 1 is the edge. */
    private static final TimeInterpolator CURVE_INTERPOLATOR
            = x -> (float) -Math.cos(x * Math.PI) / 2f + .5f;

    /**
     * The alpha of a black scrim on a page in the carousel as it leaves the screen.
     * In the resting position of the carousel, the adjacent pages have about half this scrim.
     */
    public static final float MAX_PAGE_SCRIM_ALPHA = 0.4f;

    /**
     * How much to scale down pages near the edge of the screen.
     */
    private static final float EDGE_SCALE_DOWN_FACTOR = 0.03f;

    public static final long SCALE_ICON_DURATION = 120;
    private static final long DIM_ANIM_DURATION = 700;

    public static final Property<TaskView, Float> ZOOM_SCALE =
            new FloatProperty<TaskView>("zoomScale") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setZoomScale(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mZoomScale;
                }
            };

    private Task mTask;
    private TaskThumbnailView mSnapshotView;
    private IconView mIconView;
    private float mCurveScale;
    private float mZoomScale;
    private Animator mDimAlphaAnim;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnClickListener((view) -> {
            if (getTask() == null) {
                return;
            }
            launchTask(true /* animate */);
            BaseActivity.fromContext(context).getUserEventDispatcher().logTaskLaunchOrDismiss(
                    Touch.TAP, Direction.NONE, getRecentsView().indexOfChild(this),
                    TaskUtils.getLaunchComponentKeyForTask(getTask().key));
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

    public IconView getIconView() {
        return mIconView;
    }

    public void launchTask(boolean animate) {
        launchTask(animate, (result) -> {
            if (!result) {
                notifyTaskLaunchFailed(TAG);
            }
        }, getHandler());
    }

    public void launchTask(boolean animate, Consumer<Boolean> resultCallback,
            Handler resultCallbackHandler) {
        if (mTask != null) {
            final ActivityOptions opts;
            if (animate) {
                opts = BaseDraggingActivity.fromContext(getContext())
                        .getActivityLaunchOptions(this);
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
        mIconView.setDrawable(task.icon);
        mIconView.setOnClickListener(icon -> TaskMenuView.showForTask(this));
        mIconView.setOnLongClickListener(icon -> {
            requestDisallowInterceptTouchEvent(true);
            return TaskMenuView.showForTask(this);
        });
    }

    @Override
    public void onTaskDataUnloaded() {
        mSnapshotView.setThumbnail(null, null);
        mIconView.setDrawable(null);
        mIconView.setOnLongClickListener(null);
    }

    @Override
    public void onTaskWindowingModeChanged() {
        // Do nothing
    }

    public void animateIconToScaleAndDim(float scale) {
        mIconView.animate().scaleX(scale).scaleY(scale).setDuration(SCALE_ICON_DURATION).start();
        mDimAlphaAnim = ObjectAnimator.ofFloat(mSnapshotView, DIM_ALPHA_MULTIPLIER, 1 - scale,
                scale);
        mDimAlphaAnim.setDuration(DIM_ANIM_DURATION);
        mDimAlphaAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDimAlphaAnim = null;
            }
        });
        mDimAlphaAnim.start();
    }

    protected void setIconScaleAndDim(float iconScale) {
        mIconView.animate().cancel();
        mIconView.setScaleX(iconScale);
        mIconView.setScaleY(iconScale);
        if (mDimAlphaAnim != null) {
            mDimAlphaAnim.cancel();
        }
        mSnapshotView.setDimAlphaMultipler(iconScale);
    }

    public void resetVisualProperties() {
        setZoomScale(1);
        setTranslationX(0f);
        setTranslationY(0f);
        setTranslationZ(0);
        setAlpha(1f);
        setIconScaleAndDim(1);
    }

    @Override
    public void onPageScroll(ScrollState scrollState) {
        float curveInterpolation =
                CURVE_INTERPOLATOR.getInterpolation(scrollState.linearInterpolation);

        mSnapshotView.setDimAlpha(curveInterpolation * MAX_PAGE_SCRIM_ALPHA);
        setCurveScale(getCurveScaleForCurveInterpolation(curveInterpolation));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setPivotX((right - left) * 0.5f);
        setPivotY(mSnapshotView.getTop() + mSnapshotView.getHeight() * 0.5f);
    }

    public static float getCurveScaleForInterpolation(float linearInterpolation) {
        float curveInterpolation = CURVE_INTERPOLATOR.getInterpolation(linearInterpolation);
        return getCurveScaleForCurveInterpolation(curveInterpolation);
    }

    private static float getCurveScaleForCurveInterpolation(float curveInterpolation) {
        return 1 - curveInterpolation * EDGE_SCALE_DOWN_FACTOR;
    }

    private void setCurveScale(float curveScale) {
        mCurveScale = curveScale;
        onScaleChanged();
    }

    public float getCurveScale() {
        return mCurveScale;
    }

    public void setZoomScale(float adjacentScale) {
        mZoomScale = adjacentScale;
        onScaleChanged();
    }

    private void onScaleChanged() {
        float scale = mCurveScale * mZoomScale;
        setScaleX(scale);
        setScaleY(scale);
    }

    @Override
    public boolean hasOverlappingRendering() {
        // TODO: Clip-out the icon region from the thumbnail, since they are overlapping.
        return false;
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

        final RecentsView recentsView = getRecentsView();
        final AccessibilityNodeInfo.CollectionItemInfo itemInfo =
                AccessibilityNodeInfo.CollectionItemInfo.obtain(
                        0, 1, recentsView.getChildCount() - recentsView.indexOfChild(this) - 1, 1,
                        false);
        info.setCollectionItemInfo(itemInfo);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.string.accessibility_close_task) {
            getRecentsView().dismissTask(this, true /*animateTaskView*/,
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

    private RecentsView getRecentsView() {
        return (RecentsView) getParent();
    }

    public void notifyTaskLaunchFailed(String tag) {
        String msg = "Failed to launch task";
        if (mTask != null) {
            msg += " (task=" + mTask.key.baseIntent + " userId=" + mTask.key.userId + ")";
        }
        Log.w(tag, msg);
        Toast.makeText(getContext(), R.string.activity_not_available, LENGTH_SHORT).show();
    }
}
