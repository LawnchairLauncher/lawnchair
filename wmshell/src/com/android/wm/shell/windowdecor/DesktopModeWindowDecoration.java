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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.windowingModeToString;
import static android.view.InsetsSource.FLAG_FORCE_CONSUMING;
import static android.view.InsetsSource.FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.window.DesktopModeFlags.ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION;
import static android.window.DesktopModeFlags.ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS;

import static com.android.internal.policy.SystemBarUtils.getDesktopViewAppHeaderHeightId;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.windowdecor.DragPositioningCallbackUtility.DragEventListener;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.DisabledEdge;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.DisabledEdge.NONE;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getFineResizeCornerSize;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getLargeResizeCornerSize;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getResizeEdgeHandleSize;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getResizeHandleEdgeInset;
import static com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier.AppHandleWindowingMode;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.WindowConfiguration.WindowingMode;
import android.app.assist.AssistContent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.net.Uri;
import android.os.Handler;
import android.os.Trace;
import android.os.UserHandle;
import android.util.Size;
import android.view.Choreographer;
import android.view.Display;
import android.view.InsetsState;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageButton;
import android.window.DesktopExperienceFlags;
import android.window.DesktopModeFlags;
import android.window.TaskSnapshot;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.policy.SystemBarUtils;
import com.android.window.flags2.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.apptoweb.AppToWebGenericLinksParser;
import com.android.wm.shell.apptoweb.AppToWebUtils;
import com.android.wm.shell.apptoweb.AssistContentRequester;
import com.android.wm.shell.apptoweb.OpenByDefaultDialog;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.MultiInstanceHelper;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.desktopmode.CaptionState;
import com.android.wm.shell.desktopmode.DesktopModeEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger;
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum;
import com.android.wm.shell.desktopmode.DesktopModeUtils;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.desktopmode.DesktopConfig;
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.shared.multiinstance.ManageWindowsViewContainer;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.common.DecorThemeUtil;
import com.android.wm.shell.windowdecor.common.Theme;
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.extension.TaskInfoKt;
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier;
import com.android.wm.shell.windowdecor.viewholder.AppHandleViewHolder;
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder;
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.MainCoroutineDispatcher;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Defines visuals and behaviors of a window decoration of a caption bar and shadows. It works with
 * {@link DesktopModeWindowDecorViewModel}.
 *
 * The shadow's thickness is 20dp when the window is in focus and 5dp when the window isn't.
 */
public class DesktopModeWindowDecoration extends WindowDecoration<WindowDecorLinearLayout> {
    private static final String TAG = "DesktopModeWindowDecoration";

    @VisibleForTesting
    static final long CLOSE_MAXIMIZE_MENU_DELAY_MS = 150L;

    private final @ShellMainThread Handler mHandler;
    private final @ShellMainThread ShellExecutor mMainExecutor;
    private final @ShellMainThread MainCoroutineDispatcher mMainDispatcher;
    private final @ShellBackgroundThread CoroutineScope mBgScope;
    private final @ShellBackgroundThread ShellExecutor mBgExecutor;
    private final Transitions mTransitions;
    private final Choreographer mChoreographer;
    private final SyncTransactionQueue mSyncQueue;
    private final SplitScreenController mSplitScreenController;
    private final WindowManagerWrapper mWindowManagerWrapper;
    private final @NonNull WindowDecorTaskResourceLoader mTaskResourceLoader;
    private final DesktopState mDesktopState;
    private final DesktopConfig mDesktopConfig;
    private final WindowDecorationActions mWindowDecorationActions;

    private WindowDecorationViewHolder mWindowDecorViewHolder;
    private View.OnClickListener mOnCaptionButtonClickListener;
    private View.OnTouchListener mOnCaptionTouchListener;
    private View.OnLongClickListener mOnCaptionLongClickListener;
    private View.OnGenericMotionListener mOnCaptionGenericMotionListener;
    private DragPositioningCallback mDragPositioningCallback;
    private DragResizeInputListener mDragResizeListener;
    private RelayoutParams mRelayoutParams = new RelayoutParams();
    private DisabledEdge mDisabledResizingEdge =
            NONE;
    private final WindowDecoration.RelayoutResult<WindowDecorLinearLayout> mResult =
            new WindowDecoration.RelayoutResult<>();

    private final Point mPositionInParent = new Point();
    private HandleMenu mHandleMenu;
    private boolean mMinimumInstancesFound;
    private ManageWindowsViewContainer mManageWindowsMenu;

    private MaximizeMenu mMaximizeMenu;

    private OpenByDefaultDialog mOpenByDefaultDialog;

    private ResizeVeil mResizeVeil;

    private CapturedLink mCapturedLink;
    private Uri mGenericLink;
    private Uri mWebUri;

    private ExclusionRegionListener mExclusionRegionListener;

    private final AppHeaderViewHolder.Factory mAppHeaderViewHolderFactory;
    private final AppHandleViewHolder.Factory mAppHandleViewHolderFactory;
    private final RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private final MaximizeMenuFactory mMaximizeMenuFactory;
    private final HandleMenuFactory mHandleMenuFactory;
    private final AppToWebGenericLinksParser mGenericLinksParser;
    private final AssistContentRequester mAssistContentRequester;
    private final DesktopModeCompatPolicy mDesktopModeCompatPolicy;

    // Hover state for the maximize menu and button. The menu will remain open as long as either of
    // these is true. See {@link #onMaximizeHoverStateChanged()}.
    private boolean mIsAppHeaderMaximizeButtonHovered = false;
    private boolean mIsMaximizeMenuHovered = false;
    // Used to schedule the closing of the maximize menu when neither of the button or menu are
    // being hovered. There's a small delay after stopping the hover, to allow a quick reentry
    // to cancel the close.
    private final Runnable mCloseMaximizeWindowRunnable = this::closeMaximizeMenu;
    private final MultiInstanceHelper mMultiInstanceHelper;
    private final WindowDecorCaptionRepository mWindowDecorCaptionRepository;
    private final DesktopUserRepositories mDesktopUserRepositories;
    private final DesktopModeUiEventLogger mDesktopModeUiEventLogger;
    private boolean mIsRecentsTransitionRunning = false;
    private boolean mIsDragging = false;
    private Runnable mLoadAppInfoRunnable;
    private Runnable mSetAppInfoRunnable;

    private final Function0<Unit> mCloseMaximizeMenuFunction = () -> {
        closeMaximizeMenu();
        return Unit.INSTANCE;
    };

    private final Function0<Unit> mCloseHandleMenuFunction = () -> {
        closeHandleMenu();
        return Unit.INSTANCE;
    };

    public DesktopModeWindowDecoration(
            Context context,
            @NonNull Context userContext,
            DisplayController displayController,
            @NonNull WindowDecorTaskResourceLoader taskResourceLoader,
            SplitScreenController splitScreenController,
            DesktopUserRepositories desktopUserRepositories,
            ShellTaskOrganizer taskOrganizer,
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            @ShellMainThread Handler handler,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread CoroutineScope bgScope,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            Transitions transitions,
            Choreographer choreographer,
            SyncTransactionQueue syncQueue,
            AppHeaderViewHolder.Factory appHeaderViewHolderFactory,
            AppHandleViewHolder.Factory appHandleViewHolderFactory,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            AppToWebGenericLinksParser genericLinksParser,
            AssistContentRequester assistContentRequester,
            @NonNull WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            MultiInstanceHelper multiInstanceHelper,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            DesktopModeEventLogger desktopModeEventLogger,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DesktopState desktopState,
            DesktopConfig desktopConfig,
            WindowDecorationActions windowDecorationActions) {
        this (context, userContext, displayController, taskResourceLoader, splitScreenController,
                desktopUserRepositories, taskOrganizer, taskInfo, taskSurface, handler,
                mainExecutor, mainDispatcher, bgScope, bgExecutor, transitions, choreographer,
                syncQueue, appHeaderViewHolderFactory, appHandleViewHolderFactory,
                rootTaskDisplayAreaOrganizer, genericLinksParser, assistContentRequester,
                SurfaceControl.Builder::new, SurfaceControl.Transaction::new,
                WindowContainerTransaction::new, SurfaceControl::new, new WindowManagerWrapper(
                        context.getSystemService(WindowManager.class)),
                new SurfaceControlViewHostFactory() {},
                windowDecorViewHostSupplier,
                DefaultMaximizeMenuFactory.INSTANCE,
                DefaultHandleMenuFactory.INSTANCE, multiInstanceHelper,
                windowDecorCaptionRepository, desktopModeEventLogger,
                desktopModeUiEventLogger, desktopModeCompatPolicy,
                desktopState, desktopConfig, windowDecorationActions);
    }

    DesktopModeWindowDecoration(
            Context context,
            @NonNull Context userContext,
            DisplayController displayController,
            @NonNull WindowDecorTaskResourceLoader taskResourceLoader,
            SplitScreenController splitScreenController,
            DesktopUserRepositories desktopUserRepositories,
            ShellTaskOrganizer taskOrganizer,
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            @ShellMainThread Handler handler,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread MainCoroutineDispatcher mainDispatcher,
            @ShellBackgroundThread CoroutineScope bgScope,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            Transitions transitions,
            Choreographer choreographer,
            SyncTransactionQueue syncQueue,
            AppHeaderViewHolder.Factory appHeaderViewHolderFactory,
            AppHandleViewHolder.Factory appHandleViewHolderFactory,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            AppToWebGenericLinksParser genericLinksParser,
            AssistContentRequester assistContentRequester,
            Supplier<SurfaceControl.Builder> surfaceControlBuilderSupplier,
            Supplier<SurfaceControl.Transaction> surfaceControlTransactionSupplier,
            Supplier<WindowContainerTransaction> windowContainerTransactionSupplier,
            Supplier<SurfaceControl> surfaceControlSupplier,
            WindowManagerWrapper windowManagerWrapper,
            SurfaceControlViewHostFactory surfaceControlViewHostFactory,
            @NonNull WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            MaximizeMenuFactory maximizeMenuFactory,
            HandleMenuFactory handleMenuFactory,
            MultiInstanceHelper multiInstanceHelper,
            WindowDecorCaptionRepository windowDecorCaptionRepository,
            DesktopModeEventLogger desktopModeEventLogger,
            DesktopModeUiEventLogger desktopModeUiEventLogger,
            DesktopModeCompatPolicy desktopModeCompatPolicy,
            DesktopState desktopState,
            DesktopConfig desktopConfig,
            WindowDecorationActions windowDecorationActions) {
        super(context, handler, transitions, userContext, displayController, taskOrganizer,
                taskInfo, taskSurface, surfaceControlBuilderSupplier,
                surfaceControlTransactionSupplier, windowContainerTransactionSupplier,
                surfaceControlSupplier, surfaceControlViewHostFactory, windowDecorViewHostSupplier,
                desktopModeEventLogger);
        mSplitScreenController = splitScreenController;
        mHandler = handler;
        mMainExecutor = mainExecutor;
        mMainDispatcher = mainDispatcher;
        mBgScope = bgScope;
        mBgExecutor = bgExecutor;
        mTransitions = transitions;
        mChoreographer = choreographer;
        mSyncQueue = syncQueue;
        mAppHeaderViewHolderFactory = appHeaderViewHolderFactory;
        mAppHandleViewHolderFactory = appHandleViewHolderFactory;
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
        mGenericLinksParser = genericLinksParser;
        mAssistContentRequester = assistContentRequester;
        mMaximizeMenuFactory = maximizeMenuFactory;
        mHandleMenuFactory = handleMenuFactory;
        mMultiInstanceHelper = multiInstanceHelper;
        mWindowManagerWrapper = windowManagerWrapper;
        mWindowDecorCaptionRepository = windowDecorCaptionRepository;
        mDesktopUserRepositories = desktopUserRepositories;
        mTaskResourceLoader = taskResourceLoader;
        mTaskResourceLoader.onWindowDecorCreated(taskInfo);
        mDesktopModeCompatPolicy = desktopModeCompatPolicy;
        mDesktopModeUiEventLogger = desktopModeUiEventLogger;
        mDesktopState = desktopState;
        mDesktopConfig = desktopConfig;
        mWindowDecorationActions = windowDecorationActions;
    }

    /**
     * Adds a drag resize observer that gets notified on the task being drag resized.
     *
     * @param dragResizeListener The observing object to be added.
     */
    public void addDragResizeListener(DragEventListener dragResizeListener) {
        mTaskDragResizer.addDragEventListener(dragResizeListener);
    }

    /**
     * Removes an already existing drag resize observer.
     *
     * @param dragResizeListener observer to be removed.
     */
    public void removeDragResizeListener(DragEventListener dragResizeListener) {
        mTaskDragResizer.removeDragEventListener(dragResizeListener);
    }

    void setCaptionListeners(
            View.OnClickListener onCaptionButtonClickListener,
            View.OnTouchListener onCaptionTouchListener,
            View.OnLongClickListener onLongClickListener,
            View.OnGenericMotionListener onGenericMotionListener) {
        mOnCaptionButtonClickListener = onCaptionButtonClickListener;
        mOnCaptionTouchListener = onCaptionTouchListener;
        mOnCaptionLongClickListener = onLongClickListener;
        mOnCaptionGenericMotionListener = onGenericMotionListener;
    }

    void setExclusionRegionListener(ExclusionRegionListener exclusionRegionListener) {
        mExclusionRegionListener = exclusionRegionListener;
    }

    void setDragPositioningCallback(DragPositioningCallback dragPositioningCallback) {
        mDragPositioningCallback = dragPositioningCallback;
    }

    @Override
    void onExclusionRegionChanged(@NonNull Region exclusionRegion) {
        if (Flags.appHandleNoRelayoutOnExclusionChange() && isAppHandle(mWindowDecorViewHolder)) {
            // Avoid unnecessary relayouts for app handle. See b/383672263
            return;
        }
        relayout(mTaskInfo, mHasGlobalFocus, exclusionRegion);
    }

    @Override
    void relayout(ActivityManager.RunningTaskInfo taskInfo, boolean hasGlobalFocus,
            @NonNull Region displayExclusionRegion) {
        final SurfaceControl.Transaction t = mSurfaceControlTransactionSupplier.get();
        // The visibility, crop and position of the task should only be set when a task is
        // fluid resizing. In all other cases, it is expected that the transition handler sets
        // those task properties to allow the handler time to animate with full control of the task
        // leash. In general, allowing the window decoration to set any of these is likely to cause
        // incorrect frames and flickering because relayouts from TaskListener#onTaskInfoChanged
        // aren't synchronized with shell transition callbacks, so if they come too early it
        // might show/hide or crop the task at a bad time.
        // Fluid resizing is exempt from this because it intentionally doesn't use shell
        // transitions to resize the task, so onTaskInfoChanged relayouts is the only way to make
        // sure the crop is set correctly.
        final boolean shouldSetTaskVisibilityPositionAndCrop =
                !mDesktopConfig.isVeiledResizeEnabled()
                        && mTaskDragResizer.isResizingOrAnimating();
        // For headers only (i.e. in freeform): use |applyStartTransactionOnDraw| so that the
        // transaction (that applies task crop) is synced with the buffer transaction (that draws
        // the View). Both will be shown on screen at the same, whereas applying them independently
        // causes flickering. See b/270202228.
        final boolean applyTransactionOnDraw = taskInfo.isFreeform();
        relayout(taskInfo, t, t, applyTransactionOnDraw, shouldSetTaskVisibilityPositionAndCrop,
                hasGlobalFocus, displayExclusionRegion, /* inSyncWithTransition= */ false);
        if (!applyTransactionOnDraw) {
            t.apply();
        }
    }

    /**
     * Disables resizing for the given edge.
     *
     * @param disabledResizingEdge edge to disable.
     * @param shouldDelayUpdate whether the update should be executed immediately or delayed.
     */
    public void updateDisabledResizingEdge(
            DragResizeWindowGeometry.DisabledEdge disabledResizingEdge, boolean shouldDelayUpdate) {
        mDisabledResizingEdge = disabledResizingEdge;
        final boolean inFullImmersive = mDesktopUserRepositories.getCurrent()
                .isTaskInFullImmersiveState(mTaskInfo.taskId);
        if (shouldDelayUpdate) {
            return;
        }
        updateDragResizeListenerIfNeeded(mDecorationContainerSurface, inFullImmersive);
    }

    void relayout(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            boolean applyStartTransactionOnDraw, boolean shouldSetTaskVisibilityPositionAndCrop,
            boolean hasGlobalFocus, @NonNull Region displayExclusionRegion,
            boolean inSyncWithTransition) {
        Trace.beginSection("DesktopModeWindowDecoration#relayout");

        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_APP_TO_WEB.isTrue()) {
            setCapturedLink(taskInfo.capturedLink, taskInfo.capturedLinkTimestamp);
        }

        if (DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue()) {
            final Context dc = mDisplayController.getDisplayContext(taskInfo.displayId);
            if (dc != null) {
                mWindowManagerWrapper.updateWindowManager(dc.getSystemService(WindowManager.class));
            }
        }

        if (isHandleMenuActive()) {
            mHandleMenu.relayout(
                    startT,
                    mResult.mCaptionX,
                    // Add top padding to the caption Y so that the menu is shown over what is the
                    // actual contents of the caption, ignoring padding. This is currently relevant
                    // to the Header in desktop immersive.
                    mResult.mCaptionY + mResult.mCaptionTopPadding);
        }

        if (isOpenByDefaultDialogActive()) {
            mOpenByDefaultDialog.relayout(taskInfo);
        }

        final boolean inFullImmersive = mDesktopUserRepositories.getProfile(taskInfo.userId)
                .isTaskInFullImmersiveState(taskInfo.taskId);
        updateRelayoutParams(mRelayoutParams, mContext, taskInfo, mSplitScreenController,
                applyStartTransactionOnDraw, shouldSetTaskVisibilityPositionAndCrop,
                mIsStatusBarVisible, mIsKeyguardVisibleAndOccluded, inFullImmersive,
                mIsDragging, mDisplayController.getInsetsState(taskInfo.displayId), hasGlobalFocus,
                displayExclusionRegion,
                /* shouldIgnoreCornerRadius= */ mIsRecentsTransitionRunning
                        && DesktopModeFlags
                        .ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX.isTrue(),
                mDesktopModeCompatPolicy.shouldExcludeCaptionFromAppBounds(taskInfo),
                mDesktopConfig, inSyncWithTransition);

        final WindowDecorLinearLayout oldRootView = mResult.mRootView;
        final SurfaceControl oldDecorationSurface = mDecorationContainerSurface;
        final WindowContainerTransaction wct = new WindowContainerTransaction();

        relayout(mRelayoutParams, startT, finishT, wct, oldRootView, mResult);
        // After this line, mTaskInfo is up-to-date and should be used instead of taskInfo

        if (!wct.isEmpty()) {
            if (DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue()
                    && mRelayoutParams.mShouldSetAppBounds) {
                // When expanding from PiP to freeform, we need to start a Transition for applying
                // the inset changes so that PiP receives the insets for the final bounds. This is
                // because |mShouldSetAppBounds| applies the insets by modifying app bounds, which
                // can cause a bounds offset that needs to be reported to transition handlers.
                Trace.beginSection("DesktopModeWindowDecoration#relayout-startTransition");
                mHandler.post(() -> mTransitions.startTransition(TRANSIT_CHANGE, wct,
                        /* handler= */ null));
            } else {
                Trace.beginSection("DesktopModeWindowDecoration#relayout-applyWCT");
                mBgExecutor.execute(() -> mTaskOrganizer.applyTransaction(wct));
            }
            Trace.endSection();
        }

        if (mResult.mRootView == null) {
            // This means something blocks the window decor from showing, e.g. the task is hidden.
            // Nothing is set up in this case including the decoration surface.
            if (mDesktopState.canEnterDesktopMode() && isEducationOrHandleReportingEnabled()) {
                notifyNoCaptionHandle();
            }
            mExclusionRegionListener.onExclusionRegionDismissed(mTaskInfo.taskId);
            disposeStatusBarInputLayer();
            Trace.endSection(); // DesktopModeWindowDecoration#relayout
            return;
        }

        if (DesktopModeFlags.SKIP_DECOR_VIEW_RELAYOUT_WHEN_CLOSING_BUGFIX.isTrue()
                ? (oldRootView != mResult.mRootView && taskInfo.isVisibleRequested)
                : oldRootView != mResult.mRootView) {
            disposeStatusBarInputLayer();
            mWindowDecorViewHolder = createViewHolder();
            // Load these only when first creating the view.
            loadTaskNameAndIconInBackground((name, icon) -> {
                final AppHeaderViewHolder appHeader = asAppHeader(mWindowDecorViewHolder);
                if (appHeader != null) {
                    appHeader.setAppName(name);
                    appHeader.setAppIcon(icon);
                    if (mDesktopState.canEnterDesktopMode()
                            && isEducationOrHandleReportingEnabled()) {
                        notifyCaptionStateChanged();
                    }
                }
            });
        }

        if (mDesktopState.canEnterDesktopMode() && isEducationOrHandleReportingEnabled()) {
            notifyCaptionStateChanged();
        }

        Trace.beginSection("DesktopModeWindowDecoration#relayout-bindData");
        if (isAppHandle(mWindowDecorViewHolder)) {
            updateAppHandleViewHolder();
        } else {
            updateAppHeaderViewHolder(inFullImmersive, hasGlobalFocus);
        }
        Trace.endSection();

        if (!hasGlobalFocus) {
            closeHandleMenu();
            closeManageWindowsMenu();
            closeMaximizeMenu();
            if (!DesktopExperienceFlags.ENABLE_APP_HANDLE_POSITION_REPORTING.isTrue()) {
                notifyNoCaptionHandle();
            }
        }
        updateDragResizeListenerIfNeeded(oldDecorationSurface, inFullImmersive);
        updateMaximizeMenu(startT, inFullImmersive);
        Trace.endSection(); // DesktopModeWindowDecoration#relayout
    }

    /**
     * Loads the task's name and icon in a background thread and posts the results back in the
     * main thread.
     */
    private void loadTaskNameAndIconInBackground(BiConsumer<CharSequence, Bitmap> onResult) {
        if (mWindowDecorViewHolder == null) return;
        if (asAppHeader(mWindowDecorViewHolder) == null) {
            // Only needed when drawing a header.
            return;
        }
        if (mLoadAppInfoRunnable != null) {
            mBgExecutor.removeCallbacks(mLoadAppInfoRunnable);
        }
        if (mSetAppInfoRunnable != null) {
            mMainExecutor.removeCallbacks(mSetAppInfoRunnable);
        }
        mLoadAppInfoRunnable = () -> {
            final CharSequence name = mTaskResourceLoader.getName(mTaskInfo);
            final Bitmap icon = mTaskResourceLoader.getHeaderIcon(mTaskInfo);
            mSetAppInfoRunnable = () -> {
                onResult.accept(name, icon);
            };
            mMainExecutor.execute(mSetAppInfoRunnable);
        };
        mBgExecutor.execute(mLoadAppInfoRunnable);
    }

    private boolean showInputLayer() {
        if (!DesktopModeFlags.ENABLE_INPUT_LAYER_TRANSITION_FIX.isTrue()) {
            return isCaptionVisible();
        }
        // Don't show the input layer during the recents transition, otherwise it could become
        // touchable while in overview, during quick-switch or even for a short moment after going
        // Home.
        return isCaptionVisible() && !mIsRecentsTransitionRunning;
    }

    private boolean isCaptionVisible() {
        return mTaskInfo.isVisible && mIsCaptionVisible;
    }

    private void setCapturedLink(Uri capturedLink, long timeStamp) {
        if (capturedLink == null
                || (mCapturedLink != null && mCapturedLink.mTimeStamp == timeStamp)) {
            return;
        }
        mCapturedLink = new CapturedLink(capturedLink, timeStamp);
    }

    @Nullable
    private Intent getBrowserLink() {
        final Uri browserLink;
        if (mWebUri != null) {
            browserLink = mWebUri;
        } else if (isCapturedLinkAvailable()) {
            browserLink = mCapturedLink.mUri;
        } else {
            browserLink = mGenericLink;
        }

        if (browserLink == null) return null;
        return AppToWebUtils.getBrowserIntent(browserLink, mContext.getPackageManager(),
                mUserContext.getUserId());

    }

    @Nullable
    private Intent getAppLink() {
        return mWebUri == null ? null
                : AppToWebUtils.getAppIntent(mWebUri, mContext.getPackageManager(),
                        mUserContext.getUserId());
    }

    private boolean isBrowserApp() {
        final ComponentName baseActivity = mTaskInfo.baseActivity;
        return baseActivity != null && AppToWebUtils.isBrowserApp(mContext,
                baseActivity.getPackageName(), mUserContext.getUserId());
    }

    UserHandle getUser() {
        return mUserContext.getUser();
    }

    private void updateDragResizeListenerIfNeeded(@Nullable SurfaceControl containerSurface,
            boolean inFullImmersive) {
        final boolean taskPositionChanged = !mTaskInfo.positionInParent.equals(mPositionInParent);
        if (!isDragResizable(mTaskInfo, inFullImmersive)) {
            if (taskPositionChanged) {
                // We still want to track caption bar's exclusion region on a non-resizeable task.
                updateExclusionRegion(inFullImmersive);
            }
            closeDragResizeListener();
            return;
        }
        updateDragResizeListener(containerSurface,
                (geometryChanged) -> {
                    if (geometryChanged || taskPositionChanged) {
                        updateExclusionRegion(inFullImmersive);
                    }
                });
    }

    private void updateDragResizeListener(@Nullable SurfaceControl containerSurface,
            Consumer<Boolean> onUpdateFinished) {
        final boolean containerSurfaceChanged = containerSurface != mDecorationContainerSurface;
        final boolean isFirstDragResizeListener = mDragResizeListener == null;
        final boolean shouldCreateListener = containerSurfaceChanged || isFirstDragResizeListener;
        if (containerSurfaceChanged) {
            closeDragResizeListener();
        }
        if (shouldCreateListener) {
            final ShellExecutor bgExecutor =
                    DesktopModeFlags.ENABLE_DRAG_RESIZE_SET_UP_IN_BG_THREAD.isTrue()
                            ? mBgExecutor : mMainExecutor;
            mDragResizeListener = new DragResizeInputListener(
                    mContext,
                    WindowManagerGlobal.getWindowSession(),
                    mMainExecutor,
                    bgExecutor,
                    mTaskInfo,
                    mHandler,
                    mChoreographer,
                    mDisplay.getDisplayId(),
                    mDecorationContainerSurface,
                    mDragPositioningCallback,
                    mSurfaceControlBuilderSupplier,
                    mSurfaceControlTransactionSupplier,
                    mDisplayController,
                    mDesktopModeEventLogger);
        }
        final DragResizeInputListener newListener = mDragResizeListener;
        final int touchSlop = ViewConfiguration.get(mResult.mRootView.getContext())
                .getScaledTouchSlop();
        final Resources res = mResult.mRootView.getResources();
        final DragResizeWindowGeometry newGeometry = new DragResizeWindowGeometry(
                DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue()
                        ? mResult.mCornerRadius : mRelayoutParams.mCornerRadius,
                new Size(mResult.mWidth, mResult.mHeight),
                getResizeEdgeHandleSize(res), getResizeHandleEdgeInset(res),
                getFineResizeCornerSize(res), getLargeResizeCornerSize(res),
                mDisabledResizingEdge);
        newListener.addInitializedCallback(() -> {
            onUpdateFinished.accept(newListener.setGeometry(newGeometry, touchSlop));
        });
    }

    private static boolean isDragResizable(ActivityManager.RunningTaskInfo taskInfo,
            boolean inFullImmersive) {
        if (inFullImmersive) {
            // Task cannot be resized in full immersive.
            return false;
        }
        if (DesktopModeFlags.ENABLE_WINDOWING_SCALED_RESIZING.isTrue()) {
            return taskInfo.isFreeform();
        }
        return taskInfo.isFreeform() && taskInfo.isResizeable;
    }

    private void notifyCaptionStateChanged() {
        if (!mDesktopState.canEnterDesktopMode() || !isEducationOrHandleReportingEnabled()) {
            return;
        }
        if (!isCaptionVisible()) {
            notifyNoCaptionHandle();
        } else if (isAppHandle(mWindowDecorViewHolder)) {
            // App handle is visible since `mWindowDecorViewHolder` is of type
            // [AppHandleViewHolder].
            final CaptionState captionState = new CaptionState.AppHandle(mTaskInfo,
                    isHandleMenuActive(), getCurrentAppHandleBounds(), isCapturedLinkAvailable(),
                    getAppHandleIdentifier(), mHasGlobalFocus);
            mWindowDecorCaptionRepository.notifyCaptionChanged(captionState);
        } else {
            // App header is visible since `mWindowDecorViewHolder` is of type
            // [AppHeaderViewHolder].
            final AppHeaderViewHolder appHeader = asAppHeader(mWindowDecorViewHolder);
            if (appHeader != null) {
                appHeader.runOnAppChipGlobalLayout(
                        () -> {
                            notifyAppHeaderStateChanged();
                            return Unit.INSTANCE;
                        });
            }
        }
    }

    private boolean isCapturedLinkAvailable() {
        return mCapturedLink != null && !mCapturedLink.mUsed;
    }

    private void onCapturedLinkUsed() {
        if (mCapturedLink != null) {
            mCapturedLink.setUsed();
        }
    }

    private void notifyNoCaptionHandle() {
        if (!mDesktopState.canEnterDesktopMode() || !isEducationOrHandleReportingEnabled()) {
            return;
        }
        mWindowDecorCaptionRepository.notifyCaptionChanged(
                new CaptionState.NoCaption(mTaskInfo.taskId));
    }

    /**
     * Returns app handle bounds if app handle is visible. Otherwise, returns empty Rect.
     */
    private Rect getCurrentAppHandleBounds() {
        if (!DesktopExperienceFlags.ENABLE_APP_HANDLE_POSITION_REPORTING.isTrue()) {
            return new Rect(
                    mResult.mCaptionX,
                    /* top= */ 0,
                    mResult.mCaptionX + mResult.mCaptionWidth,
                    mResult.mCaptionHeight);
        }

        if (!isAppHandle(mWindowDecorViewHolder) || !isCaptionVisible()) {
            return new Rect();
        }

        final Rect handleBounds = new Rect(
                mResult.mCaptionX,
                mResult.mCaptionY,
                mResult.mCaptionX + mResult.mCaptionWidth,
                mResult.mCaptionY + mResult.mCaptionHeight);
        if (mSplitScreenController.getSplitPosition(mTaskInfo.taskId)
                == SPLIT_POSITION_BOTTOM_OR_RIGHT
        ) {
            if (mSplitScreenController.isLeftRightSplit()) {
                final Rect rightStageBounds = new Rect();
                mSplitScreenController.getStageBounds(new Rect(), rightStageBounds);
                handleBounds.offset(rightStageBounds.left, 0);
            } else {
                final Rect bottomStageBounds = new Rect();
                mSplitScreenController.getRefStageBounds(new Rect(), bottomStageBounds);
                handleBounds.offset(0, bottomStageBounds.top);
            }
        }

        return handleBounds;
    }

    private void notifyAppHeaderStateChanged() {
        final AppHeaderViewHolder appHeader = asAppHeader(mWindowDecorViewHolder);
        if (appHeader == null) {
            return;
        }
        final Rect appChipPositionInWindow = appHeader.getAppChipLocationInWindow();
        final Rect taskBounds = mTaskInfo.configuration.windowConfiguration.getBounds();
        final Rect appChipGlobalPosition = new Rect(
                taskBounds.left + appChipPositionInWindow.left,
                taskBounds.top + appChipPositionInWindow.top,
                taskBounds.left + appChipPositionInWindow.right,
                taskBounds.top + appChipPositionInWindow.bottom);
        final CaptionState captionState = new CaptionState.AppHeader(
                mTaskInfo,
                isHandleMenuActive(),
                appChipGlobalPosition,
                isCapturedLinkAvailable(),
                mHasGlobalFocus);

        mWindowDecorCaptionRepository.notifyCaptionChanged(captionState);
    }

    /** Updates app handle position and notifies [AppHandleNotifier] of any changes. */
    private AppHandleIdentifier getAppHandleIdentifier() {
        return new AppHandleIdentifier(
                getCurrentAppHandleBounds(),
                mTaskInfo.displayId,
                mTaskInfo.taskId,
                getAppHandleIdentifierWindowingMode()
        );
    }

    /**
     * Returns the windowing mode of the App Handle. Throws an {@link IllegalArgumentException} if
     * task does not have an app handle
     */
    private AppHandleWindowingMode getAppHandleIdentifierWindowingMode() {
        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()
                && !mDesktopState.isDesktopModeSupportedOnDisplay(mDisplay)) {
            return AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_BUBBLE;
        }
        if (mSplitScreenController.isTaskInSplitScreen(mTaskInfo.taskId)) {
            return AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_SPLIT_SCREEN;
        }
        if (isAppHandle(mWindowDecorViewHolder)) {
            return AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_FULLSCREEN;
        }
        throw new IllegalArgumentException("Attempting to get the AppHandleWindowingMode of a task "
                + "that does not have an app handle");
    }

    private void updateMaximizeMenu(SurfaceControl.Transaction startT, boolean inFullImmersive) {
        if (!isDragResizable(mTaskInfo, inFullImmersive) || !isMaximizeMenuActive()) {
            return;
        }
        if (!mTaskInfo.isVisible()) {
            closeMaximizeMenu();
        } else {
            mMaximizeMenu.positionMenu(startT);
        }
    }

    private Point determineHandlePosition() {
        final Point position = new Point(mResult.mCaptionX, 0);
        if (mSplitScreenController.getSplitPosition(mTaskInfo.taskId)
                == SPLIT_POSITION_BOTTOM_OR_RIGHT
        ) {
            if (mSplitScreenController.isLeftRightSplit()) {
                // If this is the right split task, add left stage's width.
                final Rect leftStageBounds = new Rect();
                mSplitScreenController.getStageBounds(leftStageBounds, new Rect());
                position.x += leftStageBounds.width();
            } else {
                final Rect bottomStageBounds = new Rect();
                mSplitScreenController.getRefStageBounds(new Rect(), bottomStageBounds);
                position.y += bottomStageBounds.top;
            }
        }
        return position;
    }

    /**
     * Dispose of the view used to forward inputs in status bar region. Intended to be
     * used any time handle is no longer visible.
     */
    void disposeStatusBarInputLayer() {
        if (!isAppHandle(mWindowDecorViewHolder)
                || !DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
            return;
        }
        asAppHandle(mWindowDecorViewHolder).disposeStatusBarInputLayer();
    }

    /** Update the view holder for app handle. */
    private void updateAppHandleViewHolder() {
        if (!isAppHandle(mWindowDecorViewHolder)) return;
        asAppHandle(mWindowDecorViewHolder).bindData(new AppHandleViewHolder.HandleData(
                mTaskInfo, determineHandlePosition(), mResult.mCaptionWidth,
                mResult.mCaptionHeight, /* showInputLayer= */ showInputLayer(),
                /* isCaptionVisible= */ isCaptionVisible()
        ));
    }

    /** Update the view holder for app header. */
    private void updateAppHeaderViewHolder(boolean inFullImmersive, boolean hasGlobalFocus) {
        if (!isAppHeader(mWindowDecorViewHolder)) return;
        asAppHeader(mWindowDecorViewHolder).bindData(new AppHeaderViewHolder.HeaderData(
                mTaskInfo,
                DesktopModeUtils.isTaskMaximized(mTaskInfo, mDisplayController),
                inFullImmersive,
                hasGlobalFocus,
                /* maximizeHoverEnabled= */ canOpenMaximizeMenu(
                    /* animatingTaskResizeOrReposition= */ false),
                isCaptionVisible()
        ));
    }

    private WindowDecorationViewHolder createViewHolder() {
        if (mRelayoutParams.mLayoutResId == R.layout.desktop_mode_app_handle) {
            return mAppHandleViewHolderFactory.create(
                    mResult.mRootView,
                    mDecorWindowContext,
                    mOnCaptionTouchListener,
                    mOnCaptionButtonClickListener,
                    mWindowManagerWrapper,
                    mHandler,
                    mDesktopModeUiEventLogger
            );
        } else if (mRelayoutParams.mLayoutResId == R.layout.desktop_mode_app_header) {
            return mAppHeaderViewHolderFactory.create(
                    mResult.mRootView,
                    mDecorWindowContext,
                    mWindowDecorationActions,
                    mOnCaptionTouchListener,
                    mOnCaptionButtonClickListener,
                    mOnCaptionLongClickListener,
                    mOnCaptionGenericMotionListener,
                    /* onMaximizeHoverAnimationFinishedListener= */ () -> {
                        createMaximizeMenu();
                        return Unit.INSTANCE;
                    },
                    mDesktopModeUiEventLogger);
        }
        throw new IllegalArgumentException("Unexpected layout resource id");
    }

    private boolean isAppHandle(WindowDecorationViewHolder viewHolder) {
        return viewHolder instanceof AppHandleViewHolder;
    }

    private boolean isAppHeader(WindowDecorationViewHolder viewHolder) {
        return viewHolder instanceof AppHeaderViewHolder;
    }

    @Nullable
    private AppHandleViewHolder asAppHandle(WindowDecorationViewHolder viewHolder) {
        if (viewHolder instanceof AppHandleViewHolder) {
            return (AppHandleViewHolder) viewHolder;
        }
        return null;
    }

    @Nullable
    private AppHeaderViewHolder asAppHeader(WindowDecorationViewHolder viewHolder) {
        if (viewHolder instanceof AppHeaderViewHolder) {
            return (AppHeaderViewHolder) viewHolder;
        }
        return null;
    }

    @VisibleForTesting
    static void updateRelayoutParams(
            RelayoutParams relayoutParams,
            Context context,
            ActivityManager.RunningTaskInfo taskInfo,
            SplitScreenController splitScreenController,
            boolean applyStartTransactionOnDraw,
            boolean shouldSetTaskVisibilityPositionAndCrop,
            boolean isStatusBarVisible,
            boolean isKeyguardVisibleAndOccluded,
            boolean inFullImmersiveMode,
            boolean isDragging,
            @NonNull InsetsState displayInsetsState,
            boolean hasGlobalFocus,
            @NonNull Region displayExclusionRegion,
            boolean shouldIgnoreCornerRadius,
            boolean shouldExcludeCaptionFromAppBounds,
            DesktopConfig desktopConfig,
            boolean inSyncWithTransition) {
        final int captionLayoutId = getDesktopModeWindowDecorLayoutId(taskInfo.getWindowingMode());
        final boolean isAppHeader =
                captionLayoutId == R.layout.desktop_mode_app_header;
        final boolean isAppHandle = captionLayoutId == R.layout.desktop_mode_app_handle;
        relayoutParams.reset();
        relayoutParams.mRunningTaskInfo = taskInfo;
        relayoutParams.mLayoutResId = captionLayoutId;
        relayoutParams.mCaptionHeightCalculator = getCaptionHeightCalculator(
                taskInfo.getWindowingMode());
        relayoutParams.mCaptionWidthId = getCaptionWidthId(relayoutParams.mLayoutResId);
        relayoutParams.mHasGlobalFocus = hasGlobalFocus;
        relayoutParams.mDisplayExclusionRegion.set(displayExclusionRegion);
        // Allow the handle view to be delayed since the handle is just a small addition to the
        // window, whereas the header cannot be delayed because it is expected to be visible from
        // the first frame.
        relayoutParams.mAsyncViewHost = isAppHandle;

        boolean showCaption;
        if (DesktopModeFlags.ENABLE_DESKTOP_IMMERSIVE_DRAG_BUGFIX.isTrue() && isDragging) {
            // If the task is being dragged, the caption should not be hidden so that it continues
            // receiving input
            showCaption = true;
        } else if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()) {
            if (inFullImmersiveMode) {
                showCaption = (isStatusBarVisible && !isKeyguardVisibleAndOccluded);
            } else {
                showCaption = taskInfo.isFreeform()
                        || (isStatusBarVisible && !isKeyguardVisibleAndOccluded);
            }
        } else {
            // Caption should always be visible in freeform mode. When not in freeform,
            // align with the status bar except when showing over keyguard (where it should not
            // shown).
            //  TODO(b/356405803): Investigate how it's possible for the status bar visibility to
            //   be false while a freeform window is open if the status bar is always
            //   forcibly-shown. It may be that the InsetsState (from which |mIsStatusBarVisible|
            //   is set) still contains an invisible insets source in immersive cases even if the
            //   status bar is shown?
            showCaption = taskInfo.isFreeform()
                    || (isStatusBarVisible && !isKeyguardVisibleAndOccluded);
        }
        relayoutParams.mIsCaptionVisible = showCaption;
        final boolean isBottomSplit = !splitScreenController.isLeftRightSplit()
                && splitScreenController.getSplitPosition(taskInfo.taskId)
                == SPLIT_POSITION_BOTTOM_OR_RIGHT;
        relayoutParams.mIsInsetSource = (isAppHeader && !inFullImmersiveMode) || isBottomSplit;
        if (isAppHeader) {
            if (TaskInfoKt.isTransparentCaptionBarAppearance(taskInfo)) {
                // The app is requesting to customize the caption bar, which means input on
                // customizable/exclusion regions must go to the app instead of to the system.
                // This may be accomplished with spy windows or custom touchable regions:
                if (DesktopModeFlags.ENABLE_ACCESSIBLE_CUSTOM_HEADERS.isTrue()) {
                    // Set the touchable region of the caption to only the areas where input should
                    // be handled by the system (i.e. non custom-excluded areas). The region will
                    // be calculated based on occluding caption elements and exclusion areas
                    // reported by the app.
                    relayoutParams.mLimitTouchRegionToSystemAreas = true;
                } else {
                    // Allow input to fall through to the windows below so that the app can respond
                    // to input events on their custom content.
                    relayoutParams.mInputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_SPY;
                }
            } else {
                if (ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION.isTrue()) {
                    if (shouldExcludeCaptionFromAppBounds) {
                        relayoutParams.mShouldSetAppBounds = true;
                    } else {
                        // Force-consume the caption bar insets when the app tries to hide the
                        // caption. This improves app compatibility of immersive apps.
                        relayoutParams.mInsetSourceFlags |= FLAG_FORCE_CONSUMING;
                    }
                }
            }
            if (ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS.isTrue()) {
                if (shouldExcludeCaptionFromAppBounds) {
                    relayoutParams.mShouldSetAppBounds = true;
                } else {
                    // Always force-consume the caption bar insets for maximum app compatibility,
                    // including non-immersive apps that just don't handle caption insets properly.
                    relayoutParams.mInsetSourceFlags |= FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR;
                }
            }
            if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()
                    && inFullImmersiveMode) {
                final Rect taskBounds = taskInfo.getConfiguration().windowConfiguration.getBounds();
                final Insets systemBarInsets = displayInsetsState.calculateInsets(
                        taskBounds, taskBounds,
                        WindowInsets.Type.systemBars() & ~WindowInsets.Type.captionBar(),
                        false /* ignoreVisibility */);
                relayoutParams.mCaptionTopPadding = systemBarInsets.top;
            }
            // Report occluding elements as bounding rects to the insets system so that apps can
            // draw in the empty space in the center:
            //   First, the "app chip" section of the caption bar (+ some extra margins).
            final RelayoutParams.OccludingCaptionElement appChipElement =
                    new RelayoutParams.OccludingCaptionElement();
            appChipElement.mWidthResId = R.dimen.desktop_mode_customizable_caption_margin_start;
            appChipElement.mAlignment = RelayoutParams.OccludingCaptionElement.Alignment.START;
            relayoutParams.mOccludingCaptionElements.add(appChipElement);
            //   Then, the right-aligned section (drag space, maximize and close buttons).
            final RelayoutParams.OccludingCaptionElement controlsElement =
                    new RelayoutParams.OccludingCaptionElement();
            controlsElement.mWidthResId = R.dimen.desktop_mode_customizable_caption_margin_end;
            if (DesktopModeFlags.ENABLE_MINIMIZE_BUTTON.isTrue()) {
                controlsElement.mWidthResId =
                      R.dimen.desktop_mode_customizable_caption_with_minimize_button_margin_end;
            }
            controlsElement.mAlignment = RelayoutParams.OccludingCaptionElement.Alignment.END;
            relayoutParams.mOccludingCaptionElements.add(controlsElement);

            relayoutParams.mInputFeatures |=
                        WindowManager.LayoutParams.INPUT_FEATURE_DISPLAY_TOPOLOGY_AWARE;
        } else if (isAppHandle && !DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
            // The focused decor (fullscreen/split) does not need to handle input because input in
            // the App Handle is handled by the InputMonitor in DesktopModeWindowDecorViewModel.
            // Note: This does not apply with the above flag enabled as the status bar input layer
            // will forward events to the handle directly.
            relayoutParams.mInputFeatures
                    |= WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
        }
        if (isAppHeader
                && desktopConfig.useWindowShadow(/* isFocusedWindow= */ hasGlobalFocus)) {
            if (DesktopExperienceFlags.ENABLE_FREEFORM_BOX_SHADOWS.isTrue()) {
                // Shadows are same for light and dark theme.
                relayoutParams.mBoxShadowSettingsIds = hasGlobalFocus
                        ? new int[]{R.style.BoxShadowParamsKeyFocused,
                                    R.style.BoxShadowParamsAmbientFocused}
                        : new int[]{R.style.BoxShadowParamsKeyUnfocused,
                                    R.style.BoxShadowParamsAmbientUnfocused};

                final DecorThemeUtil decorThemeUtil = new DecorThemeUtil(context);
                if (decorThemeUtil.getAppTheme(taskInfo) == Theme.DARK) {
                    relayoutParams.mBorderSettingsId = hasGlobalFocus
                            ? R.style.BorderSettingsFocusedDark
                            : R.style.BorderSettingsUnfocusedDark;
                } else  {
                    relayoutParams.mBorderSettingsId = hasGlobalFocus
                            ? R.style.BorderSettingsFocusedLight
                            : R.style.BorderSettingsUnfocusedLight;
                }
            } else if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue()) {
                relayoutParams.mShadowRadiusId = hasGlobalFocus
                        ? R.dimen.freeform_decor_shadow_focused_thickness
                        : R.dimen.freeform_decor_shadow_unfocused_thickness;

            } else {
                relayoutParams.mShadowRadius = hasGlobalFocus
                        ? context.getResources().getDimensionPixelSize(
                        R.dimen.freeform_decor_shadow_focused_thickness)
                        : context.getResources().getDimensionPixelSize(
                                R.dimen.freeform_decor_shadow_unfocused_thickness);
            }
        } else {
            if (DesktopExperienceFlags.ENABLE_FREEFORM_BOX_SHADOWS.isTrue()) {
                relayoutParams.mBoxShadowSettingsIds = null;
                relayoutParams.mBorderSettingsId = Resources.ID_NULL;
            } else if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue()) {
                relayoutParams.mShadowRadiusId = Resources.ID_NULL;
            } else {
                relayoutParams.mShadowRadius = INVALID_SHADOW_RADIUS;
            }
        }
        relayoutParams.mApplyStartTransactionOnDraw = applyStartTransactionOnDraw;
        relayoutParams.mSetTaskVisibilityPositionAndCrop = shouldSetTaskVisibilityPositionAndCrop;
        relayoutParams.mInSyncWithTransition = inSyncWithTransition;

        // The configuration used to layout the window decoration. A copy is made instead of using
        // the original reference so that the configuration isn't mutated on config changes and
        // diff checks can be made in WindowDecoration#relayout using the pre/post-relayout
        // configuration. See b/301119301.
        // TODO(b/301119301): consider moving the config data needed for diffs to relayout params
        // instead of using a whole Configuration as a parameter.
        final Configuration windowDecorConfig = new Configuration();
        if (DesktopModeFlags.ENABLE_APP_HEADER_WITH_TASK_DENSITY.isTrue() && isAppHeader) {
            // Should match the density of the task. The task may have had its density overridden
            // to be different that SysUI's.
            windowDecorConfig.setTo(taskInfo.configuration);
        } else if (desktopConfig.useDesktopOverrideDensity()) {
            // The task has had its density overridden, but keep using the system's density to
            // layout the header.
            windowDecorConfig.setTo(context.getResources().getConfiguration());
        } else {
            windowDecorConfig.setTo(taskInfo.configuration);
        }
        relayoutParams.mWindowDecorConfig = windowDecorConfig;

        if (desktopConfig.useRoundedCorners()) {
            if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue()) {
                relayoutParams.mCornerRadiusId = shouldIgnoreCornerRadius ? Resources.ID_NULL :
                        getCornerRadiusId(relayoutParams.mLayoutResId);
            } else {
                relayoutParams.mCornerRadius = shouldIgnoreCornerRadius ? INVALID_CORNER_RADIUS :
                        getCornerRadius(context, relayoutParams.mLayoutResId);
            }
        }
        // Set opaque background for all freeform tasks to prevent freeform tasks below
        // from being visible if freeform task window above is translucent.
        // Otherwise if fluid resize is enabled, add a background to freeform tasks.
        relayoutParams.mShouldSetBackground = desktopConfig.shouldSetBackground(taskInfo);
    }

    @Deprecated
    private static int getCornerRadius(@NonNull Context context, int layoutResId) {
        if (layoutResId == R.layout.desktop_mode_app_header) {
            return loadDimensionPixelSize(context.getResources(),
                    com.android.wm.shell.shared.R.dimen
                            .desktop_windowing_freeform_rounded_corner_radius);
        }
        return INVALID_CORNER_RADIUS;
    }

    private static int getCornerRadiusId(int layoutResId) {
        if (layoutResId == R.layout.desktop_mode_app_header) {
            return com.android.wm.shell.shared.R.dimen
                    .desktop_windowing_freeform_rounded_corner_radius;
        }
        return Resources.ID_NULL;
    }

    /**
     * If task has focused window decor, return the caption id of the fullscreen caption size
     * resource. Otherwise, return ID_NULL and caption width be set to task width.
     */
    private static int getCaptionWidthId(int layoutResId) {
        if (layoutResId == R.layout.desktop_mode_app_handle) {
            return R.dimen.desktop_mode_fullscreen_decor_caption_width;
        }
        return Resources.ID_NULL;
    }

    private Point calculateMaximizeMenuPosition(int menuWidth, int menuHeight) {
        final Point position = new Point();
        final Resources resources = mContext.getResources();
        final DisplayLayout displayLayout =
                mDisplayController.getDisplayLayout(mTaskInfo.displayId);
        if (displayLayout == null) return position;

        final int displayWidth = displayLayout.width();
        final int displayHeight = displayLayout.height();
        final int captionHeight = getCaptionHeight(mTaskInfo.getWindowingMode());

        final ImageButton maximizeWindowButton =
                mResult.mRootView.findViewById(R.id.maximize_window);
        final int[] maximizeButtonLocation = new int[2];
        maximizeWindowButton.getLocationInWindow(maximizeButtonLocation);

        int menuLeft = (mPositionInParent.x + maximizeButtonLocation[0] - (menuWidth
                - maximizeWindowButton.getWidth()) / 2);
        int menuTop = (mPositionInParent.y + captionHeight);
        final int menuRight = menuLeft + menuWidth;
        final int menuBottom = menuTop + menuHeight;

        // If the menu is out of screen bounds, shift it as needed
        if (menuLeft < 0) {
            menuLeft = 0;
        } else if (menuRight > displayWidth) {
            menuLeft = (displayWidth - menuWidth);
        }
        if (menuBottom > displayHeight) {
            menuTop = (displayHeight - menuHeight);
        }

        return new Point(menuLeft, menuTop);
    }

    boolean isHandleMenuActive() {
        return mHandleMenu != null;
    }

    boolean isOpenByDefaultDialogActive() {
        return mOpenByDefaultDialog != null;
    }

    void createOpenByDefaultDialog() {
        if (isOpenByDefaultDialogActive()) return;
        mOpenByDefaultDialog = new OpenByDefaultDialog(
                mContext,
                mTaskInfo,
                mTaskSurface,
                mDisplayController,
                mTaskResourceLoader,
                mSurfaceControlTransactionSupplier,
                mMainDispatcher,
                mBgScope,
                new OpenByDefaultDialog.DialogLifecycleListener() {
                    @Override
                    public void onDialogCreated() {
                        closeHandleMenu();
                    }

                    @Override
                    public void onDialogDismissed() {
                        mOpenByDefaultDialog = null;
                    }
                }
        );
    }

    boolean shouldResizeListenerHandleEvent(@NonNull MotionEvent e, @NonNull Point offset) {
        return mDragResizeListener != null && mDragResizeListener.shouldHandleEvent(e, offset);
    }

    boolean isHandlingDragResize() {
        return mDragResizeListener != null && mDragResizeListener.isHandlingDragResize();
    }

    private void closeDragResizeListener() {
        if (mDragResizeListener == null) {
            return;
        }
        mDragResizeListener.close();
        mDragResizeListener = null;
    }

    /**
     * Create the resize veil for this task. Note the veil's visibility is View.GONE by default
     * until a resize event calls showResizeVeil below.
     */
    private void createResizeVeilIfNeeded() {
        if (mResizeVeil != null) return;
        mResizeVeil = new ResizeVeil(mContext, mDisplayController, mTaskResourceLoader,
                mMainDispatcher, mBgScope, mTaskSurface,
                mSurfaceControlTransactionSupplier, mTaskInfo);
    }

    /**
     * Show the resize veil.
     */
    public void showResizeVeil(Rect taskBounds) {
        createResizeVeilIfNeeded();
        mResizeVeil.showVeil(mTaskSurface, taskBounds, mTaskInfo);
    }

    /**
     * Show the resize veil.
     */
    public void showResizeVeil(SurfaceControl.Transaction tx, Rect taskBounds) {
        createResizeVeilIfNeeded();
        mResizeVeil.showVeil(tx, mTaskSurface, taskBounds, mTaskInfo, false /* fadeIn */);
    }

    /**
     * Set new bounds for the resize veil
     */
    public void updateResizeVeil(Rect newBounds) {
        mResizeVeil.updateResizeVeil(newBounds);
    }

    /**
     * Set new bounds for the resize veil
     */
    public void updateResizeVeil(SurfaceControl.Transaction tx, Rect newBounds) {
        mResizeVeil.updateResizeVeil(tx, newBounds);
    }

    /**
     * Fade the resize veil out.
     */
    public void hideResizeVeil() {
        mResizeVeil.hideVeil();
    }

    private void disposeResizeVeil() {
        if (mResizeVeil == null) return;
        mResizeVeil.dispose();
        mResizeVeil = null;
    }

    /**
     * Determine valid drag area for this task based on elements in the app chip.
     */
    @Override
    @NonNull
    Rect calculateValidDragArea() {
        final int appTextWidth = ((AppHeaderViewHolder)
                mWindowDecorViewHolder).getAppNameTextWidth();
        final int leftButtonsWidth = loadDimensionPixelSize(mContext.getResources(),
                R.dimen.desktop_mode_app_details_width_minus_text) + appTextWidth;
        final int requiredEmptySpace = loadDimensionPixelSize(mContext.getResources(),
                R.dimen.freeform_required_visible_empty_space_in_header);
        final int rightButtonsWidth = loadDimensionPixelSize(mContext.getResources(),
                R.dimen.desktop_mode_right_edge_buttons_width);
        final int taskWidth = mTaskInfo.configuration.windowConfiguration.getBounds().width();
        final DisplayLayout layout = mDisplayController.getDisplayLayout(mTaskInfo.displayId);
        final int displayWidth = layout.width();
        final Rect stableBounds = new Rect();
        layout.getStableBounds(stableBounds);
        return new Rect(
                determineMinX(leftButtonsWidth, rightButtonsWidth, requiredEmptySpace,
                        taskWidth),
                stableBounds.top,
                determineMaxX(leftButtonsWidth, rightButtonsWidth, requiredEmptySpace,
                        taskWidth, displayWidth),
                determineMaxY(requiredEmptySpace, stableBounds));
    }


    /**
     * Determine the lowest x coordinate of a freeform task. Used for restricting drag inputs.
     */
    private int determineMinX(int leftButtonsWidth, int rightButtonsWidth, int requiredEmptySpace,
            int taskWidth) {
        // Do not let apps with < 48dp empty header space go off the left edge at all.
        if (leftButtonsWidth + rightButtonsWidth + requiredEmptySpace > taskWidth) {
            return 0;
        }
        return -taskWidth + requiredEmptySpace + rightButtonsWidth;
    }

    /**
     * Determine the highest x coordinate of a freeform task. Used for restricting drag inputs.
     */
    private int determineMaxX(int leftButtonsWidth, int rightButtonsWidth, int requiredEmptySpace,
            int taskWidth, int displayWidth) {
        // Do not let apps with < 48dp empty header space go off the right edge at all.
        if (leftButtonsWidth + rightButtonsWidth + requiredEmptySpace > taskWidth) {
            return displayWidth - taskWidth;
        }
        return displayWidth - requiredEmptySpace - leftButtonsWidth;
    }

    /**
     * Determine the highest y coordinate of a freeform task. Used for restricting drag inputs.fmdra
     */
    private int determineMaxY(int requiredEmptySpace, Rect stableBounds) {
        return stableBounds.bottom - requiredEmptySpace;
    }


    /**
     * Create and display maximize menu window
     */
    void createMaximizeMenu() {
        if (isMaximizeMenuActive()) return;
        mDesktopModeUiEventLogger.log(mTaskInfo,
                DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_REVEAL_MENU);
        mMaximizeMenu = mMaximizeMenuFactory.create(mSyncQueue, mRootTaskDisplayAreaOrganizer,
                mDisplayController, mWindowDecorationActions, mTaskInfo, mContext,
                (width, height) -> calculateMaximizeMenuPosition(width, height),
                mSurfaceControlTransactionSupplier, mDesktopModeUiEventLogger);

        mMaximizeMenu.show(
                /* isTaskInImmersiveMode= */
                DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()
                        && mDesktopUserRepositories.getProfile(mTaskInfo.userId)
                            .isTaskInFullImmersiveState(mTaskInfo.taskId),
                /* showImmersiveOption= */
                DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()
                        && TaskInfoKt.getRequestingImmersive(mTaskInfo),
                /* showSnapOptions= */ mTaskInfo.isResizeable,
                hovered -> {
                    mIsMaximizeMenuHovered = hovered;
                    onMaximizeHoverStateChanged();
                    return null;
                },
                /* onOutsideTouchListener= */ mCloseMaximizeMenuFunction,
                /* onMaximizeMenuClickedListener= */ mCloseMaximizeMenuFunction
        );
    }

    /** Set whether the app header's maximize button is hovered. */
    void setAppHeaderMaximizeButtonHovered(boolean hovered) {
        mIsAppHeaderMaximizeButtonHovered = hovered;
        onMaximizeHoverStateChanged();
    }

    /**
     * Called when either one of the maximize button in the app header or the maximize menu has
     * changed its hover state.
     */
    void onMaximizeHoverStateChanged() {
        if (!mIsMaximizeMenuHovered && !mIsAppHeaderMaximizeButtonHovered) {
            // Neither is hovered, close the menu.
            if (isMaximizeMenuActive()) {
                mHandler.postDelayed(mCloseMaximizeWindowRunnable, CLOSE_MAXIMIZE_MENU_DELAY_MS);
            }
            return;
        }
        // At least one of the two is hovered, cancel the close if needed.
        mHandler.removeCallbacks(mCloseMaximizeWindowRunnable);
    }

    /**
     * Close the maximize menu window
     */
    @VisibleForTesting
    void closeMaximizeMenu() {
        if (!isMaximizeMenuActive()) return;
        mMaximizeMenu.close(() -> {
            // Request the accessibility service to refocus on the maximize button after closing
            // the menu.
            final AppHeaderViewHolder appHeader = asAppHeader(mWindowDecorViewHolder);
            if (appHeader != null) {
                appHeader.requestAccessibilityFocus();
            }
            return Unit.INSTANCE;
        });
        mMaximizeMenu = null;
    }

    boolean isMaximizeMenuActive() {
        return mMaximizeMenu != null;
    }

    /**
     * Updates app info and creates and displays handle menu window.
     */
    void createHandleMenu(boolean minimumInstancesFound) {
        if (isHandleMenuActive()) return;
        mMinimumInstancesFound = minimumInstancesFound;
        if (AppToWebUtils.canShowAppLinks(mDisplay, mDesktopState)) {
            // Requests assist content. When content is received, calls
            // {@link #onAssistContentReceived} which sets app info and creates the handle menu.
            mAssistContentRequester.requestAssistContent(
                    mTaskInfo.taskId, this::onAssistContentReceived);
        } else {
            // Skip request for assist content as it is only used for links, which are not supported
            onAssistContentReceived(null);
        }
    }

    /**
     * Called when assist content is received. updates the saved links and creates the handle menu.
     */
    @VisibleForTesting
    void onAssistContentReceived(@Nullable AssistContent assistContent) {
        mWebUri = assistContent == null ? null : AppToWebUtils.getSessionWebUri(assistContent);
        updateGenericLink();
        final boolean supportsMultiInstance = mMultiInstanceHelper
                .supportsMultiInstanceSplit(mTaskInfo.baseActivity, mTaskInfo.userId)
                && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MULTI_INSTANCE_FEATURES.isTrue();
        final boolean shouldShowManageWindowsButton = supportsMultiInstance
                && mMinimumInstancesFound;
        final boolean shouldShowChangeAspectRatioButton = HandleMenu.Companion
                .shouldShowChangeAspectRatioButton(mTaskInfo);
        final boolean shouldShowRestartButton = HandleMenu.Companion
                .shouldShowRestartButton(mTaskInfo);
        final boolean inDesktopImmersive = mDesktopUserRepositories.getProfile(mTaskInfo.userId)
                .isTaskInFullImmersiveState(mTaskInfo.taskId);
        final boolean isBrowserApp;
        final Intent openInAppOrBrowserIntent;
        if (AppToWebUtils.canShowAppLinks(mDisplay, mDesktopState)) {
            isBrowserApp = isBrowserApp();
            openInAppOrBrowserIntent = isBrowserApp ? getAppLink() : getBrowserLink();
        } else {
            isBrowserApp = false;
            openInAppOrBrowserIntent = null;
        }
        mHandleMenu = mHandleMenuFactory.create(
                mMainDispatcher,
                mBgScope,
                this,
                mWindowManagerWrapper,
                mWindowDecorationActions,
                mTaskResourceLoader,
                mRelayoutParams.mLayoutResId,
                mSplitScreenController,
                mDesktopState.canEnterDesktopModeOrShowAppHandle(),
                supportsMultiInstance,
                shouldShowManageWindowsButton,
                shouldShowChangeAspectRatioButton,
                mDesktopState.isDesktopModeSupportedOnDisplay(mDisplay),
                shouldShowRestartButton,
                isBrowserApp,
                openInAppOrBrowserIntent,
                mDesktopModeUiEventLogger,
                mResult.mCaptionWidth,
                mResult.mCaptionHeight,
                mResult.mCaptionX,
                // Add top padding to the caption Y so that the menu is shown over what is the
                // actual contents of the caption, ignoring padding. This is currently relevant
                // to the Header in desktop immersive.
                mResult.mCaptionY + mResult.mCaptionTopPadding
        );
        mWindowDecorViewHolder.onHandleMenuOpened();
        mHandleMenu.show(
                /* openInBrowserClickListener= */ (intent) -> {
                    mWindowDecorationActions.onOpenInBrowser(mTaskInfo.taskId, intent);
                    onCapturedLinkUsed();
                    if (Flags.enableDesktopWindowingAppToWebEducationIntegration()) {
                        mWindowDecorCaptionRepository.onAppToWebUsage();
                    }
                    return Unit.INSTANCE;
                },
                /* onOpenByDefaultClickListener= */ () -> {
                    createOpenByDefaultDialog();
                    return Unit.INSTANCE;
                },
                /* onCloseMenuClickListener= */ mCloseHandleMenuFunction,
                /* onOutsideTouchListener= */ mCloseHandleMenuFunction,
                /* onHandleMenuClickedListener= */ mCloseHandleMenuFunction,
                /* forceShowSystemBars= */ inDesktopImmersive
        );
        if (mDesktopState.canEnterDesktopMode() && isEducationOrHandleReportingEnabled()) {
            notifyCaptionStateChanged();
        }
        mMinimumInstancesFound = false;
    }

    void createManageWindowsMenu(@NonNull List<Pair<Integer, TaskSnapshot>> snapshotList) {
        final Function1<Integer, Unit> onOpenInstanceListener = (requestedTaskId) -> {
            closeManageWindowsMenu();
            if (mTaskInfo.taskId != requestedTaskId) {
                mWindowDecorationActions.onOpenInstance(mTaskInfo, requestedTaskId);
            }
            return Unit.INSTANCE;
        };
        if (mTaskInfo.isFreeform()) {
            // The menu uses display-wide coordinates for positioning, so make position the sum
            // of task position and caption position.
            final Rect taskBounds = mTaskInfo.configuration.windowConfiguration.getBounds();
            mManageWindowsMenu = new DesktopHeaderManageWindowsMenu(
                    mTaskInfo,
                    /* x= */ taskBounds.left + mResult.mCaptionX,
                    /* y= */ taskBounds.top + mResult.mCaptionY + mResult.mCaptionTopPadding,
                    mDisplayController,
                    mRootTaskDisplayAreaOrganizer,
                    mContext,
                    mDesktopUserRepositories,
                    mSurfaceControlBuilderSupplier,
                    mSurfaceControlTransactionSupplier,
                    snapshotList,
                    onOpenInstanceListener,
                    /* onOutsideClickListener= */ () -> {
                        closeManageWindowsMenu();
                        return Unit.INSTANCE;
                    }
                    );
        } else {
            mManageWindowsMenu = new DesktopHandleManageWindowsMenu(
                    mTaskInfo,
                    mSplitScreenController,
                    getCaptionX(),
                    mResult.mCaptionWidth,
                    mWindowManagerWrapper,
                    mDesktopState,
                    mContext,
                    snapshotList,
                    onOpenInstanceListener,
                    /* onOutsideClickListener= */ () -> {
                        closeManageWindowsMenu();
                        return Unit.INSTANCE;
                    }
                    );
        }
    }

    void closeManageWindowsMenu() {
        if (mManageWindowsMenu != null) {
            mManageWindowsMenu.animateClose();
        }
        mManageWindowsMenu = null;
    }

    private void updateGenericLink() {
        final ComponentName baseActivity = mTaskInfo.baseActivity;
        if (baseActivity == null) {
            return;
        }

        final String genericLink =
                mGenericLinksParser.getGenericLink(baseActivity.getPackageName());
        mGenericLink = genericLink == null ? null : Uri.parse(genericLink);
    }

    /**
     * Close the handle menu window.
     */
    @VisibleForTesting
    void closeHandleMenu() {
        if (!isHandleMenuActive()) return;
        mWindowDecorViewHolder.onHandleMenuClosed();
        mHandleMenu.close();
        mHandleMenu = null;
        if (mDesktopState.canEnterDesktopMode() && isEducationOrHandleReportingEnabled()) {
            notifyCaptionStateChanged();
        }
    }

    @Override
    void releaseViews(WindowContainerTransaction wct) {
        closeHandleMenu();
        closeManageWindowsMenu();
        closeMaximizeMenu();
        super.releaseViews(wct);
    }

    /**
     * Close an open handle menu if input is outside of menu coordinates
     *
     * @param ev the tapped point to compare against
     */
    void closeHandleMenuIfNeeded(MotionEvent ev) {
        if (!isHandleMenuActive()) return;

        PointF inputPoint = offsetCaptionLocation(ev);

        // If this is called before open_menu_button's onClick, we don't want to close
        // the menu since it will just reopen in onClick.
        final boolean pointInOpenMenuButton = pointInView(
                mResult.mRootView.findViewById(R.id.open_menu_button),
                inputPoint.x,
                inputPoint.y);

        if (!mHandleMenu.isValidMenuInput(inputPoint) && !pointInOpenMenuButton) {
            closeHandleMenu();
        }
    }

    boolean isFocused() {
        return mHasGlobalFocus;
    }

    /**
     * Offset the coordinates of a {@link MotionEvent} to be in the same coordinate space as caption
     *
     * @param ev the {@link MotionEvent} to offset
     * @return the point of the input in local space
     */
    private PointF offsetCaptionLocation(MotionEvent ev) {
        final PointF result = new PointF(ev.getX(), ev.getY());
        final ActivityManager.RunningTaskInfo taskInfo =
                mTaskOrganizer.getRunningTaskInfo(mTaskInfo.taskId);
        if (taskInfo == null) return result;
        final Point positionInParent = taskInfo.positionInParent;
        result.offset(-positionInParent.x, -positionInParent.y);
        return result;
    }

    /**
     * Checks if motion event occurs in the caption handle area of a focused caption (the caption on
     * a task in fullscreen or in multi-windowing mode). This should be used in cases where
     * onTouchListener will not work (i.e. when caption is in status bar area).
     *
     * @param ev       the {@link MotionEvent} to check
     * @return {@code true} if event is inside caption handle view, {@code false} if not
     */
    boolean checkTouchEventInFocusedCaptionHandle(MotionEvent ev) {
        if (isHandleMenuActive() || !isAppHandle(mWindowDecorViewHolder)
                || DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
            return false;
        }
        // The status bar input layer can only receive input in handle coordinates to begin with,
        // so checking coordinates is unnecessary as input is always within handle bounds.
        if (isAppHandle(mWindowDecorViewHolder)
                && DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()
                && isCaptionVisible()) {
            return true;
        }

        return checkTouchEventInCaption(ev);
    }

    /**
     * Checks if touch event occurs in caption.
     *
     * @param ev       the {@link MotionEvent} to check
     * @return {@code true} if event is inside caption view, {@code false} if not
     */
    boolean checkTouchEventInCaption(MotionEvent ev) {
        final PointF inputPoint = offsetCaptionLocation(ev);
        return inputPoint.x >= mResult.mCaptionX
                && inputPoint.x <= mResult.mCaptionX + mResult.mCaptionWidth
                && inputPoint.y >= 0
                && inputPoint.y <= mResult.mCaptionHeight;
    }

    /**
     * Checks whether the touch event falls inside the customizable caption region.
     */
    boolean checkTouchEventInCustomizableRegion(MotionEvent ev) {
        return mResult.mCustomizableCaptionRegion.contains((int) ev.getRawX(), (int) ev.getRawY());
    }

    /**
     * Check a passed MotionEvent if it has occurred on any button related to this decor.
     * Note this should only be called when a regular onClick is not possible
     * (i.e. the button was clicked through status bar layer)
     *
     * @param ev the MotionEvent to compare
     */
    void checkTouchEvent(MotionEvent ev) {
        if (mResult.mRootView == null || DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) return;
        final View caption = mResult.mRootView.findViewById(R.id.desktop_mode_caption);
        final View handle = caption.findViewById(R.id.caption_handle);
        final boolean inHandle = !isHandleMenuActive()
                && checkTouchEventInFocusedCaptionHandle(ev);
        final int action = ev.getActionMasked();
        if (action == ACTION_UP && inHandle) {
            handle.performClick();
        }
        if (isHandleMenuActive()) {
            // If the whole handle menu can be touched directly, rely on FLAG_WATCH_OUTSIDE_TOUCH.
            // This is for the case that some of the handle menu is underneath the status bar.
            if (isAppHandle(mWindowDecorViewHolder)
                    && !DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) {
                mHandleMenu.checkMotionEvent(ev);
                closeHandleMenuIfNeeded(ev);
            }
        }
    }

    /**
     * Updates hover and pressed status of views in this decoration. Should only be called
     * when status cannot be updated normally (i.e. the button is hovered through status
     * bar layer).
     * @param ev the MotionEvent to compare against.
     */
    void updateHoverAndPressStatus(MotionEvent ev) {
        if (mResult.mRootView == null || DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) return;
        final View handle = mResult.mRootView.findViewById(R.id.caption_handle);
        final boolean inHandle = !isHandleMenuActive()
                && checkTouchEventInFocusedCaptionHandle(ev);
        final int action = ev.getActionMasked();
        // The comparison against ACTION_UP is needed for the cancel drag to desktop case.
        handle.setHovered(inHandle && action != ACTION_UP);
        // We want handle to remain pressed if the pointer moves outside of it during a drag.
        handle.setPressed((inHandle && action == ACTION_DOWN)
                || (handle.isPressed() && action != ACTION_UP && action != ACTION_CANCEL));
        if (isHandleMenuActive()) {
            mHandleMenu.checkMotionEvent(ev);
        }
    }

    /**
     * Indicates that an app handle drag has been interrupted, this can happen e.g. if we receive an
     * unknown transition during the drag-to-desktop transition.
     */
    void handleDragInterrupted() {
        if (mResult.mRootView == null) return;
        final View handle = mResult.mRootView.findViewById(R.id.caption_handle);
        handle.setHovered(false);
        handle.setPressed(false);
    }

    private boolean pointInView(View v, float x, float y) {
        return v != null && v.getLeft() <= x && v.getRight() >= x
                && v.getTop() <= y && v.getBottom() >= y;
    }

    /**
     * Returns true if caption state should be updated either due to education or app handle
     * reporting being enabled.
     */
    private boolean isEducationOrHandleReportingEnabled() {
        return Flags.enableDesktopWindowingAppHandleEducation()
                || Flags.enableDesktopWindowingAppToWebEducationIntegration()
                || DesktopExperienceFlags.ENABLE_APP_HANDLE_POSITION_REPORTING.isTrue();
    }

    @Override
    public void close() {
        if (mLoadAppInfoRunnable != null) {
            mBgExecutor.removeCallbacks(mLoadAppInfoRunnable);
        }
        if (mSetAppInfoRunnable != null) {
            mMainExecutor.removeCallbacks(mSetAppInfoRunnable);
        }
        mTaskResourceLoader.onWindowDecorClosed(mTaskInfo);
        closeDragResizeListener();
        closeHandleMenu();
        closeManageWindowsMenu();
        mExclusionRegionListener.onExclusionRegionDismissed(mTaskInfo.taskId);
        disposeResizeVeil();
        disposeStatusBarInputLayer();
        if (mWindowDecorViewHolder != null) {
            mWindowDecorViewHolder.close();
            mWindowDecorViewHolder = null;
        }
        if (mDesktopState.canEnterDesktopMode() && isEducationOrHandleReportingEnabled()) {
            notifyNoCaptionHandle();
        }
        super.close();
    }

    private static int getDesktopModeWindowDecorLayoutId(@WindowingMode int windowingMode) {
        return windowingMode == WINDOWING_MODE_FREEFORM
                ? R.layout.desktop_mode_app_header
                : R.layout.desktop_mode_app_handle;
    }

    private void updatePositionInParent() {
        mPositionInParent.set(mTaskInfo.positionInParent);
    }

    private void updateExclusionRegion(boolean inFullImmersive) {
        // An outdated position in parent is one reason for this to be called; update it here.
        updatePositionInParent();
        mExclusionRegionListener
                .onExclusionRegionChanged(mTaskInfo.taskId,
                        getGlobalExclusionRegion(inFullImmersive));
    }

    /**
     * Create a new exclusion region from the corner rects (if resizeable) and caption bounds
     * of this task.
     */
    private Region getGlobalExclusionRegion(boolean inFullImmersive) {
        Region exclusionRegion;
        if (mDragResizeListener != null
                && isDragResizable(mTaskInfo, inFullImmersive)) {
            exclusionRegion = mDragResizeListener.getCornersRegion();
        } else {
            exclusionRegion = new Region();
        }
        if (inFullImmersive) {
            // Task can't be moved in full immersive, so skip excluding the caption region.
            return exclusionRegion;
        }
        exclusionRegion.union(new Rect(0, 0, mResult.mWidth,
                getCaptionHeight(mTaskInfo.getWindowingMode())));
        exclusionRegion.translate(mPositionInParent.x, mPositionInParent.y);
        return exclusionRegion;
    }

    int getCaptionX() {
        return mResult.mCaptionX;
    }

    private static BiFunction<Context, Display, Integer> getCaptionHeightCalculator(
            @WindowingMode int windowingMode) {
        return (ctx, display) -> {
            if (windowingMode == WINDOWING_MODE_FULLSCREEN) {
                return SystemBarUtils.getStatusBarHeight(ctx.getResources(), display.getCutout());
            } else {
                return loadDimensionPixelSize(ctx.getResources(),
                        getDesktopViewAppHeaderHeightId());
            }
        };
    }

    @Override
    int getCaptionHeight(@WindowingMode int windowingMode) {
        return getCaptionHeightCalculator(windowingMode).apply(mContext, mDisplay);
    }

    @Override
    int getCaptionViewId() {
        return R.id.desktop_mode_caption;
    }

    void setAnimatingTaskResizeOrReposition(boolean animatingTaskResizeOrReposition) {
        if (!isAppHeader(mWindowDecorViewHolder)) return;
        final boolean inFullImmersive =
                mDesktopUserRepositories.getProfile(mTaskInfo.userId)
                        .isTaskInFullImmersiveState(mTaskInfo.taskId);
        asAppHeader(mWindowDecorViewHolder).bindData(new AppHeaderViewHolder.HeaderData(
                mTaskInfo,
                DesktopModeUtils.isTaskMaximized(mTaskInfo, mDisplayController),
                inFullImmersive,
                isFocused(),
                /* maximizeHoverEnabled= */ canOpenMaximizeMenu(animatingTaskResizeOrReposition),
                isCaptionVisible()));
    }

    /**
     * Announces that the app window is now being focused for accessibility. This is used after a
     * window is minimized/closed, and a new app window gains focus.
     */
    void a11yAnnounceNewFocusedWindow() {
        if (!isAppHeader(mWindowDecorViewHolder)) return;
        asAppHeader(mWindowDecorViewHolder).a11yAnnounceFocused();
    }

    /**
     * Declares whether a Recents transition is currently active.
     *
     * <p> When a Recents transition is active we allow that transition to take ownership of the
     * corner radius of its task surfaces, so each window decoration should stop updating the corner
     * radius of its task surface during that time.
     */
    void setIsRecentsTransitionRunning(boolean isRecentsTransitionRunning) {
        mIsRecentsTransitionRunning = isRecentsTransitionRunning;
        // TODO (b/415631133): Update this to call on #relayout once b/415631133 is fixed
        if (isAppHandle(mWindowDecorViewHolder)
                && DesktopModeFlags.ENABLE_INPUT_LAYER_TRANSITION_FIX.isTrue()) {
            updateAppHandleViewHolder();
        }
    }

    /**
     * Declares whether the window decoration is being dragged.
     */
    void setIsDragging(boolean isDragging) {
        mIsDragging = isDragging;
    }

    /**
     * Called when there is a {@link MotionEvent#ACTION_HOVER_EXIT} on the maximize window button.
     */
    void onMaximizeButtonHoverExit() {
        asAppHeader(mWindowDecorViewHolder).onMaximizeWindowHoverExit();
    }

    /**
     * Called when there is a {@link MotionEvent#ACTION_HOVER_ENTER} on the maximize window button.
     */
    void onMaximizeButtonHoverEnter() {
        asAppHeader(mWindowDecorViewHolder).onMaximizeWindowHoverEnter();
    }

    private boolean canOpenMaximizeMenu(boolean animatingTaskResizeOrReposition) {
        if (!DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue()) {
            return !animatingTaskResizeOrReposition;
        }
        final boolean inImmersiveAndRequesting =
                mDesktopUserRepositories.getProfile(mTaskInfo.userId)
                        .isTaskInFullImmersiveState(mTaskInfo.taskId)
                    && TaskInfoKt.getRequestingImmersive(mTaskInfo);
        return !animatingTaskResizeOrReposition && !inImmersiveAndRequesting;
    }

    @Override
    public String toString() {
        return "{"
                + "mPositionInParent=" + mPositionInParent + ", "
                + "taskId=" + mTaskInfo.taskId + ", "
                + "windowingMode=" + windowingModeToString(mTaskInfo.getWindowingMode()) + ", "
                + "isFocused=" + isFocused()
                + "}";
    }

    static class Factory {

        DesktopModeWindowDecoration create(
                Context context,
                @NonNull Context userContext,
                DisplayController displayController,
                @NonNull WindowDecorTaskResourceLoader appResourceProvider,
                SplitScreenController splitScreenController,
                DesktopUserRepositories desktopUserRepositories,
                ShellTaskOrganizer taskOrganizer,
                ActivityManager.RunningTaskInfo taskInfo,
                SurfaceControl taskSurface,
                @ShellMainThread Handler handler,
                @ShellMainThread ShellExecutor mainExecutor,
                @ShellMainThread MainCoroutineDispatcher mainDispatcher,
                @ShellBackgroundThread CoroutineScope bgScope,
                @ShellBackgroundThread ShellExecutor bgExecutor,
                Transitions transitions,
                Choreographer choreographer,
                SyncTransactionQueue syncQueue,
                AppHeaderViewHolder.Factory appHeaderViewHolderFactory,
                AppHandleViewHolder.Factory appHandleViewHolderFactory,
                RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
                AppToWebGenericLinksParser genericLinksParser,
                AssistContentRequester assistContentRequester,
                @NonNull WindowDecorViewHostSupplier<WindowDecorViewHost>
                        windowDecorViewHostSupplier,
                MultiInstanceHelper multiInstanceHelper,
                WindowDecorCaptionRepository windowDecorCaptionRepository,
                DesktopModeEventLogger desktopModeEventLogger,
                DesktopModeUiEventLogger desktopModeUiEventLogger,
                DesktopModeCompatPolicy desktopModeCompatPolicy,
                DesktopState desktopState,
                DesktopConfig desktopConfig,
                WindowDecorationActions windowDecorationActions) {
            return new DesktopModeWindowDecoration(
                    context,
                    userContext,
                    displayController,
                    appResourceProvider,
                    splitScreenController,
                    desktopUserRepositories,
                    taskOrganizer,
                    taskInfo,
                    taskSurface,
                    handler,
                    mainExecutor,
                    mainDispatcher,
                    bgScope,
                    bgExecutor,
                    transitions,
                    choreographer,
                    syncQueue,
                    appHeaderViewHolderFactory,
                    appHandleViewHolderFactory,
                    rootTaskDisplayAreaOrganizer,
                    genericLinksParser,
                    assistContentRequester,
                    windowDecorViewHostSupplier,
                    multiInstanceHelper,
                    windowDecorCaptionRepository,
                    desktopModeEventLogger,
                    desktopModeUiEventLogger,
                    desktopModeCompatPolicy,
                    desktopState,
                    desktopConfig,
                    windowDecorationActions);
        }
    }

    @VisibleForTesting
    static class CapturedLink {
        private final long mTimeStamp;
        private final Uri mUri;
        private boolean mUsed;

        CapturedLink(@NonNull Uri uri, long timeStamp) {
            mUri = uri;
            mTimeStamp = timeStamp;
        }

        private void setUsed() {
            mUsed = true;
        }
    }

    interface ExclusionRegionListener {
        /** Inform the implementing class of this task's change in region resize handles */
        void onExclusionRegionChanged(int taskId, Region region);

        /**
         * Inform the implementing class that this task no longer needs an exclusion region,
         * likely due to it closing.
         */
        void onExclusionRegionDismissed(int taskId);
    }
}
