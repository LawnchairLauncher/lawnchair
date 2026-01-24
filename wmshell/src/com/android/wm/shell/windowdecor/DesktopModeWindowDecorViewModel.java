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
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.view.InputDevice.SOURCE_TOUCHSCREEN;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.WindowInsets.Type.statusBars;
import static android.window.DesktopExperienceFlags.ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS;

import static com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_MODE_APP_HANDLE_MENU;
import static com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod;
import static com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason;
import static com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE;
import static com.android.wm.shell.shared.multiinstance.ManageWindowsViewContainer.MANAGE_WINDOWS_MINIMUM_INSTANCES;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.InsetsState;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewRootImpl;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;
import android.window.TaskSnapshot;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.jank.Cuj;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.LatencyTracker;
import com.android.window.flags2.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.apptoweb.AppToWebGenericLinksParser;
import com.android.wm.shell.apptoweb.AssistContentRequester;
import com.android.wm.shell.common.ComponentUtils;
import com.android.wm.shell.common.DisplayChangeController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController;
import com.android.wm.shell.common.MultiInstanceHelper;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.compatui.CompatUIController;
import com.android.wm.shell.compatui.api.CompatUIHandler;
import com.android.wm.shell.compatui.impl.CompatUIRequests;
import com.android.wm.shell.desktopmode.DesktopActivityOrientationChangeHandler;
import com.android.wm.shell.desktopmode.DesktopImmersiveController;
import com.android.wm.shell.desktopmode.DesktopModeEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum;
import com.android.wm.shell.desktopmode.DesktopModeUtils;
import com.android.wm.shell.desktopmode.DesktopModeVisualIndicator;
import com.android.wm.shell.desktopmode.DesktopRepository;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopTasksController.SnapPosition;
import com.android.wm.shell.desktopmode.DesktopTasksLimiter;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository;
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction;
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeUtilsKt;
import com.android.wm.shell.desktopmode.education.AppHandleEducationController;
import com.android.wm.shell.desktopmode.education.AppToWebEducationController;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.recents.RecentsTransitionStateListener;
import com.android.wm.shell.shared.FocusTransitionListener;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.desktopmode.DesktopConfig;
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy;
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.KeyguardChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.DesktopModeWindowDecoration.ExclusionRegionListener;
import com.android.wm.shell.windowdecor.common.AppHandleAndHeaderVisibilityHelper;
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader;
import com.android.wm.shell.windowdecor.common.WindowDecorationGestureExclusionTracker;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.extension.InsetsStateKt;
import com.android.wm.shell.windowdecor.extension.TaskInfoKt;
import com.android.wm.shell.windowdecor.tiling.DesktopTilingDecorViewModel;
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler;
import com.android.wm.shell.windowdecor.viewholder.AppHandleViewHolder;
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.MainCoroutineDispatcher;

import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * View model for the window decoration with a caption and shadows. Works with
 * {@link DesktopModeWindowDecoration}.
 */

public class DesktopModeWindowDecorViewModel implements WindowDecorViewModel,
        FocusTransitionListener, SnapEventHandler {
    private static final String TAG = "DesktopModeWindowDecorViewModel";

    private final DesktopModeWindowDecoration.Factory mDesktopModeWindowDecorFactory;
    private final IWindowManager mWindowManager;
    private final ShellExecutor mMainExecutor;
    private final ActivityTaskManager mActivityTaskManager;
    private final ShellCommandHandler mShellCommandHandler;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final DesktopUserRepositories mDesktopUserRepositories;
    private final ShellController mShellController;
    private final Context mContext;
    private final @ShellMainThread Handler mMainHandler;
    private final @ShellMainThread MainCoroutineDispatcher mMainDispatcher;
    private final @ShellBackgroundThread CoroutineScope mBgScope;
    private final @ShellBackgroundThread ShellExecutor mBgExecutor;
    private final Choreographer mMainChoreographer;
    private final DisplayController mDisplayController;
    private final SyncTransactionQueue mSyncQueue;
    private final DesktopTasksController mDesktopTasksController;
    private final DesktopImmersiveController mDesktopImmersiveController;
    private final InputManager mInputManager;
    private final InteractionJankMonitor mInteractionJankMonitor;
    private final MultiInstanceHelper mMultiInstanceHelper;
    private final WindowDecorCaptionRepository mWindowDecorCaptionRepository;
    private final Optional<DesktopTasksLimiter> mDesktopTasksLimiter;
    private final AppHandleEducationController mAppHandleEducationController;
    private final AppToWebEducationController mAppToWebEducationController;
    private final AppHandleAndHeaderVisibilityHelper mAppHandleAndHeaderVisibilityHelper;
    private final AppHeaderViewHolder.Factory mAppHeaderViewHolderFactory;
    private final AppHandleViewHolder.Factory mAppHandleViewHolderFactory;
    private final DesksOrganizer mDesksOrganizer;
    private final DesktopState mDesktopState;
    private final DesktopConfig mDesktopConfig;
    private boolean mTransitionDragActive;

    private SparseArray<EventReceiver> mEventReceiversByDisplay = new SparseArray<>();

    private final ExclusionRegionListener mExclusionRegionListener =
            new ExclusionRegionListenerImpl();

    private final SparseArray<DesktopModeWindowDecoration> mWindowDecorByTaskId;
    private final InputMonitorFactory mInputMonitorFactory;
    private TaskOperations mTaskOperations;
    private final Supplier<SurfaceControl.Transaction> mTransactionFactory;
    private final Transitions mTransitions;
    private final Optional<DesktopActivityOrientationChangeHandler>
            mActivityOrientationChangeHandler;

    private SplitScreenController mSplitScreenController;

    private MoveToDesktopAnimator mMoveToDesktopAnimator;
    private final Rect mDragToDesktopAnimationStartBounds = new Rect();
    private final DesktopModeKeyguardChangeListener mDesktopModeKeyguardChangeListener =
            new DesktopModeKeyguardChangeListener();
    private final RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private final AppToWebGenericLinksParser mGenericLinksParser;
    private final DisplayInsetsController mDisplayInsetsController;

    private final WindowDecorationGestureExclusionTracker mGestureExclusionTracker;
    private boolean mInImmersiveMode;
    private final String mSysUIPackageName;
    private final AssistContentRequester mAssistContentRequester;
    private final WindowDecorViewHostSupplier<WindowDecorViewHost> mWindowDecorViewHostSupplier;

    private final DisplayChangeController.OnDisplayChangingListener mOnDisplayChangingListener;
    private final WindowDecorationActions mWindowDecorationActions;
    private final TaskPositionerFactory mTaskPositionerFactory;
    private final FocusTransitionObserver mFocusTransitionObserver;
    private final DesktopModeEventLogger mDesktopModeEventLogger;
    private final DesktopModeUiEventLogger mDesktopModeUiEventLogger;
    private final WindowDecorTaskResourceLoader mTaskResourceLoader;
    private final RecentsTransitionHandler mRecentsTransitionHandler;
    private final DesktopModeCompatPolicy mDesktopModeCompatPolicy;
    private final DesktopTilingDecorViewModel mDesktopTilingDecorViewModel;
    private final MultiDisplayDragMoveIndicatorController mMultiDisplayDragMoveIndicatorController;
    private final LatencyTracker mLatencyTracker;
    private final CompatUIHandler mCompatUI;

    public DesktopModeWindowDecorViewModel(
            Context context,
            ShellExecutor shellExecutor,
            @ShellMainThread Handler mainHandler,
            Choreographer mainChoreographer,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread CoroutineScope bgScope,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            IWindowManager windowManager,
            ShellTaskOrganizer taskOrganizer,
            DesktopUserRepositories desktopUserRepositories,
            DisplayController displayController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            Optional<DesktopTasksController> desktopTasksController,
            DesktopImmersiveController desktopImmersiveController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            InteractionJankMonitor interactionJankMonitor,
            AppToWebGenericLinksParser genericLinksParser,
            AssistContentRequester assistContentRequester,
            @NonNull WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            MultiInstanceHelper multiInstanceHelper,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            AppHandleEducationController appHandleEducationController,
            AppToWebEducationController appToWebEducationController,
            AppHandleAndHeaderVisibilityHelper appHandleAndHeaderVisibilityHelper,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            Optional<DesktopActivityOrientationChangeHandler> activityOrientationChangeHandler,
            FocusTransitionObserver focusTransitionObserver,
            DesktopModeEventLogger desktopModeEventLogger,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            WindowDecorTaskResourceLoader taskResourceLoader,
            RecentsTransitionHandler recentsTransitionHandler,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DesktopTilingDecorViewModel desktopTilingDecorViewModel,
            MultiDisplayDragMoveIndicatorController multiDisplayDragMoveIndicatorController,
            CompatUIHandler compatUI,
            DesksOrganizer desksOrganizer,
            DesktopState desktopState,
            DesktopConfig desktopConfig) {
        this(
                context,
                shellExecutor,
                mainHandler,
                mainChoreographer,
                mainDispatcher,
                bgScope,
                bgExecutor,
                shellInit,
                shellCommandHandler,
                windowManager,
                taskOrganizer,
                desktopUserRepositories,
                displayController,
                shellController,
                displayInsetsController,
                syncQueue,
                transitions,
                desktopTasksController,
                desktopImmersiveController,
                genericLinksParser,
                assistContentRequester,
                windowDecorViewHostSupplier,
                multiInstanceHelper,
                new DesktopModeWindowDecoration.Factory(),
                new InputMonitorFactory(),
                SurfaceControl.Transaction::new,
                new AppHeaderViewHolder.Factory(),
                new AppHandleViewHolder.Factory(),
                rootTaskDisplayAreaOrganizer,
                new SparseArray<>(),
                interactionJankMonitor,
                desktopTasksLimiter,
                appHandleEducationController,
                appToWebEducationController,
                appHandleAndHeaderVisibilityHelper,
                windowDecorCaptionRepository,
                activityOrientationChangeHandler,
                new TaskPositionerFactory(),
                focusTransitionObserver,
                desktopModeEventLogger,
                desktopModeUiEventLogger,
                taskResourceLoader,
                recentsTransitionHandler,
                desktopModeCompatPolicy,
                desktopTilingDecorViewModel,
                multiDisplayDragMoveIndicatorController,
                compatUI,
                desksOrganizer,
                desktopState,
                desktopConfig);
    }

    @VisibleForTesting
    DesktopModeWindowDecorViewModel(
            Context context,
            ShellExecutor shellExecutor,
            @ShellMainThread Handler mainHandler,
            Choreographer mainChoreographer,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread CoroutineScope bgScope,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            IWindowManager windowManager,
            ShellTaskOrganizer taskOrganizer,
            DesktopUserRepositories desktopUserRepositories,
            DisplayController displayController,
            ShellController shellController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            Transitions transitions,
            Optional<DesktopTasksController> desktopTasksController,
            DesktopImmersiveController desktopImmersiveController,
            AppToWebGenericLinksParser genericLinksParser,
            AssistContentRequester assistContentRequester,
            @NonNull WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            MultiInstanceHelper multiInstanceHelper,
            DesktopModeWindowDecoration.Factory desktopModeWindowDecorFactory,
            InputMonitorFactory inputMonitorFactory,
            Supplier<SurfaceControl.Transaction> transactionFactory,
            AppHeaderViewHolder.Factory appHeaderViewHolderFactory,
            AppHandleViewHolder.Factory appHandleViewHolderFactory,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            SparseArray<DesktopModeWindowDecoration> windowDecorByTaskId,
            InteractionJankMonitor interactionJankMonitor,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            AppHandleEducationController appHandleEducationController,
            AppToWebEducationController appToWebEducationController,
            AppHandleAndHeaderVisibilityHelper appHandleAndHeaderVisibilityHelper,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            Optional<DesktopActivityOrientationChangeHandler> activityOrientationChangeHandler,
            TaskPositionerFactory taskPositionerFactory,
            FocusTransitionObserver focusTransitionObserver,
            DesktopModeEventLogger desktopModeEventLogger,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            WindowDecorTaskResourceLoader taskResourceLoader,
            RecentsTransitionHandler recentsTransitionHandler,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DesktopTilingDecorViewModel desktopTilingDecorViewModel,
            MultiDisplayDragMoveIndicatorController multiDisplayDragMoveIndicatorController,
            CompatUIHandler compatUI,
            DesksOrganizer desksOrganizer,
            DesktopState desktopState,
            DesktopConfig desktopConfig) {
        mContext = context;
        mMainExecutor = shellExecutor;
        mMainHandler = mainHandler;
        mMainChoreographer = mainChoreographer;
        mMainDispatcher = mainDispatcher;
        mBgScope = bgScope;
        mBgExecutor = bgExecutor;
        mActivityTaskManager = mContext.getSystemService(ActivityTaskManager.class);
        mTaskOrganizer = taskOrganizer;
        mDesktopUserRepositories = desktopUserRepositories;
        mShellController = shellController;
        mDisplayController = displayController;
        mDisplayInsetsController = displayInsetsController;
        mSyncQueue = syncQueue;
        mTransitions = transitions;
        mDesktopTasksController = desktopTasksController.get();
        mDesktopImmersiveController = desktopImmersiveController;
        mMultiInstanceHelper = multiInstanceHelper;
        mShellCommandHandler = shellCommandHandler;
        mWindowManager = windowManager;
        mDesktopModeWindowDecorFactory = desktopModeWindowDecorFactory;
        mInputMonitorFactory = inputMonitorFactory;
        mTransactionFactory = transactionFactory;
        mAppHeaderViewHolderFactory = appHeaderViewHolderFactory;
        mAppHandleViewHolderFactory = appHandleViewHolderFactory;
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
        mGenericLinksParser = genericLinksParser;
        mInputManager = mContext.getSystemService(InputManager.class);
        mWindowDecorByTaskId = windowDecorByTaskId;
        mSysUIPackageName = mContext.getResources().getString(
                com.android.internal.R.string.config_systemUi);
        mInteractionJankMonitor = interactionJankMonitor;
        mDesktopTasksLimiter = desktopTasksLimiter;
        mAppHandleEducationController = appHandleEducationController;
        mAppToWebEducationController = appToWebEducationController;
        mAppHandleAndHeaderVisibilityHelper = appHandleAndHeaderVisibilityHelper;
        mWindowDecorCaptionRepository = windowDecorCaptionRepository;
        mActivityOrientationChangeHandler = activityOrientationChangeHandler;
        mAssistContentRequester = assistContentRequester;
        mWindowDecorViewHostSupplier = windowDecorViewHostSupplier;
        mCompatUI = compatUI;
        mOnDisplayChangingListener = (displayId, fromRotation, toRotation, displayAreaInfo, t) -> {
            DesktopModeWindowDecoration decoration;
            RunningTaskInfo taskInfo;
            for (int i = 0; i < mWindowDecorByTaskId.size(); i++) {
                decoration = mWindowDecorByTaskId.valueAt(i);
                if (decoration == null) {
                    continue;
                } else {
                    taskInfo = decoration.mTaskInfo;
                }

                // Check if display has been rotated between portrait & landscape
                if (displayId == taskInfo.displayId && taskInfo.isFreeform()
                        && (fromRotation % 2 != toRotation % 2)) {
                    // Check if the task bounds on the rotated display will be out of bounds.
                    // If so, then update task bounds to be within reachable area.
                    final Rect taskBounds = new Rect(
                            taskInfo.configuration.windowConfiguration.getBounds());
                    if (DragPositioningCallbackUtility.snapTaskBoundsIfNecessary(
                            taskBounds, decoration.calculateValidDragArea())) {
                        t.setBounds(taskInfo.token, taskBounds);
                    }
                }
            }
        };
        mTaskPositionerFactory = taskPositionerFactory;
        mFocusTransitionObserver = focusTransitionObserver;
        mDesktopModeEventLogger = desktopModeEventLogger;
        mDesktopModeUiEventLogger = desktopModeUiEventLogger;
        mTaskResourceLoader = taskResourceLoader;
        mRecentsTransitionHandler = recentsTransitionHandler;
        mDesktopModeCompatPolicy = desktopModeCompatPolicy;
        mDesktopTilingDecorViewModel = desktopTilingDecorViewModel;
        mDesktopTasksController.setSnapEventHandler(this);
        mMultiDisplayDragMoveIndicatorController = multiDisplayDragMoveIndicatorController;
        mLatencyTracker = LatencyTracker.getInstance(mContext);
        mDesksOrganizer = desksOrganizer;
        mDesktopState = desktopState;
        mDesktopConfig = desktopConfig;
        mWindowDecorationActions =
                new DefaultWindowDecorationActions(this, mDesktopTasksController,
                        mContext, mDesktopModeUiEventLogger, mCompatUI);
        mGestureExclusionTracker =
                new WindowDecorationGestureExclusionTracker(mContext, mWindowManager,
                        mDisplayController, mMainExecutor, shellInit, (displayId, region) -> {
                    onExclusionRegionChanged(displayId, region);
                    return Unit.INSTANCE;
                });
        shellInit.addInitCallback(this::onInit, this);
    }

    @OptIn(markerClass = ExperimentalCoroutinesApi.class)
    private void onInit() {
        mShellController.addKeyguardChangeListener(mDesktopModeKeyguardChangeListener);
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mDisplayInsetsController.addGlobalInsetsChangedListener(
                new DesktopModeOnInsetsChangedListener());
        mDesktopTasksController.setOnTaskResizeAnimationListener(
                new DesktopModeOnTaskResizeAnimationListener());
        mDesktopTasksController.setOnTaskRepositionAnimationListener(
                new DesktopModeOnTaskRepositionAnimationListener());
        if (DesktopModeFlags.ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX.isTrue()
                || DesktopModeFlags.ENABLE_INPUT_LAYER_TRANSITION_FIX.isTrue()) {
            mRecentsTransitionHandler.addTransitionStateListener(
                    new DesktopModeRecentsTransitionStateListener());
        }
        mDisplayController.addDisplayChangingController(mOnDisplayChangingListener);
        if (mDesktopState.canEnterDesktopModeOrShowAppHandle()
                && Flags.enableDesktopWindowingAppHandleEducation()) {
            mAppHandleEducationController.setAppHandleEducationTooltipCallbacks(
                    /* appHandleTooltipClickCallback= */(taskId) -> {
                        openHandleMenu(taskId);
                        return Unit.INSTANCE;
                    },
                    /* onToDesktopClickCallback= */(taskId, desktopModeTransitionSource) -> {
                        moveToDesktop(taskId, desktopModeTransitionSource);
                        return Unit.INSTANCE;
                    });
        }
        mFocusTransitionObserver.setLocalFocusTransitionListener(this, mMainExecutor);
        mDesksOrganizer.setOnDesktopTaskInfoChangedListener((taskInfo) -> {
            onTaskInfoChanged(taskInfo);
            return Unit.INSTANCE;
        });
    }

    @Override
    public void onFocusedTaskChanged(RunningTaskInfo taskInfo, boolean isFocusedOnDisplay,
            boolean isFocusedGlobally) {
        final WindowDecoration decor = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decor != null) {
            decor.relayout(decor.mTaskInfo, isFocusedGlobally, decor.mExclusionRegion);
        }
    }

    @Override
    public void setFreeformTaskTransitionStarter(FreeformTaskTransitionStarter transitionStarter) {
        mTaskOperations = new TaskOperations(transitionStarter, mContext);
        mDesktopTasksController.setFreeformTaskTransitionStarter(transitionStarter);
    }

    @Override
    public void setSplitScreenController(SplitScreenController splitScreenController) {
        mSplitScreenController = splitScreenController;
        mAppHandleAndHeaderVisibilityHelper.setSplitScreenController(splitScreenController);
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

        if (taskInfo.displayId != oldTaskInfo.displayId
                && !DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
            removeTaskFromEventReceiver(oldTaskInfo.displayId);
            incrementEventReceiverTasks(taskInfo.displayId);
        }
        if (ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            // Pass the current global focus status to avoid updates outside of a ShellTransition.
            decoration.relayout(taskInfo, decoration.mHasGlobalFocus, decoration.mExclusionRegion);
        } else {
            decoration.relayout(taskInfo, taskInfo.isFocused, decoration.mExclusionRegion);
        }
        mDesktopTilingDecorViewModel.onTaskInfoChange(taskInfo);
        mActivityOrientationChangeHandler.ifPresent(handler ->
                handler.handleActivityOrientationChange(oldTaskInfo, taskInfo));
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
            initializeTiling(taskInfo);
        } else {
            decoration.relayout(taskInfo, startT, finishT, false /* applyStartTransactionOnDraw */,
                    false /* shouldSetTaskPositionAndCrop */,
                    mFocusTransitionObserver.hasGlobalFocus(taskInfo),
                    mGestureExclusionTracker.getExclusionRegion(taskInfo.displayId),
                    /* inSyncWithTransition= */ true);
        }
    }

    private void initializeTiling(RunningTaskInfo taskInfo) {
        DesktopRepository taskRepository = mDesktopUserRepositories.getCurrent();
        Integer deskId = taskRepository.getActiveDeskId(taskInfo.displayId);
        if (deskId == null) {
            return;
        }
        Integer leftTiledTaskId = taskRepository.getLeftTiledTask(deskId);
        Integer rightTiledTaskId = taskRepository.getRightTiledTask(deskId);
        boolean tilingAndPersistenceEnabled = DesktopExperienceFlags.ENABLE_TILE_RESIZING.isTrue()
                && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue();
        if (leftTiledTaskId != null && leftTiledTaskId == taskInfo.taskId
                && tilingAndPersistenceEnabled) {
            snapPersistedTaskToHalfScreen(
                    taskInfo,
                    taskInfo.configuration.windowConfiguration.getBounds(),
                    SnapPosition.LEFT
            );
        }
        if (rightTiledTaskId != null && rightTiledTaskId == taskInfo.taskId
                && tilingAndPersistenceEnabled) {
            snapPersistedTaskToHalfScreen(
                    taskInfo,
                    taskInfo.configuration.windowConfiguration.getBounds(),
                    SnapPosition.RIGHT
            );
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
                false /* shouldSetTaskPositionAndCrop */,
                mFocusTransitionObserver.hasGlobalFocus(taskInfo),
                mGestureExclusionTracker.getExclusionRegion(taskInfo.displayId),
                /* inSyncWithTransition= */ true);
    }

    @Override
    public void destroyWindowDecoration(RunningTaskInfo taskInfo) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) return;

        decoration.close();
        final int displayId = taskInfo.displayId;
        if (mEventReceiversByDisplay.contains(displayId)
                && !DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
            removeTaskFromEventReceiver(displayId);
        }
        // Remove the decoration from the cache last because WindowDecoration#close could still
        // issue CANCEL MotionEvents to touch listeners before its view host is released.
        // See b/327664694.
        mWindowDecorByTaskId.remove(taskInfo.taskId);
    }

    private void onExclusionRegionChanged(int displayId, @NonNull Region exclusionRegion) {
        final int decorCount = mWindowDecorByTaskId.size();
        for (int i = 0; i < decorCount; i++) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.valueAt(i);
            if (decoration.mTaskInfo.displayId != displayId) continue;
            decoration.onExclusionRegionChanged(exclusionRegion);
        }
    }

    private void openHandleMenu(int taskId) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        // TODO(b/379873022): Run the instance check and the AssistContent request in
        //  createHandleMenu on the same bg thread dispatch.
        mBgExecutor.execute(() -> {
            final int numOfInstances = checkNumberOfOtherInstances(decoration.mTaskInfo);
            mMainExecutor.execute(() -> {
                decoration.createHandleMenu(
                        numOfInstances >= MANAGE_WINDOWS_MINIMUM_INSTANCES
                );
            });
        });
    }

    private void onToggleSizeInteraction(
            int taskId, @NonNull ToggleTaskSizeInteraction.AmbiguousSource source,
            @Nullable InputMethod inputMethod) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        final ToggleTaskSizeInteraction interaction =
                createToggleSizeInteraction(decoration, source, inputMethod);
        if (interaction == null) {
            return;
        }
        if (interaction.getCujTracing() != null) {
            mInteractionJankMonitor.begin(
                    decoration.mTaskSurface, mContext, mMainHandler,
                    interaction.getCujTracing(), interaction.getJankTag());
        }
        mDesktopTasksController.toggleDesktopTaskSize(decoration.mTaskInfo, interaction);
    }

    private ToggleTaskSizeInteraction createToggleSizeInteraction(
            @NonNull DesktopModeWindowDecoration decoration,
            @NonNull ToggleTaskSizeInteraction.AmbiguousSource source,
            @Nullable InputMethod inputMethod) {
        final RunningTaskInfo taskInfo = decoration.mTaskInfo;

        final DisplayLayout displayLayout = mDisplayController.getDisplayLayout(taskInfo.displayId);
        if (displayLayout == null) {
            return null;
        }
        final Rect stableBounds = new Rect();
        displayLayout.getStableBounds(stableBounds);
        boolean isMaximized = DesktopModeUtils.isTaskMaximized(taskInfo, stableBounds);

        return new ToggleTaskSizeInteraction(
                isMaximized
                        ? ToggleTaskSizeInteraction.Direction.RESTORE
                        : ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeUtilsKt.toSource(source, isMaximized),
                inputMethod
        );
    }

    private void onEnterOrExitImmersive(RunningTaskInfo taskInfo) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) {
            return;
        }
        final DesktopRepository desktopRepository = mDesktopUserRepositories.getProfile(
                taskInfo.userId);
        if (desktopRepository.isTaskInFullImmersiveState(taskInfo.taskId)) {
            mDesktopModeUiEventLogger.log(decoration.mTaskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_RESTORE);
            mDesktopImmersiveController.moveTaskToNonImmersive(
                    decoration.mTaskInfo, DesktopImmersiveController.ExitReason.USER_INTERACTION);
        } else {
            mDesktopModeUiEventLogger.log(decoration.mTaskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_IMMERSIVE);
            removeTaskIfTiled(decoration.mTaskInfo.displayId, decoration.mTaskInfo.taskId);
            mDesktopImmersiveController.moveTaskToImmersive(decoration.mTaskInfo);
        }
    }

    /** Snap-resize a task to the left or right side of the desktop. */
    public void onSnapResize(int taskId, boolean left, InputMethod inputMethod, boolean fromMenu) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }

        if (fromMenu) {
            final DesktopModeUiEventLogger.DesktopUiEventEnum event = left
                    ? DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_TILE_TO_LEFT
                    : DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_TILE_TO_RIGHT;
            mDesktopModeUiEventLogger.log(decoration.mTaskInfo, event);
        }

        mInteractionJankMonitor.begin(decoration.mTaskSurface, mContext, mMainHandler,
                Cuj.CUJ_DESKTOP_MODE_SNAP_RESIZE, "maximize_menu_resizable");
        mDesktopTasksController.handleInstantSnapResizingTask(
                decoration.mTaskInfo,
                left ? SnapPosition.LEFT : SnapPosition.RIGHT,
                left ? ResizeTrigger.SNAP_LEFT_MENU : ResizeTrigger.SNAP_RIGHT_MENU,
                inputMethod);
    }

    private void openInBrowser(int taskId, @NonNull Intent intent) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        openInBrowser(intent, decoration.getUser(), decoration.mTaskInfo.displayId);
    }

    private void openInBrowser(
            @NonNull Intent intent, @NonNull UserHandle userHandle, int displayId) {
        final ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId);
        mContext.startActivityAsUser(intent, options.toBundle(), userHandle);
    }

    private void moveToDesktop(int taskId, DesktopModeTransitionSource source) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        mInteractionJankMonitor.begin(decoration.mTaskSurface, mContext, mMainHandler,
                CUJ_DESKTOP_MODE_ENTER_MODE_APP_HANDLE_MENU);
        mLatencyTracker.onActionStart(LatencyTracker.ACTION_DESKTOP_MODE_ENTER_APP_HANDLE_MENU);
        // App sometimes draws before the insets from WindowDecoration#relayout have
        // been added, so they must be added here
        decoration.addCaptionInset(wct);
        if (!mDesktopTasksController.moveTaskToDefaultDeskAndActivate(
                taskId,
                wct,
                source,
                /* remoteTransition= */ null,
                /* moveToDesktopCallback= */ null)) {
            mLatencyTracker.onActionCancel(
                    LatencyTracker.ACTION_DESKTOP_MODE_ENTER_APP_HANDLE_MENU);
        }

        if (source == DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON) {
            mDesktopModeUiEventLogger.log(decoration.mTaskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_DESKTOP_MODE);
        }
    }

    private void onManageWindows(int taskId) {
        final DesktopModeWindowDecoration decor = mWindowDecorByTaskId.get(taskId);
        mBgExecutor.execute(() -> {
            final ArrayList<Pair<Integer, TaskSnapshot>> snapshotList =
                    getTaskSnapshots(decor.mTaskInfo);
            if (!snapshotList.isEmpty()) {
                mMainExecutor.execute(() -> decor.createManageWindowsMenu(snapshotList));
            }
        });
    }

    private void moveToFullscreen(int taskId) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        if (isTaskInSplitScreen(taskId)) {
            mSplitScreenController.moveTaskToFullscreen(taskId,
                    SplitScreenController.EXIT_REASON_DESKTOP_MODE);
        } else {
            mDesktopTasksController.moveToFullscreen(taskId,
                    DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON,
                    /* remoteTransition= */ null);
        }
        mDesktopModeUiEventLogger.log(decoration.mTaskInfo,
                DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_FULL_SCREEN);
    }

    private void moveToSplit(int taskId) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        mDesktopTasksController.requestSplit(decoration.mTaskInfo, false /* leftOrTop */);
        mDesktopModeUiEventLogger.log(decoration.mTaskInfo,
                DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_SPLIT_SCREEN);
    }

    private void moveToFloat(int taskId) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        // When the app enters float, the handle will no longer be visible, meaning
        // we shouldn't receive input for it any longer.
        decoration.disposeStatusBarInputLayer();
        mDesktopTasksController.requestFloat(decoration.mTaskInfo);
    }

    private void launchNewWindow(int taskId) {
        final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        mDesktopTasksController.openNewWindow(decoration.mTaskInfo);
        mDesktopModeUiEventLogger.log(decoration.mTaskInfo,
                DesktopUiEventEnum.DESKTOP_WINDOW_MULTI_INSTANCE_NEW_WINDOW_CLICK);
    }

    private ArrayList<Pair<Integer, TaskSnapshot>> getTaskSnapshots(
            @NonNull RunningTaskInfo callerTaskInfo
    ) {
        final ArrayList<Pair<Integer, TaskSnapshot>> snapshotList = new ArrayList<>();
        final IActivityManager activityManager = ActivityManager.getService();
        final IActivityTaskManager activityTaskManagerService = ActivityTaskManager.getService();
        final List<ActivityManager.RecentTaskInfo> recentTasks;
        try {
            recentTasks = mActivityTaskManager.getRecentTasks(
                    Integer.MAX_VALUE,
                    ActivityManager.RECENT_WITH_EXCLUDED,
                    activityManager.getCurrentUser().id);
        } catch (RemoteException e) {
            ProtoLog.e(WM_SHELL_DESKTOP_MODE,
                    "%s: Error getting recent tasks: %s", TAG, e);
            return new ArrayList<>();
        }
        final String callerPackageName = callerTaskInfo.baseActivity.getPackageName();
        for (ActivityManager.RecentTaskInfo info : recentTasks) {
            if (info.baseActivity == null) continue;
            final String infoPackageName = info.baseActivity.getPackageName();
            if (!infoPackageName.equals(callerPackageName)) {
                continue;
            }
            // TODO(b/337903443): Fix this returning null for freeform tasks.
            try {
                TaskSnapshot screenshot = activityTaskManagerService
                        .getTaskSnapshot(info.taskId, false);
                if (screenshot == null) {
                    screenshot = activityTaskManagerService
                            .takeTaskSnapshot(info.taskId, false);
                }
                snapshotList.add(new Pair(info.taskId, screenshot));
            } catch (RemoteException e) {
                ProtoLog.e(WM_SHELL_DESKTOP_MODE,
                        "%s: Error getting task snapshot for task %d: %s", TAG, info.taskId, e);
                return new ArrayList<>();
            }
        }
        return snapshotList;
    }

    @Override
    public boolean snapToHalfScreen(@NonNull RunningTaskInfo taskInfo,
            @NonNull Rect currentDragBounds, @NonNull SnapPosition position) {
        return mDesktopTilingDecorViewModel.snapToHalfScreen(taskInfo,
                mWindowDecorByTaskId.get(taskInfo.taskId), position, currentDragBounds, null);
    }

    @Override
    public boolean snapPersistedTaskToHalfScreen(@NotNull RunningTaskInfo taskInfo,
            @NotNull Rect currentDragBounds, @NotNull SnapPosition position) {
        return mDesktopTilingDecorViewModel.snapToHalfScreen(taskInfo,
                mWindowDecorByTaskId.get(taskInfo.taskId), position, currentDragBounds,
                currentDragBounds);
    }

    @Override
    public void onDeskActivated(int deskId, int displayId) {
        if (!mDesktopTilingDecorViewModel.onDeskActivated(deskId)) {
            return;
        }

        final DesktopRepository repository = mDesktopUserRepositories.getCurrent();
        final Integer leftTaskId = repository.getLeftTiledTask(deskId);
        final Integer rightTaskId =  repository.getRightTiledTask(deskId);
        if (leftTaskId != null) {
            final DesktopModeWindowDecoration decor = mWindowDecorByTaskId.get(leftTaskId);
            final RunningTaskInfo taskInfo = decor.mTaskInfo;
            final Rect currentBounds = taskInfo.configuration.windowConfiguration.getBounds();
            snapPersistedTaskToHalfScreen(taskInfo, currentBounds, SnapPosition.LEFT);
        }

        if (rightTaskId != null) {
            final DesktopModeWindowDecoration decor = mWindowDecorByTaskId.get(rightTaskId);
            final RunningTaskInfo taskInfo = decor.mTaskInfo;
            final Rect currentBounds = taskInfo.configuration.windowConfiguration.getBounds();
            snapPersistedTaskToHalfScreen(taskInfo, currentBounds, SnapPosition.RIGHT);
        }
    }

    @Override
    public void removeTaskIfTiled(int displayId, int taskId) {
        mDesktopTilingDecorViewModel.removeTaskIfTiled(displayId, taskId);
    }

    @Override
    public void onUserChange(int userId) {
        mDesktopTilingDecorViewModel.onUserChange(userId);
    }

    @Override
    public void onRecentsAnimationEndedToSameDesk() {
        mDesktopTilingDecorViewModel.onOverviewAnimationEndedToSameDesk();
    }

    @Override
    public boolean moveTaskToFrontIfTiled(@NonNull RunningTaskInfo taskInfo) {
        return mDesktopTilingDecorViewModel.moveTaskToFrontIfTiled(taskInfo);
    }

    @Override
    @NotNull
    public Rect getLeftSnapBoundsIfTiled(int displayId) {
        return mDesktopTilingDecorViewModel.getLeftSnapBoundsIfTiled(displayId);
    }

    @Override
    @NotNull
    public Rect getRightSnapBoundsIfTiled(int displayId) {
        return mDesktopTilingDecorViewModel.getRightSnapBoundsIfTiled(displayId);
    }

    @Override
    public void onDeskDeactivated(int deskId) {
        mDesktopTilingDecorViewModel.onDeskDeactivated(deskId);
    }

    @Override
    public void onDisplayDisconnected(int disconnectedDisplayId,
            boolean desktopModeSupportedOnNewDisplay) {
        mDesktopTilingDecorViewModel.onDisplayDisconnected(disconnectedDisplayId,
                desktopModeSupportedOnNewDisplay);
    }

    @Override
    public void onDeskRemoved(int deskId) {
        mDesktopTilingDecorViewModel.onDeskRemoved(deskId);
    }

    @VisibleForTesting
    public class DesktopModeTouchEventListener extends GestureDetector.SimpleOnGestureListener
            implements View.OnClickListener, View.OnTouchListener, View.OnLongClickListener,
            View.OnGenericMotionListener, DragDetector.MotionEventHandler {
        private static final long APP_HANDLE_HOLD_TO_DRAG_DURATION_MS = 100;
        private static final long APP_HEADER_HOLD_TO_DRAG_DURATION_MS = 0;

        private final int mTaskId;
        private final WindowContainerToken mTaskToken;
        private final DragPositioningCallback mDragPositioningCallback;
        private final DragDetector mHandleDragDetector;
        private final DragDetector mHeaderDragDetector;
        private final GestureDetector mGestureDetector;
        private final int mDisplayId;
        private final Rect mOnDragStartInitialBounds = new Rect();
        private final Rect mCurrentBounds = new Rect();

        /**
         * Whether to pilfer the next motion event to send cancellations to the windows below.
         * Useful when the caption window is spy and the gesture should be handled by the system
         * instead of by the app for their custom header content.
         * Should not have any effect when
         * {@link DesktopModeFlags#ENABLE_ACCESSIBLE_CUSTOM_HEADERS}, because a spy window is not
         * used then.
         */
        private boolean mIsCustomHeaderGesture;
        private boolean mIsResizeGesture;
        private boolean mIsDragging;
        private boolean mDragInterrupted;
        private boolean mLongClickDisabled;
        private int mDragPointerId = -1;
        private MotionEvent mMotionEvent;
        private int mCurrentPointerIconType = PointerIcon.TYPE_ARROW;

        private DesktopModeTouchEventListener(
                RunningTaskInfo taskInfo,
                DragPositioningCallback dragPositioningCallback) {
            mTaskId = taskInfo.taskId;
            mTaskToken = taskInfo.token;
            mDragPositioningCallback = dragPositioningCallback;
            final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
            final long appHandleHoldToDragDuration =
                    DesktopModeFlags.ENABLE_HOLD_TO_DRAG_APP_HANDLE.isTrue()
                            ? APP_HANDLE_HOLD_TO_DRAG_DURATION_MS : 0;
            mHandleDragDetector = new DragDetector(this, appHandleHoldToDragDuration,
                    touchSlop);
            mHeaderDragDetector = new DragDetector(this, APP_HEADER_HOLD_TO_DRAG_DURATION_MS,
                    touchSlop);
            mGestureDetector = new GestureDetector(mContext, this);
            mDisplayId = taskInfo.displayId;
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
                    if (DesktopExperienceFlags
                            .ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS.isTrue()) {
                        final int nextFocusedTaskId =
                                mDesktopTasksController.getNextFocusedTask(decoration.mTaskInfo);
                        DesktopModeWindowDecoration nextFocusedWindow =
                                mWindowDecorByTaskId.get(nextFocusedTaskId);
                        if (nextFocusedWindow != null) {
                            nextFocusedWindow.a11yAnnounceNewFocusedWindow();
                        }
                    }
                    WindowContainerTransaction wct = new WindowContainerTransaction();
                    final Function1<IBinder, Unit> runOnTransitionStart =
                            mDesktopTasksController.onDesktopWindowClose(
                                    wct, mDisplayId, decoration.mTaskInfo);
                    final IBinder transition = mTaskOperations.closeTask(mTaskToken, wct);
                    if (transition != null) {
                        runOnTransitionStart.invoke(transition);
                    }
                }
            } else if (id == R.id.back_button) {
                mTaskOperations.injectBackKey(mDisplayId);
            } else if (id == R.id.caption_handle || id == R.id.open_menu_button) {
                if (id == R.id.caption_handle && !decoration.mTaskInfo.isFreeform()) {
                    // Clicking the App Handle.
                    mDesktopModeUiEventLogger.log(decoration.mTaskInfo,
                            DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_TAP);
                }
                if (!decoration.isHandleMenuActive()) {
                    moveTaskToFront(decoration.mTaskInfo);
                    openHandleMenu(mTaskId);
                }
            } else if (id == R.id.maximize_window) {
                // TODO(b/346441962): move click detection logic into the decor's
                //  {@link AppHeaderViewHolder}. Let it encapsulate the that and have it report
                //  back to the decoration using
                //  {@link DesktopModeWindowDecoration#setOnMaximizeOrRestoreClickListener}, which
                //  should shared with the maximize menu's maximize/restore actions.
                final DesktopRepository desktopRepository = mDesktopUserRepositories.getProfile(
                        decoration.mTaskInfo.userId);
                if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()
                        && desktopRepository.isTaskInFullImmersiveState(
                        decoration.mTaskInfo.taskId)) {
                    // Task is in immersive and should exit.
                    onEnterOrExitImmersive(decoration.mTaskInfo);
                } else {
                    // Full immersive is disabled or task doesn't request/support it, so just
                    // toggle between maximize/restore states.
                    onToggleSizeInteraction(decoration.mTaskInfo.taskId,
                            ToggleTaskSizeInteraction.AmbiguousSource.HEADER_BUTTON,
                            getInputMethod(mMotionEvent));
                }
            } else if (id == R.id.minimize_window) {
                if (DesktopExperienceFlags
                        .ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS.isTrue()) {
                    final int nextFocusedTaskId = mDesktopTasksController
                            .getNextFocusedTask(decoration.mTaskInfo);
                    DesktopModeWindowDecoration nextFocusedWindow =
                            mWindowDecorByTaskId.get(nextFocusedTaskId);
                    if (nextFocusedWindow != null) {
                        nextFocusedWindow.a11yAnnounceNewFocusedWindow();
                    }
                }
                mDesktopTasksController.minimizeTask(
                        decoration.mTaskInfo, MinimizeReason.MINIMIZE_BUTTON);
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            mMotionEvent = e;
            final int id = v.getId();
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
            final boolean touchscreenSource =
                    (e.getSource() & SOURCE_TOUCHSCREEN) == SOURCE_TOUCHSCREEN;
            // Disable long click during events from a non-touchscreen source
            mLongClickDisabled = !touchscreenSource && e.getActionMasked() != ACTION_UP
                    && e.getActionMasked() != ACTION_CANCEL;

            if (id != R.id.caption_handle && id != R.id.desktop_mode_caption
                    && id != R.id.open_menu_button && id != R.id.close_window
                    && id != R.id.maximize_window && id != R.id.minimize_window) {
                return false;
            }
            final boolean isAppHandle = !getTaskInfo().isFreeform();
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
                moveTaskToFront(decoration.mTaskInfo);

                final boolean downInCustomizableCaptionRegion =
                        decoration.checkTouchEventInCustomizableRegion(e);
                final Region exclusionRegion = mGestureExclusionTracker
                        .getExclusionRegion(e.getDisplayId());
                final boolean downInExclusionRegion =
                        exclusionRegion.contains((int) e.getRawX(), (int) e.getRawY());
                final boolean isTransparentCaption =
                        TaskInfoKt.isTransparentCaptionBarAppearance(decoration.mTaskInfo);
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
                return false;
            }
            if (mInputManager != null
                    && !DesktopModeFlags.ENABLE_ACCESSIBLE_CUSTOM_HEADERS.isTrue()) {
                ViewRootImpl viewRootImpl = v.getViewRootImpl();
                if (viewRootImpl != null) {
                    // Pilfer so that windows below receive cancellations for this gesture.
                    mInputManager.pilferPointers(viewRootImpl.getInputToken());
                }
            }
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

        @Override
        public boolean onLongClick(View v) {
            final int id = v.getId();
            if (id == R.id.maximize_window && !mLongClickDisabled) {
                final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
                moveTaskToFront(decoration.mTaskInfo);
                if (!decoration.isMaximizeMenuActive()) {
                    decoration.createMaximizeMenu();
                }
                return true;
            }
            return false;
        }

        /**
         * TODO(b/346441962): move this hover detection logic into the decor's
         * {@link AppHeaderViewHolder}.
         */
        @Override
        public boolean onGenericMotion(View v, MotionEvent ev) {
            mMotionEvent = ev;
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
            final int id = v.getId();
            if (ev.getAction() == ACTION_HOVER_ENTER && id == R.id.maximize_window) {
                decoration.setAppHeaderMaximizeButtonHovered(true);
                if (!decoration.isMaximizeMenuActive()) {
                    decoration.onMaximizeButtonHoverEnter();
                }
                return true;
            }
            if (ev.getAction() == ACTION_HOVER_EXIT && id == R.id.maximize_window) {
                decoration.setAppHeaderMaximizeButtonHovered(false);
                decoration.onMaximizeHoverStateChanged();
                if (!decoration.isMaximizeMenuActive()) {
                    decoration.onMaximizeButtonHoverExit();
                }
                return true;
            }
            return false;
        }

        private void moveTaskToFront(RunningTaskInfo taskInfo) {
            if (!mFocusTransitionObserver.hasGlobalFocus(taskInfo)) {
                ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                        "%s: task#%d in display#%d does not have global focus, moving to front "
                                + "globallyFocusedTaskId=%d globallyFocusedDisplayId=%d",
                        TAG, taskInfo.taskId, taskInfo.displayId,
                        mFocusTransitionObserver.getGloballyFocusedTaskId(),
                        mFocusTransitionObserver.getGloballyFocusedDisplayId());
                mDesktopModeUiEventLogger.log(taskInfo,
                        DesktopUiEventEnum.DESKTOP_WINDOW_HEADER_TAP_TO_REFOCUS);
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
            if (mDesktopState.canEnterDesktopModeOrShowAppHandle()
                    && !taskInfo.isFreeform()) {
                return handleNonFreeformMotionEvent(decoration, v, e);
            } else {
                return handleFreeformMotionEvent(decoration, taskInfo, v, e);
            }
        }

        @NonNull
        private RunningTaskInfo getTaskInfo() {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(mTaskId);
            return decoration.mTaskInfo;
        }

        private boolean handleNonFreeformMotionEvent(DesktopModeWindowDecoration decoration,
                View v, MotionEvent e) {
            final int id = v.getId();
            if (id == R.id.caption_handle) {
                handleCaptionThroughStatusBar(e, decoration,
                        /* interruptDragCallback= */
                        () -> {
                            mDragInterrupted = true;
                            setIsDragging(decoration, /* isDragging= */ false);
                        });
                final boolean wasDragging = mIsDragging;
                updateDragStatus(decoration, e);
                final boolean upOrCancel = e.getActionMasked() == ACTION_UP
                        || e.getActionMasked() == ACTION_CANCEL;
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
            return false;
        }

        private void setIsDragging(
                @Nullable DesktopModeWindowDecoration decor, boolean isDragging) {
            mIsDragging = isDragging;
            if (decor == null) return;
            decor.setIsDragging(isDragging);
        }

        private boolean handleFreeformMotionEvent(DesktopModeWindowDecoration decoration,
                RunningTaskInfo taskInfo, View v, MotionEvent e) {
            final int id = v.getId();
            if (mGestureDetector.onTouchEvent(e)) {
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
                                e.getRawY(0));
                        updateDragStatus(decoration, e);
                        mOnDragStartInitialBounds.set(initialBounds);
                        mCurrentBounds.set(initialBounds);
                    }
                    // Do not consume input event if a button is touched, otherwise it would
                    // prevent the button's ripple effect from showing.
                    return !touchingButton;
                }
                case ACTION_MOVE: {
                    // If a decor's resize drag zone is active, don't also try to reposition it.
                    if (decoration.isHandlingDragResize()) break;
                    // Dragging the header isn't allowed, so skip the positioning work.
                    if (!dragAllowed) break;

                    if (e.findPointerIndex(mDragPointerId) == -1) {
                        mDragPointerId = e.getPointerId(0);
                    }
                    final int dragPointerIdx = e.findPointerIndex(mDragPointerId);

                    if (DesktopExperienceFlags
                            .ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX.isTrue()) {
                        final boolean inDesktopModeDisplay = isDisplayInDesktopMode(
                                e.getDisplayId());
                        // TODO: b/418651425 - Use a more specific pointer icon when available.
                        updatePointerIcon(e, dragPointerIdx, v.getViewRootImpl().getInputToken(),
                                inDesktopModeDisplay ? PointerIcon.TYPE_ARROW
                                        : PointerIcon.TYPE_NO_DROP);
                        // Allow bounds update only when cursor is on desktop-mode displays.
                        // Otherwise, ignore the MOVE event and the window holds its current bounds.
                        if (inDesktopModeDisplay) {
                            mCurrentBounds.set(mDragPositioningCallback.onDragPositioningMove(
                                    e.getDisplayId(),
                                    e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx)));
                        }
                    } else {
                        mCurrentBounds.set(mDragPositioningCallback.onDragPositioningMove(
                                e.getDisplayId(),
                                e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx)));
                    }

                    mDesktopTasksController.onDragPositioningMove(taskInfo,
                            decoration.getLeash(),
                            e.getDisplayId(),
                            e.getRawX(dragPointerIdx),
                            e.getRawY(dragPointerIdx),
                            mCurrentBounds);
                    //  Flip mIsDragging only if the bounds actually changed.
                    if (mIsDragging || !mCurrentBounds.equals(mOnDragStartInitialBounds)) {
                        updateDragStatus(decoration, e);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    final boolean wasDragging = mIsDragging;
                    if (!wasDragging) {
                        return false;
                    }
                    mDesktopModeUiEventLogger.log(taskInfo,
                            DesktopUiEventEnum.DESKTOP_WINDOW_MOVE_BY_HEADER_DRAG);
                    if (e.findPointerIndex(mDragPointerId) == -1) {
                        mDragPointerId = e.getPointerId(0);
                    }
                    final int dragPointerIdx = e.findPointerIndex(mDragPointerId);
                    final Rect newTaskBounds = mDragPositioningCallback.onDragPositioningEnd(
                            e.getDisplayId(),
                            e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx));

                    if (DesktopExperienceFlags
                            .ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX.isTrue()) {
                        updatePointerIcon(e, dragPointerIdx, v.getViewRootImpl().getInputToken(),
                                PointerIcon.TYPE_ARROW);
                        // If the cursor ends on a non-desktop-mode display, revert the window
                        // to its initial bounds prior to the drag starting.
                        if (!isDisplayInDesktopMode(e.getDisplayId())) {
                            newTaskBounds.set(mOnDragStartInitialBounds);
                        }
                    }

                    // Tasks bounds haven't actually been updated (only its leash), so pass to
                    // DesktopTasksController to allow secondary transformations (i.e. snap resizing
                    // or transforming to fullscreen) before setting new task bounds.
                    mDesktopTasksController.onDragPositioningEnd(
                            taskInfo, decoration.getLeash(),
                            e.getDisplayId(),
                            new PointF(e.getRawX(dragPointerIdx), e.getRawY(dragPointerIdx)),
                            newTaskBounds, decoration.calculateValidDragArea(),
                            new Rect(mOnDragStartInitialBounds), e);
                    if (touchingButton) {
                        // We need the input event to not be consumed here to end the ripple
                        // effect on the touched button. We will reset drag state in the ensuing
                        // onClick call that results.
                        return false;
                    } else {
                        updateDragStatus(decoration, e);
                        return true;
                    }
                }
            }
            return true;
        }

        private void updatePointerIcon(MotionEvent e, int dragPointerIdx, IBinder inputToken,
                int iconType) {
            if (mCurrentPointerIconType == iconType) {
                return;
            }
            mInputManager.setPointerIcon(PointerIcon.getSystemIcon(mContext, iconType),
                    e.getDisplayId(), e.getDeviceId(), e.getPointerId(dragPointerIdx), inputToken);
            mCurrentPointerIconType = iconType;
        }

        private boolean isDisplayInDesktopMode(int displayId) {
            return mDesktopState.isDesktopModeSupportedOnDisplay(displayId)
                    && mDesktopTasksController.getActiveDeskId(displayId) != null;
        }

        private void updateDragStatus(DesktopModeWindowDecoration decor, MotionEvent e) {
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
            onToggleSizeInteraction(mTaskId,
                    ToggleTaskSizeInteraction.AmbiguousSource.DOUBLE_TAP,
                    getInputMethod(mMotionEvent));
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
     *
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
        if (mDesktopState.canEnterDesktopMode()) {
            if (!mInImmersiveMode && (relevantDecor == null
                    || relevantDecor.mTaskInfo.getWindowingMode() != WINDOWING_MODE_FREEFORM
                    || mTransitionDragActive)) {
                handleCaptionThroughStatusBar(ev, relevantDecor,
                        /* interruptDragCallback= */ () -> {});
            }
        }
        handleEventOutsideCaption(ev, relevantDecor);
        // Prevent status bar from reacting to a caption drag.
        if (mDesktopState.canEnterDesktopMode()) {
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
    }


    /**
     * Perform caption actions if not able to through normal means.
     * Turn on desktop mode if handle is dragged below status bar.
     */
    private void handleCaptionThroughStatusBar(MotionEvent ev,
            DesktopModeWindowDecoration relevantDecor, Runnable interruptDragCallback) {
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
                final int windowingMode = relevantDecor.mTaskInfo.getWindowingMode();
                if (mDesktopState.canEnterDesktopMode()
                        || BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    // In proto2 any full screen or multi-window task can be dragged to
                    // freeform.
                    dragFromStatusBarAllowed = windowingMode == WINDOWING_MODE_FULLSCREEN
                            || windowingMode == WINDOWING_MODE_MULTI_WINDOW;
                }
                final boolean shouldStartTransitionDrag =
                        relevantDecor.checkTouchEventInFocusedCaptionHandle(ev)
                                || DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue();
                if (dragFromStatusBarAllowed && shouldStartTransitionDrag) {
                    mTransitionDragActive = true;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if (mTransitionDragActive) {
                    final DesktopModeVisualIndicator.DragStartState dragStartState =
                            DesktopModeVisualIndicator.DragStartState
                                    .getDragStartState(relevantDecor.mTaskInfo);
                    if (dragStartState == null) return;
                    mDesktopTasksController.updateVisualIndicator(relevantDecor.mTaskInfo,
                            relevantDecor.mTaskSurface, ev.getDisplayId(), ev.getRawX(),
                            ev.getRawY(), dragStartState);
                    mTransitionDragActive = false;
                    if (mMoveToDesktopAnimator != null) {
                        // Though this isn't a hover event, we need to update handle's hover state
                        // as it likely will change.
                        relevantDecor.updateHoverAndPressStatus(ev);
                        if (ev.getActionMasked() == ACTION_CANCEL) {
                            mDesktopTasksController.onDragPositioningCancelThroughStatusBar(
                                    relevantDecor.mTaskInfo);
                        } else {
                            endDragToDesktop(ev, relevantDecor);
                        }
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
                    // It's possible task is not at the top of the screen (e.g. bottom of vertical
                    // Splitscreen)
                    final int taskTop = relevantDecor.mTaskInfo.configuration.windowConfiguration
                            .getBounds().top;
                    if (ev.getRawY() < 2 * layout.stableInsets().top + taskTop
                            && mMoveToDesktopAnimator == null) {
                        return;
                    }
                    final DesktopModeVisualIndicator.DragStartState dragStartState =
                            DesktopModeVisualIndicator.DragStartState
                                    .getDragStartState(relevantDecor.mTaskInfo);
                    if (dragStartState == null) return;
                    final DesktopModeVisualIndicator.IndicatorType indicatorType =
                            mDesktopTasksController.updateVisualIndicator(
                                    relevantDecor.mTaskInfo, relevantDecor.mTaskSurface,
                                    ev.getDisplayId(), ev.getRawX(), ev.getRawY(),
                                    dragStartState);
                    if (indicatorType != TO_FULLSCREEN_INDICATOR
                            || BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                        if (mMoveToDesktopAnimator == null) {
                            mMoveToDesktopAnimator = new MoveToDesktopAnimator(
                                    mContext, mDragToDesktopAnimationStartBounds,
                                    relevantDecor.mTaskInfo, relevantDecor.mTaskSurface);
                            mDesktopTasksController.startDragToDesktop(relevantDecor.mTaskInfo,
                                    mMoveToDesktopAnimator, relevantDecor.mTaskSurface,
                                    /* dragInterruptedCallback= */ () -> {
                                        // Don't call into DesktopTasksController to cancel the
                                        // transition here - the transition handler already handles
                                        // that (including removing the visual indicator).
                                        mTransitionDragActive = false;
                                        mMoveToDesktopAnimator = null;
                                        relevantDecor.handleDragInterrupted();
                                        interruptDragCallback.run();
                                    });
                        }
                    }
                    if (mMoveToDesktopAnimator != null) {
                        mMoveToDesktopAnimator.updatePosition(ev);
                    }
                }
                break;
            }
        }
    }

    private void endDragToDesktop(MotionEvent ev, DesktopModeWindowDecoration relevantDecor) {
        DesktopModeVisualIndicator.IndicatorType resultType =
                mDesktopTasksController.onDragPositioningEndThroughStatusBar(
                        ev.getDisplayId(),
                        new PointF(ev.getRawX(), ev.getRawY()),
                        relevantDecor.mTaskInfo,
                        relevantDecor.mTaskSurface);
        // If we are entering split select, handle will no longer be visible and
        // should not be receiving any input.
        if (resultType == TO_SPLIT_LEFT_INDICATOR
                || resultType == TO_SPLIT_RIGHT_INDICATOR) {
            relevantDecor.disposeStatusBarInputLayer();
            // We should also dispose the other split task's input layer if
            // applicable.
            final int splitPosition = mSplitScreenController
                    .getSplitPosition(relevantDecor.mTaskInfo.taskId);
            if (splitPosition != SPLIT_POSITION_UNDEFINED) {
                final int oppositePosition =
                        splitPosition == SPLIT_POSITION_TOP_OR_LEFT
                                ? SPLIT_POSITION_BOTTOM_OR_RIGHT
                                : SPLIT_POSITION_TOP_OR_LEFT;
                final RunningTaskInfo oppositeTaskInfo =
                        mSplitScreenController.getTaskInfo(oppositePosition);
                if (oppositeTaskInfo != null) {
                    mWindowDecorByTaskId.get(oppositeTaskInfo.taskId)
                            .disposeStatusBarInputLayer();
                }
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
                mInputMonitorFactory.create(inputManager, displayId);
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
        return mAppHandleAndHeaderVisibilityHelper.shouldShowAppHandleOrHeader(taskInfo);
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
                        DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue()
                                ? mDisplayController.getDisplayContext(taskInfo.displayId)
                                : mContext,
                        mContext.createContextAsUser(UserHandle.of(taskInfo.userId), 0 /* flags */),
                        mDisplayController,
                        mTaskResourceLoader,
                        mSplitScreenController,
                        mDesktopUserRepositories,
                        mTaskOrganizer,
                        taskInfo,
                        taskSurface,
                        mMainHandler,
                        mMainExecutor,
                        mMainDispatcher,
                        mBgScope,
                        mBgExecutor,
                        mTransitions,
                        mMainChoreographer,
                        mSyncQueue,
                        mAppHeaderViewHolderFactory,
                        mAppHandleViewHolderFactory,
                        mRootTaskDisplayAreaOrganizer,
                        mGenericLinksParser,
                        mAssistContentRequester,
                        mWindowDecorViewHostSupplier,
                        mMultiInstanceHelper,
                        mWindowDecorCaptionRepository,
                        mDesktopModeEventLogger,
                        mDesktopModeUiEventLogger,
                        mDesktopModeCompatPolicy,
                        mDesktopState,
                        mDesktopConfig,
                        mWindowDecorationActions);
        mWindowDecorByTaskId.put(taskInfo.taskId, windowDecoration);

        final TaskPositioner taskPositioner = mTaskPositionerFactory.create(
                mTaskOrganizer,
                windowDecoration,
                mDisplayController,
                mTransitions,
                mInteractionJankMonitor,
                mTransactionFactory,
                mMainHandler,
                mMultiDisplayDragMoveIndicatorController,
                mDesktopState,
                mDesktopConfig);
        windowDecoration.setTaskDragResizer(taskPositioner);

        final DesktopModeTouchEventListener touchEventListener =
                new DesktopModeTouchEventListener(taskInfo, taskPositioner);
        windowDecoration.setCaptionListeners(
                touchEventListener, touchEventListener, touchEventListener, touchEventListener);
        windowDecoration.setExclusionRegionListener(mExclusionRegionListener);
        windowDecoration.setDragPositioningCallback(taskPositioner);
        windowDecoration.relayout(taskInfo, startT, finishT,
                false /* applyStartTransactionOnDraw */, false /* shouldSetTaskPositionAndCrop */,
                mFocusTransitionObserver.hasGlobalFocus(taskInfo),
                mGestureExclusionTracker.getExclusionRegion(taskInfo.displayId),
                /* inSyncWithTransition= */ true);
        if (!DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
            incrementEventReceiverTasks(taskInfo.displayId);
        }
    }

    @Nullable
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

    private void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + "DesktopModeWindowDecorViewModel");
        pw.println(innerPrefix + "DesktopModeStatus="
                + mDesktopState.canEnterDesktopMode());
        pw.println(innerPrefix + "mTransitionDragActive=" + mTransitionDragActive);
        pw.println(innerPrefix + "mEventReceiversByDisplay=" + mEventReceiversByDisplay);
        pw.println(innerPrefix + "mGestureExclusionTracker="
                + mGestureExclusionTracker);
    }

    private class DesktopModeOnTaskRepositionAnimationListener
            implements OnTaskRepositionAnimationListener {
        @Override
        public void onAnimationStart(int taskId) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration != null) {
                decoration.setAnimatingTaskResizeOrReposition(true);
            }
        }

        @Override
        public void onAnimationEnd(int taskId) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration != null) {
                decoration.setAnimatingTaskResizeOrReposition(false);
            }
        }
    }

    private class DesktopModeOnTaskResizeAnimationListener
            implements OnTaskResizeAnimationListener {
        @Override
        public void onAnimationStart(int taskId, Transaction t, Rect bounds) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration == null) {
                t.apply();
                return;
            }
            decoration.showResizeVeil(t, bounds);
            decoration.setAnimatingTaskResizeOrReposition(true);
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
            decoration.setAnimatingTaskResizeOrReposition(false);
        }
    }

    private class DesktopModeRecentsTransitionStateListener
            implements RecentsTransitionStateListener {
        final Set<Integer> mAnimatingTaskIds = new HashSet<>();

        @Override
        public void onTransitionStateChanged(int state) {
            switch (state) {
                case RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED:
                    for (int n = 0; n < mWindowDecorByTaskId.size(); n++) {
                        int taskId = mWindowDecorByTaskId.keyAt(n);
                        mAnimatingTaskIds.add(taskId);
                        setIsRecentsTransitionRunningForTask(taskId, true);
                    }
                    return;
                case RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING:
                    // No Recents transition running - clean up window decorations
                    for (int taskId : mAnimatingTaskIds) {
                        setIsRecentsTransitionRunningForTask(taskId, false);
                    }
                    mAnimatingTaskIds.clear();
                    return;
                default:
            }
        }

        private void setIsRecentsTransitionRunningForTask(int taskId, boolean isRecentsRunning) {
            final DesktopModeWindowDecoration decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration == null) return;
            decoration.setIsRecentsTransitionRunning(isRecentsRunning);
        }
    }

    /**
     * Gets the number of instances of a task running, not including the specified task itself.
     */
    private int checkNumberOfOtherInstances(@NonNull RunningTaskInfo info) {
        // TODO(b/336289597): Rather than returning number of instances, return a list of valid
        //  instances, then refer to the list's size and reuse the list for Manage Windows menu.
        final IActivityTaskManager activityTaskManager = ActivityTaskManager.getService();
        try {
            // TODO(b/389184897): Move the following into a helper method of
            //  RecentsTasksController, similar to #findTaskInBackground.
            final String packageName = ComponentUtils.getPackageName(info);
            return activityTaskManager.getRecentTasks(Integer.MAX_VALUE,
                    ActivityManager.RECENT_WITH_EXCLUDED,
                    info.userId).getList().stream().filter(
                    recentTaskInfo -> {
                        if (recentTaskInfo.taskId == info.taskId) {
                            return false;
                        }
                        final String recentTaskPackageName =
                                ComponentUtils.getPackageName(recentTaskInfo);
                        return packageName != null
                                && packageName.equals(recentTaskPackageName);
                    }
            ).toList().size();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    static class InputMonitorFactory {
        InputMonitor create(InputManager inputManager, int displayId) {
            return inputManager.monitorGestureInput("caption-touch", displayId);
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

    class DesktopModeKeyguardChangeListener implements KeyguardChangeListener {
        @Override
        public void onKeyguardVisibilityChanged(boolean visible, boolean occluded,
                boolean animatingDismiss) {
            final int size = mWindowDecorByTaskId.size();
            for (int i = size - 1; i >= 0; i--) {
                final DesktopModeWindowDecoration decor = mWindowDecorByTaskId.valueAt(i);
                if (decor != null) {
                    decor.onKeyguardStateChanged(visible, occluded);
                }
            }
        }
    }

    @VisibleForTesting
    class DesktopModeOnInsetsChangedListener implements
            DisplayInsetsController.OnInsetsChangedListener {
        @Override
        public void insetsChanged(int displayId, @NonNull InsetsState insetsState) {
            final int size = mWindowDecorByTaskId.size();
            for (int i = size - 1; i >= 0; i--) {
                final DesktopModeWindowDecoration decor = mWindowDecorByTaskId.valueAt(i);
                if (decor == null) {
                    continue;
                }
                if (decor.mTaskInfo.displayId == displayId
                        && DesktopModeFlags
                        .ENABLE_DESKTOP_WINDOWING_IMMERSIVE_HANDLE_HIDING.isTrue()) {
                    decor.onInsetsStateChanged(insetsState);
                }
                if (!DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
                    // If status bar inset is visible, top task is not in immersive mode.
                    // This value is only needed when the App Handle input is being handled
                    // through the global input monitor (hence the flag check) to ignore gestures
                    // when the app is in immersive mode. When disabled, the view itself handles
                    // input, and since it's removed when in immersive there's no need to track
                    // this here.
                    mInImmersiveMode = !InsetsStateKt.isVisible(insetsState, statusBars());
                }
            }
        }
    }

    private InputMethod getInputMethod(MotionEvent ev) {
        return DesktopModeEventLogger.getInputMethodFromMotionEvent(ev);
    }

    private static class DefaultWindowDecorationActions implements WindowDecorationActions {
        private final DesktopModeWindowDecorViewModel mViewModel;
        private final DesktopTasksController mDesktopTasksController;
        private final DesktopModeUiEventLogger mDesktopModeUiEventLogger;
        private final CompatUIHandler mCompatUI;
        private final Context mContext;

        DefaultWindowDecorationActions(
                @NonNull DesktopModeWindowDecorViewModel viewModel,
                @NonNull DesktopTasksController desktopTasksController,
                @NonNull Context context,
                @NonNull DesktopModeUiEventLogger desktopModeUiEventLogger,
                @NonNull CompatUIHandler compatUI
        ) {
            mViewModel = viewModel;
            mDesktopTasksController = desktopTasksController;
            mDesktopModeUiEventLogger = desktopModeUiEventLogger;
            mCompatUI = compatUI;
            mContext = context;
        }

        @Override
        public void onMaximizeOrRestore(int taskId,
                @NonNull DesktopModeEventLogger.Companion.InputMethod inputMethod) {
            mViewModel.onToggleSizeInteraction(taskId,
                    ToggleTaskSizeInteraction.AmbiguousSource.MAXIMIZE_MENU,
                    inputMethod);
        }

        @Override
        public void onMinimize(@NonNull RunningTaskInfo taskInfo) {
            if (DesktopExperienceFlags
                    .ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS.isTrue()) {
                final int nextFocusedTaskId = mDesktopTasksController.getNextFocusedTask(taskInfo);
                DesktopModeWindowDecoration nextFocusedWindow =
                        mViewModel.mWindowDecorByTaskId.get(nextFocusedTaskId);
                if (nextFocusedWindow != null) {
                    nextFocusedWindow.a11yAnnounceNewFocusedWindow();
                }
            }
            mDesktopTasksController.minimizeTask(taskInfo, MinimizeReason.MINIMIZE_BUTTON);
        }

        @Override
        public void onImmersiveOrRestore(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
            mViewModel.onEnterOrExitImmersive(taskInfo);
        }

        @Override
        public void onLeftSnap(int taskId,
                @NonNull DesktopModeEventLogger.Companion.InputMethod inputMethod) {
            mViewModel.onSnapResize(taskId, /* isLeft= */ true, inputMethod, /* fromMenu= */ true);
        }

        @Override
        public void onRightSnap(int taskId,
                @NonNull DesktopModeEventLogger.Companion.InputMethod inputMethod) {
            mViewModel.onSnapResize(taskId, /* isLeft= */ false, inputMethod, /* fromMenu= */ true);
        }

        @Override
        public void onToFullscreen(int taskId) {
            mViewModel.moveToFullscreen(taskId);
        }

        @Override
        public void onToSplitScreen(int taskId) {
            mViewModel.moveToSplit(taskId);
        }

        @Override
        public void onToDesktop(int taskId, @NonNull DesktopModeTransitionSource transitionSource) {
            mViewModel.moveToDesktop(taskId, transitionSource);
        }

        @Override
        public void onToFloat(int taskId) {
            mViewModel.moveToFloat(taskId);
        }

        @Override
        public void onOpenInBrowser(int taskId, @NonNull Intent intent) {
            mViewModel.openInBrowser(taskId, intent);
        }

        @Override
        public void onOpenInstance(@NonNull ActivityManager.RunningTaskInfo taskInfo,
                int requestedTaskId) {
            mDesktopTasksController.openInstance(taskInfo, requestedTaskId);
            mDesktopModeUiEventLogger.log(taskInfo,
                    DesktopUiEventEnum.DESKTOP_WINDOW_MULTI_INSTANCE_MANAGE_WINDOWS_ICON_CLICK);
        }

        @Override
        public void onManageWindows(int taskId) {
            mViewModel.onManageWindows(taskId);
        }

        @Override
        public void onRestart(int taskId) {
            mCompatUI.sendCompatUIRequest(new CompatUIRequests.DisplayCompatShowRestartDialog(
                    taskId));
        }

        @Override
        public void onChangeAspectRatio(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
            CompatUIController.launchUserAspectRatioSettings(mContext, taskInfo);
        }

        @Override
        public void onNewWindow(int taskId) {
            mViewModel.launchNewWindow(taskId);
        }
    }


    @VisibleForTesting
    static class TaskPositionerFactory {
        TaskPositioner create(
                ShellTaskOrganizer taskOrganizer,
                DesktopModeWindowDecoration windowDecoration,
                DisplayController displayController,
                Transitions transitions,
                InteractionJankMonitor interactionJankMonitor,
                Supplier<SurfaceControl.Transaction> transactionFactory,
                Handler handler,
                MultiDisplayDragMoveIndicatorController multiDisplayDragMoveIndicatorController,
                DesktopState desktopState,
                DesktopConfig desktopConfig) {
            final TaskPositioner taskPositioner = desktopConfig.isVeiledResizeEnabled()
                    // TODO(b/383632995): Update when the flag is launched.
                    ? (DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG.isTrue()
                        ? new MultiDisplayVeiledResizeTaskPositioner(
                            taskOrganizer,
                            windowDecoration,
                            displayController,
                            transitions,
                            interactionJankMonitor,
                            handler,
                            multiDisplayDragMoveIndicatorController,
                            desktopState)
                        : new VeiledResizeTaskPositioner(
                            taskOrganizer,
                            windowDecoration,
                            displayController,
                            transitions,
                            interactionJankMonitor,
                            handler,
                            desktopState))
                    : new FluidResizeTaskPositioner(
                            taskOrganizer,
                            transitions,
                            windowDecoration,
                            displayController,
                            transactionFactory,
                            desktopState);

            if (DesktopModeFlags.ENABLE_WINDOWING_SCALED_RESIZING.isTrue()) {
                return new FixedAspectRatioTaskPositionerDecorator(windowDecoration,
                        taskPositioner);
            }
            return taskPositioner;
        }
    }
}
