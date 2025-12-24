/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.view.InputDevice.SOURCE_TOUCHSCREEN;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOW_DECORATION;
import static com.android.wm.shell.windowdecor.DragPositioningCallbackUtility.getInputMethodFromMotionEvent;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.input.InputManager;
import android.os.IBinder;
import android.os.SystemProperties;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewRootImpl;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;
import android.window.WindowContainerToken;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController;
import com.android.wm.shell.desktopmode.DesktopModeEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.ShellDesktopState;
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction;
import com.android.wm.shell.desktopmode.data.DesktopRepository;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel.AppHandleMotionEventHandler;
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel.CaptionTouchStatusListener;
import com.android.wm.shell.windowdecor.common.WindowDecorationGestureExclusionTracker;
import com.android.wm.shell.windowdecor.extension.TaskInfoKt;
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder;

import java.util.function.Function;
import java.util.function.Supplier;

public class DesktopModeTouchEventListener
        extends GestureDetector.SimpleOnGestureListener
        implements View.OnClickListener, View.OnTouchListener, View.OnLongClickListener,
        View.OnGenericMotionListener, DragDetector.MotionEventHandler {
    private static final String TAG = "DesktopModeTouchEventListener";
    private static final long APP_HANDLE_HOLD_TO_DRAG_DURATION_MS = 100;
    private static final long APP_HEADER_HOLD_TO_DRAG_DURATION_MS = 0;

    private static final boolean DEBUG_MOTION_EVENTS = SystemProperties.getBoolean(
            "persist.wm.debug.window_decoration_motion_events_debug", false);

    private final @NonNull Context mContext;
    private final int mTaskId;
    private final WindowContainerToken mTaskToken;
    private final @NonNull DragPositioningCallback mDragPositioningCallback;
    private final @NonNull Function<Integer, WindowDecorationWrapper> mWindowDecorationFinder;
    private final @NonNull DesktopTasksController mDesktopTasksController;
    private final @NonNull TaskOperations mTaskOperations;
    private final @NonNull DesktopModeUiEventLogger mDesktopModeUiEventLogger;
    private final @NonNull WindowDecorationActions mWindowDecorationActions;
    private final @NonNull DesktopUserRepositories mDesktopUserRepositories;
    private final @NonNull WindowDecorationGestureExclusionTracker mGestureExclusionTracker;
    private final @Nullable InputManager mInputManager;
    private final @NonNull FocusTransitionObserver mFocusTransitionObserver;
    private final @NonNull ShellDesktopState mShellDesktopState;
    private final @NonNull MultiDisplayDragMoveIndicatorController
            mMultiDisplayDragMoveIndicatorController;
    private final @NonNull Supplier<SurfaceControl.Transaction> mTransactionFactory;
    private final @Nullable CaptionTouchStatusListener
            mCaptionTouchStatusListener;
    private final @NonNull AppHandleMotionEventHandler
            mAppHandleMotionEventHandler;

    private final DragDetector mHandleDragDetector;
    private final DragDetector mHeaderDragDetector;
    private final GestureDetector mGestureDetector;
    private final Rect mOnDragStartInitialBounds = new Rect();
    private final Rect mCurrentBounds = new Rect();

    /**
     * Whether to pilfer the next motion event to send cancellations to the windows below.
     * Useful when the caption window is spy and the gesture should be handled by the system
     * instead of by the app for their custom header content.
     */
    private boolean mIsCustomHeaderGesture;
    private boolean mIsResizeGesture;
    private boolean mIsDragging;
    private boolean mDragInterrupted;
    private boolean mLongClickDisabled;
    private int mDragPointerId = -1;
    private MotionEvent mMotionEvent;
    private int mCurrentPointerIconType = PointerIcon.TYPE_ARROW;

    public DesktopModeTouchEventListener(
            @NonNull Context context,
            @NonNull ActivityManager.RunningTaskInfo taskInfo,
            @NonNull DragPositioningCallback dragPositioningCallback,
            @NonNull Function<Integer, WindowDecorationWrapper> windowDecorationFinder,
            @NonNull DesktopTasksController desktopTasksController,
            @NonNull TaskOperations taskOperations,
            @NonNull DesktopModeUiEventLogger desktopModeUiEventLogger,
            @NonNull WindowDecorationActions windowDecorationActions,
            @NonNull DesktopUserRepositories desktopUserRepositories,
            @NonNull WindowDecorationGestureExclusionTracker gestureExclusionTracker,
            @Nullable InputManager inputManager,
            @NonNull FocusTransitionObserver focusTransitionObserver,
            @NonNull ShellDesktopState shellDesktopState,
            @NonNull MultiDisplayDragMoveIndicatorController
                    multiDisplayDragMoveIndicatorController,
            @NonNull Supplier<SurfaceControl.Transaction> transactionFactory,
            @Nullable CaptionTouchStatusListener captionTouchStatusListener,
            @NonNull AppHandleMotionEventHandler appHandleMotionEventHandler) {
        mContext = context;
        mDragPositioningCallback = dragPositioningCallback;
        mWindowDecorationFinder = windowDecorationFinder;
        mDesktopTasksController = desktopTasksController;
        mTaskOperations = taskOperations;
        mDesktopModeUiEventLogger = desktopModeUiEventLogger;
        mWindowDecorationActions = windowDecorationActions;
        mDesktopUserRepositories = desktopUserRepositories;
        mGestureExclusionTracker = gestureExclusionTracker;
        mInputManager = inputManager;
        mFocusTransitionObserver = focusTransitionObserver;
        mShellDesktopState = shellDesktopState;
        mMultiDisplayDragMoveIndicatorController = multiDisplayDragMoveIndicatorController;
        mTransactionFactory = transactionFactory;
        mCaptionTouchStatusListener = captionTouchStatusListener;
        mAppHandleMotionEventHandler = appHandleMotionEventHandler;

        mTaskId = taskInfo.taskId;
        mTaskToken = taskInfo.token;
        final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        final long appHandleHoldToDragDuration =
                DesktopModeFlags.ENABLE_HOLD_TO_DRAG_APP_HANDLE.isTrue()
                        ? APP_HANDLE_HOLD_TO_DRAG_DURATION_MS : 0;
        mHandleDragDetector = new DragDetector(this, appHandleHoldToDragDuration,
                touchSlop);
        mHeaderDragDetector = new DragDetector(this, APP_HEADER_HOLD_TO_DRAG_DURATION_MS,
                touchSlop);
        mGestureDetector = new GestureDetector(mContext, this);
    }

    @NonNull
    private String getResourceName(@Nullable View view) {
        if (view == null) return "null";
        final int id = view.getId();
        if (id == R.id.back_button) return "back_button";
        if (id == R.id.caption_handle) return "caption_handle";
        if (id == R.id.close_window) return "close_window";
        if (id == R.id.desktop_mode_caption) return "desktop_mode_caption";
        if (id == R.id.maximize_window) return "maximize_window";
        if (id == R.id.minimize_window) return "minimize_window";
        if (id == R.id.open_menu_button) return "open_menu_button";
        return "unknown";
    }

    @Override
    public void onClick(View v) {
        final String viewName = getResourceName(v);
        logD("onClick(%s)", viewName);
        if (mIsDragging) {
            logD("onClick(%s) while dragging in progress, ignoring", viewName);
            mIsDragging = false;
            return;
        }
        final WindowDecorationWrapper decoration = mWindowDecorationFinder.apply(mTaskId);
        if (decoration == null) {
            logD("onClick(%s) but decoration is null, ignoring", viewName);
            return;
        }
        final int id = v.getId();
        if (id == R.id.close_window) {
            mWindowDecorationActions.onClose(mTaskId);
        } else if (id == R.id.back_button) {
            mTaskOperations.injectBackKey(decoration.getTaskInfo().displayId);
        } else if (id == R.id.caption_handle || id == R.id.open_menu_button) {
            if (id == R.id.caption_handle && !decoration.getTaskInfo().isFreeform()) {
                // Clicking the App Handle.
                mDesktopModeUiEventLogger.log(decoration.getTaskInfo(),
                        DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_TAP);
            }
            if (decoration.getHandleMenuController() != null
                    && !decoration.getHandleMenuController().isHandleMenuActive()) {
                moveTaskToFront(decoration.getTaskInfo());
                mWindowDecorationActions.onOpenHandleMenu(mTaskId);
            }
        } else if (id == R.id.maximize_window) {
            // TODO(b/346441962): move click detection logic into the decor's
            //  {@link AppHeaderViewHolder}. Let it encapsulate the that and have it report
            //  back to the decoration using
            //  {@link DesktopModeWindowDecoration#setOnMaximizeOrRestoreClickListener}, which
            //  should shared with the maximize menu's maximize/restore actions.
            final DesktopRepository desktopRepository = mDesktopUserRepositories.getProfile(
                    decoration.getTaskInfo().userId);
            if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()
                    && desktopRepository.isTaskInFullImmersiveState(
                    decoration.getTaskInfo().taskId)) {
                // Task is in immersive and should exit.
                mWindowDecorationActions.onImmersiveOrRestore(decoration.getTaskInfo());
            } else {
                // Full immersive is disabled or task doesn't request/support it, so just
                // toggle between maximize/restore states.
                mWindowDecorationActions.onMaximizeOrRestore(decoration.getTaskInfo().taskId,
                        ToggleTaskSizeInteraction.AmbiguousSource.HEADER_BUTTON,
                        getInputMethod(mMotionEvent));
            }
        } else if (id == R.id.minimize_window) {
            if (DesktopExperienceFlags
                    .ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS.isTrue()) {
                final int nextFocusedTaskId = mDesktopTasksController
                        .getNextFocusedTask(decoration.getTaskInfo());
                final WindowDecorationWrapper nextFocusedWindow =
                        mWindowDecorationFinder.apply(nextFocusedTaskId);
                if (nextFocusedWindow != null) {
                    nextFocusedWindow.a11yAnnounceNewFocusedWindow();
                }
            }
            mDesktopTasksController.minimizeTask(
                    decoration.getTaskInfo(),
                    MinimizeReason.MINIMIZE_BUTTON);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        mMotionEvent = e;
        final String viewName = getResourceName(v);
        debugLogD("onTouch(%s) action=%s", viewName, MotionEvent.actionToString(e.getAction()));
        final int id = v.getId();
        final WindowDecorationWrapper decoration = mWindowDecorationFinder.apply(mTaskId);
        if (decoration == null) {
            debugLogD("onTouch(%s) but decoration is null, ignoring", viewName);
            return false;
        }
        final ActivityManager.RunningTaskInfo taskInfo = decoration.getTaskInfo();
        final boolean touchscreenSource =
                (e.getSource() & SOURCE_TOUCHSCREEN) == SOURCE_TOUCHSCREEN;
        // Disable long click during events from a non-touchscreen source
        mLongClickDisabled = !touchscreenSource && e.getActionMasked() != ACTION_UP
                && e.getActionMasked() != ACTION_CANCEL;
        debugLogD("onTouch(%s) isTouchscreen=%b longClickDisabled=%b",
                viewName, touchscreenSource, mLongClickDisabled);

        if (id != R.id.caption_handle && id != R.id.desktop_mode_caption
                && id != R.id.open_menu_button && id != R.id.close_window
                && id != R.id.maximize_window && id != R.id.minimize_window) {
            debugLogD("onTouch(%s) unsupported view, ignoring", viewName);
            return false;
        }
        if (e.isSynthesizedTouchpadGesture()) {
            // Touchpad finger gestures are ignored.
            debugLogD("onTouch(%s) but is touchpad gesture, ignoring", viewName);
            return false;
        }

        final boolean isAppHandle = !taskInfo.isFreeform();
        final int actionMasked = e.getActionMasked();
        final boolean isDown = actionMasked == MotionEvent.ACTION_DOWN;
        final boolean isUpOrCancel = actionMasked == MotionEvent.ACTION_CANCEL
                || actionMasked == MotionEvent.ACTION_UP;
        if (isDown) {
            // Only move to front on down to prevent 2+ tasks from fighting
            // (and thus flickering) for front status when drag-moving them simultaneously with
            // two pointers.
            // TODO(b/356962065): during a drag-move, this shouldn't be a WCT - just move the
            //  task surface to the top of other tasks and reorder once the user releases the
            //  gesture together with the bounds' WCT. This is probably still valid for other
            //  gestures like simple clicks.
            moveTaskToFront(taskInfo);

            final boolean downInCustomizableCaptionRegion =
                    decoration.checkTouchEventInCustomizableRegion(e);
            final Region exclusionRegion = mGestureExclusionTracker
                    .getExclusionRegion(e.getDisplayId());
            final boolean downInExclusionRegion =
                    exclusionRegion.contains((int) e.getRawX(), (int) e.getRawY());
            final boolean isTransparentCaption =
                    TaskInfoKt.isTransparentCaptionBarAppearance(taskInfo);
            // MotionEvent's coordinates are relative to view, we want location in window
            // to offset position relative to caption as a whole.
            int[] viewLocation = new int[2];
            v.getLocationInWindow(viewLocation);
            mIsResizeGesture = decoration.shouldResizeListenerHandleEvent(e,
                    new Point(viewLocation[0], viewLocation[1]));
            // The caption window may be a spy window when the caption background is
            // transparent, which means events will fall through to the app window. Make
            // sure to cancel these events if they do not happen in the intersection of the
            // customizable region and what the app reported as exclusion areas, because
            // the drag-move or other caption gestures should take priority outside those
            // regions.
            mIsCustomHeaderGesture = downInCustomizableCaptionRegion
                    && downInExclusionRegion && isTransparentCaption;
        }
        if (mIsCustomHeaderGesture || mIsResizeGesture) {
            // The event will be handled by the custom window below or pilfered by resize
            // handler.
            debugLogD("onTouch(%s) but mIsCustomHeaderGesture=%b mIsResizeGesture=%b, ignoring",
                    viewName, mIsCustomHeaderGesture, mIsResizeGesture);
            return false;
        }
        // Pilfer so that windows below receive cancellations for this gesture.
        pilferPointers(v);
        if (isUpOrCancel) {
            // Gesture is finished, reset state.
            mIsCustomHeaderGesture = false;
            mIsResizeGesture = false;
        }
        if (isAppHandle) {
            return mHandleDragDetector.onMotionEvent(v, e);
        } else {
            return mHeaderDragDetector.onMotionEvent(v, e);
        }
    }

    private void pilferPointers(@NonNull View v) {
        final ViewRootImpl viewRootImpl = v.getViewRootImpl();
        if (mInputManager == null || viewRootImpl == null) return;
        mInputManager.pilferPointers(viewRootImpl.getInputToken());
    }

    @Override
    public boolean onLongClick(View v) {
        final String viewName = getResourceName(v);
        logD("onLongClick(%s)", viewName);
        final int id = v.getId();
        if (id != R.id.maximize_window) {
            logD("onLongClick(%s) but view is unsupported, ignoring", viewName);
            return false;
        }
        if (mLongClickDisabled) {
            logD("onLongClick(%s) but long click is disabled, ignoring", viewName);
            return false;
        }
        final WindowDecorationWrapper decoration = mWindowDecorationFinder.apply(mTaskId);
        if (decoration == null) {
            logD("onLongClick(%s) but decoration is null, ignoring", viewName);
            return false;
        }
        moveTaskToFront(decoration.getTaskInfo());
        if (decoration.getMaximizeMenuController() != null
                && !decoration.getMaximizeMenuController().isMaximizeMenuActive()) {
            logD("onLongClick(%s) creating maximize menu", viewName);
            decoration.getMaximizeMenuController().createMaximizeMenu();
        }
        return true;
    }

    /**
     * TODO(b/346441962): move this hover detection logic into the decor's
     * {@link AppHeaderViewHolder}.
     */
    @Override
    public boolean onGenericMotion(View v, MotionEvent ev) {
        mMotionEvent = ev;
        final WindowDecorationWrapper decoration = mWindowDecorationFinder.apply(mTaskId);
        if (decoration == null) {
            return false;
        }
        final int id = v.getId();
        if (ev.getAction() == ACTION_HOVER_ENTER && id == R.id.maximize_window) {
            if (decoration.getMaximizeMenuController() == null) return false;
            decoration.getMaximizeMenuController().setAppHeaderMaximizeButtonHovered(true);
            if (!decoration.getMaximizeMenuController().isMaximizeMenuActive()) {
                decoration.getMaximizeMenuController().onMaximizeButtonHoverEnter();
            }
            return true;
        }
        if (ev.getAction() == ACTION_HOVER_EXIT && id == R.id.maximize_window) {
            if (decoration.getMaximizeMenuController() == null) return false;
            decoration.getMaximizeMenuController().setAppHeaderMaximizeButtonHovered(false);
            decoration.getMaximizeMenuController().onMaximizeHoverStateChanged();
            if (!decoration.getMaximizeMenuController().isMaximizeMenuActive()) {
                decoration.getMaximizeMenuController().onMaximizeButtonHoverExit();
            }
            return true;
        }
        return false;
    }

    private void moveTaskToFront(ActivityManager.RunningTaskInfo taskInfo) {
        if (!mFocusTransitionObserver.hasGlobalFocus(taskInfo)) {
            logD("moveTaskToFront display=%d "
                            + "globallyFocusedTaskId=%d globallyFocusedDisplayId=%d",
                    taskInfo.displayId,
                    mFocusTransitionObserver.getGloballyFocusedTaskId(),
                    mFocusTransitionObserver.getGloballyFocusedDisplayId());
            mDesktopModeUiEventLogger.log(taskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_HEADER_TAP_TO_REFOCUS);
            mDesktopTasksController.moveTaskToFront(taskInfo);
        } else {
            debugLogD("moveTaskToFront already had global focus, skipping "
                            + " display=%d globallyFocusedTaskId=%d globallyFocusedDisplayId=%d",
                    taskInfo.displayId,
                    mFocusTransitionObserver.getGloballyFocusedTaskId(),
                    mFocusTransitionObserver.getGloballyFocusedDisplayId());
        }
    }

    /**
     * @param e {@link MotionEvent} to process
     * @return {@code true} if the motion event is handled.
     */
    @Override
    public boolean handleMotionEvent(@Nullable View v, MotionEvent e) {
        final String viewName = getResourceName(v);
        debugLogD("handleMotionEvent(%s) action=%s",
                viewName, MotionEvent.actionToString(e.getAction()));
        final WindowDecorationWrapper decoration = mWindowDecorationFinder.apply(mTaskId);
        if (decoration == null) {
            debugLogD("handleMotionEvent(%s) but decoration is null, ignoring", viewName);
            return false;
        }
        final ActivityManager.RunningTaskInfo taskInfo = decoration.getTaskInfo();
        if (mShellDesktopState.canEnterDesktopModeOrShowAppHandle()
                && !taskInfo.isFreeform()) {
            return handleNonFreeformMotionEvent(decoration, v, e);
        } else {
            return handleFreeformMotionEvent(decoration, taskInfo, v, e);
        }
    }

    private boolean handleNonFreeformMotionEvent(WindowDecorationWrapper decoration,
            View v, MotionEvent e) {
        final int id = v.getId();
        final String viewName = getResourceName(v);
        debugLogD("handleNonFreeformMotionEvent(%s)", viewName);
        if (id != R.id.caption_handle) {
            debugLogD("handleNonFreeformMotionEvent(%s) unsupported view, ignoring",
                    viewName);
            return false;
        }
        mAppHandleMotionEventHandler.onMotionEvent(e, decoration,
                /* interruptDragCallback= */
                () -> {
                    logD("handleNonFreeformMotionEvent(%s) drag interrupted", viewName);
                    mDragInterrupted = true;
                    setIsDragging(decoration, /* isDragging= */ false);
                });
        final boolean wasDragging = mIsDragging;
        updateDragStatus(decoration, e);
        final boolean upOrCancel = e.getActionMasked() == ACTION_UP
                || e.getActionMasked() == ACTION_CANCEL;
        debugLogD("handleNonFreeformMotionEvent(%s) wasDragging=%b isDragging=%b upOrCancel=%b",
                viewName, wasDragging, mIsDragging, upOrCancel);
        if (wasDragging && upOrCancel) {
            // When finishing a drag the event will be consumed, which means the pressed
            // state of the App Handle must be manually reset to scale its drawable back to
            // its original shape. This is necessary for drag gestures of the Handle that
            // result in a cancellation (dragging back to the top).
            v.setPressed(false);
        }
        // Only prevent onClick from receiving this event if it's a drag.
        return wasDragging;
    }

    private void setIsDragging(@Nullable WindowDecorationWrapper decor, boolean isDragging) {
        mIsDragging = isDragging;
        if (decor == null) return;
        decor.setIsDragging(isDragging);
    }

    private boolean handleFreeformMotionEvent(WindowDecorationWrapper decoration,
            ActivityManager.RunningTaskInfo taskInfo, View v, MotionEvent e) {
        final String viewName = getResourceName(v);
        debugLogD("handleFreeformMotionEvent(%s)", viewName);
        updateTouchStatus(e);
        final int id = v.getId();
        if (mGestureDetector.onTouchEvent(e)) {
            debugLogD("handleFreeformMotionEvent(%s) handled by gesture detector", viewName);
            return true;
        }
        final boolean touchingButton = (id == R.id.close_window || id == R.id.maximize_window
                || id == R.id.open_menu_button || id == R.id.minimize_window);
        final DesktopRepository desktopRepository = mDesktopUserRepositories.getProfile(
                taskInfo.userId);
        final boolean dragAllowed =
                !desktopRepository.isTaskInFullImmersiveState(taskInfo.taskId);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (dragAllowed) {
                    mDragPointerId = e.getPointerId(0);
                    final Rect initialBounds = mDragPositioningCallback.onDragPositioningStart(
                            0 /* ctrlType */, e.getDisplayId(), e.getRawX(0),
                            e.getRawY(0), getInputMethodFromMotionEvent(e));
                    updateDragStatus(decoration, e);
                    mOnDragStartInitialBounds.set(initialBounds);
                    mCurrentBounds.set(initialBounds);
                    debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                    + "dispatched |onDragPositioningStart| dragAllowed=%b "
                                    + "isDragging=%b mOnDragStartInitialBounds=%s "
                                    + "mCurrentBounds=%s touchingButton=%b",
                            viewName, MotionEvent.actionToString(e.getAction()), dragAllowed,
                            mIsDragging, mOnDragStartInitialBounds, mCurrentBounds,
                            touchingButton);
                } else {
                    debugLogD("handleFreeformMotionEvent(%s) action=%s dragAllowed=%b "
                                    + "isDragging=%b mOnDragStartInitialBounds=%s "
                                    + "mCurrentBounds=%s touchingButton=%b",
                            viewName, MotionEvent.actionToString(e.getAction()), dragAllowed,
                            mIsDragging, mOnDragStartInitialBounds, mCurrentBounds,
                            touchingButton);
                }
                // Do not consume input event if a button is touched, otherwise it would
                // prevent the button's ripple effect from showing.
                return !touchingButton;
            }
            case ACTION_MOVE: {
                // If a decor's resize drag zone is active, don't also try to reposition it.
                if (decoration.isHandlingDragResize()) {
                    debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                    + "handling drag resize, ignore",
                            viewName, MotionEvent.actionToString(e.getAction()));
                    break;
                }
                // Dragging the header isn't allowed, so skip the positioning work.
                if (!dragAllowed) {
                    debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                    + "drag is not allowed, ignore",
                            viewName, MotionEvent.actionToString(e.getAction()));
                    break;
                }

                if (e.findPointerIndex(mDragPointerId) == -1) {
                    mDragPointerId = e.getPointerId(0);
                }
                final int dragPointerIdx = e.findPointerIndex(mDragPointerId);

                if (DesktopExperienceFlags
                        .ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX.isTrue()) {
                    final boolean inDesktopModeDisplay = mShellDesktopState
                            .isEligibleWindowDropTarget(e.getDisplayId());
                    // TODO: b/418651425 - Use a more specific pointer icon when available.
                    final IBinder inputToken = v.getViewRootImpl() != null
                            ? v.getViewRootImpl().getInputToken() : null;
                    updatePointerIcon(e, dragPointerIdx, inputToken,
                            inDesktopModeDisplay ? PointerIcon.TYPE_ARROW
                                    : PointerIcon.TYPE_NO_DROP);
                    // Allow bounds update only when cursor is on desktop-mode displays.
                    // Otherwise, ignore the MOVE event and the window holds its current bounds.
                    if (inDesktopModeDisplay) {
                        mCurrentBounds.set(mDragPositioningCallback.onDragPositioningMove(
                                e.getDisplayId(),
                                e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx)));
                        debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                        + "inDesktopModeDisplay=%b dispatched "
                                        + "|onDragPositioningMove| mCurrentBounds=%s",
                                viewName, MotionEvent.actionToString(e.getAction()),
                                inDesktopModeDisplay, mCurrentBounds);
                    } else {
                        debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                        + "not a desktop mode display, ignore",
                                viewName, MotionEvent.actionToString(e.getAction()));
                    }
                } else {
                    mCurrentBounds.set(mDragPositioningCallback.onDragPositioningMove(
                            e.getDisplayId(),
                            e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx)));
                    debugLogD("handleFreeformMotionEvent(%s) action=%s dispatched "
                                    + "|onDragPositioningMove| mCurrentBounds=%s",
                            viewName, MotionEvent.actionToString(e.getAction()),
                            mCurrentBounds);
                }

                mDesktopTasksController.onDragPositioningMove(taskInfo,
                        decoration.getTaskSurface(),
                        e.getDisplayId(),
                        e.getRawX(dragPointerIdx),
                        e.getRawY(dragPointerIdx),
                        mCurrentBounds);
                debugLogD("handleFreeformMotionEvent(%s) action=%s updated controller "
                                + "mIsDragging=%b mCurrentBounds=%s "
                                + "mOnDragStartInitialBounds=%s",
                        viewName, MotionEvent.actionToString(e.getAction()), mIsDragging,
                        mCurrentBounds, mOnDragStartInitialBounds);
                //  Flip mIsDragging only if the bounds actually changed.
                if (mIsDragging || !mCurrentBounds.equals(mOnDragStartInitialBounds)) {
                    updateDragStatus(decoration, e);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                final boolean wasDragging = mIsDragging;
                if (!DesktopExperienceFlags.ENABLE_FIX_LEAKING_VISUAL_INDICATOR.isTrue()
                        && !wasDragging) {
                    debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                    + "was not dragging, ignore",
                            viewName, MotionEvent.actionToString(e.getAction()));
                    return false;
                }
                if (wasDragging) {
                    mDesktopModeUiEventLogger.log(taskInfo,
                            DesktopUiEventEnum.DESKTOP_WINDOW_MOVE_BY_HEADER_DRAG);
                }
                if (e.findPointerIndex(mDragPointerId) == -1) {
                    mDragPointerId = e.getPointerId(0);
                }
                final int dragPointerIdx = e.findPointerIndex(mDragPointerId);
                if (DesktopExperienceFlags.ENABLE_FIX_LEAKING_VISUAL_INDICATOR.isTrue()
                        && !dragAllowed) {
                    debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                    + "drag is not allowed, ignore",
                            viewName, MotionEvent.actionToString(e.getAction()));
                    return false;
                }

                final Rect newTaskBounds = mDragPositioningCallback.onDragPositioningEnd(
                        e.getDisplayId(),
                        e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx));
                debugLogD("handleFreeformMotionEvent(%s) action=%s dispatched "
                                + "|onDragPositioningEnd| newTaskBounds=%s",
                        viewName, MotionEvent.actionToString(e.getAction()), newTaskBounds);

                if (DesktopExperienceFlags
                        .ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX.isTrue()) {
                    final IBinder inputToken = v.getViewRootImpl() != null
                            ? v.getViewRootImpl().getInputToken() : null;
                    updatePointerIcon(e, dragPointerIdx, inputToken,
                            PointerIcon.TYPE_ARROW);
                    // If the cursor ends on a non-desktop-mode display, revert the window
                    // to its initial bounds prior to the drag starting.
                    if (!mShellDesktopState
                            .isEligibleWindowDropTarget(e.getDisplayId())) {
                        newTaskBounds.set(mOnDragStartInitialBounds);
                        debugLogD(
                                "handleFreeformMotionEvent(%s) action=%s pointer in non-desktop"
                                        + " display(%d), reverted to initial bounds=%s",
                                viewName, MotionEvent.actionToString(e.getAction()),
                                e.getDisplayId(), newTaskBounds);
                    }
                }

                // Tasks bounds haven't actually been updated (only its leash), so pass to
                // DesktopTasksController to allow secondary transformations (i.e. snap resizing
                // or transforming to fullscreen) before setting new task bounds.
                final Rect validDragArea = decoration.getValidDragArea();
                final boolean needDragIndicatorCleanup =
                        mDesktopTasksController.onDragPositioningEnd(
                                taskInfo, decoration.getTaskSurface(), e.getDisplayId(),
                                new PointF(
                                        e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx)),
                                newTaskBounds, validDragArea,
                                new Rect(mOnDragStartInitialBounds), e);
                debugLogD("handleFreeformMotionEvent(%s) action=%s updated controller "
                                + "newTaskBounds%s validDragArea=%s "
                                + "mOnDragStartInitialBounds=%s touchingButton=%b "
                                + "needDragIndicatorCleanup=%b",
                        viewName, MotionEvent.actionToString(e.getAction()), newTaskBounds,
                        validDragArea, mOnDragStartInitialBounds, touchingButton,
                        needDragIndicatorCleanup);
                if (DesktopExperienceFlags.ENABLE_WINDOW_DROP_SMOOTH_TRANSITION.isTrue()) {
                    if (needDragIndicatorCleanup) {
                        SurfaceControl.Transaction t = mTransactionFactory.get();
                        mMultiDisplayDragMoveIndicatorController.onDragEnd(taskInfo.taskId, t);
                        t.apply();
                    }
                }
                if (DesktopExperienceFlags.ENABLE_FIX_LEAKING_VISUAL_INDICATOR.isTrue()
                        && !wasDragging) {
                    debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                    + "was not dragging, ignore",
                            viewName, MotionEvent.actionToString(e.getAction()));
                    return false;
                }
                if (touchingButton) {
                    // We need the input event to not be consumed here to end the ripple
                    // effect on the touched button. We will reset drag state in the ensuing
                    // onClick call that results.
                    debugLogD("handleFreeformMotionEvent(%s) action=%s "
                                    + "touching button, ignore",
                            viewName, MotionEvent.actionToString(e.getAction()));
                    return false;
                }
                updateDragStatus(decoration, e);
                return true;
            }
        }
        return true;
    }

    private void updatePointerIcon(MotionEvent e, int dragPointerIdx, @Nullable IBinder inputToken,
            int iconType) {
        if (mCurrentPointerIconType == iconType) {
            return;
        }
        mInputManager.setPointerIcon(PointerIcon.getSystemIcon(mContext, iconType),
                e.getDisplayId(), e.getDeviceId(), e.getPointerId(dragPointerIdx), inputToken);
        mCurrentPointerIconType = iconType;
    }

    private void updateDragStatus(WindowDecorationWrapper decor, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mDragInterrupted = false;
                setIsDragging(decor, false /* isDragging */);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!mDragInterrupted) {
                    setIsDragging(decor, true /* isDragging */);
                }
                break;
            }
        }
        debugLogD("updateDragStatus action=%s updated mIsDragging=%b mDragInterrupted=%b",
                MotionEvent.actionToString(e.getAction()), mIsDragging, mDragInterrupted);
    }

    private void updateTouchStatus(MotionEvent e) {
        if (mCaptionTouchStatusListener == null) {
            return;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mCaptionTouchStatusListener.onCaptionPressed();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mCaptionTouchStatusListener.onCaptionReleased();
                break;
            }
        }
    }

    /**
     * Perform a task size toggle on release of the double-tap, assuming no drag event
     * was handled during the double-tap.
     *
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
        final DesktopRepository desktopRepository = mDesktopUserRepositories.getCurrent();
        if (desktopRepository.isTaskInFullImmersiveState(mTaskId)) {
            // Disallow double-tap to resize when in full immersive.
            return false;
        }
        mWindowDecorationActions.onMaximizeOrRestore(mTaskId,
                ToggleTaskSizeInteraction.AmbiguousSource.DOUBLE_TAP,
                getInputMethod(mMotionEvent));
        return true;
    }

    private DesktopModeEventLogger.Companion.InputMethod getInputMethod(MotionEvent ev) {
        return DesktopModeEventLogger.getInputMethodFromMotionEvent(ev);
    }

    private void debugLogD(@NonNull String msg, @NonNull Object... args) {
        if (DEBUG_MOTION_EVENTS) {
            logD(msg, args);
        }
    }

    private void logD(@NonNull String msg, @NonNull Object... args) {
        ProtoLog.d(WM_SHELL_WINDOW_DECORATION, "%s: (taskId=%d) %s",
                TAG, mTaskId, String.format(msg, args));
    }
}
