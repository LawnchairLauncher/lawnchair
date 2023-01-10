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

import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.DimensionUtils;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.TouchController;

import java.io.PrintWriter;

/**
 * Handles properties/data collection, then passes the results to TaskbarDragLayer to render.
 */
public class TaskbarDragLayerController implements TaskbarControllers.LoggableTaskbarController,
        TaskbarControllers.BackgroundRendererController {

    private final TaskbarActivityContext mActivity;
    private final TaskbarDragLayer mTaskbarDragLayer;
    private final int mFolderMargin;
    private float mGestureHeightYThreshold;

    // Alpha properties for taskbar background.
    private final AnimatedFloat mBgTaskbar = new AnimatedFloat(this::updateBackgroundAlpha);
    private final AnimatedFloat mBgNavbar = new AnimatedFloat(this::updateBackgroundAlpha);
    private final AnimatedFloat mKeyguardBgTaskbar = new AnimatedFloat(this::updateBackgroundAlpha);
    private final AnimatedFloat mNotificationShadeBgTaskbar = new AnimatedFloat(
            this::updateBackgroundAlpha);
    private final AnimatedFloat mImeBgTaskbar = new AnimatedFloat(this::updateBackgroundAlpha);
    // Used to hide our background color when someone else (e.g. ScrimView) is handling it.
    private final AnimatedFloat mBgOverride = new AnimatedFloat(this::updateBackgroundAlpha);

    // Translation property for taskbar background.
    private final AnimatedFloat mBgOffset = new AnimatedFloat(this::updateBackgroundOffset);

    // Initialized in init.
    private TaskbarControllers mControllers;
    private AnimatedFloat mNavButtonDarkIntensityMultiplier;

    private float mLastSetBackgroundAlpha;

    public TaskbarDragLayerController(TaskbarActivityContext activity,
            TaskbarDragLayer taskbarDragLayer) {
        mActivity = activity;
        mTaskbarDragLayer = taskbarDragLayer;
        final Resources resources = mTaskbarDragLayer.getResources();
        mFolderMargin = resources.getDimensionPixelSize(R.dimen.taskbar_folder_margin);
        updateGestureHeight();
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mTaskbarDragLayer.init(new TaskbarDragLayerCallbacks());

        mNavButtonDarkIntensityMultiplier = mControllers.navbarButtonsViewController
                .getNavButtonDarkIntensityMultiplier();

        mBgTaskbar.value = 1;
        mKeyguardBgTaskbar.value = 1;
        mNotificationShadeBgTaskbar.value = 1;
        mImeBgTaskbar.value = 1;
        mBgOverride.value = 1;
        updateBackgroundAlpha();
    }

    public void onDestroy() {
        mTaskbarDragLayer.onDestroy();
    }

    /**
     * @return Bounds (in TaskbarDragLayer coordinates) where an opened Folder can display.
     */
    public Rect getFolderBoundingBox() {
        Rect boundingBox = new Rect(0, 0, mTaskbarDragLayer.getWidth(),
                mTaskbarDragLayer.getHeight() - mActivity.getDeviceProfile().taskbarSize);
        boundingBox.inset(mFolderMargin, mFolderMargin);
        return boundingBox;
    }

    public AnimatedFloat getTaskbarBackgroundAlpha() {
        return mBgTaskbar;
    }

    public AnimatedFloat getNavbarBackgroundAlpha() {
        return mBgNavbar;
    }

    public AnimatedFloat getKeyguardBgTaskbar() {
        return mKeyguardBgTaskbar;
    }

    public AnimatedFloat getNotificationShadeBgTaskbar() {
        return mNotificationShadeBgTaskbar;
    }

    public AnimatedFloat getImeBgTaskbar() {
        return mImeBgTaskbar;
    }

    public AnimatedFloat getOverrideBackgroundAlpha() {
        return mBgOverride;
    }

    public AnimatedFloat getTaskbarBackgroundOffset() {
        return mBgOffset;
    }

    private void updateGestureHeight() {
        int gestureHeight = ResourceUtils.getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE,
                mActivity.getResources());
        mGestureHeightYThreshold = mActivity.getDeviceProfile().heightPx - gestureHeight;
    }

    /**
     * Make updates when configuration changes.
     */
    public void onConfigurationChanged() {
        updateGestureHeight();
    }

    private void updateBackgroundAlpha() {
        final float bgNavbar = mBgNavbar.value;
        final float bgTaskbar = mBgTaskbar.value * mKeyguardBgTaskbar.value
                * mNotificationShadeBgTaskbar.value * mImeBgTaskbar.value;
        mLastSetBackgroundAlpha = mBgOverride.value * Math.max(bgNavbar, bgTaskbar);
        mTaskbarDragLayer.setTaskbarBackgroundAlpha(mLastSetBackgroundAlpha);

        updateNavBarDarkIntensityMultiplier();
    }

    /**
     * Sets the translation of the background during the swipe up gesture.
     */
    public void setTranslationYForSwipe(float transY) {
        mTaskbarDragLayer.setBackgroundTranslationYForSwipe(transY);
    }

    private void updateBackgroundOffset() {
        mTaskbarDragLayer.setTaskbarBackgroundOffset(mBgOffset.value);

        updateNavBarDarkIntensityMultiplier();
    }

    @Override
    public void setCornerRoundness(float cornerRoundness) {
        mTaskbarDragLayer.setCornerRoundness(cornerRoundness);
    }

    private void updateNavBarDarkIntensityMultiplier() {
        // Zero out the app-requested dark intensity when we're drawing our own background.
        float effectiveBgAlpha = mLastSetBackgroundAlpha * (1 - mBgOffset.value);
        mNavButtonDarkIntensityMultiplier.updateValue(1 - effectiveBgAlpha);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarDragLayerController:");

        pw.println(prefix + "\tmBgOffset=" + mBgOffset.value);
        pw.println(prefix + "\tmFolderMargin=" + mFolderMargin);
        pw.println(prefix + "\tmLastSetBackgroundAlpha=" + mLastSetBackgroundAlpha);
    }

    /**
     * Callbacks for {@link TaskbarDragLayer} to interact with its controller.
     */
    public class TaskbarDragLayerCallbacks {

        private final int[] mTempOutLocation = new int[2];

        /**
         * Called to update the touchable insets.
         * @see ViewTreeObserver.InternalInsetsInfo#setTouchableInsets(int)
         */
        public void updateInsetsTouchability(ViewTreeObserver.InternalInsetsInfo insetsInfo) {
            mControllers.taskbarInsetsController.updateInsetsTouchability(insetsInfo);
        }

        /**
         * Listens to TaskbarDragLayer touch events and responds accordingly.
         */
        public void tryStashBasedOnMotionEvent(MotionEvent ev) {
            if (!DisplayController.isTransientTaskbar(mActivity)) {
                return;
            }
            if (mControllers.taskbarStashController.isStashed()) {
                return;
            }

            boolean stashTaskbar = false;

            MotionEvent screenCoordinates = MotionEvent.obtain(ev);
            if (ev.getAction() == MotionEvent.ACTION_OUTSIDE) {
                stashTaskbar = true;
            } else if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                mTaskbarDragLayer.getLocationOnScreen(mTempOutLocation);
                screenCoordinates.offsetLocation(mTempOutLocation[0], mTempOutLocation[1]);

                if (!mControllers.taskbarViewController.isEventOverAnyItem(screenCoordinates)
                        && screenCoordinates.getY() < mGestureHeightYThreshold) {
                    stashTaskbar = true;
                }
            }

            if (stashTaskbar) {
                mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            }
        }

        /**
         * Called when a child is removed from TaskbarDragLayer.
         */
        public void onDragLayerViewRemoved() {
            mActivity.onDragEndOrViewRemoved();
        }

        /**
         * Returns how tall the background should be drawn at the bottom of the screen.
         */
        public int getTaskbarBackgroundHeight() {
            DeviceProfile deviceProfile = mActivity.getDeviceProfile();
            if (TaskbarManager.isPhoneMode(deviceProfile)) {
                Resources resources = mActivity.getResources();
                Point taskbarDimensions =
                        DimensionUtils.getTaskbarPhoneDimensions(deviceProfile, resources,
                                TaskbarManager.isPhoneMode(deviceProfile));
                return taskbarDimensions.y == -1 ?
                        deviceProfile.getDisplayInfo().currentSize.y :
                        taskbarDimensions.y;
            } else {
                return deviceProfile.taskbarSize;
            }
        }

        /**
         * Returns touch controllers.
         */
        public TouchController[] getTouchControllers() {
            return new TouchController[]{mActivity.getDragController(),
                    mControllers.taskbarForceVisibleImmersiveController,
                    mControllers.navbarButtonsViewController.getTouchController()};
        }
    }
}
