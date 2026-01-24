/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.window.DesktopModeFlags.ENABLE_TASKBAR_OVERFLOW;

import static com.android.launcher3.desktop.DesktopAppLaunchTransition.AppLaunchType.UNMINIMIZE;
import static com.android.launcher3.taskbar.TaskbarDesktopExperienceFlags.enableAltTabKqsFlatenning;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.animation.AnimationUtils;
import android.window.OnBackInvokedCallback;
import android.window.RemoteTransition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.jank.Cuj;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.desktop.DesktopAppLaunchTransition;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayDragLayer;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.FocusState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SlideInRemoteTransition;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.shared.desktopmode.DesktopTaskToFrontReason;

import java.io.PrintWriter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles initialization of the {@link KeyboardQuickSwitchView} and supplies it with the list of
 * tasks.
 */
public class KeyboardQuickSwitchViewController {

    @NonNull private final ViewCallbacks mViewCallbacks = new ViewCallbacks();
    @NonNull private final TaskbarControllers mControllers;
    @NonNull private final TaskbarOverlayContext mOverlayContext;
    @NonNull private final KeyboardQuickSwitchView mKeyboardQuickSwitchView;
    @NonNull private final KeyboardQuickSwitchController.ControllerCallbacks mControllerCallbacks;

    @Nullable private Animator mCloseAnimation;

    private int mCurrentFocusIndex = -1;

    private boolean mOnDesktop;
    private boolean mWasDesktopTaskFilteredOut;
    private boolean mWasOpenedFromTaskbar;

    private boolean mDetachingFromWindow = false;

    protected KeyboardQuickSwitchViewController(
            @NonNull TaskbarControllers controllers,
            @NonNull TaskbarOverlayContext overlayContext,
            @NonNull KeyboardQuickSwitchView keyboardQuickSwitchView,
            @NonNull KeyboardQuickSwitchController.ControllerCallbacks controllerCallbacks) {
        mControllers = controllers;
        mOverlayContext = overlayContext;
        mKeyboardQuickSwitchView = keyboardQuickSwitchView;
        mControllerCallbacks = controllerCallbacks;
    }

    protected int getCurrentFocusedIndex() {
        return mCurrentFocusIndex;
    }

    protected boolean wasOpenedFromTaskbar() {
        return mWasOpenedFromTaskbar;
    }

    protected void openQuickSwitchView(
            @NonNull List<GroupTask> tasks,
            int numHiddenTasks,
            boolean updateTasks,
            int currentFocusIndexOverride,
            boolean onDesktop,
            boolean hasDesktopTask,
            boolean wasDesktopTaskFilteredOut,
            boolean wasOpenedFromTaskbar) {
        final boolean isTransientTaskBar = mControllers.taskbarActivityContext.isTransientTaskbar();
        positionView(wasOpenedFromTaskbar, isTransientTaskBar);

        // Keep the taskbar unstashed if the KQS is opened.
        if (wasOpenedFromTaskbar && isTransientTaskBar) {
            mControllers.taskbarStashController.updateTaskbarTimeout(/* isAutohideSuspended= */
                    true);
        }

        mOverlayContext.getDragLayer().addView(mKeyboardQuickSwitchView);
        mOnDesktop = onDesktop;
        mWasDesktopTaskFilteredOut = wasDesktopTaskFilteredOut;
        mWasOpenedFromTaskbar = wasOpenedFromTaskbar;

        if (ENABLE_TASKBAR_OVERFLOW.isTrue() && wasOpenedFromTaskbar) {
            mKeyboardQuickSwitchView.enableScrollArrowSupport();
        }

        mKeyboardQuickSwitchView.applyLoadPlan(
                mOverlayContext,
                tasks,
                numHiddenTasks,
                updateTasks,
                currentFocusIndexOverride,
                mViewCallbacks,
                /* useDesktopTaskView= */ !onDesktop && hasDesktopTask);
    }

    protected void updateQuickSwitchView(
            @NonNull List<GroupTask> tasks,
            int numHiddenTasks,
            int currentFocusIndexOverride,
            boolean hasDesktopTask,
            boolean wasDesktopTaskFilteredOut) {
        mWasDesktopTaskFilteredOut = wasDesktopTaskFilteredOut;
        mKeyboardQuickSwitchView.applyLoadPlan(
                mOverlayContext,
                tasks,
                numHiddenTasks,
                /* updateTasks= */ true,
                currentFocusIndexOverride,
                mViewCallbacks,
                /* useDesktopTaskView= */ !mOnDesktop && hasDesktopTask);
    }

    protected void positionView(boolean wasOpenedFromTaskbar, boolean isTransientTaskbar) {
        if (!wasOpenedFromTaskbar) {
            // Keep the default positioning.
            return;
        }

        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(
                mKeyboardQuickSwitchView.getLayoutParams());
        final Resources resources = mKeyboardQuickSwitchView.getResources();
        final int marginHorizontal = resources.getDimensionPixelSize(
                R.dimen.keyboard_quick_switch_margin_ends);

        final DeviceProfile dp = mControllers.taskbarActivityContext.getDeviceProfile();
        // Calculate the additional margin space that the KQS should move up for the transient
        // taskbar. The value of spaceForTaskbar is the distance between the bottom of the KQS
        // view with 0 bottom margin to the top of the transient taskbar view.
        final int spaceForTaskbar = isTransientTaskbar ? dp.getTaskbarProfile().getHeight()
                + dp.getTaskbarProfile().getBottomMargin()
                - dp.getTaskbarProfile().getStashedTaskbarHeight() : 0;
        final int marginBottom = spaceForTaskbar + resources.getDimensionPixelSize(
                R.dimen.keyboard_quick_switch_margin_bottom);

        lp.setMargins(marginHorizontal, 0, marginHorizontal, marginBottom);
        lp.width = BaseDragLayer.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        mKeyboardQuickSwitchView.setLayoutParams(lp);
    }

    protected void updateLayoutForSurface(boolean updateLayoutFromTaskbar,
            int currentFocusIndexOverride) {
        BaseDragLayer.LayoutParams lp =
                (BaseDragLayer.LayoutParams) mKeyboardQuickSwitchView.getLayoutParams();

        if (updateLayoutFromTaskbar) {
            lp.width = BaseDragLayer.LayoutParams.WRAP_CONTENT;
        } else {
            lp.width = BaseDragLayer.LayoutParams.MATCH_PARENT;
        }

        mKeyboardQuickSwitchView.animateOpen(currentFocusIndexOverride);
    }

    boolean isCloseAnimationRunning() {
        return mCloseAnimation != null;
    }

    protected void closeQuickSwitchView(boolean animate) {
        if (isCloseAnimationRunning()) {
            if (!animate) {
                mCloseAnimation.end();
            }
            // Let currently-running animation finish.
            return;
        }
        mControllerCallbacks.onCloseStarted();
        if (!animate) {
            InteractionJankMonitorWrapper.begin(
                    mKeyboardQuickSwitchView, Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_CLOSE);
            onCloseComplete();
            return;
        }
        mCloseAnimation = mKeyboardQuickSwitchView.getCloseAnimation();

        mCloseAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                InteractionJankMonitorWrapper.begin(
                        mKeyboardQuickSwitchView, Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_CLOSE);
            }
        });
        mCloseAnimation.addListener(AnimatorListeners.forEndCallback(this::onCloseComplete));
        mCloseAnimation.start();
    }

    /**
     * Launched the currently-focused task.
     *
     * Returns index -1 iff the RecentsView shouldn't be opened.
     *
     * If the index is not -1, then the {@link com.android.quickstep.views.TaskView} at the returned
     * index will be focused.
     */
    protected int launchFocusedTask() {
        if (mCurrentFocusIndex != -1) {
            return launchTaskAt(mCurrentFocusIndex);
        }
        // If the user quick switches too quickly, updateCurrentFocusIndex might not have run.
        return launchTaskAt(mControllerCallbacks.isFirstTaskRunning()
                && mKeyboardQuickSwitchView.getTaskCount() > 1 ? 1 : 0);
    }

    private int launchTaskAt(int index) {
        if (isCloseAnimationRunning()) {
            // Ignore taps on task views and alt key unpresses while the close animation is running.
            return -1;
        }
        if (index == mKeyboardQuickSwitchView.getOverviewTaskIndex()) {
            // If there is a desktop task view, then we should account for it when focusing the
            // first hidden non-desktop task view in recents view
            return mOnDesktop ? 1 : (mWasDesktopTaskFilteredOut ? index + 1 : index);
        }
        TaskbarActivityContext context = mControllers.taskbarActivityContext;
        final RemoteTransition slideInTransition = new RemoteTransition(new SlideInRemoteTransition(
                Utilities.isRtl(mControllers.taskbarActivityContext.getResources()),
                context.getDeviceProfile().getOverviewProfile().getPageSpacing(),
                QuickStepContract.getWindowCornerRadius(context),
                AnimationUtils.loadInterpolator(
                        context, android.R.interpolator.fast_out_extra_slow_in)),
                "SlideInTransition");
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.get(
                mKeyboardQuickSwitchView.getContext());
        if (index == mKeyboardQuickSwitchView.getDesktopTaskIndex()) {
            UI_HELPER_EXECUTOR.execute(() ->
                    systemUiProxy
                            .showDesktopApps(
                                    mKeyboardQuickSwitchView.getDisplay().getDisplayId(),
                                    slideInTransition));
            return -1;
        }
        // Even with a valid index, this can be null if the user tries to quick switch before the
        // views have been added in the KeyboardQuickSwitchView.
        GroupTask task = mControllerCallbacks.getTaskAt(index);
        if (task == null) {
            return mOnDesktop ? 1 : Math.max(0, index);
        }

        if (enableAltTabKqsFlatenning.isTrue()
                && tryLaunchingCombinedTask(task, slideInTransition, systemUiProxy)) {
            return -1;
        }

        // TODO b/414410702: move this check to before tryLaunchingCombinedTask() call.
        if (mControllerCallbacks.isTaskRunning(task)) {
            // Ignore attempts to run the selected task if it is already running.
            return -1;
        }

        RemoteTransition remoteTransition = slideInTransition;
        boolean canUnminimizeDesktopTask = task instanceof SingleTask singleTask
                && mControllers.taskbarActivityContext.canUnminimizeDesktopTask(
                        singleTask.getTask().key.id);
        if (mOnDesktop && canUnminimizeDesktopTask) {
            // This app is being unminimized - use our own transition runner.
            remoteTransition = getUnminimizeTransition();
        }
        mControllers.taskbarActivityContext.handleGroupTaskLaunch(
                task,
                remoteTransition,
                mOnDesktop,
                DesktopTaskToFrontReason.ALT_TAB);
        return -1;
    }

    private boolean tryLaunchingCombinedTask(GroupTask task, RemoteTransition slideInTransition,
            SystemUiProxy systemUiProxy) {
        TaskbarActivityContext context = mControllers.taskbarActivityContext;
        int taskId = task.getTasks().getFirst().key.id;

        // All DesktopTasks, irrespective of whether desktop mode is active, are launched here as
        // the class DesktopTask is used in a special way by KQS view for showing thumbnails of
        // freeform tasks.
        if (task instanceof DesktopTask desktopTask) {
            boolean canUnminimizeDesktopTask = context.canUnminimizeDesktopTask(taskId);
            UI_HELPER_EXECUTOR.execute(() -> {
                if (!mOnDesktop) {
                    systemUiProxy.activateDesk(desktopTask.getDeskId(), slideInTransition);
                }

                systemUiProxy.showDesktopApp(taskId,
                        canUnminimizeDesktopTask ? getUnminimizeTransition() : null,
                        DesktopTaskToFrontReason.ALT_TAB);
            });
            return true;
        } else if (mOnDesktop && task instanceof SingleTask) {
            // Use the special API if user wants to switch to a fullscreen app while in desktop.
            UI_HELPER_EXECUTOR.execute(
                    () -> systemUiProxy.moveToFullscreen(taskId,
                            DesktopModeTransitionSource.KEYBOARD_SHORTCUT, slideInTransition));
            return true;
        }

        // For all other cases, let TaskbarActivityContext handle launching the task.
        return false;
    }

    private RemoteTransition getUnminimizeTransition() {
        return new RemoteTransition(
                new DesktopAppLaunchTransition(
                        mControllers.taskbarActivityContext,
                        UNMINIMIZE,
                        Cuj.CUJ_DESKTOP_MODE_KEYBOARD_QUICK_SWITCH_APP_LAUNCH,
                        MAIN_EXECUTOR
                ),
                "DesktopKeyboardQuickSwitchUnminimize");
    }

    private void onCloseComplete() {
        mCloseAnimation = null;
        // Reset the view callbacks to prevent `onDetachedFromWindow` getting called in response to
        // the `removeView(mKeyboardQuickSwitchView)` call.
        mKeyboardQuickSwitchView.resetViewCallbacks();
        if (!mDetachingFromWindow) {
            mOverlayContext.getDragLayer().removeView(mKeyboardQuickSwitchView);
        }
        mControllerCallbacks.onCloseComplete();
        InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_CLOSE);
    }

    protected void onDestroy() {
        closeQuickSwitchView(false);
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "KeyboardQuickSwitchViewController:");

        pw.println(prefix + "\thasFocus=" + mKeyboardQuickSwitchView.hasFocus());
        pw.println(prefix + "\tisCloseAnimationRunning=" + isCloseAnimationRunning());
        pw.println(prefix + "\tmCurrentFocusIndex=" + mCurrentFocusIndex);
        pw.println(prefix + "\tmOnDesktop=" + mOnDesktop);
        pw.println(prefix + "\tmWasDesktopTaskFilteredOut=" + mWasDesktopTaskFilteredOut);
        pw.println(prefix + "\tmWasOpenedFromTaskbar=" + mWasOpenedFromTaskbar);
    }

    /**
     * @return True if the MotionEvent is over the {@link KeyboardQuickSwitchView}.
     */
    protected boolean isEventOverKeyboardQuickSwitch(TaskbarOverlayDragLayer dl, MotionEvent ev) {
        return dl.isEventOverView(mKeyboardQuickSwitchView, ev);
    }

    class ViewCallbacks implements FocusState.FocusChangeListener {
        public final OnBackInvokedCallback onBackInvokedCallback = () -> closeQuickSwitchView(true);

        boolean onKeyUp(int keyCode, KeyEvent event, boolean isRTL, boolean allowTraversal) {
            if (keyCode != KeyEvent.KEYCODE_TAB
                    && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                    && keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                    && keyCode != KeyEvent.KEYCODE_GRAVE
                    && keyCode != KeyEvent.KEYCODE_ESCAPE
                    && keyCode != KeyEvent.KEYCODE_ENTER) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_GRAVE || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                closeQuickSwitchView(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                launchTaskAt(mCurrentFocusIndex);
                return true;
            }
            if (!allowTraversal) {
                return false;
            }
            boolean traverseBackwards = (keyCode == KeyEvent.KEYCODE_TAB && event.isShiftPressed())
                    || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isRTL)
                    || (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !isRTL);
            int taskCount = mKeyboardQuickSwitchView.getTaskCount();
            int toIndex = mCurrentFocusIndex == -1
                    // Focus the second-most recent app if possible
                    ? (taskCount > 1 ? 1 : 0)
                    : (traverseBackwards
                            // focus a more recent task or loop back to the opposite end
                            ? Math.max(0, mCurrentFocusIndex == 0
                                    ? taskCount - 1 : mCurrentFocusIndex - 1)
                            // focus a less recent app or loop back to the opposite end
                            : ((mCurrentFocusIndex + 1) % taskCount));

            if (mCurrentFocusIndex == toIndex) {
                return true;
            }
            mKeyboardQuickSwitchView.animateFocusMove(mCurrentFocusIndex, toIndex);

            return true;
        }

        void updateCurrentFocusIndex(int index) {
            mCurrentFocusIndex = index;
        }

        void launchTaskAt(int index) {
            mCurrentFocusIndex = index;
            mControllers.taskbarActivityContext.launchKeyboardFocusedTask();
        }

        void updateThumbnailInBackground(Task task, Consumer<ThumbnailData> callback) {
            mControllerCallbacks.updateThumbnailInBackground(task, callback);
        }

        void updateIconInBackground(Task task, Consumer<Task> callback) {
            mControllerCallbacks.updateIconInBackground(task, callback);
        }

        boolean isAspectRatioSquare() {
            return mControllerCallbacks.isAspectRatioSquare();
        }

        void onViewDetchedFromWindow() {
            mDetachingFromWindow = true;
            closeQuickSwitchView(false);
            mDetachingFromWindow = false;
        }

        @Override
        public void onFocusedDisplayChanged(int displayId) {
            if (mControllers.taskbarActivityContext.getDisplayId() != displayId) {
                closeQuickSwitchView(/* animate= */ true);
            }
        }
    }
}
