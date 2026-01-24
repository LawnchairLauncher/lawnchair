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

package com.android.wm.shell.bubbles;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.service.notification.NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_DELETED;
import static android.service.notification.NotificationListenerService.NOTIFICATION_CHANNEL_OR_GROUP_UPDATED;
import static android.service.notification.NotificationListenerService.REASON_CANCEL;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_BUBBLES;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_BLOCKED;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_GROUP_CANCELLED;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_INVALID_INTENT;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_NOTIF_CANCEL;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_NO_BUBBLE_UP;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_NO_LONGER_BUBBLE;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_PACKAGE_REMOVED;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_SHORTCUT_REMOVED;
import static com.android.wm.shell.bubbles.Bubbles.DISMISS_USER_CHANGED;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY;
import static com.android.wm.shell.transition.Transitions.TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR;

import android.annotation.BinderThread;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.view.IWindowManager;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.window.IMultitaskingController;
import android.window.IMultitaskingControllerCallback;
import android.window.ScreenCapture;
import android.window.ScreenCapture.SynchronousScreenCaptureListener;
import android.window.TaskOrganizer;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.CollectionUtils;
import com.android.launcher3.icons.BubbleIconFactory;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfoProvider;
import com.android.wm.shell.bubbles.bar.BubbleBarLayerView;
import com.android.wm.shell.bubbles.shortcut.BubbleShortcutHelper;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.HomeIntentProvider;
import com.android.wm.shell.common.ImeListener;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.draganddrop.DragAndDropController;
import com.android.wm.shell.onehanded.OneHandedController;
import com.android.wm.shell.onehanded.OneHandedTransitionCallback;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation.UpdateSource;
import com.android.wm.shell.shared.bubbles.BubbleBarUpdate;
import com.android.wm.shell.shared.bubbles.BubbleDropTargetBoundsProvider;
import com.android.wm.shell.shared.bubbles.ContextUtils;
import com.android.wm.shell.shared.bubbles.DeviceConfig;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskView;
import com.android.wm.shell.taskview.TaskViewController;
import com.android.wm.shell.taskview.TaskViewTaskController;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.Transitions;

import dagger.Lazy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Bubbles are a special type of content that can "float" on top of other apps or System UI.
 * Bubbles can be expanded to show more content.
 *
 * The controller manages addition, removal, and visible state of bubbles on screen.
 */
public class BubbleController implements ConfigurationChangeListener,
        RemoteCallable<BubbleController>, Bubbles.SysuiProxy.Provider,
        BubbleTaskUnfoldTransitionMerger {

    private static final String TAG = TAG_WITH_CLASS_NAME ? "BubbleController" : TAG_BUBBLES;

    // Should match with PhoneWindowManager
    private static final String SYSTEM_DIALOG_REASON_KEY = "reason";
    private static final String SYSTEM_DIALOG_REASON_GESTURE_NAV = "gestureNav";
    private static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
    private static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";

    /**
     * Common interface to send updates to bubble views.
     */
    public interface BubbleViewCallback {
        /** Called when the provided bubble should be removed. */
        void removeBubble(Bubble removedBubble);
        /** Called when the provided bubble should be added. */
        void addBubble(Bubble addedBubble);
        /** Called when the provided bubble should be updated. */
        void updateBubble(Bubble updatedBubble);
        /** Called when the provided bubble should be selected. */
        void selectionChanged(BubbleViewProvider selectedBubble);
        /** Called when the provided bubble's suppression state has changed. */
        void suppressionChanged(Bubble bubble, boolean isSuppressed);
        /** Called when the expansion state of bubbles has changed. */
        void expansionChanged(boolean isExpanded);
        /**
         * Called when the order of the bubble list has changed. Depending on the expanded state
         * the pointer might need to be updated.
         */
        void bubbleOrderChanged(List<Bubble> bubbleOrder, boolean updatePointer);
        /** Called when the bubble overflow empty state changes, used to show/hide the overflow. */
        void bubbleOverflowChanged(boolean hasBubbles);
    }

    private final Context mContext;
    private final BubblesImpl mImpl = new BubblesImpl();
    private Bubbles.BubbleExpandListener mExpandListener;
    @Nullable private final BubbleStackView.SurfaceSynchronizer mSurfaceSynchronizer;
    private final FloatingContentCoordinator mFloatingContentCoordinator;
    private final BubbleDataRepository mDataRepository;
    private final DisplayInsetsController mDisplayInsetsController;
    private final DisplayImeController mDisplayImeController;
    private final UserManager mUserManager;
    private final LauncherApps mLauncherApps;
    private final IStatusBarService mBarService;
    private final WindowManager mWindowManager;
    private final TaskStackListenerImpl mTaskStackListener;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final DisplayController mDisplayController;
    private final TaskViewController mTaskViewController;
    private final Transitions mTransitions;
    private final ShellController mShellController;
    private final ShellCommandHandler mShellCommandHandler;
    private final IWindowManager mWmService;
    private final BubbleTaskViewFactory mBubbleTaskViewFactory;
    private final BubbleExpandedViewManager mExpandedViewManager;
    private final ResizabilityChecker mResizabilityChecker;
    private final HomeIntentProvider mHomeIntentProvider;
    private final BubbleAppInfoProvider mAppInfoProvider;
    private final Lazy<Optional<SplitScreenController>> mSplitScreenController;

    // Used to post to main UI thread
    private final ShellExecutor mMainExecutor;
    private final Handler mMainHandler;
    private final ShellExecutor mBackgroundExecutor;

    private final BubbleLogger mLogger;
    private final BubbleData mBubbleData;
    @Nullable private BubbleStackView mStackView;
    @Nullable private BubbleBarLayerView mLayerView;
    @Nullable private ActivityManager.RunningTaskInfo mAppBubbleRootTaskInfo;
    private BubbleIconFactory mBubbleIconFactory;
    private final BubblePositioner mBubblePositioner;
    private Bubbles.SysuiProxy mSysuiProxy;

    @Nullable private Runnable mOnImeHidden;

    // Tracks the id of the current (foreground) user.
    private int mCurrentUserId;
    // Current profiles of the user (e.g. user with a workprofile)
    private SparseArray<UserInfo> mCurrentProfiles;
    // Saves data about active bubbles when users are switched.
    private final SparseArray<UserBubbleData> mSavedUserBubbleData;

    // Used when ranking updates occur and we check if things should bubble / unbubble
    private NotificationListenerService.Ranking mTmpRanking;

    // Callback that updates BubbleOverflowActivity on data change.
    @Nullable private BubbleData.Listener mOverflowListener = null;

    // Typically only load once & after user switches
    private boolean mOverflowDataLoadNeeded = true;

    /**
     * When the shade status changes to SHADE (from anything but SHADE, like LOCKED) we'll select
     * this bubble and expand the stack.
     */
    @Nullable private BubbleEntry mNotifEntryToExpandOnShadeUnlock;

    /** LayoutParams used to add the BubbleStackView to the window manager. */
    private WindowManager.LayoutParams mWmLayoutParams;
    /** Whether or not the BubbleStackView has been added to the WindowManager. */
    private boolean mAddedToWindowManager = false;

    /**
     * Saved screen density, used to detect display size changes in {@link #onConfigurationChanged}.
     */
    private int mDensityDpi = Configuration.DENSITY_DPI_UNDEFINED;

    /**
     * Saved screen bounds, used to detect screen size changes in {@link #onConfigurationChanged}.
     */
    private final Rect mScreenBounds = new Rect();

    /** Saved font scale, used to detect font size changes in {@link #onConfigurationChanged}. */
    private float mFontScale = 0;

    /** Saved locale, used to detect local changes in {@link #onConfigurationChanged}. */
    private Locale mLocale = null;

    /** Saved direction, used to detect layout direction changes @link #onConfigChanged}. */
    private int mLayoutDirection = View.LAYOUT_DIRECTION_UNDEFINED;

    /** Saved insets, used to detect WindowInset changes. */
    private WindowInsets mWindowInsets;

    private boolean mInflateSynchronously;

    /** True when user is in status bar unlock shade. */
    private boolean mIsStatusBarShade = true;

    /** One handed mode controller to register transition listener. */
    private final Optional<OneHandedController> mOneHandedOptional;
    /** Drag and drop controller to register listener for onDragStarted. */
    private final DragAndDropController mDragAndDropController;
    /**
     * Used to send bubble events to launcher.
     * Set when taskbar is created in launcher and bubble bar gets initialized.
     * Can be cleared during Launcher lifecycle changes, for example when taskbar gets recreated
     * during rotation.
     */
    @Nullable
    private Bubbles.BubbleStateListener mBubbleStateListener;
    /** True when launcher can show the bubble bar. */
    private boolean mLauncherHasBubbleBar;
    /**
     * Used to track previous navigation mode to detect switch to buttons navigation. Set to
     * true to switch the bubble bar to the opposite side for 3 nav buttons mode on device boot.
     */
    private boolean mIsPrevNavModeGestures = true;
    /** Used to send updates to the views from {@link #mBubbleDataListener}. */
    private BubbleViewCallback mBubbleViewCallback;

    private final BubbleTransitions mBubbleTransitions;

    // Experimental listener for app requests for bubble actions.
    private BubbleMultitaskingDelegate mBubbleMultitaskingDelegate;

    public BubbleController(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            BubbleData data,
            @Nullable BubbleStackView.SurfaceSynchronizer synchronizer,
            FloatingContentCoordinator floatingContentCoordinator,
            BubbleDataRepository dataRepository,
            BubbleTransitions bubbleTransitions,
            @Nullable IStatusBarService statusBarService,
            WindowManager windowManager,
            DisplayInsetsController displayInsetsController,
            DisplayImeController displayImeController,
            UserManager userManager,
            LauncherApps launcherApps,
            BubbleLogger bubbleLogger,
            TaskStackListenerImpl taskStackListener,
            ShellTaskOrganizer organizer,
            BubblePositioner positioner,
            DisplayController displayController,
            Optional<OneHandedController> oneHandedOptional,
            DragAndDropController dragAndDropController,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellMainThread Handler mainHandler,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            @NonNull TaskViewTransitions taskViewTransitions,
            Transitions transitions,
            SyncTransactionQueue syncQueue,
            IWindowManager wmService,
            ResizabilityChecker resizabilityChecker,
            HomeIntentProvider homeIntentProvider,
            BubbleAppInfoProvider appInfoProvider,
            Lazy<Optional<SplitScreenController>> splitScreenController) {
        mContext = context;
        mShellCommandHandler = shellCommandHandler;
        mShellController = shellController;
        mLauncherApps = launcherApps;
        mBarService = statusBarService == null
                ? IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE))
                : statusBarService;
        mWindowManager = windowManager;
        mDisplayInsetsController = displayInsetsController;
        mDisplayImeController = displayImeController;
        mUserManager = userManager;
        mFloatingContentCoordinator = floatingContentCoordinator;
        mDataRepository = dataRepository;
        mLogger = bubbleLogger;
        mMainExecutor = mainExecutor;
        mMainHandler = mainHandler;
        mBackgroundExecutor = bgExecutor;
        mTaskStackListener = taskStackListener;
        mTaskOrganizer = organizer;
        mSurfaceSynchronizer = synchronizer;
        mCurrentUserId = ActivityManager.getCurrentUser();
        mBubblePositioner = positioner;
        mBubbleData = data;
        mSavedUserBubbleData = new SparseArray<>();
        mBubbleIconFactory = new BubbleIconFactory(context,
                context.getResources().getDimensionPixelSize(R.dimen.bubble_size),
                context.getResources().getDimensionPixelSize(R.dimen.bubble_badge_size),
                context.getResources().getColor(
                        com.android.launcher3.icons.R.color.important_conversation),
                context.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.importance_ring_stroke_width));
        mDisplayController = displayController;
        mTaskViewController = new BubbleTaskViewController(taskViewTransitions);
        mTransitions = transitions;
        mOneHandedOptional = oneHandedOptional;
        mDragAndDropController = dragAndDropController;
        mWmService = wmService;
        mBubbleTransitions = bubbleTransitions;
        mBubbleTransitions.setBubbleController(this);
        mBubbleTaskViewFactory = new BubbleTaskViewFactory() {
            @Override
            public BubbleTaskView create() {
                TaskViewTaskController taskViewTaskController = new TaskViewTaskController(
                        context, organizer, mTaskViewController, syncQueue);
                TaskView taskView = new TaskView(context, mTaskViewController,
                        taskViewTaskController);
                return new BubbleTaskView(taskView, mainExecutor);
            }
        };
        mExpandedViewManager = BubbleExpandedViewManager.fromBubbleController(this);
        mResizabilityChecker = resizabilityChecker;
        mHomeIntentProvider = homeIntentProvider;
        mAppInfoProvider = appInfoProvider;
        mSplitScreenController = splitScreenController;
        shellInit.addInitCallback(this::onInit, this);
    }

    private void registerOneHandedState(OneHandedController oneHanded) {
        oneHanded.registerTransitionCallback(
                new OneHandedTransitionCallback() {
                    @Override
                    public void onStartFinished(Rect bounds) {
                        mMainExecutor.execute(() -> {
                            if (mStackView != null) {
                                mStackView.onVerticalOffsetChanged(bounds.top);
                            }
                        });
                    }

                    @Override
                    public void onStopFinished(Rect bounds) {
                        mMainExecutor.execute(() -> {
                            if (mStackView != null) {
                                mStackView.onVerticalOffsetChanged(bounds.top);
                            }
                        });
                    }
                });
    }

    protected void onInit() {
        mBubbleViewCallback = isShowingAsBubbleBar()
                ? mBubbleBarViewCallback
                : mBubbleStackViewCallback;
        mBubbleData.setListener(mBubbleDataListener);
        mBubbleData.setSuppressionChangedListener(this::onBubbleMetadataFlagChanged);
        mDataRepository.setSuppressionChangedListener(this::onBubbleMetadataFlagChanged);
        mBubbleData.setPendingIntentCancelledListener(bubble -> {
            if (bubble.getPendingIntent() == null) {
                return;
            }
            if (bubble.isPendingIntentActive()
                    || mBubbleData.hasBubbleInStackWithKey(bubble.getKey())) {
                bubble.setPendingIntentCanceled();
                return;
            }
            mMainExecutor.execute(() -> removeBubble(bubble.getKey(), DISMISS_INVALID_INTENT));
        });

        BubblesImeListener bubblesImeListener =
                new BubblesImeListener(mDisplayController, mContext.getDisplayId());
        // the insets controller is notified whenever the IME visibility changes whether the IME is
        // requested by a bubbled task or non-bubbled task. in the latter case, we need to update
        // the position of the stack to avoid overlapping with the IME.
        mDisplayInsetsController.addInsetsChangedListener(mContext.getDisplayId(),
                bubblesImeListener);
        // the ime controller is notified when the IME is requested only by a bubbled task.
        mDisplayImeController.addPositionProcessor(bubblesImeListener);

        mBubbleData.setCurrentUserId(mCurrentUserId);

        mTaskOrganizer.addLocusIdListener((taskId, locus, visible) ->
                mBubbleData.onLocusVisibilityChanged(taskId, locus, visible));

        mLauncherApps.registerCallback(new LauncherApps.Callback() {
            @Override
            public void onPackageAdded(String s, UserHandle userHandle) {}

            @Override
            public void onPackageChanged(String s, UserHandle userHandle) {}

            @Override
            public void onPackageRemoved(String s, UserHandle userHandle) {
                // Remove bubbles with this package name, since it has been uninstalled and attempts
                // to open a bubble from an uninstalled app can cause issues.
                mBubbleData.removeBubblesWithPackageName(s, DISMISS_PACKAGE_REMOVED);
            }

            @Override
            public void onPackagesAvailable(String[] strings, UserHandle userHandle, boolean b) {}

            @Override
            public void onPackagesUnavailable(String[] packages, UserHandle userHandle,
                    boolean b) {
                for (String packageName : packages) {
                    // Remove bubbles from unavailable apps. This can occur when the app is on
                    // external storage that has been removed.
                    mBubbleData.removeBubblesWithPackageName(packageName, DISMISS_PACKAGE_REMOVED);
                }
            }

            @Override
            public void onShortcutsChanged(String packageName, List<ShortcutInfo> validShortcuts,
                    UserHandle user) {
                super.onShortcutsChanged(packageName, validShortcuts, user);

                // Remove bubbles whose shortcuts aren't in the latest list of valid shortcuts.
                mBubbleData.removeBubblesWithInvalidShortcuts(
                        packageName, validShortcuts, DISMISS_SHORTCUT_REMOVED);
            }
        }, mMainHandler);

        mTransitions.registerObserver(new BubblesTransitionObserver(this, mBubbleData,
                mBubbleTransitions.mTaskViewTransitions, mSplitScreenController));

        mTaskStackListener.addListener(
                new BubbleTaskStackListener(this, mBubbleData, mSplitScreenController));

        mDisplayController.addDisplayChangingController(
                (displayId, fromRotation, toRotation, newDisplayAreaInfo, t) -> {
                    Rect newScreenBounds = new Rect();
                    if (newDisplayAreaInfo != null) {
                        newScreenBounds =
                                newDisplayAreaInfo.configuration.windowConfiguration.getBounds();
                    }
                    // This is triggered right before the rotation or new screen size is applied
                    if (fromRotation != toRotation || !newScreenBounds.equals(mScreenBounds)) {
                        if (mStackView != null) {
                            // Layout listener set on stackView will update the positioner
                            // once the rotation or screen change is applied
                            mStackView.onOrientationChanged();
                        }
                    }
                });

        mOneHandedOptional.ifPresent(this::registerOneHandedState);
        mDragAndDropController.addListener(new DragAndDropController.DragAndDropListener() {
            @Override
            public void onDragStarted() {
                collapseStack();
            }
        });

        // Clear out any persisted bubbles on disk that no longer have a valid user.
        List<UserInfo> users = mUserManager.getAliveUsers();
        mDataRepository.sanitizeBubbles(users);

        // Init profiles
        SparseArray<UserInfo> userProfiles = new SparseArray<>();
        for (UserInfo user : mUserManager.getProfiles(mCurrentUserId)) {
            userProfiles.put(user.id, user);
        }
        mCurrentProfiles = userProfiles;

        if (Flags.enableRetrievableBubbles()) {
            registerShortcutBroadcastReceiver();
        }

        mShellController.addConfigurationChangeListener(this);
        mShellController.addExternalInterface(IBubbles.DESCRIPTOR,
                this::createExternalInterface, this);
        mShellCommandHandler.addDumpCallback(this::dump, this);

        if (com.android.window.flags2.Flags.enableExperimentalBubblesController()) {
            try {
                final BubbleMultitaskingDelegate delegate = new BubbleMultitaskingDelegate(
                        this, mBubbleData, mCurrentUserId);
                final IMultitaskingController mtController = ActivityTaskManager.getService()
                        .getWindowOrganizerController().getMultitaskingController();
                final IMultitaskingControllerCallback callback =
                        mtController.setMultitaskingDelegate(delegate);
                mBubbleMultitaskingDelegate = delegate;
                mBubbleMultitaskingDelegate.setControllerCallback(callback);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to register Bubble multitasking delegate.", e);
            }
        }

        if (BubbleAnythingFlagHelper.enableRootTaskForBubble()) {
            // Create a root-task in WM Core. The app bubble tasks will be positioned as the leaf
            // tasks under this root-task.
            // The app bubble should be dismissed with proper transition (such as need to convert
            // it to fullscreen) if the bubble task is no longer be a leaf task under this leaf
            // task.
            mTaskOrganizer.createRootTask(
                    new TaskOrganizer.CreateRootTaskRequest()
                            .setName("Bubbles")
                            .setDisplayId(mContext.getDisplayId())
                            .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW),
                    new ShellTaskOrganizer.TaskListener() {
                        @Override
                        public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                                SurfaceControl leash) {
                            if (mAppBubbleRootTaskInfo != null) {
                                return;
                            }
                            mAppBubbleRootTaskInfo = taskInfo;
                        }
                    });
        }
    }

    private ExternalInterfaceBinder createExternalInterface() {
        return new IBubblesImpl(this);
    }

    @VisibleForTesting
    public Bubbles asBubbles() {
        return mImpl;
    }

    @VisibleForTesting
    public BubblesImpl.CachedState getImplCachedState() {
        return mImpl.mCachedState;
    }

    public ShellExecutor getMainExecutor() {
        return mMainExecutor;
    }

    ShellExecutor getBackgroundExecutor() {
        return mBackgroundExecutor;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mMainExecutor;
    }

    /**
     * Sets a listener to be notified of bubble updates. This is used by launcher so that
     * it may render bubbles in itself. Only one listener is supported.
     */
    @VisibleForTesting
    public void registerBubbleStateListener(Bubbles.BubbleStateListener listener) {
        final boolean bubbleBarAllowed = Flags.enableBubbleBar()
                && (mBubblePositioner.isLargeScreen() || Flags.enableBubbleBarOnPhones())
                && listener != null;
        if (bubbleBarAllowed) {
            // Only set the listener if we can show the bubble bar.
            mBubbleStateListener = listener;
            if (mLauncherHasBubbleBar) {
                // Launcher is ready, send initial listener update.
                sendInitialListenerUpdate();
            }
        } else {
            mBubbleStateListener = null;
        }
    }

    /**
     * Unregisters the {@link Bubbles.BubbleStateListener}.
     */
    @VisibleForTesting
    public void unregisterBubbleStateListener() {
        mBubbleStateListener = null;
    }

    /**
     * Store whether Launcher can show bubbles in Bubble Bar.
     * <p>
     * If {@code launcherHasBubbleBar} is set to {@code true}, bubble views will be updated to
     * switch to bar mode. If it is set to {@code false}, bubble views will be updated to switch
     * to stack mode.
     */
    @VisibleForTesting
    public void setLauncherHasBubbleBar(boolean launcherHasBubbleBar) {
        if (launcherHasBubbleBar == mLauncherHasBubbleBar) return;
        mLauncherHasBubbleBar = launcherHasBubbleBar;
        if (mLauncherHasBubbleBar) {
            setUpBubbleViewsForMode();
            if (mBubbleStateListener != null) {
                // Listener got set first, send it an initial update.
                sendInitialListenerUpdate();
            }
        } else {
            unregisterBubbleStateListener();
            setUpBubbleViewsForMode();
        }
    }

    /**
     * If a {@link Bubbles.BubbleStateListener} is present, this will send the current bubble
     * state to it.
     */
    private void sendInitialListenerUpdate() {
        if (mBubbleStateListener != null) {
            boolean isCurrentNavModeGestures = ContextUtils.isGestureNavigationMode(mContext);
            if (mIsPrevNavModeGestures && !isCurrentNavModeGestures) {
                BubbleBarLocation bubbleBarLocation = ContextUtils.isRtl(mContext)
                        ? BubbleBarLocation.RIGHT : BubbleBarLocation.LEFT;
                mBubblePositioner.setBubbleBarLocation(bubbleBarLocation);
            }
            mIsPrevNavModeGestures = isCurrentNavModeGestures;
            BubbleBarUpdate update = mBubbleData.getInitialStateForBubbleBar();
            mBubbleStateListener.onBubbleStateChange(update);
        }
    }

    /**
     * Hides the current input method, wherever it may be focused, via InputMethodManagerInternal.
     */
    void hideCurrentInputMethod(@Nullable Runnable onImeHidden) {
        mOnImeHidden = onImeHidden;
        mBubblePositioner.setImeVisible(false /* visible */, 0 /* height */);
        int displayId = mWindowManager.getDefaultDisplay().getDisplayId();
        // if the device is locked we can't use the status bar service to hide the IME because
        // the IME state is frozen and it will lead to internal IME state going out of sync. This
        // will make the IME visible when the device is unlocked. Instead we use
        // DisplayImeController directly to make sure the state is correct when the device unlocks.
        if (isDeviceLocked()) {
            mDisplayImeController.hideImeForBubblesWhenLocked(displayId);
            return;
        }
        try {
            mBarService.hideCurrentInputMethodForBubbles(displayId);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to hide IME", e);
        }
    }

    /**
     * Allows callers to reset runnable scheduled to run after IME is hidden by
     * {@link #hideCurrentInputMethod(Runnable)}
     */
    void clearImeHiddenRunnable() {
        mOnImeHidden = null;
    }

    /**
     * Called when the status bar has become visible or invisible (either permanently or
     * temporarily).
     */
    private void onStatusBarVisibilityChanged(boolean visible) {
        if (mStackView != null) {
            // Hide the stack temporarily if the status bar has been made invisible, and the stack
            // is collapsed. An expanded stack should remain visible until collapsed.
            mStackView.setTemporarilyInvisible(!visible && !isStackExpanded());
            ProtoLog.d(WM_SHELL_BUBBLES, "onStatusBarVisibilityChanged=%b stackExpanded=%b",
                    visible, isStackExpanded());
        }
    }

    private void onZenStateChanged() {
        if (hasBubbles()) {
            ProtoLog.d(WM_SHELL_BUBBLES, "onZenStateChanged");
        }
        for (Bubble b : mBubbleData.getBubbles()) {
            b.setShowDot(b.showInShade());
        }
    }

    @VisibleForTesting
    public void onStatusBarStateChanged(boolean isShade) {
        boolean didChange = mIsStatusBarShade != isShade;
        ProtoLog.d(WM_SHELL_BUBBLES, "onStatusBarStateChanged "
                        + "isShade=%b didChange=%b mNotifEntryToExpandOnShadeUnlock=%s",
                isShade, didChange, (mNotifEntryToExpandOnShadeUnlock != null
                        ? mNotifEntryToExpandOnShadeUnlock.getKey() : "null"));
        mIsStatusBarShade = isShade;
        if (!mIsStatusBarShade && didChange) {
            if (mBubbleData.isExpanded()) {
                // If the IME is visible, hide it first and then collapse.
                if (mBubblePositioner.isImeVisible()) {
                    hideCurrentInputMethod(this::collapseStack);
                } else {
                    collapseStack();
                }
            } else if (mOnImeHidden != null) {
                // a request to collapse started before we're notified that the device is locking.
                // we're currently waiting for the IME to collapse, before mOnImeHidden can be
                // executed, which may not happen since the screen may already be off. hide the IME
                // immediately now that we're locked and pass the same runnable so it can complete.
                hideCurrentInputMethod(mOnImeHidden);
            }
        }

        if (mNotifEntryToExpandOnShadeUnlock != null) {
            expandStackAndSelectBubble(mNotifEntryToExpandOnShadeUnlock);
        }

        updateBubbleViews();
    }

    @VisibleForTesting
    public void onBubbleMetadataFlagChanged(Bubble bubble) {
        ProtoLog.d(WM_SHELL_BUBBLES, "onBubbleMetadataFlagChanged=%s flags=%d",
                bubble.getKey(), bubble.getFlags());
        // Make sure NoMan knows suppression state so that anyone querying it can tell.
        try {
            mBarService.onBubbleMetadataFlagChanged(bubble.getKey(), bubble.getFlags());
        } catch (RemoteException e) {
            // Bad things have happened
        }
        mImpl.mCachedState.updateBubbleSuppressedState(bubble);
    }

    /** Called when the current user changes. */
    @VisibleForTesting
    public void onUserChanged(int newUserId) {
        ProtoLog.d(WM_SHELL_BUBBLES, "onUserChanged currentUser=%d newUser=%d",
                mCurrentUserId, newUserId);
        saveBubbles(mCurrentUserId);
        mCurrentUserId = newUserId;

        mBubbleData.dismissAll(DISMISS_USER_CHANGED);
        mBubbleData.clearOverflow();
        mOverflowDataLoadNeeded = true;

        restoreBubbles(newUserId);
        mBubbleData.setCurrentUserId(newUserId);
        if (mBubbleMultitaskingDelegate != null) {
            mBubbleMultitaskingDelegate.setCurrentUserId(newUserId);
        }
    }

    /** Called when the profiles for the current user change. **/
    public void onCurrentProfilesChanged(SparseArray<UserInfo> currentProfiles) {
        mCurrentProfiles = currentProfiles;
    }

    /** Called when a user is removed from the device, including work profiles. */
    public void onUserRemoved(int removedUserId) {
        UserInfo parent = mUserManager.getProfileParent(removedUserId);
        int parentUserId = parent != null ? parent.getUserHandle().getIdentifier() : -1;
        mBubbleData.removeBubblesForUser(removedUserId);
        // Typically calls from BubbleData would remove bubbles from the DataRepository as well,
        // however, this gets complicated when users are removed (mCurrentUserId won't necessarily
        // be correct for this) so we update the repo directly.
        mDataRepository.removeBubblesForUser(removedUserId, parentUserId);
    }

    /** Called when sensitive notification state has changed */
    public void onSensitiveNotificationProtectionStateChanged(
            boolean sensitiveNotificationProtectionActive) {
        if (mStackView != null) {
            mStackView.onSensitiveNotificationProtectionStateChanged(
                    sensitiveNotificationProtectionActive);
            ProtoLog.d(WM_SHELL_BUBBLES, "onSensitiveNotificationProtectionStateChanged=%b",
                    sensitiveNotificationProtectionActive);
        }
    }

    /** Whether bubbles would be shown with the bubble bar UI. */
    public boolean isShowingAsBubbleBar() {
        return Flags.enableBubbleBar()
                && (mBubblePositioner.isLargeScreen() || Flags.enableBubbleBarOnPhones())
                && mLauncherHasBubbleBar;
    }

    /**
     * Returns current {@link BubbleBarLocation} if bubble bar is being used.
     * Otherwise returns <code>null</code>
     */
    @Nullable
    public BubbleBarLocation getBubbleBarLocation() {
        if (isShowingAsBubbleBar()) {
            return mBubblePositioner.getBubbleBarLocation();
        }
        return null;
    }

    /**
     * Update bubble bar location and trigger and update to listeners
     */
    public void setBubbleBarLocation(BubbleBarLocation bubbleBarLocation,
            @UpdateSource int source) {
        if (isShowingAsBubbleBar()) {
            updateExpandedViewForBubbleBarLocation(bubbleBarLocation, source);
            if (mBubbleStateListener != null) {
                BubbleBarUpdate bubbleBarUpdate = new BubbleBarUpdate();
                bubbleBarUpdate.bubbleBarLocation = bubbleBarLocation;
                mBubbleStateListener.onBubbleStateChange(bubbleBarUpdate);
            }
        }
    }

    private void updateExpandedViewForBubbleBarLocation(BubbleBarLocation bubbleBarLocation,
            @UpdateSource int source) {
        if (isShowingAsBubbleBar()) {
            BubbleBarLocation previousLocation = mBubblePositioner.getBubbleBarLocation();
            mBubblePositioner.setBubbleBarLocation(bubbleBarLocation);
            if (mLayerView != null && !mLayerView.isExpandedViewDragged()) {
                mLayerView.updateExpandedView();
            }
            logBubbleBarLocationIfChanged(bubbleBarLocation, previousLocation, source);
        }
    }

    private void logBubbleBarLocationIfChanged(BubbleBarLocation location,
            BubbleBarLocation previous,
            @UpdateSource int source) {
        if (mLayerView == null) {
            return;
        }
        boolean isRtl = mLayerView.isLayoutRtl();
        boolean wasLeft = previous.isOnLeft(isRtl);
        boolean onLeft = location.isOnLeft(isRtl);
        if (wasLeft == onLeft) {
            // No changes, skip logging
            return;
        }
        switch (source) {
            case UpdateSource.DRAG_BAR:
            case UpdateSource.A11Y_ACTION_BAR:
                mLogger.log(onLeft ? BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_BAR
                        : BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_BAR);
                break;
            case UpdateSource.DRAG_BUBBLE:
            case UpdateSource.A11Y_ACTION_BUBBLE:
                mLogger.log(onLeft ? BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_BUBBLE
                        : BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_BUBBLE);
                break;
            case UpdateSource.DRAG_EXP_VIEW:
            case UpdateSource.A11Y_ACTION_EXP_VIEW:
                // TODO(b/349845968): move logging from BubbleBarLayerView to here
                break;
            case UpdateSource.APP_ICON_DRAG:
                mLogger.log(onLeft ? BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_APP_ICON_DROP
                        : BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_APP_ICON_DROP);
                break;
            case UpdateSource.DRAG_TASK:
                mLogger.log(onLeft ? BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_TASK
                        : BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_TASK);
                break;
        }
    }

    /**
     * Animate bubble bar to the given location. The location change is transient. It does not
     * update the state of the bubble bar.
     * To update bubble bar pinned location, use
     * {@link #setBubbleBarLocation(BubbleBarLocation, int)}.
     */
    public void animateBubbleBarLocation(BubbleBarLocation bubbleBarLocation) {
        if (isShowingAsBubbleBar() && mBubbleStateListener != null) {
            mBubbleStateListener.animateBubbleBarLocation(bubbleBarLocation);
        }
    }

    /**
     * Show bubble bar pin view given location.
     */
    public void showBubbleBarPinAtLocation(@Nullable BubbleBarLocation bubbleBarLocation) {
        if (isShowingAsBubbleBar() && mBubbleStateListener != null) {
            mBubbleStateListener.showBubbleBarPillowAt(bubbleBarLocation);
        }
    }

    private void showBubbleBarExpandedViewDropTarget(BubbleBarLocation bubbleBarLocation) {
        ensureBubbleViewsAndWindowCreated();
        if (mLayerView != null) {
            mLayerView.showBubbleBarExtendedViewDropTarget(bubbleBarLocation);
        }
    }

    private void hideBubbleBarExpandedViewDropTarget() {
        if (mLayerView != null) {
            mLayerView.hideBubbleBarExpandedViewDropTarget();
        }
    }

    /** Whether this userId belongs to the current user. */
    private boolean isCurrentProfile(int userId) {
        return userId == UserHandle.USER_ALL
                || (mCurrentProfiles != null && mCurrentProfiles.get(userId) != null);
    }

    /**
     * Sets whether to perform inflation on the same thread as the caller. This method should only
     * be used in tests, not in production.
     */
    @VisibleForTesting
    public void setInflateSynchronously(boolean inflateSynchronously) {
        mInflateSynchronously = inflateSynchronously;
    }

    /** Set a listener to be notified of when overflow view update. */
    public void setOverflowListener(BubbleData.Listener listener) {
        mOverflowListener = listener;
    }

    /**
     * @return Bubbles for updating overflow.
     */
    List<Bubble> getOverflowBubbles() {
        return mBubbleData.getOverflowBubbles();
    }

    /** The task listener for events in bubble tasks. */
    public ShellTaskOrganizer getTaskOrganizer() {
        return mTaskOrganizer;
    }

    /** Contains information to help position things on the screen. */
    @VisibleForTesting
    public BubblePositioner getPositioner() {
        return mBubblePositioner;
    }

    /** Provides bounds for drag zone drop targets */
    public BubbleDropTargetBoundsProvider getBubbleDropTargetBoundsProvider() {
        return mBubblePositioner;
    }

    BubbleIconFactory getIconFactory() {
        return mBubbleIconFactory;
    }

    @Override
    public Bubbles.SysuiProxy getSysuiProxy() {
        return mSysuiProxy;
    }

    /**
     * The view and window for bubbles is lazily created by this method the first time a Bubble
     * is added. Depending on the device state, this method will:
     * - initialize a {@link BubbleStackView} and add it to window manager OR
     * - initialize a {@link com.android.wm.shell.bubbles.bar.BubbleBarLayerView} and adds
     *   it to window manager.
     */
    private void ensureBubbleViewsAndWindowCreated() {
        mBubblePositioner.setShowingInBubbleBar(isShowingAsBubbleBar());
        if (isShowingAsBubbleBar()) {
            // When we're showing in launcher / bubble bar is enabled, we don't have bubble stack
            // view, instead we just show the expanded bubble view as necessary. We still need a
            // window to show this in, but we use a separate code path.
            // TODO(b/273312602): consider foldables where we do need a stack view when folded
            if (mLayerView == null) {
                mLayerView = new BubbleBarLayerView(mContext, this, mBubbleData, mLogger);
                mLayerView.setUnBubbleConversationCallback(mSysuiProxy::onUnbubbleConversation);
            }
        } else {
            if (mStackView == null) {
                BubbleStackViewManager bubbleStackViewManager =
                        BubbleStackViewManager.fromBubbleController(this);
                mStackView = new BubbleStackView(
                        mContext, bubbleStackViewManager, mBubblePositioner, mBubbleData,
                        mSurfaceSynchronizer, mFloatingContentCoordinator, this, mMainExecutor);
                mStackView.onOrientationChanged();
                if (mExpandListener != null) {
                    mStackView.setExpandListener(mExpandListener);
                }
                mStackView.setUnbubbleConversationCallback(mSysuiProxy::onUnbubbleConversation);
            }
        }
        addToWindowManagerMaybe();
    }

    /** Adds the appropriate view to WindowManager if it's not already there. */
    private void addToWindowManagerMaybe() {
        // If already added, don't add it.
        if (mAddedToWindowManager) {
            return;
        }
        // If the appropriate view is null, don't add it.
        if (isShowingAsBubbleBar() && mLayerView == null) {
            return;
        } else if (!isShowingAsBubbleBar() && mStackView == null) {
            return;
        }

        mWmLayoutParams = new WindowManager.LayoutParams(
                // Fill the screen so we can use translation animations to position the bubble
                // views. We'll use touchable regions to ignore touches that are not on the bubbles
                // themselves.
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        mWmLayoutParams.setTrustedOverlay();
        mWmLayoutParams.setFitInsetsTypes(0);
        mWmLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mWmLayoutParams.token = new Binder();
        mWmLayoutParams.setTitle("Bubbles!");
        mWmLayoutParams.packageName = mContext.getPackageName();
        mWmLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWmLayoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;

        try {
            mAddedToWindowManager = true;
            registerBroadcastReceiver();
            if (isShowingAsBubbleBar()) {
                mBubbleData.getOverflow().initializeForBubbleBar(
                        mExpandedViewManager, mBubblePositioner);
            } else {
                mBubbleData.getOverflow().initialize(
                        mExpandedViewManager, mStackView, mBubblePositioner);
            }
            // (TODO: b/273314541) some duplication in the inset listener
            if (isShowingAsBubbleBar()) {
                mWindowManager.addView(mLayerView, mWmLayoutParams);
                mLayerView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                    if (!windowInsets.equals(mWindowInsets) && mLayerView != null) {
                        mWindowInsets = windowInsets;
                        mBubblePositioner.update(DeviceConfig.create(mContext, mWindowManager));
                        mLayerView.onDisplaySizeChanged();
                    }
                    return windowInsets;
                });
            } else {
                mWindowManager.addView(mStackView, mWmLayoutParams);
                mStackView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                    if (!windowInsets.equals(mWindowInsets) && mStackView != null) {
                        mWindowInsets = windowInsets;
                        mBubblePositioner.update(DeviceConfig.create(mContext, mWindowManager));
                        mStackView.onDisplaySizeChanged();
                    }
                    return windowInsets;
                });
            }
        } catch (IllegalStateException e) {
            // This means the view has already been added. This shouldn't happen...
            e.printStackTrace();
        }
    }

    /**
     * In some situations bubble's should be able to receive key events for back:
     * - when the bubble overflow is showing
     * - when the user education for the stack is showing.
     *
     * @param interceptBack whether back should be intercepted or not.
     */
    void updateWindowFlagsForBackpress(boolean interceptBack) {
        if (mAddedToWindowManager) {
            ProtoLog.d(WM_SHELL_BUBBLES, "updateFlagsForBackPress interceptBack=%b", interceptBack);
            mWmLayoutParams.flags = interceptBack
                    ? 0
                    : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            mWmLayoutParams.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            if (mStackView != null) {
                mWindowManager.updateViewLayout(mStackView, mWmLayoutParams);
            } else if (mLayerView != null) {
                mWindowManager.updateViewLayout(mLayerView, mWmLayoutParams);
            }
        }
    }

    /** Removes any bubble views from the WindowManager that exist. */
    private void removeFromWindowManagerMaybe() {
        if (!mAddedToWindowManager) {
            return;
        }

        mAddedToWindowManager = false;
        // Put on background for this binder call, was causing jank
        mBackgroundExecutor.execute(() -> {
            try {
                mContext.unregisterReceiver(mBroadcastReceiver);
            } catch (IllegalArgumentException e) {
                // Not sure if this happens in production, but was happening in tests
                // (b/253647225)
                e.printStackTrace();
            }
        });
        try {
            if (mStackView != null) {
                mWindowManager.removeView(mStackView);
                mBubbleData.getOverflow().cleanUpExpandedState();
            }
            if (mLayerView != null) {
                mWindowManager.removeView(mLayerView);
                mBubbleData.getOverflow().cleanUpExpandedState();
            }
        } catch (IllegalArgumentException e) {
            // This means the stack has already been removed - it shouldn't happen, but ignore if it
            // does, since we wanted it removed anyway.
            e.printStackTrace();
        }
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isStackExpanded()) return; // Nothing to do

            String action = intent.getAction();
            String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
            boolean validReasonToCollapse = SYSTEM_DIALOG_REASON_RECENT_APPS.equals(reason)
                    || SYSTEM_DIALOG_REASON_HOME_KEY.equals(reason)
                    || SYSTEM_DIALOG_REASON_GESTURE_NAV.equals(reason);
            if ((Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action) && validReasonToCollapse)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                // if we're converting the bubble to a different mode, don't collapse since we want
                // the bubble to stay expanded.
                if (!isSelectedBubbleConvertingMode()) {
                    mMainExecutor.execute(() -> collapseStack());
                }
            }
        }
    };

    /** Returns the broadcast receiver registered for bubbles. */
    @VisibleForTesting
    BroadcastReceiver getBroadcastReceiver() {
        return mBroadcastReceiver;
    }

    private boolean isSelectedBubbleConvertingMode() {
        if (mBubbleData.getSelectedBubble() instanceof Bubble) {
            Bubble bubble = (Bubble) mBubbleData.getSelectedBubble();
            return bubble.isConvertingToBar();
        }
        return false;
    }

    private void registerShortcutBroadcastReceiver() {
        IntentFilter shortcutFilter = new IntentFilter();
        shortcutFilter.addAction(BubbleShortcutHelper.ACTION_SHOW_BUBBLES);
        ProtoLog.d(WM_SHELL_BUBBLES, "register broadcast receive for bubbles shortcut");
        mContext.registerReceiver(mShortcutBroadcastReceiver, shortcutFilter,
                Context.RECEIVER_NOT_EXPORTED);
    }

    private final BroadcastReceiver mShortcutBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ProtoLog.v(WM_SHELL_BUBBLES, "receive broadcast to show bubbles %s",
                    intent.getAction());
            if (BubbleShortcutHelper.ACTION_SHOW_BUBBLES.equals(intent.getAction())) {
                mMainExecutor.execute(() -> showBubblesFromShortcut());
            }
        }
    };

    /**
     * Called by the BubbleStackView and whenever all bubbles have animated out, and none have been
     * added in the meantime.
     */
    public void onAllBubblesAnimatedOut() {
        if (mStackView != null) {
            mStackView.setVisibility(INVISIBLE);
            removeFromWindowManagerMaybe();
        } else if (mLayerView != null) {
            mLayerView.setVisibility(INVISIBLE);
            removeFromWindowManagerMaybe();
        }
    }

    /**
     * Records the notification key for any active bubbles. These are used to restore active
     * bubbles when the user returns to the foreground.
     *
     * @param userId the id of the user
     */
    private void saveBubbles(@UserIdInt int userId) {
        // First clear any existing keys that might be stored.
        mSavedUserBubbleData.remove(userId);
        UserBubbleData userBubbleData = new UserBubbleData();
        // Add in all active bubbles for the current user.
        for (Bubble bubble : mBubbleData.getBubbles()) {
            userBubbleData.add(bubble.getKey(), bubble.showInShade());
        }
        mSavedUserBubbleData.put(userId, userBubbleData);
    }

    /**
     * Promotes existing notifications to Bubbles if they were previously bubbles.
     *
     * @param userId the id of the user
     */
    private void restoreBubbles(@UserIdInt int userId) {
        UserBubbleData savedBubbleData = mSavedUserBubbleData.get(userId);
        if (savedBubbleData == null) {
            // There were no bubbles saved for this used.
            return;
        }
        mSysuiProxy.getShouldRestoredEntries(savedBubbleData.getKeys(), (entries) -> {
            mMainExecutor.execute(() -> {
                for (BubbleEntry e : entries) {
                    if (canLaunchInTaskView(mContext, e)) {
                        boolean showInShade = savedBubbleData.isShownInShade(e.getKey());
                        updateBubble(e, true /* suppressFlyout */, showInShade);
                    }
                }
            });
        });
        // Finally, remove the entries for this user now that bubbles are restored.
        mSavedUserBubbleData.remove(userId);
    }

    @Override
    public void onThemeChanged() {
        if (mStackView != null) {
            mStackView.onThemeChanged();
        }
        mBubbleIconFactory = new BubbleIconFactory(mContext,
                mContext.getResources().getDimensionPixelSize(R.dimen.bubble_size),
                mContext.getResources().getDimensionPixelSize(R.dimen.bubble_badge_size),
                mContext.getResources().getColor(
                        com.android.launcher3.icons.R.color.important_conversation),
                mContext.getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.importance_ring_stroke_width));

        // Reload each bubble
        for (Bubble b : mBubbleData.getBubbles()) {
            b.inflate(null /* callback */,
                    mContext,
                    mExpandedViewManager,
                    mBubbleTaskViewFactory,
                    mBubblePositioner,
                    mStackView,
                    mLayerView,
                    mBubbleIconFactory,
                    mAppInfoProvider,
                    false /* skipInflation */);
        }
        for (Bubble b : mBubbleData.getOverflowBubbles()) {
            b.inflate(null /* callback */,
                    mContext,
                    mExpandedViewManager,
                    mBubbleTaskViewFactory,
                    mBubblePositioner,
                    mStackView,
                    mLayerView,
                    mBubbleIconFactory,
                    mAppInfoProvider,
                    false /* skipInflation */);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        DeviceConfig deviceConfig = DeviceConfig.create(mContext, mWindowManager);
        if (mBubblePositioner != null) {
            mBubblePositioner.update(deviceConfig);
        }
        if (mLayerView != null) {
            mLayerView.update(deviceConfig);
        }
        if (mStackView != null && newConfig != null) {
            if (newConfig.densityDpi != mDensityDpi
                    || !newConfig.windowConfiguration.getBounds().equals(mScreenBounds)) {
                mDensityDpi = newConfig.densityDpi;
                mScreenBounds.set(newConfig.windowConfiguration.getBounds());
                mBubbleData.onMaxBubblesChanged();
                mBubbleIconFactory = new BubbleIconFactory(mContext,
                        mContext.getResources().getDimensionPixelSize(R.dimen.bubble_size),
                        mContext.getResources().getDimensionPixelSize(R.dimen.bubble_badge_size),
                        mContext.getResources().getColor(
                                com.android.launcher3.icons.R.color.important_conversation),
                        mContext.getResources().getDimensionPixelSize(
                                com.android.internal.R.dimen.importance_ring_stroke_width));
                mStackView.onDisplaySizeChanged();
            }
            if (newConfig.fontScale != mFontScale) {
                mFontScale = newConfig.fontScale;
                mStackView.updateFontScale();
            }
            if (newConfig.getLayoutDirection() != mLayoutDirection) {
                mLayoutDirection = newConfig.getLayoutDirection();
                mStackView.onLayoutDirectionChanged(mLayoutDirection);
            }
            Locale newLocale = newConfig.locale;
            if (newLocale != null && !newLocale.equals(mLocale)) {
                mLocale = newLocale;
                mStackView.updateLocale();
            }
        }
    }

    private void onNotificationPanelExpandedChanged(boolean expanded) {
        if (mStackView != null && mStackView.isExpanded()) {
            ProtoLog.d(WM_SHELL_BUBBLES,
                    "onNotificationPanelExpandedChanged expanded=%b", expanded);
            if (expanded) {
                mStackView.stopMonitoringSwipeUpGesture();
            } else {
                mStackView.startMonitoringSwipeUpGesture();
            }
        }
    }

    private void setSysuiProxy(Bubbles.SysuiProxy proxy) {
        mSysuiProxy = proxy;
    }

    @VisibleForTesting
    public void setExpandListener(Bubbles.BubbleExpandListener listener) {
        mExpandListener = ((isExpanding, key) -> {
            if (listener != null) {
                listener.onBubbleExpandChanged(isExpanding, key);
            }
        });
        if (mStackView != null) {
            mStackView.setExpandListener(mExpandListener);
        }
    }

    /**
     * Whether or not there are bubbles present, regardless of them being visible on the
     * screen (e.g. if on AOD).
     */
    public boolean hasBubbles() {
        if (mStackView == null && mLayerView == null) {
            return false;
        }
        return mBubbleData.hasBubbles() || mBubbleData.isShowingOverflow();
    }

    /** Returns whether the given task is a non-transient bubble. */
    public boolean hasStableBubbleForTask(int taskId) {
        final Bubble bubble = mBubbleData.getBubbleInStackWithTaskId(taskId);
        return bubble != null && bubble.getPreparingTransition() == null;
    }

    /** Returns whether the given task should be an App Bubble */
    public boolean shouldBeAppBubble(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        if (com.android.window.flags2.Flags.rootTaskForBubble()) {
            return mAppBubbleRootTaskInfo != null
                    && taskInfo.parentTaskId == mAppBubbleRootTaskInfo.taskId;
        }
        return taskInfo.isAppBubble;
    }

    public boolean isStackExpanded() {
        return mBubbleData.isExpanded();
    }

    public void collapseStack() {
        mBubbleData.setExpanded(false /* expanded */);
    }

    /**
     * A bubble is being dragged in Launcher.
     * Will be called only when bubble bar is expanded.
     *
     * @param bubbleKey key of the bubble being dragged
     */
    public void startBubbleDrag(String bubbleKey) {
        if (mBubbleData.getSelectedBubble() != null) {
            collapseExpandedViewForBubbleBar();
        }
        if (mBubbleStateListener != null) {
            boolean overflow = BubbleOverflow.KEY.equals(bubbleKey);
            Rect rect = new Rect();
            mBubblePositioner.getBubbleBarExpandedViewBounds(mBubblePositioner.isBubbleBarOnLeft(),
                    overflow, rect);
            BubbleBarUpdate update = new BubbleBarUpdate();
            update.expandedViewDropTargetSize = new Point(rect.width(), rect.height());
            mBubbleStateListener.onBubbleStateChange(update);
        }
    }

    /**
     * A bubble is no longer being dragged in Launcher. And was released in given location.
     * Will be called only when bubble bar is expanded.
     *
     * @param location location where bubble was released
     * @param bubbleBarTopToScreenBottom the distance between the top coordinate of the bubble
     *        bar and the bottom of the screen after release
     */
    public void stopBubbleDrag(BubbleBarLocation location, int bubbleBarTopToScreenBottom) {
        mBubblePositioner.setBubbleBarLocation(location);
        mBubblePositioner.updateBubbleBarTopOnScreen(bubbleBarTopToScreenBottom);
        if (mBubbleData.getSelectedBubble() != null) {
            showExpandedViewForBubbleBar();
        }
    }

    /**
     * A bubble was dragged and is released in dismiss target in Launcher.
     *
     * @param bubbleKey key of the bubble being dragged to dismiss target
     * @param timestamp the timestamp of the removal
     */
    public void dragBubbleToDismiss(String bubbleKey, long timestamp) {
        final String selectedBubbleKey = mBubbleData.getSelectedBubbleKey();
        final Bubble bubbleToDismiss = mBubbleData.getAnyBubbleWithKey(bubbleKey);
        if (bubbleToDismiss != null) {
            mBubbleData.dismissBubbleWithKey(
                    bubbleKey, Bubbles.DISMISS_USER_GESTURE_FROM_LAUNCHER, timestamp);
            mLogger.log(bubbleToDismiss,
                    BubbleLogger.Event.BUBBLE_BAR_BUBBLE_DISMISSED_DRAG_BUBBLE);
        }
        if (mBubbleData.hasBubbles()) {
            // We still have bubbles, if we dragged an individual bubble to dismiss we were expanded
            // so re-expand to whatever is selected.
            showExpandedViewForBubbleBar();
            if (bubbleKey.equals(selectedBubbleKey)) {
                // We dragged the selected bubble to dismiss, log switch event
                if (mBubbleData.getSelectedBubble() instanceof Bubble) {
                    // Log only bubbles as overflow can't be dragged
                    mLogger.log((Bubble) mBubbleData.getSelectedBubble(),
                            BubbleLogger.Event.BUBBLE_BAR_BUBBLE_SWITCHED);
                }
            }
        }
    }

    /**
     * Show bubble bar user education relative to the reference position.
     * @param position the reference position in Screen coordinates.
     */
    public void showUserEducation(Point position) {
        if (mLayerView == null) return;
        mLayerView.showUserEducation(position);
    }

    @VisibleForTesting
    public boolean isBubbleNotificationSuppressedFromShade(String key, String groupKey) {
        final boolean isSuppressedBubble = (mBubbleData.hasAnyBubbleWithKey(key)
                && !mBubbleData.getAnyBubbleWithKey(key).showInShade());

        final boolean isSuppressedSummary = mBubbleData.isSummarySuppressed(groupKey);
        final boolean isSummary = key.equals(mBubbleData.getSummaryKey(groupKey));
        return (isSummary && isSuppressedSummary) || isSuppressedBubble;
    }

    /** Promote the provided bubble from the overflow view. */
    public void promoteBubbleFromOverflow(Bubble bubble) {
        if (isShowingAsBubbleBar()) {
            mLogger.log(bubble, BubbleLogger.Event.BUBBLE_BAR_OVERFLOW_REMOVE_BACK_TO_BAR);
        } else {
            mLogger.log(bubble, BubbleLogger.Event.BUBBLE_OVERFLOW_REMOVE_BACK_TO_STACK);
        }
        ProtoLog.d(WM_SHELL_BUBBLES, "promoteBubbleFromOverflow=%s", bubble.getKey());
        bubble.setInflateSynchronously(mInflateSynchronously);
        bubble.setShouldAutoExpand(true);
        bubble.markAsAccessedAt(System.currentTimeMillis());
        setIsBubble(bubble, true /* isBubble */);
    }

    /**
     * Expands and selects the provided bubble as long as it already exists in the stack or the
     * overflow.
     *
     * <p>This is used by external callers (launcher).
     */
    @VisibleForTesting
    public void expandStackAndSelectBubbleFromLauncher(String key, int bubbleBarTopToScreenBottom) {
        mBubblePositioner.updateBubbleBarTopOnScreen(bubbleBarTopToScreenBottom);

        if (BubbleOverflow.KEY.equals(key)) {
            mBubbleData.setSelectedBubbleFromLauncher(mBubbleData.getOverflow());
            mLayerView.showExpandedView(mBubbleData.getOverflow());
            mLogger.log(BubbleLogger.Event.BUBBLE_BAR_OVERFLOW_SELECTED);
            return;
        }

        final Bubble b = mBubbleData.getAnyBubbleWithKey(key);
        if (b == null) {
            return;
        }
        final boolean wasExpanded = (mLayerView != null && mLayerView.isExpanded());
        if (mBubbleData.hasBubbleInStackWithKey(b.getKey())) {
            // already in the stack
            mBubbleData.setSelectedBubbleFromLauncher(b);
            mLayerView.showExpandedView(b);
            if (wasExpanded) {
                mLogger.log(b, BubbleLogger.Event.BUBBLE_BAR_BUBBLE_SWITCHED);
            } else {
                mLogger.log(b, BubbleLogger.Event.BUBBLE_BAR_EXPANDED);
            }
        } else if (mBubbleData.hasOverflowBubbleWithKey(b.getKey())) {
            // TODO: (b/271468319) handle overflow
        } else {
            Log.w(TAG, "didn't add bubble from launcher: " + key);
        }
    }

    /**
     * Expands the stack if the selected bubble is present. This is currently used when user
     * education view is clicked to expand the selected bubble.
     */
    public void expandStackWithSelectedBubble() {
        if (mBubbleData.getSelectedBubble() != null) {
            mBubbleData.setExpanded(true);
        }
    }

    /**
     * Expands and selects the provided bubble as long as it already exists in the stack or the
     * overflow. This is currently used when opening a bubble via clicking on a conversation widget.
     */
    public void expandStackAndSelectBubble(Bubble b) {
        if (b == null) {
            return;
        }
        if (mBubbleData.hasBubbleInStackWithKey(b.getKey())) {
            // already in the stack
            mBubbleData.setSelectedBubbleAndExpandStack(b);
        } else if (mBubbleData.hasOverflowBubbleWithKey(b.getKey())) {
            // promote it out of the overflow
            promoteBubbleFromOverflow(b);
        }
    }

    /**
     * Expands and selects a bubble created or found via the provided shortcut info.
     *
     * @param info the shortcut info for the bubble.
     * @param bubbleBarLocation optional location in case bubble bar should be repositioned.
     */
    public void expandStackAndSelectBubble(ShortcutInfo info,
            @Nullable BubbleBarLocation bubbleBarLocation) {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) return;
        Bubble b = mBubbleData.getOrCreateBubble(info); // Removes from overflow
        ProtoLog.v(WM_SHELL_BUBBLES, "expandStackAndSelectBubble - shortcut=%s", info);
        expandStackAndSelectAppBubble(b, bubbleBarLocation, UpdateSource.APP_ICON_DRAG);
    }

    /**
     * Expands and selects a bubble created or found for this app.
     *
     * @param intent the intent for the bubble.
     */
    public void expandStackAndSelectBubble(Intent intent, UserHandle user,
            @Nullable BubbleBarLocation bubbleBarLocation) {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) return;
        Bubble b = mBubbleData.getOrCreateBubble(intent, user); // Removes from overflow
        ProtoLog.v(WM_SHELL_BUBBLES, "expandStackAndSelectBubble - intent=%s", intent);
        expandStackAndSelectAppBubble(b, bubbleBarLocation, UpdateSource.APP_ICON_DRAG);
    }

    /**
     * Expands and selects a bubble created or found for this app.
     *
     * @param pendingIntent     the intent for the bubble.
     * @param bubbleBarLocation optional location in case bubble bar should be repositioned.
     */
    public void expandStackAndSelectBubble(PendingIntent pendingIntent, UserHandle user,
            @Nullable BubbleBarLocation bubbleBarLocation) {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) return;
        Bubble b = mBubbleData.getOrCreateBubble(pendingIntent, user); // Removes from overflow
        ProtoLog.v(WM_SHELL_BUBBLES, "expandStackAndSelectBubble - pendingIntent=%s",
                pendingIntent);
        expandStackAndSelectAppBubble(b, bubbleBarLocation, UpdateSource.APP_ICON_DRAG);
    }

    void expandStackAndSelectAppBubble(Bubble b, @Nullable BubbleBarLocation bubbleBarLocation,
            @UpdateSource int source) {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) return;
        BubbleBarLocation updateLocation = isShowingAsBubbleBar() ? bubbleBarLocation : null;
        if (updateLocation != null) {
            // does not update the bubble bar location of the bubble bar, just expanded view
            updateExpandedViewForBubbleBarLocation(updateLocation, source);
        }
        if (b.isInflated()) {
            // mBubbleData should be updated with the new location to update the bubble bar location
            mBubbleData.setSelectedBubbleAndExpandStack(b, updateLocation);
        } else {
            b.enable(Notification.BubbleMetadata.FLAG_AUTO_EXPAND_BUBBLE);

            if (isShowingAsBubbleBar()) {
                ensureBubbleViewsAndWindowCreated();
                mBubbleTransitions.startLaunchIntoOrConvertToBubble(b, mExpandedViewManager,
                        mBubbleTaskViewFactory, mBubblePositioner, mStackView, mLayerView,
                        mBubbleIconFactory, mInflateSynchronously, bubbleBarLocation);
            } else {
                inflateAndAdd(b, /* suppressFlyout= */ true, /* showInShade= */ false,
                        updateLocation);
            }
        }
    }

    /**
     * Expands and selects a bubble created from a running task in a different mode.
     *
     * @param taskInfo the task.
     * @param dragData optional information about the task when it is being dragged into a bubble
     */
    public void expandStackAndSelectBubble(ActivityManager.RunningTaskInfo taskInfo,
            @Nullable BubbleTransitions.DragData dragData) {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) return;
        Bubble b = mBubbleData.getOrCreateBubble(taskInfo); // Removes from overflow
        ProtoLog.v(WM_SHELL_BUBBLES, "expandStackAndSelectBubble - taskId=%s", taskInfo.taskId);
        BubbleBarLocation location = null;
        if (dragData != null) {
            location =
                    dragData.isReleasedOnLeft() ? BubbleBarLocation.LEFT : BubbleBarLocation.RIGHT;
        }
        if (b.isInflated()) {
            mBubbleData.setSelectedBubbleAndExpandStack(b, location);
        } else {
            if (location != null) {
                setBubbleBarLocation(location, UpdateSource.DRAG_TASK);
            }
            b.enable(Notification.BubbleMetadata.FLAG_AUTO_EXPAND_BUBBLE);
            // Lazy init stack view when a bubble is created
            ensureBubbleViewsAndWindowCreated();
            mBubbleTransitions.startConvertToBubble(b, taskInfo, mExpandedViewManager,
                    mBubbleTaskViewFactory, mBubblePositioner, mStackView, mLayerView,
                    mBubbleIconFactory, mHomeIntentProvider, dragData, mInflateSynchronously);
        }
    }

    /**
     * Expands and selects a bubble created from a running task in a different mode.
     *
     * @param taskInfo the task.
     */
    @Nullable
    public Transitions.TransitionHandler expandStackAndSelectBubbleForExistingTransition(
            @NonNull ActivityManager.RunningTaskInfo taskInfo,
            @NonNull IBinder transition,
            Consumer<Transitions.TransitionHandler> onInflatedCallback) {
        if (!BubbleAnythingFlagHelper.enableCreateAnyBubble()) return null;

        Bubble b = mBubbleData.getBubbleInStackWithTaskId(taskInfo.taskId);
        if (b != null) {
            // Reuse the existing bubble
            mBubbleData.setSelectedBubbleAndExpandStack(b, BubbleBarLocation.DEFAULT);
        } else {
            // Create a new bubble and show it, remove from overflow
            b = mBubbleData.getOrCreateBubble(taskInfo);
        }
        ProtoLog.v(WM_SHELL_BUBBLES, "expandStackAndSelectBubbleForExistingTransition() taskId=%s",
                taskInfo.taskId);
        b.enable(Notification.BubbleMetadata.FLAG_AUTO_EXPAND_BUBBLE);

        // Lazy init stack view when a bubble is created
        ensureBubbleViewsAndWindowCreated();
        return mBubbleTransitions.startLaunchNewTaskBubbleForExistingTransition(b,
                mExpandedViewManager, mBubbleTaskViewFactory, mBubblePositioner, mStackView,
                mLayerView, mBubbleIconFactory, mInflateSynchronously, transition,
                onInflatedCallback);
    }

    /**
     * Expands and selects a bubble based on the provided {@link BubbleEntry}. If no bubble
     * exists for this entry, and it is able to bubble, a new bubble will be created.
     *
     * <p>This is the method to use when opening a bubble via a notification or in a state where
     * the device might not be unlocked.
     *
     * @param entry the entry to use for the bubble.
     */
    public void expandStackAndSelectBubble(BubbleEntry entry) {
        ProtoLog.d(WM_SHELL_BUBBLES, "opening bubble from notification key=%s mIsStatusBarShade=%b",
                entry.getKey(), mIsStatusBarShade);
        if (mIsStatusBarShade) {
            mNotifEntryToExpandOnShadeUnlock = null;

            String key = entry.getKey();
            Bubble bubble = mBubbleData.getBubbleInStackWithKey(key);
            if (bubble != null) {
                mBubbleData.setSelectedBubbleAndExpandStack(bubble);
            } else {
                bubble = mBubbleData.getOverflowBubbleWithKey(key);
                if (bubble != null) {
                    promoteBubbleFromOverflow(bubble);
                } else if (entry.canBubble()) {
                    // It can bubble but it's not -- it got aged out of the overflow before it
                    // was dismissed or opened, make it a bubble again.
                    setIsBubble(entry, true /* isBubble */, true /* autoExpand */);
                }
            }
        } else {
            // Wait until we're unlocked to expand, so that the user can see the expand animation
            // and also to work around bugs with expansion animation + shade unlock happening at the
            // same time.
            mNotifEntryToExpandOnShadeUnlock = entry;
        }
    }

    /**
     * Adds or updates a bubble associated with the provided notification entry.
     *
     * @param notif the notification associated with this bubble.
     */
    @VisibleForTesting
    public void updateBubble(BubbleEntry notif) {
        int bubbleUserId = notif.getStatusBarNotification().getUserId();
        if (isCurrentProfile(bubbleUserId)) {
            updateBubble(notif, false /* suppressFlyout */, true /* showInShade */);
        } else {
            // Skip update, but store it in user bubbles so it gets restored after user switch
            mSavedUserBubbleData.get(bubbleUserId, new UserBubbleData()).add(notif.getKey(),
                    true /* shownInShade */);
            Log.w(TAG, "updateBubble, ignore update for non-active user=" + bubbleUserId
                    + " currentUser=" + mCurrentUserId);
        }
    }

    /**
     * This method has different behavior depending on:
     *    - if a notes bubble exists
     *    - if a notes bubble is expanded
     *
     * If no notes bubble exists, this will add and expand a bubble with the provided intent. The
     * intent must be explicit (i.e. include a package name or fully qualified component class name)
     * and the activity for it should be resizable.
     *
     * If a notes bubble exists, this will toggle the visibility of it, i.e. if the notes bubble is
     * expanded, calling this method will collapse it. If the notes bubble is not expanded, calling
     * this method will expand it.
     *
     * These bubbles are <b>not</b> backed by a notification and remain until the user dismisses
     * the bubble or bubble stack.
     *
     * Some details:
     *    - Calling this method with a different intent than the existing bubble will do nothing
     *
     * @param intent the intent to display in the bubble expanded view.
     * @param user the {@link UserHandle} of the user to start this activity for.
     * @param icon the {@link Icon} to use for the bubble view.
     */
    public void showOrHideNotesBubble(Intent intent, UserHandle user, @Nullable Icon icon) {
        if (intent == null || intent.getPackage() == null) {
            Log.w(TAG, "Notes bubble failed to show, invalid intent: " + intent
                    + ((intent != null) ? " with package: " + intent.getPackage() : " "));
            return;
        }

        String noteBubbleKey = Bubble.getNoteBubbleKeyForApp(intent.getPackage(), user);
        PackageManager packageManager = getPackageManagerForUser(mContext, user.getIdentifier());
        if (!mResizabilityChecker.isResizableActivity(intent, packageManager, noteBubbleKey)) {
            // resize check logs any errors
            return;
        }

        Bubble existingNotebubble = mBubbleData.getBubbleInStackWithKey(noteBubbleKey);
        ProtoLog.d(WM_SHELL_BUBBLES,
                "showOrHideNotesBubble, key=%s existingAppBubble=%s stackVisibility=%s "
                        + "statusBarShade=%s",
                noteBubbleKey, existingNotebubble,
                (mStackView != null ? mStackView.getVisibility() : "null"),
                mIsStatusBarShade);

        if (existingNotebubble != null) {
            BubbleViewProvider selectedBubble = mBubbleData.getSelectedBubble();
            if (isStackExpanded()) {
                if (selectedBubble != null && noteBubbleKey.equals(selectedBubble.getKey())) {
                    ProtoLog.d(WM_SHELL_BUBBLES, "collapseStack for %s", noteBubbleKey);
                    // Notes bubble is expanded, lets collapse
                    collapseStack();
                } else {
                    ProtoLog.d(WM_SHELL_BUBBLES, "setSelected for %s", noteBubbleKey);
                    // Notes bubble is not selected, select it
                    mBubbleData.setSelectedBubble(existingNotebubble);
                }
            } else {
                ProtoLog.d(WM_SHELL_BUBBLES, "setSelectedBubbleAndExpandStack %s", noteBubbleKey);
                // Notes bubble is not selected, select it & expand
                mBubbleData.setSelectedBubbleAndExpandStack(existingNotebubble);
            }
        } else {
            // Check if it exists in the overflow
            Bubble b = mBubbleData.getOverflowBubbleWithKey(noteBubbleKey);
            if (b != null) {
                // It's in the overflow, so remove it & reinflate
                mBubbleData.dismissBubbleWithKey(noteBubbleKey, Bubbles.DISMISS_NOTIF_CANCEL);
                // Update the bubble entry in the overflow with the latest intent.
                b.setIntent(intent);
            } else {
                // Notes bubble does not exist, lets add and expand it
                b = Bubble.createNotesBubble(intent, user, icon, mMainExecutor,
                        mBackgroundExecutor);
            }
            ProtoLog.d(WM_SHELL_BUBBLES, "inflateAndAdd %s", noteBubbleKey);
            b.setShouldAutoExpand(true);
            inflateAndAdd(b, /* suppressFlyout= */ true, /* showInShade= */ false);
        }
    }

    /**
     * Dismiss bubble if it exists and remove it from the stack
     */
    public void dismissBubble(Bubble bubble, @Bubbles.DismissReason int reason) {
        dismissBubble(bubble.getKey(), reason);
    }

    /**
     * Dismiss bubble with given key if it exists and remove it from the stack
     */
    public void dismissBubble(String key, @Bubbles.DismissReason int reason) {
        mBubbleData.dismissBubbleWithKey(key, reason);
    }

    /**
     * Performs a screenshot that may exclude the bubble layer, if one is present. The screenshot
     * can be access via the supplied {@link SynchronousScreenCaptureListener#getBuffer()}
     * asynchronously.
     */
    public void getScreenshotExcludingBubble(int displayId,
            SynchronousScreenCaptureListener screenCaptureListener) {
        try {
            ScreenCapture.CaptureArgs args = null;
            View viewToUse = mStackView != null ? mStackView : mLayerView;
            if (viewToUse != null) {
                ViewRootImpl viewRoot = viewToUse.getViewRootImpl();
                if (viewRoot != null) {
                    SurfaceControl bubbleLayer = viewRoot.getSurfaceControl();
                    if (bubbleLayer != null) {
                        args = new ScreenCapture.CaptureArgs.Builder<>()
                                .setExcludeLayers(new SurfaceControl[] {bubbleLayer})
                                .build();
                    }
                }
            }

            mWmService.captureDisplay(displayId, args, screenCaptureListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to capture screenshot");
        }
    }

    /** Sets the note bubble's taskId which is cached for SysUI. */
    public void setNoteBubbleTaskId(String key, int taskId) {
        mImpl.mCachedState.setNoteBubbleTaskId(key, taskId);
    }

    /**
     * Fills the overflow bubbles by loading them from disk.
     */
    void loadOverflowBubblesFromDisk() {
        if (!mOverflowDataLoadNeeded) {
            return;
        }
        mOverflowDataLoadNeeded = false;
        List<UserInfo> users = mUserManager.getAliveUsers();
        List<Integer> userIds = users.stream().map(userInfo -> userInfo.id).toList();
        mDataRepository.loadBubbles(mCurrentUserId, userIds, (bubbles) -> {
            bubbles.forEach(bubble -> {
                if (mBubbleData.hasAnyBubbleWithKey(bubble.getKey())) {
                    // if the bubble is already active, there's no need to push it to overflow
                    return;
                }
                bubble.inflate(
                        (b) -> mBubbleData.overflowBubble(Bubbles.DISMISS_RELOAD_FROM_DISK, bubble),
                        mContext,
                        mExpandedViewManager,
                        mBubbleTaskViewFactory,
                        mBubblePositioner,
                        mStackView,
                        mLayerView,
                        mBubbleIconFactory,
                        mAppInfoProvider,
                        true /* skipInflation */);
            });
            return null;
        });
    }

    void setUpBubbleViewsForMode() {
        if (mBubbleData.isExpanded() && !mBubbleData.isShowingOverflow()
                && isShowingAsBubbleBar()) {
            Bubble bubble = (Bubble) mBubbleData.getSelectedBubble();
            if (bubble != null) {
                mBubbleTransitions.startFloatingToBarConversion(bubble, mBubblePositioner);
                TaskView taskView = bubble.getTaskView();
                taskView.setIsMovingWindows(true);
                ViewGroup parent = (ViewGroup) taskView.getParent();
                parent.removeView(taskView);
            }
        }

        mBubbleViewCallback = isShowingAsBubbleBar()
                ? mBubbleBarViewCallback
                : mBubbleStackViewCallback;

        // reset the overflow so that it can be re-added later if needed.
        if (mStackView != null) {
            mStackView.resetOverflowView();
            mStackView.removeAllViews();
        }
        // cleanup existing bubble views so they can be recreated later if needed, but retain
        // TaskView.
        mBubbleData.getBubbles().forEach(b -> b.cleanupViews(/* cleanupTaskView= */ false));

        // remove the current bubble container from window manager, null it out, and create a new
        // container based on the current mode.
        removeFromWindowManagerMaybe();
        mLayerView = null;
        mStackView = null;

        if (!mBubbleData.hasBubbles()) {
            // if there are no bubbles, don't create the stack or layer views. they will be created
            // later when the first bubble is added.
            return;
        }

        ensureBubbleViewsAndWindowCreated();

        // inflate bubble views
        BubbleViewInfoTask.Callback callback = null;
        if (!isShowingAsBubbleBar()) {
            callback = b -> {
                if (mStackView != null) {
                    b.setSuppressFlyout(true);
                    mStackView.addBubble(b);
                    if (b.getKey().equals(mBubbleData.getSelectedBubbleKey())) {
                        mStackView.setSelectedBubble(b);
                    }
                } else {
                    Log.w(TAG, "Tried to add a bubble to the stack but the stack is null");
                }
            };
        } else if (mBubbleData.isExpanded() && mBubbleData.getSelectedBubble() != null) {
            callback = b -> {
                if (b.getKey().equals(mBubbleData.getSelectedBubbleKey())) {
                    mLayerView.showExpandedView(b);
                }
            };
        }
        // TODO (b/380105874): Remove this after properly transitioning the expanded bubble from bar
        // to floating
        if (mStackView != null && mBubbleData.isExpanded()) {
            mBubbleData.collapseNoUpdate();
        }
        for (int i = mBubbleData.getBubbles().size() - 1; i >= 0; i--) {
            Bubble bubble = mBubbleData.getBubbles().get(i);
            bubble.inflate(callback,
                    mContext,
                    mExpandedViewManager,
                    mBubbleTaskViewFactory,
                    mBubblePositioner,
                    mStackView,
                    mLayerView,
                    mBubbleIconFactory,
                    mAppInfoProvider,
                    false /* skipInflation */);
        }
        if (mBubbleData.isShowingOverflow()) {
            BubbleOverflow bubbleOverflow = mBubbleData.getOverflow();
            if (isShowingAsBubbleBar()) {
                bubbleOverflow.initializeForBubbleBar(mExpandedViewManager, mBubblePositioner);
                mLayerView.showExpandedView(bubbleOverflow);
            } else {
                bubbleOverflow.initialize(mExpandedViewManager, mStackView, mBubblePositioner);
            }

        }
    }

    /**
     * Adds or updates a bubble associated with the provided notification entry.
     *
     * @param notif          the notification associated with this bubble.
     * @param suppressFlyout this bubble suppress flyout or not.
     * @param showInShade    this bubble show in shade or not.
     */
    @VisibleForTesting
    public void updateBubble(BubbleEntry notif, boolean suppressFlyout, boolean showInShade) {
        // If this is an interruptive notif, mark that it's interrupted
        mSysuiProxy.setNotificationInterruption(notif.getKey());
        final boolean isNonInterruptiveNotExpanding = !notif.getRanking().isTextChanged()
                && (notif.getBubbleMetadata() != null
                && !notif.getBubbleMetadata().getAutoExpandBubble());
        final Bubble bubble;
        if (isNonInterruptiveNotExpanding
                && mBubbleData.hasOverflowBubbleWithKey(notif.getKey())) {
            // Update the bubble but don't promote it out of overflow
            bubble = mBubbleData.getOverflowBubbleWithKey(notif.getKey());
            if (notif.isBubble()) {
                notif.setFlagBubble(false);
            }
            updateNotNotifyingEntry(bubble, notif, showInShade);
        } else if (mBubbleData.hasAnyBubbleWithKey(notif.getKey())
                && isNonInterruptiveNotExpanding) {
            bubble = mBubbleData.getAnyBubbleWithKey(notif.getKey());
            if (bubble != null) {
                updateNotNotifyingEntry(bubble, notif, showInShade);
            }
        } else if (mBubbleData.isSuppressedWithLocusId(notif.getLocusId())) {
            // Update the bubble but don't promote it out of overflow
            bubble = mBubbleData.getSuppressedBubbleWithKey(notif.getKey());
            if (bubble != null) {
                updateNotNotifyingEntry(bubble, notif, showInShade);
            }
        } else {
            bubble = mBubbleData.getOrCreateBubble(notif, null /* persistedBubble */);
            if (notif.shouldSuppressNotificationList()) {
                // If we're suppressing notifs for DND, we don't want the bubbles to randomly
                // expand when DND turns off so flip the flag.
                if (bubble.shouldAutoExpand()) {
                    bubble.setShouldAutoExpand(false);
                }
                mImpl.mCachedState.updateBubbleSuppressedState(bubble);
            } else {
                inflateAndAdd(bubble, suppressFlyout, showInShade);
            }
        }
    }

    void updateNotNotifyingEntry(Bubble b, BubbleEntry entry, boolean showInShade) {
        boolean showInShadeBefore = b.showInShade();
        boolean isBubbleSelected = Objects.equals(b, mBubbleData.getSelectedBubble());
        boolean isBubbleExpandedAndSelected = isStackExpanded() && isBubbleSelected;
        b.setEntry(entry);
        boolean suppress = isBubbleExpandedAndSelected || !showInShade || !b.showInShade();
        b.setSuppressNotification(suppress);
        b.setShowDot(!isBubbleExpandedAndSelected);
        if (showInShadeBefore != b.showInShade()) {
            mImpl.mCachedState.updateBubbleSuppressedState(b);
        }
    }

    @VisibleForTesting
    public void inflateAndAdd(Bubble bubble, boolean suppressFlyout, boolean showInShade) {
        inflateAndAdd(bubble, suppressFlyout, showInShade, /* bubbleBarLocation= */ null);
    }

    /**
     * Inflates and adds a bubble. Updates Bubble Bar location if bubbles
     * are shown in the Bubble Bar and the location is not null.
     */
    @VisibleForTesting
    public void inflateAndAdd(Bubble bubble, boolean suppressFlyout, boolean showInShade,
            @Nullable BubbleBarLocation bubbleBarLocation) {
        // Lazy init stack view when a bubble is created
        ensureBubbleViewsAndWindowCreated();
        bubble.setInflateSynchronously(mInflateSynchronously);
        bubble.inflate(
                b -> mBubbleData.notificationEntryUpdated(b, suppressFlyout, showInShade,
                        bubbleBarLocation),
                mContext,
                mExpandedViewManager,
                mBubbleTaskViewFactory,
                mBubblePositioner,
                mStackView,
                mLayerView,
                mBubbleIconFactory,
                mAppInfoProvider,
                false /* skipInflation */);
    }

    /**
     * Removes the bubble with the given key.
     * <p>
     * Must be called from the main thread.
     */
    @MainThread
    public void removeBubble(String key, int reason) {
        if (mBubbleData.hasAnyBubbleWithKey(key)) {
            mBubbleData.dismissBubbleWithKey(key, reason);
        }
    }

    /**
     * Removes all the bubbles.
     * <p>
     * Must be called from the main thread.
     */
    @VisibleForTesting
    @MainThread
    public void removeAllBubbles(@Bubbles.DismissReason int reason) {
        mBubbleData.dismissAll(reason);
        if (reason == Bubbles.DISMISS_USER_GESTURE) {
            mLogger.log(BubbleLogger.Event.BUBBLE_BAR_DISMISSED_DRAG_BAR);
        }
    }

    private void onEntryAdded(BubbleEntry entry) {
        if (canLaunchInTaskView(mContext, entry)) {
            updateBubble(entry);
        }
    }

    @VisibleForTesting
    public void onEntryUpdated(BubbleEntry entry, boolean shouldBubbleUp, boolean fromSystem) {
        if (!fromSystem) {
            return;
        }
        // shouldBubbleUp checks canBubble & for bubble metadata
        boolean shouldBubble = shouldBubbleUp && canLaunchInTaskView(mContext, entry);
        if (!shouldBubble && mBubbleData.hasAnyBubbleWithKey(entry.getKey())) {
            // It was previously a bubble but no longer a bubble -- lets remove it
            removeBubble(entry.getKey(), DISMISS_NO_LONGER_BUBBLE);
        } else if (shouldBubble && entry.isBubble()) {
            updateBubble(entry);
        }
    }

    private void onEntryRemoved(BubbleEntry entry) {
        if (isSummaryOfBubbles(entry)) {
            final String groupKey = entry.getStatusBarNotification().getGroupKey();
            mBubbleData.removeSuppressedSummary(groupKey);

            // Remove any associated bubble children with the summary
            final List<Bubble> bubbleChildren = getBubblesInGroup(groupKey);
            for (int i = 0; i < bubbleChildren.size(); i++) {
                removeBubble(bubbleChildren.get(i).getKey(), DISMISS_GROUP_CANCELLED);
            }
        } else {
            removeBubble(entry.getKey(), DISMISS_NOTIF_CANCEL);
        }
    }

    @VisibleForTesting
    public void onRankingUpdated(RankingMap rankingMap,
            HashMap<String, Pair<BubbleEntry, Boolean>> entryDataByKey) {
        if (mTmpRanking == null) {
            mTmpRanking = new NotificationListenerService.Ranking();
        }
        String[] orderedKeys = rankingMap.getOrderedKeys();
        for (int i = 0; i < orderedKeys.length; i++) {
            String key = orderedKeys[i];
            Pair<BubbleEntry, Boolean> entryData = entryDataByKey.get(key);
            BubbleEntry entry = entryData.first;
            boolean shouldBubbleUp = entryData.second;
            if (entry != null && !isCurrentProfile(
                    entry.getStatusBarNotification().getUser().getIdentifier())) {
                return;
            }
            if (entry != null && (entry.shouldSuppressNotificationList()
                    || entry.getRanking().isSuspended())) {
                shouldBubbleUp = false;
            }
            rankingMap.getRanking(key, mTmpRanking);
            boolean isActiveOrInOverflow = mBubbleData.hasAnyBubbleWithKey(key);
            boolean isActive = mBubbleData.hasBubbleInStackWithKey(key);
            if (isActiveOrInOverflow && !mTmpRanking.canBubble()) {
                // If this entry is no longer allowed to bubble, dismiss with the BLOCKED reason.
                // This means that the app or channel's ability to bubble has been revoked.
                mBubbleData.dismissBubbleWithKey(key, DISMISS_BLOCKED);
            } else if (isActiveOrInOverflow && !shouldBubbleUp) {
                // If this entry is allowed to bubble, but cannot currently bubble up or is
                // suspended, dismiss it. This happens when DND is enabled and configured to hide
                // bubbles, or focus mode is enabled and the app is designated as distracting.
                // Dismissing with the reason DISMISS_NO_BUBBLE_UP will retain the underlying
                // notification, so that the bubble will be re-created if shouldBubbleUp returns
                // true.
                mBubbleData.dismissBubbleWithKey(key, DISMISS_NO_BUBBLE_UP);
            } else if (entry != null && mTmpRanking.isBubble() && !isActiveOrInOverflow) {
                entry.setFlagBubble(true);
                onEntryUpdated(entry, shouldBubbleUp, /* fromSystem= */ true);
            }
        }
    }

    @VisibleForTesting
    public void onNotificationChannelModified(String pkg, UserHandle user,
            NotificationChannel channel, int modificationType) {
        // Only query overflow bubbles here because active bubbles will have an active notification
        // and channel changes we care about would result in a ranking update.
        List<Bubble> overflowBubbles = new ArrayList<>(mBubbleData.getOverflowBubbles());
        for (int i = 0; i < overflowBubbles.size(); i++) {
            Bubble b = overflowBubbles.get(i);
            if (Objects.equals(b.getShortcutId(), channel.getConversationId())
                    && b.getPackageName().equals(pkg)
                    && b.getUser().getIdentifier() == user.getIdentifier()) {
                if (!channel.canBubble() || channel.isDeleted()) {
                    mBubbleData.dismissBubbleWithKey(b.getKey(), DISMISS_NO_LONGER_BUBBLE);
                }
            }
        }
    }

    /**
     * Retrieves any bubbles that are part of the notification group represented by the provided
     * group key.
     */
    private ArrayList<Bubble> getBubblesInGroup(@Nullable String groupKey) {
        ArrayList<Bubble> bubbleChildren = new ArrayList<>();
        if (groupKey == null) {
            return bubbleChildren;
        }
        for (Bubble bubble : mBubbleData.getBubbles()) {
            if (bubble.getGroupKey() != null && groupKey.equals(bubble.getGroupKey())) {
                bubbleChildren.add(bubble);
            }
        }
        return bubbleChildren;
    }

    private void setIsBubble(@NonNull final BubbleEntry entry, final boolean isBubble,
            final boolean autoExpand) {
        Objects.requireNonNull(entry);
        entry.setFlagBubble(isBubble);
        try {
            int flags = 0;
            if (autoExpand) {
                flags = Notification.BubbleMetadata.FLAG_SUPPRESS_NOTIFICATION;
                flags |= Notification.BubbleMetadata.FLAG_AUTO_EXPAND_BUBBLE;
            }
            mBarService.onNotificationBubbleChanged(entry.getKey(), isBubble, flags);
        } catch (RemoteException e) {
            // Bad things have happened
        }
    }

    private void setIsBubble(@NonNull final Bubble b, final boolean isBubble) {
        Objects.requireNonNull(b);
        b.setIsBubble(isBubble);
        mSysuiProxy.getPendingOrActiveEntry(b.getKey(), (entry) -> {
            mMainExecutor.execute(() -> {
                if (entry != null) {
                    // Updating the entry to be a bubble will trigger our normal update flow
                    setIsBubble(entry, isBubble, b.shouldAutoExpand());
                } else if (isBubble) {
                    // If bubble doesn't exist, it's a persisted bubble so we need to add it to the
                    // stack ourselves
                    Bubble bubble = mBubbleData.getOrCreateBubble(null, b /* persistedBubble */);
                    inflateAndAdd(bubble, bubble.shouldAutoExpand() /* suppressFlyout */,
                            !bubble.shouldAutoExpand() /* showInShade */);
                }
            });
        });
    }

    @Override
    public boolean mergeTaskWithUnfold(@NonNull ActivityManager.RunningTaskInfo taskInfo,
            @NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change change,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT) {
        if (!mBubbleTransitions.mTaskViewTransitions.isTaskViewTask(taskInfo)) {
            // if this task isn't managed by bubble transitions just bail.
            return false;
        }
        if (isShowingAsBubbleBar()) {
            if (info.getType() != TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR) {
                return false;
            }
            if (mBubbleData.getSelectedBubble() instanceof Bubble) {
                Bubble bubble = (Bubble) mBubbleData.getSelectedBubble();
                if (bubble.getPreparingTransition() != null) {
                    bubble.getPreparingTransition().mergeWithUnfold(change.getLeash(), finishT);
                }
                return true;
            }
            return false;
        }

        boolean merged = mBubbleTransitions.mTaskViewTransitions.updateBoundsForUnfold(
                change.getEndAbsBounds(), startT, finishT, change.getTaskInfo(), change.getLeash());
        if (merged) {
            BubbleViewProvider selectedBubble = mBubbleData.getSelectedBubble();
            if (selectedBubble != null && selectedBubble.getExpandedView() != null) {
                selectedBubble.getExpandedView().onContainerClipUpdate();
            }
        }
        return merged;
    }

    /** When bubbles are floating, this will be used to notify the floating views. */
    private final BubbleViewCallback mBubbleStackViewCallback = new BubbleViewCallback() {
        @Override
        public void removeBubble(Bubble removedBubble) {
            if (mStackView != null) {
                mStackView.removeBubble(removedBubble);
            }
        }

        @Override
        public void addBubble(Bubble addedBubble) {
            if (mStackView != null) {
                mStackView.addBubble(addedBubble);
            }
        }

        @Override
        public void updateBubble(Bubble updatedBubble) {
            if (mStackView != null) {
                mStackView.updateBubble(updatedBubble);
            }
        }

        @Override
        public void bubbleOrderChanged(List<Bubble> bubbleOrder, boolean updatePointer) {
            if (mStackView != null) {
                mStackView.updateBubbleOrder(bubbleOrder, updatePointer);
            }
        }

        @Override
        public void suppressionChanged(Bubble bubble, boolean isSuppressed) {
            if (mStackView != null) {
                mStackView.setBubbleSuppressed(bubble, isSuppressed);
            }
        }

        @Override
        public void expansionChanged(boolean isExpanded) {
            if (mStackView != null) {
                mStackView.setExpanded(isExpanded);
            }
        }

        @Override
        public void selectionChanged(BubbleViewProvider selectedBubble) {
            if (mStackView != null) {
                mStackView.setSelectedBubble(selectedBubble);
            }

        }

        @Override
        public void bubbleOverflowChanged(boolean hasBubbles) {
            if (Flags.enableOptionalBubbleOverflow()) {
                if (mStackView != null) {
                    mStackView.showOverflow(hasBubbles);
                }
            }
        }
    };

    /** When bubbles are in the bubble bar, this will be used to notify bubble bar views. */
    private final BubbleViewCallback mBubbleBarViewCallback = new BubbleViewCallback() {
        @Override
        public void removeBubble(Bubble removedBubble) {
            if (mLayerView != null) {
                final BubbleTransitions.BubbleTransition bubbleTransit =
                        removedBubble.getPreparingTransition();
                mLayerView.removeBubble(removedBubble, () -> {
                    if (bubbleTransit != null) {
                        bubbleTransit.continueCollapse();
                    }
                    if (!mBubbleData.hasBubbles() && !isStackExpanded()) {
                        mLayerView.setVisibility(INVISIBLE);
                        removeFromWindowManagerMaybe();
                    }
                });
            }
        }

        @Override
        public void addBubble(Bubble addedBubble) {
            // Only log metrics event
            mLogger.log(addedBubble, BubbleLogger.Event.BUBBLE_BAR_BUBBLE_POSTED);
            // Nothing to do for adds, these are handled by launcher / in the bubble bar.
        }

        @Override
        public void updateBubble(Bubble updatedBubble) {
            // Only log metrics event
            mLogger.log(updatedBubble, BubbleLogger.Event.BUBBLE_BAR_BUBBLE_UPDATED);
            // Nothing to do for updates, these are handled by launcher / in the bubble bar.
        }

        @Override
        public void bubbleOrderChanged(List<Bubble> bubbleOrder, boolean updatePointer) {
            // Nothing to do for order changes, these are handled by launcher / in the bubble bar.
        }

        @Override
        public void bubbleOverflowChanged(boolean hasBubbles) {
            // Nothing to do for our views, handled by launcher / in the bubble bar.
        }

        @Override
        public void suppressionChanged(Bubble bubble, boolean isSuppressed) {
            // Nothing to do for our views, handled by launcher / in the bubble bar.
        }

        @Override
        public void expansionChanged(boolean isExpanded) {
            // in bubble bar mode, let the request to show the expanded view come from launcher.
            // only collapse here if we're collapsing.
            if (!isExpanded) {
                collapseExpandedViewForBubbleBar();
            }

            BubbleLogger.Event event = isExpanded ? BubbleLogger.Event.BUBBLE_BAR_EXPANDED
                    : BubbleLogger.Event.BUBBLE_BAR_COLLAPSED;
            BubbleViewProvider selectedBubble = mBubbleData.getSelectedBubble();
            if (selectedBubble instanceof Bubble) {
                mLogger.log((Bubble) selectedBubble, event);
            } else {
                mLogger.log(event);
            }
        }

        @Override
        public void selectionChanged(BubbleViewProvider selectedBubble) {
            // Only need to update the layer view if we're currently expanded for selection changes.
            if (mLayerView != null && mLayerView.isExpanded()) {
                mLayerView.showExpandedView(selectedBubble);
                if (selectedBubble instanceof Bubble) {
                    mLogger.log((Bubble) selectedBubble,
                            BubbleLogger.Event.BUBBLE_BAR_BUBBLE_SWITCHED);
                }
            }
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final BubbleData.Listener mBubbleDataListener = new BubbleData.Listener() {

        @Override
        public void applyUpdate(BubbleData.Update update) {
            ProtoLog.d(WM_SHELL_BUBBLES, "mBubbleDataListener#applyUpdate:"
                    + " added=%s removed=%b updated=%s orderChanged=%b expansionChanged=%b"
                    + " expanded=%b selectionChanged=%b selected=%s"
                    + " suppressed=%s unsupressed=%s shouldShowEducation=%b showOverflowChanged=%b"
                    + " bubbleBarLocation=%s",
                    update.addedBubble != null ? update.addedBubble.getKey() : "null",
                    !update.removedBubbles.isEmpty(),
                    update.updatedBubble != null ? update.updatedBubble.getKey() : "null",
                    update.orderChanged, update.expandedChanged, update.expanded,
                    update.selectionChanged,
                    update.selectedBubble != null ? update.selectedBubble.getKey() : "null",
                    update.suppressedBubble != null ? update.suppressedBubble.getKey() : "null",
                    update.unsuppressedBubble != null ? update.unsuppressedBubble.getKey() : "null",
                    update.shouldShowEducation, update.showOverflowChanged,
                    update.mBubbleBarLocation != null ? update.mBubbleBarLocation.toString()
                            : "null");

            ensureBubbleViewsAndWindowCreated();

            // Lazy load overflow bubbles from disk
            loadOverflowBubblesFromDisk();

            if (update.showOverflowChanged) {
                mBubbleViewCallback.bubbleOverflowChanged(!update.overflowBubbles.isEmpty());
            }

            // If bubbles in the overflow have a dot, make sure the overflow shows a dot
            updateOverflowButtonDot();

            // Update bubbles in overflow.
            if (mOverflowListener != null) {
                mOverflowListener.applyUpdate(update);
            }

            // Do removals, if any.
            ArrayList<Pair<Bubble, Integer>> removedBubbles =
                    new ArrayList<>(update.removedBubbles);
            ArrayList<Bubble> bubblesToBeRemovedFromRepository = new ArrayList<>();
            for (Pair<Bubble, Integer> removed : removedBubbles) {
                final Bubble bubble = removed.first;
                @Bubbles.DismissReason final int reason = removed.second;

                mBubbleViewCallback.removeBubble(bubble);
                if (bubble.getClientToken() != null && mBubbleMultitaskingDelegate != null) {
                    mBubbleMultitaskingDelegate.onBubbleRemoved(bubble.getClientToken(), reason);
                }

                // Leave the notification in place if we're dismissing due to user switching, or
                // because DND is suppressing the bubble. In both of those cases, we need to be able
                // to restore the bubble from the notification later.
                if (reason == DISMISS_USER_CHANGED || reason == DISMISS_NO_BUBBLE_UP) {
                    continue;
                }
                if (reason == DISMISS_NOTIF_CANCEL
                        || reason == DISMISS_SHORTCUT_REMOVED) {
                    bubblesToBeRemovedFromRepository.add(bubble);
                }
                if (!mBubbleData.hasBubbleInStackWithKey(bubble.getKey())) {
                    if (!mBubbleData.hasOverflowBubbleWithKey(bubble.getKey())
                            && (!bubble.showInShade()
                            || reason == DISMISS_NOTIF_CANCEL
                            || reason == DISMISS_GROUP_CANCELLED)) {
                        // The bubble is now gone & the notification is hidden from the shade, so
                        // time to actually remove it
                        mSysuiProxy.notifyRemoveNotification(bubble.getKey(), REASON_CANCEL);
                    } else {
                        if (bubble.isBubble()) {
                            setIsBubble(bubble, false /* isBubble */);
                        }
                        mSysuiProxy.updateNotificationBubbleButton(bubble.getKey());
                    }
                }
            }
            mDataRepository.removeBubbles(mCurrentUserId, bubblesToBeRemovedFromRepository);

            if (update.addedBubble != null) {
                mDataRepository.addBubble(mCurrentUserId, update.addedBubble);
                mBubbleViewCallback.addBubble(update.addedBubble);
            }

            if (update.updatedBubble != null) {
                mBubbleViewCallback.updateBubble(update.updatedBubble);
            }

            if (update.suppressedBubble != null) {
                mBubbleViewCallback.suppressionChanged(update.suppressedBubble, true);
            }

            if (update.unsuppressedBubble != null) {
                mBubbleViewCallback.suppressionChanged(update.unsuppressedBubble, false);
            }

            boolean collapseStack = update.expandedChanged && !update.expanded;

            // At this point, the correct bubbles are inflated in the stack.
            // Make sure the order in bubble data is reflected in bubble row.
            if (update.orderChanged) {
                mDataRepository.addBubbles(mCurrentUserId, update.bubbles);
                // if the stack is going to be collapsed, do not update pointer position
                // after reordering
                mBubbleViewCallback.bubbleOrderChanged(update.bubbles, !collapseStack);
            }

            if (collapseStack) {
                mBubbleViewCallback.expansionChanged(/* expanded= */ false);
                mSysuiProxy.requestNotificationShadeTopUi(false, TAG);
            }

            if (update.selectionChanged) {
                mBubbleViewCallback.selectionChanged(update.selectedBubble);
            }

            // Expanding? Apply this last.
            if (update.expandedChanged && update.expanded) {
                mBubbleViewCallback.expansionChanged(/* expanded= */ true);
                mSysuiProxy.requestNotificationShadeTopUi(true, TAG);
            }

            if (Flags.enableBubbleSwipeUpCleanup() && !update.removedBubbles.isEmpty()
                    && !mBubbleData.hasBubbles()) {
                // This update removed all the bubbles. Send an update to SystemUI to mark the stack
                // collapsed. This should be sent by the UI classes (BubbleStackView or
                // BubbleBarLayerView), but if we fail to send this, home gesture stops working.
                // To avoid leaving the device in a bad state, add a failsafe call here to clean
                // up the state.
                mSysuiProxy.onStackExpandChanged(false);
            }

            mSysuiProxy.notifyInvalidateNotifications("BubbleData.Listener.applyUpdate");
            updateBubbleViews();

            // Update the cached state for queries from SysUI
            mImpl.mCachedState.update(update);

            if (isShowingAsBubbleBar()) {
                BubbleBarUpdate bubbleBarUpdate = update.toBubbleBarUpdate();
                // Some updates aren't relevant to the bubble bar so check first.
                if (bubbleBarUpdate.anythingChanged() && mBubbleStateListener != null) {
                    mBubbleStateListener.onBubbleStateChange(bubbleBarUpdate);
                }
            }
        }
    };

    private void showExpandedViewForBubbleBar() {
        BubbleViewProvider selectedBubble = mBubbleData.getSelectedBubble();
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Controller.showExpandedViewForBubbleBar: bubble=%s",
                selectedBubble);
        if (selectedBubble == null) return;
        if (selectedBubble instanceof Bubble) {
            final Bubble bubble = (Bubble) selectedBubble;
            if (bubble.getPreparingTransition() != null) {
                bubble.getPreparingTransition().continueExpand();
                return;
            }
        }
        if (mLayerView == null) return;
        mLayerView.showExpandedView(selectedBubble);
    }

    private void collapseExpandedViewForBubbleBar() {
        if (mLayerView != null && mLayerView.isExpanded()) {
            if (mBubblePositioner.isImeVisible()) {
                // If we're collapsing, hide the IME
                hideCurrentInputMethod(null);
            }
            mLayerView.collapse();
        }
    }

    private void updateOverflowButtonDot() {
        BubbleOverflow overflow = mBubbleData.getOverflow();
        if (overflow == null) return;

        for (Bubble b : mBubbleData.getOverflowBubbles()) {
            if (b.showDot()) {
                overflow.setShowDot(true);
                return;
            }
        }
        overflow.setShowDot(false);
    }

    private boolean handleDismissalInterception(BubbleEntry entry,
            @Nullable List<BubbleEntry> children, IntConsumer removeCallback) {
        if (isSummaryOfBubbles(entry)) {
            handleSummaryDismissalInterception(entry, children, removeCallback);
        } else {
            Bubble bubble = mBubbleData.getBubbleInStackWithKey(entry.getKey());
            if (bubble == null || !entry.isBubble()) {
                bubble = mBubbleData.getOverflowBubbleWithKey(entry.getKey());
            }
            if (bubble == null) {
                return false;
            }
            bubble.setSuppressNotification(true);
            bubble.setShowDot(false /* show */);
        }
        // Update the shade
        mSysuiProxy.notifyInvalidateNotifications("BubbleController.handleDismissalInterception");
        return true;
    }

    private boolean isSummaryOfBubbles(BubbleEntry entry) {
        String groupKey = entry.getStatusBarNotification().getGroupKey();
        ArrayList<Bubble> bubbleChildren = getBubblesInGroup(groupKey);
        boolean isSuppressedSummary = mBubbleData.isSummarySuppressed(groupKey)
                && mBubbleData.getSummaryKey(groupKey).equals(entry.getKey());
        boolean isSummary = entry.getStatusBarNotification().getNotification().isGroupSummary();
        return (isSuppressedSummary || isSummary) && !bubbleChildren.isEmpty();
    }

    private void handleSummaryDismissalInterception(
            BubbleEntry summary, @Nullable List<BubbleEntry> children, IntConsumer removeCallback) {
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                final BubbleEntry child = children.get(i);
                if (mBubbleData.hasAnyBubbleWithKey(child.getKey())) {
                    // Suppress the bubbled child
                    // As far as group manager is concerned, once a child is no longer shown
                    // in the shade, it is essentially removed.
                    final Bubble bubbleChild = mBubbleData.getAnyBubbleWithKey(child.getKey());
                    if (bubbleChild != null) {
                        bubbleChild.setSuppressNotification(true);
                        bubbleChild.setShowDot(false /* show */);
                    }
                } else {
                    // non-bubbled children can be removed
                    removeCallback.accept(i);
                }
            }
        }

        // And since all children are removed, remove the summary.
        removeCallback.accept(-1);

        mBubbleData.addSummaryToSuppress(summary.getStatusBarNotification().getGroupKey(),
                summary.getKey());
    }

    /**
     * Updates the visibility of the bubbles based on current state.
     * Does not un-bubble, just hides or un-hides the views themselves.
     *
     * Updates view description for TalkBack focus.
     * Updates bubbles' icon views clickable states (when floating).
     */
    public void updateBubbleViews() {
        if (mStackView == null && mLayerView == null) {
            return;
        }
        ProtoLog.v(WM_SHELL_BUBBLES, "updateBubbleViews mIsStatusBarShade=%s hasBubbles=%s",
                mIsStatusBarShade, hasBubbles());
        if (!mIsStatusBarShade) {
            // Bubbles don't appear when the device is locked.
            if (mStackView != null) {
                mStackView.setVisibility(INVISIBLE);
            }
            if (mLayerView != null) {
                mLayerView.setVisibility(INVISIBLE);
            }
        } else if (hasBubbles()) {
            // If we're unlocked, show the stack if we have bubbles. If we don't have bubbles, the
            // stack will be set to INVISIBLE in onAllBubblesAnimatedOut after the bubbles animate
            // out.
            if (mStackView != null) {
                mStackView.setVisibility(VISIBLE);
            }
            if (mLayerView != null) {
                mLayerView.setVisibility(VISIBLE);
            }
        }

        if (mStackView != null) {
            mStackView.updateContentDescription();
            mStackView.updateBubblesAcessibillityStates();
        } else if (mLayerView != null) {
            // TODO(b/273313561): handle a11y for BubbleBarLayerView
        }
    }

    /**
     * Returns whether the stack is animating or not.
     */
    public boolean isStackAnimating() {
        return mStackView != null
                && (mStackView.isExpansionAnimating()
                || mStackView.isSwitchAnimating());
    }

    @VisibleForTesting
    @Nullable
    public BubbleStackView getStackView() {
        return mStackView;
    }

    @VisibleForTesting
    @Nullable
    public BubbleBarLayerView getLayerView() {
        return mLayerView;
    }

    @Nullable
    public ActivityManager.RunningTaskInfo getAppBubbleRootTaskInfo() {
        return mAppBubbleRootTaskInfo;
    }

    /**
     * Returns the id of the display to which the current Bubble view is attached if it is currently
     * showing, {@link INVALID_DISPLAY} otherwise.
     */
    @VisibleForTesting
    public int getCurrentViewDisplayId() {
        if (isShowingAsBubbleBar() && mLayerView != null && mLayerView.getDisplay() != null) {
            return mLayerView.getDisplay().getDisplayId();
        }

        if (!isShowingAsBubbleBar() && mStackView != null && mStackView.getDisplay() != null) {
            return mStackView.getDisplay().getDisplayId();
        }

        return INVALID_DISPLAY;
    }

    /**
     * Check if notification panel is in an expanded state.
     * Makes a call to System UI process and delivers the result via {@code callback} on the
     * WM Shell main thread.
     *
     * @param callback callback that has the result of notification panel expanded state
     */
    public void isNotificationPanelExpanded(Consumer<Boolean> callback) {
        mSysuiProxy.isNotificationPanelExpand(expanded ->
                mMainExecutor.execute(() -> callback.accept(expanded)));
    }

    /**
     * Show bubbles UI when triggered via shortcut.
     *
     * <p>When there are bubbles visible, expands the top-most bubble. When there are no bubbles
     * visible, opens the bubbles overflow UI.
     */
    public void showBubblesFromShortcut() {
        if (isStackExpanded()) {
            ProtoLog.v(WM_SHELL_BUBBLES, "showBubblesFromShortcut: stack visible, skip");
            return;
        }
        if (mBubbleData.getSelectedBubble() != null) {
            ProtoLog.v(WM_SHELL_BUBBLES, "showBubblesFromShortcut: open selected bubble");
            expandStackWithSelectedBubble();
            return;
        }
        BubbleViewProvider bubbleToSelect = CollectionUtils.firstOrNull(mBubbleData.getBubbles());
        if (bubbleToSelect == null) {
            ProtoLog.v(WM_SHELL_BUBBLES, "showBubblesFromShortcut: no bubbles");
            // make sure overflow bubbles are loaded
            loadOverflowBubblesFromDisk();
            bubbleToSelect = mBubbleData.getOverflow();
        }
        ProtoLog.v(WM_SHELL_BUBBLES, "showBubblesFromShortcut: select and open %s",
                bubbleToSelect.getKey());
        mBubbleData.setSelectedBubbleAndExpandStack(bubbleToSelect);
    }

    private void moveDraggedBubbleToFullscreen(String key, Point dropLocation) {
        Bubble b = mBubbleData.getBubbleInStackWithKey(key);
        mBubbleTransitions.startDraggedBubbleIconToFullscreen(b, dropLocation);
    }

    private boolean isDeviceLocked() {
        return !mIsStatusBarShade;
    }

    /**
     * Description of current bubble state.
     */
    private void dump(PrintWriter pw, String prefix) {
        pw.print(prefix); pw.println("BubbleController state:");
        pw.print(prefix); pw.println("  currentUserId= " + mCurrentUserId);
        pw.print(prefix); pw.println("  isStatusBarShade= " + mIsStatusBarShade);
        pw.print(prefix); pw.println("  isShowingAsBubbleBar= " + isShowingAsBubbleBar());
        pw.print(prefix); pw.println("  launcherHasBubbleBar= " + mLauncherHasBubbleBar);
        pw.print(prefix); pw.println("  bubbleStateListenerSet= " + (mBubbleStateListener != null));
        pw.print(prefix); pw.println("  stackViewSet= " + (mStackView != null));
        pw.print(prefix); pw.println("  layerViewSet= " + (mLayerView != null));
        pw.print(prefix); pw.println("  isImeVisible= " + mBubblePositioner.isImeVisible());
        pw.println();

        mBubbleData.dump(pw);
        pw.println();

        if (mStackView != null) {
            mStackView.dump(pw);
        }
        pw.println();

        mImpl.mCachedState.dump(pw);

        pw.println();
        mBubbleTransitions.mTaskViewTransitions.dump(pw);
    }

    /**
     * Whether an intent is properly configured to display in a
     * {@link TaskView}.
     *
     * Keep checks in sync with BubbleExtractor#canLaunchInTaskView. Typically
     * that should filter out any invalid bubbles, but should protect SysUI side just in case.
     *
     * @param context the context to use.
     * @param entry   the entry to bubble.
     */
    boolean canLaunchInTaskView(Context context, BubbleEntry entry) {
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) return true;
        PendingIntent intent = entry.getBubbleMetadata() != null
                ? entry.getBubbleMetadata().getIntent()
                : null;
        if (entry.getBubbleMetadata() != null
                && entry.getBubbleMetadata().getShortcutId() != null) {
            return true;
        }
        if (intent == null) {
            Log.w(TAG, "Unable to create bubble -- no intent: " + entry.getKey());
            return false;
        }
        PackageManager packageManager = getPackageManagerForUser(
                context, entry.getStatusBarNotification().getUser().getIdentifier());
        return mResizabilityChecker.isResizableActivity(intent.getIntent(), packageManager,
                entry.getKey());
    }

    /** Gets the {@link PackageManager} for the user's context. */
    public static PackageManager getPackageManagerForUser(Context context, int userId) {
        Context contextForUser = context;
        // UserHandle defines special userId as negative values, e.g. USER_ALL
        if (userId >= 0) {
            try {
                // Create a context for the correct user so if a package isn't installed
                // for user 0 we can still load information about the package.
                contextForUser =
                        context.createPackageContextAsUser(context.getPackageName(),
                                Context.CONTEXT_RESTRICTED,
                                new UserHandle(userId));
            } catch (PackageManager.NameNotFoundException e) {
                // Shouldn't fail to find the package name for system ui.
            }
        }
        return contextForUser.getPackageManager();
    }

    /** {@link ImeListener} that dispatches IME visibility updates to the stack. */
    private class BubblesImeListener extends ImeListener implements
            DisplayImeController.ImePositionProcessor {

        BubblesImeListener(DisplayController displayController, int displayId) {
            super(displayController, displayId);
        }

        @Override
        protected void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
            if (getDisplayId() != mContext.getDisplayId()) {
                return;
            }
            // the imeHeight here is actually the ime inset; it only includes the part of the ime
            // that overlaps with the Bubbles window. adjust it to include the bottom screen inset,
            // so we have the total height of the ime.
            int totalImeHeight = imeHeight + mBubblePositioner.getInsets().bottom;
            mBubblePositioner.setImeVisible(imeVisible, totalImeHeight);
            if (mStackView != null) {
                mStackView.setImeVisible(imeVisible);
                if (!imeVisible && mOnImeHidden != null) {
                    mOnImeHidden.run();
                    mOnImeHidden = null;
                }
            }
        }

        @Override
        public int onImeStartPositioning(int displayId, int hiddenTop, int shownTop,
                boolean showing, boolean isFloating, SurfaceControl.Transaction t) {
            if (mContext.getDisplayId() != displayId) {
                return IME_ANIMATION_DEFAULT;
            }

            if (showing) {
                mBubblePositioner.setImeVisible(true, hiddenTop - shownTop);
            } else {
                mBubblePositioner.setImeVisible(false, 0);
            }
            if (mStackView != null) {
                mStackView.setImeVisible(showing);
            }

            return IME_ANIMATION_DEFAULT;
        }

        @Override
        public void onImePositionChanged(int displayId, int imeTop, SurfaceControl.Transaction t) {
            if (mContext.getDisplayId() != displayId) {
                return;
            }
            if (mLayerView != null) {
                mLayerView.onImeTopChanged(imeTop);
            }
        }
    }

    /**
     * The interface for calls from outside the host process.
     */
    @BinderThread
    private class IBubblesImpl extends IBubbles.Stub implements ExternalInterfaceBinder {
        private BubbleController mController;
        private final SingleInstanceRemoteListener<BubbleController, IBubblesListener> mListener;
        private final Bubbles.BubbleStateListener mBubbleListener =
                new Bubbles.BubbleStateListener() {
                    @Override
                    public void onBubbleStateChange(BubbleBarUpdate update) {
                        Bundle b = new Bundle();
                        b.setClassLoader(BubbleBarUpdate.class.getClassLoader());
                        b.putParcelable(BubbleBarUpdate.BUNDLE_KEY, update);
                        mListener.call(l -> l.onBubbleStateChange(b));
                    }

                    @Override
                    public void animateBubbleBarLocation(BubbleBarLocation location) {
                        mListener.call(l -> l.animateBubbleBarLocation(location));
                    }

                    @Override
                    public void showBubbleBarPillowAt(@Nullable BubbleBarLocation location) {
                        mListener.call(l -> l.showBubbleBarPillowAt(location));
                    }
                };

        IBubblesImpl(BubbleController controller) {
            mController = controller;
            mListener = new SingleInstanceRemoteListener<>(mController,
                    c -> c.registerBubbleStateListener(mBubbleListener),
                    c -> c.unregisterBubbleStateListener());
        }

        /**
         * Invalidates this instance, preventing future calls from updating the controller.
         */
        @Override
        public void invalidate() {
            mController = null;
            // Unregister the listeners to ensure any binder death recipients are unlinked
            mListener.unregister();
        }

        @Override
        public void registerBubbleListener(IBubblesListener listener) {
            mMainExecutor.execute(() -> mListener.register(listener));
        }

        @Override
        public void unregisterBubbleListener(IBubblesListener listener) {
            mMainExecutor.execute(mListener::unregister);
        }

        @Override
        public void showShortcutBubble(ShortcutInfo info, @Nullable BubbleBarLocation location) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.showShortcutBubble: info=%s loc=%s",
                    info, location);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "showShortcutBubble",
                    (controller) -> controller.expandStackAndSelectBubble(info, location));
        }

        @Override
        public void showAppBubble(Intent intent, UserHandle user,
                @Nullable BubbleBarLocation location) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.showAppBubble: intent=%s user=%s loc=%s",
                    intent, user, location);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "showAppBubble",
                    (controller) -> controller.expandStackAndSelectBubble(intent, user, location));
        }

        @Override
        public void showBubble(String key, int bubbleBarTopToScreenBottom) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                    "IBubbles.showBubble: key=%s bubbleBarTopToScreenBottom=%d",
                    key, bubbleBarTopToScreenBottom);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "showBubble",
                    (controller) ->
                            controller.expandStackAndSelectBubbleFromLauncher(
                                key, bubbleBarTopToScreenBottom));
        }

        @Override
        public void removeAllBubbles() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.removeAllBubbles");
            executeRemoteCallWithTaskPermission(
                    mController,
                    "removeAllBubbles",
                    (controller) -> controller.removeAllBubbles(Bubbles.DISMISS_USER_GESTURE));
        }

        @Override
        public void collapseBubbles() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.collapseBubbles");
            executeRemoteCallWithTaskPermission(
                    mController,
                    "collapseBubbles",
                    (controller) -> {
                        if (mBubbleData.getSelectedBubble() instanceof Bubble) {
                            if (((Bubble) mBubbleData.getSelectedBubble()).getPreparingTransition()
                                    != null) {
                                // Currently preparing a transition which will, itself, collapse the
                                // bubble.
                                // For transition preparation, the timing of bubble-collapse must be
                                // in sync with the rest of the set-up.
                                return;
                            }
                        }
                        controller.collapseStack();
                    });
        }

        @Override
        public void startBubbleDrag(String bubbleKey) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.startBubbleDrag: key=%s", bubbleKey);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "startBubbleDrag",
                    (controller) -> controller.startBubbleDrag(bubbleKey));
        }

        @Override
        public void stopBubbleDrag(BubbleBarLocation location, int bubbleBarTopToScreenBottom) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                    "IBubbles.stopBubbleDrag: log=%s bubbleBarTopToScreenBottom=%d",
                    location, bubbleBarTopToScreenBottom);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "stopBubbleDrag",
                    (controller) ->
                        controller.stopBubbleDrag(location, bubbleBarTopToScreenBottom));
        }

        @Override
        public void dragBubbleToDismiss(String key, long timestamp) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.dragBubbleToDismiss: key=%s time=%d",
                    key, timestamp);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "dragBubbleToDismiss",
                    (controller) -> controller.dragBubbleToDismiss(key, timestamp));
        }

        @Override
        public void showUserEducation(int positionX, int positionY) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.showUserEducation: pos=[%d, %d]",
                    positionX, positionY);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "showUserEducation",
                    (controller) -> controller.showUserEducation(new Point(positionX, positionY)));
        }

        @Override
        public void setBubbleBarLocation(BubbleBarLocation location,
                @UpdateSource int source) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.setBubbleBarLocation: loc=%s src=%d",
                    location, source);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "setBubbleBarLocation",
                    (controller) -> controller.setBubbleBarLocation(location, source));
        }

        @Override
        public void updateBubbleBarTopToScreenBottom(int bubbleBarTopToScreenBottom) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                    "IBubbles.updateBubbleBarTopOnScreen: bubbleBarTopToScreenBottom=%d",
                    bubbleBarTopToScreenBottom);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "updateBubbleBarTopToScreenBottom",
                    (controller) -> {
                        mBubblePositioner.updateBubbleBarTopOnScreen(bubbleBarTopToScreenBottom);
                        if (isSelectedBubbleConvertingMode()) {
                            // if we're in the process of converting the selected bubble to bar mode
                            // we just received an updated bubble bar relative position so we can
                            // now continue converting the bubble
                            ((Bubble) mBubbleData.getSelectedBubble()).getPreparingTransition()
                                    .continueConvert(mLayerView);
                        }
                        if (mLayerView != null) mLayerView.updateExpandedView();
                    });
        }

        @Override
        public void showExpandedView() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.showExpandedView");
            executeRemoteCallWithTaskPermission(
                    mController,
                    "showExpandedView",
                    (controller) -> {
                        if (mLayerView != null) {
                            showExpandedViewForBubbleBar();
                        }
                    });
        }

        @Override
        public void moveDraggedBubbleToFullscreen(String key, Point dropLocation) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "IBubbles.moveDraggedBubbleToFullscreen: key=%s "
                            + "loc=%s", key, dropLocation);
            executeRemoteCallWithTaskPermission(
                    mController,
                    "moveDraggedBubbleToFullscreen",
                    (controller) -> controller.moveDraggedBubbleToFullscreen(key, dropLocation));
        }

        @Override
        public void setHasBubbleBar(boolean hasBubbleBar) {
            executeRemoteCallWithTaskPermission(
                    mController,
                    "setHasBubbleBar",
                    (controller) -> controller.setLauncherHasBubbleBar(hasBubbleBar));
        }
    }

    private class BubblesImpl implements Bubbles {
        // Up-to-date cached state of bubbles data for SysUI to query from the calling thread
        @VisibleForTesting
        public class CachedState {
            private boolean mIsStackExpanded;
            private String mSelectedBubbleKey;
            private HashSet<String> mSuppressedBubbleKeys = new HashSet<>();
            private HashMap<String, String> mSuppressedGroupToNotifKeys = new HashMap<>();
            private HashMap<String, Bubble> mShortcutIdToBubble = new HashMap<>();

            private HashMap<String, Integer> mNoteBubbleTaskIds = new HashMap();

            private ArrayList<Bubble> mTmpBubbles = new ArrayList<>();

            /**
             * Updates the cached state based on the last full BubbleData change.
             */
            synchronized void update(BubbleData.Update update) {
                if (update.selectionChanged) {
                    mSelectedBubbleKey = update.selectedBubble != null
                            ? update.selectedBubble.getKey()
                            : null;
                }
                if (update.expandedChanged) {
                    mIsStackExpanded = update.expanded;
                }
                if (update.suppressedSummaryChanged) {
                    String summaryKey =
                            mBubbleData.getSummaryKey(update.suppressedSummaryGroup);
                    if (summaryKey != null) {
                        mSuppressedGroupToNotifKeys.put(update.suppressedSummaryGroup, summaryKey);
                    } else {
                        mSuppressedGroupToNotifKeys.remove(update.suppressedSummaryGroup);
                    }
                }

                mTmpBubbles.clear();
                mTmpBubbles.addAll(update.bubbles);
                mTmpBubbles.addAll(update.overflowBubbles);

                mSuppressedBubbleKeys.clear();
                mShortcutIdToBubble.clear();
                mNoteBubbleTaskIds.clear();
                for (Bubble b : mTmpBubbles) {
                    mShortcutIdToBubble.put(b.getShortcutId(), b);
                    updateBubbleSuppressedState(b);

                    if (b.isNote()) {
                        mNoteBubbleTaskIds.put(b.getKey(), b.getTaskId());
                    }
                }
            }

            /** Sets the note bubble's taskId which is cached for SysUI. */
            synchronized void setNoteBubbleTaskId(String key, int taskId) {
                mNoteBubbleTaskIds.put(key, taskId);
            }

            /**
             * Updates a specific bubble suppressed state.  This is used mainly because notification
             * suppression changes don't go through the same BubbleData update mechanism.
             */
            synchronized void updateBubbleSuppressedState(Bubble b) {
                if (!b.showInShade()) {
                    mSuppressedBubbleKeys.add(b.getKey());
                } else {
                    mSuppressedBubbleKeys.remove(b.getKey());
                }
            }

            public synchronized boolean isStackExpanded() {
                return mIsStackExpanded;
            }

            public synchronized boolean isBubbleExpanded(String key) {
                return mIsStackExpanded && key.equals(mSelectedBubbleKey);
            }

            public synchronized boolean isBubbleNotificationSuppressedFromShade(String key,
                    String groupKey) {
                return mSuppressedBubbleKeys.contains(key)
                        || (mSuppressedGroupToNotifKeys.containsKey(groupKey)
                        && key.equals(mSuppressedGroupToNotifKeys.get(groupKey)));
            }

            @Nullable
            public synchronized Bubble getBubbleWithShortcutId(String id) {
                return mShortcutIdToBubble.get(id);
            }

            synchronized void dump(PrintWriter pw) {
                pw.println("BubbleImpl.CachedState state:");

                pw.println("mIsStackExpanded: " + mIsStackExpanded);
                pw.println("mSelectedBubbleKey: " + mSelectedBubbleKey);

                pw.println("mSuppressedBubbleKeys: " + mSuppressedBubbleKeys.size());
                for (String key : mSuppressedBubbleKeys) {
                    pw.println("   suppressing: " + key);
                }

                pw.print("mSuppressedGroupToNotifKeys: ");
                pw.println(mSuppressedGroupToNotifKeys.size());
                for (String key : mSuppressedGroupToNotifKeys.keySet()) {
                    pw.println("   suppressing: " + key);
                }

                pw.println("mNoteBubbleTaskIds: " + mNoteBubbleTaskIds.values());
            }
        }

        private CachedState mCachedState = new CachedState();

        @Override
        public boolean isBubbleNotificationSuppressedFromShade(String key, String groupKey) {
            return mCachedState.isBubbleNotificationSuppressedFromShade(key, groupKey);
        }

        @Override
        public boolean isBubbleExpanded(String key) {
            return mCachedState.isBubbleExpanded(key);
        }

        @Override
        @Nullable
        public Bubble getBubbleWithShortcutId(String shortcutId) {
            return mCachedState.getBubbleWithShortcutId(shortcutId);
        }

        @Override
        public void expandStackAndSelectBubble(BubbleEntry entry) {
            mMainExecutor.execute(() -> {
                BubbleController.this.expandStackAndSelectBubble(entry);
            });
        }

        @Override
        public void expandStackAndSelectBubble(ShortcutInfo info) {
            mMainExecutor.execute(() ->
                    BubbleController.this
                            .expandStackAndSelectBubble(info, /* bubbleBarLocation = */ null)
            );
        }

        @Override
        public void expandStackAndSelectBubble(Bubble bubble) {
            mMainExecutor.execute(() -> {
                BubbleController.this.expandStackAndSelectBubble(bubble);
            });
        }

        @Override
        public void showOrHideNoteBubble(Intent intent, UserHandle user, @Nullable Icon icon) {
            mMainExecutor.execute(
                    () -> BubbleController.this.showOrHideNotesBubble(intent, user, icon));
        }

        @Override
        public boolean isNoteBubbleTaskId(int taskId) {
            return mCachedState.mNoteBubbleTaskIds.values().contains(taskId);
        }

        @Override
        @Nullable
        public SynchronousScreenCaptureListener getScreenshotExcludingBubble(int displayId) {
            SynchronousScreenCaptureListener screenCaptureListener =
                    ScreenCapture.createSyncCaptureListener();

            mMainExecutor.execute(
                    () -> BubbleController.this.getScreenshotExcludingBubble(displayId,
                            screenCaptureListener));

            return screenCaptureListener;
        }

        @Override
        public boolean handleDismissalInterception(BubbleEntry entry,
                @Nullable List<BubbleEntry> children, IntConsumer removeCallback,
                Executor callbackExecutor) {
            IntConsumer cb = removeCallback != null
                    ? (index) -> callbackExecutor.execute(() -> removeCallback.accept(index))
                    : null;
            return mMainExecutor.executeBlockingForResult(() -> {
                return BubbleController.this.handleDismissalInterception(entry, children, cb);
            }, Boolean.class);
        }

        @Override
        public void setSysuiProxy(SysuiProxy proxy) {
            mMainExecutor.execute(() -> {
                BubbleController.this.setSysuiProxy(proxy);
            });
        }

        @Override
        public void setExpandListener(BubbleExpandListener listener) {
            mMainExecutor.execute(() -> {
                BubbleController.this.setExpandListener(listener);
            });
        }

        @Override
        public void onEntryAdded(BubbleEntry entry) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onEntryAdded(entry);
            });
        }

        @Override
        public void onEntryUpdated(BubbleEntry entry, boolean shouldBubbleUp, boolean fromSystem) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onEntryUpdated(entry, shouldBubbleUp, fromSystem);
            });
        }

        @Override
        public void onEntryRemoved(BubbleEntry entry) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onEntryRemoved(entry);
            });
        }

        @Override
        public void onRankingUpdated(RankingMap rankingMap,
                HashMap<String, Pair<BubbleEntry, Boolean>> entryDataByKey) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onRankingUpdated(rankingMap, entryDataByKey);
            });
        }

        @Override
        public void onNotificationChannelModified(String pkg,
                UserHandle user, NotificationChannel channel, int modificationType) {
            // Bubbles only cares about updates or deletions.
            if (modificationType == NOTIFICATION_CHANNEL_OR_GROUP_UPDATED
                    || modificationType == NOTIFICATION_CHANNEL_OR_GROUP_DELETED) {
                mMainExecutor.execute(() -> {
                    BubbleController.this.onNotificationChannelModified(pkg, user, channel,
                            modificationType);
                });
            }
        }

        @Override
        public void onStatusBarVisibilityChanged(boolean visible) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onStatusBarVisibilityChanged(visible);
            });
        }

        @Override
        public void onZenStateChanged() {
            mMainExecutor.execute(() -> {
                BubbleController.this.onZenStateChanged();
            });
        }

        @Override
        public void onStatusBarStateChanged(boolean isShade) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onStatusBarStateChanged(isShade);
            });
        }

        @Override
        public void onUserChanged(int newUserId) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onUserChanged(newUserId);
            });
        }

        @Override
        public void onCurrentProfilesChanged(SparseArray<UserInfo> currentProfiles) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onCurrentProfilesChanged(currentProfiles);
            });
        }

        @Override
        public void onUserRemoved(int removedUserId) {
            mMainExecutor.execute(() -> {
                BubbleController.this.onUserRemoved(removedUserId);
            });
        }

        @Override
        public void onNotificationPanelExpandedChanged(boolean expanded) {
            mMainExecutor.execute(
                    () -> BubbleController.this.onNotificationPanelExpandedChanged(expanded));
        }

        @Override
        public void onSensitiveNotificationProtectionStateChanged(
                boolean sensitiveNotificationProtectionActive) {
            mMainExecutor.execute(
                    () -> BubbleController.this.onSensitiveNotificationProtectionStateChanged(
                            sensitiveNotificationProtectionActive));
        }

        @Override
        public boolean canShowBubbleNotification() {
            // in bubble bar mode, when the IME is visible we can't animate new bubbles.
            if (BubbleController.this.isShowingAsBubbleBar()) {
                return !BubbleController.this.mBubblePositioner.isImeVisible();
            }
            return true;
        }
    }

    /**
     * Bubble data that is stored per user.
     * Used to store and restore active bubbles during user switching.
     */
    private static class UserBubbleData {
        private final Map<String, Boolean> mKeyToShownInShadeMap = new HashMap<>();

        /**
         * Add bubble key and whether it should be shown in notification shade
         */
        void add(String key, boolean shownInShade) {
            mKeyToShownInShadeMap.put(key, shownInShade);
        }

        /**
         * Get all bubble keys stored for this user
         */
        Set<String> getKeys() {
            return mKeyToShownInShadeMap.keySet();
        }

        /**
         * Check if this bubble with the given key should be shown in the notification shade
         */
        boolean isShownInShade(String key) {
            return mKeyToShownInShadeMap.get(key);
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    class BubbleTaskViewController implements TaskViewController {
        private final TaskViewTransitions mBaseTransitions;

        BubbleTaskViewController(@NonNull TaskViewTransitions baseTransitions) {
            mBaseTransitions = baseTransitions;
        }

        @Override
        public void registerTaskView(TaskViewTaskController tv) {
            mBaseTransitions.registerTaskView(tv);
        }

        @Override
        public void unregisterTaskView(TaskViewTaskController tv) {
            mBaseTransitions.unregisterTaskView(tv);
        }

        @Override
        public void startShortcutActivity(@NonNull TaskViewTaskController destination,
                @NonNull ShortcutInfo shortcut, @NonNull ActivityOptions options,
                @Nullable Rect launchBounds) {
            mBaseTransitions.startShortcutActivity(destination, shortcut, options, launchBounds);
        }

        @Override
        public void startActivity(@NonNull TaskViewTaskController destination,
                @NonNull PendingIntent pendingIntent, @Nullable Intent fillInIntent,
                @NonNull ActivityOptions options, @Nullable Rect launchBounds) {
            mBaseTransitions.startActivity(destination, pendingIntent, fillInIntent,
                    options, launchBounds);
        }

        @Override
        public void startRootTask(@NonNull TaskViewTaskController destination,
                ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash,
                @Nullable WindowContainerTransaction wct) {
            mBaseTransitions.startRootTask(destination, taskInfo, leash, wct);
        }

        @Override
        public void removeTaskView(@NonNull TaskViewTaskController taskView,
                @Nullable WindowContainerToken taskToken) {
            mBaseTransitions.removeTaskView(taskView, taskToken);
        }

        @Override
        public void moveTaskViewToFullscreen(@NonNull TaskViewTaskController taskView) {
            final TaskInfo tinfo = taskView.getTaskInfo();
            if (tinfo == null) {
                return;
            }
            Bubble bub = mBubbleData.getBubbleInStackWithTaskId(tinfo.taskId);
            if (bub == null) {
                return;
            }
            mBubbleTransitions.startConvertFromBubble(bub, tinfo);
        }

        @Override
        public void setTaskViewVisible(TaskViewTaskController taskView, boolean visible) {
            if (BubbleAnythingFlagHelper.enableCreateAnyBubbleWithForceExcludedFromRecents()) {
                // When removing the last bubble, BubbleData has already removed the bubble from
                // the stack before this call occurs. Without this check, the TO_BACK transition
                // would trigger DesktopModeWindowDecorViewModel#onTaskChanging, which
                // incorrectly creates desktop mode window decorations for the removed bubble task
                // since AppHandleAndHeaderVisibilityHelper#allowedForTask can't find the task in
                // the bubble stack anymore. These decorations then "leak" because the task will be
                // closed in the subsequent CLOSE transition. See b/416655338 for more details.
                if (!visible && !mBubbleData.hasBubbleInStackWithTaskView(taskView)) {
                    return;
                }
                // Use reorder instead of always-on-top with hidden.
                mBaseTransitions.setTaskViewVisible(taskView, visible, true /* reorder */,
                        false /* toggleHiddenOnReorder */);
            } else {
                mBaseTransitions.setTaskViewVisible(taskView, visible);
            }
        }

        @Override
        public void setTaskBounds(TaskViewTaskController taskView, Rect boundsOnScreen) {
            mBaseTransitions.setTaskBounds(taskView, boundsOnScreen);
        }
    }
}
