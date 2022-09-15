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
import android.graphics.Rect;
import android.view.ViewTreeObserver;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.AnimatedFloat;

import java.io.PrintWriter;

/**
 * Handles properties/data collection, then passes the results to TaskbarDragLayer to render.
 */
public class TaskbarDragLayerController implements TaskbarControllers.LoggableTaskbarController {

    private final TaskbarActivityContext mActivity;
    private final TaskbarDragLayer mTaskbarDragLayer;
    private final int mFolderMargin;

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

    private void updateBackgroundAlpha() {
        final float bgNavbar = mBgNavbar.value;
        final float bgTaskbar = mBgTaskbar.value * mKeyguardBgTaskbar.value
                * mNotificationShadeBgTaskbar.value * mImeBgTaskbar.value;
        mLastSetBackgroundAlpha = mBgOverride.value * Math.max(bgNavbar, bgTaskbar);
        mTaskbarDragLayer.setTaskbarBackgroundAlpha(mLastSetBackgroundAlpha);

        updateNavBarDarkIntensityMultiplier();
    }

    private void updateBackgroundOffset() {
        mTaskbarDragLayer.setTaskbarBackgroundOffset(mBgOffset.value);

        updateNavBarDarkIntensityMultiplier();
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

        /**
         * Called to update the touchable insets.
         * @see ViewTreeObserver.InternalInsetsInfo#setTouchableInsets(int)
         */
        public void updateInsetsTouchability(ViewTreeObserver.InternalInsetsInfo insetsInfo) {
            mControllers.taskbarInsetsController.updateInsetsTouchability(insetsInfo);
        }

        /**
         * Called when a child is removed from TaskbarDragLayer.
         */
        public void onDragLayerViewRemoved() {
            mActivity.maybeSetTaskbarWindowNotFullscreen();
        }

        /**
         * Returns how tall the background should be drawn at the bottom of the screen.
         */
        public int getTaskbarBackgroundHeight() {
            DeviceProfile deviceProfile = mActivity.getDeviceProfile();
            if (TaskbarManager.isPhoneMode(deviceProfile)) {
                Resources resources = mActivity.getResources();
                return mActivity.isThreeButtonNav() ?
                        resources.getDimensionPixelSize(R.dimen.taskbar_size) :
                        resources.getDimensionPixelSize(R.dimen.taskbar_stashed_size);
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
