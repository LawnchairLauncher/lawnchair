/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.window.DesktopModeFlags.ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION;

import static com.android.launcher3.config.FeatureFlags.enableTaskbarPinning;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_ALLAPPS_BUTTON_LONG_PRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_ALLAPPS_BUTTON_TAP;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_TASKBAR_OVERFLOW;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.jank.Cuj;
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;

/**
 * Callbacks for {@link TaskbarView} to interact with its controller.
 */
public class TaskbarViewCallbacks {

    private final TaskbarActivityContext mActivity;
    private final TaskbarControllers mControllers;
    private final TaskbarView mTaskbarView;
    private final GestureDetector mGestureDetector;

    public TaskbarViewCallbacks(TaskbarActivityContext activity, TaskbarControllers controllers,
            TaskbarView taskbarView) {
        mActivity = activity;
        mControllers = controllers;
        mTaskbarView = taskbarView;
        mGestureDetector = new GestureDetector(activity, new TaskbarViewGestureListener());
    }

    public View.OnClickListener getIconOnClickListener() {
        return mActivity.getItemOnClickListener();
    }

    /** Trigger All Apps button click action. */
    public void triggerAllAppsButtonClick(View v) {
        InteractionJankMonitorWrapper.begin(v, Cuj.CUJ_LAUNCHER_OPEN_ALL_APPS,
                /* tag= */ "TASKBAR_BUTTON");
        mActivity.getStatsLogManager().logger().log(LAUNCHER_TASKBAR_ALLAPPS_BUTTON_TAP);
        if (mActivity.showLockedTaskbarOnHome()
                || mActivity.showDesktopTaskbarForFreeformDisplay()) {
            // If the taskbar can be shown on the home screen, use mAllAppsToggler to toggle all
            // apps, which will toggle the launcher activity all apps when on home screen.
            // TODO(b/395913143): Reconsider this if a gap in taskbar all apps functionality that
            //  prevents users to drag items to workspace is addressed.
            mControllers.uiController.toggleAllApps(false);
        } else {
            mControllers.taskbarAllAppsController.toggle();
        }
    }

    /** Trigger All Apps button long click action. */
    public void triggerAllAppsButtonLongClick() {
        mActivity.getStatsLogManager().logger().log(LAUNCHER_TASKBAR_ALLAPPS_BUTTON_LONG_PRESS);
    }

    /** @return true if haptic feedback should occur when long pressing the all apps button. */
    public boolean isAllAppsButtonHapticFeedbackEnabled(Context context) {
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    public View.OnTouchListener getTaskbarTouchListener() {
        return (view, event) -> mGestureDetector.onTouchEvent(event);
    }

    public View.OnLongClickListener getTaskbarDividerLongClickListener() {
        return v -> {
            mControllers.taskbarPinningController.showPinningView(v, getDividerCenterX());
            return true;
        };
    }

    public View.OnTouchListener getTaskbarDividerRightClickListener() {
        return (v, event) -> {
            if (event.isFromSource(InputDevice.SOURCE_MOUSE)
                    && event.getAction() == MotionEvent.ACTION_DOWN
                    && event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                mControllers.taskbarPinningController.showPinningView(v, getDividerCenterX());
                return true;
            }
            return false;
        };
    }

    public View.OnLongClickListener getIconOnLongClickListener() {
        return mControllers.taskbarDragController::startDragOnLongClick;
    }

    /** Gets the hover listener for the provided icon view. */
    public View.OnHoverListener getIconOnHoverListener(View icon) {
        return new TaskbarHoverToolTipController(mActivity, mTaskbarView, icon);
    }

    /** Callback invoked before Taskbar icons are laid out. */
    void onPreLayoutChildren() {
        if (enableTaskbarPinning() && ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION.isTrue()) {
            mControllers.taskbarViewController.updateTaskbarIconTranslationXForPinning();
        }
    }

    /**
     * Notifies launcher to update icon alignment.
     */
    public void notifyIconLayoutBoundsChanged() {
        mControllers.taskbarViewController.notifyIconLayoutBoundsChanged();
    }

    /**
     * Notifies the taskbar scrim when the visibility of taskbar changes.
     */
    public void notifyVisibilityChanged() {
        mControllers.taskbarScrimViewController.onTaskbarVisibilityChanged(
                mTaskbarView.getVisibility());
    }

    /**
     * Get current location of bubble bar, if it is visible.
     * Returns {@code null} if bubble bar is not shown.
     */
    @Nullable
    public BubbleBarLocation getBubbleBarLocationIfVisible() {
        BubbleBarViewController bubbleBarViewController =
                mControllers.bubbleControllers.map(c -> c.bubbleBarViewController).orElse(null);
        if (bubbleBarViewController != null && bubbleBarViewController.isBubbleBarVisible()) {
            return bubbleBarViewController.getBubbleBarLocation();
        }
        return null;
    }

    /**
     * Get the max bubble bar collapsed width for the current bubble bar visibility state. Used to
     * reserve space for the bubble bar when transitioning taskbar view into overflow.
     */
    public float getBubbleBarMaxCollapsedWidthIfVisible() {
        return mControllers.bubbleControllers
                .filter(c -> !c.bubbleBarViewController.isHiddenForNoBubbles())
                .map(c -> c.bubbleBarViewController.getCollapsedWidthWithMaxVisibleBubbles())
                .orElse(0f);
    }

    /** Returns true if bubble bar controllers are present. */
    public boolean isBubbleBarEnabled() {
        return mControllers.bubbleControllers.isPresent();
    }

    /** Returns on click listener for the taskbar overflow view. */
    public View.OnClickListener getOverflowOnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleKeyboardQuickSwitchView();
            }
        };
    }

    /** Returns on long click listener for the taskbar overflow view. */
    public View.OnLongClickListener getOverflowOnLongClickListener() {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                toggleKeyboardQuickSwitchView();
                return true;
            }
        };
    }

    private void toggleKeyboardQuickSwitchView() {
        if (mTaskbarView.getTaskbarOverflowView() != null) {
            mTaskbarView.getTaskbarOverflowView().setIsActive(
                    !mTaskbarView.getTaskbarOverflowView().getIsActive());
            mControllers.taskbarAutohideSuspendController
                    .updateFlag(FLAG_AUTOHIDE_SUSPEND_TASKBAR_OVERFLOW,
                            mTaskbarView.getTaskbarOverflowView().getIsActive());
        }
        mControllers.keyboardQuickSwitchController.toggleQuickSwitchViewForTaskbar(
                mControllers.taskbarViewController.getShownTaskIds(),
                this::onKeyboardQuickSwitchViewClosed);
    }

    private void onKeyboardQuickSwitchViewClosed() {
        if (mTaskbarView.getTaskbarOverflowView() != null) {
            mTaskbarView.getTaskbarOverflowView().setIsActive(false);
        }
        mControllers.taskbarAutohideSuspendController.updateFlag(
                FLAG_AUTOHIDE_SUSPEND_TASKBAR_OVERFLOW, false);
    }

    private float getDividerCenterX() {
        View divider = mTaskbarView.getTaskbarDividerViewContainer();
        if (divider == null) {
            return 0.0f;
        }
        return divider.getX() + (float) divider.getWidth() / 2;
    }

    private class TaskbarViewGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(@NonNull MotionEvent event) {
            if (event.isFromSource(InputDevice.SOURCE_MOUSE)
                    && event.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
                maybeShowPinningView(event);
            }
            return true;
        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent event) {
            return true;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent event) {
            if (maybeShowPinningView(event)) {
                mTaskbarView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }

        /** Returns true if the taskbar pinning popup view was shown for {@code event}. */
        private boolean maybeShowPinningView(@NonNull MotionEvent event) {
            if (!mActivity.isPinnedTaskbar() || mTaskbarView.isEventOverAnyItem(event)) {
                return false;
            }
            mControllers.taskbarPinningController.showPinningView(mTaskbarView, event.getRawX());
            return true;
        }
    }
}
