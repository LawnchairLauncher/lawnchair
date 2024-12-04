/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.pip.tv;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;

import android.annotation.IntDef;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.RemoteAction;
import android.app.TaskInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Handler;
import android.os.RemoteException;
import android.view.Gravity;

import androidx.annotation.NonNull;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.WindowManagerShellWrapper;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TaskStackListenerCallback;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.pip.PinnedStackListenerForwarder;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.pip.PipAnimationController;
import com.android.wm.shell.pip.PipParamsChangedForwarder;
import com.android.wm.shell.pip.PipTaskOrganizer;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip.PipTransitionState;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.sysui.UserChangeListener;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manages the picture-in-picture (PIP) UI and states.
 */
public class TvPipController implements PipTransitionController.PipTransitionCallback,
        TvPipBoundsController.PipBoundsListener, TvPipMenuController.Delegate,
        DisplayController.OnDisplaysChangedListener, ConfigurationChangeListener,
        UserChangeListener {
    private static final String TAG = "TvPipController";

    private static final int NONEXISTENT_TASK_ID = -1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"STATE_"}, value = {
            STATE_NO_PIP,
            STATE_PIP,
            STATE_PIP_MENU,
    })
    public @interface State {
    }

    /**
     * State when there is no applications in Pip.
     */
    private static final int STATE_NO_PIP = 0;
    /**
     * State when there is an applications in Pip and the Pip window located at its "normal" place
     * (usually the bottom right corner).
     */
    private static final int STATE_PIP = 1;
    /**
     * State when there is an applications in Pip and the Pip menu is open. In this state Pip window
     * is usually moved from its "normal" position on the screen to the "menu" position - which is
     * often at the middle of the screen, and gets slightly scaled up.
     */
    private static final int STATE_PIP_MENU = 2;

    static final String ACTION_SHOW_PIP_MENU =
            "com.android.wm.shell.pip.tv.notification.action.SHOW_PIP_MENU";
    static final String ACTION_CLOSE_PIP =
            "com.android.wm.shell.pip.tv.notification.action.CLOSE_PIP";
    static final String ACTION_MOVE_PIP =
            "com.android.wm.shell.pip.tv.notification.action.MOVE_PIP";
    static final String ACTION_TOGGLE_EXPANDED_PIP =
            "com.android.wm.shell.pip.tv.notification.action.TOGGLE_EXPANDED_PIP";
    static final String ACTION_TO_FULLSCREEN =
            "com.android.wm.shell.pip.tv.notification.action.FULLSCREEN";

    private final Context mContext;

    private final ShellController mShellController;
    private final TvPipBoundsState mTvPipBoundsState;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final TvPipBoundsAlgorithm mTvPipBoundsAlgorithm;
    private final TvPipBoundsController mTvPipBoundsController;
    private final PipTransitionState mPipTransitionState;
    private final PipAppOpsListener mAppOpsListener;
    private final PipTaskOrganizer mPipTaskOrganizer;
    private final PipMediaController mPipMediaController;
    private final TvPipActionsProvider mTvPipActionsProvider;
    private final TvPipNotificationController mPipNotificationController;
    private final TvPipMenuController mTvPipMenuController;
    private final PipTransitionController mPipTransitionController;
    private final TaskStackListenerImpl mTaskStackListener;
    private final PipParamsChangedForwarder mPipParamsChangedForwarder;
    private final DisplayController mDisplayController;
    private final WindowManagerShellWrapper mWmShellWrapper;
    private final ShellExecutor mMainExecutor;
    private final Handler mMainHandler; // For registering the broadcast receiver
    private final TvPipImpl mImpl = new TvPipImpl();

    private final ActionBroadcastReceiver mActionBroadcastReceiver;

    @State
    private int mState = STATE_NO_PIP;
    private int mPinnedTaskId = NONEXISTENT_TASK_ID;

    // How long the shell will wait for the app to close the PiP if a custom action is set.
    private int mPipForceCloseDelay;

    private int mResizeAnimationDuration;
    private int mEduTextWindowExitAnimationDuration;

    public static Pip create(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            TvPipBoundsState tvPipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            TvPipBoundsController tvPipBoundsController,
            PipTransitionState pipTransitionState,
            PipAppOpsListener pipAppOpsListener,
            PipTaskOrganizer pipTaskOrganizer,
            PipTransitionController pipTransitionController,
            TvPipMenuController tvPipMenuController,
            PipMediaController pipMediaController,
            TvPipNotificationController pipNotificationController,
            TaskStackListenerImpl taskStackListener,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            DisplayController displayController,
            WindowManagerShellWrapper wmShell,
            Handler mainHandler,
            ShellExecutor mainExecutor) {
        return new TvPipController(
                context,
                shellInit,
                shellController,
                tvPipBoundsState,
                pipDisplayLayoutState,
                tvPipBoundsAlgorithm,
                tvPipBoundsController,
                pipTransitionState,
                pipAppOpsListener,
                pipTaskOrganizer,
                pipTransitionController,
                tvPipMenuController,
                pipMediaController,
                pipNotificationController,
                taskStackListener,
                pipParamsChangedForwarder,
                displayController,
                wmShell,
                mainHandler,
                mainExecutor).mImpl;
    }

    private TvPipController(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            TvPipBoundsState tvPipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            TvPipBoundsController tvPipBoundsController,
            PipTransitionState pipTransitionState,
            PipAppOpsListener pipAppOpsListener,
            PipTaskOrganizer pipTaskOrganizer,
            PipTransitionController pipTransitionController,
            TvPipMenuController tvPipMenuController,
            PipMediaController pipMediaController,
            TvPipNotificationController pipNotificationController,
            TaskStackListenerImpl taskStackListener,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            DisplayController displayController,
            WindowManagerShellWrapper wmShellWrapper,
            Handler mainHandler,
            ShellExecutor mainExecutor) {
        mContext = context;
        mPipTransitionState = pipTransitionState;
        mMainHandler = mainHandler;
        mMainExecutor = mainExecutor;
        mShellController = shellController;
        mDisplayController = displayController;

        mTvPipBoundsState = tvPipBoundsState;

        DisplayLayout layout = new DisplayLayout(context, context.getDisplay());
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDisplayLayoutState.setDisplayLayout(layout);
        mPipDisplayLayoutState.setDisplayId(context.getDisplayId());

        mTvPipBoundsAlgorithm = tvPipBoundsAlgorithm;
        mTvPipBoundsController = tvPipBoundsController;
        mTvPipBoundsController.setListener(this);

        mPipMediaController = pipMediaController;
        mTvPipActionsProvider = new TvPipActionsProvider(context, pipMediaController,
                this::executeAction);

        mPipNotificationController = pipNotificationController;
        mPipNotificationController.setTvPipActionsProvider(mTvPipActionsProvider);

        mTvPipMenuController = tvPipMenuController;
        mTvPipMenuController.setDelegate(this);
        mTvPipMenuController.setTvPipActionsProvider(mTvPipActionsProvider);

        mActionBroadcastReceiver = new ActionBroadcastReceiver();

        mAppOpsListener = pipAppOpsListener;
        mPipTaskOrganizer = pipTaskOrganizer;
        mPipTransitionController = pipTransitionController;
        mPipParamsChangedForwarder = pipParamsChangedForwarder;
        mTaskStackListener = taskStackListener;
        mWmShellWrapper = wmShellWrapper;
        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mPipTransitionController.registerPipTransitionCallback(this);

        reloadResources();

        registerPipParamsChangedListener(mPipParamsChangedForwarder);
        registerTaskStackListenerCallback(mTaskStackListener);
        registerWmShellPinnedStackListener(mWmShellWrapper);
        registerSessionListenerForCurrentUser();
        mDisplayController.addDisplayWindowListener(this);

        mShellController.addConfigurationChangeListener(this);
        mShellController.addUserChangeListener(this);
    }

    @Override
    public void onUserChanged(int newUserId, @NonNull Context userContext) {
        // Re-register the media session listener when switching users
        registerSessionListenerForCurrentUser();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onConfigurationChanged(), state=%s", TAG, stateToName(mState));

        int previousDefaultGravityX = mTvPipBoundsState.getDefaultGravity()
                & Gravity.HORIZONTAL_GRAVITY_MASK;

        reloadResources();

        mPipNotificationController.onConfigurationChanged();
        mTvPipBoundsAlgorithm.onConfigurationChanged(mContext);
        mTvPipBoundsState.onConfigurationChanged();
        mPipDisplayLayoutState.onConfigurationChanged();

        int defaultGravityX = mTvPipBoundsState.getDefaultGravity()
                & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (isPipShown() && previousDefaultGravityX != defaultGravityX) {
            movePipToOppositeSide();
        }
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onDensityOrFontScaleChanged()", TAG);
        updatePinnedStackBounds();
        mTvPipMenuController.reloadMenu();
    }

    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onDisplayConfigurationChanged(), displayId %d, saved display id %d",
                TAG, displayId, mPipDisplayLayoutState.getDisplayId());
        mPipDisplayLayoutState.setDisplayLayout(
                new DisplayLayout(mContext, mContext.getDisplay()));
        mPipDisplayLayoutState.setDisplayId(mContext.getDisplayId());
    }

    private void reloadResources() {
        final Resources res = mContext.getResources();
        mResizeAnimationDuration = res.getInteger(R.integer.config_pipResizeAnimationDuration);
        mPipForceCloseDelay = res.getInteger(R.integer.config_pipForceCloseDelay);
        mEduTextWindowExitAnimationDuration =
                res.getInteger(R.integer.pip_edu_text_window_exit_animation_duration);
    }

    private void movePipToOppositeSide() {
        ProtoLog.i(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: movePipToOppositeSide", TAG);
        if ((mTvPipBoundsState.getTvPipGravity() & Gravity.RIGHT) == Gravity.RIGHT) {
            movePip(KEYCODE_DPAD_LEFT);
        } else if ((mTvPipBoundsState.getTvPipGravity() & Gravity.LEFT) == Gravity.LEFT) {
            movePip(KEYCODE_DPAD_RIGHT);
        }
    }

    /**
     * Returns {@code true} if Pip is shown.
     */
    private boolean isPipShown() {
        return mState != STATE_NO_PIP;
    }

    /**
     * Starts the process if bringing up the Pip menu if by issuing a command to move Pip
     * task/window to the "Menu" position. We'll show the actual Menu UI (eg. actions) once the Pip
     * task/window is properly positioned in {@link #onPipTransitionFinished(int)}.
     *
     * @param moveMenu If true, show the moveMenu, otherwise show the regular menu.
     */
    private void showPictureInPictureMenu(boolean moveMenu) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: showPictureInPictureMenu(), state=%s", TAG, stateToName(mState));

        if (mState == STATE_NO_PIP) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s:  > cannot open Menu from the current state.", TAG);
            return;
        }

        setState(STATE_PIP_MENU);
        if (moveMenu) {
            mTvPipMenuController.showMovementMenu();
        } else {
            mTvPipMenuController.showMenu();
        }
        updatePinnedStackBounds();
    }

    @Override
    public void onMenuClosed() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: closeMenu(), state before=%s", TAG, stateToName(mState));
        setState(STATE_PIP);
        updatePinnedStackBounds();
    }

    @Override
    public void onInMoveModeChanged() {
        updatePinnedStackBounds();
    }

    /**
     * Opens the "Pip-ed" Activity fullscreen.
     */
    private void movePipToFullscreen() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: movePipToFullscreen(), state=%s", TAG, stateToName(mState));

        mPipTaskOrganizer.exitPip(mResizeAnimationDuration, false /* requestEnterSplit */);
    }

    private void togglePipExpansion() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: togglePipExpansion()", TAG);
        boolean expanding = !mTvPipBoundsState.isTvPipExpanded();
        mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(expanding);
        mTvPipBoundsState.setTvPipManuallyCollapsed(!expanding);
        mTvPipBoundsState.setTvPipExpanded(expanding);

        updatePinnedStackBounds();
    }

    @Override
    public void movePip(int keycode) {
        if (mTvPipBoundsAlgorithm.updateGravity(keycode)) {
            mTvPipMenuController.updateGravity(mTvPipBoundsState.getTvPipGravity());
            updatePinnedStackBounds();
        } else {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Position hasn't changed", TAG);
        }
    }

    @Override
    public void onKeepClearAreasChanged(int displayId, Set<Rect> restricted,
            Set<Rect> unrestricted) {
        if (mPipDisplayLayoutState.getDisplayId() == displayId) {
            boolean unrestrictedAreasChanged = !Objects.equals(unrestricted,
                    mTvPipBoundsState.getUnrestrictedKeepClearAreas());
            mTvPipBoundsState.setKeepClearAreas(restricted, unrestricted);
            updatePinnedStackBounds(mResizeAnimationDuration, unrestrictedAreasChanged);
        }
    }

    private void updatePinnedStackBounds() {
        updatePinnedStackBounds(mResizeAnimationDuration, true);
    }

    /**
     * Update the PiP bounds based on the state of the PiP and keep clear areas.
     */
    private void updatePinnedStackBounds(int animationDuration, boolean immediate) {
        if (mState == STATE_NO_PIP) {
            return;
        }
        final boolean stayAtAnchorPosition = mTvPipMenuController.isInMoveMode();
        final boolean disallowStashing = mState == STATE_PIP_MENU || stayAtAnchorPosition;
        mTvPipBoundsController.recalculatePipBounds(stayAtAnchorPosition, disallowStashing,
                animationDuration, immediate);
    }

    @Override
    public void onPipTargetBoundsChange(Rect targetBounds, int animationDuration) {
        if (!mPipTransitionState.hasEnteredPip()) {
            // Do not schedule a move animation while we're still transitioning into/out of PiP
            return;
        }

        mPipTaskOrganizer.scheduleAnimateResizePip(targetBounds,
                animationDuration, null);
        mTvPipMenuController.onPipTransitionToTargetBoundsStarted(targetBounds);
    }

    /**
     * Closes Pip window.
     */
    public void closePip() {
        closeCurrentPiP(mPinnedTaskId);
    }

    /**
     * Force close the current PiP after some time in case the custom action hasn't done it by
     * itself.
     */
    public void customClosePip() {
        mMainExecutor.executeDelayed(() -> closeCurrentPiP(mPinnedTaskId), mPipForceCloseDelay);
    }

    private void closeCurrentPiP(int pinnedTaskId) {
        if (mPinnedTaskId != pinnedTaskId) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: PiP has already been closed by custom close action", TAG);
            return;
        }
        mPipTaskOrganizer.removePip();
        mTvPipMenuController.closeMenu();
    }

    @Override
    public void closeEduText() {
        updatePinnedStackBounds(mEduTextWindowExitAnimationDuration, false);
    }

    private void registerSessionListenerForCurrentUser() {
        mPipMediaController.registerSessionListenerForCurrentUser();
    }

    private void checkIfPinnedTaskIsGone() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onTaskStackChanged()", TAG);

        if (isPipShown() && getPinnedTaskInfo() == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Pinned task is gone.", TAG);
            onPipDisappeared();
        }
    }

    private void onPipDisappeared() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onPipDisappeared() state=%s", TAG, stateToName(mState));

        mPipNotificationController.dismiss();
        mActionBroadcastReceiver.unregister();

        mTvPipMenuController.detach();
        mTvPipActionsProvider.reset();
        mTvPipBoundsState.resetTvPipState();
        mTvPipBoundsController.reset();
        setState(STATE_NO_PIP);
        mPinnedTaskId = NONEXISTENT_TASK_ID;
    }

    @Override
    public void onPipTransitionStarted(int direction, Rect currentPipBounds) {
        final boolean enterPipTransition = PipAnimationController.isInPipDirection(direction);
        if (enterPipTransition && mState == STATE_NO_PIP) {
            // Set the initial ability to expand the PiP when entering PiP.
            updateExpansionState();
        }
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onPipTransition_Started(), state=%s, direction=%d",
                TAG, stateToName(mState), direction);
    }

    @Override
    public void onPipTransitionCanceled(int direction) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onPipTransition_Canceled(), state=%s", TAG, stateToName(mState));
        mTvPipActionsProvider.updatePipExpansionState(mTvPipBoundsState.isTvPipExpanded());
    }

    @Override
    public void onPipTransitionFinished(int direction) {
        final boolean enterPipTransition = PipAnimationController.isInPipDirection(direction);
        if (enterPipTransition && mState == STATE_NO_PIP) {
            setState(STATE_PIP);
        }
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: onPipTransition_Finished(), state=%s, direction=%d",
                TAG, stateToName(mState), direction);
        mTvPipMenuController.onPipTransitionFinished(enterPipTransition);
        mTvPipActionsProvider.updatePipExpansionState(mTvPipBoundsState.isTvPipExpanded());
    }

    private void updateExpansionState() {
        mTvPipActionsProvider.updateExpansionEnabled(mTvPipBoundsState.isTvExpandedPipSupported()
                && mTvPipBoundsState.getDesiredTvExpandedAspectRatio() != 0);
    }

    private void setState(@State int state) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: setState(), state=%s, prev=%s",
                TAG, stateToName(state), stateToName(mState));
        mState = state;
    }

    private void registerTaskStackListenerCallback(TaskStackListenerImpl taskStackListener) {
        taskStackListener.addListener(new TaskStackListenerCallback() {
            @Override
            public void onActivityPinned(String packageName, int userId, int taskId, int stackId) {
                final TaskInfo pinnedTask = getPinnedTaskInfo();
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: onActivityPinned(), task=%s", TAG, pinnedTask);
                if (pinnedTask == null || pinnedTask.topActivity == null) return;
                mPinnedTaskId = pinnedTask.taskId;

                mPipMediaController.onActivityPinned();
                mActionBroadcastReceiver.register();
                mPipNotificationController.show(pinnedTask.topActivity.getPackageName());
                mTvPipBoundsController.reset();
                mAppOpsListener.onActivityPinned(packageName);
            }

            @Override
            public void onActivityUnpinned() {
                mAppOpsListener.onActivityUnpinned();
            }

            @Override
            public void onTaskStackChanged() {
                checkIfPinnedTaskIsGone();
            }

            @Override
            public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                    boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
                if (task.getWindowingMode() == WINDOWING_MODE_PINNED) {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: onPinnedActivityRestartAttempt()", TAG);
                    // If the "Pip-ed" Activity is launched again by Launcher or intent, make it
                    // fullscreen.
                    movePipToFullscreen();
                }
            }
        });
    }

    private void registerPipParamsChangedListener(PipParamsChangedForwarder provider) {
        provider.addListener(new PipParamsChangedForwarder.PipParamsChangedCallback() {
            @Override
            public void onActionsChanged(List<RemoteAction> actions,
                    RemoteAction closeAction) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: onActionsChanged()", TAG);

                mTvPipActionsProvider.setAppActions(actions, closeAction);
            }

            @Override
            public void onAspectRatioChanged(float ratio) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: onAspectRatioChanged: %f", TAG, ratio);

                mTvPipBoundsState.setAspectRatio(ratio);
                if (!mTvPipBoundsState.isTvPipExpanded()) {
                    updatePinnedStackBounds();
                }
            }

            @Override
            public void onExpandedAspectRatioChanged(float ratio) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: onExpandedAspectRatioChanged: %f", TAG, ratio);

                mTvPipBoundsState.setDesiredTvExpandedAspectRatio(ratio, false);
                updateExpansionState();

                // 1) PiP is expanded and only aspect ratio changed, but wasn't disabled
                // --> update bounds, but don't toggle
                if (mTvPipBoundsState.isTvPipExpanded() && ratio != 0) {
                    mTvPipBoundsAlgorithm.updateExpandedPipSize();
                    updatePinnedStackBounds();
                }

                // 2) PiP is expanded, but expanded PiP was disabled
                // --> collapse PiP
                if (mTvPipBoundsState.isTvPipExpanded() && ratio == 0) {
                    mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(/* expanding= */ false);
                    mTvPipBoundsState.setTvPipExpanded(false);
                    updatePinnedStackBounds();
                }

                // 3) PiP not expanded and not manually collapsed and expand was enabled
                // --> expand to new ratio
                if (!mTvPipBoundsState.isTvPipExpanded() && ratio != 0
                        && !mTvPipBoundsState.isTvPipManuallyCollapsed()) {
                    mTvPipBoundsAlgorithm.updateExpandedPipSize();
                    mTvPipBoundsAlgorithm.updateGravityOnExpansionToggled(/* expanding= */ true);
                    mTvPipBoundsState.setTvPipExpanded(true);
                    updatePinnedStackBounds();
                }
            }
        });
    }

    private void registerWmShellPinnedStackListener(WindowManagerShellWrapper wmShell) {
        try {
            wmShell.addPinnedStackListener(new PinnedStackListenerForwarder.PinnedTaskListener() {
                @Override
                public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: onImeVisibilityChanged(), visible=%b, height=%d",
                            TAG, imeVisible, imeHeight);

                    if (imeVisible == mTvPipBoundsState.isImeShowing()
                            && (!imeVisible || imeHeight == mTvPipBoundsState.getImeHeight())) {
                        // Nothing changed: either IME has been and remains invisible, or remains
                        // visible with the same height.
                        return;
                    }
                    mTvPipBoundsState.setImeVisibility(imeVisible, imeHeight);

                    if (mState != STATE_NO_PIP) {
                        updatePinnedStackBounds();
                    }
                }
            });
        } catch (RemoteException e) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Failed to register pinned stack listener, %s", TAG, e);
        }
    }

    private static TaskInfo getPinnedTaskInfo() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: getPinnedTaskInfo()", TAG);
        try {
            final TaskInfo taskInfo = ActivityTaskManager.getService().getRootTaskInfo(
                    WINDOWING_MODE_PINNED, ACTIVITY_TYPE_UNDEFINED);
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: taskInfo=%s", TAG, taskInfo);
            return taskInfo;
        } catch (RemoteException e) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: getRootTaskInfo() failed, %s", TAG, e);
            return null;
        }
    }

    private static String stateToName(@State int state) {
        switch (state) {
            case STATE_NO_PIP:
                return "NO_PIP";
            case STATE_PIP:
                return "PIP";
            case STATE_PIP_MENU:
                return "PIP_MENU";
            default:
                // This can't happen.
                throw new IllegalArgumentException("Unknown state " + state);
        }
    }

    private void executeAction(@TvPipAction.ActionType int actionType) {
        switch (actionType) {
            case TvPipAction.ACTION_FULLSCREEN:
                movePipToFullscreen();
                break;
            case TvPipAction.ACTION_CLOSE:
                closePip();
                break;
            case TvPipAction.ACTION_MOVE:
                showPictureInPictureMenu(/* moveMenu= */ true);
                break;
            case TvPipAction.ACTION_CUSTOM_CLOSE:
                customClosePip();
                break;
            case TvPipAction.ACTION_EXPAND_COLLAPSE:
                togglePipExpansion();
                break;
            default:
                // NOOP
                break;
        }
    }

    private class ActionBroadcastReceiver extends BroadcastReceiver {
        private static final String SYSTEMUI_PERMISSION = "com.android.systemui.permission.SELF";

        final IntentFilter mIntentFilter;

        {
            mIntentFilter = new IntentFilter();
            mIntentFilter.addAction(ACTION_CLOSE_PIP);
            mIntentFilter.addAction(ACTION_SHOW_PIP_MENU);
            mIntentFilter.addAction(ACTION_MOVE_PIP);
            mIntentFilter.addAction(ACTION_TOGGLE_EXPANDED_PIP);
            mIntentFilter.addAction(ACTION_TO_FULLSCREEN);
        }

        boolean mRegistered = false;

        void register() {
            if (mRegistered) return;

            mContext.registerReceiverForAllUsers(this, mIntentFilter, SYSTEMUI_PERMISSION,
                    mMainHandler, Context.RECEIVER_NOT_EXPORTED);
            mRegistered = true;
        }

        void unregister() {
            if (!mRegistered) return;

            mContext.unregisterReceiver(this);
            mRegistered = false;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: on(Broadcast)Receive(), action=%s", TAG, action);

            if (ACTION_SHOW_PIP_MENU.equals(action)) {
                showPictureInPictureMenu(/* moveMenu= */ false);
            } else {
                executeAction(getCorrespondingActionType(action));
            }
        }

        @TvPipAction.ActionType
        private int getCorrespondingActionType(String broadcast) {
            if (ACTION_CLOSE_PIP.equals(broadcast)) {
                return TvPipAction.ACTION_CLOSE;
            } else if (ACTION_MOVE_PIP.equals(broadcast)) {
                return TvPipAction.ACTION_MOVE;
            } else if (ACTION_TOGGLE_EXPANDED_PIP.equals(broadcast)) {
                return TvPipAction.ACTION_EXPAND_COLLAPSE;
            } else if (ACTION_TO_FULLSCREEN.equals(broadcast)) {
                return TvPipAction.ACTION_FULLSCREEN;
            }

            // Default: handle it like an action we don't know the content of.
            return TvPipAction.ACTION_CUSTOM;
        }
    }

    private class TvPipImpl implements Pip {
        // Not used
    }
}
