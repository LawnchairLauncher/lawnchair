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

package com.android.wm.shell.windowdecor;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.InputDevice.SOURCE_TOUCHSCREEN;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_HOVER_MOVE;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.compatui.AppCompatUtils.isSingleTopActivityTranslucent;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_UNDEFINED;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.ISystemGestureExclusionListener;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.View;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.window.flags.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.common.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.desktopmode.DesktopModeVisualIndicator;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopTasksController.SnapPosition;
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.shared.DesktopModeStatus;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.splitscreen.SplitScreen.StageType;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.KeyguardChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.DesktopModeWindowDecoration.ExclusionRegionListener;
import com.android.wm.shell.windowdecor.extension.TaskInfoKt;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * View model for the window decoration with a caption and shadows. Works with
 * {@link DesktopModeWindowDecoration}.
 */

public class DesktopModeWindowDecorViewModel implements WindowDecorViewModel {
    private static final String TAG = "DesktopModeWindowDecorViewModel";

    private final DesktopModeWindowDecoration.Factory mDesktopModeWindowDecorFactory;
    private final IWindowManager mWindowManager;
    private final ShellExecutor mMainExecutor;
    private final ActivityTaskManager mActivityTaskManager;
    private final ShellCommandHandler mShellCommandHandler;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final ShellController mShellController;
    private final Context mContext;
    private final Handler mMainHandler;
    private final Choreographer mMainChoreographer;
    private final DisplayController mDisplayController;
    private final SyncTransactionQueue mSyncQueue;
    private final DesktopTasksController mDesktopTasksController;
    private final InputManager mInputManager;
    private boolean mTransitionDragActive;

    private SparseArray<EventReceiver> mEventReceiversByDisplay = new SparseArray<>();

    private final ExclusionRegionListener mExclusionRegionListener =
            new ExclusionRegionListenerImpl();

    private final SparseArray<DesktopModeWindowDecoration> mWindowDecorByTaskId;
    private final DragStartListenerImpl mDragStartListener = new DragStartListenerImpl();
    private final InputMonitorFactory mInputMonitorFactory;
    private TaskOperations mTaskOperations;
    private final Supplier<SurfaceControl.Transaction> mTransactionFactory;
    private final Transitions mTransitions;

    private SplitScreenController mSplitScreenController;

    private MoveToDesktopAnimator mMoveToDesktopAnimator;
    private final Rect mDragToDesktopAnimationStartBounds = new Rect();
    private final DesktopModeKeyguardChangeListener mDesktopModeKeyguardChangeListener =
            new DesktopModeKeyguardChangeListener();
    private final RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private final DisplayInsetsController mDisplayInsetsController;
    private final Region mExclusionRegion = Region.obtain();
    private boolean mInImmersiveMode;
    private final String mSysUIPackageName;

    private final ISystemGestureExclusionListener mGestureExclusionListener =
            new ISystemGestureExclusionListener.Stub() {
                @Override
                public void onSystemGestureExclusionChanged(int displayId,
                        Region systemGestureExclusion, Region systemGestureExclusionUnrestricted) {
                    if (mContext.getDisplayId() != displayId) {
                        return;
                    }
                    mMainExecutor.execute(() -> {
                        mExclusionRegion.set(systemGestureExclusion);
                    });
                }
            };

    public DesktopModeWindowDecorViewModel(
            Context context,
            ShellExecutor shellExecutor,
            Handler mainHandler,
            Choreographer mainChoreographer,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            IWindowManager windowManager,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            Optional<DesktopTasksController> desktopTasksController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer
    ) {
        this(
                context,
                shellExecutor,
                mainHandler,
                mainChoreographer,
                shellInit,
                shellCommandHandler,
                windowManager,
                taskOrganizer,
                displayController,
                shellController,
                displayInsetsController,
                syncQueue,
                transitions,
                desktopTasksController,
                new DesktopModeWindowDecoration.Factory(),
                new InputMonitorFactory(),
                SurfaceControl.Transaction::new,
                rootTaskDisplayAreaOrganizer,
                new SparseArray<>());
    }

    @VisibleForTesting
    DesktopModeWindowDecorViewModel(
            Context context,
            ShellExecutor shellExecutor,
            Handler mainHandler,
            Choreographer mainChoreographer,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            IWindowManager windowManager,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            Optional<DesktopTasksController> desktopTasksController,
            DesktopModeWindowDecoration.Factory desktopModeWindowDecorFactory,
            InputMonitorFactory inputMonitorFactory,
            Supplier<SurfaceControl.Transaction> transactionFactory,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            SparseArray<DesktopModeWindowDecoration> windowDecorByTaskId) {
        mContext = context;
        mMainExecutor = shellExecutor;
        mMainHandler = mainHandler;
        mMainChoreographer = mainChoreographer;
        mActivityTaskManager = mContext.getSystemService(ActivityTaskManager.class);
        mTaskOrganizer = taskOrganizer;
        mShellController = shellController;
        mDisplayController = displayController;
        mDisplayInsetsController = displayInsetsController;
        mSyncQueue = syncQueue;
        mTransitions = transitions;
        mDesktopTasksController = desktopTasksController.get();
        mShellCommandHandler = shellCommandHandler;
        mWindowManager = windowManager;
        mDesktopModeWindowDecorFactory = desktopModeWindowDecorFactory;
        mInputMonitorFactory = inputMonitorFactory;
        mTransactionFactory = transactionFactory;
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
        mInputManager = mContext.getSystemService(InputManager.class);
        mWindowDecorByTaskId = windowDecorByTaskId;
        mSysUIPackageName = mContext.getResources().getString(
                com.android.internal.R.string.config_systemUi);

        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mShellController.addKeyguardChangeListener(mDesktopModeKeyguardChangeListener);
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mDisplayInsetsController.addInsetsChangedListener(mContext.getDisplayId(),
                new DesktopModeOnInsetsChangedListener());
        mDesktopTasksController.setOnTaskResizeAnimationListener(
                new DeskopModeOnTaskResizeAnimationListener());
        try {
            mWindowManager.registerSystemGestureExclusionListener(mGestureExclusionListener,
                    mContext.getDisplayId());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register window manager callbacks", e);
        }
    }

    @Override
    public void setFreeformTaskTransitionStarter(FreeformTaskTransitionStarter transitionStarter) {
        mTaskOperations = new TaskOperations(transitionStarter, mContext, mSyncQueue);
    }

    @Override
    public void setSplitScreenController(SplitScreenController splitScreenController) {
        mSplitScreenController = splitScreenController;
        mSplitScreenController.registerSplitScreenListener(new SplitScreen.SplitScreenListener() {
            @Override
            public void onTaskStageChanged(int taskId, @StageType int stage, boolean visible) {
                if (visible && stage != STAGE_TYPE_UNDEFINED) {
                    DesktopModeWindowDecoration decor = mWindowDecorByTaskId.get(taskId);
                    if (decor != null && DesktopModeStatus.canEnterDesktopMode(mContext)) {
                        mDesktopTasksController.moveToSplit(decor.mTaskInfo);
                    }
                }
            }
        });
    }

    @Override
    public boolean onTaskOpening(
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        if (!shouldShowWindowDecor(taskInfo)) return false;
        createWindowDecoration(taskInfo, taskSurface, startT, finishT);
        return true;
    }

    @Override
    public void onTaskInfoChanged(RunningTaskInfo taskInfo) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) return;
        final RunningTaskInfo oldTaskInfo = decoration.mTaskInfo;

        if (taskInfo.displayId != oldTaskInfo.displayId) {
            removeTaskFromEventReceiver(oldTaskInfo.displayId);
            incrementEventReceiverTasks(taskInfo.displayId);
        }
        decoration.relayout(taskInfo);
    }

    @Override
    public void onTaskVanished(RunningTaskInfo taskInfo) {
        // A task vanishing doesn't necessarily mean the task was closed, it could also mean its
        // windowing mode changed. We're only interested in closing tasks so checking whether
        // its info still exists in the task organizer is one way to disambiguate.
        final boolean closed = mTaskOrganizer.getRunningTaskInfo(taskInfo.taskId) == null;
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "Task Vanished: #%d closed=%b", taskInfo.taskId, closed);
        if (closed) {
            // Destroying the window decoration is usually handled when a TRANSIT_CLOSE transition
            // changes happen, but there are certain cases in which closing tasks aren't included
            // in transitions, such as when a non-visible task is closed. See b/296921167.
            // Destroy the decoration here in case the lack of transition missed it.
            destroyWindowDecoration(taskInfo);
        }
    }

    @Override
    public void onTaskChanging(
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (!shouldShowWindowDecor(taskInfo)) {
            if (decoration != null) {
                destroyWindowDecoration(taskInfo);
            }
            return;
        }

        if (decoration == null) {
            createWindowDecoration(taskInfo, taskSurface, startT, finishT);
        } else {
            decoration.relayout(taskInfo, startT, finishT, false /* applyStartTransactionOnDraw */,
                    false /* shouldSetTaskPositionAndCrop */);
        }
    }

    @Override
    public void onTaskClosing(
            RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) return;

        decoration.relayout(taskInfo, startT, finishT, false /* applyStartTransactionOnDraw */,
                false /* shouldSetTaskPositionAndCrop */);
    }

    @Override
    public void destroyWindowDecoration(RunningTaskInfo taskInfo) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) return;

        decoration.close();
        final int displayId = taskInfo.displayId;
        if (mEventReceiversByDisplay.contains(displayId)) {
            removeTaskFromEventReceiver(displayId);
        }
        // Remove the decoration from the cache last because WindowDecoration#close could still
        // issue CANCEL MotionEvents to touch listeners before its view host is released.
        // See b/327664694.
        mWindowDecorByTaskId.remove(taskInfo.taskId);
    }

    private class DesktopModeTouchEventListener extends GestureDetector.SimpleOnGestureListener
            implements View.OnClickListener, View.OnTouchListener, View.OnLongClickListener,
            View.OnGenericMotionListener, DragDetector.MotionEventHandler {
        private static final int CLOSE_MAXIMIZE_MENU_DELAY_MS = 150;

        private final int mTaskId;
        private final WindowContainerToken mTaskToken;
        private final DragPositioningCallback mDragPositioningCallback;
        private final DragDetector mDragDetector;
        private final GestureDetector mGestureDetector;
        private final int mDisplayId;

        /**
         * Whether to pilfer the next motion event to send cancellations to the windows below.
         * Useful when the caption window is spy and the gesture should be handle by the system
         * instead of by the app for their custom header content.
         */
        private boolean mShouldPilferCaptionEvents;
        private boolean mIsDragging;
        private boolean mTouchscreenInUse;
        private boolean mHasLongClicked;
        private int mDragPointerId = -1;
        private final Runnable mCloseMaximizeWindowRunnable;

        private DesktopModeTouchEventListener(
                RunningTaskInfo taskInfo,
                DragPositioningCallback dragPositioningCallback) {
            mTaskId = taskInfo.taskId;
            mTaskToken = taskInfo.token;
            mDragPositioningCallback = dragPositioningCallback;
            mDragDetector = new DragDetector(this);
            mGestureDetector = new GestureDetector(mContext, this);
            mDisplayId = taskInfo.displayId;
            mCloseMaximizeWindowRunnable = () -> {
                final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
                if (decoration == null) return;
                decoration.closeMaximizeMenu();
            };
        }

        @Override
        public void onClick(View v) {
            if (mIsDragging) {
                mIsDragging = false;
                return;
            }
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
            final int id = v.getId();
            if (id == R.id.close_window) {
                if (isTaskInSplitScreen(mTaskId)) {
                    mSplitScreenController.moveTaskToFullscreen(getOtherSplitTask(mTaskId).taskId,
                            SplitScreenController.EXIT_REASON_DESKTOP_MODE);
                } else {
                    WindowContainerTransaction wct = new WindowContainerTransaction();
                    mDesktopTasksController.onDesktopWindowClose(wct, mTaskId);
                    mTaskOperations.closeTask(mTaskToken, wct);
                }
            } else if (id == R.id.back_button) {
                mTaskOperations.injectBackKey(mDisplayId);
            } else if (id == R.id.caption_handle || id == R.id.open_menu_button) {
                if (!decoration.isHandleMenuActive()) {
                    moveTaskToFront(decoration.mTaskInfo);
                    decoration.createHandleMenu(mSplitScreenController);
                } else {
                    decoration.closeHandleMenu();
                }
            } else if (id == R.id.desktop_button) {
                final WindowContainerTransaction wct = new WindowContainerTransaction();
                // App sometimes draws before the insets from WindowDecoration#relayout have
                // been added, so they must be added here
                mWindowDecorByTaskId.get(mTaskId).addCaptionInset(wct);
                mDesktopTasksController.moveToDesktop(mTaskId, wct,
                        DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON);
                decoration.closeHandleMenu();
            } else if (id == R.id.fullscreen_button) {
                decoration.closeHandleMenu();
                if (isTaskInSplitScreen(mTaskId)) {
                    mSplitScreenController.moveTaskToFullscreen(mTaskId,
                            SplitScreenController.EXIT_REASON_DESKTOP_MODE);
                } else {
                    mDesktopTasksController.moveToFullscreen(mTaskId,
                            DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON);
                }
            } else if (id == R.id.split_screen_button) {
                decoration.closeHandleMenu();
                mDesktopTasksController.requestSplit(decoration.mTaskInfo);
            } else if (id == R.id.collapse_menu_button) {
                decoration.closeHandleMenu();
            } else if (id == R.id.maximize_window) {
                final RunningTaskInfo taskInfo = decoration.mTaskInfo;
                decoration.closeHandleMenu();
                decoration.closeMaximizeMenu();
                mDesktopTasksController.toggleDesktopTaskSize(taskInfo);
            } else if (id == R.id.maximize_menu_maximize_button) {
                final RunningTaskInfo taskInfo = decoration.mTaskInfo;
                mDesktopTasksController.toggleDesktopTaskSize(taskInfo);
                decoration.closeHandleMenu();
                decoration.closeMaximizeMenu();
            } else if (id == R.id.maximize_menu_snap_left_button) {
                final RunningTaskInfo taskInfo = decoration.mTaskInfo;
                mDesktopTasksController.snapToHalfScreen(taskInfo, SnapPosition.LEFT);
                decoration.closeHandleMenu();
                decoration.closeMaximizeMenu();
            } else if (id == R.id.maximize_menu_snap_right_button) {
                final RunningTaskInfo taskInfo = decoration.mTaskInfo;
                mDesktopTasksController.snapToHalfScreen(taskInfo, SnapPosition.RIGHT);
                decoration.closeHandleMenu();
                decoration.closeMaximizeMenu();
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            final int id = v.getId();
            if ((e.getSource() & SOURCE_TOUCHSCREEN) == SOURCE_TOUCHSCREEN) {
                mTouchscreenInUse = e.getActionMasked() != ACTION_UP
                        && e.getActionMasked() != ACTION_CANCEL;
            }
            if (id != R.id.caption_handle && id != R.id.desktop_mode_caption
                    && id != R.id.open_menu_button && id != R.id.close_window
                    && id != R.id.maximize_window) {
                return false;
            }
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
            moveTaskToFront(decoration.mTaskInfo);

            final int actionMasked = e.getActionMasked();
            final boolean isDown = actionMasked == MotionEvent.ACTION_DOWN;
            final boolean isUpOrCancel = actionMasked == MotionEvent.ACTION_CANCEL
                    || actionMasked == MotionEvent.ACTION_UP;
            if (isDown) {
                final boolean downInCustomizableCaptionRegion =
                        decoration.checkTouchEventInCustomizableRegion(e);
                final boolean downInExclusionRegion = mExclusionRegion.contains(
                        (int) e.getRawX(), (int) e.getRawY());
                final boolean isTransparentCaption =
                        TaskInfoKt.isTransparentCaptionBarAppearance(decoration.mTaskInfo);
                // MotionEvent's coordinates are relative to view, we want location in window
                // to offset position relative to caption as a whole.
                int[] viewLocation = new int[2];
                v.getLocationInWindow(viewLocation);
                final boolean isResizeEvent = decoration.shouldResizeListenerHandleEvent(e,
                        new Point(viewLocation[0], viewLocation[1]));
                // The caption window may be a spy window when the caption background is
                // transparent, which means events will fall through to the app window. Make
                // sure to cancel these events if they do not happen in the intersection of the
                // customizable region and what the app reported as exclusion areas, because
                // the drag-move or other caption gestures should take priority outside those
                // regions.
                mShouldPilferCaptionEvents = !(downInCustomizableCaptionRegion
                        && downInExclusionRegion && isTransparentCaption) && !isResizeEvent;
            }

            if (!mShouldPilferCaptionEvents) {
                // The event will be handled by a window below or pilfered by resize handler.
                return false;
            }
            // Otherwise pilfer so that windows below receive cancellations for this gesture, and
            // continue normal handling as a caption gesture.
            if (mInputManager != null) {
                mInputManager.pilferPointers(v.getViewRootImpl().getInputToken());
            }
            if (isUpOrCancel) {
                // Gesture is finished, reset state.
                mShouldPilferCaptionEvents = false;
            }
            if (!mHasLongClicked && id != R.id.maximize_window) {
                decoration.closeMaximizeMenuIfNeeded(e);
            }
            return mDragDetector.onMotionEvent(v, e);
        }

        @Override
        public boolean onLongClick(View v) {
            final int id = v.getId();
            if (id == R.id.maximize_window && mTouchscreenInUse) {
                final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
                moveTaskToFront(decoration.mTaskInfo);
                if (decoration.isMaximizeMenuActive()) {
                    decoration.closeMaximizeMenu();
                } else {
                    mHasLongClicked = true;
                    decoration.createMaximizeMenu();
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onGenericMotion(View v, MotionEvent ev) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
            final int id = v.getId();
            if (ev.getAction() == ACTION_HOVER_ENTER) {
                if (!decoration.isMaximizeMenuActive() && id == R.id.maximize_window) {
                    decoration.onMaximizeWindowHoverEnter();
                } else if (id == R.id.maximize_window
                        || MaximizeMenu.Companion.isMaximizeMenuView(id)) {
                    // Re-hovering over any of the maximize menu views should keep the menu open by
                    // cancelling any attempts to close the menu.
                    mMainHandler.removeCallbacks(mCloseMaximizeWindowRunnable);
                    if (id != R.id.maximize_window) {
                        decoration.onMaximizeMenuHoverEnter(id, ev);
                    }
                }
                return true;
            } else if (ev.getAction() == ACTION_HOVER_MOVE
                    && MaximizeMenu.Companion.isMaximizeMenuView(id)) {
                decoration.onMaximizeMenuHoverMove(id, ev);
                mMainHandler.removeCallbacks(mCloseMaximizeWindowRunnable);
            } else if (ev.getAction() == ACTION_HOVER_EXIT) {
                if (!decoration.isMaximizeMenuActive() && id == R.id.maximize_window) {
                    decoration.onMaximizeWindowHoverExit();
                } else if (id == R.id.maximize_window
                        || MaximizeMenu.Companion.isMaximizeMenuView(id)) {
                    // Close menu if not hovering over maximize menu or maximize button after a
                    // delay to give user a chance to re-enter view or to move from one maximize
                    // menu view to another.
                    mMainHandler.postDelayed(mCloseMaximizeWindowRunnable,
                            CLOSE_MAXIMIZE_MENU_DELAY_MS);
                    if (id != R.id.maximize_window) {
                        decoration.onMaximizeMenuHoverExit(id, ev);
                    }
                }
                return true;
            }
            return false;
        }

        private void moveTaskToFront(RunningTaskInfo taskInfo) {
            if (!taskInfo.isFocused) {
                mDesktopTasksController.moveTaskToFront(taskInfo);
            }
        }

        /**
         * @param e {@link MotionEvent} to process
         * @return {@code true} if the motion event is handled.
         */
        @Override
        public boolean handleMotionEvent(@Nullable View v, MotionEvent e) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
            final RunningTaskInfo taskInfo = decoration.mTaskInfo;
            if (DesktopModeStatus.canEnterDesktopMode(mContext)
                    && taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
                return false;
            }
            if (mGestureDetector.onTouchEvent(e)) {
                return true;
            }
            final int id = v.getId();
            final boolean touchingButton = (id == R.id.close_window || id == R.id.maximize_window
                    || id == R.id.open_menu_button);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    mDragPointerId = e.getPointerId(0);
                    mDragPositioningCallback.onDragPositioningStart(
                            0 /* ctrlType */, e.getRawX(0),
                            e.getRawY(0));
                    mIsDragging = false;
                    mHasLongClicked = false;
                    // Do not consume input event if a button is touched, otherwise it would
                    // prevent the button's ripple effect from showing.
                    return !touchingButton;
                }
                case ACTION_MOVE: {
                    // If a decor's resize drag zone is active, don't also try to reposition it.
                    if (decoration.isHandlingDragResize()) break;
                    decoration.closeMaximizeMenu();
                    if (e.findPointerIndex(mDragPointerId) == -1) {
                        mDragPointerId = e.getPointerId(0);
                    }
                    final int dragPointerIdx = e.findPointerIndex(mDragPointerId);
                    final Rect newTaskBounds = mDragPositioningCallback.onDragPositioningMove(
                            e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx));
                    mDesktopTasksController.onDragPositioningMove(taskInfo,
                            decoration.mTaskSurface,
                            e.getRawX(dragPointerIdx),
                            newTaskBounds);
                    mIsDragging = true;
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    final boolean wasDragging = mIsDragging;
                    if (!wasDragging) {
                        return false;
                    }
                    if (e.findPointerIndex(mDragPointerId) == -1) {
                        mDragPointerId = e.getPointerId(0);
                    }
                    final int dragPointerIdx = e.findPointerIndex(mDragPointerId);
                    // Position of the task is calculated by subtracting the raw location of the
                    // motion event (the location of the motion relative to the display) by the
                    // location of the motion event relative to the task's bounds
                    final Point position = new Point(
                            (int) (e.getRawX(dragPointerIdx) - e.getX(dragPointerIdx)),
                            (int) (e.getRawY(dragPointerIdx) - e.getY(dragPointerIdx)));
                    final Rect newTaskBounds = mDragPositioningCallback.onDragPositioningEnd(
                            e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx));
                    mDesktopTasksController.onDragPositioningEnd(taskInfo, position,
                            new PointF(e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx)),
                            newTaskBounds, decoration.calculateValidDragArea());
                    if (touchingButton && !mHasLongClicked) {
                        // We need the input event to not be consumed here to end the ripple
                        // effect on the touched button. We will reset drag state in the ensuing
                        // onClick call that results.
                        return false;
                    } else {
                        mIsDragging = false;
                        return true;
                    }
                }
            }
            return true;
        }

        /**
         * Perform a task size toggle on release of the double-tap, assuming no drag event
         * was handled during the double-tap.
         * @param e The motion event that occurred during the double-tap gesture.
         * @return true if the event should be consumed, false if not
         */
        @Override
        public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
            final int action = e.getActionMasked();
            if (mIsDragging || (action != MotionEvent.ACTION_UP
                    && action != MotionEvent.ACTION_CANCEL)) {
                return false;
            }
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
            mDesktopTasksController.toggleDesktopTaskSize(decoration.mTaskInfo);
            return true;
        }
    }

    // InputEventReceiver to listen for touch input outside of caption bounds
    class EventReceiver extends InputEventReceiver {
        private InputMonitor mInputMonitor;
        private int mTasksOnDisplay;
        EventReceiver(InputMonitor inputMonitor, InputChannel channel, Looper looper) {
            super(channel, looper);
            mInputMonitor = inputMonitor;
            mTasksOnDisplay = 1;
        }

        @Override
        public void onInputEvent(InputEvent event) {
            boolean handled = false;
            if (event instanceof MotionEvent) {
                handled = true;
                DesktopModeWindowDecorViewModel.this
                        .handleReceivedMotionEvent((MotionEvent) event, mInputMonitor);
            }
            finishInputEvent(event, handled);
        }

        @Override
        public void dispose() {
            if (mInputMonitor != null) {
                mInputMonitor.dispose();
                mInputMonitor = null;
            }
            super.dispose();
        }

        @Override
        public String toString() {
            return "EventReceiver"
                    + "{"
                    + "tasksOnDisplay="
                    + mTasksOnDisplay
                    + "}";
        }

        private void incrementTaskNumber() {
            mTasksOnDisplay++;
        }

        private void decrementTaskNumber() {
            mTasksOnDisplay--;
        }

        private int getTasksOnDisplay() {
            return mTasksOnDisplay;
        }
    }

    /**
     * Check if an EventReceiver exists on a particular display.
     * If it does, increment its task count. Otherwise, create one for that display.
     * @param displayId the display to check against
     */
    private void incrementEventReceiverTasks(int displayId) {
        if (mEventReceiversByDisplay.contains(displayId)) {
            final EventReceiver eventReceiver = mEventReceiversByDisplay.get(displayId);
            eventReceiver.incrementTaskNumber();
        } else {
            createInputChannel(displayId);
        }
    }

    // If all tasks on this display are gone, we don't need to monitor its input.
    private void removeTaskFromEventReceiver(int displayId) {
        if (!mEventReceiversByDisplay.contains(displayId)) return;
        final EventReceiver eventReceiver = mEventReceiversByDisplay.get(displayId);
        if (eventReceiver == null) return;
        eventReceiver.decrementTaskNumber();
        if (eventReceiver.getTasksOnDisplay() == 0) {
            disposeInputChannel(displayId);
        }
    }

    /**
     * Handle MotionEvents relevant to focused task's caption that don't directly touch it
     *
     * @param ev the {@link MotionEvent} received by {@link EventReceiver}
     */
    private void handleReceivedMotionEvent(MotionEvent ev, InputMonitor inputMonitor) {
        final DesktopModeWindowDecoration relevantDecor = getRelevantWindowDecor(ev);
        if (DesktopModeStatus.canEnterDesktopMode(mContext)) {
            if (!mInImmersiveMode && (relevantDecor == null
                    || relevantDecor.mTaskInfo.getWindowingMode() != WINDOWING_MODE_FREEFORM
                    || mTransitionDragActive)) {
                handleCaptionThroughStatusBar(ev, relevantDecor);
            }
        }
        handleEventOutsideCaption(ev, relevantDecor);
        // Prevent status bar from reacting to a caption drag.
        if (DesktopModeStatus.canEnterDesktopMode(mContext)) {
            if (mTransitionDragActive) {
                inputMonitor.pilferPointers();
            }
        }
    }

    /**
     * If an UP/CANCEL action is received outside of the caption bounds, close the handle and
     * maximize the menu.
     *
     * @param relevantDecor the window decoration of the focused task's caption. This method only
     *                      handles motion events outside this caption's bounds.
     */
    private void handleEventOutsideCaption(MotionEvent ev,
            DesktopModeWindowDecoration relevantDecor) {
        // Returns if event occurs within caption
        if (relevantDecor == null || relevantDecor.checkTouchEventInCaption(ev)) {
            return;
        }
        relevantDecor.updateHoverAndPressStatus(ev);
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!mTransitionDragActive) {
                relevantDecor.closeHandleMenuIfNeeded(ev);
                relevantDecor.closeMaximizeMenuIfNeeded(ev);
            }
        }
    }


    /**
     * Perform caption actions if not able to through normal means.
     * Turn on desktop mode if handle is dragged below status bar.
     */
    private void handleCaptionThroughStatusBar(MotionEvent ev,
            DesktopModeWindowDecoration relevantDecor) {
        if (relevantDecor == null) {
            if (ev.getActionMasked() == ACTION_UP) {
                mMoveToDesktopAnimator = null;
                mTransitionDragActive = false;
            }
            return;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_EXIT:
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_HOVER_ENTER: {
                relevantDecor.updateHoverAndPressStatus(ev);
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                // Begin drag through status bar if applicable.
                relevantDecor.checkTouchEvent(ev);
                relevantDecor.updateHoverAndPressStatus(ev);
                mDragToDesktopAnimationStartBounds.set(
                        relevantDecor.mTaskInfo.configuration.windowConfiguration.getBounds());
                boolean dragFromStatusBarAllowed = false;
                if (DesktopModeStatus.canEnterDesktopMode(mContext)) {
                    // In proto2 any full screen or multi-window task can be dragged to
                    // freeform.
                    final int windowingMode = relevantDecor.mTaskInfo.getWindowingMode();
                    dragFromStatusBarAllowed = windowingMode == WINDOWING_MODE_FULLSCREEN
                            || windowingMode == WINDOWING_MODE_MULTI_WINDOW;
                }

                if (dragFromStatusBarAllowed
                        && relevantDecor.checkTouchEventInFocusedCaptionHandle(ev)) {
                    mTransitionDragActive = true;
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (mTransitionDragActive) {
                    mDesktopTasksController.updateVisualIndicator(relevantDecor.mTaskInfo,
                            relevantDecor.mTaskSurface, ev.getRawX(), ev.getRawY());
                    mTransitionDragActive = false;
                    if (mMoveToDesktopAnimator != null) {
                        // Though this isn't a hover event, we need to update handle's hover state
                        // as it likely will change.
                        relevantDecor.updateHoverAndPressStatus(ev);
                        mDesktopTasksController.onDragPositioningEndThroughStatusBar(
                                new PointF(ev.getRawX(), ev.getRawY()), relevantDecor.mTaskInfo);
                        mMoveToDesktopAnimator = null;
                        return;
                    } else {
                        // In cases where we create an indicator but do not start the
                        // move-to-desktop animation, we need to dismiss it.
                        mDesktopTasksController.releaseVisualIndicator();
                    }
                }
                relevantDecor.checkTouchEvent(ev);
                break;
            }

            case ACTION_MOVE: {
                if (relevantDecor == null) {
                    return;
                }
                if (mTransitionDragActive) {
                    // Do not create an indicator at all if we're not past transition height.
                    DisplayLayout layout = mDisplayController
                            .getDisplayLayout(relevantDecor.mTaskInfo.displayId);
                    if (ev.getRawY() < 2 * layout.stableInsets().top
                            && mMoveToDesktopAnimator == null) {
                        return;
                    }
                    final DesktopModeVisualIndicator.IndicatorType indicatorType =
                            mDesktopTasksController.updateVisualIndicator(
                                    relevantDecor.mTaskInfo,
                                    relevantDecor.mTaskSurface, ev.getRawX(), ev.getRawY());
                    if (indicatorType != TO_FULLSCREEN_INDICATOR) {
                        if (mMoveToDesktopAnimator == null) {
                            mMoveToDesktopAnimator = new MoveToDesktopAnimator(
                                    mContext, mDragToDesktopAnimationStartBounds,
                                    relevantDecor.mTaskInfo, relevantDecor.mTaskSurface);
                            mDesktopTasksController.startDragToDesktop(relevantDecor.mTaskInfo,
                                    mMoveToDesktopAnimator);
                        }
                    }
                    if (mMoveToDesktopAnimator != null) {
                        mMoveToDesktopAnimator.updatePosition(ev);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mTransitionDragActive = false;
                mMoveToDesktopAnimator = null;
            }
        }
    }

    @Nullable
    private DesktopModeWindowDecoration getRelevantWindowDecor(MotionEvent ev) {
        // If we are mid-transition, dragged task's decor is always relevant.
        final int draggedTaskId = mDesktopTasksController.getDraggingTaskId();
        if (draggedTaskId != INVALID_TASK_ID) {
            return mWindowDecorByTaskId.get(draggedTaskId);
        }
        final DesktopModeWindowDecoration focusedDecor = getFocusedDecor();
        if (focusedDecor == null) {
            return null;
        }
        final boolean splitScreenVisible = mSplitScreenController != null
                && mSplitScreenController.isSplitScreenVisible();
        // It's possible that split tasks are visible but neither is focused, such as when there's
        // a fullscreen translucent window on top of them. In that case, the relevant decor should
        // just be that translucent focused window.
        final boolean focusedTaskInSplit = mSplitScreenController != null
                && mSplitScreenController.isTaskInSplitScreen(focusedDecor.mTaskInfo.taskId);
        if (splitScreenVisible && focusedTaskInSplit) {
            // We can't look at focused task here as only one task will have focus.
            DesktopModeWindowDecoration splitTaskDecor = getSplitScreenDecor(ev);
            return splitTaskDecor == null ? getFocusedDecor() : splitTaskDecor;
        } else {
            return getFocusedDecor();
        }
    }

    @Nullable
    private DesktopModeWindowDecoration getSplitScreenDecor(MotionEvent ev) {
        ActivityManager.RunningTaskInfo topOrLeftTask =
                mSplitScreenController.getTaskInfo(SPLIT_POSITION_TOP_OR_LEFT);
        ActivityManager.RunningTaskInfo bottomOrRightTask =
                mSplitScreenController.getTaskInfo(SPLIT_POSITION_BOTTOM_OR_RIGHT);
        if (topOrLeftTask != null && topOrLeftTask.getConfiguration()
                .windowConfiguration.getBounds().contains((int) ev.getX(), (int) ev.getY())) {
            return mWindowDecorByTaskId.get(topOrLeftTask.taskId);
        } else if (bottomOrRightTask != null && bottomOrRightTask.getConfiguration()
                .windowConfiguration.getBounds().contains((int) ev.getX(), (int) ev.getY())) {
            Rect bottomOrRightBounds = bottomOrRightTask.getConfiguration().windowConfiguration
                    .getBounds();
            ev.offsetLocation(-bottomOrRightBounds.left, -bottomOrRightBounds.top);
            return mWindowDecorByTaskId.get(bottomOrRightTask.taskId);
        } else {
            return null;
        }

    }

    @Nullable
    private DesktopModeWindowDecoration getFocusedDecor() {
        final int size = mWindowDecorByTaskId.size();
        DesktopModeWindowDecoration focusedDecor = null;
        // TODO(b/323251951): We need to iterate this in reverse to avoid potentially getting
        //  a decor for a closed task. This is a short term fix while the core issue is addressed,
        //  which involves refactoring the window decor lifecycle to be visibility based.
        for (int i = size - 1; i >= 0; i--) {
            final DesktopModeWindowDecoration decor = mWindowDecorByTaskId.valueAt(i);
            if (decor != null && decor.isFocused()) {
                focusedDecor = decor;
                break;
            }
        }
        return focusedDecor;
    }

    private int getStatusBarHeight(int displayId) {
        return mDisplayController.getDisplayLayout(displayId).stableInsets().top;
    }

    private void createInputChannel(int displayId) {
        final InputManager inputManager = mContext.getSystemService(InputManager.class);
        final InputMonitor inputMonitor =
                mInputMonitorFactory.create(inputManager, mContext);
        final EventReceiver eventReceiver = new EventReceiver(inputMonitor,
                inputMonitor.getInputChannel(), Looper.myLooper());
        mEventReceiversByDisplay.put(displayId, eventReceiver);
    }

    private void disposeInputChannel(int displayId) {
        final EventReceiver eventReceiver = mEventReceiversByDisplay.removeReturnOld(displayId);
        if (eventReceiver != null) {
            eventReceiver.dispose();
        }
    }

    private boolean shouldShowWindowDecor(RunningTaskInfo taskInfo) {
        if (taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM) return true;
        if (mSplitScreenController != null
                && mSplitScreenController.isTaskRootOrStageRoot(taskInfo.taskId)) {
            return false;
        }
        if (mDesktopModeKeyguardChangeListener.isKeyguardVisibleAndOccluded()
                && taskInfo.isFocused) {
            return false;
        }
        // TODO(b/347289970): Consider replacing with API
        if (Flags.enableDesktopWindowingModalsPolicy()
                && isSingleTopActivityTranslucent(taskInfo)) {
            return false;
        }
        if (isSystemUIApplication(taskInfo)) {
            return false;
        }
        return DesktopModeStatus.canEnterDesktopMode(mContext)
                && !DesktopWallpaperActivity.isWallpaperTask(taskInfo)
                && taskInfo.getWindowingMode() != WINDOWING_MODE_PINNED
                && taskInfo.getActivityType() == ACTIVITY_TYPE_STANDARD
                && !taskInfo.configuration.windowConfiguration.isAlwaysOnTop();
    }

    private void createWindowDecoration(
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT) {
        final DesktopModeWindowDecoration oldDecoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (oldDecoration != null) {
            // close the old decoration if it exists to avoid two window decorations being added
            oldDecoration.close();
        }
        final DesktopModeWindowDecoration windowDecoration =
                mDesktopModeWindowDecorFactory.create(
                        mContext,
                        mDisplayController,
                        mTaskOrganizer,
                        taskInfo,
                        taskSurface,
                        mMainHandler,
                        mMainChoreographer,
                        mSyncQueue,
                        mRootTaskDisplayAreaOrganizer);
        mWindowDecorByTaskId.put(taskInfo.taskId, windowDecoration);

        final DragPositioningCallback dragPositioningCallback;
        if (!DesktopModeStatus.isVeiledResizeEnabled()) {
            dragPositioningCallback =  new FluidResizeTaskPositioner(
                    mTaskOrganizer, mTransitions, windowDecoration, mDisplayController,
                    mDragStartListener, mTransactionFactory);
            windowDecoration.setTaskDragResizer(
                    (FluidResizeTaskPositioner) dragPositioningCallback);
        } else {
            dragPositioningCallback =  new VeiledResizeTaskPositioner(
                    mTaskOrganizer, windowDecoration, mDisplayController,
                    mDragStartListener, mTransitions);
            windowDecoration.setTaskDragResizer(
                    (VeiledResizeTaskPositioner) dragPositioningCallback);
        }

        final DesktopModeTouchEventListener touchEventListener =
                new DesktopModeTouchEventListener(taskInfo, dragPositioningCallback);

        windowDecoration.setCaptionListeners(
                touchEventListener, touchEventListener, touchEventListener, touchEventListener);
        windowDecoration.setExclusionRegionListener(mExclusionRegionListener);
        windowDecoration.setDragPositioningCallback(dragPositioningCallback);
        windowDecoration.setDragDetector(touchEventListener.mDragDetector);
        windowDecoration.relayout(taskInfo, startT, finishT,
                false /* applyStartTransactionOnDraw */, false /* shouldSetTaskPositionAndCrop */);
        incrementEventReceiverTasks(taskInfo.displayId);
    }

    private RunningTaskInfo getOtherSplitTask(int taskId) {
        @SplitPosition int remainingTaskPosition = mSplitScreenController
                .getSplitPosition(taskId) == SPLIT_POSITION_BOTTOM_OR_RIGHT
                ? SPLIT_POSITION_TOP_OR_LEFT : SPLIT_POSITION_BOTTOM_OR_RIGHT;
        return mSplitScreenController.getTaskInfo(remainingTaskPosition);
    }

    private boolean isTaskInSplitScreen(int taskId) {
        return mSplitScreenController != null
                && mSplitScreenController.isTaskInSplitScreen(taskId);
    }

    // TODO(b/347289970): Consider replacing with API
    private boolean isSystemUIApplication(RunningTaskInfo taskInfo) {
        if (taskInfo.baseActivity != null) {
            return (Objects.equals(taskInfo.baseActivity.getPackageName(), mSysUIPackageName));
        }
        return false;
    }

    private void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + "DesktopModeWindowDecorViewModel");
        pw.println(innerPrefix + "DesktopModeStatus="
                + DesktopModeStatus.canEnterDesktopMode(mContext));
        pw.println(innerPrefix + "mTransitionDragActive=" + mTransitionDragActive);
        pw.println(innerPrefix + "mEventReceiversByDisplay=" + mEventReceiversByDisplay);
        pw.println(innerPrefix + "mWindowDecorByTaskId=" + mWindowDecorByTaskId);
    }

    private class DeskopModeOnTaskResizeAnimationListener
            implements OnTaskResizeAnimationListener {
        @Override
        public void onAnimationStart(int taskId, Transaction t, Rect bounds) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration == null)  {
                t.apply();
                return;
            }
            decoration.showResizeVeil(t, bounds);
            decoration.setAnimatingTaskResize(true);
        }

        @Override
        public void onBoundsChange(int taskId, Transaction t, Rect bounds) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration == null) return;
            decoration.updateResizeVeil(t, bounds);
        }

        @Override
        public void onAnimationEnd(int taskId) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration == null) return;
            decoration.hideResizeVeil();
            decoration.setAnimatingTaskResize(false);
        }
    }


    private class DragStartListenerImpl
            implements DragPositioningCallbackUtility.DragStartListener {
        @Override
        public void onDragStart(int taskId) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
            decoration.closeHandleMenu();
        }
    }

    static class InputMonitorFactory {
        InputMonitor create(InputManager inputManager, Context context) {
            return inputManager.monitorGestureInput("caption-touch", context.getDisplayId());
        }
    }

    private class ExclusionRegionListenerImpl
            implements ExclusionRegionListener {

        @Override
        public void onExclusionRegionChanged(int taskId, Region region) {
            mDesktopTasksController.onExclusionRegionChanged(taskId, region);
        }

        @Override
        public void onExclusionRegionDismissed(int taskId) {
            mDesktopTasksController.removeExclusionRegionForTask(taskId);
        }
    }

    static class DesktopModeKeyguardChangeListener implements KeyguardChangeListener {
        private boolean mIsKeyguardVisible;
        private boolean mIsKeyguardOccluded;

        @Override
        public void onKeyguardVisibilityChanged(boolean visible, boolean occluded,
                boolean animatingDismiss) {
            mIsKeyguardVisible = visible;
            mIsKeyguardOccluded = occluded;
        }

        public boolean isKeyguardVisibleAndOccluded() {
            return mIsKeyguardVisible && mIsKeyguardOccluded;
        }
    }

    @VisibleForTesting
    class DesktopModeOnInsetsChangedListener implements
            DisplayInsetsController.OnInsetsChangedListener {
        @Override
        public void insetsChanged(InsetsState insetsState) {
            for (int i = 0; i < insetsState.sourceSize(); i++) {
                final InsetsSource source = insetsState.sourceAt(i);
                if (source.getType() != statusBars()) {
                    continue;
                }

                final DesktopModeWindowDecoration decor = getFocusedDecor();
                if (decor == null) {
                    return;
                }
                // If status bar inset is visible, top task is not in immersive mode
                final boolean inImmersiveMode = !source.isVisible();
                // Calls WindowDecoration#relayout if decoration visibility needs to be updated
                if (inImmersiveMode != mInImmersiveMode) {
                    if (Flags.enableDesktopWindowingImmersiveHandleHiding()) {
                        decor.relayout(decor.mTaskInfo);
                    }
                    mInImmersiveMode = inImmersiveMode;
                }

                return;
            }
        }
    }
}


