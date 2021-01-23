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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_BOTTOM_TAPPABLE_ELEMENT;
import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_EXTRA_NAVIGATION_BAR;

import android.animation.Animator;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepAppTransitionManagerImpl;
import com.android.launcher3.R;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.quickstep.AnimatedFloat;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Interfaces with Launcher/WindowManager/SystemUI to determine what to show in TaskbarView.
 */
public class TaskbarController {

    private static final String WINDOW_TITLE = "Taskbar";

    private final TaskbarContainerView mTaskbarContainerView;
    private final TaskbarView mTaskbarView;
    private final BaseQuickstepLauncher mLauncher;
    private final WindowManager mWindowManager;
    // Layout width and height of the Taskbar in the default state.
    private final Point mTaskbarSize;
    private final TaskbarStateHandler mTaskbarStateHandler;
    private final TaskbarVisibilityController mTaskbarVisibilityController;
    private final TaskbarHotseatController mHotseatController;
    private final TaskbarDragController mDragController;

    // Initialized in init().
    private WindowManager.LayoutParams mWindowLayoutParams;

    public TaskbarController(BaseQuickstepLauncher launcher,
            TaskbarContainerView taskbarContainerView) {
        mLauncher = launcher;
        mTaskbarContainerView = taskbarContainerView;
        mTaskbarView = mTaskbarContainerView.findViewById(R.id.taskbar_view);
        mTaskbarView.setCallbacks(createTaskbarViewCallbacks());
        mWindowManager = mLauncher.getWindowManager();
        mTaskbarSize = new Point(MATCH_PARENT,
                mLauncher.getResources().getDimensionPixelSize(R.dimen.taskbar_size));
        mTaskbarStateHandler = mLauncher.getTaskbarStateHandler();
        mTaskbarVisibilityController = new TaskbarVisibilityController(mLauncher,
                createTaskbarVisibilityControllerCallbacks());
        mHotseatController = new TaskbarHotseatController(mLauncher,
                createTaskbarHotseatControllerCallbacks());
        mDragController = new TaskbarDragController(mLauncher);
    }

    private TaskbarVisibilityControllerCallbacks createTaskbarVisibilityControllerCallbacks() {
        return new TaskbarVisibilityControllerCallbacks() {
            @Override
            public void updateTaskbarBackgroundAlpha(float alpha) {
                mTaskbarView.setBackgroundAlpha(alpha);
            }

            @Override
            public void updateTaskbarVisibilityAlpha(float alpha) {
                mTaskbarContainerView.setAlpha(alpha);
                AlphaUpdateListener.updateVisibility(mTaskbarContainerView);
            }
        };
    }

    private TaskbarViewCallbacks createTaskbarViewCallbacks() {
        return new TaskbarViewCallbacks() {
            @Override
            public View.OnClickListener getItemOnClickListener() {
                return ItemClickHandler.INSTANCE;
            }

            @Override
            public View.OnLongClickListener getItemOnLongClickListener() {
                return mDragController::startDragOnLongClick;
            }
        };
    }

    private TaskbarHotseatControllerCallbacks createTaskbarHotseatControllerCallbacks() {
        return new TaskbarHotseatControllerCallbacks() {
            @Override
            public void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
                mTaskbarView.updateHotseatItems(hotseatItemInfos);
            }
        };
    }

    /**
     * Initializes the Taskbar, including adding it to the screen.
     */
    public void init() {
        mTaskbarView.init(mHotseatController.getNumHotseatIcons());
        addToWindowManager();
        mTaskbarStateHandler.setTaskbarCallbacks(createTaskbarStateHandlerCallbacks());
        mTaskbarVisibilityController.init();
        mHotseatController.init();
    }

    private TaskbarStateHandlerCallbacks createTaskbarStateHandlerCallbacks() {
        return new TaskbarStateHandlerCallbacks() {
            @Override
            public AnimatedFloat getAlphaTarget() {
                return mTaskbarVisibilityController.getTaskbarVisibilityForLauncherState();
            }
        };
    }

    /**
     * Removes the Taskbar from the screen, and removes any obsolete listeners etc.
     */
    public void cleanup() {
        mTaskbarView.cleanup();
        removeFromWindowManager();
        mTaskbarStateHandler.setTaskbarCallbacks(null);
        mTaskbarVisibilityController.cleanup();
        mHotseatController.cleanup();
    }

    private void removeFromWindowManager() {
        if (mTaskbarContainerView.isAttachedToWindow()) {
            mWindowManager.removeViewImmediate(mTaskbarContainerView);
        }
    }

    private void addToWindowManager() {
        removeFromWindowManager();

        final int gravity = Gravity.BOTTOM;

        mWindowLayoutParams = new WindowManager.LayoutParams(
                mTaskbarSize.x,
                mTaskbarSize.y,
                TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.setTitle(WINDOW_TITLE);
        mWindowLayoutParams.packageName = mLauncher.getPackageName();
        mWindowLayoutParams.gravity = gravity;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

        WindowManagerWrapper wmWrapper = WindowManagerWrapper.getInstance();
        wmWrapper.setProvidesInsetsTypes(
                mWindowLayoutParams,
                new int[] { ITYPE_EXTRA_NAVIGATION_BAR, ITYPE_BOTTOM_TAPPABLE_ELEMENT }
        );

        TaskbarContainerView.LayoutParams taskbarLayoutParams =
                new TaskbarContainerView.LayoutParams(mTaskbarSize.x, mTaskbarSize.y);
        taskbarLayoutParams.gravity = gravity;
        mTaskbarView.setLayoutParams(taskbarLayoutParams);

        mWindowManager.addView(mTaskbarContainerView, mWindowLayoutParams);
    }

    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    public void onLauncherResumedOrPaused(boolean isResumed) {
        long duration = QuickstepAppTransitionManagerImpl.CONTENT_ALPHA_DURATION;
        final Animator anim;
        if (isResumed) {
            anim = createAnimToLauncher(null, duration);
        } else {
            anim = createAnimToApp(duration);
        }
        anim.start();
    }

    /**
     * Create Taskbar animation when going from an app to Launcher.
     * @param toState If known, the state we will end up in when reaching Launcher.
     */
    public Animator createAnimToLauncher(@Nullable LauncherState toState, long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(mTaskbarVisibilityController.createAnimToBackgroundAlpha(0, duration));
        if (toState != null) {
            mTaskbarStateHandler.setStateWithAnimation(toState, new StateAnimationConfig(), anim);
        }
        return anim.buildAnim();
    }

    private Animator createAnimToApp(long duration) {
        return mTaskbarVisibilityController.createAnimToBackgroundAlpha(1, duration);
    }

    /**
     * Should be called when the IME visibility changes, so we can hide/show Taskbar accordingly.
     */
    public void setIsImeVisible(boolean isImeVisible) {
        mTaskbarVisibilityController.animateToVisibilityForIme(isImeVisible ? 0 : 1);
    }

    /**
     * Should be called when one or more items in the Hotseat have changed.
     */
    public void onHotseatUpdated() {
        mHotseatController.onHotseatUpdated();
    }

    /**
     * @param ev MotionEvent in screen coordinates.
     * @return Whether any Taskbar item could handle the given MotionEvent if given the chance.
     */
    public boolean isEventOverAnyTaskbarItem(MotionEvent ev) {
        return mTaskbarView.isEventOverAnyItem(ev);
    }

    public boolean isDraggingItem() {
        return mTaskbarView.isDraggingItem();
    }

    /**
     * @return Whether the given View is in the same window as Taskbar.
     */
    public boolean isViewInTaskbar(View v) {
        return mTaskbarContainerView.getWindowId().equals(v.getWindowId());
    }

    /**
     * Contains methods that TaskbarStateHandler can call to interface with TaskbarController.
     */
    protected interface TaskbarStateHandlerCallbacks {
        AnimatedFloat getAlphaTarget();
    }

    /**
     * Contains methods that TaskbarVisibilityController can call to interface with
     * TaskbarController.
     */
    protected interface TaskbarVisibilityControllerCallbacks {
        void updateTaskbarBackgroundAlpha(float alpha);
        void updateTaskbarVisibilityAlpha(float alpha);
    }

    /**
     * Contains methods that TaskbarView can call to interface with TaskbarController.
     */
    protected interface TaskbarViewCallbacks {
        View.OnClickListener getItemOnClickListener();
        View.OnLongClickListener getItemOnLongClickListener();
    }

    /**
     * Contains methods that TaskbarHotseatController can call to interface with TaskbarController.
     */
    protected interface TaskbarHotseatControllerCallbacks {
        void updateHotseatItems(ItemInfo[] hotseatItemInfos);
    }
}
