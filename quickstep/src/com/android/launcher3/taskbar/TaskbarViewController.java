/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.AnimatedFloat.VALUE;

import android.graphics.Rect;
import android.util.FloatProperty;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.AnimatedFloat;

import java.io.PrintWriter;

/**
 * Handles properties/data collection, then passes the results to TaskbarView to render.
 */
public class TaskbarViewController implements TaskbarControllers.LoggableTaskbarController {

    private static final String TAG = TaskbarViewController.class.getSimpleName();

    private static final Runnable NO_OP = () -> { };

    public static final int ALPHA_INDEX_HOME = 0;
    public static final int ALPHA_INDEX_KEYGUARD = 1;
    public static final int ALPHA_INDEX_STASH = 2;
    public static final int ALPHA_INDEX_RECENTS_DISABLED = 3;
    public static final int ALPHA_INDEX_NOTIFICATION_EXPANDED = 4;
    private static final int NUM_ALPHA_CHANNELS = 5;

    private final TaskbarActivityContext mActivity;
    private final TaskbarView mTaskbarView;
    private final MultiValueAlpha mTaskbarIconAlpha;
    private final AnimatedFloat mTaskbarIconScaleForStash = new AnimatedFloat(this::updateScale);
    private final AnimatedFloat mTaskbarIconTranslationYForHome = new AnimatedFloat(
            this::updateTranslationY);
    private final AnimatedFloat mTaskbarIconTranslationYForStash = new AnimatedFloat(
            this::updateTranslationY);
    private AnimatedFloat mTaskbarNavButtonTranslationY;

    private final TaskbarModelCallbacks mModelCallbacks;

    // Initialized in init.
    private TaskbarControllers mControllers;

    // Animation to align icons with Launcher, created lazily. This allows the controller to be
    // active only during the animation and does not need to worry about layout changes.
    private AnimatorPlaybackController mIconAlignControllerLazy = null;
    private Runnable mOnControllerPreCreateCallback = NO_OP;

    public TaskbarViewController(TaskbarActivityContext activity, TaskbarView taskbarView) {
        mActivity = activity;
        mTaskbarView = taskbarView;
        mTaskbarIconAlpha = new MultiValueAlpha(mTaskbarView, NUM_ALPHA_CHANNELS);
        mTaskbarIconAlpha.setUpdateVisibility(true);
        mModelCallbacks = new TaskbarModelCallbacks(activity, mTaskbarView);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mTaskbarView.init(new TaskbarViewCallbacks());
        mTaskbarView.getLayoutParams().height = mActivity.getDeviceProfile().taskbarSize;

        mTaskbarIconScaleForStash.updateValue(1f);

        mModelCallbacks.init(controllers);
        if (mActivity.isUserSetupComplete()) {
            // Only load the callbacks if user setup is completed
            LauncherAppState.getInstance(mActivity).getModel().addCallbacksAndLoad(mModelCallbacks);
        }
        mTaskbarNavButtonTranslationY =
                controllers.navbarButtonsViewController.getTaskbarNavButtonTranslationY();
    }

    public void onDestroy() {
        LauncherAppState.getInstance(mActivity).getModel().removeCallbacks(mModelCallbacks);
    }

    public boolean areIconsVisible() {
        return mTaskbarView.areIconsVisible();
    }

    public MultiValueAlpha getTaskbarIconAlpha() {
        return mTaskbarIconAlpha;
    }

    /**
     * Should be called when the IME visibility changes, so we can make Taskbar not steal touches.
     */
    public void setImeIsVisible(boolean isImeVisible) {
        mTaskbarView.setTouchesEnabled(!isImeVisible);
    }

    /**
     * Should be called when the recents button is disabled, so we can hide taskbar icons as well.
     */
    public void setRecentsButtonDisabled(boolean isDisabled) {
        // TODO: check TaskbarStashController#supportsStashing(), to stash instead of setting alpha.
        mTaskbarIconAlpha.getProperty(ALPHA_INDEX_RECENTS_DISABLED).setValue(isDisabled ? 0 : 1);
    }

    /**
     * Sets OnClickListener and OnLongClickListener for the given view.
     */
    public void setClickAndLongClickListenersForIcon(View icon) {
        mTaskbarView.setClickAndLongClickListenersForIcon(icon);
    }

    /**
     * Adds one time pre draw listener to the taskbar view, it is called before
     * drawing a frame and invoked only once
     * @param listener callback that will be invoked before drawing the next frame
     */
    public void addOneTimePreDrawListener(Runnable listener) {
        mTaskbarView.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                final ViewTreeObserver viewTreeObserver = mTaskbarView.getViewTreeObserver();
                if (viewTreeObserver.isAlive()) {
                    listener.run();
                    viewTreeObserver.removeOnPreDrawListener(this);
                }
                return true;
            }
        });
    }

    public Rect getIconLayoutBounds() {
        return mTaskbarView.getIconLayoutBounds();
    }

    public View[] getIconViews() {
        return mTaskbarView.getIconViews();
    }

    public View getAllAppsButtonView() {
        return mTaskbarView.getAllAppsButtonView();
    }

    public AnimatedFloat getTaskbarIconScaleForStash() {
        return mTaskbarIconScaleForStash;
    }

    public AnimatedFloat getTaskbarIconTranslationYForStash() {
        return mTaskbarIconTranslationYForStash;
    }

    /**
     * Applies scale properties for the entire TaskbarView (rather than individual icons).
     */
    private void updateScale() {
        float scale = mTaskbarIconScaleForStash.value;
        mTaskbarView.setScaleX(scale);
        mTaskbarView.setScaleY(scale);
    }

    private void updateTranslationY() {
        mTaskbarView.setTranslationY(mTaskbarIconTranslationYForHome.value
                + mTaskbarIconTranslationYForStash.value);
    }

    /**
     * Sets the taskbar icon alignment relative to Launcher hotseat icons
     * @param alignmentRatio [0, 1]
     *                       0 => not aligned
     *                       1 => fully aligned
     */
    public void setLauncherIconAlignment(float alignmentRatio, DeviceProfile launcherDp) {
        if (mIconAlignControllerLazy == null) {
            mIconAlignControllerLazy = createIconAlignmentController(launcherDp);
        }
        mIconAlignControllerLazy.setPlayFraction(alignmentRatio);
        if (alignmentRatio <= 0 || alignmentRatio >= 1) {
            // Cleanup lazy controller so that it is created again in next animation
            mIconAlignControllerLazy = null;
        }
    }

    /**
     * Creates an animation for aligning the taskbar icons with the provided Launcher device profile
     */
    private AnimatorPlaybackController createIconAlignmentController(DeviceProfile launcherDp) {
        mOnControllerPreCreateCallback.run();
        PendingAnimation setter = new PendingAnimation(100);
        Rect hotseatPadding = launcherDp.getHotseatLayoutPadding(mActivity);
        float scaleUp = ((float) launcherDp.iconSizePx) / mActivity.getDeviceProfile().iconSizePx;
        int borderSpacing = launcherDp.cellLayoutBorderSpacePx.x;
        int hotseatCellSize = DeviceProfile.calculateCellWidth(
                launcherDp.availableWidthPx - hotseatPadding.left - hotseatPadding.right,
                borderSpacing,
                launcherDp.numShownHotseatIcons);

        int offsetY = launcherDp.getTaskbarOffsetY();
        setter.setFloat(mTaskbarIconTranslationYForHome, VALUE, -offsetY, LINEAR);
        setter.setFloat(mTaskbarNavButtonTranslationY, VALUE, -offsetY, LINEAR);

        int collapsedHeight = mActivity.getDefaultTaskbarWindowHeight();
        int expandedHeight = Math.max(collapsedHeight,
                mActivity.getDeviceProfile().taskbarSize + offsetY);
        setter.addOnFrameListener(anim -> mActivity.setTaskbarWindowHeight(
                anim.getAnimatedFraction() > 0 ? expandedHeight : collapsedHeight));

        int count = mTaskbarView.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = mTaskbarView.getChildAt(i);

            int positionInHotseat = -1;
            if (FeatureFlags.ENABLE_ALL_APPS_IN_TASKBAR.get() && i == count - 1) {
                // Note that there is no All Apps button in the hotseat, this position is only used
                // as its convenient for animation purposes.
                positionInHotseat = Utilities.isRtl(child.getResources())
                        ? -1
                        : mActivity.getDeviceProfile().inv.numShownHotseatIcons;

                setter.setViewAlpha(child, 0, LINEAR);
            } else if (child.getTag() instanceof ItemInfo) {
                positionInHotseat = ((ItemInfo) child.getTag()).screenId;
            } else {
                Log.w(TAG, "Unsupported view found in createIconAlignmentController, v=" + child);
                continue;
            }

            float hotseatIconCenter = hotseatPadding.left
                    + (hotseatCellSize + borderSpacing) * positionInHotseat
                    + hotseatCellSize / 2;

            float childCenter = (child.getLeft() + child.getRight()) / 2;
            setter.setFloat(child, ICON_TRANSLATE_X, hotseatIconCenter - childCenter, LINEAR);

            setter.setFloat(child, SCALE_PROPERTY, scaleUp, LINEAR);
        }

        AnimatorPlaybackController controller = setter.createPlaybackController();
        mOnControllerPreCreateCallback = () -> controller.setPlayFraction(0);
        return controller;
    }

    public void onRotationChanged(DeviceProfile deviceProfile) {
        if (areIconsVisible()) {
            // We only translate on rotation when on home
            return;
        }
        mTaskbarNavButtonTranslationY.updateValue(-deviceProfile.getTaskbarOffsetY());
    }

    public View mapOverItems(LauncherBindableItemsContainer.ItemOperator op) {
        return mTaskbarView.mapOverItems(op);
    }

    /**
     * Returns whether the given MotionEvent, *in screen coorindates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        return mTaskbarView.isEventOverAnyItem(ev);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarViewController:");
        mModelCallbacks.dumpLogs(prefix + "\t", pw);
    }

    /**
     * Callbacks for {@link TaskbarView} to interact with its controller.
     */
    public class TaskbarViewCallbacks {
        private final float mSquaredTouchSlop = Utilities.squaredTouchSlop(mActivity);

        private float mDownX, mDownY;
        private boolean mCanceledStashHint;

        public View.OnClickListener getIconOnClickListener() {
            return mActivity.getItemOnClickListener();
        }

        public View.OnClickListener getAllAppsButtonClickListener() {
            return v -> mControllers.taskbarAllAppsViewController.show();
        }

        public View.OnLongClickListener getIconOnLongClickListener() {
            return mControllers.taskbarDragController::startDragOnLongClick;
        }

        public View.OnLongClickListener getBackgroundOnLongClickListener() {
            return view -> mControllers.taskbarStashController
                    .updateAndAnimateIsManuallyStashedInApp(true);
        }

        /**
         * Get the first chance to handle TaskbarView#onTouchEvent, and return whether we want to
         * consume the touch so TaskbarView treats it as an ACTION_CANCEL.
         */
        public boolean onTouchEvent(MotionEvent motionEvent) {
            final float x = motionEvent.getRawX();
            final float y = motionEvent.getRawY();
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = x;
                    mDownY = y;
                    mControllers.taskbarStashController.startStashHint(/* animateForward = */ true);
                    mCanceledStashHint = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!mCanceledStashHint
                            && squaredHypot(mDownX - x, mDownY - y) > mSquaredTouchSlop) {
                        mControllers.taskbarStashController.startStashHint(
                                /* animateForward= */ false);
                        mCanceledStashHint = true;
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!mCanceledStashHint) {
                        mControllers.taskbarStashController.startStashHint(
                                /* animateForward= */ false);
                    }
                    break;
            }
            return false;
        }
    }

    public static final FloatProperty<View> ICON_TRANSLATE_X =
            new FloatProperty<View>("taskbarAligmentTranslateX") {

                @Override
                public void setValue(View view, float v) {
                    if (view instanceof BubbleTextView) {
                        ((BubbleTextView) view).setTranslationXForTaskbarAlignmentAnimation(v);
                    } else if (view instanceof FolderIcon) {
                        ((FolderIcon) view).setTranslationForTaskbarAlignmentAnimation(v);
                    } else {
                        view.setTranslationX(v);
                    }
                }

                @Override
                public Float get(View view) {
                    if (view instanceof BubbleTextView) {
                        return ((BubbleTextView) view)
                                .getTranslationXForTaskbarAlignmentAnimation();
                    } else if (view instanceof FolderIcon) {
                        return ((FolderIcon) view).getTranslationXForTaskbarAlignmentAnimation();
                    }
                    return view.getTranslationX();
                }
            };
}
