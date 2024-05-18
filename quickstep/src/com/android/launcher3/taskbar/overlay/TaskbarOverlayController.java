/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar.overlay;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_CONSUME_IME_INSETS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.LauncherState.ALL_APPS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.util.Optional;

/**
 * Handles the Taskbar overlay window lifecycle.
 * <p>
 * Overlays need to be inflated in a separate window so that have the correct hierarchy. For
 * instance, they need to be below the notification tray. If there are multiple overlays open, the
 * same window is used.
 */
public final class TaskbarOverlayController {

    private static final String WINDOW_TITLE = "Taskbar Overlay";

    private final TaskbarActivityContext mTaskbarContext;
    private final Context mWindowContext;
    private final TaskbarOverlayProxyView mProxyView;
    private final LayoutParams mLayoutParams;

    private final TaskStackChangeListener mTaskStackListener = new TaskStackChangeListener() {
        @Override
        public void onTaskMovedToFront(int taskId) {
            // New front task will be below existing overlay, so move out of the way.
            hideWindowOnTaskStackChange();
        }

        @Override
        public void onTaskStackChanged() {
            // The other callbacks are insufficient for All Apps, because there are many cases where
            // it can relaunch the same task already behind it. However, this callback needs to be a
            // no-op when only EDU is shown, because going between the EDU steps invokes this
            // callback.
            if (mControllers.getSharedState() != null
                    && mControllers.getSharedState().allAppsVisible) {
                hideWindowOnTaskStackChange();
            }
        }

        private void hideWindowOnTaskStackChange() {
            // A task was launched while overlay window was open, so stash Taskbar.
            mControllers.taskbarStashController.updateAndAnimateTransientTaskbar(true);
            hideWindow();
        }
    };

    private DeviceProfile mLauncherDeviceProfile;
    private @Nullable TaskbarOverlayContext mOverlayContext;
    private TaskbarControllers mControllers; // Initialized in init.

    public TaskbarOverlayController(
            TaskbarActivityContext taskbarContext, DeviceProfile launcherDeviceProfile) {
        mTaskbarContext = taskbarContext;
        mWindowContext = mTaskbarContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null);
        mProxyView = new TaskbarOverlayProxyView();
        mLayoutParams = createLayoutParams();
        mLauncherDeviceProfile = launcherDeviceProfile;
    }

    /** Initialize the controller. */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    /**
     * Creates a window for Taskbar overlays, if it does not already exist. Returns the window
     * context for the current overlay window.
     */
    public TaskbarOverlayContext requestWindow() {
        if (mOverlayContext == null) {
            mOverlayContext = new TaskbarOverlayContext(
                    mWindowContext, mTaskbarContext, mControllers);
        }

        if (!mProxyView.isOpen()) {
            mProxyView.show();
            Optional.ofNullable(mOverlayContext.getSystemService(WindowManager.class))
                    .ifPresent(m -> m.addView(mOverlayContext.getDragLayer(), mLayoutParams));
            TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
        }

        return mOverlayContext;
    }

    /** Hides the current overlay window with animation. */
    public void hideWindow() {
        mProxyView.close(true);
    }

    /**
     * Removes the overlay window from the hierarchy, if all floating views are closed and there is
     * no system drag operation in progress.
     * <p>
     * This method should be called after an exit animation finishes, if applicable.
     */
    @SuppressLint("WrongConstant")
    void maybeCloseWindow() {
        if (mOverlayContext != null && (AbstractFloatingView.hasOpenView(mOverlayContext, TYPE_ALL)
                || mOverlayContext.getDragController().isSystemDragInProgress())) {
            return;
        }
        mProxyView.close(false);
        onDestroy();
    }

    /** Destroys the controller and any overlay window if present. */
    public void onDestroy() {
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
        Optional.ofNullable(mOverlayContext)
                .map(c -> c.getSystemService(WindowManager.class))
                .ifPresent(m -> m.removeViewImmediate(mOverlayContext.getDragLayer()));
        mOverlayContext = null;
    }

    /** The current device profile for the overlay window. */
    public DeviceProfile getLauncherDeviceProfile() {
        return mLauncherDeviceProfile;
    }

    /** Updates {@link DeviceProfile} instance for Taskbar's overlay window. */
    public void updateLauncherDeviceProfile(DeviceProfile dp) {
        mLauncherDeviceProfile = dp;
        Optional.ofNullable(mOverlayContext).ifPresent(c -> {
            AbstractFloatingView.closeAllOpenViewsExcept(c, false, TYPE_REBIND_SAFE);
            c.dispatchDeviceProfileChanged();
        });
    }

    /** The default open duration for overlays. */
    public int getOpenDuration() {
        return ALL_APPS.getTransitionDuration(mTaskbarContext, true);
    }

    /** The default close duration for overlays. */
    public int getCloseDuration() {
        return ALL_APPS.getTransitionDuration(mTaskbarContext, false);
    }

    @SuppressLint("WrongConstant")
    private LayoutParams createLayoutParams() {
        LayoutParams layoutParams = new LayoutParams(
                TYPE_APPLICATION_OVERLAY,
                LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        layoutParams.setTitle(WINDOW_TITLE);
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.packageName = mTaskbarContext.getPackageName();
        layoutParams.setFitInsetsTypes(0); // Handled by container view.
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.setSystemApplicationOverlay(true);
        layoutParams.privateFlags = PRIVATE_FLAG_CONSUME_IME_INSETS;
        return layoutParams;
    }

    /**
     * Proxy view connecting taskbar drag layer to the overlay window.
     *
     * Overlays are in a separate window and has its own drag layer, but this proxy lets its views
     * behave as though they are in the taskbar drag layer. For instance, when the taskbar closes
     * all {@link AbstractFloatingView} instances, the overlay window will also close.
     */
    private class TaskbarOverlayProxyView extends AbstractFloatingView {

        private TaskbarOverlayProxyView() {
            super(mTaskbarContext, null);
        }

        private void show() {
            mIsOpen = true;
            mTaskbarContext.getDragLayer().addView(this);
        }

        @Override
        protected void handleClose(boolean animate) {
            if (mIsOpen) {
                mTaskbarContext.getDragLayer().removeView(this);
                Optional.ofNullable(mOverlayContext).ifPresent(c -> closeAllOpenViews(c, animate));
            }
        }

        @Override
        protected boolean isOfType(int type) {
            return (type & TYPE_TASKBAR_OVERLAY_PROXY) != 0;
        }

        @Override
        public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
            return false;
        }
    }
}
