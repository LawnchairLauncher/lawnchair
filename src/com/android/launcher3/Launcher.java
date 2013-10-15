
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.SearchManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Advanceable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.DropTarget.DragObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Default launcher application.
 */
public class Launcher extends Activity
        implements View.OnClickListener, OnLongClickListener, LauncherModel.Callbacks,
                   View.OnTouchListener {
    static final String TAG = "Launcher";
    static final boolean LOGD = false;

    static final boolean PROFILE_STARTUP = false;
    static final boolean DEBUG_WIDGETS = false;
    static final boolean DEBUG_STRICT_MODE = false;
    static final boolean DEBUG_RESUME_TIME = false;
    static final boolean DEBUG_DUMP_LOG = false;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPLICATION = 6;
    private static final int REQUEST_PICK_SHORTCUT = 7;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_PICK_WALLPAPER = 10;

    private static final int REQUEST_BIND_APPWIDGET = 11;

    /**
     * IntentStarter uses request codes starting with this. This must be greater than all activity
     * request codes used internally.
     */
    protected static final int REQUEST_LAST = 100;

    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

    static final int SCREEN_COUNT = 5;
    static final int DEFAULT_SCREEN = 2;

    private static final String PREFERENCES = "launcher.preferences";
    // To turn on these properties, type
    // adb shell setprop log.tag.PROPERTY_NAME [VERBOSE | SUPPRESS]
    static final String FORCE_ENABLE_ROTATION_PROPERTY = "launcher_force_rotate";
    static final String DUMP_STATE_PROPERTY = "launcher_dump_state";

    // The Intent extra that defines whether to ignore the launch animation
    static final String INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
            "com.android.launcher3.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CONTAINER = "launcher.add_container";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cell_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cell_y";
    // Type: boolean
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
    // Type: long
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID = "launcher.rename_folder_id";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_X = "launcher.add_span_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_Y = "launcher.add_span_y";
    // Type: parcelable
    private static final String RUNTIME_STATE_PENDING_ADD_WIDGET_INFO = "launcher.add_widget_info";

    private static final String TOOLBAR_ICON_METADATA_NAME = "com.android.launcher.toolbar_icon";
    private static final String TOOLBAR_SEARCH_ICON_METADATA_NAME =
            "com.android.launcher.toolbar_search_icon";
    private static final String TOOLBAR_VOICE_SEARCH_ICON_METADATA_NAME =
            "com.android.launcher.toolbar_voice_search_icon";

    public static final String SHOW_WEIGHT_WATCHER = "debug.show_mem";
    public static final boolean SHOW_WEIGHT_WATCHER_DEFAULT = false;

    /** The different states that Launcher can be in. */
    private enum State { NONE, WORKSPACE, APPS_CUSTOMIZE, APPS_CUSTOMIZE_SPRING_LOADED };
    private State mState = State.WORKSPACE;
    private AnimatorSet mStateAnimation;

    static final int APPWIDGET_HOST_ID = 1024;
    private static final int EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT = 300;
    private static final int EXIT_SPRINGLOADED_MODE_LONG_TIMEOUT = 600;
    private static final int SHOW_CLING_DURATION = 250;
    private static final int DISMISS_CLING_DURATION = 200;

    private static final Object sLock = new Object();
    private static int sScreen = DEFAULT_SCREEN;

    // How long to wait before the new-shortcut animation automatically pans the workspace
    private static int NEW_APPS_PAGE_MOVE_DELAY = 500;
    private static int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 5;
    private static int NEW_APPS_ANIMATION_DELAY = 500;

    private final BroadcastReceiver mCloseSystemDialogsReceiver
            = new CloseSystemDialogsIntentReceiver();
    private final ContentObserver mWidgetObserver = new AppWidgetResetObserver();

    private LayoutInflater mInflater;

    private Workspace mWorkspace;
    private View mLauncherView;
    private DragLayer mDragLayer;
    private DragController mDragController;
    private View mWeightWatcher;

    private AppWidgetManager mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    private ItemInfo mPendingAddInfo = new ItemInfo();
    private AppWidgetProviderInfo mPendingAddWidgetInfo;

    private int[] mTmpAddItemCellCoordinates = new int[2];

    private FolderInfo mFolderInfo;

    private Hotseat mHotseat;
    private View mOverviewPanel;

    private View mAllAppsButton;

    private SearchDropTargetBar mSearchDropTargetBar;
    private AppsCustomizeTabHost mAppsCustomizeTabHost;
    private AppsCustomizePagedView mAppsCustomizeContent;
    private boolean mAutoAdvanceRunning = false;
    private View mQsbBar;

    private Bundle mSavedState;
    // We set the state in both onCreate and then onNewIntent in some cases, which causes both
    // scroll issues (because the workspace may not have been measured yet) and extra work.
    // Instead, just save the state that we need to restore Launcher to, and commit it in onResume.
    private State mOnResumeState = State.NONE;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mWorkspaceLoading = true;

    private boolean mPaused = true;
    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mOnResumeNeedsLoad;

    private ArrayList<Runnable> mBindOnResumeCallbacks = new ArrayList<Runnable>();
    private ArrayList<Runnable> mOnResumeCallbacks = new ArrayList<Runnable>();

    // Keep track of whether the user has left launcher
    private static boolean sPausedFromUserAction = false;

    private Bundle mSavedInstanceState;

    private LauncherModel mModel;
    private IconCache mIconCache;
    private boolean mUserPresent = true;
    private boolean mVisible = false;
    private boolean mHasFocus = false;
    private boolean mAttached = false;
    private static final boolean DISABLE_CLINGS = false;
    private static final boolean DISABLE_CUSTOM_CLINGS = true;

    private static LocaleConfiguration sLocaleConfiguration = null;

    private static HashMap<Long, FolderInfo> sFolders = new HashMap<Long, FolderInfo>();

    private View.OnTouchListener mHapticFeedbackTouchListener;

    // Related to the auto-advancing of widgets
    private final int ADVANCE_MSG = 1;
    private final int mAdvanceInterval = 20000;
    private final int mAdvanceStagger = 250;
    private long mAutoAdvanceSentTime;
    private long mAutoAdvanceTimeLeft = -1;
    private HashMap<View, AppWidgetProviderInfo> mWidgetsToAdvance =
        new HashMap<View, AppWidgetProviderInfo>();

    // Determines how long to wait after a rotation before restoring the screen orientation to
    // match the sensor state.
    private final int mRestoreScreenOrientationDelay = 500;

    // External icons saved in case of resource changes, orientation, etc.
    private static Drawable.ConstantState[] sGlobalSearchIcon = new Drawable.ConstantState[2];
    private static Drawable.ConstantState[] sVoiceSearchIcon = new Drawable.ConstantState[2];
    private static Drawable.ConstantState[] sAppMarketIcon = new Drawable.ConstantState[2];

    private Intent mAppMarketIntent = null;
    private static final boolean DISABLE_MARKET_BUTTON = true;

    private Drawable mWorkspaceBackgroundDrawable;

    private final ArrayList<Integer> mSynchronouslyBoundPages = new ArrayList<Integer>();

    static final ArrayList<String> sDumpLogs = new ArrayList<String>();
    static Date sDateStamp = new Date();
    static DateFormat sDateFormat =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    static long sRunStart = System.currentTimeMillis();
    static final String CORRUPTION_EMAIL_SENT_KEY = "corruptionEmailSent";

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private SharedPreferences mSharedPrefs;

    private static ArrayList<ComponentName> mIntentsOnWorkspaceFromUpgradePath = null;

    // Holds the page that we need to animate to, and the icon views that we need to animate up
    // when we scroll to that page on resume.
    private ImageView mFolderIconImageView;
    private Bitmap mFolderIconBitmap;
    private Canvas mFolderIconCanvas;
    private Rect mRectForFolderAnimation = new Rect();

    private BubbleTextView mWaitingForResume;

    private HideFromAccessibilityHelper mHideFromAccessibilityHelper
        = new HideFromAccessibilityHelper();

    private Runnable mBuildLayersRunnable = new Runnable() {
        public void run() {
            if (mWorkspace != null) {
                mWorkspace.buildPageHardwareLayers();
            }
        }
    };

    private static ArrayList<PendingAddArguments> sPendingAddList
            = new ArrayList<PendingAddArguments>();

    public static boolean sForceEnableRotation = isPropertyEnabled(FORCE_ENABLE_ROTATION_PROPERTY);

    private static class PendingAddArguments {
        int requestCode;
        Intent intent;
        long container;
        long screenId;
        int cellX;
        int cellY;
    }

    private Stats mStats;

    private static boolean isPropertyEnabled(String propertyName) {
        return Log.isLoggable(propertyName, Log.VERBOSE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        super.onCreate(savedInstanceState);

        LauncherAppState.setApplicationContext(getApplicationContext());
        LauncherAppState app = LauncherAppState.getInstance();

        // Determine the dynamic grid properties
        Point smallestSize = new Point();
        Point largestSize = new Point();
        Point realSize = new Point();
        Display display = getWindowManager().getDefaultDisplay();
        display.getCurrentSizeRange(smallestSize, largestSize);
        display.getRealSize(realSize);
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);
        // Lazy-initialize the dynamic grid
        DeviceProfile grid = app.initDynamicGrid(this,
                Math.min(smallestSize.x, smallestSize.y),
                Math.min(largestSize.x, largestSize.y),
                realSize.x, realSize.y,
                dm.widthPixels, dm.heightPixels);

        // the LauncherApplication should call this, but in case of Instrumentation it might not be present yet
        mSharedPrefs = getSharedPreferences(LauncherAppState.getSharedPreferencesKey(),
                Context.MODE_PRIVATE);
        mModel = app.setLauncher(this);
        mIconCache = app.getIconCache();
        mIconCache.flushInvalidIcons(grid);
        mDragController = new DragController(this);
        mInflater = getLayoutInflater();

        mStats = new Stats(this);

        mAppWidgetManager = AppWidgetManager.getInstance(this);

        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        // If we are getting an onCreate, we can actually preempt onResume and unset mPaused here,
        // this also ensures that any synchronous binding below doesn't re-trigger another
        // LauncherModel load.
        mPaused = false;

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing(
                    Environment.getExternalStorageDirectory() + "/launcher");
        }


        checkForLocaleChange();
        setContentView(R.layout.launcher);

        setupViews();
        grid.layout(this);

        registerContentObservers();

        lockAllApps();

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        // Update customization drawer _after_ restoring the states
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.onPackagesUpdated(
                LauncherModel.getSortedWidgetsAndShortcuts(this));
        }

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }

        if (!mRestoring) {
            if (sPausedFromUserAction) {
                // If the user leaves launcher, then we should just load items asynchronously when
                // they return.
                mModel.startLoader(true, -1);
            } else {
                // We only load the page synchronously if the user rotates (or triggers a
                // configuration change) while launcher is in the foreground
                mModel.startLoader(true, mWorkspace.getCurrentPage());
            }
        }

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);

        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mCloseSystemDialogsReceiver, filter);

        updateGlobalIcons();

        // On large interfaces, we want the screen to auto-rotate based on the current orientation
        unlockScreenOrientation(true);

        showFirstRunCling();
    }

    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        sPausedFromUserAction = true;
    }

    /** To be overriden by subclasses to hint to Launcher that we have custom content */
    protected boolean hasCustomContentToLeft() {
        return false;
    }

    /**
     * To be overridden by subclasses to create the custom content and call
     * {@link #addToCustomContentPage}. This will only be invoked if
     * {@link #hasCustomContentToLeft()} is {@code true}.
     */
    protected void addCustomContentToLeft() {
    }

    /**
     * Invoked by subclasses to signal a change to the {@link #addCustomContentToLeft} value to
     * ensure the custom content page is added or removed if necessary.
     */
    protected void invalidateHasCustomContentToLeft() {
        if (mWorkspace == null || mWorkspace.getScreenOrder().isEmpty()) {
            // Not bound yet, wait for bindScreens to be called.
            return;
        }

        if (!mWorkspace.hasCustomContent() && hasCustomContentToLeft()) {
            // Create the custom content page and call the subclass to populate it.
            mWorkspace.createCustomContentPage();
            addCustomContentToLeft();
        } else if (mWorkspace.hasCustomContent() && !hasCustomContentToLeft()) {
            mWorkspace.removeCustomContentPage();
        }
    }

    private void updateGlobalIcons() {
        boolean searchVisible = false;
        boolean voiceVisible = false;
        // If we have a saved version of these external icons, we load them up immediately
        int coi = getCurrentOrientationIndexForGlobalIcons();
        if (sGlobalSearchIcon[coi] == null || sVoiceSearchIcon[coi] == null ||
                sAppMarketIcon[coi] == null) {
            if (!DISABLE_MARKET_BUTTON) {
                updateAppMarketIcon();
            }
            searchVisible = updateGlobalSearchIcon();
            voiceVisible = updateVoiceSearchIcon(searchVisible);
        }
        if (sGlobalSearchIcon[coi] != null) {
             updateGlobalSearchIcon(sGlobalSearchIcon[coi]);
             searchVisible = true;
        }
        if (sVoiceSearchIcon[coi] != null) {
            updateVoiceSearchIcon(sVoiceSearchIcon[coi]);
            voiceVisible = true;
        }
        if (!DISABLE_MARKET_BUTTON && sAppMarketIcon[coi] != null) {
            updateAppMarketIcon(sAppMarketIcon[coi]);
        }
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.onSearchPackagesChanged(searchVisible, voiceVisible);
        }
    }

    private void checkForLocaleChange() {
        if (sLocaleConfiguration == null) {
            new AsyncTask<Void, Void, LocaleConfiguration>() {
                @Override
                protected LocaleConfiguration doInBackground(Void... unused) {
                    LocaleConfiguration localeConfiguration = new LocaleConfiguration();
                    readConfiguration(Launcher.this, localeConfiguration);
                    return localeConfiguration;
                }

                @Override
                protected void onPostExecute(LocaleConfiguration result) {
                    sLocaleConfiguration = result;
                    checkForLocaleChange();  // recursive, but now with a locale configuration
                }
            }.execute();
            return;
        }

        final Configuration configuration = getResources().getConfiguration();

        final String previousLocale = sLocaleConfiguration.locale;
        final String locale = configuration.locale.toString();

        final int previousMcc = sLocaleConfiguration.mcc;
        final int mcc = configuration.mcc;

        final int previousMnc = sLocaleConfiguration.mnc;
        final int mnc = configuration.mnc;

        boolean localeChanged = !locale.equals(previousLocale) || mcc != previousMcc || mnc != previousMnc;

        if (localeChanged) {
            sLocaleConfiguration.locale = locale;
            sLocaleConfiguration.mcc = mcc;
            sLocaleConfiguration.mnc = mnc;

            mIconCache.flush();

            final LocaleConfiguration localeConfiguration = sLocaleConfiguration;
            new Thread("WriteLocaleConfiguration") {
                @Override
                public void run() {
                    writeConfiguration(Launcher.this, localeConfiguration);
                }
            }.start();
        }
    }

    private static class LocaleConfiguration {
        public String locale;
        public int mcc = -1;
        public int mnc = -1;
    }

    private static void readConfiguration(Context context, LocaleConfiguration configuration) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(context.openFileInput(PREFERENCES));
            configuration.locale = in.readUTF();
            configuration.mcc = in.readInt();
            configuration.mnc = in.readInt();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private static void writeConfiguration(Context context, LocaleConfiguration configuration) {
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(context.openFileOutput(PREFERENCES, MODE_PRIVATE));
            out.writeUTF(configuration.locale);
            out.writeInt(configuration.mcc);
            out.writeInt(configuration.mnc);
            out.flush();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            context.getFileStreamPath(PREFERENCES).delete();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    public Stats getStats() {
        return mStats;
    }

    public LayoutInflater getInflater() {
        return mInflater;
    }

    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !mModel.isLoadingWorkspace();
    }

    static int getScreen() {
        synchronized (sLock) {
            return sScreen;
        }
    }

    static void setScreen(int screen) {
        synchronized (sLock) {
            sScreen = screen;
        }
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private boolean completeAdd(PendingAddArguments args) {
        boolean result = false;
        switch (args.requestCode) {
            case REQUEST_PICK_APPLICATION:
                completeAddApplication(args.intent, args.container, args.screenId, args.cellX,
                        args.cellY);
                break;
            case REQUEST_PICK_SHORTCUT:
                processShortcut(args.intent);
                break;
            case REQUEST_CREATE_SHORTCUT:
                completeAddShortcut(args.intent, args.container, args.screenId, args.cellX,
                        args.cellY);
                result = true;
                break;
            case REQUEST_CREATE_APPWIDGET:
                int appWidgetId = args.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                completeAddAppWidget(appWidgetId, args.container, args.screenId, null, null);
                result = true;
                break;
        }
        // Before adding this resetAddInfo(), after a shortcut was added to a workspace screen,
        // if you turned the screen off and then back while in All Apps, Launcher would not
        // return to the workspace. Clearing mAddInfo.container here fixes this issue
        resetAddInfo();
        return result;
    }

    @Override
    protected void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        // Reset the startActivity waiting flag
        mWaitingForResult = false;

        if (requestCode == REQUEST_BIND_APPWIDGET) {
            int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(appWidgetId, mPendingAddInfo, null, mPendingAddWidgetInfo);
            }
            return;
        } else if (requestCode == REQUEST_PICK_WALLPAPER) {
            if (resultCode == RESULT_OK && mWorkspace.isInOverviewMode()) {
                mWorkspace.exitOverviewMode(false);
            }
            return;
        }

        boolean delayExitSpringLoadedMode = false;
        boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET);

        // We have special handling for widgets
        if (isWidgetDrop) {
            int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (appWidgetId < 0) {
                Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not returned from the \\" +
                        "widget configuration activity.");
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId);
                mWorkspace.stripEmptyScreens();
            } else {
                completeTwoStageWidgetDrop(resultCode, appWidgetId);
            }
            return;
        }

        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.

        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.
        if (resultCode == RESULT_OK && mPendingAddInfo.container != ItemInfo.NO_ID) {
            final PendingAddArguments args = new PendingAddArguments();
            args.requestCode = requestCode;
            args.intent = data;
            args.container = mPendingAddInfo.container;
            args.screenId = mPendingAddInfo.screenId;
            args.cellX = mPendingAddInfo.cellX;
            args.cellY = mPendingAddInfo.cellY;
            if (isWorkspaceLocked()) {
                sPendingAddList.add(args);
            } else {
                delayExitSpringLoadedMode = completeAdd(args);
            }
        } else if (resultCode == RESULT_CANCELED) {
            mWorkspace.stripEmptyScreens();
        }
        mDragLayer.clearAnimatedView();
        // Exit spring loaded mode if necessary after cancelling the configuration of a widget
        exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED), delayExitSpringLoadedMode,
                null);
    }

    private void completeTwoStageWidgetDrop(final int resultCode, final int appWidgetId) {
        CellLayout cellLayout =
                (CellLayout) mWorkspace.getScreenWithId(mPendingAddInfo.screenId);
        Runnable onCompleteRunnable = null;
        int animationType = 0;

        AppWidgetHostView boundWidget = null;
        if (resultCode == RESULT_OK) {
            animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
            final AppWidgetHostView layout = mAppWidgetHost.createView(this, appWidgetId,
                    mPendingAddWidgetInfo);
            boundWidget = layout;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    completeAddAppWidget(appWidgetId, mPendingAddInfo.container,
                            mPendingAddInfo.screenId, layout, null);
                    exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED), false,
                            null);
                }
            };
        } else if (resultCode == RESULT_CANCELED) {
            animationType = Workspace.CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED), false,
                            null);
                }
            };
        }
        if (mDragLayer.getAnimatedView() != null) {
            mWorkspace.animateWidgetDrop(mPendingAddInfo, cellLayout,
                    (DragView) mDragLayer.getAnimatedView(), onCompleteRunnable,
                    animationType, boundWidget, true);
        } else {
            // The animated view may be null in the case of a rotation during widget configuration
            onCompleteRunnable.run();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        FirstFrameAnimatorHelper.setIsVisible(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirstFrameAnimatorHelper.setIsVisible(true);
    }

    @Override
    protected void onResume() {
        long startTime = 0;
        if (DEBUG_RESUME_TIME) {
            startTime = System.currentTimeMillis();
            Log.v(TAG, "Launcher.onResume()");
        }
        super.onResume();

        // Restore the previous launcher state
        if (mOnResumeState == State.WORKSPACE) {
            showWorkspace(false);
        } else if (mOnResumeState == State.APPS_CUSTOMIZE) {
            showAllApps(false, AppsCustomizePagedView.ContentType.Applications, false);
        }
        mOnResumeState = State.NONE;

        // Background was set to gradient in onPause(), restore to black if in all apps.
        setWorkspaceBackground(mState == State.WORKSPACE);

        mPaused = false;
        sPausedFromUserAction = false;
        if (mRestoring || mOnResumeNeedsLoad) {
            mWorkspaceLoading = true;
            mModel.startLoader(true, -1);
            mRestoring = false;
            mOnResumeNeedsLoad = false;
        }
        if (mBindOnResumeCallbacks.size() > 0) {
            // We might have postponed some bind calls until onResume (see waitUntilResume) --
            // execute them here
            long startTimeCallbacks = 0;
            if (DEBUG_RESUME_TIME) {
                startTimeCallbacks = System.currentTimeMillis();
            }

            if (mAppsCustomizeContent != null) {
                mAppsCustomizeContent.setBulkBind(true);
            }
            for (int i = 0; i < mBindOnResumeCallbacks.size(); i++) {
                mBindOnResumeCallbacks.get(i).run();
            }
            if (mAppsCustomizeContent != null) {
                mAppsCustomizeContent.setBulkBind(false);
            }
            mBindOnResumeCallbacks.clear();
            if (DEBUG_RESUME_TIME) {
                Log.d(TAG, "Time spent processing callbacks in onResume: " +
                    (System.currentTimeMillis() - startTimeCallbacks));
            }
        }
        if (mOnResumeCallbacks.size() > 0) {
            for (int i = 0; i < mOnResumeCallbacks.size(); i++) {
                mOnResumeCallbacks.get(i).run();
            }
            mOnResumeCallbacks.clear();
        }

        // Reset the pressed state of icons that were locked in the press state while activities
        // were launching
        if (mWaitingForResume != null) {
            // Resets the previous workspace icon press state
            mWaitingForResume.setStayPressed(false);
        }
        if (mAppsCustomizeContent != null) {
            // Resets the previous all apps icon press state
            mAppsCustomizeContent.resetDrawableState();
        }

        // It is possible that widgets can receive updates while launcher is not in the foreground.
        // Consequently, the widgets will be inflated in the orientation of the foreground activity
        // (framework issue). On resuming, we ensure that any widgets are inflated for the current
        // orientation.
        getWorkspace().reinflateWidgetsIfNecessary();

        // Process any items that were added while Launcher was away.
        InstallShortcutReceiver.disableAndFlushInstallQueue(this);

        // Update the voice search button proxy
        updateVoiceButtonProxyVisible(false);

        // Again, as with the above scenario, it's possible that one or more of the global icons
        // were updated in the wrong orientation.
        updateGlobalIcons();
        if (DEBUG_RESUME_TIME) {
            Log.d(TAG, "Time spent in onResume: " + (System.currentTimeMillis() - startTime));
        }

        if (mWorkspace.getCustomContentCallbacks() != null) {
            // If we are resuming and the custom content is the current page, we call onShow().
            // It is also poassible that onShow will instead be called slightly after first layout
            // if PagedView#setRestorePage was set to the custom content page in onCreate().
            if (mWorkspace.isOnOrMovingToCustomContent()) {
                mWorkspace.getCustomContentCallbacks().onShow();
            }
        }
        mWorkspace.updateInteractionForState();
        mWorkspace.onResume();
    }

    @Override
    protected void onPause() {
        // Ensure that items added to Launcher are queued until Launcher returns
        InstallShortcutReceiver.enableInstallQueue();

        super.onPause();
        mPaused = true;
        mDragController.cancelDrag();
        mDragController.resetLastGestureUpTime();

        // We call onHide() aggressively. The custom content callbacks should be able to
        // debounce excess onHide calls.
        if (mWorkspace.getCustomContentCallbacks() != null) {
            mWorkspace.getCustomContentCallbacks().onHide();
        }
    }

    protected void onFinishBindingItems() {
        if (mWorkspace != null && hasCustomContentToLeft() && mWorkspace.hasCustomContent()) {
            addCustomContentToLeft();
        }
    }

    QSBScroller mQsbScroller = new QSBScroller() {
        int scrollY = 0;

        @Override
        public void setScrollY(int scroll) {
            scrollY = scroll;

            if (mWorkspace.isOnOrMovingToCustomContent()) {
                mSearchDropTargetBar.setTranslationY(- scrollY);
                getQsbBar().setTranslationY(-scrollY);
            }
        }
    };

    public void resetQSBScroll() {
        mSearchDropTargetBar.animate().translationY(0).start();
        getQsbBar().animate().translationY(0).start();
    }

    public interface CustomContentCallbacks {
        // Custom content is completely shown
        public void onShow();

        // Custom content is completely hidden
        public void onHide();

        // Custom content scroll progress changed. From 0 (not showing) to 1 (fully showing).
        public void onScrollProgressChanged(float progress);
    }

    protected void startSettings() {
    }

    public interface QSBScroller {
        public void setScrollY(int scrollY);
    }

    public QSBScroller addToCustomContentPage(View customContent,
            CustomContentCallbacks callbacks, String description) {
        mWorkspace.addToCustomContentPage(customContent, callbacks, description);
        return mQsbScroller;
    }

    // The custom content needs to offset its content to account for the QSB
    public int getTopOffsetForCustomContent() {
        return mWorkspace.getPaddingTop();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Flag the loader to stop early before switching
        mModel.stopLoader();
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.surrender();
        }
        return Boolean.TRUE;
    }

    // We can't hide the IME if it was forced open.  So don't bother
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mHasFocus = hasFocus;
    }

    private boolean acceptFilter() {
        final InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        return !inputManager.isFullscreenMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int uniChar = event.getUnicodeChar();
        final boolean handled = super.onKeyDown(keyCode, event);
        final boolean isKeyNotWhitespace = uniChar > 0 && !Character.isWhitespace(uniChar);
        if (!handled && acceptFilter() && isKeyNotWhitespace) {
            boolean gotKey = TextKeyListener.getInstance().onKeyDown(mWorkspace, mDefaultKeySsb,
                    keyCode, event);
            if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
                // something usable has been typed - start a search
                // the typed text will be retrieved and cleared by
                // showSearchDialog()
                // If there are multiple keystrokes before the search dialog takes focus,
                // onSearchRequested() will be called for every keystroke,
                // but it is idempotent, so it's fine.
                return onSearchRequested();
            }
        }

        // Eat the long press event so the keyboard doesn't come up.
        if (keyCode == KeyEvent.KEYCODE_MENU && event.isLongPress()) {
            return true;
        }

        return handled;
    }

    private String getTypedText() {
        return mDefaultKeySsb.toString();
    }

    private void clearTypedText() {
        mDefaultKeySsb.clear();
        mDefaultKeySsb.clearSpans();
        Selection.setSelection(mDefaultKeySsb, 0);
    }

    /**
     * Given the integer (ordinal) value of a State enum instance, convert it to a variable of type
     * State
     */
    private static State intToState(int stateOrdinal) {
        State state = State.WORKSPACE;
        final State[] stateValues = State.values();
        for (int i = 0; i < stateValues.length; i++) {
            if (stateValues[i].ordinal() == stateOrdinal) {
                state = stateValues[i];
                break;
            }
        }
        return state;
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

        State state = intToState(savedState.getInt(RUNTIME_STATE, State.WORKSPACE.ordinal()));
        if (state == State.APPS_CUSTOMIZE) {
            mOnResumeState = State.APPS_CUSTOMIZE;
        }

        int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN,
                PagedView.INVALID_RESTORE_PAGE);
        if (currentScreen != PagedView.INVALID_RESTORE_PAGE) {
            mWorkspace.setRestorePage(currentScreen);
        }

        final long pendingAddContainer = savedState.getLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, -1);
        final long pendingAddScreen = savedState.getLong(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);

        if (pendingAddContainer != ItemInfo.NO_ID && pendingAddScreen > -1) {
            mPendingAddInfo.container = pendingAddContainer;
            mPendingAddInfo.screenId = pendingAddScreen;
            mPendingAddInfo.cellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            mPendingAddInfo.cellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            mPendingAddInfo.spanX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_X);
            mPendingAddInfo.spanY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y);
            mPendingAddWidgetInfo = savedState.getParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO);
            mWaitingForResult = true;
            mRestoring = true;
        }

        boolean renameFolder = savedState.getBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
        if (renameFolder) {
            long id = savedState.getLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID);
            mFolderInfo = mModel.getFolderById(this, sFolders, id);
            mRestoring = true;
        }

        // Restore the AppsCustomize tab
        if (mAppsCustomizeTabHost != null) {
            String curTab = savedState.getString("apps_customize_currentTab");
            if (curTab != null) {
                mAppsCustomizeTabHost.setContentTypeImmediate(
                        mAppsCustomizeTabHost.getContentTypeForTabTag(curTab));
                mAppsCustomizeContent.loadAssociatedPages(
                        mAppsCustomizeContent.getCurrentPage());
            }

            int currentIndex = savedState.getInt("apps_customize_currentIndex");
            mAppsCustomizeContent.restorePageForIndex(currentIndex);
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        final DragController dragController = mDragController;

        mLauncherView = findViewById(R.id.launcher);
        mDragLayer = (DragLayer) findViewById(R.id.drag_layer);
        mWorkspace = (Workspace) mDragLayer.findViewById(R.id.workspace);

        mLauncherView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mWorkspaceBackgroundDrawable = getResources().getDrawable(R.drawable.workspace_bg);

        // Setup the drag layer
        mDragLayer.setup(this, dragController);

        // Setup the hotseat
        mHotseat = (Hotseat) findViewById(R.id.hotseat);
        if (mHotseat != null) {
            mHotseat.setup(this);
            mHotseat.setOnLongClickListener(this);
        }

        mOverviewPanel = findViewById(R.id.overview_panel);
        View widgetButton = findViewById(R.id.widget_button);
        widgetButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showAllApps(true, AppsCustomizePagedView.ContentType.Widgets, true);
            }
        });
        widgetButton.setOnTouchListener(getHapticFeedbackTouchListener());

        View wallpaperButton = findViewById(R.id.wallpaper_button);
        wallpaperButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                startWallpaper();
            }
        });
        wallpaperButton.setOnTouchListener(getHapticFeedbackTouchListener());

        View settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                startSettings();
            }
        });
        settingsButton.setOnTouchListener(getHapticFeedbackTouchListener());
        mOverviewPanel.setAlpha(0f);

        // Setup the workspace
        mWorkspace.setHapticFeedbackEnabled(false);
        mWorkspace.setOnLongClickListener(this);
        mWorkspace.setup(dragController);
        dragController.addDragListener(mWorkspace);

        // Get the search/delete bar
        mSearchDropTargetBar = (SearchDropTargetBar) mDragLayer.findViewById(R.id.qsb_bar);

        // Setup AppsCustomize
        mAppsCustomizeTabHost = (AppsCustomizeTabHost) findViewById(R.id.apps_customize_pane);
        mAppsCustomizeContent = (AppsCustomizePagedView)
                mAppsCustomizeTabHost.findViewById(R.id.apps_customize_pane_content);
        mAppsCustomizeContent.setup(this, dragController);

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        dragController.setDragScoller(mWorkspace);
        dragController.setScrollView(mDragLayer);
        dragController.setMoveTarget(mWorkspace);
        dragController.addDropTarget(mWorkspace);
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.setup(this, dragController);
        }

        if (getResources().getBoolean(R.bool.debug_memory_enabled)) {
            Log.v(TAG, "adding WeightWatcher");
            mWeightWatcher = new WeightWatcher(this);
            mWeightWatcher.setAlpha(0.5f);
            ((FrameLayout) mLauncherView).addView(mWeightWatcher,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM)
            );

            boolean show = shouldShowWeightWatcher();
            mWeightWatcher.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from R.layout.application.
     */
    View createShortcut(ShortcutInfo info) {
        return createShortcut(R.layout.application,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param layoutResId The id of the XML layout used to create the shortcut.
     * @param parent The group the shortcut belongs to.
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from layoutResId.
     */
    View createShortcut(int layoutResId, ViewGroup parent, ShortcutInfo info) {
        BubbleTextView favorite = (BubbleTextView) mInflater.inflate(layoutResId, parent, false);
        favorite.applyFromShortcutInfo(info, mIconCache);
        favorite.setOnClickListener(this);
        return favorite;
    }

    /**
     * Add an application shortcut to the workspace.
     *
     * @param data The intent describing the application.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    void completeAddApplication(Intent data, long container, long screenId, int cellX, int cellY) {
        final int[] cellXY = mTmpAddItemCellCoordinates;
        final CellLayout layout = getCellLayout(container, screenId);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
        } else if (!layout.findCellForSpan(cellXY, 1, 1)) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        final ShortcutInfo info = mModel.getShortcutInfo(getPackageManager(), data, this);

        if (info != null) {
            info.setActivity(this, data.getComponent(), Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            info.container = ItemInfo.NO_ID;
            mWorkspace.addApplicationShortcut(info, layout, container, screenId, cellXY[0], cellXY[1],
                    isWorkspaceLocked(), cellX, cellY);
        } else {
            Log.e(TAG, "Couldn't find ActivityInfo for selected application: " + data);
        }
    }

    /**
     * Add a shortcut to the workspace.
     *
     * @param data The intent describing the shortcut.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    private void completeAddShortcut(Intent data, long container, long screenId, int cellX,
            int cellY) {
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        CellLayout layout = getCellLayout(container, screenId);

        boolean foundCellSpan = false;

        ShortcutInfo info = mModel.infoFromShortcutIntent(this, data, null);
        if (info == null) {
            return;
        }
        final View view = createShortcut(info);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
            foundCellSpan = true;

            // If appropriate, either create a folder or add to an existing folder
            if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                    true, null,null)) {
                return;
            }
            DragObject dragObject = new DragObject();
            dragObject.dragInfo = info;
            if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0, dragObject,
                    true)) {
                return;
            }
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(touchXY[0], touchXY[1], 1, 1, cellXY);
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
        }

        if (!foundCellSpan) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        LauncherModel.addItemToDatabase(this, info, container, screenId, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            mWorkspace.addInScreen(view, container, screenId, cellXY[0], cellXY[1], 1, 1,
                    isWorkspaceLocked());
        }
    }

    static int[] getSpanForWidget(Context context, ComponentName component, int minWidth,
            int minHeight) {
        Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(context, component, null);
        // We want to account for the extra amount of padding that we are adding to the widget
        // to ensure that it gets the full amount of space that it has requested
        int requiredWidth = minWidth + padding.left + padding.right;
        int requiredHeight = minHeight + padding.top + padding.bottom;
        return CellLayout.rectToCell(requiredWidth, requiredHeight, null);
    }

    static int[] getSpanForWidget(Context context, AppWidgetProviderInfo info) {
        return getSpanForWidget(context, info.provider, info.minWidth, info.minHeight);
    }

    static int[] getMinSpanForWidget(Context context, AppWidgetProviderInfo info) {
        return getSpanForWidget(context, info.provider, info.minResizeWidth, info.minResizeHeight);
    }

    static int[] getSpanForWidget(Context context, PendingAddWidgetInfo info) {
        return getSpanForWidget(context, info.componentName, info.minWidth, info.minHeight);
    }

    static int[] getMinSpanForWidget(Context context, PendingAddWidgetInfo info) {
        return getSpanForWidget(context, info.componentName, info.minResizeWidth,
                info.minResizeHeight);
    }

    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     * @param cellInfo The position on screen where to create the widget.
     */
    private void completeAddAppWidget(final int appWidgetId, long container, long screenId,
            AppWidgetHostView hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null) {
            appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        }

        // Calculate the grid spans needed to fit this widget
        CellLayout layout = getCellLayout(container, screenId);

        int[] minSpanXY = getMinSpanForWidget(this, appWidgetInfo);
        int[] spanXY = getSpanForWidget(this, appWidgetInfo);

        // Try finding open space on Launcher screen
        // We have saved the position to which the widget was dragged-- this really only matters
        // if we are placing widgets on a "spring-loaded" screen
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        int[] finalSpan = new int[2];
        boolean foundCellSpan = false;
        if (mPendingAddInfo.cellX >= 0 && mPendingAddInfo.cellY >= 0) {
            cellXY[0] = mPendingAddInfo.cellX;
            cellXY[1] = mPendingAddInfo.cellY;
            spanXY[0] = mPendingAddInfo.spanX;
            spanXY[1] = mPendingAddInfo.spanY;
            foundCellSpan = true;
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(
                    touchXY[0], touchXY[1], minSpanXY[0], minSpanXY[1], spanXY[0],
                    spanXY[1], cellXY, finalSpan);
            spanXY[0] = finalSpan[0];
            spanXY[1] = finalSpan[1];
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, minSpanXY[0], minSpanXY[1]);
        }

        if (!foundCellSpan) {
            if (appWidgetId != -1) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new Thread("deleteAppWidgetId") {
                    public void run() {
                        mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                    }
                }.start();
            }
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        // Build Launcher-specific widget info and save to database
        LauncherAppWidgetInfo launcherInfo = new LauncherAppWidgetInfo(appWidgetId,
                appWidgetInfo.provider);
        launcherInfo.spanX = spanXY[0];
        launcherInfo.spanY = spanXY[1];
        launcherInfo.minSpanX = mPendingAddInfo.minSpanX;
        launcherInfo.minSpanY = mPendingAddInfo.minSpanY;

        LauncherModel.addItemToDatabase(this, launcherInfo,
                container, screenId, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            if (hostView == null) {
                // Perform actual inflation because we're live
                launcherInfo.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
                launcherInfo.hostView.setAppWidget(appWidgetId, appWidgetInfo);
            } else {
                // The AppWidgetHostView has already been inflated and instantiated
                launcherInfo.hostView = hostView;
            }

            launcherInfo.hostView.setTag(launcherInfo);
            launcherInfo.hostView.setVisibility(View.VISIBLE);
            launcherInfo.notifyWidgetSizeChanged(this);

            mWorkspace.addInScreen(launcherInfo.hostView, container, screenId, cellXY[0], cellXY[1],
                    launcherInfo.spanX, launcherInfo.spanY, isWorkspaceLocked());

            addWidgetToAutoAdvanceIfNeeded(launcherInfo.hostView, appWidgetInfo);
        }
        resetAddInfo();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mUserPresent = false;
                mDragLayer.clearAllResizeFrames();
                updateRunning();

                // Reset AllApps to its initial state only if we are not in the middle of
                // processing a multi-step drop
                if (mAppsCustomizeTabHost != null && mPendingAddInfo.container == ItemInfo.NO_ID) {
                    showWorkspace(false);
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mUserPresent = true;
                updateRunning();
            }
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Listen for broadcasts related to user-presence
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mReceiver, filter);
        FirstFrameAnimatorHelper.initializeDrawListener(getWindow().getDecorView());
        mAttached = true;
        mVisible = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;

        if (mAttached) {
            unregisterReceiver(mReceiver);
            mAttached = false;
        }
        updateRunning();
    }

    public void onWindowVisibilityChanged(int visibility) {
        mVisible = visibility == View.VISIBLE;
        updateRunning();
        // The following code used to be in onResume, but it turns out onResume is called when
        // you're in All Apps and click home to go to the workspace. onWindowVisibilityChanged
        // is a more appropriate event to handle
        if (mVisible) {
            mAppsCustomizeTabHost.onWindowVisible();
            if (!mWorkspaceLoading) {
                final ViewTreeObserver observer = mWorkspace.getViewTreeObserver();
                // We want to let Launcher draw itself at least once before we force it to build
                // layers on all the workspace pages, so that transitioning to Launcher from other
                // apps is nice and speedy.
                observer.addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                    private boolean mStarted = false;
                    public void onDraw() {
                        if (mStarted) return;
                        mStarted = true;
                        // We delay the layer building a bit in order to give
                        // other message processing a time to run.  In particular
                        // this avoids a delay in hiding the IME if it was
                        // currently shown, because doing that may involve
                        // some communication back with the app.
                        mWorkspace.postDelayed(mBuildLayersRunnable, 500);
                        final ViewTreeObserver.OnDrawListener listener = this;
                        mWorkspace.post(new Runnable() {
                                public void run() {
                                    if (mWorkspace != null &&
                                            mWorkspace.getViewTreeObserver() != null) {
                                        mWorkspace.getViewTreeObserver().
                                                removeOnDrawListener(listener);
                                    }
                                }
                            });
                        return;
                    }
                });
            }
            // When Launcher comes back to foreground, a different Activity might be responsible for
            // the app market intent, so refresh the icon
            if (!DISABLE_MARKET_BUTTON) {
                updateAppMarketIcon();
            }
            clearTypedText();
        }
    }

    private void sendAdvanceMessage(long delay) {
        mHandler.removeMessages(ADVANCE_MSG);
        Message msg = mHandler.obtainMessage(ADVANCE_MSG);
        mHandler.sendMessageDelayed(msg, delay);
        mAutoAdvanceSentTime = System.currentTimeMillis();
    }

    private void updateRunning() {
        boolean autoAdvanceRunning = mVisible && mUserPresent && !mWidgetsToAdvance.isEmpty();
        if (autoAdvanceRunning != mAutoAdvanceRunning) {
            mAutoAdvanceRunning = autoAdvanceRunning;
            if (autoAdvanceRunning) {
                long delay = mAutoAdvanceTimeLeft == -1 ? mAdvanceInterval : mAutoAdvanceTimeLeft;
                sendAdvanceMessage(delay);
            } else {
                if (!mWidgetsToAdvance.isEmpty()) {
                    mAutoAdvanceTimeLeft = Math.max(0, mAdvanceInterval -
                            (System.currentTimeMillis() - mAutoAdvanceSentTime));
                }
                mHandler.removeMessages(ADVANCE_MSG);
                mHandler.removeMessages(0); // Remove messages sent using postDelayed()
            }
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ADVANCE_MSG) {
                int i = 0;
                for (View key: mWidgetsToAdvance.keySet()) {
                    final View v = key.findViewById(mWidgetsToAdvance.get(key).autoAdvanceViewId);
                    final int delay = mAdvanceStagger * i;
                    if (v instanceof Advanceable) {
                       postDelayed(new Runnable() {
                           public void run() {
                               ((Advanceable) v).advance();
                           }
                       }, delay);
                    }
                    i++;
                }
                sendAdvanceMessage(mAdvanceInterval);
            }
        }
    };

    void addWidgetToAutoAdvanceIfNeeded(View hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null || appWidgetInfo.autoAdvanceViewId == -1) return;
        View v = hostView.findViewById(appWidgetInfo.autoAdvanceViewId);
        if (v instanceof Advanceable) {
            mWidgetsToAdvance.put(hostView, appWidgetInfo);
            ((Advanceable) v).fyiWillBeAdvancedByHostKThx();
            updateRunning();
        }
    }

    void removeWidgetToAutoAdvance(View hostView) {
        if (mWidgetsToAdvance.containsKey(hostView)) {
            mWidgetsToAdvance.remove(hostView);
            updateRunning();
        }
    }

    public void removeAppWidget(LauncherAppWidgetInfo launcherInfo) {
        removeWidgetToAutoAdvance(launcherInfo.hostView);
        launcherInfo.hostView = null;
    }

    void showOutOfSpaceMessage(boolean isHotseatLayout) {
        int strId = (isHotseatLayout ? R.string.hotseat_out_of_space : R.string.out_of_space);
        Toast.makeText(this, getString(strId), Toast.LENGTH_SHORT).show();
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    public void closeSystemDialogs() {
        getWindow().closeAllPanels();

        // Whatever we were doing is hereby canceled.
        mWaitingForResult = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        long startTime = 0;
        if (DEBUG_RESUME_TIME) {
            startTime = System.currentTimeMillis();
        }
        super.onNewIntent(intent);

        // Close the menu
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            // also will cancel mWaitingForResult.
            closeSystemDialogs();

            final boolean alreadyOnHome = mHasFocus && ((intent.getFlags() &
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                    != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

            if (mWorkspace == null) {
                // Can be cases where mWorkspace is null, this prevents a NPE
                return;
            }
            Folder openFolder = mWorkspace.getOpenFolder();
            // In all these cases, only animate if we're already on home
            mWorkspace.exitWidgetResizeMode();
            if (alreadyOnHome && mState == State.WORKSPACE && !mWorkspace.isTouchActive() &&
                    openFolder == null) {
                mWorkspace.moveToDefaultScreen(true);
            }

            closeFolder();
            exitSpringLoadedDragMode();

            // If we are already on home, then just animate back to the workspace,
            // otherwise, just wait until onResume to set the state back to Workspace
            if (alreadyOnHome) {
                showWorkspace(true);
            } else {
                mOnResumeState = State.WORKSPACE;
            }

            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager)getSystemService(
                        INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            // Reset the apps customize page
            if (mAppsCustomizeTabHost != null) {
                mAppsCustomizeTabHost.reset();
            }
        }

        if (DEBUG_RESUME_TIME) {
            Log.d(TAG, "Time spent in onNewIntent: " + (System.currentTimeMillis() - startTime));
        }
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        for (int page: mSynchronouslyBoundPages) {
            mWorkspace.restoreInstanceStateForChild(page);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mWorkspace.getChildCount() > 0) {
            outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getRestorePage());
        }
        super.onSaveInstanceState(outState);

        outState.putInt(RUNTIME_STATE, mState.ordinal());
        // We close any open folder since it will not be re-opened, and we need to make sure
        // this state is reflected.
        closeFolder();

        if (mPendingAddInfo.container != ItemInfo.NO_ID && mPendingAddInfo.screenId > -1 &&
                mWaitingForResult) {
            outState.putLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, mPendingAddInfo.container);
            outState.putLong(RUNTIME_STATE_PENDING_ADD_SCREEN, mPendingAddInfo.screenId);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, mPendingAddInfo.cellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, mPendingAddInfo.cellY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_X, mPendingAddInfo.spanX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y, mPendingAddInfo.spanY);
            outState.putParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO, mPendingAddWidgetInfo);
        }

        if (mFolderInfo != null && mWaitingForResult) {
            outState.putBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, true);
            outState.putLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID, mFolderInfo.id);
        }

        // Save the current AppsCustomize tab
        if (mAppsCustomizeTabHost != null) {
            String currentTabTag = mAppsCustomizeTabHost.getCurrentTabTag();
            if (currentTabTag != null) {
                outState.putString("apps_customize_currentTab", currentTabTag);
            }
            int currentIndex = mAppsCustomizeContent.getSaveInstanceStateIndex();
            outState.putInt("apps_customize_currentIndex", currentIndex);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove all pending runnables
        mHandler.removeMessages(ADVANCE_MSG);
        mHandler.removeMessages(0);
        mWorkspace.removeCallbacks(mBuildLayersRunnable);

        // Stop callbacks from LauncherModel
        LauncherAppState app = (LauncherAppState.getInstance());
        mModel.stopLoader();
        app.setLauncher(null);

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }
        mAppWidgetHost = null;

        mWidgetsToAdvance.clear();

        TextKeyListener.getInstance().release();

        // Disconnect any of the callbacks and drawables associated with ItemInfos on the workspace
        // to prevent leaking Launcher activities on orientation change.
        if (mModel != null) {
            mModel.unbindItemInfosAndClearQueuedBindRunnables();
        }

        getContentResolver().unregisterContentObserver(mWidgetObserver);
        unregisterReceiver(mCloseSystemDialogsReceiver);

        mDragLayer.clearAllResizeFrames();
        ((ViewGroup) mWorkspace.getParent()).removeAllViews();
        mWorkspace.removeAllViews();
        mWorkspace = null;
        mDragController = null;

        LauncherAnimUtils.onDestroyActivity();
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode >= 0) mWaitingForResult = true;
        super.startActivityForResult(intent, requestCode);
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {

        showWorkspace(true);

        if (initialQuery == null) {
            // Use any text typed in the launcher as the initial query
            initialQuery = getTypedText();
        }
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString("source", "launcher-search");
        }
        Rect sourceBounds = new Rect();
        if (mSearchDropTargetBar != null) {
            sourceBounds = mSearchDropTargetBar.getSearchBarBounds();
        }

        startSearch(initialQuery, selectInitialQuery,
                appSearchData, sourceBounds);
    }

    public void startSearch(String initialQuery,
            boolean selectInitialQuery, Bundle appSearchData, Rect sourceBounds) {
        startGlobalSearch(initialQuery, selectInitialQuery,
                appSearchData, sourceBounds);
    }

    /**
     * Starts the global search activity. This code is a copied from SearchManager
     */
    private void startGlobalSearch(String initialQuery,
            boolean selectInitialQuery, Bundle appSearchData, Rect sourceBounds) {
        final SearchManager searchManager =
            (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName globalSearchActivity = searchManager.getGlobalSearchActivity();
        if (globalSearchActivity == null) {
            Log.w(TAG, "No global search activity found.");
            return;
        }
        Intent intent = new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(globalSearchActivity);
        // Make sure that we have a Bundle to put source in
        if (appSearchData == null) {
            appSearchData = new Bundle();
        } else {
            appSearchData = new Bundle(appSearchData);
        }
        // Set source to package name of app that starts global search, if not set already.
        if (!appSearchData.containsKey("source")) {
            appSearchData.putString("source", getPackageName());
        }
        intent.putExtra(SearchManager.APP_DATA, appSearchData);
        if (!TextUtils.isEmpty(initialQuery)) {
            intent.putExtra(SearchManager.QUERY, initialQuery);
        }
        if (selectInitialQuery) {
            intent.putExtra(SearchManager.EXTRA_SELECT_QUERY, selectInitialQuery);
        }
        intent.setSourceBounds(sourceBounds);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Global search activity not found: " + globalSearchActivity);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (!mWorkspace.isInOverviewMode()) {
            mWorkspace.enterOverviewMode();
        }
        return false;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, true);
        // Use a custom animation for launching search
        return true;
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mWaitingForResult;
    }

    private void resetAddInfo() {
        mPendingAddInfo.container = ItemInfo.NO_ID;
        mPendingAddInfo.screenId = -1;
        mPendingAddInfo.cellX = mPendingAddInfo.cellY = -1;
        mPendingAddInfo.spanX = mPendingAddInfo.spanY = -1;
        mPendingAddInfo.minSpanX = mPendingAddInfo.minSpanY = -1;
        mPendingAddInfo.dropPos = null;
    }

    void addAppWidgetImpl(final int appWidgetId, ItemInfo info, AppWidgetHostView boundWidget,
            AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo.configure != null) {
            mPendingAddWidgetInfo = appWidgetInfo;

            // Launch over to configure widget, if needed
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            Utilities.startActivityForResultSafely(this, intent, REQUEST_CREATE_APPWIDGET);
        } else {
            // Otherwise just add it
            completeAddAppWidget(appWidgetId, info.container, info.screenId, boundWidget,
                    appWidgetInfo);
            // Exit spring loaded mode if necessary after adding the widget
            exitSpringLoadedDragModeDelayed(true, false, null);
        }
    }

    protected void moveToCustomContentScreen(boolean animate) {
        // Close any folders that may be open.
        closeFolder();
        mWorkspace.moveToCustomContentScreen(animate);
    }
    /**
     * Process a shortcut drop.
     *
     * @param componentName The name of the component
     * @param screenId The ID of the screen where it should be added
     * @param cell The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void processShortcutFromDrop(ComponentName componentName, long container, long screenId,
            int[] cell, int[] loc) {
        resetAddInfo();
        mPendingAddInfo.container = container;
        mPendingAddInfo.screenId = screenId;
        mPendingAddInfo.dropPos = loc;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }

        Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        createShortcutIntent.setComponent(componentName);
        processShortcut(createShortcutIntent);
    }

    /**
     * Process a widget drop.
     *
     * @param info The PendingAppWidgetInfo of the widget being added.
     * @param screenId The ID of the screen where it should be added
     * @param cell The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void addAppWidgetFromDrop(PendingAddWidgetInfo info, long container, long screenId,
            int[] cell, int[] span, int[] loc) {
        resetAddInfo();
        mPendingAddInfo.container = info.container = container;
        mPendingAddInfo.screenId = info.screenId = screenId;
        mPendingAddInfo.dropPos = loc;
        mPendingAddInfo.minSpanX = info.minSpanX;
        mPendingAddInfo.minSpanY = info.minSpanY;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }
        if (span != null) {
            mPendingAddInfo.spanX = span[0];
            mPendingAddInfo.spanY = span[1];
        }

        AppWidgetHostView hostView = info.boundWidget;
        int appWidgetId;
        if (hostView != null) {
            appWidgetId = hostView.getAppWidgetId();
            addAppWidgetImpl(appWidgetId, info, hostView, info.info);
        } else {
            // In this case, we either need to start an activity to get permission to bind
            // the widget, or we need to start an activity to configure the widget, or both.
            appWidgetId = getAppWidgetHost().allocateAppWidgetId();
            Bundle options = info.bindOptions;

            boolean success = false;
            if (options != null) {
                success = mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,
                        info.componentName, options);
            } else {
                success = mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,
                        info.componentName);
            }
            if (success) {
                addAppWidgetImpl(appWidgetId, info, null, info.info);
            } else {
                mPendingAddWidgetInfo = info.info;
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.componentName);
                // TODO: we need to make sure that this accounts for the options bundle.
                // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
                startActivityForResult(intent, REQUEST_BIND_APPWIDGET);
            }
        }
    }

    void processShortcut(Intent intent) {
        // Handle case where user selected "Applications"
        String applicationName = getResources().getString(R.string.group_applications);
        String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (applicationName != null && applicationName.equals(shortcutName)) {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
            pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
            pickIntent.putExtra(Intent.EXTRA_TITLE, getText(R.string.title_select_application));
            Utilities.startActivityForResultSafely(this, pickIntent, REQUEST_PICK_APPLICATION);
        } else {
            Utilities.startActivityForResultSafely(this, intent, REQUEST_CREATE_SHORTCUT);
        }
    }

    void processWallpaper(Intent intent) {
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    FolderIcon addFolder(CellLayout layout, long container, final long screenId, int cellX,
            int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = getText(R.string.folder_name);

        // Update the model
        LauncherModel.addItemToDatabase(Launcher.this, folderInfo, container, screenId, cellX, cellY,
                false);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder =
            FolderIcon.fromXml(R.layout.folder_icon, this, layout, folderInfo, mIconCache);
        mWorkspace.addInScreen(newFolder, container, screenId, cellX, cellY, 1, 1,
                isWorkspaceLocked());
        // Force measure the new folder icon
        CellLayout parent = mWorkspace.getParentCellLayoutForView(newFolder);
        parent.getShortcutsAndWidgets().measureChild(newFolder);
        return newFolder;
    }

    void removeFolder(FolderInfo folder) {
        sFolders.remove(folder.id);
    }

    protected void startWallpaper() {
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        pickWallpaper.setComponent(getWallpaperPickerComponent());
        startActivityForResult(pickWallpaper, REQUEST_PICK_WALLPAPER);
    }

    protected ComponentName getWallpaperPickerComponent() {
        return new ComponentName(getPackageName(), WallpaperPickerActivity.class.getName());
    }

    /**
     * Registers various content observers. The current implementation registers
     * only a favorites observer to keep track of the favorites applications.
     */
    private void registerContentObservers() {
        ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(LauncherProvider.CONTENT_APPWIDGET_RESET_URI,
                true, mWidgetObserver);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (isPropertyEnabled(DUMP_STATE_PROPERTY)) {
                        dumpState();
                        return true;
                    }
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (isAllAppsVisible()) {
            if (mAppsCustomizeContent.getContentType() ==
                    AppsCustomizePagedView.ContentType.Applications) {
                showWorkspace(true);
            } else {
                showOverviewMode(true);
            }
        } else if (mWorkspace.isInOverviewMode()) {
            mWorkspace.exitOverviewMode(true);
        } else if (mWorkspace.getOpenFolder() != null) {
            Folder openFolder = mWorkspace.getOpenFolder();
            if (openFolder.isEditingName()) {
                openFolder.dismissEditingName();
            } else {
                closeFolder();
            }
        } else {
            mWorkspace.exitWidgetResizeMode();

            // Back button is a no-op here, but give at least some feedback for the button press
            mWorkspace.showOutlinesTemporarily();
        }
    }

    /**
     * Re-listen when widgets are reset.
     */
    private void onAppWidgetReset() {
        if (mAppWidgetHost != null) {
            mAppWidgetHost.startListening();
        }
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) {
            return;
        }

        if (!mWorkspace.isFinishedSwitchingState()) {
            return;
        }

        if (v instanceof Workspace) {
            if (mWorkspace.isInOverviewMode()) {
                mWorkspace.exitOverviewMode(true);
            }
            return;
        }

        if (v instanceof CellLayout) {
            if (mWorkspace.isInOverviewMode()) {
                mWorkspace.exitOverviewMode(mWorkspace.indexOfChild(v), true);
            }
        }

        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            // Open shortcut
            final ShortcutInfo shortcut = (ShortcutInfo) tag;
            final Intent intent = shortcut.intent;

            // Check for special shortcuts
            if (intent.getComponent() != null) {
                final String shortcutClass = intent.getComponent().getClassName();

                if (shortcutClass.equals(WidgetAdder.class.getName())) {
                    showAllApps(true, AppsCustomizePagedView.ContentType.Widgets, true);
                    return;
                } else if (shortcutClass.equals(MemoryDumpActivity.class.getName())) {
                    MemoryDumpActivity.startDump(this);
                    return;
                } else if (shortcutClass.equals(ToggleWeightWatcher.class.getName())) {
                    toggleShowWeightWatcher();
                    return;
                }
            }

            // Start activities
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));

            boolean success = startActivitySafely(v, intent, tag);

            mStats.recordLaunch(intent, shortcut);

            if (success && v instanceof BubbleTextView) {
                mWaitingForResume = (BubbleTextView) v;
                mWaitingForResume.setStayPressed(true);
            }
        } else if (tag instanceof FolderInfo) {
            if (v instanceof FolderIcon) {
                FolderIcon fi = (FolderIcon) v;
                handleFolderClick(fi);
            }
        } else if (v == mAllAppsButton) {
            if (isAllAppsVisible()) {
                showWorkspace(true);
            } else {
                onClickAllAppsButton(v);
            }
        }
    }

    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    /**
     * Event handler for the search button
     *
     * @param v The view that was clicked.
     */
    public void onClickSearchButton(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        onSearchRequested();
    }

    /**
     * Event handler for the voice button
     *
     * @param v The view that was clicked.
     */
    public void onClickVoiceButton(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        startVoice();
    }

    public void startVoice() {
        try {
            final SearchManager searchManager =
                    (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            ComponentName activityName = searchManager.getGlobalSearchActivity();
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (activityName != null) {
                intent.setPackage(activityName.getPackageName());
            }
            startActivity(null, intent, "onClickVoiceButton");
        } catch (ActivityNotFoundException e) {
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySafely(null, intent, "onClickVoiceButton");
        }
    }

    /**
     * Event handler for the "grid" button that appears on the home screen, which
     * enters all apps mode.
     *
     * @param v The view that was clicked.
     */
    public void onClickAllAppsButton(View v) {
        showAllApps(true, AppsCustomizePagedView.ContentType.Applications, true);
    }

    public void onTouchDownAllAppsButton(View v) {
        // Provide the same haptic feedback that the system offers for virtual keys.
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public void performHapticFeedbackOnTouchDown(View v) {
        // Provide the same haptic feedback that the system offers for virtual keys.
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public View.OnTouchListener getHapticFeedbackTouchListener() {
        if (mHapticFeedbackTouchListener == null) {
            mHapticFeedbackTouchListener = new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    return false;
                }
            };
        }
        return mHapticFeedbackTouchListener;
    }

    public void onClickAppMarketButton(View v) {
        if (!DISABLE_MARKET_BUTTON) {
            if (mAppMarketIntent != null) {
                startActivitySafely(v, mAppMarketIntent, "app market");
            } else {
                Log.e(TAG, "Invalid app market intent.");
            }
        }
    }

    /**
     * Called when the user stops interacting with the launcher.
     * This implies that the user is now on the homescreen and is not doing housekeeping.
     */
    protected void onInteractionEnd() {}

    /**
     * Called when the user starts interacting with the launcher.
     * The possible interactions are:
     *  - open all apps
     *  - reorder an app shortcut, or a widget
     *  - open the overview mode.
     * This is a good time to stop doing things that only make sense
     * when the user is on the homescreen and not doing housekeeping.
     */
    protected void onInteractionBegin() {}

    void startApplicationDetailsActivity(ComponentName componentName) {
        String packageName = componentName.getPackageName();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivitySafely(null, intent, "startApplicationDetailsActivity");
    }

    // returns true if the activity was started
    boolean startApplicationUninstallActivity(ComponentName componentName, int flags) {
        if ((flags & AppInfo.DOWNLOADED_FLAG) == 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            int messageId = R.string.uninstall_system_app_text;
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
            return false;
        } else {
            String packageName = componentName.getPackageName();
            String className = componentName.getClassName();
            Intent intent = new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", packageName, className));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
            return true;
        }
    }

    boolean startActivity(View v, Intent intent, Object tag) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            // Only launch using the new animation if the shortcut has not opted out (this is a
            // private contract between launcher and may be ignored in the future).
            boolean useLaunchAnimation = (v != null) &&
                    !intent.hasExtra(INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION);
            if (useLaunchAnimation) {
                ActivityOptions opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0,
                        v.getMeasuredWidth(), v.getMeasuredHeight());

                startActivity(intent, opts.toBundle());
            } else {
                startActivity(intent);
            }
            return true;
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity. "
                    + "tag="+ tag + " intent=" + intent, e);
        }
        return false;
    }

    boolean startActivitySafely(View v, Intent intent, Object tag) {
        boolean success = false;
        try {
            success = startActivity(v, intent, tag);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + tag + " intent=" + intent, e);
        }
        return success;
    }

    private void handleFolderClick(FolderIcon folderIcon) {
        final FolderInfo info = folderIcon.getFolderInfo();
        Folder openFolder = mWorkspace.getFolderForTag(info);

        // If the folder info reports that the associated folder is open, then verify that
        // it is actually opened. There have been a few instances where this gets out of sync.
        if (info.opened && openFolder == null) {
            Log.d(TAG, "Folder info marked as open, but associated folder is not open. Screen: "
                    + info.screenId + " (" + info.cellX + ", " + info.cellY + ")");
            info.opened = false;
        }

        if (!info.opened && !folderIcon.getFolder().isDestroyed()) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openFolder(folderIcon);
        } else {
            // Find the open folder...
            int folderScreen;
            if (openFolder != null) {
                folderScreen = mWorkspace.getPageForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentPage()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderIcon);
                }
            }
        }
    }

    /**
     * This method draws the FolderIcon to an ImageView and then adds and positions that ImageView
     * in the DragLayer in the exact absolute location of the original FolderIcon.
     */
    private void copyFolderIconToImage(FolderIcon fi) {
        final int width = fi.getMeasuredWidth();
        final int height = fi.getMeasuredHeight();

        // Lazy load ImageView, Bitmap and Canvas
        if (mFolderIconImageView == null) {
            mFolderIconImageView = new ImageView(this);
        }
        if (mFolderIconBitmap == null || mFolderIconBitmap.getWidth() != width ||
                mFolderIconBitmap.getHeight() != height) {
            mFolderIconBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mFolderIconCanvas = new Canvas(mFolderIconBitmap);
        }

        DragLayer.LayoutParams lp;
        if (mFolderIconImageView.getLayoutParams() instanceof DragLayer.LayoutParams) {
            lp = (DragLayer.LayoutParams) mFolderIconImageView.getLayoutParams();
        } else {
            lp = new DragLayer.LayoutParams(width, height);
        }

        // The layout from which the folder is being opened may be scaled, adjust the starting
        // view size by this scale factor.
        float scale = mDragLayer.getDescendantRectRelativeToSelf(fi, mRectForFolderAnimation);
        lp.customPosition = true;
        lp.x = mRectForFolderAnimation.left;
        lp.y = mRectForFolderAnimation.top;
        lp.width = (int) (scale * width);
        lp.height = (int) (scale * height);

        mFolderIconCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        fi.draw(mFolderIconCanvas);
        mFolderIconImageView.setImageBitmap(mFolderIconBitmap);
        if (fi.getFolder() != null) {
            mFolderIconImageView.setPivotX(fi.getFolder().getPivotXForIconAnimation());
            mFolderIconImageView.setPivotY(fi.getFolder().getPivotYForIconAnimation());
        }
        // Just in case this image view is still in the drag layer from a previous animation,
        // we remove it and re-add it.
        if (mDragLayer.indexOfChild(mFolderIconImageView) != -1) {
            mDragLayer.removeView(mFolderIconImageView);
        }
        mDragLayer.addView(mFolderIconImageView, lp);
        if (fi.getFolder() != null) {
            fi.getFolder().bringToFront();
        }
    }

    private void growAndFadeOutFolderIcon(FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.5f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.5f);

        FolderInfo info = (FolderInfo) fi.getTag();
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            CellLayout cl = (CellLayout) fi.getParent().getParent();
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) fi.getLayoutParams();
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY);
        }

        // Push an ImageView copy of the FolderIcon into the DragLayer and hide the original
        copyFolderIconToImage(fi);
        fi.setVisibility(View.INVISIBLE);

        ObjectAnimator oa = LauncherAnimUtils.ofPropertyValuesHolder(mFolderIconImageView, alpha,
                scaleX, scaleY);
        oa.setDuration(getResources().getInteger(R.integer.config_folderAnimDuration));
        oa.start();
    }

    private void shrinkAndFadeInFolderIcon(final FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 1.0f);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f);

        final CellLayout cl = (CellLayout) fi.getParent().getParent();

        // We remove and re-draw the FolderIcon in-case it has changed
        mDragLayer.removeView(mFolderIconImageView);
        copyFolderIconToImage(fi);
        ObjectAnimator oa = LauncherAnimUtils.ofPropertyValuesHolder(mFolderIconImageView, alpha,
                scaleX, scaleY);
        oa.setDuration(getResources().getInteger(R.integer.config_folderAnimDuration));
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cl != null) {
                    cl.clearFolderLeaveBehind();
                    // Remove the ImageView copy of the FolderIcon and make the original visible.
                    mDragLayer.removeView(mFolderIconImageView);
                    fi.setVisibility(View.VISIBLE);
                }
            }
        });
        oa.start();
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderInfo The FolderInfo describing the folder to open.
     */
    public void openFolder(FolderIcon folderIcon) {
        Folder folder = folderIcon.getFolder();
        FolderInfo info = folder.mInfo;

        info.opened = true;

        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (folder.getParent() == null) {
            mDragLayer.addView(folder);
            mDragController.addDropTarget((DropTarget) folder);
        } else {
            Log.w(TAG, "Opening folder (" + folder + ") which already has a parent (" +
                    folder.getParent() + ").");
        }
        folder.animateOpen();
        growAndFadeOutFolderIcon(folderIcon);

        // Notify the accessibility manager that this folder "window" has appeared and occluded
        // the workspace items
        folder.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        getDragLayer().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public void closeFolder() {
        Folder folder = mWorkspace.getOpenFolder();
        if (folder != null) {
            if (folder.isEditingName()) {
                folder.dismissEditingName();
            }
            closeFolder(folder);

            // Dismiss the folder cling
            dismissFolderCling(null);
        }
    }

    void closeFolder(Folder folder) {
        folder.getInfo().opened = false;

        ViewGroup parent = (ViewGroup) folder.getParent().getParent();
        if (parent != null) {
            FolderIcon fi = (FolderIcon) mWorkspace.getViewForTag(folder.mInfo);
            shrinkAndFadeInFolderIcon(fi);
        }
        folder.animateClosed();

        // Notify the accessibility manager that this folder "window" has disappeard and no
        // longer occludeds the workspace items
        getDragLayer().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    public boolean onLongClick(View v) {
        if (!isDraggingEnabled()) return false;
        if (isWorkspaceLocked()) return false;
        if (mState != State.WORKSPACE) return false;

        if (v instanceof Workspace) {
            if (!mWorkspace.isInOverviewMode()) {
                if (mWorkspace.enterOverviewMode()) {
                    mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    return true;
                } else {
                    return false;
                }
            }
        }

        if (!(v instanceof CellLayout)) {
            v = (View) v.getParent().getParent();
        }

        resetAddInfo();
        CellLayout.CellInfo longClickCellInfo = (CellLayout.CellInfo) v.getTag();
        // This happens when long clicking an item with the dpad/trackball
        if (longClickCellInfo == null) {
            return true;
        }

        // The hotseat touch handling does not go through Workspace, and we always allow long press
        // on hotseat items.
        final View itemUnderLongClick = longClickCellInfo.cell;
        boolean allowLongPress = isHotseatLayout(v) || mWorkspace.allowLongPress();
        if (allowLongPress && !mDragController.isDragging()) {
            if (itemUnderLongClick == null) {
                // User long pressed on empty space
                mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                // Disabling reordering until we sort out some issues.
                if (mWorkspace.isInOverviewMode()) {
                    mWorkspace.startReordering(v);
                } else {
                    mWorkspace.enterOverviewMode();
                }
            } else {
                if (!(itemUnderLongClick instanceof Folder)) {
                    // User long pressed on an item
                    mWorkspace.startDrag(longClickCellInfo);
                }
            }
        }
        return true;
    }

    boolean isHotseatLayout(View layout) {
        return mHotseat != null && layout != null &&
                (layout instanceof CellLayout) && (layout == mHotseat.getLayout());
    }
    Hotseat getHotseat() {
        return mHotseat;
    }
    View getOverviewPanel() {
        return mOverviewPanel;
    }
    SearchDropTargetBar getSearchBar() {
        return mSearchDropTargetBar;
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     */
    CellLayout getCellLayout(long container, long screenId) {
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            if (mHotseat != null) {
                return mHotseat.getLayout();
            } else {
                return null;
            }
        } else {
            return (CellLayout) mWorkspace.getScreenWithId(screenId);
        }
    }

    Workspace getWorkspace() {
        return mWorkspace;
    }

    public boolean isAllAppsVisible() {
        return (mState == State.APPS_CUSTOMIZE) || (mOnResumeState == State.APPS_CUSTOMIZE);
    }

    /**
     * Helper method for the cameraZoomIn/cameraZoomOut animations
     * @param view The view being animated
     * @param scaleFactor The scale factor used for the zoom
     */
    private void setPivotsForZoom(View view, float scaleFactor) {
        view.setPivotX(view.getWidth() / 2.0f);
        view.setPivotY(view.getHeight() / 2.0f);
    }

    private void setWorkspaceBackground(boolean workspace) {
        mLauncherView.setBackground(workspace ?
                mWorkspaceBackgroundDrawable : null);
    }

    void updateWallpaperVisibility(boolean visible) {
        int wpflags = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int curflags = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (wpflags != curflags) {
            getWindow().setFlags(wpflags, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
        setWorkspaceBackground(visible);
    }

    private void dispatchOnLauncherTransitionPrepare(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionPrepare(this, animated, toWorkspace);
        }
    }

    private void dispatchOnLauncherTransitionStart(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStart(this, animated, toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 0f);
    }

    private void dispatchOnLauncherTransitionStep(View v, float t) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStep(this, t);
        }
    }

    private void dispatchOnLauncherTransitionEnd(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionEnd(this, animated, toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 1f);
    }

    /**
     * Things to test when changing the following seven functions.
     *   - Home from workspace
     *          - from center screen
     *          - from other screens
     *   - Home from all apps
     *          - from center screen
     *          - from other screens
     *   - Back from all apps
     *          - from center screen
     *          - from other screens
     *   - Launch app from workspace and quit
     *          - with back
     *          - with home
     *   - Launch app from all apps and quit
     *          - with back
     *          - with home
     *   - Go to a screen that's not the default, then all
     *     apps, and launch and app, and go back
     *          - with back
     *          -with home
     *   - On workspace, long press power and go back
     *          - with back
     *          - with home
     *   - On all apps, long press power and go back
     *          - with back
     *          - with home
     *   - On workspace, power off
     *   - On all apps, power off
     *   - Launch an app and turn off the screen while in that app
     *          - Go back with home key
     *          - Go back with back key  TODO: make this not go to workspace
     *          - From all apps
     *          - From workspace
     *   - Enter and exit car mode (becuase it causes an extra configuration changed)
     *          - From all apps
     *          - From the center workspace
     *          - From another workspace
     */

    /**
     * Zoom the camera out from the workspace to reveal 'toView'.
     * Assumes that the view to show is anchored at either the very top or very bottom
     * of the screen.
     */
    private void showAppsCustomizeHelper(final boolean animated, final boolean springLoaded) {
        AppsCustomizePagedView.ContentType contentType = mAppsCustomizeContent.getContentType();
        showAppsCustomizeHelper(animated, springLoaded, contentType);
    }
    private void showAppsCustomizeHelper(final boolean animated, final boolean springLoaded,
                                         final AppsCustomizePagedView.ContentType contentType) {
        if (mStateAnimation != null) {
            mStateAnimation.setDuration(0);
            mStateAnimation.cancel();
            mStateAnimation = null;
        }
        final Resources res = getResources();

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomInTime);
        final int fadeDuration = res.getInteger(R.integer.config_appsCustomizeFadeInTime);
        final float scale = (float) res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mWorkspace;
        final AppsCustomizeTabHost toView = mAppsCustomizeTabHost;
        final int startDelay =
                res.getInteger(R.integer.config_workspaceAppsCustomizeAnimationStagger);

        setPivotsForZoom(toView, scale);

        // Shrink workspaces away if going to AppsCustomize from workspace
        Animator workspaceAnim =
                mWorkspace.getChangeStateAnimation(Workspace.State.SMALL, animated);
        if (!AppsCustomizePagedView.DISABLE_ALL_APPS) {
            // Set the content type for the all apps space
            mAppsCustomizeTabHost.setContentTypeImmediate(contentType);
        }

        if (animated) {
            toView.setScaleX(scale);
            toView.setScaleY(scale);
            final LauncherViewPropertyAnimator scaleAnim = new LauncherViewPropertyAnimator(toView);
            scaleAnim.
                scaleX(1f).scaleY(1f).
                setDuration(duration).
                setInterpolator(new Workspace.ZoomOutInterpolator());

            toView.setVisibility(View.VISIBLE);
            toView.setAlpha(0f);
            final ObjectAnimator alphaAnim = LauncherAnimUtils
                .ofFloat(toView, "alpha", 0f, 1f)
                .setDuration(fadeDuration);
            alphaAnim.setInterpolator(new DecelerateInterpolator(1.5f));
            alphaAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (animation == null) {
                        throw new RuntimeException("animation is null");
                    }
                    float t = (Float) animation.getAnimatedValue();
                    dispatchOnLauncherTransitionStep(fromView, t);
                    dispatchOnLauncherTransitionStep(toView, t);
                }
            });

            // toView should appear right at the end of the workspace shrink
            // animation
            mStateAnimation = LauncherAnimUtils.createAnimatorSet();
            mStateAnimation.play(scaleAnim).after(startDelay);
            mStateAnimation.play(alphaAnim).after(startDelay);

            mStateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    // Prepare the position
                    toView.setTranslationX(0.0f);
                    toView.setTranslationY(0.0f);
                    toView.setVisibility(View.VISIBLE);
                    toView.bringToFront();
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchOnLauncherTransitionEnd(fromView, animated, false);
                    dispatchOnLauncherTransitionEnd(toView, animated, false);

                    // Hide the search bar
                    if (mSearchDropTargetBar != null) {
                        mSearchDropTargetBar.hideSearchBar(false);
                    }
                }
            });

            if (workspaceAnim != null) {
                mStateAnimation.play(workspaceAnim);
            }

            boolean delayAnim = false;

            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);

            // If any of the objects being animated haven't been measured/laid out
            // yet, delay the animation until we get a layout pass
            if ((((LauncherTransitionable) toView).getContent().getMeasuredWidth() == 0) ||
                    (mWorkspace.getMeasuredWidth() == 0) ||
                    (toView.getMeasuredWidth() == 0)) {
                delayAnim = true;
            }

            final AnimatorSet stateAnimation = mStateAnimation;
            final Runnable startAnimRunnable = new Runnable() {
                public void run() {
                    // Check that mStateAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mStateAnimation != stateAnimation)
                        return;
                    setPivotsForZoom(toView, scale);
                    dispatchOnLauncherTransitionStart(fromView, animated, false);
                    dispatchOnLauncherTransitionStart(toView, animated, false);
                    LauncherAnimUtils.startAnimationAfterNextDraw(mStateAnimation, toView);
                }
            };
            if (delayAnim) {
                final ViewTreeObserver observer = toView.getViewTreeObserver();
                observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                        public void onGlobalLayout() {
                            startAnimRunnable.run();
                            toView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
            } else {
                startAnimRunnable.run();
            }
        } else {
            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setVisibility(View.VISIBLE);
            toView.bringToFront();

            if (!springLoaded && !LauncherAppState.getInstance().isScreenLarge()) {
                // Hide the search bar
                if (mSearchDropTargetBar != null) {
                    mSearchDropTargetBar.hideSearchBar(false);
                }
            }
            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionStart(fromView, animated, false);
            dispatchOnLauncherTransitionEnd(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);
            dispatchOnLauncherTransitionStart(toView, animated, false);
            dispatchOnLauncherTransitionEnd(toView, animated, false);
        }
    }

    /**
     * Zoom the camera back into the workspace, hiding 'fromView'.
     * This is the opposite of showAppsCustomizeHelper.
     * @param animated If true, the transition will be animated.
     */
    private void hideAppsCustomizeHelper(Workspace.State toState, final boolean animated,
            final boolean springLoaded, final Runnable onCompleteRunnable) {

        if (mStateAnimation != null) {
            mStateAnimation.setDuration(0);
            mStateAnimation.cancel();
            mStateAnimation = null;
        }
        Resources res = getResources();

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomOutTime);
        final int fadeOutDuration =
                res.getInteger(R.integer.config_appsCustomizeFadeOutTime);
        final float scaleFactor = (float)
                res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mAppsCustomizeTabHost;
        final View toView = mWorkspace;
        Animator workspaceAnim = null;
        if (toState == Workspace.State.NORMAL) {
            int stagger = res.getInteger(R.integer.config_appsCustomizeWorkspaceAnimationStagger);
            workspaceAnim = mWorkspace.getChangeStateAnimation(
                    toState, animated, stagger, -1);
        } else if (toState == Workspace.State.SPRING_LOADED ||
                toState == Workspace.State.OVERVIEW) {
            workspaceAnim = mWorkspace.getChangeStateAnimation(
                    toState, animated);
        }

        setPivotsForZoom(fromView, scaleFactor);
        showHotseat(animated);
        if (animated) {
            final LauncherViewPropertyAnimator scaleAnim =
                    new LauncherViewPropertyAnimator(fromView);
            scaleAnim.
                scaleX(scaleFactor).scaleY(scaleFactor).
                setDuration(duration).
                setInterpolator(new Workspace.ZoomInInterpolator());

            final ObjectAnimator alphaAnim = LauncherAnimUtils
                .ofFloat(fromView, "alpha", 1f, 0f)
                .setDuration(fadeOutDuration);
            alphaAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            alphaAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float t = 1f - (Float) animation.getAnimatedValue();
                    dispatchOnLauncherTransitionStep(fromView, t);
                    dispatchOnLauncherTransitionStep(toView, t);
                }
            });

            mStateAnimation = LauncherAnimUtils.createAnimatorSet();

            dispatchOnLauncherTransitionPrepare(fromView, animated, true);
            dispatchOnLauncherTransitionPrepare(toView, animated, true);
            mAppsCustomizeContent.pauseScrolling();

            mStateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fromView.setVisibility(View.GONE);
                    dispatchOnLauncherTransitionEnd(fromView, animated, true);
                    dispatchOnLauncherTransitionEnd(toView, animated, true);
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }
                    mAppsCustomizeContent.updateCurrentPageScroll();
                    mAppsCustomizeContent.resumeScrolling();
                }
            });

            mStateAnimation.playTogether(scaleAnim, alphaAnim);
            if (workspaceAnim != null) {
                mStateAnimation.play(workspaceAnim);
            }
            dispatchOnLauncherTransitionStart(fromView, animated, true);
            dispatchOnLauncherTransitionStart(toView, animated, true);
            LauncherAnimUtils.startAnimationAfterNextDraw(mStateAnimation, toView);
        } else {
            fromView.setVisibility(View.GONE);
            dispatchOnLauncherTransitionPrepare(fromView, animated, true);
            dispatchOnLauncherTransitionStart(fromView, animated, true);
            dispatchOnLauncherTransitionEnd(fromView, animated, true);
            dispatchOnLauncherTransitionPrepare(toView, animated, true);
            dispatchOnLauncherTransitionStart(toView, animated, true);
            dispatchOnLauncherTransitionEnd(toView, animated, true);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            mAppsCustomizeTabHost.onTrimMemory();
        }
    }

    protected void showWorkspace(boolean animated) {
        showWorkspace(animated, null);
    }

    protected void showWorkspace() {
        showWorkspace(true);
    }

    void showWorkspace(boolean animated, Runnable onCompleteRunnable) {
        if (mWorkspace.isInOverviewMode()) {
            mWorkspace.exitOverviewMode(animated);
        }
        if (mState != State.WORKSPACE) {
            boolean wasInSpringLoadedMode = (mState != State.WORKSPACE);
            mWorkspace.setVisibility(View.VISIBLE);
            hideAppsCustomizeHelper(Workspace.State.NORMAL, animated, false, onCompleteRunnable);

            // Show the search bar (only animate if we were showing the drop target bar in spring
            // loaded mode)
            if (mSearchDropTargetBar != null) {
                mSearchDropTargetBar.showSearchBar(animated && wasInSpringLoadedMode);
            }

            // Set focus to the AppsCustomize button
            if (mAllAppsButton != null) {
                mAllAppsButton.requestFocus();
            }
        }

        // Change the state *after* we've called all the transition code
        mState = State.WORKSPACE;

        // Resume the auto-advance of widgets
        mUserPresent = true;
        updateRunning();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);

        onWorkspaceShown(animated);
    }

    void showOverviewMode(boolean animated) {
        mWorkspace.setVisibility(View.VISIBLE);
        hideAppsCustomizeHelper(Workspace.State.OVERVIEW, animated, false, null);
        mState = State.WORKSPACE;
        onWorkspaceShown(animated);
    }

    public void onWorkspaceShown(boolean animated) {
    }

    void showAllApps(boolean animated, AppsCustomizePagedView.ContentType contentType,
                     boolean resetPageToZero) {
        if (mState != State.WORKSPACE) return;

        if (resetPageToZero) {
            mAppsCustomizeTabHost.reset();
        }
        showAppsCustomizeHelper(animated, false, contentType);
        mAppsCustomizeTabHost.requestFocus();

        // Change the state *after* we've called all the transition code
        mState = State.APPS_CUSTOMIZE;

        // Pause the auto-advance of widgets until we are out of AllApps
        mUserPresent = false;
        updateRunning();
        closeFolder();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void enterSpringLoadedDragMode() {
        if (isAllAppsVisible()) {
            hideAppsCustomizeHelper(Workspace.State.SPRING_LOADED, true, true, null);
            mState = State.APPS_CUSTOMIZE_SPRING_LOADED;
        }
    }

    void exitSpringLoadedDragModeDelayed(final boolean successfulDrop, boolean extendedDelay,
            final Runnable onCompleteRunnable) {
        if (mState != State.APPS_CUSTOMIZE_SPRING_LOADED) return;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (successfulDrop) {
                    // Before we show workspace, hide all apps again because
                    // exitSpringLoadedDragMode made it visible. This is a bit hacky; we should
                    // clean up our state transition functions
                    mAppsCustomizeTabHost.setVisibility(View.GONE);
                    showWorkspace(true, onCompleteRunnable);
                } else {
                    exitSpringLoadedDragMode();
                }
            }
        }, (extendedDelay ?
                EXIT_SPRINGLOADED_MODE_LONG_TIMEOUT :
                EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT));
    }

    void exitSpringLoadedDragMode() {
        if (mState == State.APPS_CUSTOMIZE_SPRING_LOADED) {
            final boolean animated = true;
            final boolean springLoaded = true;
            showAppsCustomizeHelper(animated, springLoaded);
            mState = State.APPS_CUSTOMIZE;
        }
        // Otherwise, we are not in spring loaded mode, so don't do anything.
    }

    void lockAllApps() {
        // TODO
    }

    void unlockAllApps() {
        // TODO
    }

    /**
     * Shows the hotseat area.
     */
    void showHotseat(boolean animated) {
        if (!LauncherAppState.getInstance().isScreenLarge()) {
            if (animated) {
                if (mHotseat.getAlpha() != 1f) {
                    int duration = 0;
                    if (mSearchDropTargetBar != null) {
                        duration = mSearchDropTargetBar.getTransitionInDuration();
                    }
                    mHotseat.animate().alpha(1f).setDuration(duration);
                }
            } else {
                mHotseat.setAlpha(1f);
            }
        }
    }

    /**
     * Hides the hotseat area.
     */
    void hideHotseat(boolean animated) {
        if (!LauncherAppState.getInstance().isScreenLarge()) {
            if (animated) {
                if (mHotseat.getAlpha() != 0f) {
                    int duration = 0;
                    if (mSearchDropTargetBar != null) {
                        duration = mSearchDropTargetBar.getTransitionOutDuration();
                    }
                    mHotseat.animate().alpha(0f).setDuration(duration);
                }
            } else {
                mHotseat.setAlpha(0f);
            }
        }
    }

    /**
     * Add an item from all apps or customize onto the given workspace screen.
     * If layout is null, add to the current screen.
     */
    void addExternalItemToScreen(ItemInfo itemInfo, final CellLayout layout) {
        if (!mWorkspace.addExternalItemToScreen(itemInfo, layout)) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
        }
    }

    /** Maps the current orientation to an index for referencing orientation correct global icons */
    private int getCurrentOrientationIndexForGlobalIcons() {
        // default - 0, landscape - 1
        switch (getResources().getConfiguration().orientation) {
        case Configuration.ORIENTATION_LANDSCAPE:
            return 1;
        default:
            return 0;
        }
    }

    private Drawable getExternalPackageToolbarIcon(ComponentName activityName, String resourceName) {
        try {
            PackageManager packageManager = getPackageManager();
            // Look for the toolbar icon specified in the activity meta-data
            Bundle metaData = packageManager.getActivityInfo(
                    activityName, PackageManager.GET_META_DATA).metaData;
            if (metaData != null) {
                int iconResId = metaData.getInt(resourceName);
                if (iconResId != 0) {
                    Resources res = packageManager.getResourcesForActivity(activityName);
                    return res.getDrawable(iconResId);
                }
            }
        } catch (NameNotFoundException e) {
            // This can happen if the activity defines an invalid drawable
            Log.w(TAG, "Failed to load toolbar icon; " + activityName.flattenToShortString() +
                    " not found", e);
        } catch (Resources.NotFoundException nfe) {
            // This can happen if the activity defines an invalid drawable
            Log.w(TAG, "Failed to load toolbar icon from " + activityName.flattenToShortString(),
                    nfe);
        }
        return null;
    }

    // if successful in getting icon, return it; otherwise, set button to use default drawable
    private Drawable.ConstantState updateTextButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId,
            String toolbarResourceName) {
        Drawable toolbarIcon = getExternalPackageToolbarIcon(activityName, toolbarResourceName);
        Resources r = getResources();
        int w = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_width);
        int h = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_height);

        TextView button = (TextView) findViewById(buttonId);
        // If we were unable to find the icon via the meta-data, use a generic one
        if (toolbarIcon == null) {
            toolbarIcon = r.getDrawable(fallbackDrawableId);
            toolbarIcon.setBounds(0, 0, w, h);
            if (button != null) {
                button.setCompoundDrawables(toolbarIcon, null, null, null);
            }
            return null;
        } else {
            toolbarIcon.setBounds(0, 0, w, h);
            if (button != null) {
                button.setCompoundDrawables(toolbarIcon, null, null, null);
            }
            return toolbarIcon.getConstantState();
        }
    }

    // if successful in getting icon, return it; otherwise, set button to use default drawable
    private Drawable.ConstantState updateButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId,
            String toolbarResourceName) {
        ImageView button = (ImageView) findViewById(buttonId);
        Drawable toolbarIcon = getExternalPackageToolbarIcon(activityName, toolbarResourceName);

        if (button != null) {
            // If we were unable to find the icon via the meta-data, use a
            // generic one
            if (toolbarIcon == null) {
                button.setImageResource(fallbackDrawableId);
            } else {
                button.setImageDrawable(toolbarIcon);
            }
        }

        return toolbarIcon != null ? toolbarIcon.getConstantState() : null;

    }

    private void updateTextButtonWithDrawable(int buttonId, Drawable d) {
        TextView button = (TextView) findViewById(buttonId);
        button.setCompoundDrawables(d, null, null, null);
    }

    private void updateButtonWithDrawable(int buttonId, Drawable.ConstantState d) {
        ImageView button = (ImageView) findViewById(buttonId);
        button.setImageDrawable(d.newDrawable(getResources()));
    }

    private void invalidatePressedFocusedStates(View container, View button) {
        if (container instanceof HolographicLinearLayout) {
            HolographicLinearLayout layout = (HolographicLinearLayout) container;
            layout.invalidatePressedFocusedStates();
        } else if (button instanceof HolographicImageView) {
            HolographicImageView view = (HolographicImageView) button;
            view.invalidatePressedFocusedStates();
        }
    }

    public View getQsbBar() {
        if (mQsbBar == null) {
            mQsbBar = mInflater.inflate(R.layout.search_bar, mSearchDropTargetBar, false);
            mSearchDropTargetBar.addView(mQsbBar);
        }
        return mQsbBar;
    }

    protected boolean updateGlobalSearchIcon() {
        final View searchButtonContainer = findViewById(R.id.search_button_container);
        final ImageView searchButton = (ImageView) findViewById(R.id.search_button);
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);

        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName activityName = searchManager.getGlobalSearchActivity();
        if (activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sGlobalSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                    R.id.search_button, activityName, R.drawable.ic_home_search_normal_holo,
                    TOOLBAR_SEARCH_ICON_METADATA_NAME);
            if (sGlobalSearchIcon[coi] == null) {
                sGlobalSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                        R.id.search_button, activityName, R.drawable.ic_home_search_normal_holo,
                        TOOLBAR_ICON_METADATA_NAME);
            }

            if (searchButtonContainer != null) searchButtonContainer.setVisibility(View.VISIBLE);
            searchButton.setVisibility(View.VISIBLE);
            invalidatePressedFocusedStates(searchButtonContainer, searchButton);
            return true;
        } else {
            // We disable both search and voice search when there is no global search provider
            if (searchButtonContainer != null) searchButtonContainer.setVisibility(View.GONE);
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.GONE);
            if (searchButton != null) searchButton.setVisibility(View.GONE);
            if (voiceButton != null) voiceButton.setVisibility(View.GONE);
            updateVoiceButtonProxyVisible(false);
            return false;
        }
    }

    protected void updateGlobalSearchIcon(Drawable.ConstantState d) {
        final View searchButtonContainer = findViewById(R.id.search_button_container);
        final View searchButton = (ImageView) findViewById(R.id.search_button);
        updateButtonWithDrawable(R.id.search_button, d);
        invalidatePressedFocusedStates(searchButtonContainer, searchButton);
    }

    protected boolean updateVoiceSearchIcon(boolean searchVisible) {
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);

        // We only show/update the voice search icon if the search icon is enabled as well
        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName globalSearchActivity = searchManager.getGlobalSearchActivity();

        ComponentName activityName = null;
        if (globalSearchActivity != null) {
            // Check if the global search activity handles voice search
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setPackage(globalSearchActivity.getPackageName());
            activityName = intent.resolveActivity(getPackageManager());
        }

        if (activityName == null) {
            // Fallback: check if an activity other than the global search activity
            // resolves this
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            activityName = intent.resolveActivity(getPackageManager());
        }
        if (searchVisible && activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sVoiceSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                    R.id.voice_button, activityName, R.drawable.ic_home_voice_search_holo,
                    TOOLBAR_VOICE_SEARCH_ICON_METADATA_NAME);
            if (sVoiceSearchIcon[coi] == null) {
                sVoiceSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                        R.id.voice_button, activityName, R.drawable.ic_home_voice_search_holo,
                        TOOLBAR_ICON_METADATA_NAME);
            }
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.VISIBLE);
            voiceButton.setVisibility(View.VISIBLE);
            updateVoiceButtonProxyVisible(false);
            invalidatePressedFocusedStates(voiceButtonContainer, voiceButton);
            return true;
        } else {
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.GONE);
            if (voiceButton != null) voiceButton.setVisibility(View.GONE);
            updateVoiceButtonProxyVisible(false);
            return false;
        }
    }

    protected void updateVoiceSearchIcon(Drawable.ConstantState d) {
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);
        updateButtonWithDrawable(R.id.voice_button, d);
        invalidatePressedFocusedStates(voiceButtonContainer, voiceButton);
    }

    public void updateVoiceButtonProxyVisible(boolean forceDisableVoiceButtonProxy) {
        final View voiceButtonProxy = findViewById(R.id.voice_button_proxy);
        if (voiceButtonProxy != null) {
            boolean visible = !forceDisableVoiceButtonProxy &&
                    mWorkspace.shouldVoiceButtonProxyBeVisible();
            voiceButtonProxy.setVisibility(visible ? View.VISIBLE : View.GONE);
            voiceButtonProxy.bringToFront();
        }
    }

    /**
     * This is an overrid eot disable the voice button proxy.  If disabled is true, then the voice button proxy
     * will be hidden regardless of what shouldVoiceButtonProxyBeVisible() returns.
     */
    public void disableVoiceButtonProxy(boolean disabled) {
        updateVoiceButtonProxyVisible(disabled);
    }
    /**
     * Sets the app market icon
     */
    private void updateAppMarketIcon() {
        if (!DISABLE_MARKET_BUTTON) {
            final View marketButton = findViewById(R.id.market_button);
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET);
            // Find the app market activity by resolving an intent.
            // (If multiple app markets are installed, it will return the ResolverActivity.)
            ComponentName activityName = intent.resolveActivity(getPackageManager());
            if (activityName != null) {
                int coi = getCurrentOrientationIndexForGlobalIcons();
                mAppMarketIntent = intent;
                sAppMarketIcon[coi] = updateTextButtonWithIconFromExternalActivity(
                        R.id.market_button, activityName, R.drawable.ic_launcher_market_holo,
                        TOOLBAR_ICON_METADATA_NAME);
                marketButton.setVisibility(View.VISIBLE);
            } else {
                // We should hide and disable the view so that we don't try and restore the visibility
                // of it when we swap between drag & normal states from IconDropTarget subclasses.
                marketButton.setVisibility(View.GONE);
                marketButton.setEnabled(false);
            }
        }
    }

    private void updateAppMarketIcon(Drawable.ConstantState d) {
        if (!DISABLE_MARKET_BUTTON) {
            // Ensure that the new drawable we are creating has the approprate toolbar icon bounds
            Resources r = getResources();
            Drawable marketIconDrawable = d.newDrawable(r);
            int w = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_width);
            int h = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_height);
            marketIconDrawable.setBounds(0, 0, w, h);

            updateTextButtonWithDrawable(R.id.market_button, marketIconDrawable);
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        // Populate event with a fake title based on the current state.
        if (mState == State.APPS_CUSTOMIZE) {
            text.add(mAppsCustomizeTabHost.getCurrentTabView().getContentDescription());
        } else {
            text.add(getString(R.string.all_apps_home_button_label));
        }
        return result;
    }

    /**
     * Receives notifications when system dialogs are to be closed.
     */
    private class CloseSystemDialogsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            closeSystemDialogs();
        }
    }

    /**
     * Receives notifications whenever the appwidgets are reset.
     */
    private class AppWidgetResetObserver extends ContentObserver {
        public AppWidgetResetObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            onAppWidgetReset();
        }
    }

    /**
     * If the activity is currently paused, signal that we need to run the passed Runnable
     * in onResume.
     *
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    private boolean waitUntilResume(Runnable run, boolean deletePreviousRunnables) {
        if (mPaused) {
            Log.i(TAG, "Deferring update until onResume");
            if (deletePreviousRunnables) {
                while (mBindOnResumeCallbacks.remove(run)) {
                }
            }
            mBindOnResumeCallbacks.add(run);
            return true;
        } else {
            return false;
        }
    }

    private boolean waitUntilResume(Runnable run) {
        return waitUntilResume(run, false);
    }

    public void addOnResumeCallback(Runnable run) {
        mOnResumeCallbacks.add(run);
    }

    /**
     * If the activity is currently paused, signal that we need to re-run the loader
     * in onResume.
     *
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    public boolean setLoadOnResume() {
        if (mPaused) {
            Log.i(TAG, "setLoadOnResume");
            mOnResumeNeedsLoad = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public int getCurrentWorkspaceScreen() {
        if (mWorkspace != null) {
            return mWorkspace.getCurrentPage();
        } else {
            return SCREEN_COUNT / 2;
        }
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        // If we're starting binding all over again, clear any bind calls we'd postponed in
        // the past (see waitUntilResume) -- we don't need them since we're starting binding
        // from scratch again
        mBindOnResumeCallbacks.clear();

        // Clear the workspace because it's going to be rebound
        mWorkspace.clearDropTargets();
        mWorkspace.removeAllWorkspaceScreens();

        mWidgetsToAdvance.clear();
        if (mHotseat != null) {
            mHotseat.resetLayout();
        }
    }

    @Override
    public void bindScreens(ArrayList<Long> orderedScreenIds) {
        bindAddScreens(orderedScreenIds);

        // If there are no screens, we need to have an empty screen
        if (orderedScreenIds.size() == 0) {
            mWorkspace.addExtraEmptyScreen();
        }

        // Create the custom content page (this call updates mDefaultScreen which calls
        // setCurrentPage() so ensure that all pages are added before calling this).
        // The actual content of the custom page will be added during onFinishBindingItems().
        if (!mWorkspace.hasCustomContent() && hasCustomContentToLeft()) {
            mWorkspace.createCustomContentPage();
        }
    }

    @Override
    public void bindAddScreens(ArrayList<Long> orderedScreenIds) {
        int count = orderedScreenIds.size();
        for (int i = 0; i < count; i++) {
            mWorkspace.insertNewWorkspaceScreenBeforeEmptyScreen(orderedScreenIds.get(i));
        }
    }

    private boolean shouldShowWeightWatcher() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_PRIVATE);
        boolean show = sp.getBoolean(SHOW_WEIGHT_WATCHER, SHOW_WEIGHT_WATCHER_DEFAULT);

        return show;
    }

    private void toggleShowWeightWatcher() {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = getSharedPreferences(spKey, Context.MODE_PRIVATE);
        boolean show = sp.getBoolean(SHOW_WEIGHT_WATCHER, true);

        show = !show;

        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(SHOW_WEIGHT_WATCHER, show);
        editor.commit();

        if (mWeightWatcher != null) {
            mWeightWatcher.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public void bindAppsAdded(final ArrayList<Long> newScreens,
                              final ArrayList<ItemInfo> addNotAnimated,
                              final ArrayList<ItemInfo> addAnimated,
                              final ArrayList<AppInfo> addedApps) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppsAdded(newScreens, addNotAnimated, addAnimated, addedApps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Add the new screens
        bindAddScreens(newScreens);

        // We add the items without animation on non-visible pages, and with
        // animations on the new page (which we will try and snap to).
        if (!addNotAnimated.isEmpty()) {
            bindItems(addNotAnimated, 0,
                    addNotAnimated.size(), false);
        }
        if (!addAnimated.isEmpty()) {
            bindItems(addAnimated, 0,
                    addAnimated.size(), true);
        }

        // Remove the extra empty screen
        mWorkspace.removeExtraEmptyScreen();

        if (!AppsCustomizePagedView.DISABLE_ALL_APPS &&
                addedApps != null && mAppsCustomizeContent != null) {
            mAppsCustomizeContent.addApps(addedApps);
        }
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindItems(final ArrayList<ItemInfo> shortcuts, final int start, final int end,
                          final boolean forceAnimateIcons) {
        Runnable r = new Runnable() {
            public void run() {
                bindItems(shortcuts, start, end, forceAnimateIcons);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Get the list of added shortcuts and intersect them with the set of shortcuts here
        final AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();
        final Collection<Animator> bounceAnims = new ArrayList<Animator>();
        final boolean animateIcons = forceAnimateIcons && canRunNewAppsAnimation();
        Workspace workspace = mWorkspace;
        long newShortcutsScreenId = -1;
        for (int i = start; i < end; i++) {
            final ItemInfo item = shortcuts.get(i);

            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                    mHotseat == null) {
                continue;
            }

            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    ShortcutInfo info = (ShortcutInfo) item;
                    View shortcut = createShortcut(info);

                    /*
                     * TODO: FIX collision case
                     */
                    if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                        CellLayout cl = mWorkspace.getScreenWithId(item.screenId);
                        if (cl != null && cl.isOccupied(item.cellX, item.cellY)) {
                            throw new RuntimeException("OCCUPIED");
                        }
                    }

                    workspace.addInScreenFromBind(shortcut, item.container, item.screenId, item.cellX,
                            item.cellY, 1, 1);
                    if (animateIcons) {
                        // Animate all the applications up now
                        shortcut.setAlpha(0f);
                        shortcut.setScaleX(0f);
                        shortcut.setScaleY(0f);
                        bounceAnims.add(createNewAppBounceAnimation(shortcut, i));
                        newShortcutsScreenId = item.screenId;
                    }
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                    FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item, mIconCache);
                    workspace.addInScreenFromBind(newFolder, item.container, item.screenId, item.cellX,
                            item.cellY, 1, 1);
                    break;
                default:
                    throw new RuntimeException("Invalid Item Type");
            }
        }

        if (animateIcons) {
            // Animate to the correct page
            if (newShortcutsScreenId > -1) {
                long currentScreenId = mWorkspace.getScreenIdForPageIndex(mWorkspace.getNextPage());
                final int newScreenIndex = mWorkspace.getPageIndexForScreenId(newShortcutsScreenId);
                final Runnable startBounceAnimRunnable = new Runnable() {
                    public void run() {
                        anim.playTogether(bounceAnims);
                        anim.start();
                    }
                };
                if (newShortcutsScreenId != currentScreenId) {
                    // We post the animation slightly delayed to prevent slowdowns
                    // when we are loading right after we return to launcher.
                    mWorkspace.postDelayed(new Runnable() {
                        public void run() {
                            mWorkspace.snapToPage(newScreenIndex);
                            mWorkspace.postDelayed(startBounceAnimRunnable,
                                    NEW_APPS_ANIMATION_DELAY);
                        }
                    }, NEW_APPS_PAGE_MOVE_DELAY);
                } else {
                    mWorkspace.postDelayed(startBounceAnimRunnable, NEW_APPS_ANIMATION_DELAY);
                }
            }
        }
        workspace.requestLayout();
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindFolders(final HashMap<Long, FolderInfo> folders) {
        Runnable r = new Runnable() {
            public void run() {
                bindFolders(folders);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        sFolders.clear();
        sFolders.putAll(folders);
    }

    /**
     * Add the views for a widget to the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppWidget(final LauncherAppWidgetInfo item) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppWidget(item);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        final long start = DEBUG_WIDGETS ? SystemClock.uptimeMillis() : 0;
        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bindAppWidget: " + item);
        }
        final Workspace workspace = mWorkspace;

        final int appWidgetId = item.appWidgetId;
        final AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bindAppWidget: id=" + item.appWidgetId + " belongs to component " + appWidgetInfo.provider);
        }

        item.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);

        item.hostView.setTag(item);
        item.onBindAppWidget(this);

        workspace.addInScreen(item.hostView, item.container, item.screenId, item.cellX,
                item.cellY, item.spanX, item.spanY, false);
        addWidgetToAutoAdvanceIfNeeded(item.hostView, appWidgetInfo);

        workspace.requestLayout();

        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bound widget id="+item.appWidgetId+" in "
                    + (SystemClock.uptimeMillis()-start) + "ms");
        }
    }

    public void onPageBoundSynchronously(int page) {
        mSynchronouslyBoundPages.add(page);
    }

    /**
     * Callback saying that there aren't any more items to bind.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems(final boolean upgradePath) {
        Runnable r = new Runnable() {
            public void run() {
                finishBindingItems(upgradePath);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentPage()).requestFocus();
            }
            mSavedState = null;
        }

        mWorkspace.restoreInstanceStateForRemainingPages();

        // If we received the result of any pending adds while the loader was running (e.g. the
        // widget configuration forced an orientation change), process them now.
        for (int i = 0; i < sPendingAddList.size(); i++) {
            completeAdd(sPendingAddList.get(i));
        }
        sPendingAddList.clear();

        // Update the market app icon as necessary (the other icons will be managed in response to
        // package changes in bindSearchablesChanged()
        if (!DISABLE_MARKET_BUTTON) {
            updateAppMarketIcon();
        }

        mWorkspaceLoading = false;
        if (upgradePath) {
            mWorkspace.getUniqueComponents(true, null);
            mIntentsOnWorkspaceFromUpgradePath = mWorkspace.getUniqueComponents(true, null);
        }

        mWorkspace.post(new Runnable() {
            @Override
            public void run() {
                onFinishBindingItems();
            }
        });
    }

    public boolean isAllAppsButtonRank(int rank) {
        if (mHotseat != null) {
            return mHotseat.isAllAppsButtonRank(rank);
        }
        return false;
    }

    private boolean canRunNewAppsAnimation() {
        long diff = System.currentTimeMillis() - mDragController.getLastGestureUpTime();
        return diff > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
    }

    private ValueAnimator createNewAppBounceAnimation(View v, int i) {
        ValueAnimator bounceAnim = LauncherAnimUtils.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat("alpha", 1f),
                PropertyValuesHolder.ofFloat("scaleX", 1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f));
        bounceAnim.setDuration(InstallShortcutReceiver.NEW_SHORTCUT_BOUNCE_DURATION);
        bounceAnim.setStartDelay(i * InstallShortcutReceiver.NEW_SHORTCUT_STAGGER_DELAY);
        bounceAnim.setInterpolator(new SmoothPagedView.OvershootInterpolator());
        return bounceAnim;
    }

    @Override
    public void bindSearchablesChanged() {
        boolean searchVisible = updateGlobalSearchIcon();
        boolean voiceVisible = updateVoiceSearchIcon(searchVisible);
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.onSearchPackagesChanged(searchVisible, voiceVisible);
        }
    }

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAllApplications(final ArrayList<AppInfo> apps) {
        if (AppsCustomizePagedView.DISABLE_ALL_APPS) {
            if (mIntentsOnWorkspaceFromUpgradePath != null) {
                if (LauncherModel.UPGRADE_USE_MORE_APPS_FOLDER) {
                    getHotseat().addAllAppsFolder(mIconCache, apps,
                            mIntentsOnWorkspaceFromUpgradePath, Launcher.this, mWorkspace);
                }
                mIntentsOnWorkspaceFromUpgradePath = null;
            }
        } else {
            if (mAppsCustomizeContent != null) {
                mAppsCustomizeContent.setApps(apps);
            }
        }
    }

    /**
     * A package was updated.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsUpdated(final ArrayList<AppInfo> apps) {
        Runnable r = new Runnable() {
            public void run() {
                bindAppsUpdated(apps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (mWorkspace != null) {
            mWorkspace.updateShortcuts(apps);
        }

        if (!AppsCustomizePagedView.DISABLE_ALL_APPS &&
                mAppsCustomizeContent != null) {
            mAppsCustomizeContent.updateApps(apps);
        }
    }

    /**
     * A package was uninstalled.  We take both the super set of packageNames
     * in addition to specific applications to remove, the reason being that
     * this can be called when a package is updated as well.  In that scenario,
     * we only remove specific components from the workspace, where as
     * package-removal should clear all items by package name.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindComponentsRemoved(final ArrayList<String> packageNames,
                                      final ArrayList<AppInfo> appInfos,
                                      final boolean packageRemoved) {
        Runnable r = new Runnable() {
            public void run() {
                bindComponentsRemoved(packageNames, appInfos, packageRemoved);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (packageRemoved) {
            mWorkspace.removeItemsByPackageName(packageNames);
        } else {
            mWorkspace.removeItemsByApplicationInfo(appInfos);
        }

        // Notify the drag controller
        mDragController.onAppsRemoved(appInfos, this);

        if (!AppsCustomizePagedView.DISABLE_ALL_APPS &&
                mAppsCustomizeContent != null) {
            mAppsCustomizeContent.removeApps(appInfos);
        }
    }

    /**
     * A number of packages were updated.
     */
    private ArrayList<Object> mWidgetsAndShortcuts;
    private Runnable mBindPackagesUpdatedRunnable = new Runnable() {
            public void run() {
                bindPackagesUpdated(mWidgetsAndShortcuts);
                mWidgetsAndShortcuts = null;
            }
        };

    public void bindPackagesUpdated(final ArrayList<Object> widgetsAndShortcuts) {
        if (waitUntilResume(mBindPackagesUpdatedRunnable, true)) {
            mWidgetsAndShortcuts = widgetsAndShortcuts;
            return;
        }

        // Update the widgets pane
        if (!AppsCustomizePagedView.DISABLE_ALL_APPS &&
                mAppsCustomizeContent != null) {
            mAppsCustomizeContent.onPackagesUpdated(widgetsAndShortcuts);
        }
    }

    private int mapConfigurationOriActivityInfoOri(int configOri) {
        final Display d = getWindowManager().getDefaultDisplay();
        int naturalOri = Configuration.ORIENTATION_LANDSCAPE;
        switch (d.getRotation()) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
            // We are currently in the same basic orientation as the natural orientation
            naturalOri = configOri;
            break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
            // We are currently in the other basic orientation to the natural orientation
            naturalOri = (configOri == Configuration.ORIENTATION_LANDSCAPE) ?
                    Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
            break;
        }

        int[] oriMap = {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        };
        // Since the map starts at portrait, we need to offset if this device's natural orientation
        // is landscape.
        int indexOffset = 0;
        if (naturalOri == Configuration.ORIENTATION_LANDSCAPE) {
            indexOffset = 1;
        }
        return oriMap[(d.getRotation() + indexOffset) % 4];
    }

    public boolean isRotationEnabled() {
        boolean enableRotation = sForceEnableRotation ||
                getResources().getBoolean(R.bool.allow_rotation);
        return enableRotation;
    }
    public void lockScreenOrientation() {
        if (isRotationEnabled()) {
            setRequestedOrientation(mapConfigurationOriActivityInfoOri(getResources()
                    .getConfiguration().orientation));
        }
    }
    public void unlockScreenOrientation(boolean immediate) {
        if (isRotationEnabled()) {
            if (immediate) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } else {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                }, mRestoreScreenOrientationDelay);
            }
        }
    }

    /* Cling related */
    private boolean isClingsEnabled() {
        if (DISABLE_CLINGS) {
            return false;
        }

        // For now, limit only to phones
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        if (grid.isTablet()) {
            return false;
        }

        // disable clings when running in a test harness
        if(ActivityManager.isRunningInTestHarness()) return false;

        // Disable clings for accessibility when explore by touch is enabled
        final AccessibilityManager a11yManager = (AccessibilityManager) getSystemService(
                ACCESSIBILITY_SERVICE);
        if (a11yManager.isTouchExplorationEnabled()) {
            return false;
        }

        // Restricted secondary users (child mode) will potentially have very few apps
        // seeded when they start up for the first time. Clings won't work well with that
//        boolean supportsLimitedUsers =
//                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
//        Account[] accounts = AccountManager.get(this).getAccounts();
//        if (supportsLimitedUsers && accounts.length == 0) {
//            UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
//            Bundle restrictions = um.getUserRestrictions();
//            if (restrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false)) {
//               return false;
//            }
//        }
        return true;
    }

    private Cling initCling(int clingId, int scrimId, boolean animate,
                            boolean dimNavBarVisibilty) {
        Cling cling = (Cling) findViewById(clingId);
        View scrim = null;
        if (scrimId > 0) {
            scrim = findViewById(R.id.cling_scrim);
        }
        if (cling != null) {
            cling.init(this, scrim);
            cling.show(animate, SHOW_CLING_DURATION);

            if (dimNavBarVisibilty) {
                cling.setSystemUiVisibility(cling.getSystemUiVisibility() |
                        View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }
        return cling;
    }

    private void dismissCling(final Cling cling, final Runnable postAnimationCb,
                              final String flag, int duration, boolean restoreNavBarVisibilty) {
        // To catch cases where siblings of top-level views are made invisible, just check whether
        // the cling is directly set to GONE before dismissing it.
        if (cling != null && cling.getVisibility() != View.GONE) {
            final Runnable cleanUpClingCb = new Runnable() {
                public void run() {
                    cling.cleanup();
                    // We should update the shared preferences on a background thread
                    new Thread("dismissClingThread") {
                        public void run() {
                            SharedPreferences.Editor editor = mSharedPrefs.edit();
                            editor.putBoolean(flag, true);
                            editor.commit();
                        }
                    }.start();
                    if (postAnimationCb != null) {
                        postAnimationCb.run();
                    }
                }
            };
            if (duration <= 0) {
                cleanUpClingCb.run();
            } else {
                cling.hide(duration, cleanUpClingCb);
            }
            mHideFromAccessibilityHelper.restoreImportantForAccessibility(mDragLayer);

            if (restoreNavBarVisibilty) {
                cling.setSystemUiVisibility(cling.getSystemUiVisibility() &
                        ~View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }
    }

    private void removeCling(int id) {
        final View cling = findViewById(id);
        if (cling != null) {
            final ViewGroup parent = (ViewGroup) cling.getParent();
            parent.post(new Runnable() {
                @Override
                public void run() {
                    parent.removeView(cling);
                }
            });
            mHideFromAccessibilityHelper.restoreImportantForAccessibility(mDragLayer);
        }
    }

    private boolean skipCustomClingIfNoAccounts() {
        Cling cling = (Cling) findViewById(R.id.workspace_cling);
        boolean customCling = cling.getDrawIdentifier().equals("workspace_custom");
        if (customCling) {
            AccountManager am = AccountManager.get(this);
            if (am == null) return false;
            Account[] accounts = am.getAccountsByType("com.google");
            return accounts.length == 0;
        }
        return false;
    }

    public void updateCustomContentHintVisibility() {
        Cling cling = (Cling) findViewById(R.id.first_run_cling);
        String ccHintStr = getFirstRunCustomContentHint();

        if (mWorkspace.hasCustomContent()) {
            // Show the custom content hint if ccHintStr is not empty
            if (cling != null) {
                setCustomContentHintVisibility(cling, ccHintStr, true, true);
            }
        } else {
            // Hide the custom content hint
            if (cling != null) {
                setCustomContentHintVisibility(cling, ccHintStr, false, true);
            }
        }
    }

    private void setCustomContentHintVisibility(Cling cling, String ccHintStr, boolean visible,
                                                boolean animate) {
        final TextView ccHint = (TextView) cling.findViewById(R.id.custom_content_hint);
        if (ccHint != null) {
            if (visible && !ccHintStr.isEmpty()) {
                ccHint.setText(ccHintStr);
                ccHint.setVisibility(View.VISIBLE);
                if (animate) {
                    ccHint.setAlpha(0f);
                    ccHint.animate().alpha(1f)
                                    .setDuration(SHOW_CLING_DURATION)
                                    .start();
                } else {
                    ccHint.setAlpha(1f);
                }
            } else {
                if (animate) {
                    ccHint.animate().alpha(0f)
                                    .setDuration(SHOW_CLING_DURATION)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            ccHint.setVisibility(View.GONE);
                                        }
                                    })
                                    .start();
                } else {
                    ccHint.setAlpha(0f);
                    ccHint.setVisibility(View.GONE);
                }
            }
        }
    }

    public void showFirstRunCling() {
        if (isClingsEnabled() &&
                !mSharedPrefs.getBoolean(Cling.FIRST_RUN_CLING_DISMISSED_KEY, false) &&
                !skipCustomClingIfNoAccounts() ) {
            // If we're not using the default workspace layout, replace workspace cling
            // with a custom workspace cling (usually specified in an overlay)
            // For now, only do this on tablets
            if (!DISABLE_CUSTOM_CLINGS) {
                if (mSharedPrefs.getInt(LauncherProvider.DEFAULT_WORKSPACE_RESOURCE_ID, 0) != 0 &&
                        getResources().getBoolean(R.bool.config_useCustomClings)) {
                    // Use a custom cling
                    View cling = findViewById(R.id.workspace_cling);
                    ViewGroup clingParent = (ViewGroup) cling.getParent();
                    int clingIndex = clingParent.indexOfChild(cling);
                    clingParent.removeViewAt(clingIndex);
                    View customCling = mInflater.inflate(R.layout.custom_workspace_cling, clingParent, false);
                    clingParent.addView(customCling, clingIndex);
                    customCling.setId(R.id.workspace_cling);
                }
            }
            Cling cling = (Cling) findViewById(R.id.first_run_cling);
            if (cling != null) {
                String sbHintStr = getFirstRunClingSearchBarHint();
                String ccHintStr = getFirstRunCustomContentHint();
                if (!sbHintStr.isEmpty()) {
                    TextView sbHint = (TextView) cling.findViewById(R.id.search_bar_hint);
                    sbHint.setText(sbHintStr);
                    sbHint.setVisibility(View.VISIBLE);
                }
                setCustomContentHintVisibility(cling, ccHintStr, true, false);
            }
            initCling(R.id.first_run_cling, 0, false, true);
        } else {
            removeCling(R.id.first_run_cling);
        }
    }

    protected String getFirstRunClingSearchBarHint() {
        return "";
    }
    protected String getFirstRunCustomContentHint() {
        return "";
    }
    protected int getFirstRunFocusedHotseatAppDrawableId() {
        return -1;
    }
    protected ComponentName getFirstRunFocusedHotseatAppComponentName() {
        return null;
    }
    protected int getFirstRunFocusedHotseatAppRank() {
        return -1;
    }
    protected String getFirstRunFocusedHotseatAppBubbleTitle() {
        return "";
    }
    protected String getFirstRunFocusedHotseatAppBubbleDescription() {
        return "";
    }

    public void showFirstRunWorkspaceCling() {
        // Enable the clings only if they have not been dismissed before
        if (isClingsEnabled() &&
                !mSharedPrefs.getBoolean(Cling.WORKSPACE_CLING_DISMISSED_KEY, false)) {
            Cling c = initCling(R.id.workspace_cling, 0, false, true);

            // Set the focused hotseat app if there is one
            c.setFocusedHotseatApp(getFirstRunFocusedHotseatAppDrawableId(),
                    getFirstRunFocusedHotseatAppRank(),
                    getFirstRunFocusedHotseatAppComponentName(),
                    getFirstRunFocusedHotseatAppBubbleTitle(),
                    getFirstRunFocusedHotseatAppBubbleDescription());
        } else {
            removeCling(R.id.workspace_cling);
        }
    }
    public Cling showFirstRunFoldersCling() {
        // Enable the clings only if they have not been dismissed before
        if (isClingsEnabled() &&
                !mSharedPrefs.getBoolean(Cling.FOLDER_CLING_DISMISSED_KEY, false)) {
            Cling cling = initCling(R.id.folder_cling, R.id.cling_scrim,
                    true, true);
            return cling;
        } else {
            removeCling(R.id.folder_cling);
            return null;
        }
    }
    protected SharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }
    public boolean isFolderClingVisible() {
        Cling cling = (Cling) findViewById(R.id.folder_cling);
        if (cling != null) {
            return cling.getVisibility() == View.VISIBLE;
        }
        return false;
    }
    public void dismissFirstRunCling(View v) {
        Cling cling = (Cling) findViewById(R.id.first_run_cling);
        Runnable cb = new Runnable() {
            public void run() {
                // Show the workspace cling next
                showFirstRunWorkspaceCling();
            }
        };
        dismissCling(cling, cb, Cling.FIRST_RUN_CLING_DISMISSED_KEY,
                DISMISS_CLING_DURATION, false);

        // Fade out the search bar for the workspace cling coming up
        mSearchDropTargetBar.hideSearchBar(true);
    }
    public void dismissWorkspaceCling(View v) {
        Cling cling = (Cling) findViewById(R.id.workspace_cling);
        Runnable cb = null;
        if (v == null) {
            cb = new Runnable() {
                public void run() {
                    mWorkspace.enterOverviewMode();
                }
            };
        }
        dismissCling(cling, cb, Cling.WORKSPACE_CLING_DISMISSED_KEY,
                DISMISS_CLING_DURATION, true);

        // Fade in the search bar
        mSearchDropTargetBar.showSearchBar(true);
    }
    public void dismissFolderCling(View v) {
        Cling cling = (Cling) findViewById(R.id.folder_cling);
        dismissCling(cling, null, Cling.FOLDER_CLING_DISMISSED_KEY,
                DISMISS_CLING_DURATION, true);
    }

    /**
     * Prints out out state for debugging.
     */
    public void dumpState() {
        Log.d(TAG, "BEGIN launcher3 dump state for launcher " + this);
        Log.d(TAG, "mSavedState=" + mSavedState);
        Log.d(TAG, "mWorkspaceLoading=" + mWorkspaceLoading);
        Log.d(TAG, "mRestoring=" + mRestoring);
        Log.d(TAG, "mWaitingForResult=" + mWaitingForResult);
        Log.d(TAG, "mSavedInstanceState=" + mSavedInstanceState);
        Log.d(TAG, "sFolders.size=" + sFolders.size());
        mModel.dumpState();

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.dumpState();
        }
        Log.d(TAG, "END launcher3 dump state");
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        synchronized (sDumpLogs) {
            writer.println(" ");
            writer.println("Debug logs: ");
            for (int i = 0; i < sDumpLogs.size(); i++) {
                writer.println("  " + sDumpLogs.get(i));
            }
        }
    }

    public static void dumpDebugLogsToConsole() {
        if (DEBUG_DUMP_LOG) {
            synchronized (sDumpLogs) {
                Log.d(TAG, "");
                Log.d(TAG, "*********************");
                Log.d(TAG, "Launcher debug logs: ");
                for (int i = 0; i < sDumpLogs.size(); i++) {
                    Log.d(TAG, "  " + sDumpLogs.get(i));
                }
                Log.d(TAG, "*********************");
                Log.d(TAG, "");
            }
        }
    }

    public static void addDumpLog(String tag, String log, boolean debugLog) {
        if (debugLog) {
            Log.d(tag, log);
        }
        if (DEBUG_DUMP_LOG) {
            sDateStamp.setTime(System.currentTimeMillis());
            synchronized (sDumpLogs) {
                sDumpLogs.add(sDateFormat.format(sDateStamp) + ": " + tag + ", " + log);
            }
        }
    }

    public void dumpLogsToLocalData() {
        if (DEBUG_DUMP_LOG) {
            new Thread("DumpLogsToLocalData") {
                @Override
                public void run() {
                    boolean success = false;
                    sDateStamp.setTime(sRunStart);
                    String FILENAME = sDateStamp.getMonth() + "-"
                            + sDateStamp.getDay() + "_"
                            + sDateStamp.getHours() + "-"
                            + sDateStamp.getMinutes() + "_"
                            + sDateStamp.getSeconds() + ".txt";

                    FileOutputStream fos = null;
                    File outFile = null;
                    try {
                        outFile = new File(getFilesDir(), FILENAME);
                        outFile.createNewFile();
                        fos = new FileOutputStream(outFile);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (fos != null) {
                        PrintWriter writer = new PrintWriter(fos);

                        writer.println(" ");
                        writer.println("Debug logs: ");
                        synchronized (sDumpLogs) {
                            for (int i = 0; i < sDumpLogs.size(); i++) {
                                writer.println("  " + sDumpLogs.get(i));
                            }
                        }
                        writer.close();
                    }
                    try {
                        if (fos != null) {
                            fos.close();
                            success = true;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }
}

interface LauncherTransitionable {
    View getContent();
    void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace);
    void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace);
    void onLauncherTransitionStep(Launcher l, float t);
    void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace);
}
