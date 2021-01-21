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

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.END;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.widget.Toast.LENGTH_SHORT;

import static com.android.launcher3.QuickstepAppTransitionManagerImpl.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.Utilities.comp;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.android.launcher3.util.ViewPool.Reusable;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TaskIconCache;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.quickstep.views.RecentsView.PageCallbacks;
import com.android.quickstep.views.RecentsView.ScrollState;
import com.android.quickstep.views.TaskThumbnailView.PreviewPositionHelper;
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
    /**
     * This technically can be a vanilla {@link TouchDelegate} class, however that class requires
     * setting the touch bounds at construction, so we'd repeatedly be created many instances
     * unnecessarily as scrolling occurs, whereas {@link TransformingTouchDelegate} allows touch
     * delegated bounds only to be updated.
     */
    private TransformingTouchDelegate mIconTouchDelegate;
    private TransformingTouchDelegate mChipTouchDelegate;

    private static final List<Rect> SYSTEM_GESTURE_EXCLUSION_RECT =
            Collections.singletonList(new Rect());

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

    private static final FloatProperty<TaskView> FILL_DISMISS_GAP_TRANSLATION_X =
            new FloatProperty<TaskView>("fillDismissGapTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setFillDismissGapTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mFillDismissGapTranslationX;
                }
            };

    private static final FloatProperty<TaskView> FILL_DISMISS_GAP_TRANSLATION_Y =
            new FloatProperty<TaskView>("fillDismissGapTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setFillDismissGapTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mFillDismissGapTranslationY;
                }
            };

    private static final FloatProperty<TaskView> TASK_OFFSET_TRANSLATION_X =
            new FloatProperty<TaskView>("taskOffsetTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskOffsetTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskOffsetTranslationX;
                }
            };

    private static final FloatProperty<TaskView> TASK_OFFSET_TRANSLATION_Y =
            new FloatProperty<TaskView>("taskOffsetTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskOffsetTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskOffsetTranslationY;
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
    private final BaseDraggingActivity mActivity;

    // Various causes of changing primary translation, which we aggregate to setTranslationX/Y().
    // TODO: We should do this for secondary translation properties as well.
    private float mFillDismissGapTranslationX;
    private float mFillDismissGapTranslationY;
    private float mTaskOffsetTranslationX;
    private float mTaskOffsetTranslationY;

    private ObjectAnimator mIconAndDimAnimator;
    private float mIconScaleAnimStartProgress = 0;
    private float mFocusTransitionProgress = 1;
    private float mModalness = 0;
    private float mStableAlpha = 1;

    private boolean mShowScreenshot;

    // The current background requests to load the task thumbnail and icon
    private TaskThumbnailCache.ThumbnailLoadRequest mThumbnailLoadRequest;
    private TaskIconCache.IconLoadRequest mIconLoadRequest;

    // Order in which the footers appear. Lower order appear below higher order.
    public static final int INDEX_DIGITAL_WELLBEING_TOAST = 0;
    private final FooterWrapper[] mFooters = new FooterWrapper[2];
    private float mFooterVerticalOffset = 0;
    private float mFooterAlpha = 1;
    private int mStackHeight;
    private View mContextualChipWrapper;
    private View mContextualChip;
    private final float[] mIconCenterCoords = new float[2];
    private final float[] mChipCenterCoords = new float[2];

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
            mActivity.getStatsLogManager().logger().withItemInfo(getItemInfo())
                    .log(LAUNCHER_TASK_LAUNCH_TAP);
        });

        mCurrentFullscreenParams = new FullscreenDrawParams(context);
        mDigitalWellBeingToast = new DigitalWellBeingToast(mActivity, this);

        mOutlineProvider = new TaskOutlineProvider(getContext(), mCurrentFullscreenParams);
        setOutlineProvider(mOutlineProvider);
    }

    /**
     * Builds proto for logging
     */
    public WorkspaceItemInfo getItemInfo() {
        ComponentKey componentKey = TaskUtils.getLaunchComponentKeyForTask(getTask().key);
        WorkspaceItemInfo dummyInfo = new WorkspaceItemInfo();
        dummyInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_TASK;
        dummyInfo.container = LauncherSettings.Favorites.CONTAINER_TASKSWITCHER;
        dummyInfo.user = componentKey.user;
        dummyInfo.intent = new Intent().setComponent(componentKey.componentName);
        dummyInfo.title = TaskUtils.getTitle(getContext(), getTask());
        dummyInfo.screenId = getRecentsView().indexOfChild(this);
        return dummyInfo;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView = findViewById(R.id.snapshot);
        mIconView = findViewById(R.id.icon);
        mIconTouchDelegate = new TransformingTouchDelegate(mIconView);
    }

    /**
     * Whether the taskview should take the touch event from parent. Events passed to children
     * that might require special handling.
     */
    public boolean offerTouchToChildren(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            computeAndSetIconTouchDelegate();
            computeAndSetChipTouchDelegate();
        }
        if (mIconTouchDelegate != null && mIconTouchDelegate.onTouchEvent(event)) {
            return true;
        }
        if (mChipTouchDelegate != null && mChipTouchDelegate.onTouchEvent(event)) {
            return true;
        }
        return false;
    }

    private void computeAndSetIconTouchDelegate() {
        float iconHalfSize = mIconView.getWidth() / 2f;
        mIconCenterCoords[0] = mIconCenterCoords[1] = iconHalfSize;
        getDescendantCoordRelativeToAncestor(mIconView, mActivity.getDragLayer(), mIconCenterCoords,
                false);
        mIconTouchDelegate.setBounds(
                (int) (mIconCenterCoords[0] - iconHalfSize),
                (int) (mIconCenterCoords[1] - iconHalfSize),
                (int) (mIconCenterCoords[0] + iconHalfSize),
                (int) (mIconCenterCoords[1] + iconHalfSize));
    }

    private void computeAndSetChipTouchDelegate() {
        if (mContextualChipWrapper != null) {
            float chipHalfWidth = mContextualChipWrapper.getWidth() / 2f;
            float chipHalfHeight = mContextualChipWrapper.getHeight() / 2f;
            mChipCenterCoords[0] = chipHalfWidth;
            mChipCenterCoords[1] = chipHalfHeight;
            getDescendantCoordRelativeToAncestor(mContextualChipWrapper, mActivity.getDragLayer(),
                    mChipCenterCoords,
                    false);
            mChipTouchDelegate.setBounds(
                    (int) (mChipCenterCoords[0] - chipHalfWidth),
                    (int) (mChipCenterCoords[1] - chipHalfHeight),
                    (int) (mChipCenterCoords[0] + chipHalfWidth),
                    (int) (mChipCenterCoords[1] + chipHalfHeight));
        }
    }

    /**
     * The modalness of this view is how it should be displayed when it is shown on its own in the
     * modal state of overview.
     *
     * @param modalness [0, 1] 0 being in context with other tasks, 1 being shown on its own.
     */
    public void setModalness(float modalness) {
        if (mModalness == modalness) {
            return;
        }
        mModalness = modalness;
        mIconView.setAlpha(comp(modalness));
        if (mContextualChip != null) {
            mContextualChip.setScaleX(comp(modalness));
            mContextualChip.setScaleY(comp(modalness));
        }
        if (mContextualChipWrapper != null) {
            mContextualChipWrapper.setAlpha(comp(modalness));
        }
        updateFooterVerticalOffset(mFooterVerticalOffset);
    }

    public TaskMenuView getMenuView() {
        return mMenuView;
    }

    public DigitalWellBeingToast getDigitalWellBeingToast() {
        return mDigitalWellBeingToast;
    }

    /**
     * Updates this task view to the given {@param task}.
     *
     * TODO(b/142282126) Re-evaluate if we need to pass in isMultiWindowMode after
     *   that issue is fixed
     */
    public void bind(Task task, RecentsOrientedState orientedState) {
        cancelPendingLoadTasks();
        mTask = task;
        mSnapshotView.bind(task);
        setOrientationState(orientedState);
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
        final PendingAnimation pendingAnimation = getRecentsView().createTaskLaunchAnimation(
                this, RECENTS_LAUNCH_DURATION, TOUCH_RESPONSE_INTERPOLATOR);
        AnimatorPlaybackController currentAnimation = pendingAnimation.createPlaybackController();
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
            RecentsView recentsView = getRecentsView();
            if (isRunningTask()) {
                recentsView.finishRecentsAnimation(false /* toRecents */,
                        () -> resultCallbackHandler.post(() -> resultCallback.accept(true)));
            } else {
                // This is a workaround against the WM issue that app open is not correctly animated
                // when recents animation is being cleaned up (b/143774568). When that's possible,
                // we should rely on the framework side to cancel the recents animation, and we will
                // clean up the screenshot on the launcher side while we launch the next task.
                recentsView.switchToScreenshot(null,
                        () -> recentsView.finishRecentsAnimation(true /* toRecents */,
                                () -> launchTaskInternal(animate, freezeTaskList, resultCallback,
                                        resultCallbackHandler)));
            }
        } else {
            launchTaskInternal(animate, freezeTaskList, resultCallback, resultCallbackHandler);
        }
    }

    private void launchTaskInternal(boolean animate, boolean freezeTaskList,
            Consumer<Boolean> resultCallback, Handler resultCallbackHandler) {
        if (mTask != null) {
            final ActivityOptions opts;
            TestLogging.recordEvent(
                    TestProtocol.SEQUENCE_MAIN, "startActivityFromRecentsAsync", mTask);
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
            getRecentsView().onTaskLaunched(mTask);
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
        if (!getRecentsView().isClearAllHidden()) {
            getRecentsView().snapToPage(getRecentsView().indexOfChild(this));
        } else {
            mMenuView = TaskMenuView.showForTask(this);
            mActivity.getStatsLogManager().logger().withItemInfo(getItemInfo())
                    .log(LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS);
            UserEventDispatcher.newInstance(getContext()).logActionOnItem(action, Direction.NONE,
                    LauncherLogProto.ItemType.TASK_ICON);
            if (mMenuView != null) {
                mMenuView.addOnAttachStateChangeListener(mTaskMenuStateListener);
            }
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

    public void setOrientationState(RecentsOrientedState orientationState) {
        PagedOrientationHandler orientationHandler = orientationState.getOrientationHandler();
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        LayoutParams snapshotParams = (LayoutParams) mSnapshotView.getLayoutParams();
        int thumbnailPadding = (int) getResources().getDimension(R.dimen.task_thumbnail_top_margin);
        LayoutParams iconParams = (LayoutParams) mIconView.getLayoutParams();
        switch (orientationHandler.getRotation()) {
            case ROTATION_90:
                iconParams.gravity = (isRtl ? START : END) | CENTER_VERTICAL;
                iconParams.rightMargin = -thumbnailPadding;
                iconParams.leftMargin = 0;
                iconParams.topMargin = snapshotParams.topMargin / 2;
                break;
            case ROTATION_180:
                iconParams.gravity = BOTTOM | CENTER_HORIZONTAL;
                iconParams.bottomMargin = -thumbnailPadding;
                iconParams.leftMargin = iconParams.topMargin = iconParams.rightMargin = 0;
                break;
            case ROTATION_270:
                iconParams.gravity = (isRtl ? END : START) | CENTER_VERTICAL;
                iconParams.leftMargin = -thumbnailPadding;
                iconParams.rightMargin = 0;
                iconParams.topMargin = snapshotParams.topMargin / 2;
                break;
            case Surface.ROTATION_0:
            default:
                iconParams.gravity = TOP | CENTER_HORIZONTAL;
                iconParams.leftMargin = iconParams.topMargin = iconParams.rightMargin = 0;
                break;
        }
        mIconView.setLayoutParams(iconParams);
        mIconView.setRotation(orientationHandler.getDegreesRotated());

        if (mMenuView != null) {
            mMenuView.onRotationChanged();
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
        if (mContextualChip != null && mContextualChipWrapper != null) {
            mContextualChipWrapper.setAlpha(scale);
            mContextualChip.setScaleX(scale);
            mContextualChip.setScaleY(scale);
        }
        updateFooterVerticalOffset(1.0f - scale);
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

    protected void resetViewTransforms() {
        setCurveScale(1);
        mFillDismissGapTranslationX = mTaskOffsetTranslationX = 0f;
        mFillDismissGapTranslationY = mTaskOffsetTranslationY = 0f;
        setTranslationX(0f);
        setTranslationY(0f);
        setTranslationZ(0);
        setAlpha(mStableAlpha);
        setIconScaleAndDim(1);
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
        // Don't do anything if it's modal.
        if (mModalness > 0) {
            return;
        }

        float curveInterpolation =
                CURVE_INTERPOLATOR.getInterpolation(scrollState.linearInterpolation);
        float curveScaleForCurveInterpolation = getCurveScaleForCurveInterpolation(
                curveInterpolation);
        mSnapshotView.setDimAlpha(curveInterpolation * MAX_PAGE_SCRIM_ALPHA);
        setCurveScale(curveScaleForCurveInterpolation);

        mFooterAlpha = Utilities.boundToRange(1.0f - 2 * scrollState.linearInterpolation, 0f, 1f);
        for (FooterWrapper footer : mFooters) {
            if (footer != null) {
                footer.mView.setAlpha(mFooterAlpha);
            }
        }

        if (mMenuView != null) {
            PagedOrientationHandler pagedOrientationHandler = getPagedOrientationHandler();
            RecentsView recentsView = getRecentsView();
            mMenuView.setPosition(getX() - recentsView.getScrollX(),
                    getY() - recentsView.getScrollY(), pagedOrientationHandler);
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
            LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
            layoutParams.gravity = BOTTOM | CENTER_HORIZONTAL;
            layoutParams.bottomMargin =
                    ((MarginLayoutParams) mSnapshotView.getLayoutParams()).bottomMargin;
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

    /**
     * Sets the contextual chip.
     *
     * @param view Wrapper view containing contextual chip.
     */
    public void setContextualChip(View view) {
        if (mContextualChipWrapper != null) {
            removeView(mContextualChipWrapper);
        }
        if (view != null) {
            mContextualChipWrapper = view;
            LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = BOTTOM | CENTER_HORIZONTAL;
            int expectedChipHeight = getExpectedViewHeight(view);
            float chipOffset = getResources().getDimension(R.dimen.chip_hint_vertical_offset);
            layoutParams.bottomMargin = (int)
                    (((MarginLayoutParams) mSnapshotView.getLayoutParams()).bottomMargin
                            - expectedChipHeight + chipOffset);
            mContextualChip = ((FrameLayout) mContextualChipWrapper).getChildAt(0);
            mContextualChip.setScaleX(0f);
            mContextualChip.setScaleY(0f);
            GradientDrawable scrimDrawable = (GradientDrawable) getResources().getDrawable(
                    R.drawable.chip_scrim_gradient, mActivity.getTheme());
            float cornerRadius = getTaskCornerRadius();
            scrimDrawable.setCornerRadii(
                    new float[]{0, 0, 0, 0, cornerRadius, cornerRadius, cornerRadius,
                            cornerRadius});
            InsetDrawable scrimDrawableInset = new InsetDrawable(scrimDrawable, 0, 0, 0,
                    (int) (expectedChipHeight - chipOffset));
            mContextualChipWrapper.setBackground(scrimDrawableInset);
            mContextualChipWrapper.setPadding(0, 0, 0, 0);
            mContextualChipWrapper.setAlpha(0f);
            addView(view, getChildCount(), layoutParams);
            if (mContextualChip != null) {
                mContextualChip.animate().scaleX(1f).scaleY(1f).setDuration(50);
            }
            if (mContextualChipWrapper != null) {
                mChipTouchDelegate = new TransformingTouchDelegate(mContextualChipWrapper);
                mContextualChipWrapper.animate().alpha(1f).setDuration(50);
            }
        }
    }

    public float getTaskCornerRadius() {
        return TaskCornerRadius.get(mActivity);
    }

    /**
     * Clears the contextual chip from TaskView.
     *
     * @return The contextual chip wrapper view to be recycled.
     */
    public View clearContextualChip() {
        if (mContextualChipWrapper != null) {
            removeView(mContextualChipWrapper);
        }
        View oldContextualChipWrapper = mContextualChipWrapper;
        mContextualChipWrapper = null;
        mContextualChip = null;
        mChipTouchDelegate = null;
        return oldContextualChipWrapper;
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
        updateFooterVerticalOffset(0);
    }

    private void updateFooterVerticalOffset(float offset) {
        mFooterVerticalOffset = offset;

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

    private void setCurveScale(float curveScale) {
        mCurveScale = curveScale;
        setScaleX(mCurveScale);
        setScaleY(mCurveScale);
    }

    public float getCurveScale() {
        return mCurveScale;
    }

    private void setFillDismissGapTranslationX(float x) {
        mFillDismissGapTranslationX = x;
        applyTranslationX();
    }

    private void setFillDismissGapTranslationY(float y) {
        mFillDismissGapTranslationY = y;
        applyTranslationY();
    }

    private void setTaskOffsetTranslationX(float x) {
        mTaskOffsetTranslationX = x;
        applyTranslationX();
    }

    private void setTaskOffsetTranslationY(float y) {
        mTaskOffsetTranslationY = y;
        applyTranslationY();
    }

    private void applyTranslationX() {
        setTranslationX(mFillDismissGapTranslationX + mTaskOffsetTranslationX);
    }

    private void applyTranslationY() {
        setTranslationY(mFillDismissGapTranslationY + mTaskOffsetTranslationY);
    }

    public FloatProperty<TaskView> getPrimaryFillDismissGapTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                FILL_DISMISS_GAP_TRANSLATION_X, FILL_DISMISS_GAP_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getPrimaryTaskOffsetTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                TASK_OFFSET_TRANSLATION_X, TASK_OFFSET_TRANSLATION_Y);
    }

    @Override
    public boolean hasOverlappingRendering() {
        // TODO: Clip-out the icon region from the thumbnail, since they are overlapping.
        return false;
    }

    private static final class TaskOutlineProvider extends ViewOutlineProvider {

        private final int mMarginTop;
        private FullscreenDrawParams mFullscreenParams;

        TaskOutlineProvider(Context context, FullscreenDrawParams fullscreenParams) {
            mMarginTop = context.getResources().getDimensionPixelSize(
                    R.dimen.task_thumbnail_top_margin);
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

            mExpectedHeight = getExpectedViewHeight(view);
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
            float offset = Utilities.or(mFooterVerticalOffset, mModalness);
            mAnimationOffset = Math.round(mStackHeight * offset);
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

    private int getExpectedViewHeight(View view) {
        int expectedHeight;
        int h = view.getLayoutParams().height;
        if (h > 0) {
            expectedHeight = h;
        } else {
            int m = MeasureSpec.makeMeasureSpec(MeasureSpec.EXACTLY - 1, MeasureSpec.AT_MOST);
            view.measure(m, m);
            expectedHeight = view.getMeasuredHeight();
        }
        return expectedHeight;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.addAction(
                new AccessibilityNodeInfo.AccessibilityAction(R.string.accessibility_close,
                        getContext().getText(R.string.accessibility_close)));

        final Context context = getContext();
        for (SystemShortcut s : TaskOverlayFactory.getEnabledShortcuts(this)) {
            info.addAction(s.createAccessibilityAction(context));
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
                        0, 1, recentsView.getTaskViewCount() - recentsView.indexOfChild(this) - 1,
                        1, false);
        info.setCollectionItemInfo(itemInfo);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.string.accessibility_close) {
            getRecentsView().dismissTask(this, true /*animateTaskView*/,
                    true /*removeTask*/);
            return true;
        }

        if (action == R.string.accessibility_app_usage_settings) {
            mDigitalWellBeingToast.openAppUsageSettings(this);
            return true;
        }

        for (SystemShortcut s : TaskOverlayFactory.getEnabledShortcuts(this)) {
            if (s.hasHandlerForAction(action)) {
                s.onClick(this);
                return true;
            }
        }

        return super.performAccessibilityAction(action, arguments);
    }

    public RecentsView getRecentsView() {
        return (RecentsView) getParent();
    }

    PagedOrientationHandler getPagedOrientationHandler() {
        return getRecentsView().mOrientationState.getOrientationHandler();
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
     *
     * @param progress: 0 = show icon and no insets; 1 = don't show icon and show full insets.
     */
    public void setFullscreenProgress(float progress) {
        progress = Utilities.boundToRange(progress, 0, 1);
        mFullscreenProgress = progress;
        boolean isFullscreen = mFullscreenProgress > 0;
        mIconView.setVisibility(progress < 1 ? VISIBLE : INVISIBLE);
        setClipChildren(!isFullscreen);
        setClipToPadding(!isFullscreen);

        TaskThumbnailView thumbnail = getThumbnail();
        updateCurrentFullscreenParams(thumbnail.getPreviewPositionHelper());

        if (!getRecentsView().isTaskIconScaledDown(this)) {
            // Some of the items in here are dependent on the current fullscreen params, but don't
            // update them if the icon is supposed to be scaled down.
            setIconScaleAndDim(progress, true /* invert */);
        }

        thumbnail.setFullscreenParams(mCurrentFullscreenParams);
        mOutlineProvider.setFullscreenParams(mCurrentFullscreenParams);
        invalidateOutline();
    }

    void updateCurrentFullscreenParams(PreviewPositionHelper previewPositionHelper) {
        if (getRecentsView() == null) {
            return;
        }
        mCurrentFullscreenParams.setProgress(
                mFullscreenProgress,
                getRecentsView().getScaleX(),
                getWidth(), mActivity.getDeviceProfile(),
                previewPositionHelper);
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
    public static class FullscreenDrawParams {

        private final float mCornerRadius;
        private final float mWindowCornerRadius;

        public RectF mCurrentDrawnInsets = new RectF();
        public float mCurrentDrawnCornerRadius;
        /** The current scale we apply to the thumbnail to adjust for new left/right insets. */
        public float mScale = 1;

        public FullscreenDrawParams(Context context) {
            mCornerRadius = TaskCornerRadius.get(context);
            mWindowCornerRadius = QuickStepContract.getWindowCornerRadius(context.getResources());

            mCurrentDrawnCornerRadius = mCornerRadius;
        }

        /**
         * Sets the progress in range [0, 1]
         */
        public void setProgress(float fullscreenProgress, float parentScale, int previewWidth,
                DeviceProfile dp, PreviewPositionHelper pph) {
            RectF insets = pph.getInsetsToDrawInFullscreen();

            float currentInsetsLeft = insets.left * fullscreenProgress;
            float currentInsetsRight = insets.right * fullscreenProgress;
            mCurrentDrawnInsets.set(currentInsetsLeft, insets.top * fullscreenProgress,
                    currentInsetsRight, insets.bottom * fullscreenProgress);
            float fullscreenCornerRadius = dp.isMultiWindowMode ? 0 : mWindowCornerRadius;

            mCurrentDrawnCornerRadius =
                    Utilities.mapRange(fullscreenProgress, mCornerRadius, fullscreenCornerRadius)
                            / parentScale;

            // We scaled the thumbnail to fit the content (excluding insets) within task view width.
            // Now that we are drawing left/right insets again, we need to scale down to fit them.
            if (previewWidth > 0) {
                mScale = previewWidth / (previewWidth + currentInsetsLeft + currentInsetsRight);
            }
        }

    }
}
