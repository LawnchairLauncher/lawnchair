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
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.WindowInsets.Type.statusBars;
import static android.window.DesktopExperienceFlags.ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS;

import static com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_MODE_APP_HANDLE_MENU;
import static com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_MOVE_FROM_SPLIT_SCREEN;
import static com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod;
import static com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason;
import static com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR;
import static com.android.wm.shell.desktopmode.DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOW_DECORATION;
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
import android.content.res.Configuration;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.InsetsState;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.WindowManager;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;
import android.window.TaskSnapshot;
import android.window.TaskSnapshotManager;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.jank.Cuj;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.policy.DesktopModeCompatPolicy;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.LatencyTracker;
import com.android.window.flags2.Flags;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.apptoweb.AppToWebGenericLinksParser;
import com.android.wm.shell.apptoweb.AppToWebRepository;
import com.android.wm.shell.apptoweb.AssistContentRequester;
import com.android.wm.shell.common.ComponentUtils;
import com.android.wm.shell.common.DisplayChangeController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.LockTaskChangeListener;
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController;
import com.android.wm.shell.common.MultiInstanceHelper;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.UserProfileContexts;
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
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopTasksController.SnapPosition;
import com.android.wm.shell.desktopmode.DesktopTasksLimiter;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.ShellDesktopState;
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository;
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction;
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeUtilsKt;
import com.android.wm.shell.desktopmode.data.DesktopRepository;
import com.android.wm.shell.desktopmode.education.AppHandleEducationController;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.recents.RecentsTransitionStateListener;
import com.android.wm.shell.shared.FocusTransitionListener;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.desktopmode.DesktopConfig;
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
import com.android.wm.shell.windowdecor.common.AppHandleAndHeaderVisibilityHelper;
import com.android.wm.shell.windowdecor.common.ExclusionRegionListener;
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader;
import com.android.wm.shell.windowdecor.common.WindowDecorationGestureExclusionTracker;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.extension.InsetsStateKt;
import com.android.wm.shell.windowdecor.tiling.DesktopTilingDecorViewModel;
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler;
import com.android.wm.shell.windowdecor.viewholder.AppHandleViewHolder;
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * View model for the window decoration with a caption and shadows. Works with
 * {@link DesktopModeWindowDecoration}.
 */

public class DesktopModeWindowDecorViewModel implements WindowDecorViewModel,
        FocusTransitionListener, SnapEventHandler,
        LockTaskChangeListener.LockTaskModeChangedListener,
        AppToWebRepository {
    private static final String TAG = "DesktopModeWindowDecorViewModel";

    private final WindowDecorationWrapper.Factory mWindowDecoratioWrapperFactory;
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
    private final @ShellMainThread CoroutineScope mMainScope;
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
    private final AppHandleAndHeaderVisibilityHelper mAppHandleAndHeaderVisibilityHelper;
    private final AppHeaderViewHolder.Factory mAppHeaderViewHolderFactory;
    private final AppHandleViewHolder.Factory mAppHandleViewHolderFactory;
    private final DesksOrganizer mDesksOrganizer;
    private final ShellDesktopState mShellDesktopState;
    private final DesktopConfig mDesktopConfig;
    private boolean mTransitionDragActive;

    private SparseArray<EventReceiver> mEventReceiversByDisplay = new SparseArray<>();

    private final ExclusionRegionListener mExclusionRegionListener =
            new ExclusionRegionListenerImpl();
    private final DragPositioningCallbackUtility.DragEventListener mDragEventListener =
            new DesktopModeDragEventListener();

    private final SparseArray<WindowDecorationWrapper> mWindowDecorByTaskId;
    private final Function<Integer, WindowDecorationWrapper> mWindowDecorationFinder =
            new Function<>() {
                @Override
                public WindowDecorationWrapper apply(Integer taskId) {
                    return mWindowDecorByTaskId.get(taskId);
                }
            };

    private final AppHandleMotionEventHandler mAppHandleMotionEventHandler =
            this::handleCaptionThroughStatusBar;

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
    private final AppToWebRepository mAppToWebRepository;
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
    private final UserProfileContexts mUserProfileContexts;
    private CaptionTouchStatusListener mCaptionTouchStatusListener;

    private final LockTaskChangeListener mLockTaskChangeListener;

    public DesktopModeWindowDecorViewModel(
            Context context,
            ShellExecutor shellExecutor,
            @ShellMainThread Handler mainHandler,
            Choreographer mainChoreographer,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellMainThread CoroutineScope mainScope,
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
            AppToWebRepository appToWebRepository,
            AssistContentRequester assistContentRequester,
            @NonNull WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            MultiInstanceHelper multiInstanceHelper,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            AppHandleEducationController appHandleEducationController,
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
            ShellDesktopState shellDesktopState,
            DesktopConfig desktopConfig,
            UserProfileContexts userProfileContexts,
            LockTaskChangeListener lockTaskChangeListener) {
        this(
                context,
                shellExecutor,
                mainHandler,
                mainChoreographer,
                mainDispatcher,
                mainScope,
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
                appToWebRepository,
                assistContentRequester,
                windowDecorViewHostSupplier,
                multiInstanceHelper,
                new WindowDecorationWrapper.Factory(),
                new InputMonitorFactory(),
                SurfaceControl.Transaction::new,
                new AppHeaderViewHolder.Factory(),
                new AppHandleViewHolder.Factory(),
                rootTaskDisplayAreaOrganizer,
                new SparseArray<>(),
                interactionJankMonitor,
                desktopTasksLimiter,
                appHandleEducationController,
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
                shellDesktopState,
                desktopConfig,
                userProfileContexts,
                lockTaskChangeListener);
    }

    @VisibleForTesting
    DesktopModeWindowDecorViewModel(
            Context context,
            ShellExecutor shellExecutor,
            @ShellMainThread Handler mainHandler,
            Choreographer mainChoreographer,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellMainThread CoroutineScope mainScope,
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
            AppToWebRepository appToWebRepository,
            AssistContentRequester assistContentRequester,
            @NonNull WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            MultiInstanceHelper multiInstanceHelper,
            WindowDecorationWrapper.Factory windowDecoratioWrapperFactory,
            InputMonitorFactory inputMonitorFactory,
            Supplier<SurfaceControl.Transaction> transactionFactory,
            AppHeaderViewHolder.Factory appHeaderViewHolderFactory,
            AppHandleViewHolder.Factory appHandleViewHolderFactory,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            SparseArray<WindowDecorationWrapper> decorationByTaskId,
            InteractionJankMonitor interactionJankMonitor,
            Optional<DesktopTasksLimiter> desktopTasksLimiter,
            AppHandleEducationController appHandleEducationController,
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
            ShellDesktopState shellDesktopState,
            DesktopConfig desktopConfig,
            UserProfileContexts userProfileContexts,
            LockTaskChangeListener lockTaskChangeListener) {
        mContext = context;
        mMainExecutor = shellExecutor;
        mMainHandler = mainHandler;
        mMainChoreographer = mainChoreographer;
        mMainDispatcher = mainDispatcher;
        mMainScope = mainScope;
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
        mWindowDecoratioWrapperFactory = windowDecoratioWrapperFactory;
        mInputMonitorFactory = inputMonitorFactory;
        mTransactionFactory = transactionFactory;
        mAppHeaderViewHolderFactory = appHeaderViewHolderFactory;
        mAppHandleViewHolderFactory = appHandleViewHolderFactory;
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
        mGenericLinksParser = genericLinksParser;
        mAppToWebRepository = appToWebRepository;
        mInputManager = mContext.getSystemService(InputManager.class);
        mWindowDecorByTaskId = decorationByTaskId;
        mSysUIPackageName = mContext.getResources().getString(
                com.android.internal.R.string.config_systemUi);
        mInteractionJankMonitor = interactionJankMonitor;
        mDesktopTasksLimiter = desktopTasksLimiter;
        mAppHandleEducationController = appHandleEducationController;
        mAppHandleAndHeaderVisibilityHelper = appHandleAndHeaderVisibilityHelper;
        mWindowDecorCaptionRepository = windowDecorCaptionRepository;
        mActivityOrientationChangeHandler = activityOrientationChangeHandler;
        mAssistContentRequester = assistContentRequester;
        mWindowDecorViewHostSupplier = windowDecorViewHostSupplier;
        mCompatUI = compatUI;
        mUserProfileContexts = userProfileContexts;
        mOnDisplayChangingListener = (displayId, fromRotation, toRotation, displayAreaInfo, t) -> {
            WindowDecorationWrapper decoration;
            RunningTaskInfo taskInfo;
            for (int i = 0; i < mWindowDecorByTaskId.size(); i++) {
                decoration = mWindowDecorByTaskId.valueAt(i);
                if (decoration == null) {
                    continue;
                } else {
                    taskInfo = decoration.getTaskInfo();
                }

                // Check if display has been rotated between portrait & landscape
                if (displayId == taskInfo.displayId && (fromRotation % 2 != toRotation % 2)) {
                    final Rect validDragArea = decoration.getValidDragArea();
                    // If not draggable, return
                    if (validDragArea == null) return;
                    // Check if the task bounds on the rotated display will be out of bounds.
                    // If so, then update task bounds to be within reachable area.
                    final Rect taskBounds = new Rect(
                            taskInfo.configuration.windowConfiguration.getBounds());
                    if (DragPositioningCallbackUtility.snapTaskBoundsIfNecessary(
                            taskBounds, validDragArea)) {
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
        mShellDesktopState = shellDesktopState;
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
        mLockTaskChangeListener = lockTaskChangeListener;
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
        if (mShellDesktopState.canEnterDesktopModeOrShowAppHandle()
                && Flags.enableDesktopWindowingAppHandleEducation()) {
            mAppHandleEducationController.setAppHandleEducationTooltipCallbacks(
                    /* appHandleTooltipClickCallback= */(taskId) -> {
                        mWindowDecorationActions.onOpenHandleMenu(taskId);
                        return Unit.INSTANCE;
                    },
                    /* onToDesktopClickCallback= */(taskId, desktopModeTransitionSource) -> {
                        moveToDesktop(taskId, desktopModeTransitionSource);
                        return Unit.INSTANCE;
                    });
        }
        mFocusTransitionObserver.setLocalFocusTransitionListener(this, mMainExecutor);
        mDesksOrganizer.addOnDesktopTaskInfoChangedListener((taskInfo) -> {
            onTaskInfoChanged(taskInfo);
            return Unit.INSTANCE;
        });
        mLockTaskChangeListener.addListener(this);
    }

    @Override
    public void onFocusedTaskChanged(RunningTaskInfo taskInfo, boolean isFocusedOnDisplay,
            boolean isFocusedGlobally) {
        final WindowDecorationWrapper decor = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decor != null) {
            decor.relayout(decor.getTaskInfo(), isFocusedGlobally, decor.getExclusionRegion());
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
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) return;
        final RunningTaskInfo oldTaskInfo = decoration.getTaskInfo();

        if (taskInfo.displayId != oldTaskInfo.displayId
                && !DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
            removeTaskFromEventReceiver(oldTaskInfo.displayId);
            incrementEventReceiverTasks(taskInfo.displayId);
        }
        if (ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS.isTrue()) {
            // Pass the current global focus status to avoid updates outside of a ShellTransition.
            decoration.relayout(
                    taskInfo, decoration.getHasGlobalFocus(), decoration.getExclusionRegion());
        } else {
            decoration.relayout(taskInfo, taskInfo.isFocused, decoration.getExclusionRegion());
        }
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
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
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
                    /* inSyncWithTransition= */ true, taskSurface);
        }
    }

    @Override
    public void onLockTaskModeChanged(int mode) {
        forAllWindowDecorations(decoration -> {
            final RunningTaskInfo taskInfo = decoration.getTaskInfo();
            decoration.relayout(taskInfo, decoration.getHasGlobalFocus(),
                    decoration.getExclusionRegion());
        });
    }

    private void forAllWindowDecorations(Consumer<WindowDecorationWrapper> callback) {
        forAllWindowDecorations(callback, /* reverseOrder= */ false);
    }

    private void forAllWindowDecorations(Consumer<WindowDecorationWrapper> callback,
            boolean reverseOrder) {
        final int decorSize = mWindowDecorByTaskId.size();
        if (reverseOrder) {
            for (int i = decorSize - 1; i >= 0; i--) {
                final WindowDecorationWrapper decoration = mWindowDecorByTaskId.valueAt(i);
                if (decoration == null) continue;
                callback.accept(decoration);
            }
        } else {
            for (int i = 0; i < decorSize; i++) {
                final WindowDecorationWrapper decoration = mWindowDecorByTaskId.valueAt(i);
                if (decoration == null) continue;
                callback.accept(decoration);
            }
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
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) return;

        decoration.relayout(taskInfo, startT, finishT, false /* applyStartTransactionOnDraw */,
                false /* shouldSetTaskPositionAndCrop */,
                mFocusTransitionObserver.hasGlobalFocus(taskInfo),
                mGestureExclusionTracker.getExclusionRegion(taskInfo.displayId),
                /* inSyncWithTransition= */ true, /* taskSurface */ null);
    }

    @Override
    public void destroyWindowDecoration(RunningTaskInfo taskInfo) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
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
        forAllWindowDecorations(decoration -> {
            if (decoration.getTaskInfo().displayId == displayId) {
                decoration.onExclusionRegionChanged(exclusionRegion);
            }
        });
    }

    private void openHandleMenu(int taskId) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
        // TODO(b/379873022): Run the instance check and the AssistContent request in
        //  createHandleMenu on the same bg thread dispatch.
        mBgExecutor.execute(() -> {
            final int numOfInstances = checkNumberOfOtherInstances(decoration.getTaskInfo());
            mMainExecutor.execute(() -> {
                if (decoration.getHandleMenuController() == null) return;
                decoration.getHandleMenuController().createHandleMenu(
                        numOfInstances >= MANAGE_WINDOWS_MINIMUM_INSTANCES);
            });
        });
    }

    private void onToggleSizeInteraction(
            int taskId, @NonNull ToggleTaskSizeInteraction.AmbiguousSource source,
            @Nullable InputMethod inputMethod) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
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
                    decoration.getTaskSurface(), mContext, mMainHandler,
                    interaction.getCujTracing(), interaction.getJankTag());
        }
        mDesktopTasksController.toggleDesktopTaskSize(decoration.getTaskInfo(), interaction);
    }

    private ToggleTaskSizeInteraction createToggleSizeInteraction(
            @NonNull WindowDecorationWrapper decoration,
            @NonNull ToggleTaskSizeInteraction.AmbiguousSource source,
            @Nullable InputMethod inputMethod) {
        final RunningTaskInfo taskInfo = decoration.getTaskInfo();

        final DisplayLayout displayLayout = mDisplayController.getDisplayLayout(taskInfo.displayId);
        if (displayLayout == null) {
            return null;
        }
        boolean isMaximized = DesktopModeUtils.isTaskMaximized(taskInfo, displayLayout);

        return new ToggleTaskSizeInteraction(
                isMaximized
                        ? ToggleTaskSizeInteraction.Direction.RESTORE
                        : ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeUtilsKt.toSource(source, isMaximized),
                inputMethod
        );
    }

    private void onEnterOrExitImmersive(RunningTaskInfo taskInfo) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decoration == null) {
            return;
        }
        final DesktopRepository desktopRepository = mDesktopUserRepositories.getProfile(
                taskInfo.userId);
        if (desktopRepository.isTaskInFullImmersiveState(taskInfo.taskId)) {
            mDesktopModeUiEventLogger.log(decoration.getTaskInfo(),
                    DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_RESTORE);
            mDesktopImmersiveController.moveTaskToNonImmersive(decoration.getTaskInfo(),
                    DesktopImmersiveController.ExitReason.USER_INTERACTION);
        } else {
            mDesktopModeUiEventLogger.log(decoration.getTaskInfo(),
                    DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_IMMERSIVE);
            removeTaskIfTiled(decoration.getTaskInfo().displayId, decoration.getTaskInfo().taskId);
            mDesktopImmersiveController.moveTaskToImmersive(decoration.getTaskInfo());
        }
    }

    /** Snap-resize a task to the left or right side of the desktop. */
    public void onSnapResize(int taskId, boolean left, InputMethod inputMethod, boolean fromMenu) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }

        if (fromMenu) {
            final DesktopModeUiEventLogger.DesktopUiEventEnum event = left
                    ? DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_TILE_TO_LEFT
                    : DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_TILE_TO_RIGHT;
            mDesktopModeUiEventLogger.log(decoration.getTaskInfo(), event);
        }

        mInteractionJankMonitor.begin(decoration.getTaskSurface(), mContext, mMainHandler,
                Cuj.CUJ_DESKTOP_MODE_SNAP_RESIZE, "maximize_menu_resizable");
        mDesktopTasksController.handleInstantSnapResizingTask(
                decoration.getTaskInfo(),
                left ? SnapPosition.LEFT : SnapPosition.RIGHT,
                left ? ResizeTrigger.SNAP_LEFT_MENU : ResizeTrigger.SNAP_RIGHT_MENU,
                inputMethod);
    }

    private void openInBrowser(int taskId, @NonNull Intent intent) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        openInBrowser(intent, decoration.getUser(), decoration.getTaskInfo().displayId);
    }

    private void openInBrowser(
            @NonNull Intent intent, @NonNull UserHandle userHandle, int displayId) {
        final ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId);
        mContext.startActivityAsUser(intent, options.toBundle(), userHandle);
    }

    private void moveToDesktop(int taskId, DesktopModeTransitionSource source) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (decoration.getTaskInfo().configuration.windowConfiguration.getWindowingMode()
                == WINDOWING_MODE_MULTI_WINDOW) {
            mInteractionJankMonitor.begin(decoration.getTaskSurface(), mContext, mMainHandler,
                    CUJ_DESKTOP_MODE_MOVE_FROM_SPLIT_SCREEN);
        } else {
            mInteractionJankMonitor.begin(decoration.getTaskSurface(), mContext, mMainHandler,
                    CUJ_DESKTOP_MODE_ENTER_MODE_APP_HANDLE_MENU);
        }
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
            mDesktopModeUiEventLogger.log(decoration.getTaskInfo(),
                    DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_DESKTOP_MODE);
        }
    }

    private void onManageWindows(int taskId) {
        final WindowDecorationWrapper decor = mWindowDecorByTaskId.get(taskId);
        mBgExecutor.execute(() -> {
            final ArrayList<Pair<Integer, TaskSnapshot>> snapshotList =
                    getTaskSnapshots(decor.getTaskInfo());
            if (!snapshotList.isEmpty()) {
                mMainExecutor.execute(() -> {
                    if (decor.getManageWindowsMenuController() == null) return;
                    decor.getManageWindowsMenuController()
                            .createManageWindowsMenu(snapshotList);
                });
            }
        });
    }

    private void moveToFullscreen(int taskId) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
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
        mDesktopModeUiEventLogger.log(decoration.getTaskInfo(),
                DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_FULL_SCREEN);
    }

    private void moveToSplit(int taskId) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }

        int displayId = decoration.getTaskInfo().displayId;
        final int orientation = mDisplayController.getDisplayContext(displayId).getResources()
                .getConfiguration().orientation;

        // Set leftOrTop as True to split to the top in portrait mode.
        // Set leftOrTop as False to split to the right in landscape mode.
        boolean leftOrTop = orientation == Configuration.ORIENTATION_PORTRAIT;

        mDesktopTasksController.requestSplit(decoration.getTaskInfo(), leftOrTop);
        mDesktopModeUiEventLogger.log(decoration.getTaskInfo(),
                DesktopUiEventEnum.DESKTOP_WINDOW_APP_HANDLE_MENU_TAP_TO_SPLIT_SCREEN);
    }

    private void moveToFloat(int taskId) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        // When the app enters float, the handle will no longer be visible, meaning
        // we shouldn't receive input for it any longer.
        decoration.disposeStatusBarInputLayer();
        mDesktopTasksController.requestFloat(decoration.getTaskInfo());
    }

    private void launchNewWindow(int taskId) {
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            return;
        }
        mDesktopTasksController.openNewWindow(decoration.getTaskInfo());
        mDesktopModeUiEventLogger.log(decoration.getTaskInfo(),
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
                TaskSnapshot screenshot;
                if (com.android.window.flags2.Flags.reduceTaskSnapshotMemoryUsage()) {
                    screenshot = TaskSnapshotManager.getInstance().getTaskSnapshot(
                            info.taskId, TaskSnapshotManager.RESOLUTION_HIGH);
                    if (screenshot == null) {
                        screenshot = TaskSnapshotManager.getInstance().takeTaskSnapshot(
                                info.taskId, false /* updateCache */);
                    }
                } else {
                    screenshot = activityTaskManagerService
                            .getTaskSnapshot(info.taskId, false);
                    if (screenshot == null) {
                        screenshot = activityTaskManagerService
                                .takeTaskSnapshot(info.taskId, false);
                    }
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
    public void notifyTilingOfExplodedViewReorder(int deskId, int topTaskId) {
        mDesktopTilingDecorViewModel.onExplodedViewReorder(deskId, topTaskId);
    }

    @Override
    public void onDisplayLayoutChange(int displayId, Configuration config,
            @NonNull Rect oldStableBounds, double newToOldDpiRatio) {
        mDesktopTilingDecorViewModel.onDisplayLayoutChange(displayId, config, oldStableBounds,
                newToOldDpiRatio);
    }

    @Override
    public @NonNull Rect getDividerBounds(int deskId) {
        return mDesktopTilingDecorViewModel.getDividerBounds(deskId);
    }

    @Override
    public void onDeskActivated(int deskId, int displayId) {
        if (mDesktopTilingDecorViewModel.tilingDeskActive(deskId)) {
            return;
        }
        final DesktopRepository repository = mDesktopUserRepositories.getCurrent();
        final Integer leftTaskId = repository.getLeftTiledTask(deskId);
        final Integer rightTaskId = repository.getRightTiledTask(deskId);
        // if the decor wrapper is null, tiling will be initialised when the decor is created.
        if (leftTaskId != null && mWindowDecorByTaskId.get(leftTaskId) != null) {
            final WindowDecorationWrapper decor = mWindowDecorByTaskId.get(leftTaskId);
            final RunningTaskInfo taskInfo = decor.getTaskInfo();
            final Rect currentBounds = taskInfo.configuration.windowConfiguration.getBounds();
            snapPersistedTaskToHalfScreen(taskInfo, currentBounds, SnapPosition.LEFT);
        }

        if (rightTaskId != null && mWindowDecorByTaskId.get(rightTaskId) != null) {
            final WindowDecorationWrapper decor = mWindowDecorByTaskId.get(rightTaskId);
            final RunningTaskInfo taskInfo = decor.getTaskInfo();
            final Rect currentBounds = taskInfo.configuration.windowConfiguration.getBounds();
            snapPersistedTaskToHalfScreen(taskInfo, currentBounds, SnapPosition.RIGHT);
        }
    }

    /**
     * @deprecated will be removed once window decoration refactor is turned on
     * TODO: b/409648813 : to be removed when [WindowDecoration] is deprecated.
     */
    @Deprecated
    @Override
    public boolean updateAppToWebEducationRequestTimestamp(int taskId,
            long latestOpenInBrowserEducationTimestamp) {
        final WindowDecorationWrapper decor = mWindowDecorByTaskId.get(taskId);
        if (decor == null) return false;
        return decor.updateAppToWebEducationRequestTimestamp(latestOpenInBrowserEducationTimestamp);
    }

    /**
     * @deprecated will be removed once window decoration refactor is turned on
     * TODO: b/409648813 : to be removed when [WindowDecoration] is deprecated.
     */
    @Deprecated
    @Override
    public Object isBrowserSessionAvailable(RunningTaskInfo taskInfo,
            @NonNull Continuation<? super Boolean> completion) {
        final WindowDecorationWrapper decor = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (decor == null) return false;
        return decor.isBrowserSessionAvailable();
    }

    /**
     * @deprecated will be removed once window decoration refactor is turned on
     * TODO: b/409648813 : to be removed when [WindowDecoration] is deprecated.
     */
    @Deprecated
    @Override
    public boolean isCapturedLinkAvailable(int taskId) {
        final WindowDecorationWrapper decor = mWindowDecorByTaskId.get(taskId);
        if (decor == null) return false;
        return decor.isCapturedLinkAvailable();
    }

    /**
     * @deprecated Actual implementation within {@link WindowDecoration}
     * TODO: b/409648813 : to be removed when [WindowDecoration] is deprecated.
     */
    @Deprecated
    @Override
    public void setCapturedLink(int taskId, @NonNull Uri link,
            long timeStamp) {
    }

    /**
     * @deprecated Actual implementation within {@link WindowDecoration}
     * TODO: b/409648813 : to be removed when [WindowDecoration] is deprecated.
     */
    @Deprecated
    @Override
    public void onCapturedLinkUsed(int taskId) {

    }

    /**
     * @deprecated Actual implementation within {@link WindowDecoration}
     * TODO: b/409648813 : to be removed when [WindowDecoration] is deprecated.
     */
    @Deprecated
    @Override
    public Object getAppToWebIntent(@NonNull RunningTaskInfo taskInfo,
            boolean isBrowserApp, @NonNull Continuation<? super Intent> completion) {
        return null;
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
    public void onDisplayDisconnected(int disconnectedDisplayId) {
        mDesktopTilingDecorViewModel.onDisplayDisconnected(disconnectedDisplayId);
    }

    @Override
    public void onDeskRemoved(int deskId) {
        mDesktopTilingDecorViewModel.onDeskRemoved(deskId);
    }

    /** Registers a {@link CaptionTouchStatusListener}. */
    public void registerCaptionTouchStatusListener(CaptionTouchStatusListener l) {
        mCaptionTouchStatusListener = l;
    }

    /**
     * Closes a task.
     * This method closes a task as if the close button on the window decor is clicked.
     * It does nothing if the task does not have a window decor, thus cannot be closed through the
     * UI.
     * Must call the method on the shell main executor.
     * @param task Task to be closed.
     */
    public void closeTask(RunningTaskInfo task) {
        if (mTaskOperations == null) {
            ProtoLog.e(WM_SHELL_WINDOW_DECORATION,
                    "%s: handled close key gesture but mTaskOperations is null, ignoring", TAG);
            return;
        }
        ProtoLog.e(WM_SHELL_WINDOW_DECORATION,
                "%s: close task %d vis key gesture", TAG, task.taskId);
        onCloseTask(task.taskId);
    }

    private void onCloseTask(int taskId) {
        if (isTaskInSplitScreen(taskId)) {
            ProtoLog.i(WM_SHELL_WINDOW_DECORATION,
                    "%s: onCloseTask(taskId=%d): closing split screen", TAG, taskId);
            mSplitScreenController.moveTaskToFullscreen(getOtherSplitTask(taskId).taskId,
                    SplitScreenController.EXIT_REASON_DESKTOP_MODE);
            return;
        }
        final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
        if (decoration == null) {
            ProtoLog.e(WM_SHELL_WINDOW_DECORATION,
                    "%s: onCloseTask(taskId=%d): decoration is null, ignoring", TAG, taskId);
            return;
        }
        if (DesktopExperienceFlags
                .CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT.isTrue()
                && decoration.getTaskInfo().getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
            ProtoLog.i(WM_SHELL_WINDOW_DECORATION,
                    "%s: onCloseTask(taskId=%d): closing fullscreen task", TAG, taskId);
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.removeTask(decoration.getTaskInfo().token);
            mTransitions.startTransition(WindowManager.TRANSIT_CLOSE, wct, null);
            return;
        }
        if (DesktopExperienceFlags
                .ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS.isTrue()) {
            final int nextFocusedTaskId = mDesktopTasksController.getNextFocusedTask(
                    decoration.getTaskInfo());
            final WindowDecorationWrapper nextFocusedWindow =
                    mWindowDecorationFinder.apply(nextFocusedTaskId);
            if (nextFocusedWindow != null) {
                nextFocusedWindow.a11yAnnounceNewFocusedWindow();
            }
        }
        ProtoLog.w(WM_SHELL_WINDOW_DECORATION,
                "%s: onCloseTask(taskId=%d): closing desktop task", TAG, taskId);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final Function1<IBinder, Unit> runOnTransitionStart =
                mDesktopTasksController.onDesktopWindowClose(wct,
                        decoration.getTaskInfo().displayId, decoration.getTaskInfo());
        final IBinder transition = mTaskOperations.closeTask(
                decoration.getTaskInfo().token, wct);
        if (transition != null && runOnTransitionStart != null) {
            runOnTransitionStart.invoke(transition);
        }
    }

    /** Listener for caption touch events. */
    public interface CaptionTouchStatusListener {
        /** Called when the caption is pressed. */
        void onCaptionPressed();
        /** Called when the caption is released. */
        void onCaptionReleased();
    }

    /** Handler of motion events on the app handle. */
    public interface AppHandleMotionEventHandler {
        /**
         * Handles motion events coming from the decor's app handle.
         *
         * @param ev the event to handle
         * @param relevantDecor the window decoration from which the event originates
         * @param interruptDragCallback a callback to invoke when the drag is interrupted
         **/
        void onMotionEvent(MotionEvent ev, WindowDecorationWrapper relevantDecor,
                Runnable interruptDragCallback);
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
                        .handleReceivedMotionEvent((MotionEvent) event, getToken());
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
    private void handleReceivedMotionEvent(MotionEvent ev, IBinder inputChannelToken) {
        final WindowDecorationWrapper relevantDecor = getRelevantWindowDecor(ev);
        if (mShellDesktopState.canEnterDesktopMode()) {
            if (!mInImmersiveMode && (relevantDecor == null
                    || relevantDecor.getTaskInfo().getWindowingMode() != WINDOWING_MODE_FREEFORM
                    || mTransitionDragActive)) {
                handleCaptionThroughStatusBar(ev, relevantDecor,
                        /* interruptDragCallback= */ () -> {});
            }
        }
        handleEventOutsideCaption(ev, relevantDecor);
        // Prevent status bar from reacting to a caption drag.
        if (mShellDesktopState.canEnterDesktopMode()) {
            if (mTransitionDragActive) {
                final InputManager inputManager = mContext.getSystemService(InputManager.class);
                inputManager.pilferPointers(inputChannelToken);
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
    private void handleEventOutsideCaption(MotionEvent ev, WindowDecorationWrapper relevantDecor) {
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
            WindowDecorationWrapper relevantDecor, Runnable interruptDragCallback) {
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
                        relevantDecor.getTaskInfo().configuration.windowConfiguration.getBounds());
                boolean dragFromStatusBarAllowed = false;
                final int windowingMode = relevantDecor.getTaskInfo().getWindowingMode();
                if (mShellDesktopState.canEnterDesktopMode()
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
                                    .getDragStartState(relevantDecor.getTaskInfo());
                    if (dragStartState == null) return;
                    mDesktopTasksController.updateVisualIndicator(relevantDecor.getTaskInfo(),
                            relevantDecor.getTaskSurface(), ev.getDisplayId(), ev.getRawX(),
                            ev.getRawY(), dragStartState);
                    mTransitionDragActive = false;
                    if (mMoveToDesktopAnimator != null) {
                        // Though this isn't a hover event, we need to update handle's hover state
                        // as it likely will change.
                        relevantDecor.updateHoverAndPressStatus(ev);
                        if (ev.getActionMasked() == ACTION_CANCEL) {
                            mDesktopTasksController.onDragPositioningCancelThroughStatusBar(
                                    relevantDecor.getTaskInfo());
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
                            .getDisplayLayout(relevantDecor.getTaskInfo().displayId);
                    // It's possible task is not at the top of the screen (e.g. bottom of vertical
                    // Splitscreen)
                    final int taskTop = relevantDecor.getTaskInfo().configuration
                            .windowConfiguration.getBounds().top;
                    if (ev.getRawY() < 2 * layout.stableInsets().top + taskTop
                            && mMoveToDesktopAnimator == null) {
                        return;
                    }
                    final DesktopModeVisualIndicator.DragStartState dragStartState =
                            DesktopModeVisualIndicator.DragStartState
                                    .getDragStartState(relevantDecor.getTaskInfo());
                    if (dragStartState == null) return;
                    final DesktopModeVisualIndicator.IndicatorType indicatorType =
                            mDesktopTasksController.updateVisualIndicator(
                                    relevantDecor.getTaskInfo(), relevantDecor.getTaskSurface(),
                                    ev.getDisplayId(), ev.getRawX(), ev.getRawY(),
                                    dragStartState);
                    if (indicatorType != TO_FULLSCREEN_INDICATOR
                            || BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                        if (mMoveToDesktopAnimator == null) {
                            Context displayContext = mDisplayController.getDisplayContext(
                                    ev.getDisplayId());
                            Context animatorContext =
                                    displayContext != null ? displayContext : mContext;
                            mMoveToDesktopAnimator = new MoveToDesktopAnimator(
                                    animatorContext, mDragToDesktopAnimationStartBounds,
                                    relevantDecor.getTaskInfo(), relevantDecor.getTaskSurface());
                            mDesktopTasksController.startDragToDesktop(relevantDecor.getTaskInfo(),
                                    mMoveToDesktopAnimator, relevantDecor.getTaskSurface(),
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

    private void endDragToDesktop(MotionEvent ev, WindowDecorationWrapper relevantDecor) {
        DesktopModeVisualIndicator.IndicatorType resultType =
                mDesktopTasksController.onDragPositioningEndThroughStatusBar(
                        ev.getDisplayId(),
                        new PointF(ev.getRawX(), ev.getRawY()),
                        relevantDecor.getTaskInfo(),
                        relevantDecor.getTaskSurface());
        // If we are entering split select, handle will no longer be visible and
        // should not be receiving any input.
        if (resultType == TO_SPLIT_LEFT_INDICATOR
                || resultType == TO_SPLIT_RIGHT_INDICATOR) {
            relevantDecor.disposeStatusBarInputLayer();
            // We should also dispose the other split task's input layer if
            // applicable.
            final int splitPosition = mSplitScreenController
                    .getSplitPosition(relevantDecor.getTaskInfo().taskId);
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
    private WindowDecorationWrapper getRelevantWindowDecor(MotionEvent ev) {
        // If we are mid-transition, dragged task's decor is always relevant.
        final int draggedTaskId = mDesktopTasksController.getDraggingTaskId();
        if (draggedTaskId != INVALID_TASK_ID) {
            return mWindowDecorByTaskId.get(draggedTaskId);
        }
        final WindowDecorationWrapper focusedDecor = getFocusedDecor();
        if (focusedDecor == null) {
            return null;
        }
        final boolean splitScreenVisible = mSplitScreenController != null
                && mSplitScreenController.isSplitScreenVisible();
        // It's possible that split tasks are visible but neither is focused, such as when there's
        // a fullscreen translucent window on top of them. In that case, the relevant decor should
        // just be that translucent focused window.
        final boolean focusedTaskInSplit = mSplitScreenController != null
                && mSplitScreenController.isTaskInSplitScreen(focusedDecor.getTaskInfo().taskId);
        if (splitScreenVisible && focusedTaskInSplit) {
            // We can't look at focused task here as only one task will have focus.
            WindowDecorationWrapper splitTaskDecor = getSplitScreenDecor(ev);
            return splitTaskDecor == null ? getFocusedDecor() : splitTaskDecor;
        } else {
            return getFocusedDecor();
        }
    }

    @Nullable
    private WindowDecorationWrapper getSplitScreenDecor(MotionEvent ev) {
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
    private WindowDecorationWrapper getFocusedDecor() {
        final int size = mWindowDecorByTaskId.size();
        WindowDecorationWrapper focusedDecor = null;
        // TODO(b/323251951): We need to iterate this in reverse to avoid potentially getting
        //  a decor for a closed task. This is a short term fix while the core issue is addressed,
        //  which involves refactoring the window decor lifecycle to be visibility based.
        for (int i = size - 1; i >= 0; i--) {
            final WindowDecorationWrapper decor = mWindowDecorByTaskId.valueAt(i);
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
        final WindowDecorationWrapper oldDecoration = mWindowDecorByTaskId.get(taskInfo.taskId);
        if (oldDecoration != null) {
            // close the old decoration if it exists to avoid two window decorations being added
            oldDecoration.close();
        }
        final WindowDecorationWrapper windowDecoration;
        if (DesktopExperienceFlags.ENABLE_WINDOW_DECORATION_REFACTOR.isTrue()) {
            final DefaultWindowDecoration defaultWindowDecoration = new DefaultWindowDecoration(
                    taskInfo,
                    taskSurface,
                    DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue()
                            ? mDisplayController.getDisplayContext(taskInfo.displayId)
                            : mContext,
                    mContext.createContextAsUser(UserHandle.of(taskInfo.userId),
                            0 /* flags */),
                    mDisplayController,
                    mTaskResourceLoader,
                    mSplitScreenController,
                    mDesktopUserRepositories,
                    mTaskOrganizer,
                    mMainHandler,
                    mMainExecutor,
                    mMainDispatcher,
                    mMainScope,
                    mBgExecutor,
                    mTransitions,
                    mMainChoreographer,
                    mSyncQueue,
                    mRootTaskDisplayAreaOrganizer,
                    mWindowDecorViewHostSupplier,
                    mMultiInstanceHelper,
                    mWindowDecorCaptionRepository,
                    mDesktopModeEventLogger,
                    mDesktopModeUiEventLogger,
                    mDesktopModeCompatPolicy,
                    mShellDesktopState,
                    mDesktopConfig,
                    mWindowDecorationActions,
                    mAppToWebRepository,
                    mLockTaskChangeListener);
            windowDecoration =
                    mWindowDecoratioWrapperFactory.fromDefaultDecoration(defaultWindowDecoration);
        } else {
            final DesktopModeWindowDecoration desktopModeWindowDecoration =
                    new DesktopModeWindowDecoration(
                            DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue()
                                    ? mDisplayController.getDisplayContext(taskInfo.displayId)
                                    : mContext,
                            mUserProfileContexts.getOrCreate(taskInfo.userId),
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
                            mMainScope,
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
                            mShellDesktopState,
                            mDesktopConfig,
                            mWindowDecorationActions,
                            mLockTaskChangeListener);
            windowDecoration = mWindowDecoratioWrapperFactory
                    .fromDesktopDecoration(desktopModeWindowDecoration);
        }
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
                mShellDesktopState,
                mDesktopConfig,
                mDesktopTasksController);
        windowDecoration.setTaskDragResizer(taskPositioner);

        final DesktopModeTouchEventListener touchEventListener =
                new DesktopModeTouchEventListener(mContext, taskInfo, taskPositioner,
                        mWindowDecorationFinder, mDesktopTasksController, mTaskOperations,
                        mDesktopModeUiEventLogger, mWindowDecorationActions,
                        mDesktopUserRepositories, mGestureExclusionTracker, mInputManager,
                        mFocusTransitionObserver, mShellDesktopState,
                        mMultiDisplayDragMoveIndicatorController, mTransactionFactory,
                        mCaptionTouchStatusListener, mAppHandleMotionEventHandler);
        windowDecoration.setCaptionListeners(
                touchEventListener, touchEventListener, touchEventListener, touchEventListener);
        windowDecoration.setExclusionRegionListener(mExclusionRegionListener);
        windowDecoration.setDragPositioningCallback(taskPositioner);
        windowDecoration.addDragResizeListener(mDragEventListener);
        windowDecoration.relayout(taskInfo, startT, finishT,
                false /* applyStartTransactionOnDraw */, false /* shouldSetTaskPositionAndCrop */,
                mFocusTransitionObserver.hasGlobalFocus(taskInfo),
                mGestureExclusionTracker.getExclusionRegion(taskInfo.displayId),
                /* inSyncWithTransition= */ true, taskSurface);
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
                + mShellDesktopState.canEnterDesktopMode());
        pw.println(innerPrefix + "mTransitionDragActive=" + mTransitionDragActive);
        pw.println(innerPrefix + "mEventReceiversByDisplay=" + mEventReceiversByDisplay);
        pw.println(innerPrefix + "mGestureExclusionTracker="
                + mGestureExclusionTracker);
    }

    private class DesktopModeOnTaskRepositionAnimationListener
            implements OnTaskRepositionAnimationListener {
        @Override
        public void onAnimationStart(int taskId) {
            final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration != null) {
                decoration.setAnimatingTaskResizeOrReposition(true);
            }
        }

        @Override
        public void onAnimationEnd(int taskId) {
            final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration != null) {
                decoration.setAnimatingTaskResizeOrReposition(false);
            }
        }
    }

    private class DesktopModeOnTaskResizeAnimationListener
            implements OnTaskResizeAnimationListener {
        @Override
        public void onAnimationStart(int taskId, Transaction t, Rect bounds) {
            final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration == null) {
                t.apply();
                return;
            }
            decoration.showResizeVeil(t, bounds);
            decoration.setAnimatingTaskResizeOrReposition(true);
        }

        @Override
        public void onBoundsChange(int taskId, Transaction t, Rect bounds) {
            final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration == null) return;
            decoration.updateResizeVeil(t, bounds);
        }

        @Override
        public void onAnimationEnd(int taskId) {
            final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
            if (decoration == null) return;
            decoration.hideResizeVeil();
            decoration.setAnimatingTaskResizeOrReposition(false);
            decoration.requestFocusMaximizeButton();
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
            final WindowDecorationWrapper decoration = mWindowDecorByTaskId.get(taskId);
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

    private class ExclusionRegionListenerImpl implements ExclusionRegionListener {

        @Override
        public void onExclusionRegionChanged(int taskId, Region region) {
            mDesktopTasksController.onExclusionRegionChanged(taskId, region);
        }

        @Override
        public void onExclusionRegionDismissed(int taskId) {
            mDesktopTasksController.removeExclusionRegionForTask(taskId);
        }
    }

    private class DesktopModeDragEventListener implements
            DragPositioningCallbackUtility.DragEventListener {

        @Override
        public void onDragMove(int taskId) {
            // Do nothing.
        }

        @Override
        public void onDragResizeStarted(int taskId,
                @NonNull ResizeTrigger resizeTrigger,
                @NonNull InputMethod inputMethod,
                @NonNull Rect startTaskBounds) {
            final WindowDecorationWrapper winDecor = mWindowDecorByTaskId.get(taskId);
            final RunningTaskInfo currTaskInfo = winDecor.getTaskInfo();
            final DesktopRepository desktopRepository =
                    mDesktopUserRepositories.getProfile(currTaskInfo.userId);
            final Integer deskId = desktopRepository.getDeskIdForTask(taskId);
            mDesktopModeEventLogger.logTaskResizingStarted(resizeTrigger, inputMethod,
                    currTaskInfo, startTaskBounds.width(),
                    startTaskBounds.height(), mDisplayController, deskId);
        }

        @Override
        public void onDragResizeEnded(int taskId,
                @NonNull ResizeTrigger resizeTrigger,
                @NonNull InputMethod inputMethod,
                @NonNull Rect endTaskBounds) {
            final WindowDecorationWrapper winDecor = mWindowDecorByTaskId.get(taskId);
            final RunningTaskInfo currTaskInfo = winDecor.getTaskInfo();
            final DesktopRepository desktopRepository =
                    mDesktopUserRepositories.getProfile(currTaskInfo.userId);
            final Integer deskId = desktopRepository.getDeskIdForTask(taskId);
            mDesktopModeEventLogger.logTaskResizingEnded(resizeTrigger, inputMethod,
                    winDecor.getTaskInfo(), endTaskBounds.width(),
                    endTaskBounds.height(), mDisplayController, deskId);
        }
    }

    class DesktopModeKeyguardChangeListener implements KeyguardChangeListener {
        @Override
        public void onKeyguardVisibilityChanged(boolean visible, boolean occluded,
                boolean animatingDismiss) {
            forAllWindowDecorations(decor -> {
                decor.onKeyguardStateChanged(visible, occluded);
            }, /* reverseOrder= */ true);
        }
    }

    @VisibleForTesting
    class DesktopModeOnInsetsChangedListener implements
            DisplayInsetsController.OnInsetsChangedListener {
        @Override
        public void insetsChanged(int displayId, @NonNull InsetsState insetsState) {
            final int size = mWindowDecorByTaskId.size();
            for (int i = size - 1; i >= 0; i--) {
                final WindowDecorationWrapper decor = mWindowDecorByTaskId.valueAt(i);
                if (decor == null) {
                    continue;
                }
                if (decor.getTaskInfo().displayId == displayId
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

    @VisibleForTesting
    static class DefaultWindowDecorationActions implements WindowDecorationActions {
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
                @NotNull ToggleTaskSizeInteraction.AmbiguousSource ambiguousSource,
                @NotNull InputMethod inputMethod) {
            mViewModel.onToggleSizeInteraction(taskId, ambiguousSource, inputMethod);
        }

        @Override
        public void onMinimize(@NonNull RunningTaskInfo taskInfo) {
            if (DesktopExperienceFlags
                    .ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS.isTrue()) {
                final int nextFocusedTaskId = mDesktopTasksController.getNextFocusedTask(taskInfo);
                WindowDecorationWrapper nextFocusedWindow =
                        mViewModel.mWindowDecorByTaskId.get(nextFocusedTaskId);
                if (nextFocusedWindow != null) {
                    nextFocusedWindow.a11yAnnounceNewFocusedWindow();
                }
            }
            mDesktopTasksController.minimizeTask(taskInfo, MinimizeReason.MINIMIZE_BUTTON);
        }

        @Override
        public void onClose(int taskId) {
            mViewModel.onCloseTask(taskId);
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
            CompatUIController.launchUserAspectRatioSettingsNoAnimation(mContext, taskInfo);
        }

        @Override
        public void onNewWindow(int taskId) {
            mViewModel.launchNewWindow(taskId);
        }

        @Override
        public void onOpenHandleMenu(int taskId) {
            mViewModel.openHandleMenu(taskId);
        }
    }


    @VisibleForTesting
    static class TaskPositionerFactory {
        TaskPositioner create(
                ShellTaskOrganizer taskOrganizer,
                WindowDecorationWrapper windowDecoration,
                DisplayController displayController,
                Transitions transitions,
                InteractionJankMonitor interactionJankMonitor,
                Supplier<SurfaceControl.Transaction> transactionFactory,
                Handler handler,
                MultiDisplayDragMoveIndicatorController multiDisplayDragMoveIndicatorController,
                DesktopState desktopState,
                DesktopConfig desktopConfig,
                DesktopTasksController desktopTasksController) {
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
                            desktopState,
                            desktopTasksController)
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
