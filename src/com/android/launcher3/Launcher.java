/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.ActivityInfo.CONFIG_UI_MODE;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_FOLDER;
import static com.android.launcher3.AbstractFloatingView.TYPE_ICON_SURFACE;
import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.AbstractFloatingView.TYPE_SNACKBAR;
import static com.android.launcher3.AbstractFloatingView.getTopOpenViewWithType;
import static com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY;
import static com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_WIDGET_TRANSITION;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.FLAG_MULTI_PAGE;
import static com.android.launcher3.LauncherState.FLAG_NON_INTERACTIVE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.NO_OFFSET;
import static com.android.launcher3.LauncherState.NO_SCALE;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.getSupportedActions;
import static com.android.launcher3.anim.Interpolators.EMPHASIZED;
import static com.android.launcher3.config.FeatureFlags.FOLDABLE_SINGLE_PAGE;
import static com.android.launcher3.config.FeatureFlags.MULTI_SELECT_EDIT_MODE;
import static com.android.launcher3.config.FeatureFlags.SHOW_DOT_PAGINATION;
import static com.android.launcher3.logging.StatsLogManager.EventEnum;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_ENTRY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_ENTRY_WITH_DEVICE_SEARCH;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_EXIT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ONRESUME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ONSTOP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SWIPELEFT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SWIPERIGHT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGET_RECONFIGURED;
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE;
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC;
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC;
import static com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.LatencyType.COLD;
import static com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.LatencyType.COLD_DEVICE_REBOOTING;
import static com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.LatencyType.WARM;
import static com.android.launcher3.model.ItemInstallQueue.FLAG_ACTIVITY_PAUSED;
import static com.android.launcher3.model.ItemInstallQueue.FLAG_DRAG_AND_DROP;
import static com.android.launcher3.popup.SystemShortcut.APP_INFO;
import static com.android.launcher3.popup.SystemShortcut.INSTALL;
import static com.android.launcher3.popup.SystemShortcut.WIDGETS;
import static com.android.launcher3.states.RotationHelper.REQUEST_LOCK;
import static com.android.launcher3.states.RotationHelper.REQUEST_NONE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.ItemInfoMatcher.forFolderMatch;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Parcelable;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.OvershootInterpolator;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.accessibility.BaseAccessibilityDelegate.LauncherAction;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.allapps.BaseSearchConfig;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.anim.PropertyListBuilder;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.celllayout.CellPosMapper.TwoPanelCellPosMapper;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.LauncherDragController;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderGridOrganizer;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.WorkspaceContainer;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StartupLatencyLogger;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.pageindicators.WorkspacePageIndicator;
import com.android.launcher3.pm.PinRequestHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.AllAppsSwipeController;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.ActivityResultInfo;
import com.android.launcher3.util.ActivityTracker;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.launcher3.util.ScreenOnTracker.ScreenOnListener;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ComposeInitializer;
import com.android.launcher3.views.FloatingIconView;
import com.android.launcher3.views.FloatingSurfaceView;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingAppWidgetHostView;
import com.android.launcher3.widget.WidgetAddFlowHandler;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.custom.CustomWidgetManager;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.picker.WidgetsFullSheet;
import com.android.systemui.plugins.LauncherOverlayPlugin;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.shared.LauncherExterns;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Default launcher application.
 */
public class Launcher extends StatefulActivity<LauncherState>
        implements LauncherExterns, Callbacks, InvariantDeviceProfile.OnIDPChangeListener,
        PluginListener<LauncherOverlayPlugin> {
    public static final String TAG = "Launcher";

    public static final ActivityTracker<Launcher> ACTIVITY_TRACKER = new ActivityTracker<>();

    static final boolean LOGD = false;

    static final boolean DEBUG_STRICT_MODE = false;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 5;

    private static final int REQUEST_PICK_APPWIDGET = 9;

    private static final int REQUEST_BIND_APPWIDGET = 11;
    public static final int REQUEST_BIND_PENDING_APPWIDGET = 12;
    public static final int REQUEST_RECONFIGURE_APPWIDGET = 13;

    private static final float BOUNCE_ANIMATION_TENSION = 1.3f;

    /**
     * IntentStarter uses request codes starting with this. This must be greater than all activity
     * request codes used internally.
     */
    protected static final int REQUEST_LAST = 100;

    // Type: int
    protected static final String RUNTIME_STATE = "launcher.state";
    // Type: PendingRequestArgs
    private static final String RUNTIME_STATE_PENDING_REQUEST_ARGS = "launcher.request_args";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_REQUEST_CODE = "launcher.request_code";
    // Type: ActivityResultInfo
    private static final String RUNTIME_STATE_PENDING_ACTIVITY_RESULT = "launcher.activity_result";
    // Type: SparseArray<Parcelable>
    private static final String RUNTIME_STATE_WIDGET_PANEL = "launcher.widget_panel";
    // Type int[]
    private static final String RUNTIME_STATE_CURRENT_SCREEN_IDS = "launcher.current_screen_ids";

    // Type PendingSplitSelectInfo<Parcelable>
    protected static final String PENDING_SPLIT_SELECT_INFO = "launcher.pending_split_select_info";

    public static final String INTENT_ACTION_ALL_APPS_TOGGLE =
            "launcher.intent_action_all_apps_toggle";

    public static final String ON_CREATE_EVT = "Launcher.onCreate";
    public static final String ON_START_EVT = "Launcher.onStart";
    public static final String ON_RESUME_EVT = "Launcher.onResume";
    public static final String ON_NEW_INTENT_EVT = "Launcher.onNewIntent";

    private static boolean sIsNewProcess = true;

    private StateManager<LauncherState> mStateManager;

    private static final int ON_ACTIVITY_RESULT_ANIMATION_DELAY = 500;

    // How long to wait before the new-shortcut animation automatically pans the workspace
    @VisibleForTesting public static final int NEW_APPS_PAGE_MOVE_DELAY = 500;
    private static final int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 5;
    @Thunk @VisibleForTesting public static final int NEW_APPS_ANIMATION_DELAY = 500;

    private static final String DISPLAY_WORKSPACE_TRACE_METHOD_NAME = "DisplayWorkspaceFirstFrame";
    private static final String DISPLAY_ALL_APPS_TRACE_METHOD_NAME = "DisplayAllApps";
    public static final int DISPLAY_WORKSPACE_TRACE_COOKIE = 0;
    public static final int DISPLAY_ALL_APPS_TRACE_COOKIE = 1;

    private static final FloatProperty<Workspace<?>> WORKSPACE_WIDGET_SCALE =
            WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_WIDGET_TRANSITION);
    private static final FloatProperty<Hotseat> HOTSEAT_WIDGET_SCALE =
            HOTSEAT_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_WIDGET_TRANSITION);

    private static final boolean DESKTOP_MODE_1_SUPPORTED =
            "1".equals(Utilities.getSystemProperty("persist.wm.debug.desktop_mode", "0"));

    private static final boolean DESKTOP_MODE_2_SUPPORTED =
            "1".equals(Utilities.getSystemProperty("persist.wm.debug.desktop_mode_2", "0"));

    @Thunk
    Workspace<?> mWorkspace;
    @Thunk
    DragLayer mDragLayer;

    private WidgetManagerHelper mAppWidgetManager;
    private LauncherWidgetHolder mAppWidgetHolder;

    private final int[] mTmpAddItemCellCoordinates = new int[2];

    @Thunk
    Hotseat mHotseat;

    private DropTargetBar mDropTargetBar;

    // Main container view for the all apps screen.
    @Thunk
    ActivityAllAppsContainerView<Launcher> mAppsView;
    AllAppsTransitionController mAllAppsController;

    // Scrim view for the all apps and overview state.
    @Thunk
    ScrimView mScrimView;

    // UI and state for the overview panel
    private View mOverviewPanel;

    @Thunk
    boolean mWorkspaceLoading = true;

    // Used to notify when an activity launch has been deferred because launcher is not yet resumed
    // TODO: See if we can remove this later
    private Runnable mOnDeferredActivityLaunchCallback;

    private ViewOnDrawExecutor mPendingExecutor;
    private OnPreDrawListener mOnInitialBindListener;

    private LauncherModel mModel;
    private ModelWriter mModelWriter;
    private IconCache mIconCache;
    private LauncherAccessibilityDelegate mAccessibilityDelegate;

    private PopupDataProvider mPopupDataProvider;

    private IntSet mSynchronouslyBoundPages = new IntSet();
    @NonNull private IntSet mPagesToBindSynchronously = new IntSet();

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private SharedPreferences mSharedPrefs;
    private OnboardingPrefs<? extends Launcher> mOnboardingPrefs;

    // Activity result which needs to be processed after workspace has loaded.
    private ActivityResultInfo mPendingActivityResult;
    /**
     * Holds extra information required to handle a result from an external call, like
     * {@link #startActivityForResult(Intent, int)} or {@link #requestPermissions(String[], int)}
     */
    private PendingRequestArgs mPendingRequestArgs;
    // Request id for any pending activity result
    protected int mPendingActivityRequestCode = -1;

    private ViewGroupFocusHelper mFocusHandler;

    private RotationHelper mRotationHelper;

    protected LauncherOverlayManager mOverlayManager;
    protected DragController mDragController;
    // If true, overlay callbacks are deferred
    private boolean mDeferOverlayCallbacks;
    private final Runnable mDeferredOverlayCallbacks = this::checkIfOverlayStillDeferred;

    protected long mLastTouchUpTime = -1;
    private boolean mTouchInProgress;

    private SafeCloseable mUserChangedCallbackCloseable;

    // New InstanceId is assigned to mAllAppsSessionLogId for each AllApps sessions.
    // When Launcher is not in AllApps state mAllAppsSessionLogId will be null.
    // User actions within AllApps state are logged with this InstanceId, to recreate AllApps
    // session on the server side.
    protected InstanceId mAllAppsSessionLogId;
    private LauncherState mPrevLauncherState;

    private StringCache mStringCache;
    private BaseSearchConfig mBaseSearchConfig;
    private StartupLatencyLogger mStartupLatencyLogger;
    private CellPosMapper mCellPosMapper = CellPosMapper.DEFAULT;

    @Override
    @TargetApi(Build.VERSION_CODES.S)
    protected void onCreate(Bundle savedInstanceState) {
        mStartupLatencyLogger = createStartupLatencyLogger(
                sIsNewProcess
                        ? LockedUserState.get(this).isUserUnlockedAtLauncherStartup()
                            ? COLD
                            : COLD_DEVICE_REBOOTING
                        : WARM);
        sIsNewProcess = false;
        mStartupLatencyLogger
                .logStart(LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION)
                .logStart(LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE);
        // Only use a hard-coded cookie since we only want to trace this once.
        if (Utilities.ATLEAST_S) {
            Trace.beginAsyncSection(
                    DISPLAY_WORKSPACE_TRACE_METHOD_NAME, DISPLAY_WORKSPACE_TRACE_COOKIE);
            Trace.beginAsyncSection(DISPLAY_ALL_APPS_TRACE_METHOD_NAME,
                    DISPLAY_ALL_APPS_TRACE_COOKIE);
        }
        Object traceToken = TraceHelper.INSTANCE.beginSection(ON_CREATE_EVT,
                TraceHelper.FLAG_UI_EVENT);
        if (DEBUG_STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        if (Utilities.IS_DEBUG_DEVICE && FeatureFlags.NOTIFY_CRASHES.get()) {
            final String notificationChannelId = "com.android.launcher3.Debug";
            final String notificationChannelName = "Debug";
            final String notificationTag = "Debug";
            final int notificationId = 0;

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(new NotificationChannel(
                    notificationChannelId, notificationChannelName,
                    NotificationManager.IMPORTANCE_HIGH));

            Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
                String stackTrace = Log.getStackTraceString(throwable);

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, stackTrace);
                shareIntent = Intent.createChooser(shareIntent, null);
                PendingIntent sharePendingIntent = PendingIntent.getActivity(
                        this, 0, shareIntent, FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);

                Notification notification = new Notification.Builder(this, notificationChannelId)
                        .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
                        .setContentTitle("Launcher crash detected!")
                        .setStyle(new Notification.BigTextStyle().bigText(stackTrace))
                        .addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
                        .build();
                notificationManager.notify(notificationTag, notificationId, notification);

                Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler =
                        Thread.getDefaultUncaughtExceptionHandler();
                if (defaultUncaughtExceptionHandler != null) {
                    defaultUncaughtExceptionHandler.uncaughtException(thread, throwable);
                }
            });
        }

        super.onCreate(savedInstanceState);

        LauncherAppState app = LauncherAppState.getInstance(this);
        mModel = app.getModel();

        mRotationHelper = new RotationHelper(this);
        InvariantDeviceProfile idp = app.getInvariantDeviceProfile();
        initDeviceProfile(idp);
        idp.addOnChangeListener(this);
        mSharedPrefs = LauncherPrefs.getPrefs(this);
        mIconCache = app.getIconCache();
        mAccessibilityDelegate = createAccessibilityDelegate();

        initDragController();
        mAllAppsController = new AllAppsTransitionController(this);
        mStateManager = new StateManager<>(this, NORMAL);

        mOnboardingPrefs = createOnboardingPrefs(mSharedPrefs);

        // TODO: move the SearchConfig to SearchState when new LauncherState is created.
        mBaseSearchConfig = new BaseSearchConfig();

        setupViews();

        mAppWidgetManager = new WidgetManagerHelper(this);
        mAppWidgetHolder = createAppWidgetHolder();
        mAppWidgetHolder.startListening();

        mPopupDataProvider = new PopupDataProvider(this::updateNotificationDots);

        boolean internalStateHandled = ACTIVITY_TRACKER.handleCreate(this);
        if (internalStateHandled) {
            if (savedInstanceState != null) {
                // InternalStateHandler has already set the appropriate state.
                // We dont need to do anything.
                savedInstanceState.remove(RUNTIME_STATE);
            }
        }
        restoreState(savedInstanceState);
        mStateManager.reapplyState();

        if (savedInstanceState != null) {
            int[] pageIds = savedInstanceState.getIntArray(RUNTIME_STATE_CURRENT_SCREEN_IDS);
            if (pageIds != null) {
                mPagesToBindSynchronously = IntSet.wrap(pageIds);
            }
        }

        mStartupLatencyLogger.logWorkspaceLoadStartTime();
        if (!mModel.addCallbacksAndLoad(this)) {
            if (!internalStateHandled) {
                // If we are not binding synchronously, pause drawing until initial bind complete,
                // so that the system could continue to show the device loading prompt
                mOnInitialBindListener = Boolean.FALSE::booleanValue;
            }
        }

        // For handling default keys
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        setContentView(getRootView());
        ComposeInitializer.initCompose(this);

        if (mOnInitialBindListener != null) {
            getRootView().getViewTreeObserver().addOnPreDrawListener(mOnInitialBindListener);
        }
        getRootView().dispatchInsets();

        // Listen for screen turning off
        ScreenOnTracker.INSTANCE.get(this).addListener(mScreenOnListener);
        getSystemUiController().updateUiState(SystemUiController.UI_STATE_BASE_WINDOW,
                Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText));

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.onCreate(savedInstanceState);
        }
        mOverlayManager = getDefaultOverlay();
        PluginManagerWrapper.INSTANCE.get(this).addPluginListener(this,
                LauncherOverlayPlugin.class, false /* allowedMultiple */);

        mRotationHelper.initialize();
        TraceHelper.INSTANCE.endSection(traceToken);

        mUserChangedCallbackCloseable = UserCache.INSTANCE.get(this).addUserChangeListener(
                () -> getStateManager().goToState(NORMAL));

        if (Utilities.ATLEAST_R) {
            getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        setTitle(R.string.home_screen);
        mStartupLatencyLogger.logEnd(LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE);
    }

    /**
     * Create {@link StartupLatencyLogger} that only collects launcher startup latency metrics
     * without sending them anywhere. Child class can override this method to create logger
     * that overrides {@link StartupLatencyLogger#log()} to report those metrics.
     */
    protected StartupLatencyLogger createStartupLatencyLogger(
            StatsLogManager.StatsLatencyLogger.LatencyType latencyType) {
        return new StartupLatencyLogger(latencyType);
    }

    /**
     * Provide {@link OnBackAnimationCallback} in below order:
     * <ol>
     *  <li> auto cancel action mode handler
     *  <li> drag handler
     *  <li> view handler
     *  <li> state handler
     * </ol>
     *
     * A back gesture (a single click on back button, or a swipe back gesture that contains a series
     * of swipe events) should be handled by the same handler from above list. For a new back
     * gesture, a new handler should be regenerated.
     *
     * Note that state handler will always be handling the back press event if the previous 3 don't.
     */
    @NonNull
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    protected OnBackAnimationCallback getOnBackAnimationCallback() {
        // #1 auto cancel action mode handler
        if (isInAutoCancelActionMode()) {
            return this::finishAutoCancelActionMode;
        }

        // #2 drag handler
        if (mDragController.isDragging()) {
            return mDragController::cancelDrag;
        }

        // #3 view handler
        AbstractFloatingView topView =
                AbstractFloatingView.getTopOpenView(Launcher.this);
        if (topView != null && topView.canHandleBack()) {
            return topView;
        }

        // #4 state handler
        return new OnBackAnimationCallback() {
            @Override
            public void onBackInvoked() {
                onStateBack();
            }

            @Override
            public void onBackProgressed(@NonNull BackEvent backEvent) {
                mStateManager.getState().onBackProgressed(
                        Launcher.this, backEvent.getProgress());
            }

            @Override
            public void onBackCancelled() {
                mStateManager.getState().onBackCancelled(Launcher.this);
            }
        };
    }

    protected LauncherOverlayManager getDefaultOverlay() {
        return new LauncherOverlayManager() { };
    }

    protected OnboardingPrefs<? extends Launcher> createOnboardingPrefs(
            SharedPreferences sharedPrefs) {
        return new OnboardingPrefs<>(this, sharedPrefs);
    }

    public OnboardingPrefs<? extends Launcher> getOnboardingPrefs() {
        return mOnboardingPrefs;
    }

    @Override
    public void onPluginConnected(LauncherOverlayPlugin overlayManager, Context context) {
        switchOverlay(() -> overlayManager.createOverlayManager(this, this));
    }

    @Override
    public void onPluginDisconnected(LauncherOverlayPlugin plugin) {
        switchOverlay(this::getDefaultOverlay);
    }

    private void switchOverlay(Supplier<LauncherOverlayManager> overlaySupplier) {
        if (mOverlayManager != null) {
            mOverlayManager.onActivityDestroyed(this);
        }
        mOverlayManager = overlaySupplier.get();
        if (getRootView().isAttachedToWindow()) {
            mOverlayManager.onAttachedToWindow();
        }
        mDeferOverlayCallbacks = true;
        checkIfOverlayStillDeferred();
    }

    @Override
    public void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        mOverlayManager.onDeviceProvideChanged();
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        mRotationHelper.setCurrentTransitionRequest(REQUEST_NONE);
        // Starting with Android S, onEnterAnimationComplete is sent immediately
        // causing the surface to get removed before the animation completed (b/175345344).
        // Instead we rely on next user touch event to remove the view and optionally a callback
        // from system from Android T onwards.
        if (!Utilities.ATLEAST_S) {
            AbstractFloatingView.closeOpenViews(this, false, TYPE_ICON_SURFACE);
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        // Always update device profile when multi window mode changed.
        initDeviceProfile(mDeviceProfile.inv);
        dispatchDeviceProfileChanged();
    }

    /**
     * Initializes the drag controller.
     */
    protected void initDragController() {
        mDragController = new LauncherDragController(this);
    }

    @Override
    public void onIdpChanged(boolean modelPropertiesChanged) {
        onHandleConfigurationChanged();
    }

    @Override
    protected void onHandleConfigurationChanged() {
        if (!initDeviceProfile(mDeviceProfile.inv)) {
            return;
        }

        dispatchDeviceProfileChanged();
        reapplyUi();
        mDragLayer.recreateControllers();

        // Calling onSaveInstanceState ensures that static cache used by listWidgets is
        // initialized properly.
        onSaveInstanceState(new Bundle());
        mModel.rebindCallbacks();
    }

    public void onAssistantVisibilityChanged(float visibility) {
        mHotseat.getQsb().setAlpha(1f - visibility);
    }

    /**
     * Returns {@code true} if a new DeviceProfile is initialized, and {@code false} otherwise.
     */
    protected boolean initDeviceProfile(InvariantDeviceProfile idp) {
        // Load configuration-specific DeviceProfile
        DeviceProfile deviceProfile = idp.getDeviceProfile(this);
        if (mDeviceProfile == deviceProfile) {
            return false;
        }

        mDeviceProfile = deviceProfile;
        if (isInMultiWindowMode()) {
            mDeviceProfile = mDeviceProfile.getMultiWindowProfile(
                    this, getMultiWindowDisplaySize());
        }

        onDeviceProfileInitiated();
        if (FOLDABLE_SINGLE_PAGE.get() && mDeviceProfile.isTwoPanels) {
            mCellPosMapper = new TwoPanelCellPosMapper(mDeviceProfile.inv.numColumns);
        } else {
            mCellPosMapper = CellPosMapper.DEFAULT;
        }
        mModelWriter = mModel.getWriter(getDeviceProfile().isVerticalBarLayout(), true,
                mCellPosMapper, this);
        return true;
    }

    @Override
    public CellPosMapper getCellPosMapper() {
        return mCellPosMapper;
    }

    public RotationHelper getRotationHelper() {
        return mRotationHelper;
    }

    public ViewGroupFocusHelper getFocusHandler() {
        return mFocusHandler;
    }

    @Override
    public StateManager<LauncherState> getStateManager() {
        return mStateManager;
    }

    private LauncherCallbacks mLauncherCallbacks;

    /**
     * Call this after onCreate to set or clear overlay.
     */
    @Override
    public void setLauncherOverlay(LauncherOverlay overlay) {
        mWorkspace.setLauncherOverlay(overlay);
    }

    public boolean setLauncherCallbacks(LauncherCallbacks callbacks) {
        mLauncherCallbacks = callbacks;
        return true;
    }

    public boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !isWorkspaceLoading();
    }

    @NonNull
    @Override
    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    @Override
    public DotInfo getDotInfoForItem(ItemInfo info) {
        return mPopupDataProvider.getDotInfoForItem(info);
    }

    @Override
    public void invalidateParent(ItemInfo info) {
        if (info.container >= 0) {
            View folderIcon = getWorkspace().getHomescreenIconByItemId(info.container);
            if (folderIcon instanceof FolderIcon && folderIcon.getTag() instanceof FolderInfo) {
                if (new FolderGridOrganizer(getDeviceProfile().inv)
                        .setFolderInfo((FolderInfo) folderIcon.getTag())
                        .isItemInPreview(info.rank)) {
                    folderIcon.invalidate();
                }
            }
        }
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private int completeAdd(
            int requestCode, Intent intent, int appWidgetId, PendingRequestArgs info) {
        CellPos cellPos = getCellPosMapper().mapModelToPresenter(info);
        int screenId = cellPos.screenId;
        if (info.container == CONTAINER_DESKTOP) {
            // When the screen id represents an actual screen (as opposed to a rank) we make sure
            // that the drop page actually exists.
            screenId = ensurePendingDropLayoutExists(cellPos.screenId);
        }

        switch (requestCode) {
            case REQUEST_CREATE_SHORTCUT:
                completeAddShortcut(intent, info.container, screenId,
                        cellPos.cellX, cellPos.cellY, info);
                announceForAccessibility(R.string.item_added_to_workspace);
                break;
            case REQUEST_CREATE_APPWIDGET:
                completeAddAppWidget(appWidgetId, info, null, null);
                break;
            case REQUEST_RECONFIGURE_APPWIDGET:
                getStatsLogManager().logger().withItemInfo(info).log(LAUNCHER_WIDGET_RECONFIGURED);
                completeRestoreAppWidget(appWidgetId, LauncherAppWidgetInfo.RESTORE_COMPLETED);
                break;
            case REQUEST_BIND_PENDING_APPWIDGET: {
                int widgetId = appWidgetId;
                LauncherAppWidgetInfo widgetInfo =
                        completeRestoreAppWidget(widgetId, LauncherAppWidgetInfo.FLAG_UI_NOT_READY);
                if (widgetInfo != null) {
                    // Since the view was just bound, also launch the configure activity if needed
                    LauncherAppWidgetProviderInfo provider = mAppWidgetManager
                            .getLauncherAppWidgetInfo(widgetId);
                    if (provider != null) {
                        new WidgetAddFlowHandler(provider)
                                .startConfigActivity(this, widgetInfo,
                                        REQUEST_RECONFIGURE_APPWIDGET);
                    }
                }
                break;
            }
        }
        return screenId;
    }

    private void handleActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        if (isWorkspaceLoading()) {
            // process the result once the workspace has loaded.
            mPendingActivityResult = new ActivityResultInfo(requestCode, resultCode, data);
            return;
        }
        mPendingActivityResult = null;

        // Reset the startActivity waiting flag
        final PendingRequestArgs requestArgs = mPendingRequestArgs;
        setWaitingForResult(null);
        if (requestArgs == null) {
            return;
        }

        final int pendingAddWidgetId = requestArgs.getWidgetId();

        Runnable exitSpringLoaded = MULTI_SELECT_EDIT_MODE.get() ? null
                : () -> mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);

        if (requestCode == REQUEST_BIND_APPWIDGET) {
            // This is called only if the user did not previously have permissions to bind widgets
            final int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(
                        appWidgetId, requestArgs, null,
                        requestArgs.getWidgetHandler(),
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY);
            }
            return;
        }

        boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET);

        // We have special handling for widgets
        if (isWidgetDrop) {
            final int appWidgetId;
            int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    : -1;
            if (widgetId < 0) {
                appWidgetId = pendingAddWidgetId;
            } else {
                appWidgetId = widgetId;
            }

            final int result;
            if (appWidgetId < 0 || resultCode == RESULT_CANCELED) {
                Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not " +
                        "returned from the widget configuration activity.");
                result = RESULT_CANCELED;
                completeTwoStageWidgetDrop(result, appWidgetId, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false,
                        () -> getStateManager().goToState(NORMAL));
            } else {
                CellPos presenterPos = getCellPosMapper().mapModelToPresenter(requestArgs);
                if (requestArgs.container == CONTAINER_DESKTOP) {
                    // When the screen id represents an actual screen (as opposed to a rank)
                    // we make sure that the drop page actually exists.
                    int newScreenId = ensurePendingDropLayoutExists(presenterPos.screenId);
                    requestArgs.screenId = getCellPosMapper().mapPresenterToModel(
                            presenterPos.cellX, presenterPos.cellY, newScreenId, CONTAINER_DESKTOP)
                                    .screenId;
                }
                final CellLayout dropLayout =
                        mWorkspace.getScreenWithId(presenterPos.screenId);

                dropLayout.setDropPending(true);
                final Runnable onComplete = new Runnable() {
                    @Override
                    public void run() {
                        completeTwoStageWidgetDrop(resultCode, appWidgetId, requestArgs);
                        dropLayout.setDropPending(false);
                    }
                };
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, onComplete);
            }
            return;
        }

        if (requestCode == REQUEST_RECONFIGURE_APPWIDGET
                || requestCode == REQUEST_BIND_PENDING_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                // Update the widget view.
                completeAdd(requestCode, data, pendingAddWidgetId, requestArgs);
            }
            // Leave the widget in the pending state if the user canceled the configure.
            return;
        }

        if (requestCode == REQUEST_CREATE_SHORTCUT) {
            // Handle custom shortcuts created using ACTION_CREATE_SHORTCUT.
            if (resultCode == RESULT_OK && requestArgs.container != ItemInfo.NO_ID) {
                completeAdd(requestCode, data, -1, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);

            } else if (resultCode == RESULT_CANCELED) {
                mWorkspace.removeExtraEmptyScreenDelayed(
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false, exitSpringLoaded);
            }
        }

        mDragLayer.clearAnimatedView();
    }

    @Override
    public void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        mPendingActivityRequestCode = -1;
        handleActivityResult(requestCode, resultCode, data);
    }

    /**
     * Check to see if a given screen id exists. If not, create it at the end, return the new id.
     *
     * @param screenId the screen id to check
     * @return the new screen, or screenId if it exists
     */
    private int ensurePendingDropLayoutExists(int screenId) {
        CellLayout dropLayout = mWorkspace.getScreenWithId(screenId);
        if (dropLayout == null) {
            // it's possible that the add screen was removed because it was
            // empty and a re-bind occurred
            mWorkspace.addExtraEmptyScreens();
            IntSet emptyPagesAdded = mWorkspace.commitExtraEmptyScreens();
            return emptyPagesAdded.isEmpty() ? -1 : emptyPagesAdded.getArray().get(0);
        }
        return screenId;
    }

    @Thunk
    void completeTwoStageWidgetDrop(
            final int resultCode, final int appWidgetId, final PendingRequestArgs requestArgs) {
        CellLayout cellLayout = mWorkspace.getScreenWithId(
                getCellPosMapper().mapModelToPresenter(requestArgs).screenId);
        Runnable onCompleteRunnable = null;
        int animationType = 0;

        AppWidgetHostView boundWidget = null;
        if (resultCode == RESULT_OK) {
            animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
            final AppWidgetHostView layout = mAppWidgetHolder.createView(this, appWidgetId,
                    requestArgs.getWidgetHandler().getProviderInfo(this));
            boundWidget = layout;
            onCompleteRunnable = () -> {
                completeAddAppWidget(appWidgetId, requestArgs, layout, null);
                if (!isInState(EDIT_MODE)) {
                    mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
                }
            };
        } else if (resultCode == RESULT_CANCELED) {
            mAppWidgetHolder.deleteAppWidgetId(appWidgetId);
            animationType = Workspace.CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION;
        }
        if (mDragLayer.getAnimatedView() != null) {
            mWorkspace.animateWidgetDrop(requestArgs, cellLayout,
                    (DragView) mDragLayer.getAnimatedView(), onCompleteRunnable,
                    animationType, boundWidget, true);
        } else if (onCompleteRunnable != null) {
            // The animated view may be null in the case of a rotation during widget configuration
            onCompleteRunnable.run();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mDeferOverlayCallbacks) {
            checkIfOverlayStillDeferred();
        } else {
            mOverlayManager.onActivityStopped(this);
        }
        hideKeyboard();
        logStopAndResume(false /* isResume */);
        mAppWidgetHolder.setActivityStarted(false);
        NotificationListener.removeNotificationsChangedListener(getPopupDataProvider());
        FloatingIconView.resetIconLoadResult();
    }

    @Override
    protected void onStart() {
        Object traceToken = TraceHelper.INSTANCE.beginSection(ON_START_EVT,
                TraceHelper.FLAG_UI_EVENT);
        super.onStart();
        if (!mDeferOverlayCallbacks) {
            mOverlayManager.onActivityStarted(this);
        }

        mAppWidgetHolder.setActivityStarted(true);
        TraceHelper.INSTANCE.endSection(traceToken);
    }

    @Override
    @CallSuper
    protected void onDeferredResumed() {
        logStopAndResume(true /* isResume */);

        // Process any items that were added while Launcher was away.
        ItemInstallQueue.INSTANCE.get(this)
                .resumeModelPush(FLAG_ACTIVITY_PAUSED);

        // Refresh shortcuts if the permission changed.
        mModel.validateModelDataOnResume();

        // Set the notification listener and fetch updated notifications when we resume
        NotificationListener.addNotificationsChangedListener(mPopupDataProvider);

        DiscoveryBounce.showForHomeIfNeeded(this);
        mAppWidgetHolder.setActivityResumed(true);
    }

    private void logStopAndResume(boolean isResume) {
        if (mPendingExecutor != null) return;
        int pageIndex = mWorkspace.isOverlayShown() ? -1 : mWorkspace.getCurrentPage();
        int statsLogOrdinal = mStateManager.getState().statsLogOrdinal;

        StatsLogManager.EventEnum event;
        StatsLogManager.StatsLogger logger = getStatsLogManager().logger();
        if (isResume) {
            logger.withSrcState(LAUNCHER_STATE_BACKGROUND)
                .withDstState(mStateManager.getState().statsLogOrdinal);
            event = LAUNCHER_ONRESUME;
        } else { /* command == Action.Command.STOP */
            logger.withSrcState(mStateManager.getState().statsLogOrdinal)
                    .withDstState(LAUNCHER_STATE_BACKGROUND);
            event = LAUNCHER_ONSTOP;
        }

        if (statsLogOrdinal == LAUNCHER_STATE_HOME && mWorkspace != null) {
            logger.withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                    .setWorkspace(
                            LauncherAtom.WorkspaceContainer.newBuilder()
                                    .setPageIndex(pageIndex)).build());
        }
        logger.log(event);
    }

    private void scheduleDeferredCheck() {
        mHandler.removeCallbacks(mDeferredOverlayCallbacks);
        postAsyncCallback(mHandler, mDeferredOverlayCallbacks);
    }

    private void checkIfOverlayStillDeferred() {
        if (!mDeferOverlayCallbacks) {
            return;
        }
        if (isStarted() && (!hasBeenResumed()
                || mStateManager.getState().hasFlag(FLAG_NON_INTERACTIVE))) {
            return;
        }
        mDeferOverlayCallbacks = false;

        // Move the client to the correct state. Calling the same method twice is no-op.
        if (isStarted()) {
            mOverlayManager.onActivityStarted(this);
        }
        if (hasBeenResumed()) {
            mOverlayManager.onActivityResumed(this);
        } else {
            mOverlayManager.onActivityPaused(this);
        }
        if (!isStarted()) {
            mOverlayManager.onActivityStopped(this);
        }
    }

    public void deferOverlayCallbacksUntilNextResumeOrStop() {
        mDeferOverlayCallbacks = true;
    }

    public LauncherOverlayManager getOverlayManager() {
        return mOverlayManager;
    }

    @Override
    public void onStateSetStart(LauncherState state) {
        super.onStateSetStart(state);
        if (mDeferOverlayCallbacks) {
            scheduleDeferredCheck();
        }
        addActivityFlags(ACTIVITY_STATE_TRANSITION_ACTIVE);

        if (state == SPRING_LOADED || state == EDIT_MODE) {
            // Prevent any Un/InstallShortcutReceivers from updating the db while we are
            // not on homescreen
            ItemInstallQueue.INSTANCE.get(this).pauseModelPush(FLAG_DRAG_AND_DROP);
            getRotationHelper().setCurrentStateRequest(REQUEST_LOCK);

            mWorkspace.showPageIndicatorAtCurrentScroll();
            mWorkspace.setClipChildren(false);
        }
        // When multiple pages are visible, show persistent page indicator
        mWorkspace.getPageIndicator().setShouldAutoHide(!state.hasFlag(FLAG_MULTI_PAGE));

        mPrevLauncherState = mStateManager.getCurrentStableState();
        if (mPrevLauncherState != state && ALL_APPS.equals(state)
                // Making sure mAllAppsSessionLogId is null to avoid double logging.
                && mAllAppsSessionLogId == null) {
            // creates new instance ID since new all apps session is started.
            mAllAppsSessionLogId = new InstanceIdSequence().newInstanceId();
            if (getAllAppsEntryEvent().isPresent()) {
                getStatsLogManager().logger()
                        .withContainerInfo(ContainerInfo.newBuilder()
                                .setWorkspace(WorkspaceContainer.newBuilder()
                                        .setPageIndex(getWorkspace().getCurrentPage())).build())
                        .log(getAllAppsEntryEvent().get());
            }
        }
        updateDisallowBack();
    }

    /**
     * Returns {@link EventEnum} that should be logged when Launcher enters into AllApps state.
     */
    protected Optional<EventEnum> getAllAppsEntryEvent() {
        return Optional.of(FeatureFlags.ENABLE_DEVICE_SEARCH.get()
                ? LAUNCHER_ALLAPPS_ENTRY_WITH_DEVICE_SEARCH
                : LAUNCHER_ALLAPPS_ENTRY);
    }

    @Override
    public void onStateSetEnd(LauncherState state) {
        super.onStateSetEnd(state);
        getAppWidgetHolder().setStateIsNormal(state == LauncherState.NORMAL);
        getWorkspace().setClipChildren(!state.hasFlag(FLAG_MULTI_PAGE));

        finishAutoCancelActionMode();
        removeActivityFlags(ACTIVITY_STATE_TRANSITION_ACTIVE);

        // dispatch window state changed
        getWindow().getDecorView().sendAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED);
        AccessibilityManagerCompat.sendStateEventToTest(this, state.ordinal);

        if (state == NORMAL) {
            // Re-enable any Un/InstallShortcutReceiver and now process any queued items
            ItemInstallQueue.INSTANCE.get(this)
                    .resumeModelPush(FLAG_DRAG_AND_DROP);

            // Clear any rotation locks when going to normal state
            getRotationHelper().setCurrentStateRequest(REQUEST_NONE);
        }

        if (ALL_APPS.equals(mPrevLauncherState) && !ALL_APPS.equals(state)
                // Making sure mAllAppsSessionLogId is not null to avoid double logging.
                && mAllAppsSessionLogId != null) {
            getAppsView().reset(false);
            getAllAppsExitEvent().ifPresent(getStatsLogManager().logger()::log);
            mAllAppsSessionLogId = null;
        }
    }

    /**
     * Returns {@link EventEnum} that should be logged when Launcher exists from AllApps state.
     */
    protected Optional<EventEnum> getAllAppsExitEvent() {
        return Optional.of(LAUNCHER_ALLAPPS_EXIT);
    }

    @Override
    protected void onResume() {
        Object traceToken = TraceHelper.INSTANCE.beginSection(ON_RESUME_EVT,
                TraceHelper.FLAG_UI_EVENT);
        super.onResume();

        if (mDeferOverlayCallbacks) {
            scheduleDeferredCheck();
        } else {
            mOverlayManager.onActivityResumed(this);
        }

        DragView.removeAllViews(this);
        TraceHelper.INSTANCE.endSection(traceToken);
    }

    @Override
    protected void onPause() {
        // Ensure that items added to Launcher are queued until Launcher returns
        ItemInstallQueue.INSTANCE.get(this).pauseModelPush(FLAG_ACTIVITY_PAUSED);

        super.onPause();
        mDragController.cancelDrag();
        mLastTouchUpTime = -1;
        mDropTargetBar.animateToVisibility(false);

        if (!mDeferOverlayCallbacks) {
            mOverlayManager.onActivityPaused(this);
        }
        mAppWidgetHolder.setActivityResumed(false);
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        int stateOrdinal = savedState.getInt(RUNTIME_STATE, NORMAL.ordinal);
        LauncherState[] stateValues = LauncherState.values();
        LauncherState state = stateValues[stateOrdinal];

        NonConfigInstance lastInstance = (NonConfigInstance) getLastNonConfigurationInstance();
        boolean forceRestore = lastInstance != null
                && (lastInstance.config.diff(mOldConfig) & CONFIG_UI_MODE) != 0;
        if (forceRestore || !state.shouldDisableRestore()) {
            mStateManager.goToState(state, false /* animated */);
        }

        PendingRequestArgs requestArgs = savedState.getParcelable(
                RUNTIME_STATE_PENDING_REQUEST_ARGS);
        if (requestArgs != null) {
            setWaitingForResult(requestArgs);
        }
        mPendingActivityRequestCode = savedState.getInt(RUNTIME_STATE_PENDING_REQUEST_CODE);

        mPendingActivityResult = savedState.getParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT);

        SparseArray<Parcelable> widgetsState =
                savedState.getSparseParcelableArray(RUNTIME_STATE_WIDGET_PANEL);
        if (widgetsState != null) {
            WidgetsFullSheet.show(this, false).restoreHierarchyState(widgetsState);
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    protected void setupViews() {
        mStartupLatencyLogger.logStart(LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION);
        inflateRootView(R.layout.launcher);
        mStartupLatencyLogger.logEnd(LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION);

        mDragLayer = findViewById(R.id.drag_layer);
        mFocusHandler = mDragLayer.getFocusIndicatorHelper();
        mWorkspace = mDragLayer.findViewById(R.id.workspace);
        mWorkspace.initParentViews(mDragLayer);
        mOverviewPanel = findViewById(R.id.overview_panel);
        mHotseat = findViewById(R.id.hotseat);
        mHotseat.setWorkspace(mWorkspace);

        // Setup the drag layer
        mDragLayer.setup(mDragController, mWorkspace);

        mWorkspace.setup(mDragController);
        // Until the workspace is bound, ensure that we keep the wallpaper offset locked to the
        // default state, otherwise we will update to the wrong offsets in RTL
        mWorkspace.lockWallpaperToDefaultPage();
        mWorkspace.bindAndInitFirstWorkspaceScreen();
        mDragController.addDragListener(mWorkspace);

        // Get the search/delete/uninstall bar
        mDropTargetBar = mDragLayer.findViewById(R.id.drop_target_bar);

        // Setup Apps
        mAppsView = findViewById(R.id.apps_view);
        mAppsView.setAllAppsTransitionController(mAllAppsController);

        // Setup Scrim
        mScrimView = findViewById(R.id.scrim_view);

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        mDropTargetBar.setup(mDragController);
        mAllAppsController.setupViews(mScrimView, mAppsView);

        if (SHOW_DOT_PAGINATION.get()) {
            mWorkspace.getPageIndicator().setShouldAutoHide(true);
            mWorkspace.getPageIndicator().setPaintColor(
                    Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText)
                            ? Color.BLACK
                            : Color.WHITE);
        }
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        if (SHOW_DOT_PAGINATION.get() && WorkspacePageIndicator.class.getName().equals(name)) {
            return LayoutInflater.from(context).inflate(R.layout.page_indicator_dots,
                    (ViewGroup) parent, false);
        }
        return super.onCreateView(parent, name, context, attrs);
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     */
    View createShortcut(WorkspaceItemInfo info) {
        // This can be called before PagedView#pageScrollsInitialized returns true, so use the
        // first page, which we always assume to be present.
        return createShortcut((ViewGroup) mWorkspace.getChildAt(0), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param parent The group the shortcut belongs to. This is not necessarily the group where
     *               the shortcut should be added.
     * @param info   The data structure describing the shortcut.
     * @return A View inflated from layoutResId.
     */
    public View createShortcut(ViewGroup parent, WorkspaceItemInfo info) {
        BubbleTextView favorite = (BubbleTextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_icon, parent, false);
        favorite.applyFromWorkspaceItem(info);
        favorite.setOnClickListener(getItemOnClickListener());
        favorite.setOnFocusChangeListener(mFocusHandler);
        return favorite;
    }

    /**
     * Add a shortcut to the workspace or to a Folder.
     *
     * @param data The intent describing the shortcut.
     */
    protected void completeAddShortcut(Intent data, int container, int screenId, int cellX,
            int cellY, PendingRequestArgs args) {
        if (args.getRequestCode() != REQUEST_CREATE_SHORTCUT) {
            return;
        }

        int[] cellXY = mTmpAddItemCellCoordinates;
        CellLayout layout = getCellLayout(container, screenId);

        WorkspaceItemInfo info = PinRequestHelper.createWorkspaceItemFromPinItemRequest(
                    this, PinRequestHelper.getPinItemRequest(data), 0);
        if (info == null) {
            Log.e(TAG, "Unable to parse a valid shortcut result");
            return;
        }

        if (container < 0) {
            // Adding a shortcut to the Workspace.
            final View view = createShortcut(info);
            boolean foundCellSpan = false;
            // First we check if we already know the exact location where we want to add this item.
            if (cellX >= 0 && cellY >= 0) {
                cellXY[0] = cellX;
                cellXY[1] = cellY;
                foundCellSpan = true;

                DragObject dragObject = new DragObject(getApplicationContext());
                dragObject.dragInfo = info;
                // If appropriate, either create a folder or add to an existing folder
                if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                        true, dragObject)) {
                    return;
                }
                if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0, dragObject,
                        true)) {
                    return;
                }
            } else {
                foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
            }

            if (!foundCellSpan) {
                mWorkspace.onNoCellFound(layout, info, /* logInstanceId= */ null);
                return;
            }

            getModelWriter().addItemToDatabase(info, container, screenId, cellXY[0], cellXY[1]);
            mWorkspace.addInScreen(view, info);
        } else {
            // Adding a shortcut to a Folder.
            FolderIcon folderIcon = findFolderIcon(container);
            if (folderIcon != null) {
                FolderInfo folderInfo = (FolderInfo) folderIcon.getTag();
                folderInfo.add(info, args.rank, false);
            } else {
                Log.e(TAG, "Could not find folder with id " + container + " to add shortcut.");
            }
        }
    }

    @Override
    public @Nullable FolderIcon findFolderIcon(final int folderIconId) {
        return (FolderIcon) mWorkspace.getHomescreenIconByItemId(folderIconId);
    }

    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     */
    @Thunk
    void completeAddAppWidget(int appWidgetId, ItemInfo itemInfo,
            AppWidgetHostView hostView, LauncherAppWidgetProviderInfo appWidgetInfo) {

        if (appWidgetInfo == null) {
            appWidgetInfo = mAppWidgetManager.getLauncherAppWidgetInfo(appWidgetId);
        }

        if (hostView == null) {
            // Perform actual inflation because we're live
            hostView = mAppWidgetHolder.createView(this, appWidgetId, appWidgetInfo);
        }

        LauncherAppWidgetInfo launcherInfo;
        launcherInfo =
                new LauncherAppWidgetInfo(
                        appWidgetId, appWidgetInfo.provider, appWidgetInfo, hostView);
        launcherInfo.spanX = itemInfo.spanX;
        launcherInfo.spanY = itemInfo.spanY;
        launcherInfo.minSpanX = itemInfo.minSpanX;
        launcherInfo.minSpanY = itemInfo.minSpanY;
        launcherInfo.user = appWidgetInfo.getProfile();
        if (itemInfo instanceof PendingAddWidgetInfo) {
            launcherInfo.sourceContainer = ((PendingAddWidgetInfo) itemInfo).sourceContainer;
        } else if (itemInfo instanceof PendingRequestArgs) {
            launcherInfo.sourceContainer =
                    ((PendingRequestArgs) itemInfo).getWidgetSourceContainer();
        }
        CellPos presenterPos = getCellPosMapper().mapModelToPresenter(itemInfo);
        getModelWriter().addItemToDatabase(launcherInfo,
                itemInfo.container, presenterPos.screenId, presenterPos.cellX, presenterPos.cellY);

        hostView.setVisibility(View.VISIBLE);
        prepareAppWidget(hostView, launcherInfo);
        mWorkspace.addInScreen(hostView, launcherInfo);
        announceForAccessibility(R.string.item_added_to_workspace);

        // Show the widget resize frame.
        if (hostView instanceof LauncherAppWidgetHostView) {
            final LauncherAppWidgetHostView launcherHostView = (LauncherAppWidgetHostView) hostView;
            CellLayout cellLayout = getCellLayout(launcherInfo.container, presenterPos.screenId);
            if (mStateManager.getState() == NORMAL) {
                AppWidgetResizeFrame.showForWidget(launcherHostView, cellLayout);
            } else {
                mStateManager.addStateListener(new StateManager.StateListener<LauncherState>() {
                    @Override
                    public void onStateTransitionComplete(LauncherState finalState) {
                        if ((mPrevLauncherState == SPRING_LOADED || mPrevLauncherState == EDIT_MODE)
                                && finalState == NORMAL) {
                            AppWidgetResizeFrame.showForWidget(launcherHostView, cellLayout);
                            mStateManager.removeStateListener(this);
                        }
                    }
                });
            }
        }
    }

    private void prepareAppWidget(AppWidgetHostView hostView, LauncherAppWidgetInfo item) {
        hostView.setTag(item);
        item.onBindAppWidget(this, hostView);
        hostView.setFocusable(true);
        hostView.setOnFocusChangeListener(mFocusHandler);
    }

    private final ScreenOnListener mScreenOnListener = this::onScreenOnChanged;

    private void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        mWorkspace.updateNotificationDots(updatedDots);
        mAppsView.getAppsStore().updateNotificationDots(updatedDots);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mOverlayManager.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mOverlayManager.onDetachedFromWindow();
        closeContextMenu();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        NonConfigInstance instance = new NonConfigInstance();
        instance.config = new Configuration(mOldConfig);
        return instance;
    }

    public AllAppsTransitionController getAllAppsController() {
        return mAllAppsController;
    }

    @Override
    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    @Override
    public ActivityAllAppsContainerView<Launcher> getAppsView() {
        return mAppsView;
    }

    public Workspace<?> getWorkspace() {
        return mWorkspace;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }

    public <T extends View> T getOverviewPanel() {
        return (T) mOverviewPanel;
    }

    public DropTargetBar getDropTargetBar() {
        return mDropTargetBar;
    }

    @Override
    public ScrimView getScrimView() {
        return mScrimView;
    }

    public LauncherWidgetHolder getAppWidgetHolder() {
        return mAppWidgetHolder;
    }

    protected LauncherWidgetHolder createAppWidgetHolder() {
        return LauncherWidgetHolder.HolderFactory.newFactory(this).newInstance(
                this, appWidgetId -> getWorkspace().removeWidget(appWidgetId));
    }

    public LauncherModel getModel() {
        return mModel;
    }

    /**
     * Returns the ModelWriter writer, make sure to call the function every time you want to use it.
     */
    public ModelWriter getModelWriter() {
        return mModelWriter;
    }

    @Override
    public SharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }

    @Override
    public SharedPreferences getDevicePrefs() {
        return LauncherPrefs.getDevicePrefs(this);
    }

    public int getOrientation() {
        return mOldConfig.orientation;
    }

    public BaseSearchConfig getSearchConfig() {
        return mBaseSearchConfig;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (Utilities.isRunningInTestHarness()) {
            Log.d(TestProtocol.PERMANENT_DIAG_TAG, "Launcher.onNewIntent: " + intent);
        }
        Object traceToken = TraceHelper.INSTANCE.beginSection(ON_NEW_INTENT_EVT);
        super.onNewIntent(intent);

        boolean alreadyOnHome = hasWindowFocus() && ((intent.getFlags() &
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        // Check this condition before handling isActionMain, as this will get reset.
        boolean shouldMoveToDefaultScreen = alreadyOnHome && isInState(NORMAL)
                && AbstractFloatingView.getTopOpenView(this) == null;
        boolean isActionMain = Intent.ACTION_MAIN.equals(intent.getAction());
        boolean internalStateHandled = ACTIVITY_TRACKER.handleNewIntent(this);

        if (isActionMain) {
            if (!internalStateHandled) {
                // In all these cases, only animate if we're already on home
                closeOpenViews(isStarted());

                if (!isInState(NORMAL)) {
                    // Only change state, if not already the same. This prevents cancelling any
                    // animations running as part of resume
                    mStateManager.goToState(NORMAL, mStateManager.shouldAnimateStateChange());
                }

                // Reset the apps view
                if (!alreadyOnHome) {
                    mAppsView.reset(isStarted() /* animate */);
                }

                if (shouldMoveToDefaultScreen && !mWorkspace.isHandlingTouch()) {
                    mWorkspace.post(mWorkspace::moveToDefaultScreen);
                }
            }

            if (mLauncherCallbacks != null) {
                mLauncherCallbacks.onHomeIntent(internalStateHandled);
            }
            mOverlayManager.hideOverlay(isStarted() && !isForceInvisible());
            handleGestureContract(intent);
        } else if (Intent.ACTION_ALL_APPS.equals(intent.getAction())) {
            showAllAppsFromIntent(alreadyOnHome);
        } else if (INTENT_ACTION_ALL_APPS_TOGGLE.equals(intent.getAction())) {
            toggleAllAppsFromIntent(alreadyOnHome);
        } else if (Intent.ACTION_SHOW_WORK_APPS.equals(intent.getAction())) {
            showAllAppsWorkTabFromIntent(alreadyOnHome);
        }

        TraceHelper.INSTANCE.endSection(traceToken);
    }

    protected void toggleAllAppsFromIntent(boolean alreadyOnHome) {
        if (getStateManager().isInStableState(ALL_APPS)) {
            getStateManager().goToState(NORMAL, alreadyOnHome);
        } else {
            showAllAppsFromIntent(alreadyOnHome);
        }
    }

    protected void showAllAppsFromIntent(boolean alreadyOnHome) {
        AbstractFloatingView.closeAllOpenViews(this);
        getStateManager().goToState(ALL_APPS, alreadyOnHome);
    }

    private void showAllAppsWorkTabFromIntent(boolean alreadyOnHome) {
        showAllAppsFromIntent(alreadyOnHome);
        mAppsView.switchToTab(ActivityAllAppsContainerView.AdapterHolder.WORK);
    }

    /**
     * Handles gesture nav contract
     */
    protected void handleGestureContract(Intent intent) {
        GestureNavContract gnc = GestureNavContract.fromIntent(intent);
        if (gnc != null) {
            AbstractFloatingView.closeOpenViews(this, false, TYPE_ICON_SURFACE);
            FloatingSurfaceView.show(this, gnc);
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        if (mSynchronouslyBoundPages != null) {
            mSynchronouslyBoundPages.forEach(screenId -> {
                int pageIndex = mWorkspace.getPageIndexForScreenId(screenId);
                if (pageIndex != PagedView.INVALID_PAGE) {
                    mWorkspace.restoreInstanceStateForChild(pageIndex);
                }
            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putIntArray(RUNTIME_STATE_CURRENT_SCREEN_IDS,
                mWorkspace.getCurrentPageScreenIds().getArray().toArray());
        outState.putInt(RUNTIME_STATE, mStateManager.getState().ordinal);

        AbstractFloatingView widgets = AbstractFloatingView
                .getOpenView(this, AbstractFloatingView.TYPE_WIDGETS_FULL_SHEET);
        if (widgets != null) {
            SparseArray<Parcelable> widgetsState = new SparseArray<>();
            widgets.saveHierarchyState(widgetsState);
            outState.putSparseParcelableArray(RUNTIME_STATE_WIDGET_PANEL, widgetsState);
        } else {
            outState.remove(RUNTIME_STATE_WIDGET_PANEL);
        }

        // We close any open folders and shortcut containers that are not safe for rebind,
        // and we need to make sure this state is reflected.
        AbstractFloatingView.closeAllOpenViewsExcept(
                this, isStarted() && !isForceInvisible(), TYPE_REBIND_SAFE);
        finishAutoCancelActionMode();

        if (mPendingRequestArgs != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_REQUEST_ARGS, mPendingRequestArgs);
        }
        outState.putInt(RUNTIME_STATE_PENDING_REQUEST_CODE, mPendingActivityRequestCode);

        if (mPendingActivityResult != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT, mPendingActivityResult);
        }

        super.onSaveInstanceState(outState);
        mOverlayManager.onActivitySaveInstanceState(this, outState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ACTIVITY_TRACKER.onActivityDestroyed(this);

        ScreenOnTracker.INSTANCE.get(this).removeListener(mScreenOnListener);
        mWorkspace.removeFolderListeners();
        PluginManagerWrapper.INSTANCE.get(this).removePluginListener(this);

        mModel.removeCallbacks(this);
        mRotationHelper.destroy();

        try {
            mAppWidgetHolder.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }
        mAppWidgetHolder.destroy();

        TextKeyListener.getInstance().release();
        clearPendingBinds();
        LauncherAppState.getIDP(this).removeOnChangeListener(this);

        mOverlayManager.onActivityDestroyed(this);
        mUserChangedCallbackCloseable.close();
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public DropTargetHandler getDropTargetHandler() {
        return new DropTargetHandler(this);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
        }
        super.startActivityForResult(intent, requestCode, options);
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
        }
        try {
            super.startIntentSenderForResult(intent, requestCode,
                    fillInIntent, flagsMask, flagsValues, extraFlags, options);
        } catch (IntentSender.SendIntentException e) {
            throw new ActivityNotFoundException();
        }
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString("source", "launcher-search");
        }

        if (mLauncherCallbacks == null ||
                !mLauncherCallbacks.startSearch(initialQuery, selectInitialQuery, appSearchData)) {
            // Starting search from the callbacks failed. Start the default global search.
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, true);
        }

        // We need to show the workspace after starting the search
        mStateManager.goToState(NORMAL);
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mPendingRequestArgs != null;
    }

    public boolean isWorkspaceLoading() {
        return mWorkspaceLoading;
    }

    @Override
    public boolean isBindingItems() {
        return mWorkspaceLoading;
    }

    private void setWorkspaceLoading(boolean value) {
        mWorkspaceLoading = value;
    }

    public void setWaitingForResult(PendingRequestArgs args) {
        mPendingRequestArgs = args;
    }

    void addAppWidgetFromDropImpl(int appWidgetId, ItemInfo info, AppWidgetHostView boundWidget,
            WidgetAddFlowHandler addFlowHandler) {
        if (LOGD) {
            Log.d(TAG, "Adding widget from drop");
        }
        addAppWidgetImpl(appWidgetId, info, boundWidget, addFlowHandler, 0);
    }

    void addAppWidgetImpl(int appWidgetId, ItemInfo info,
            AppWidgetHostView boundWidget, WidgetAddFlowHandler addFlowHandler, int delay) {
        if (!addFlowHandler.startConfigActivity(this, appWidgetId, info,
                REQUEST_CREATE_APPWIDGET)) {
            // If the configuration flow was not started, add the widget

            // Exit spring loaded mode if necessary after adding the widget
            Runnable onComplete = MULTI_SELECT_EDIT_MODE.get() ? null
                    : () -> mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
            completeAddAppWidget(appWidgetId, info, boundWidget,
                    addFlowHandler.getProviderInfo(this));
            mWorkspace.removeExtraEmptyScreenDelayed(delay, false, onComplete);
        }
    }

    public void addPendingItem(PendingAddItemInfo info, int container, int screenId,
            int[] cell, int spanX, int spanY) {
        if (cell == null) {
            CellPos modelPos = getCellPosMapper().mapPresenterToModel(0, 0, screenId, container);
            info.screenId = modelPos.screenId;
        } else {
            CellPos modelPos = getCellPosMapper().mapPresenterToModel(
                    cell[0],  cell[1], screenId, container);
            info.screenId = modelPos.screenId;
            info.cellX = modelPos.cellX;
            info.cellY = modelPos.cellY;
        }
        info.container = container;
        info.spanX = spanX;
        info.spanY = spanY;

        if (info instanceof PendingAddWidgetInfo) {
            addAppWidgetFromDrop((PendingAddWidgetInfo) info);
        } else { // info can only be PendingAddShortcutInfo
            processShortcutFromDrop((PendingAddShortcutInfo) info);
        }
    }

    /**
     * Process a shortcut drop.
     */
    private void processShortcutFromDrop(PendingAddShortcutInfo info) {
        Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(info.componentName);
        setWaitingForResult(PendingRequestArgs.forIntent(REQUEST_CREATE_SHORTCUT, intent, info));
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "start: processShortcutFromDrop");
        if (!info.getActivityInfo(this).startConfigActivity(this, REQUEST_CREATE_SHORTCUT)) {
            handleActivityResult(REQUEST_CREATE_SHORTCUT, RESULT_CANCELED, null);
        }
    }

    /**
     * Process a widget drop.
     */
    private void addAppWidgetFromDrop(PendingAddWidgetInfo info) {
        AppWidgetHostView hostView = info.boundWidget;
        final int appWidgetId;
        WidgetAddFlowHandler addFlowHandler = info.getHandler();
        if (hostView != null) {
            // In the case where we've prebound the widget, we remove it from the DragLayer
            if (LOGD) {
                Log.d(TAG, "Removing widget view from drag layer and setting boundWidget to null");
            }
            getDragLayer().removeView(hostView);

            appWidgetId = hostView.getAppWidgetId();
            addAppWidgetFromDropImpl(appWidgetId, info, hostView, addFlowHandler);

            // Clear the boundWidget so that it doesn't get destroyed.
            info.boundWidget = null;
        } else {
            // In this case, we either need to start an activity to get permission to bind
            // the widget, or we need to start an activity to configure the widget, or both.
            if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET) {
                appWidgetId = CustomWidgetManager.INSTANCE.get(this).getWidgetIdForCustomProvider(
                        info.componentName);
            } else {
                appWidgetId = getAppWidgetHolder().allocateAppWidgetId();
            }
            Bundle options = info.bindOptions;

            boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                    appWidgetId, info.info, options);
            if (success) {
                addAppWidgetFromDropImpl(appWidgetId, info, null, addFlowHandler);
            } else {
                addFlowHandler.startBindFlow(this, appWidgetId, info, REQUEST_BIND_APPWIDGET);
            }
        }
    }

    /**
     * Creates and adds new folder to CellLayout
     */
    public FolderIcon addFolder(CellLayout layout, int container, final int screenId, int cellX,
            int cellY) {
        final FolderInfo folderInfo = new FolderInfo();

        // Update the model
        getModelWriter().addItemToDatabase(folderInfo, container, screenId, cellX, cellY);

        // Create the view
        FolderIcon newFolder = FolderIcon.inflateFolderAndIcon(R.layout.folder_icon, this, layout,
                folderInfo);
        mWorkspace.addInScreen(newFolder, folderInfo);
        // Force measure the new folder icon
        CellLayout parent = mWorkspace.getParentCellLayoutForView(newFolder);
        parent.getShortcutsAndWidgets().measureChild(newFolder);
        return newFolder;
    }

    @Override
    public Rect getFolderBoundingBox() {
        // We need to bound the folder to the currently visible workspace area
        return getWorkspace().getPageAreaRelativeToDragLayer();
    }

    @Override
    public void updateOpenFolderPosition(int[] inOutPosition, Rect bounds, int width, int height) {
        int left = inOutPosition[0];
        int top = inOutPosition[1];
        DeviceProfile grid = getDeviceProfile();
        int distFromEdgeOfScreen = getWorkspace().getPaddingLeft();
        if (grid.isPhone && (grid.availableWidthPx - width) < 4 * distFromEdgeOfScreen) {
            // Center the folder if it is very close to being centered anyway, by virtue of
            // filling the majority of the viewport. ie. remove it from the uncanny valley
            // of centeredness.
            left = (grid.availableWidthPx - width) / 2;
        } else if (width >= bounds.width()) {
            // If the folder doesn't fit within the bounds, center it about the desired bounds
            left = bounds.left + (bounds.width() - width) / 2;
        }
        if (height >= bounds.height()) {
            // Folder height is greater than page height, center on page
            top = bounds.top + (bounds.height() - height) / 2;
        } else {
            // Folder height is less than page height, so bound it to the absolute open folder
            // bounds if necessary
            Rect folderBounds = grid.getAbsoluteOpenFolderBounds();
            left = Math.max(folderBounds.left, Math.min(left, folderBounds.right - width));
            top = Math.max(folderBounds.top, Math.min(top, folderBounds.bottom - height));
        }
        inOutPosition[0] = left;
        inOutPosition[1] = top;
    }

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v the view being removed.
     * @param itemInfo the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb) {
        return removeItem(v, itemInfo, deleteFromDb, null);
    }

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v the view being removed.
     * @param itemInfo the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     * @param reason the resaon for removal.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb,
            @Nullable final String reason) {
        if (itemInfo instanceof WorkspaceItemInfo) {
            // Remove the shortcut from the folder before removing it from launcher
            View folderIcon = mWorkspace.getHomescreenIconByItemId(itemInfo.container);
            if (folderIcon instanceof FolderIcon) {
                ((FolderInfo) folderIcon.getTag()).remove((WorkspaceItemInfo) itemInfo, true);
            } else {
                mWorkspace.removeWorkspaceItem(v);
            }
            if (deleteFromDb) {
                getModelWriter().deleteItemFromDatabase(itemInfo, reason);
            }
        } else if (itemInfo instanceof FolderInfo) {
            final FolderInfo folderInfo = (FolderInfo) itemInfo;
            if (v instanceof FolderIcon) {
                ((FolderIcon) v).removeListeners();
            }
            mWorkspace.removeWorkspaceItem(v);
            if (deleteFromDb) {
                getModelWriter().deleteFolderAndContentsFromDatabase(folderInfo);
            }
        } else if (itemInfo instanceof LauncherAppWidgetInfo) {
            final LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) itemInfo;
            mWorkspace.removeWorkspaceItem(v);
            if (deleteFromDb) {
                getModelWriter().deleteWidgetInfo(widgetInfo, getAppWidgetHolder(), reason);
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        TestLogging.recordKeyEvent(TestProtocol.SEQUENCE_MAIN, "Key event", event);
        return (event.getKeyCode() == KeyEvent.KEYCODE_HOME) || super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchInProgress = true;
                break;
            case MotionEvent.ACTION_UP:
                mLastTouchUpTime = SystemClock.uptimeMillis();
                // Follow through
            case MotionEvent.ACTION_CANCEL:
                mTouchInProgress = false;
                break;
        }
        TestLogging.recordMotionEvent(TestProtocol.SEQUENCE_MAIN, "Touch event", ev);
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Returns true if a touch interaction is in progress
     */
    public boolean isTouchInProgress() {
        return mTouchInProgress;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onBackPressed() {
        getOnBackAnimationCallback().onBackInvoked();
    }

    protected void onStateBack() {
        mStateManager.getState().onBackPressed(this);
    }

    protected void onScreenOnChanged(boolean isOn) {
        // Reset AllApps to its initial state only if we are not in the middle of
        // processing a multi-step drop
        if (!isOn && mPendingRequestArgs == null) {
            if (!isInState(NORMAL)) {
                onUiChangedWhileSleeping();
            }
            mStateManager.goToState(NORMAL);
        }
    }

    @Override
    public RunnableList startActivitySafely(View v, Intent intent, ItemInfo item) {
        if (!hasBeenResumed()) {
            RunnableList result = new RunnableList();
            // Workaround an issue where the WM launch animation is clobbered when finishing the
            // recents animation into launcher. Defer launching the activity until Launcher is
            // next resumed.
            addOnResumeCallback(() -> {
                RunnableList actualResult = startActivitySafely(v, intent, item);
                if (actualResult != null) {
                    actualResult.add(result::executeAllAndDestroy);
                } else {
                    result.executeAllAndDestroy();
                }
            });
            if (mOnDeferredActivityLaunchCallback != null) {
                mOnDeferredActivityLaunchCallback.run();
                mOnDeferredActivityLaunchCallback = null;
            }
            return result;
        }

        RunnableList result = super.startActivitySafely(v, intent, item);
        if (result != null && v instanceof BubbleTextView) {
            // This is set to the view that launched the activity that navigated the user away
            // from launcher. Since there is no callback for when the activity has finished
            // launching, enable the press state and keep this reference to reset the press
            // state when we return to launcher.
            BubbleTextView btv = (BubbleTextView) v;
            btv.setStayPressed(true);
            result.add(() -> btv.setStayPressed(false));
        }
        return result;
    }

    boolean isHotseatLayout(View layout) {
        // TODO: Remove this method
        return mHotseat != null && (layout == mHotseat);
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     *
     * @param screenId must be presenterPos and not modelPos.
     */
    public CellLayout getCellLayout(int container, int screenId) {
        return (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT)
                ? mHotseat : mWorkspace.getScreenWithId(screenId);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // The widget preview db can result in holding onto over
            // 3MB of memory for caching which isn't necessary.
            SQLiteDatabase.releaseMemory();

            // This clears all widget bitmaps from the widget tray
            // TODO(hyunyoungs)
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        // Populate event with a fake title based on the current state.
        // TODO: When can workspace be null?
        text.add(mWorkspace == null
                ? getString(R.string.home_screen)
                : mStateManager.getState().getDescription(this));
        return result;
    }

    /**
     * Persistant callback which notifies when an activity launch is deferred because the activity
     * was not yet resumed.
     */
    public void setOnDeferredActivityLaunchCallback(Runnable callback) {
        mOnDeferredActivityLaunchCallback = callback;
    }

    /**
     * Sets the next pages to bind synchronously on next bind.
     * @param pages should not be null.
     */
    public void setPagesToBindSynchronously(@NonNull IntSet pages) {
        mPagesToBindSynchronously = pages;
    }

    @Override
    public IntSet getPagesToBindSynchronously(IntArray orderedScreenIds) {
        IntSet visibleIds;
        if (!mPagesToBindSynchronously.isEmpty()) {
            visibleIds = mPagesToBindSynchronously;
        } else if (!mWorkspaceLoading) {
            visibleIds = mWorkspace.getCurrentPageScreenIds();
        } else {
            // If workspace binding is still in progress, getCurrentPageScreenIds won't be accurate,
            // and we should use mSynchronouslyBoundPages that's set during initial binding.
            visibleIds = mSynchronouslyBoundPages;
        }
        IntArray actualIds = new IntArray();

        IntSet result = new IntSet();
        if (visibleIds.isEmpty()) {
            return result;
        }
        for (int id : orderedScreenIds.toArray()) {
            actualIds.add(id);
        }
        int firstId = visibleIds.getArray().get(0);
        int pairId = mWorkspace.getScreenPair(firstId);
        // Double check that actual screenIds contains the visibleId, as empty screens are hidden
        // in single panel.
        if (actualIds.contains(firstId)) {
            result.add(firstId);
            if (mDeviceProfile.isTwoPanels && actualIds.contains(pairId)) {
                result.add(pairId);
            }
        } else if (LauncherAppState.getIDP(this).supportedProfiles.stream().anyMatch(
                deviceProfile -> deviceProfile.isTwoPanels) && actualIds.contains(pairId)) {
            // Add the right panel if left panel is hidden when switching display, due to empty
            // pages being hidden in single panel.
            result.add(pairId);
        }
        return result;
    }

    /**
     * Clear any pending bind callbacks. This is called when is loader is planning to
     * perform a full rebind from scratch.
     */
    @Override
    public void clearPendingBinds() {
        if (mPendingExecutor != null) {
            mPendingExecutor.cancel();
            mPendingExecutor = null;

            // We might have set this flag previously and forgot to clear it.
            mAppsView.getAppsStore()
                    .disableDeferUpdatesSilently(AllAppsStore.DEFER_UPDATES_NEXT_DRAW);
        }
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        Object traceToken = TraceHelper.INSTANCE.beginSection("startBinding");
        // Floating panels (except the full widget sheet) are associated with individual icons. If
        // we are starting a fresh bind, close all such panels as all the icons are about
        // to go away.
        AbstractFloatingView.closeOpenViews(this, true, TYPE_ALL & ~TYPE_REBIND_SAFE);

        setWorkspaceLoading(true);

        // Clear the workspace because it's going to be rebound
        mDragController.cancelDrag();

        mWorkspace.clearDropTargets();
        mWorkspace.removeAllWorkspaceScreens();
        mAppWidgetHolder.clearViews();

        if (mHotseat != null) {
            mHotseat.resetLayout(getDeviceProfile().isVerticalBarLayout());
        }
        TraceHelper.INSTANCE.endSection(traceToken);
    }

    @Override
    public void bindScreens(IntArray orderedScreenIds) {
        int firstScreenPosition = 0;
        if (FeatureFlags.QSB_ON_FIRST_SCREEN &&
                orderedScreenIds.indexOf(Workspace.FIRST_SCREEN_ID) != firstScreenPosition) {
            orderedScreenIds.removeValue(Workspace.FIRST_SCREEN_ID);
            orderedScreenIds.add(firstScreenPosition, Workspace.FIRST_SCREEN_ID);
        } else if (!FeatureFlags.QSB_ON_FIRST_SCREEN && orderedScreenIds.isEmpty()) {
            // If there are no screens, we need to have an empty screen
            mWorkspace.addExtraEmptyScreens();
        }
        bindAddScreens(orderedScreenIds);

        // After we have added all the screens, if the wallpaper was locked to the default state,
        // then notify to indicate that it can be released and a proper wallpaper offset can be
        // computed before the next layout
        mWorkspace.unlockWallpaperFromDefaultPageOnNextLayout();
    }

    private void bindAddScreens(IntArray orderedScreenIds) {
        if (mDeviceProfile.isTwoPanels) {
            // Some empty pages might have been removed while the phone was in a single panel
            // mode, so we want to add those empty pages back.
            IntSet screenIds = IntSet.wrap(orderedScreenIds);
            orderedScreenIds.forEach(screenId -> screenIds.add(mWorkspace.getScreenPair(screenId)));
            orderedScreenIds = screenIds.getArray();
        }

        int count = orderedScreenIds.size();
        for (int i = 0; i < count; i++) {
            int screenId = orderedScreenIds.get(i);
            if (FeatureFlags.QSB_ON_FIRST_SCREEN && screenId == Workspace.FIRST_SCREEN_ID) {
                // No need to bind the first screen, as its always bound.
                continue;
            }
            mWorkspace.insertNewWorkspaceScreenBeforeEmptyScreen(screenId);
        }
    }

    @Override
    public void preAddApps() {
        // If there's an undo snackbar, force it to complete to ensure empty screens are removed
        // before trying to add new items.
        mModelWriter.commitDelete();
        AbstractFloatingView snackbar = AbstractFloatingView.getOpenView(this, TYPE_SNACKBAR);
        if (snackbar != null) {
            snackbar.post(() -> snackbar.close(true));
        }
    }

    @Override
    public void bindAppsAdded(IntArray newScreens, ArrayList<ItemInfo> addNotAnimated,
            ArrayList<ItemInfo> addAnimated) {
        // Add the new screens
        if (newScreens != null) {
            // newScreens can contain an empty right panel that is already bound, but not known
            // by BgDataModel.
            newScreens.removeAllValues(mWorkspace.mScreenOrder);
            bindAddScreens(newScreens);
        }

        // We add the items without animation on non-visible pages, and with
        // animations on the new page (which we will try and snap to).
        if (addNotAnimated != null && !addNotAnimated.isEmpty()) {
            bindItems(addNotAnimated, false);
        }
        if (addAnimated != null && !addAnimated.isEmpty()) {
            bindItems(addAnimated, true);
        }

        // Remove the extra empty screen
        mWorkspace.removeExtraEmptyScreen(false);
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindItems(final List<ItemInfo> items, final boolean forceAnimateIcons) {
        bindItems(items, forceAnimateIcons, /* focusFirstItemForAccessibility= */ false);
    }


    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @param focusFirstItemForAccessibility true iff the first item to be added to the workspace
     *                                       should be focused for accessibility.
     */
    public void bindItems(
            final List<ItemInfo> items,
            final boolean forceAnimateIcons,
            final boolean focusFirstItemForAccessibility) {
        // Get the list of added items and intersect them with the set of items here
        final Collection<Animator> bounceAnims = new ArrayList<>();
        boolean canAnimatePageChange = canAnimatePageChange();
        Workspace<?> workspace = mWorkspace;
        int newItemsScreenId = -1;
        int end = items.size();
        View newView = null;
        for (int i = 0; i < end; i++) {
            final ItemInfo item = items.get(i);

            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                    mHotseat == null) {
                continue;
            }

            final View view;
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
                    WorkspaceItemInfo info = (WorkspaceItemInfo) item;
                    view = createShortcut(info);
                    break;
                }
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER: {
                    view = FolderIcon.inflateFolderAndIcon(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item);
                    break;
                }
                case LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR: {
                    FolderInfo info = (FolderInfo) item;
                    // TODO (jeremysim b/274189428): Create app pair icon
                    view = null;
                    break;
                }
                case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET: {
                    view = inflateAppWidget((LauncherAppWidgetInfo) item);
                    if (view == null) {
                        continue;
                    }
                    break;
                }
                default:
                    throw new RuntimeException("Invalid Item Type");
            }

            /*
             * Remove colliding items.
             */
            CellPos presenterPos = getCellPosMapper().mapModelToPresenter(item);
            if (item.container == CONTAINER_DESKTOP) {
                CellLayout cl = mWorkspace.getScreenWithId(presenterPos.screenId);
                if (cl != null && cl.isOccupied(presenterPos.cellX, presenterPos.cellY)) {
                    View v = cl.getChildAt(presenterPos.cellX, presenterPos.cellY);
                    if (v == null) {
                        Log.e(TAG, "bindItems failed when removing colliding item=" + item);
                    }
                    Object tag = v.getTag();
                    String desc = "Collision while binding workspace item: " + item
                            + ". Collides with " + tag;
                    if (FeatureFlags.IS_STUDIO_BUILD) {
                        throw (new RuntimeException(desc));
                    } else {
                        getModelWriter().deleteItemFromDatabase(item, desc);
                        continue;
                    }
                }
            }
            workspace.addInScreenFromBind(view, item);
            if (forceAnimateIcons) {
                // Animate all the applications up now
                view.setAlpha(0f);
                view.setScaleX(0f);
                view.setScaleY(0f);
                bounceAnims.add(createNewAppBounceAnimation(view, i));
                newItemsScreenId = presenterPos.screenId;
            }

            if (newView == null) {
                newView = view;
            }
        }

        View viewToFocus = newView;
        // Animate to the correct pager
        if (forceAnimateIcons && newItemsScreenId > -1) {
            AnimatorSet anim = new AnimatorSet();
            anim.playTogether(bounceAnims);
            if (focusFirstItemForAccessibility && viewToFocus != null) {
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewToFocus.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                    }
                });
            }

            int currentScreenId = mWorkspace.getScreenIdForPageIndex(mWorkspace.getNextPage());
            final int newScreenIndex = mWorkspace.getPageIndexForScreenId(newItemsScreenId);
            final Runnable startBounceAnimRunnable = anim::start;

            if (canAnimatePageChange && newItemsScreenId != currentScreenId) {
                // We post the animation slightly delayed to prevent slowdowns
                // when we are loading right after we return to launcher.
                mWorkspace.postDelayed(new Runnable() {
                    public void run() {
                        if (mWorkspace != null) {
                            closeOpenViews(false);

                            mWorkspace.snapToPage(newScreenIndex);
                            mWorkspace.postDelayed(startBounceAnimRunnable,
                                    NEW_APPS_ANIMATION_DELAY);
                        }
                    }
                }, NEW_APPS_PAGE_MOVE_DELAY);
            } else {
                mWorkspace.postDelayed(startBounceAnimRunnable, NEW_APPS_ANIMATION_DELAY);
            }
        } else if (focusFirstItemForAccessibility && viewToFocus != null) {
            viewToFocus.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
        workspace.requestLayout();
    }

    /**
     * Add the views for a widget to the workspace.
     */
    public void bindAppWidget(LauncherAppWidgetInfo item) {
        View view = inflateAppWidget(item);
        if (view != null) {
            mWorkspace.addInScreen(view, item);
            mWorkspace.requestLayout();
        }
    }

    private View inflateAppWidget(LauncherAppWidgetInfo item) {
        if (item.hasOptionFlag(LauncherAppWidgetInfo.OPTION_SEARCH_WIDGET)) {
            item.providerName = QsbContainerView.getSearchComponentName(this);
            if (item.providerName == null) {
                getModelWriter().deleteItemFromDatabase(item,
                        "search widget removed because search component cannot be found");
                return null;
            }
        }
        final AppWidgetHostView view;
        if (mIsSafeModeEnabled) {
            view = new PendingAppWidgetHostView(this, item, mIconCache, true);
            prepareAppWidget(view, item);
            return view;
        }

        Object traceToken = TraceHelper.INSTANCE.beginSection("BIND_WIDGET_id=" + item.appWidgetId);

        try {
            final LauncherAppWidgetProviderInfo appWidgetInfo;
            String removalReason = "";

            if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                // If the provider is not ready, bind as a pending widget.
                appWidgetInfo = null;
                removalReason = "the provider isn't ready.";
            } else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                // The widget id is not valid. Try to find the widget based on the provider info.
                appWidgetInfo = mAppWidgetManager.findProvider(item.providerName, item.user);
                if (appWidgetInfo == null) {
                    if (WidgetsModel.GO_DISABLE_WIDGETS) {
                        removalReason = "widgets are disabled on go device.";
                    } else {
                        removalReason =
                                "WidgetManagerHelper cannot find a provider from provider info.";
                    }
                }
            } else {
                appWidgetInfo = mAppWidgetManager.getLauncherAppWidgetInfo(item.appWidgetId);
                if (appWidgetInfo == null) {
                    if (item.appWidgetId <= LauncherAppWidgetInfo.CUSTOM_WIDGET_ID) {
                        removalReason =
                                "CustomWidgetManager cannot find provider from that widget id.";
                    } else {
                        removalReason = "AppWidgetManager cannot find provider for that widget id."
                                + " It could be because AppWidgetService is not available, or the"
                                + " appWidgetId has not been bound to a the provider yet, or you"
                                + " don't have access to that appWidgetId.";
                    }
                }
            }

            // If the provider is ready, but the width is not yet restored, try to restore it.
            if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
                    && (item.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED)) {
                if (appWidgetInfo == null) {
                    getModelWriter().deleteItemFromDatabase(item,
                            "Removing restored widget: id=" + item.appWidgetId
                            + " belongs to component " + item.providerName + " user " + item.user
                            + ", as the provider is null and " + removalReason);
                    return null;
                }

                // If we do not have a valid id, try to bind an id.
                if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                    if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) {
                        // Id has not been allocated yet. Allocate a new id.
                        item.appWidgetId = mAppWidgetHolder.allocateAppWidgetId();
                        item.restoreStatus |= LauncherAppWidgetInfo.FLAG_ID_ALLOCATED;

                        // Also try to bind the widget. If the bind fails, the user will be shown
                        // a click to setup UI, which will ask for the bind permission.
                        PendingAddWidgetInfo pendingInfo =
                                new PendingAddWidgetInfo(appWidgetInfo, item.sourceContainer);
                        pendingInfo.spanX = item.spanX;
                        pendingInfo.spanY = item.spanY;
                        pendingInfo.minSpanX = item.minSpanX;
                        pendingInfo.minSpanY = item.minSpanY;
                        Bundle options = pendingInfo.getDefaultSizeOptions(this);

                        boolean isDirectConfig =
                                item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG);
                        if (isDirectConfig && item.bindOptions != null) {
                            Bundle newOptions = item.bindOptions.getExtras();
                            if (options != null) {
                                newOptions.putAll(options);
                            }
                            options = newOptions;
                        }
                        boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                                item.appWidgetId, appWidgetInfo, options);

                        // We tried to bind once. If we were not able to bind, we would need to
                        // go through the permission dialog, which means we cannot skip the config
                        // activity.
                        item.bindOptions = null;
                        item.restoreStatus &= ~LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG;

                        // Bind succeeded
                        if (success) {
                            // If the widget has a configure activity, it is still needs to set it
                            // up, otherwise the widget is ready to go.
                            item.restoreStatus = (appWidgetInfo.configure == null) || isDirectConfig
                                    ? LauncherAppWidgetInfo.RESTORE_COMPLETED
                                    : LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                        }

                        getModelWriter().updateItemInDatabase(item);
                    }
                } else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY)
                        && (appWidgetInfo.configure == null)) {
                    // The widget was marked as UI not ready, but there is no configure activity to
                    // update the UI.
                    item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;
                    getModelWriter().updateItemInDatabase(item);
                }
                else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY)
                        && appWidgetInfo.configure != null) {
                    if (mAppWidgetManager.isAppWidgetRestored(item.appWidgetId)) {
                        item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;
                        getModelWriter().updateItemInDatabase(item);
                    }
                }
            }

            if (item.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
                // Verify that we own the widget
                if (appWidgetInfo == null) {
                    FileLog.e(TAG, "Removing invalid widget: id=" + item.appWidgetId);
                    getModelWriter().deleteWidgetInfo(item, getAppWidgetHolder(), removalReason);
                    return null;
                }

                item.minSpanX = appWidgetInfo.minSpanX;
                item.minSpanY = appWidgetInfo.minSpanY;
                view = mAppWidgetHolder.createView(this, item.appWidgetId, appWidgetInfo);
            } else if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)
                    && appWidgetInfo != null) {
                mAppWidgetHolder.addPendingView(item.appWidgetId,
                        new PendingAppWidgetHostView(this, item, mIconCache, false));
                view = mAppWidgetHolder.createView(this, item.appWidgetId, appWidgetInfo);
            } else {
                view = new PendingAppWidgetHostView(this, item, mIconCache, false);
            }
            prepareAppWidget(view, item);
        } finally {
            TraceHelper.INSTANCE.endSection(traceToken);
        }

        return view;
    }

    /**
     * Restores a pending widget.
     *
     * @param appWidgetId The app widget id
     */
    private LauncherAppWidgetInfo completeRestoreAppWidget(int appWidgetId, int finalRestoreFlag) {
        LauncherAppWidgetHostView view = mWorkspace.getWidgetForAppWidgetId(appWidgetId);
        if ((view == null) || !(view instanceof PendingAppWidgetHostView)) {
            Log.e(TAG, "Widget update called, when the widget no longer exists.");
            return null;
        }

        LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) view.getTag();
        info.restoreStatus = finalRestoreFlag;
        if (info.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
            info.pendingItemInfo = null;
        }

        if (((PendingAppWidgetHostView) view).isReinflateIfNeeded()) {
            view.reInflate();
        }

        getModelWriter().updateItemInDatabase(info);
        return info;
    }

    public void clearPendingExecutor(ViewOnDrawExecutor executor) {
        if (mPendingExecutor == executor) {
            mPendingExecutor = null;
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.S)
    public void onInitialBindComplete(IntSet boundPages, RunnableList pendingTasks,
            int workspaceItemCount, boolean isBindSync) {
        mSynchronouslyBoundPages = boundPages;
        mPagesToBindSynchronously = new IntSet();

        clearPendingBinds();
        ViewOnDrawExecutor executor = new ViewOnDrawExecutor(pendingTasks);
        mPendingExecutor = executor;
        if (!isInState(ALL_APPS)) {
            mAppsView.getAppsStore().enableDeferUpdates(AllAppsStore.DEFER_UPDATES_NEXT_DRAW);
            pendingTasks.add(() -> mAppsView.getAppsStore().disableDeferUpdates(
                    AllAppsStore.DEFER_UPDATES_NEXT_DRAW));
        }

        if (mOnInitialBindListener != null) {
            getRootView().getViewTreeObserver().removeOnPreDrawListener(mOnInitialBindListener);
            mOnInitialBindListener = null;
        }

        executor.onLoadAnimationCompleted();
        executor.attachTo(this);
        if (Utilities.ATLEAST_S) {
            Trace.endAsyncSection(DISPLAY_WORKSPACE_TRACE_METHOD_NAME,
                    DISPLAY_WORKSPACE_TRACE_COOKIE);
        }
        mStartupLatencyLogger
                .logCardinality(workspaceItemCount)
                .logEnd(isBindSync
                        ? LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC
                        : LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC);
        // In the first rootview's onDraw after onInitialBindComplete(), log end of startup latency.
        getRootView().getViewTreeObserver().addOnDrawListener(
                new ViewTreeObserver.OnDrawListener() {

                    @Override
                    public void onDraw() {
                        mStartupLatencyLogger
                                .logEnd(LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION)
                                .log()
                                .reset();
                        MAIN_EXECUTOR.getHandler().postAtFrontOfQueue(
                                () -> getRootView().getViewTreeObserver()
                                        .removeOnDrawListener(this));
                    }
                });
    }

    /**
     * Callback saying that there aren't any more items to bind.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems(IntSet pagesBoundFirst) {
        Object traceToken = TraceHelper.INSTANCE.beginSection("finishBindingItems");
        mWorkspace.restoreInstanceStateForRemainingPages();

        setWorkspaceLoading(false);

        if (mPendingActivityResult != null) {
            handleActivityResult(mPendingActivityResult.requestCode,
                    mPendingActivityResult.resultCode, mPendingActivityResult.data);
            mPendingActivityResult = null;
        }

        int currentPage = pagesBoundFirst != null && !pagesBoundFirst.isEmpty()
                ? mWorkspace.getPageIndexForScreenId(pagesBoundFirst.getArray().get(0))
                : PagedView.INVALID_PAGE;
        // When undoing the removal of the last item on a page, return to that page.
        // Since we are just resetting the current page without user interaction,
        // override the previous page so we don't log the page switch.
        mWorkspace.setCurrentPage(currentPage, currentPage /* overridePrevPage */);
        mPagesToBindSynchronously = new IntSet();

        // Cache one page worth of icons
        getViewCache().setCacheSize(R.layout.folder_application,
                mDeviceProfile.inv.numFolderColumns * mDeviceProfile.inv.numFolderRows);
        getViewCache().setCacheSize(R.layout.folder_page, 2);

        TraceHelper.INSTANCE.endSection(traceToken);

        mWorkspace.removeExtraEmptyScreen(true);
    }

    private boolean canAnimatePageChange() {
        if (mDragController.isDragging()) {
            return false;
        } else {
            return (SystemClock.uptimeMillis() - mLastTouchUpTime)
                    > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
        }
    }

    /**
     * Similar to {@link #getFirstMatch} but optimized to finding a suitable view for the app close
     * animation.
     *
     * @param preferredItemId The id of the preferred item to match to if it exists.
     * @param packageName The package name of the app to match.
     * @param user The user of the app to match.
     * @param supportsAllAppsState If true and we are in All Apps state, looks for view in All Apps.
     *                             Else we only looks on the workspace.
     */
    public @Nullable View getFirstMatchForAppClose(int preferredItemId, String packageName,
            UserHandle user, boolean supportsAllAppsState) {
        final Predicate<ItemInfo> preferredItem = info ->
                info != null && info.id == preferredItemId;
        final Predicate<ItemInfo> packageAndUserAndApp = info ->
                info != null
                        && info.itemType == ITEM_TYPE_APPLICATION
                        && info.user.equals(user)
                        && info.getTargetComponent() != null
                        && TextUtils.equals(info.getTargetComponent().getPackageName(),
                        packageName);

        if (supportsAllAppsState && isInState(LauncherState.ALL_APPS)) {
            AllAppsRecyclerView activeRecyclerView = mAppsView.getActiveRecyclerView();
            View v = getFirstMatch(Collections.singletonList(activeRecyclerView),
                    preferredItem, packageAndUserAndApp);

            if (v != null && activeRecyclerView.computeVerticalScrollOffset() > 0) {
                RectF locationBounds = new RectF();
                FloatingIconView.getLocationBoundsForView(this, v, false, locationBounds,
                        new Rect());
                if (locationBounds.top < mAppsView.getHeaderBottom()) {
                    // Icon is covered by scrim, return null to play fallback animation.
                    return null;
                }
            }

            return v;
        }

        // Look for the item inside the folder at the current page
        Folder folder = Folder.getOpen(this);
        if (folder != null) {
            View v = getFirstMatch(Collections.singletonList(
                    folder.getContent().getCurrentCellLayout().getShortcutsAndWidgets()),
                    preferredItem,
                    packageAndUserAndApp);
            if (v == null) {
                folder.close(isStarted() && !isForceInvisible());
            } else {
                return v;
            }
        }

        List<ViewGroup> containers = new ArrayList<>(mWorkspace.getPanelCount() + 1);
        containers.add(mWorkspace.getHotseat().getShortcutsAndWidgets());
        mWorkspace.forEachVisiblePage(page
                -> containers.add(((CellLayout) page).getShortcutsAndWidgets()));

        // Order: Preferred item by itself or in folder, then by matching package/user
        return getFirstMatch(containers, preferredItem, forFolderMatch(preferredItem),
                packageAndUserAndApp, forFolderMatch(packageAndUserAndApp));
    }

    /**
     * Finds the first view matching the ordered operators across the given viewgroups in order.
     * @param containers List of ViewGroups to scan, in order of preference.
     * @param operators List of operators, in order starting from best matching operator.
     */
    @Nullable
    private static View getFirstMatch(Iterable<ViewGroup> containers,
            final Predicate<ItemInfo>... operators) {
        for (Predicate<ItemInfo> operator : operators) {
            for (ViewGroup container : containers) {
                View match = mapOverViewGroup(container, operator);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /**
     * Returns the first view matching the operator in the given ViewGroups, or null if none.
     * Forward iteration matters.
     */
    @Nullable
    private static View mapOverViewGroup(ViewGroup container, Predicate<ItemInfo> op) {
        final int itemCount = container.getChildCount();
        for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
            View item = container.getChildAt(itemIdx);
            if (op.test((ItemInfo) item.getTag())) {
                return item;
            }
        }
        return null;
    }

    private ValueAnimator createNewAppBounceAnimation(View v, int i) {
        ValueAnimator bounceAnim = new PropertyListBuilder().alpha(1).scale(1).build(v)
                .setDuration(ItemInstallQueue.NEW_SHORTCUT_BOUNCE_DURATION);
        bounceAnim.setStartDelay(i * ItemInstallQueue.NEW_SHORTCUT_STAGGER_DELAY);
        bounceAnim.setInterpolator(new OvershootInterpolator(BOUNCE_ANIMATION_TENSION));
        return bounceAnim;
    }

    private void announceForAccessibility(@StringRes int stringResId) {
        getDragLayer().announceForAccessibility(getString(stringResId));
    }

    /**
     * Informs us that the overlay (-1 screen, typically), has either become visible or invisible.
     */
    public void onOverlayVisibilityChanged(boolean visible) {
        getStatsLogManager().logger()
                .withSrcState(LAUNCHER_STATE_HOME)
                .withDstState(LAUNCHER_STATE_HOME)
                .withContainerInfo(LauncherAtom.ContainerInfo.newBuilder()
                        .setWorkspace(WorkspaceContainer.newBuilder()
                                .setPageIndex(visible ? 0 : -1))
                        .build())
                .log(visible ? LAUNCHER_SWIPELEFT : LAUNCHER_SWIPERIGHT);
    }

    /**
     * Informs us that the page transition has ended, so that we can react to the newly selected
     * page if we want to.
     */
    public void onPageEndTransition() {}

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    @TargetApi(Build.VERSION_CODES.S)
    @UiThread
    public void bindAllApplications(AppInfo[] apps, int flags,
            Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        Preconditions.assertUIThread();
        AllAppsStore appsStore = mAppsView.getAppsStore();
        appsStore.setApps(apps, flags, packageUserKeytoUidMap);
        PopupContainerWithArrow.dismissInvalidPopup(this);
        if (Utilities.ATLEAST_S) {
            Trace.endAsyncSection(DISPLAY_ALL_APPS_TRACE_METHOD_NAME,
                    DISPLAY_ALL_APPS_TRACE_COOKIE);
        }
    }

    /**
     * Copies LauncherModel's map of activities to shortcut counts to Launcher's. This is necessary
     * because LauncherModel's map is updated in the background, while Launcher runs on the UI.
     */
    @Override
    public void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mPopupDataProvider.setDeepShortcutMap(deepShortcutMapCopy);
    }

    @Override
    public void bindIncrementalDownloadProgressUpdated(AppInfo app) {
        mAppsView.getAppsStore().updateProgressBar(app);
    }

    @Override
    public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets) {
        mWorkspace.widgetsRestored(widgets);
    }

    /**
     * Some shortcuts were updated in the background.
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @param updated list of shortcuts which have changed.
     */
    @Override
    public void bindWorkspaceItemsChanged(List<WorkspaceItemInfo> updated) {
        if (!updated.isEmpty()) {
            mWorkspace.updateWorkspaceItems(updated, this);
            PopupContainerWithArrow.dismissInvalidPopup(this);
        }
    }

    /**
     * Update the state of a package, typically related to install state.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindRestoreItemsChange(HashSet<ItemInfo> updates) {
        mWorkspace.updateRestoreItems(updates, this);
    }

    /**
     * A package was uninstalled/updated.  We take both the super set of packageNames
     * in addition to specific applications to remove, the reason being that
     * this can be called when a package is updated as well.  In that scenario,
     * we only remove specific components from the workspace and hotseat, where as
     * package-removal should clear all items by package name.
     */
    @Override
    public void bindWorkspaceComponentsRemoved(Predicate<ItemInfo> matcher) {
        mWorkspace.removeItemsByMatcher(matcher);
        mDragController.onAppsRemoved(matcher);
        PopupContainerWithArrow.dismissInvalidPopup(this);
    }

    @Override
    public void bindAllWidgets(final List<WidgetsListBaseEntry> allWidgets) {
        mPopupDataProvider.setAllWidgets(allWidgets);
    }

    @Override
    public void bindStringCache(StringCache cache) {
        mStringCache = cache;
        mAppsView.updateWorkUI();
    }

    @Override
    public StringCache getStringCache() {
        return mStringCache;
    }

    /**
     * @param packageUser if null, refreshes all widgets and shortcuts, otherwise only
     *                    refreshes the widgets and shortcuts associated with the given package/user
     */
    public void refreshAndBindWidgetsForPackageUser(@Nullable PackageUserKey packageUser) {
        mModel.refreshAndBindWidgetsAndShortcuts(packageUser);
    }

    /**
     * $ adb shell dumpsys activity com.android.launcher3.Launcher [--all]
     */
    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);

        if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
            writer.println(prefix + "Workspace Items");
            for (int i = 0; i < mWorkspace.getPageCount(); i++) {
                writer.println(prefix + "  Homescreen " + i);

                ViewGroup layout = ((CellLayout) mWorkspace.getPageAt(i)).getShortcutsAndWidgets();
                for (int j = 0; j < layout.getChildCount(); j++) {
                    Object tag = layout.getChildAt(j).getTag();
                    if (tag != null) {
                        writer.println(prefix + "    " + tag.toString());
                    }
                }
            }

            writer.println(prefix + "  Hotseat");
            ViewGroup layout = mHotseat.getShortcutsAndWidgets();
            for (int j = 0; j < layout.getChildCount(); j++) {
                Object tag = layout.getChildAt(j).getTag();
                if (tag != null) {
                    writer.println(prefix + "    " + tag.toString());
                }
            }
        }

        writer.println(prefix + "Misc:");
        dumpMisc(prefix + "\t", writer);
        writer.println(prefix + "\tmWorkspaceLoading=" + mWorkspaceLoading);
        writer.println(prefix + "\tmPendingRequestArgs=" + mPendingRequestArgs
                + " mPendingActivityResult=" + mPendingActivityResult);
        writer.println(prefix + "\tmRotationHelper: " + mRotationHelper);
        writer.println(prefix + "\tmAppWidgetHolder.isListening: "
                + mAppWidgetHolder.isListening());

        // Extra logging for general debugging
        mDragLayer.dump(prefix, writer);
        mStateManager.dump(prefix, writer);
        mPopupDataProvider.dump(prefix, writer);
        mDeviceProfile.dump(this, prefix, writer);

        try {
            FileLog.flushAll(writer);
        } catch (Exception e) {
            // Ignore
        }

        mModel.dumpState(prefix, fd, writer, args);

        if (mLauncherCallbacks != null) {
            mLauncherCallbacks.dump(prefix, fd, writer, args);
        }
        mOverlayManager.dump(prefix, writer);
    }

    @Override
    public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {

        ArrayList<KeyboardShortcutInfo> shortcutInfos = new ArrayList<>();
        if (isInState(NORMAL)) {
            shortcutInfos.add(new KeyboardShortcutInfo(getString(R.string.all_apps_button_label),
                    KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON));
            shortcutInfos.add(new KeyboardShortcutInfo(getString(R.string.widget_button_text),
                    KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON));
        }
        getSupportedActions(this,  getCurrentFocus()).forEach(la ->
                shortcutInfos.add(new KeyboardShortcutInfo(
                        la.accessibilityAction.getLabel(), la.keyCode, KeyEvent.META_CTRL_ON)));
        if (!shortcutInfos.isEmpty()) {
            data.add(new KeyboardShortcutGroup(getString(R.string.home_screen), shortcutInfos));
        }

        super.onProvideKeyboardShortcuts(data, menu, deviceId);
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_A:
                    if (isInState(NORMAL)) {
                        getStateManager().goToState(ALL_APPS);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_W:
                    if (isInState(NORMAL)) {
                        OptionsPopupView.openWidgets(this);
                        return true;
                    }
                    break;
                default:
                    for (LauncherAction la : getSupportedActions(this, getCurrentFocus())) {
                        if (la.keyCode == keyCode) {
                            return la.invokeFromKeyboard(getCurrentFocus());
                        }
                    }
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            // Close any open floating views.
            closeOpenViews();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // KEYCODE_MENU is sent by some tests, for example
            // LauncherJankTests#testWidgetsContainerFling. Don't just remove its handling.
            if (!mDragController.isDragging() && !mWorkspace.isSwitchingState() &&
                    isInState(NORMAL)) {
                // Close any open floating views.
                closeOpenViews();

                // Setting the touch point to (-1, -1) will show the options popup in the center of
                // the screen.
                if (Utilities.isRunningInTestHarness()) {
                    Log.d(TestProtocol.PERMANENT_DIAG_TAG, "Opening options popup on key up");
                }
                showDefaultOptions(-1, -1);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Shows the default options popup
     */
    public void showDefaultOptions(float x, float y) {
        OptionsPopupView.show(this, getPopupTarget(x, y), OptionsPopupView.getOptions(this),
                false);
    }

    /**
     * Returns target rectangle for anchoring a popup menu.
     */
    protected RectF getPopupTarget(float x, float y) {
        float halfSize = getResources().getDimension(R.dimen.options_menu_thumb_size) / 2;
        if (x < 0 || y < 0) {
            x = mDragLayer.getWidth() / 2;
            y = mDragLayer.getHeight() / 2;
        }
        return new RectF(x - halfSize, y - halfSize, x + halfSize, y + halfSize);
    }

    @Override
    public boolean canUseMultipleShadesForPopup() {
        return getTopOpenViewWithType(this, TYPE_FOLDER) == null
                && getStateManager().getState() != LauncherState.ALL_APPS;
    }

    @Override
    protected void collectStateHandlers(List<StateHandler> out) {
        out.add(getAllAppsController());
        out.add(getWorkspace());
    }

    public TouchController[] createTouchControllers() {
        return new TouchController[] {getDragController(), new AllAppsSwipeController(this)};
    }

    public void useFadeOutAnimationForLauncherStart(CancellationSignal signal) { }

    public void onDragLayerHierarchyChanged() {
        updateDisallowBack();
    }

    private void updateDisallowBack() {
        if (DESKTOP_MODE_1_SUPPORTED || DESKTOP_MODE_2_SUPPORTED) {
            // Do not disable back in launcher when prototype behavior is enabled
            return;
        }
        LauncherRootView rv = getRootView();
        if (rv != null) {
            boolean disableBack = getStateManager().getState() == NORMAL
                    && AbstractFloatingView.getTopOpenView(this) == null;
            rv.setDisallowBackGesture(disableBack);
        }
    }

    @Override
    public void returnToHomescreen() {
        super.returnToHomescreen();
        getStateManager().goToState(LauncherState.NORMAL);
    }

    private void closeOpenViews() {
        closeOpenViews(true);
    }

    protected void closeOpenViews(boolean animate) {
        AbstractFloatingView.closeAllOpenViews(this, animate);
    }

    public Stream<SystemShortcut.Factory> getSupportedShortcuts() {
        return Stream.of(APP_INFO, WIDGETS, INSTALL);
    }

    protected LauncherAccessibilityDelegate createAccessibilityDelegate() {
        return new LauncherAccessibilityDelegate(this);
    }

    /** Enables/disabled the hotseat prediction icon long press edu for testing. */
    @VisibleForTesting
    public void enableHotseatEdu(boolean enable) {}

    /**
     * @see LauncherState#getOverviewScaleAndOffset(Launcher)
     */
    public float[] getNormalOverviewScaleAndOffset() {
        return new float[] {NO_SCALE, NO_OFFSET};
    }

    public static Launcher getLauncher(Context context) {
        return fromContext(context);
    }

    /**
     * Just a wrapper around the type cast to allow easier tracking of calls.
     */
    public static <T extends Launcher> T cast(ActivityContext activityContext) {
        return (T) activityContext;
    }

    public boolean supportsAdaptiveIconAnimation(View clickedView) {
        return false;
    }

    public DragOptions getDefaultWorkspaceDragOptions() {
        return new DragOptions();
    }

    /**
     * Animates Launcher elements during a transition to the All Apps page.
     *
     * @param progress Transition progress from 0 to 1; where 0 => home and 1 => all apps.
     */
    public void onAllAppsTransition(float progress) {
        // No-Op
    }

    /**
     * Animates Launcher elements during a transition to the Widgets pages.
     *
     * @param progress Transition progress from 0 to 1; where 0 => home and 1 => widgets.
     */
    public void onWidgetsTransition(float progress) {
        float scale = Utilities.mapToRange(progress, 0f, 1f, 1f,
                mDeviceProfile.bottomSheetWorkspaceScale, EMPHASIZED);
        WORKSPACE_WIDGET_SCALE.set(getWorkspace(), scale);
        HOTSEAT_WIDGET_SCALE.set(getHotseat(), scale);
    }

    private static class NonConfigInstance {
        public Configuration config;
    }

    @Override
    public StatsLogManager getStatsLogManager() {
        return super.getStatsLogManager().withDefaultInstanceId(mAllAppsSessionLogId);
    }

    /**
     * Returns the current popup for testing, if any.
     */
    @VisibleForTesting
    @Nullable
    public ArrowPopup<?> getOptionsPopup() {
        return findViewById(R.id.popup_container);
    }

    /** Pauses view updates that should not be run during the app launch animation. */
    public void pauseExpensiveViewUpdates() {
        // Pause page indicator animations as they lead to layer trashing.
        getWorkspace().getPageIndicator().pauseAnimations();

        getWorkspace().mapOverItems((info, view) -> {
            if (view instanceof LauncherAppWidgetHostView) {
                ((LauncherAppWidgetHostView) view).beginDeferringUpdates();
            }
            return false; // Return false to continue iterating through all the items.
        });
    }

    /** Resumes view updates at the end of the app launch animation. */
    public void resumeExpensiveViewUpdates() {
        getWorkspace().getPageIndicator().skipAnimationsToEnd();

        getWorkspace().mapOverItems((info, view) -> {
            if (view instanceof LauncherAppWidgetHostView) {
                ((LauncherAppWidgetHostView) view).endDeferringUpdates();
            }
            return false; // Return false to continue iterating through all the items.
        });
    }

    /**
     * Returns {@code true} if there are visible tasks with windowing mode set to
     * {@link android.app.WindowConfiguration#WINDOWING_MODE_FREEFORM}
     */
    public boolean areFreeformTasksVisible() {
        return false; // Base launcher does not track freeform tasks
    }
}
