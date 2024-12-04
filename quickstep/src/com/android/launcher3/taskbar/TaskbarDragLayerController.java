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

import static com.android.launcher3.taskbar.TaskbarPinningController.PINNING_PERSISTENT;
import static com.android.launcher3.taskbar.TaskbarPinningController.PINNING_TRANSIENT;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemProperties;
import android.view.ViewTreeObserver;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.util.DimensionUtils;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.TouchController;

import java.io.PrintWriter;

/**
 * Handles properties/data collection, then passes the results to TaskbarDragLayer to render.
 */
public class TaskbarDragLayerController implements TaskbarControllers.LoggableTaskbarController,
        TaskbarControllers.BackgroundRendererController {

    private static final boolean DEBUG = SystemProperties.getBoolean(
            "persist.debug.draw_taskbar_debug_ui", false);

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
    private final AnimatedFloat mAssistantBgTaskbar = new AnimatedFloat(
            this::updateBackgroundAlpha);
    // Used to hide our background color when someone else (e.g. ScrimView) is handling it.
    private final AnimatedFloat mBgOverride = new AnimatedFloat(this::updateBackgroundAlpha);

    // Translation property for taskbar background.
    private final AnimatedFloat mBgOffset = new AnimatedFloat(this::updateBackgroundOffset);

    // Used to fade in/out the entirety of the taskbar, for a smooth transition before/after sysui
    // changes the inset visibility.
    private final AnimatedFloat mTaskbarAlpha = new AnimatedFloat(this::updateTaskbarAlpha);

    private final AnimatedFloat mTaskbarBackgroundProgress = new AnimatedFloat(
            this::updateTaskbarBackgroundProgress);

    // Initialized in init.
    private TaskbarControllers mControllers;
    private TaskbarStashViaTouchController mTaskbarStashViaTouchController;
    private AnimatedFloat mOnBackgroundNavButtonColorIntensity;

    private MultiProperty mBackgroundRendererAlpha;
    private float mLastSetBackgroundAlpha;

    public TaskbarDragLayerController(TaskbarActivityContext activity,
            TaskbarDragLayer taskbarDragLayer) {
        mActivity = activity;
        mTaskbarDragLayer = taskbarDragLayer;
        mBackgroundRendererAlpha = mTaskbarDragLayer.getBackgroundRendererAlpha();
        final Resources resources = mTaskbarDragLayer.getResources();
        mFolderMargin = resources.getDimensionPixelSize(R.dimen.taskbar_folder_margin);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        mTaskbarStashViaTouchController = new TaskbarStashViaTouchController(mControllers);
        mTaskbarDragLayer.init(new TaskbarDragLayerCallbacks());

        mOnBackgroundNavButtonColorIntensity = mControllers.navbarButtonsViewController
                .getOnTaskbarBackgroundNavButtonColorOverride();

        mTaskbarBackgroundProgress.updateValue(DisplayController.isTransientTaskbar(mActivity)
                ? PINNING_TRANSIENT
                : PINNING_PERSISTENT);

        mBgTaskbar.value = 1;
        mKeyguardBgTaskbar.value = 1;
        mNotificationShadeBgTaskbar.value = 1;
        mImeBgTaskbar.value = 1;
        mAssistantBgTaskbar.value = 1;
        mBgOverride.value = 1;
        updateBackgroundAlpha();

        mTaskbarAlpha.value = 1;
        updateTaskbarAlpha();
    }

    public void onDestroy() {
        mTaskbarDragLayer.onDestroy();
    }

    /**
     * @return Bounds (in TaskbarDragLayer coordinates) where an opened Folder can display.
     */
    public Rect getFolderBoundingBox() {
        Rect boundingBox = new Rect(0, 0, mTaskbarDragLayer.getWidth(),
                mTaskbarDragLayer.getHeight() - mActivity.getDeviceProfile().taskbarHeight
                        - mActivity.getDeviceProfile().taskbarBottomMargin);
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

    public AnimatedFloat getAssistantBgTaskbar() {
        return mAssistantBgTaskbar;
    }

    public AnimatedFloat getTaskbarBackgroundOffset() {
        return mBgOffset;
    }

    // AnimatedFloat is for animating between pinned and transient taskbar
    public AnimatedFloat getTaskbarBackgroundProgress() {
        return mTaskbarBackgroundProgress;
    }

    public AnimatedFloat getTaskbarAlpha() {
        return mTaskbarAlpha;
    }

    /**
     * Make updates when configuration changes.
     */
    public void onConfigurationChanged() {
        mTaskbarStashViaTouchController.updateGestureHeight();
    }

    private void updateBackgroundAlpha() {
        final float bgNavbar = mBgNavbar.value;
        final float bgTaskbar = mBgTaskbar.value * mKeyguardBgTaskbar.value
                * mNotificationShadeBgTaskbar.value * mImeBgTaskbar.value
                * mAssistantBgTaskbar.value;
        mLastSetBackgroundAlpha = mBgOverride.value * Math.max(bgNavbar, bgTaskbar);
        mBackgroundRendererAlpha.setValue(mLastSetBackgroundAlpha);

        updateOnBackgroundNavButtonColorIntensity();
    }

    public MultiProperty getBackgroundRendererAlphaForStash() {
        return mTaskbarDragLayer.getBackgroundRendererAlphaForStash();
    }

    /**
     * Sets the translation of the background during the swipe up gesture.
     */
    public void setTranslationYForSwipe(float transY) {
        mTaskbarDragLayer.setBackgroundTranslationYForSwipe(transY);
    }

    /**
     * Sets the translation of the background during the spring on stash animation.
     */
    public void setTranslationYForStash(float transY) {
        mTaskbarDragLayer.setBackgroundTranslationYForStash(transY);
    }

    private void updateBackgroundOffset() {
        mTaskbarDragLayer.setTaskbarBackgroundOffset(mBgOffset.value);
        updateOnBackgroundNavButtonColorIntensity();
    }

    private void updateTaskbarBackgroundProgress() {
        mTaskbarDragLayer.setTaskbarBackgroundProgress(mTaskbarBackgroundProgress.value);
    }

    private void updateTaskbarAlpha() {
        mTaskbarDragLayer.setAlpha(mTaskbarAlpha.value);
    }

    @Override
    public void setCornerRoundness(float cornerRoundness) {
        mTaskbarDragLayer.setCornerRoundness(cornerRoundness);
    }

    /**
     * Set if another controller is temporarily handling background drawing. In this case we
     * override our background alpha to be {@code 0}.
     */
    public void setIsBackgroundDrawnElsewhere(boolean isBackgroundDrawnElsewhere) {
        mBgOverride.updateValue(isBackgroundDrawnElsewhere ? 0 : 1);
    }

    private void updateOnBackgroundNavButtonColorIntensity() {
        mOnBackgroundNavButtonColorIntensity.updateValue(
                mLastSetBackgroundAlpha * (1 - mBgOffset.value));
    }

    /**
     * Sets the width percentage to inset the transient taskbar's background from the left and from
     * the right.
     */
    public void setBackgroundHorizontalInsets(float insetPercentage) {
        mTaskbarDragLayer.setBackgroundHorizontalInsets(insetPercentage);

    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarDragLayerController:");

        pw.println(prefix + "\tmBgOffset=" + mBgOffset.value);
        pw.println(prefix + "\tmTaskbarAlpha=" + mTaskbarAlpha.value);
        pw.println(prefix + "\tmFolderMargin=" + mFolderMargin);
        pw.println(prefix + "\tmLastSetBackgroundAlpha=" + mLastSetBackgroundAlpha);
        pw.println(prefix + "\t\tmBgOverride=" + mBgOverride.value);
        pw.println(prefix + "\t\tmBgNavbar=" + mBgNavbar.value);
        pw.println(prefix + "\t\tmBgTaskbar=" + mBgTaskbar.value);
        pw.println(prefix + "\t\tmKeyguardBgTaskbar=" + mKeyguardBgTaskbar.value);
        pw.println(prefix + "\t\tmNotificationShadeBgTaskbar=" + mNotificationShadeBgTaskbar.value);
        pw.println(prefix + "\t\tmImeBgTaskbar=" + mImeBgTaskbar.value);
        pw.println(prefix + "\t\tmAssistantBgTaskbar=" + mAssistantBgTaskbar.value);
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
         * Called when an IME inset is changed.
         */
        public void onImeInsetChanged() {
            mControllers.taskbarStashController.onImeInsetChanged();
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
            if (mActivity.isPhoneMode()) {
                Resources resources = mActivity.getResources();
                Point taskbarDimensions = DimensionUtils.getTaskbarPhoneDimensions(deviceProfile,
                        resources, true /* isPhoneMode */);
                return taskbarDimensions.y == -1 ?
                        deviceProfile.getDisplayInfo().currentSize.y :
                        taskbarDimensions.y;
            } else {
                return deviceProfile.taskbarHeight;
            }
        }

        /**
         * Returns touch controllers.
         */
        public TouchController[] getTouchControllers() {
            return new TouchController[] {
                    mActivity.getDragController(),
                    mControllers.taskbarForceVisibleImmersiveController,
                    mControllers.navbarButtonsViewController.getTouchController(),
                    mTaskbarStashViaTouchController,
            };
        }

        /**
         * Draws debug UI on top of everything in TaskbarDragLayer.
         */
        public void drawDebugUi(Canvas canvas) {
            if (!DEBUG) {
                return;
            }
            mControllers.taskbarInsetsController.drawDebugTouchableRegionBounds(canvas);
        }
    }
}
