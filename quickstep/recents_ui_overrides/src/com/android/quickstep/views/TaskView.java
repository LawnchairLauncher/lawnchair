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

import static com.android.launcher3.QuickstepAppTransitionManagerImpl.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.PendingAnimation;
import com.android.launcher3.util.ViewPool.Reusable;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TaskIconCache;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskSystemShortcut;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.quickstep.views.RecentsView.PageCallbacks;
import com.android.quickstep.views.RecentsView.ScrollState;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.QuickStepContract;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A task in the Recents view.
 */
public class TaskView extends FrameLayout implements PageCallbacks, Reusable {

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
    public static final float EDGE_SCALE_DOWN_FACTOR = 0.03f;

    public static final long SCALE_ICON_DURATION = 120;
    private static final long DIM_ANIM_DURATION = 700;

    private static final List<Rect> SYSTEM_GESTURE_EXCLUSION_RECT =
            Collections.singletonList(new Rect());

    public static final FloatProperty<TaskView> FULLSCREEN_PROGRESS =
            new FloatProperty<TaskView>("fullscreenProgress") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setFullscreenProgress(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mFullscreenProgress;
                }
            };

    private static final FloatProperty<TaskView> FOCUS_TRANSITION =
            new FloatProperty<TaskView>("focusTransition") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setIconAndDimTransitionProgress(v, false /* invert */);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mFocusTransitionProgress;
                }
            };

    private final OnAttachStateChangeListener mTaskMenuStateListener =
            new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    if (mMenuView != null) {
                        mMenuView.removeOnAttachStateChangeListener(this);
                        mMenuView = null;
                    }
                }
            };

    private final TaskOutlineProvider mOutlineProvider;

    private Task mTask;
    private TaskThumbnailView mSnapshotView;
    private TaskMenuView mMenuView;
    private IconView mIconView;
    private final DigitalWellBeingToast mDigitalWellBeingToast;
    private float mCurveScale;
    private float mFullscreenProgress;
    private final FullscreenDrawParams mCurrentFullscreenParams;
    private final float mCornerRadius;
    private final float mWindowCornerRadius;
    private final BaseDraggingActivity mActivity;

    private ObjectAnimator mIconAndDimAnimator;
    private float mIconScaleAnimStartProgress = 0;
    private float mFocusTransitionProgress = 1;
    private float mStableAlpha = 1;

    private boolean mShowScreenshot;

    // The current background requests to load the task thumbnail and icon
    private TaskThumbnailCache.ThumbnailLoadRequest mThumbnailLoadRequest;
    private TaskIconCache.IconLoadRequest mIconLoadRequest;

    // Order in which the footers appear. Lower order appear below higher order.
    public static final int INDEX_DIGITAL_WELLBEING_TOAST = 0;
    public static final int INDEX_PROACTIVE_SUGGEST = 1;
    private final FooterWrapper[] mFooters = new FooterWrapper[2];
    private float mFooterVerticalOffset = 0;
    private float mFooterAlpha = 1;
    private int mStackHeight;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivity = BaseDraggingActivity.fromContext(context);
        setOnClickListener((view) -> {
            if (getTask() == null) {
                return;
            }
            if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
                if (isRunningTask()) {
                    createLaunchAnimationForRunningTask().start();
                } else {
                    launchTask(true /* animate */);
                }
            } else {
                launchTask(true /* animate */);
            }

            mActivity.getUserEventDispatcher().logTaskLaunchOrDismiss(
                    Touch.TAP, Direction.NONE, getRecentsView().indexOfChild(this),
                    TaskUtils.getLaunchComponentKeyForTask(getTask().key));
            mActivity.getStatsLogManager().logTaskLaunch(getRecentsView(),
                    TaskUtils.getLaunchComponentKeyForTask(getTask().key));
        });
        mCornerRadius = TaskCornerRadius.get(context);
        mWindowCornerRadius = QuickStepContract.getWindowCornerRadius(context.getResources());
        mCurrentFullscreenParams = new FullscreenDrawParams(mCornerRadius);
        mDigitalWellBeingToast = new DigitalWellBeingToast(mActivity, this);

        mOutlineProvider = new TaskOutlineProvider(getResources(), mCurrentFullscreenParams);
        setOutlineProvider(mOutlineProvider);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView = findViewById(R.id.snapshot);
        mIconView = findViewById(R.id.icon);
    }

    public TaskMenuView getMenuView() {
        return mMenuView;
    }

    public DigitalWellBeingToast getDigitalWellBeingToast() {
        return mDigitalWellBeingToast;
    }

    /**
     * Updates this task view to the given {@param task}.
     */
    public void bind(Task task) {
        cancelPendingLoadTasks();
        mTask = task;
        mSnapshotView.bind(task);
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

    public AnimatorPlaybackController createLaunchAnimationForRunningTask() {
        final PendingAnimation pendingAnimation =
                getRecentsView().createTaskLauncherAnimation(this, RECENTS_LAUNCH_DURATION);
        pendingAnimation.anim.setInterpolator(Interpolators.TOUCH_RESPONSE_INTERPOLATOR);
        AnimatorPlaybackController currentAnimation = AnimatorPlaybackController
                .wrap(pendingAnimation.anim, RECENTS_LAUNCH_DURATION, null);
        currentAnimation.setEndAction(() -> {
            pendingAnimation.finish(true, Touch.SWIPE);
            launchTask(false);
        });
        return currentAnimation;
    }

    public void launchTask(boolean animate) {
        launchTask(animate, false /* freezeTaskList */);
    }

    public void launchTask(boolean animate, boolean freezeTaskList) {
        launchTask(animate, freezeTaskList, (result) -> {
            if (!result) {
                notifyTaskLaunchFailed(TAG);
            }
        }, getHandler());
    }

    public void launchTask(boolean animate, Consumer<Boolean> resultCallback,
            Handler resultCallbackHandler) {
        launchTask(animate, false /* freezeTaskList */, resultCallback, resultCallbackHandler);
    }

    public void launchTask(boolean animate, boolean freezeTaskList, Consumer<Boolean> resultCallback,
            Handler resultCallbackHandler) {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            if (isRunningTask()) {
                getRecentsView().finishRecentsAnimation(false /* toRecents */,
                        () -> resultCallbackHandler.post(() -> resultCallback.accept(true)));
            } else {
                launchTaskInternal(animate, freezeTaskList, resultCallback, resultCallbackHandler);
            }
        } else {
            launchTaskInternal(animate, freezeTaskList, resultCallback, resultCallbackHandler);
        }
    }

    private void launchTaskInternal(boolean animate, boolean freezeTaskList,
            Consumer<Boolean> resultCallback, Handler resultCallbackHandler) {
        if (mTask != null) {
            final ActivityOptions opts;
            if (animate) {
                opts = mActivity.getActivityLaunchOptions(this);
                if (freezeTaskList) {
                    ActivityOptionsCompat.setFreezeRecentTasksList(opts);
                }
                ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(mTask.key,
                        opts, resultCallback, resultCallbackHandler);
            } else {
                opts = ActivityOptionsCompat.makeCustomAnimation(getContext(), 0, 0, () -> {
                    if (resultCallback != null) {
                        // Only post the animation start after the system has indicated that the
                        // transition has started
                        resultCallbackHandler.post(() -> resultCallback.accept(true));
                    }
                }, resultCallbackHandler);
                if (freezeTaskList) {
                    ActivityOptionsCompat.setFreezeRecentTasksList(opts);
                }
                ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(mTask.key,
                        opts, (success) -> {
                            if (resultCallback != null && !success) {
                                // If the call to start activity failed, then post the result
                                // immediately, otherwise, wait for the animation start callback
                                // from the activity options above
                                resultCallbackHandler.post(() -> resultCallback.accept(false));
                            }
                        }, resultCallbackHandler);
            }
        }
    }

    public void onTaskListVisibilityChanged(boolean visible) {
        if (mTask == null) {
            return;
        }
        cancelPendingLoadTasks();
        if (visible) {
            // These calls are no-ops if the data is already loaded, try and load the high
            // resolution thumbnail if the state permits
            RecentsModel model = RecentsModel.INSTANCE.get(getContext());
            TaskThumbnailCache thumbnailCache = model.getThumbnailCache();
            TaskIconCache iconCache = model.getIconCache();
            mThumbnailLoadRequest = thumbnailCache.updateThumbnailInBackground(
                    mTask, thumbnail -> mSnapshotView.setThumbnail(mTask, thumbnail));
            mIconLoadRequest = iconCache.updateIconInBackground(mTask,
                    (task) -> {
                        setIcon(task.icon);
                        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && isRunningTask()) {
                            getRecentsView().updateLiveTileIcon(task.icon);
                        }
                        mDigitalWellBeingToast.initialize(mTask);
                    });
        } else {
            mSnapshotView.setThumbnail(null, null);
            setIcon(null);
            // Reset the task thumbnail reference as well (it will be fetched from the cache or
            // reloaded next time we need it)
            mTask.thumbnail = null;
        }
    }

    private void cancelPendingLoadTasks() {
        if (mThumbnailLoadRequest != null) {
            mThumbnailLoadRequest.cancel();
            mThumbnailLoadRequest = null;
        }
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
    }

    private boolean showTaskMenu(int action) {
        getRecentsView().snapToPage(getRecentsView().indexOfChild(this));
        mMenuView = TaskMenuView.showForTask(this);
        UserEventDispatcher.newInstance(getContext()).logActionOnItem(action, Direction.NONE,
                LauncherLogProto.ItemType.TASK_ICON);
        if (mMenuView != null) {
            mMenuView.addOnAttachStateChangeListener(mTaskMenuStateListener);
        }
        return mMenuView != null;
    }

    private void setIcon(Drawable icon) {
        if (icon != null) {
            mIconView.setDrawable(icon);
            mIconView.setOnClickListener(v -> showTaskMenu(Touch.TAP));
            mIconView.setOnLongClickListener(v -> {
                requestDisallowInterceptTouchEvent(true);
                return showTaskMenu(Touch.LONGPRESS);
            });
        } else {
            mIconView.setDrawable(null);
            mIconView.setOnClickListener(null);
            mIconView.setOnLongClickListener(null);
        }
    }

    private void setIconAndDimTransitionProgress(float progress, boolean invert) {
        if (invert) {
            progress = 1 - progress;
        }
        mFocusTransitionProgress = progress;
        mSnapshotView.setDimAlphaMultipler(progress);
        float iconScalePercentage = (float) SCALE_ICON_DURATION / DIM_ANIM_DURATION;
        float lowerClamp = invert ? 1f - iconScalePercentage : 0;
        float upperClamp = invert ? 1 : iconScalePercentage;
        float scale = Interpolators.clampToProgress(FAST_OUT_SLOW_IN, lowerClamp, upperClamp)
                .getInterpolation(progress);
        mIconView.setScaleX(scale);
        mIconView.setScaleY(scale);

        mFooterVerticalOffset = 1.0f - scale;
        for (FooterWrapper footer : mFooters) {
            if (footer != null) {
                footer.updateFooterOffset();
            }
        }
    }

    public void setIconScaleAnimStartProgress(float startProgress) {
        mIconScaleAnimStartProgress = startProgress;
    }

    public void animateIconScaleAndDimIntoView() {
        if (mIconAndDimAnimator != null) {
            mIconAndDimAnimator.cancel();
        }
        mIconAndDimAnimator = ObjectAnimator.ofFloat(this, FOCUS_TRANSITION, 1);
        mIconAndDimAnimator.setCurrentFraction(mIconScaleAnimStartProgress);
        mIconAndDimAnimator.setDuration(DIM_ANIM_DURATION).setInterpolator(LINEAR);
        mIconAndDimAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIconAndDimAnimator = null;
            }
        });
        mIconAndDimAnimator.start();
    }

    protected void setIconScaleAndDim(float iconScale) {
        setIconScaleAndDim(iconScale, false);
    }

    private void setIconScaleAndDim(float iconScale, boolean invert) {
        if (mIconAndDimAnimator != null) {
            mIconAndDimAnimator.cancel();
        }
        setIconAndDimTransitionProgress(iconScale, invert);
    }

    private void resetViewTransforms() {
        setCurveScale(1);
        setTranslationX(0f);
        setTranslationY(0f);
        setTranslationZ(0);
        setAlpha(mStableAlpha);
        setIconScaleAndDim(1);
    }

    public void resetVisualProperties() {
        resetViewTransforms();
        setFullscreenProgress(0);
    }

    public void setStableAlpha(float parentAlpha) {
        mStableAlpha = parentAlpha;
        setAlpha(mStableAlpha);
    }

    @Override
    public void onRecycle() {
        resetViewTransforms();
        // Clear any references to the thumbnail (it will be re-read either from the cache or the
        // system on next bind)
        mSnapshotView.setThumbnail(mTask, null);
        setOverlayEnabled(false);
        onTaskListVisibilityChanged(false);
    }

    @Override
    public void onPageScroll(ScrollState scrollState) {
        float curveInterpolation =
                CURVE_INTERPOLATOR.getInterpolation(scrollState.linearInterpolation);

        mSnapshotView.setDimAlpha(curveInterpolation * MAX_PAGE_SCRIM_ALPHA);
        setCurveScale(getCurveScaleForCurveInterpolation(curveInterpolation));

        mFooterAlpha = Utilities.boundToRange(1.0f - 2 * scrollState.linearInterpolation, 0f, 1f);
        for (FooterWrapper footer : mFooters) {
            if (footer != null) {
                footer.mView.setAlpha(mFooterAlpha);
            }
        }

        if (mMenuView != null) {
            mMenuView.setPosition(getX() - getRecentsView().getScrollX(), getY());
            mMenuView.setScaleX(getScaleX());
            mMenuView.setScaleY(getScaleY());
        }
    }


    /**
     * Sets the footer at the specific index and returns the previously set footer.
     */
    public View setFooter(int index, View view) {
        View oldFooter = null;

        // If the footer are is already collapsed, do not animate entry
        boolean shouldAnimateEntry = mFooterVerticalOffset <= 0;

        if (mFooters[index] != null) {
            oldFooter = mFooters[index].mView;
            mFooters[index].release();
            removeView(oldFooter);

            // If we are replacing an existing footer, do not animate entry
            shouldAnimateEntry = false;
        }
        if (view != null) {
            int indexToAdd = getChildCount();
            for (int i = index - 1; i >= 0; i--) {
                if (mFooters[i] != null) {
                    indexToAdd = indexOfChild(mFooters[i].mView);
                    break;
                }
            }

            addView(view, indexToAdd);
            ((LayoutParams) view.getLayoutParams()).gravity =
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            view.setAlpha(mFooterAlpha);
            mFooters[index] = new FooterWrapper(view);
            if (shouldAnimateEntry) {
                mFooters[index].animateEntry();
            }
        } else {
            mFooters[index] = null;
        }

        mStackHeight = 0;
        for (FooterWrapper footer : mFooters) {
            if (footer != null) {
                footer.setVerticalShift(mStackHeight);
                mStackHeight += footer.mExpectedHeight;
            }
        }

        return oldFooter;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setPivotX((right - left) * 0.5f);
        setPivotY(mSnapshotView.getTop() + mSnapshotView.getHeight() * 0.5f);
        if (Utilities.ATLEAST_Q) {
            SYSTEM_GESTURE_EXCLUSION_RECT.get(0).set(0, 0, getWidth(), getHeight());
            setSystemGestureExclusionRects(SYSTEM_GESTURE_EXCLUSION_RECT);
        }

        mStackHeight = 0;
        for (FooterWrapper footer : mFooters) {
            if (footer != null) {
                mStackHeight += footer.mView.getHeight();
            }
        }
        for (FooterWrapper footer : mFooters) {
            if (footer != null) {
                footer.updateFooterOffset();
            }
        }
    }

    public static float getCurveScaleForInterpolation(float linearInterpolation) {
        float curveInterpolation = CURVE_INTERPOLATOR.getInterpolation(linearInterpolation);
        return getCurveScaleForCurveInterpolation(curveInterpolation);
    }

    private static float getCurveScaleForCurveInterpolation(float curveInterpolation) {
        return 1 - curveInterpolation * EDGE_SCALE_DOWN_FACTOR;
    }

    public void setCurveScale(float curveScale) {
        mCurveScale = curveScale;
        onScaleChanged();
    }

    public float getCurveScale() {
        return mCurveScale;
    }

    private void onScaleChanged() {
        float scale = mCurveScale;
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
        private FullscreenDrawParams mFullscreenParams;

        TaskOutlineProvider(Resources res, FullscreenDrawParams fullscreenParams) {
            mMarginTop = res.getDimensionPixelSize(R.dimen.task_thumbnail_top_margin);
            mFullscreenParams = fullscreenParams;
        }

        public void setFullscreenParams(FullscreenDrawParams params) {
            mFullscreenParams = params;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            RectF insets = mFullscreenParams.mCurrentDrawnInsets;
            float scale = mFullscreenParams.mScale;
            outline.setRoundRect(0,
                    (int) (mMarginTop * scale),
                    (int) ((insets.left + view.getWidth() + insets.right) * scale),
                    (int) ((insets.top + view.getHeight() + insets.bottom) * scale),
                    mFullscreenParams.mCurrentDrawnCornerRadius);
        }
    }

    private class FooterWrapper extends ViewOutlineProvider {

        final View mView;
        final ViewOutlineProvider mOldOutlineProvider;
        final ViewOutlineProvider mDelegate;

        final int mExpectedHeight;
        final int mOldPaddingBottom;

        int mAnimationOffset = 0;
        int mEntryAnimationOffset = 0;

        public FooterWrapper(View view) {
            mView = view;
            mOldOutlineProvider = view.getOutlineProvider();
            mDelegate = mOldOutlineProvider == null
                    ? ViewOutlineProvider.BACKGROUND : mOldOutlineProvider;

            int h = view.getLayoutParams().height;
            if (h > 0) {
                mExpectedHeight = h;
            } else {
                int m = MeasureSpec.makeMeasureSpec(MeasureSpec.EXACTLY - 1, MeasureSpec.AT_MOST);
                view.measure(m, m);
                mExpectedHeight = view.getMeasuredHeight();
            }
            mOldPaddingBottom = view.getPaddingBottom();

            if (mOldOutlineProvider != null) {
                view.setOutlineProvider(this);
                view.setClipToOutline(true);
            }
        }

        public void setVerticalShift(int shift) {
            mView.setPadding(mView.getPaddingLeft(), mView.getPaddingTop(),
                    mView.getPaddingRight(), mOldPaddingBottom + shift);
        }

        @Override
        public void getOutline(View view, Outline outline) {
            mDelegate.getOutline(view, outline);
            outline.offset(0, -mAnimationOffset - mEntryAnimationOffset);
        }

        void updateFooterOffset() {
            mAnimationOffset = Math.round(mStackHeight * mFooterVerticalOffset);
            mView.setTranslationY(mAnimationOffset + mEntryAnimationOffset
                    + mCurrentFullscreenParams.mCurrentDrawnInsets.bottom
                    + mCurrentFullscreenParams.mCurrentDrawnInsets.top);
            mView.invalidateOutline();
        }

        void release() {
            mView.setOutlineProvider(mOldOutlineProvider);
            setVerticalShift(0);
        }

        void animateEntry() {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.addUpdateListener(anim -> {
               float factor = 1 - anim.getAnimatedFraction();
               int totalShift = mExpectedHeight + mView.getPaddingBottom() - mOldPaddingBottom;
                mEntryAnimationOffset = Math.round(factor * totalShift);
                updateFooterOffset();
            });
            animator.setDuration(100);
            animator.start();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.addAction(
                new AccessibilityNodeInfo.AccessibilityAction(R.string.accessibility_close_task,
                        getContext().getText(R.string.accessibility_close_task)));

        final Context context = getContext();
        final List<TaskSystemShortcut> shortcuts =
                TaskOverlayFactory.INSTANCE.get(getContext()).getEnabledShortcuts(this);
        final int count = shortcuts.size();
        for (int i = 0; i < count; ++i) {
            final TaskSystemShortcut menuOption = shortcuts.get(i);
            OnClickListener onClickListener = menuOption.getOnClickListener(mActivity, this);
            if (onClickListener != null) {
                info.addAction(menuOption.createAccessibilityAction(context));
            }
        }

        if (mDigitalWellBeingToast.hasLimit()) {
            info.addAction(
                    new AccessibilityNodeInfo.AccessibilityAction(
                            R.string.accessibility_app_usage_settings,
                            getContext().getText(R.string.accessibility_app_usage_settings)));
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

        if (action == R.string.accessibility_app_usage_settings) {
            mDigitalWellBeingToast.openAppUsageSettings(this);
            return true;
        }

        final List<TaskSystemShortcut> shortcuts =
                TaskOverlayFactory.INSTANCE.get(getContext()).getEnabledShortcuts(this);
        final int count = shortcuts.size();
        for (int i = 0; i < count; ++i) {
            final TaskSystemShortcut menuOption = shortcuts.get(i);
            if (menuOption.hasHandlerForAction(action)) {
                OnClickListener onClickListener = menuOption.getOnClickListener(mActivity, this);
                if (onClickListener != null) {
                    onClickListener.onClick(this);
                }
                return true;
            }
        }

        return super.performAccessibilityAction(action, arguments);
    }

    public RecentsView getRecentsView() {
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

    /**
     * Hides the icon and shows insets when this TaskView is about to be shown fullscreen.
     * @param progress: 0 = show icon and no insets; 1 = don't show icon and show full insets.
     */
    public void setFullscreenProgress(float progress) {
        progress = Utilities.boundToRange(progress, 0, 1);
        if (progress == mFullscreenProgress) {
            return;
        }
        mFullscreenProgress = progress;
        boolean isFullscreen = mFullscreenProgress > 0;
        mIconView.setVisibility(progress < 1 ? VISIBLE : INVISIBLE);
        setClipChildren(!isFullscreen);
        setClipToPadding(!isFullscreen);

        TaskThumbnailView thumbnail = getThumbnail();
        boolean isMultiWindowMode = mActivity.getDeviceProfile().isMultiWindowMode;
        RectF insets = thumbnail.getInsetsToDrawInFullscreen(isMultiWindowMode);
        float currentInsetsLeft = insets.left * mFullscreenProgress;
        float currentInsetsRight = insets.right * mFullscreenProgress;
        mCurrentFullscreenParams.setInsets(currentInsetsLeft,
                insets.top * mFullscreenProgress,
                currentInsetsRight,
                insets.bottom * mFullscreenProgress);
        float fullscreenCornerRadius = isMultiWindowMode ? 0 : mWindowCornerRadius;
        mCurrentFullscreenParams.setCornerRadius(Utilities.mapRange(mFullscreenProgress,
                mCornerRadius, fullscreenCornerRadius) / getRecentsView().getScaleX());
        // We scaled the thumbnail to fit the content (excluding insets) within task view width.
        // Now that we are drawing left/right insets again, we need to scale down to fit them.
        if (getWidth() > 0) {
            mCurrentFullscreenParams.setScale(getWidth()
                    / (getWidth() + currentInsetsLeft + currentInsetsRight));
        }

        if (!getRecentsView().isTaskIconScaledDown(this)) {
            // Some of the items in here are dependent on the current fullscreen params, but don't
            // update them if the icon is supposed to be scaled down.
            setIconScaleAndDim(progress, true /* invert */);
        }

        thumbnail.setFullscreenParams(mCurrentFullscreenParams);
        mOutlineProvider.setFullscreenParams(mCurrentFullscreenParams);
        invalidateOutline();
    }

    public boolean isRunningTask() {
        if (getRecentsView() == null) {
            return false;
        }
        return this == getRecentsView().getRunningTaskView();
    }

    public void setShowScreenshot(boolean showScreenshot) {
        mShowScreenshot = showScreenshot;
    }

    public boolean showScreenshot() {
        if (!isRunningTask()) {
            return true;
        }
        return mShowScreenshot;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        mSnapshotView.setOverlayEnabled(overlayEnabled);
    }

    /**
     * We update and subsequently draw these in {@link #setFullscreenProgress(float)}.
     */
    static class FullscreenDrawParams {
        RectF mCurrentDrawnInsets = new RectF();
        float mCurrentDrawnCornerRadius;
        /** The current scale we apply to the thumbnail to adjust for new left/right insets. */
        float mScale = 1;

        public FullscreenDrawParams(float cornerRadius) {
            setCornerRadius(cornerRadius);
        }

        public void setInsets(float left, float top, float right, float bottom) {
            mCurrentDrawnInsets.set(left, top, right, bottom);
        }

        public void setCornerRadius(float cornerRadius) {
            mCurrentDrawnCornerRadius = cornerRadius;
        }

        public void setScale(float scale) {
            mScale = scale;
        }
    }
}
