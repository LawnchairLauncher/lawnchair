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

package ch.deletescape.lawnchair;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Advanceable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ch.deletescape.lawnchair.DropTarget.DragObject;
import ch.deletescape.lawnchair.LauncherSettings.Favorites;
import ch.deletescape.lawnchair.accessibility.LauncherAccessibilityDelegate;
import ch.deletescape.lawnchair.allapps.AllAppsContainerView;
import ch.deletescape.lawnchair.allapps.AllAppsIconRowView;
import ch.deletescape.lawnchair.allapps.AllAppsTransitionController;
import ch.deletescape.lawnchair.allapps.UnicodeStrippedAppSearchController;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.compat.AppWidgetManagerCompat;
import ch.deletescape.lawnchair.compat.LauncherAppsCompat;
import ch.deletescape.lawnchair.compat.UserManagerCompat;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.dragndrop.DragController;
import ch.deletescape.lawnchair.dragndrop.DragLayer;
import ch.deletescape.lawnchair.dragndrop.DragOptions;
import ch.deletescape.lawnchair.dragndrop.DragView;
import ch.deletescape.lawnchair.dynamicui.ExtractedColors;
import ch.deletescape.lawnchair.folder.Folder;
import ch.deletescape.lawnchair.folder.FolderIcon;
import ch.deletescape.lawnchair.iconpack.EditIconActivity;
import ch.deletescape.lawnchair.keyboard.CustomActionsPopup;
import ch.deletescape.lawnchair.keyboard.ViewGroupFocusHelper;
import ch.deletescape.lawnchair.model.WidgetsModel;
import ch.deletescape.lawnchair.notification.NotificationListener;
import ch.deletescape.lawnchair.overlay.ILauncherClient;
import ch.deletescape.lawnchair.popup.PopupContainerWithArrow;
import ch.deletescape.lawnchair.popup.PopupDataProvider;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;
import ch.deletescape.lawnchair.preferences.PreferenceProvider;
import ch.deletescape.lawnchair.settings.Settings;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutManager;
import ch.deletescape.lawnchair.shortcuts.ShortcutKey;
import ch.deletescape.lawnchair.shortcuts.ShortcutsItemView;
import ch.deletescape.lawnchair.util.ActivityResultInfo;
import ch.deletescape.lawnchair.util.ComponentKey;
import ch.deletescape.lawnchair.util.ItemInfoMatcher;
import ch.deletescape.lawnchair.util.MultiHashMap;
import ch.deletescape.lawnchair.util.PackageManagerHelper;
import ch.deletescape.lawnchair.util.PackageUserKey;
import ch.deletescape.lawnchair.util.PendingRequestArgs;
import ch.deletescape.lawnchair.util.Thunk;
import ch.deletescape.lawnchair.util.ViewOnDrawExecutor;
import ch.deletescape.lawnchair.widget.PendingAddWidgetInfo;
import ch.deletescape.lawnchair.widget.WidgetHostViewLoader;
import ch.deletescape.lawnchair.widget.WidgetsContainerView;
import ch.deletescape.wallpaperpicker.WallpaperPickerActivity;

/**
 * Default launcher application.
 */
public class Launcher extends Activity
        implements OnClickListener, OnLongClickListener,
        LauncherModel.Callbacks, View.OnTouchListener, LauncherProviderChangeListener,
        AccessibilityManager.AccessibilityStateChangeListener, DialogInterface.OnDismissListener {
    public static final String TAG = "Launcher";

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_PICK_WALLPAPER = 10;

    private static final int REQUEST_BIND_APPWIDGET = 11;
    private static final int REQUEST_BIND_PENDING_APPWIDGET = 14;
    private static final int REQUEST_RECONFIGURE_APPWIDGET = 12;

    private static final int REQUEST_PERMISSION_CALL_PHONE = 13;
    public static final int REQUEST_PERMISSION_STORAGE_ACCESS = 666;

    private static final int REQUEST_EDIT_ICON = 14;

    private static final float BOUNCE_ANIMATION_TENSION = 1.3f;

    private static final int SOFT_INPUT_MODE_DEFAULT =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
    private static final int SOFT_INPUT_MODE_ALL_APPS =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

    // The Intent extra that defines whether to ignore the launch animation
    static final String INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
            "ch.deletescape.lawnchair.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";

    public static final String ACTION_APPWIDGET_HOST_RESET =
            "ch.deletescape.lawnchair.intent.ACTION_APPWIDGET_HOST_RESET";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: PendingRequestArgs
    private static final String RUNTIME_STATE_PENDING_REQUEST_ARGS = "launcher.request_args";
    // Type: ActivityResultInfo
    private static final String RUNTIME_STATE_PENDING_ACTIVITY_RESULT = "launcher.activity_result";

    /**
     * The different states that Launcher can be in.
     */
    enum State {
        NONE, WORKSPACE, WORKSPACE_SPRING_LOADED, APPS, APPS_SPRING_LOADED,
        WIDGETS, WIDGETS_SPRING_LOADED
    }

    @Thunk
    State mState = State.WORKSPACE;
    @Thunk
    LauncherStateTransitionAnimation mStateTransitionAnimation;

    private boolean mIsSafeModeEnabled;

    static final int APPWIDGET_HOST_ID = 1024;
    public static final int EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT = 500;
    private static final int ON_ACTIVITY_RESULT_ANIMATION_DELAY = 500;

    // How long to wait before the new-shortcut animation automatically pans the workspace
    private static int NEW_APPS_PAGE_MOVE_DELAY = 500;
    private static int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 5;
    @Thunk
    static int NEW_APPS_ANIMATION_DELAY = 500;

    private final BroadcastReceiver mUiBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_APPWIDGET_HOST_RESET.equals(intent.getAction())) {
                if (mAppWidgetHost != null) {
                    mAppWidgetHost.startListening();
                }
            }
        }
    };

    @Thunk
    Workspace mWorkspace;
    @Thunk
    DragLayer mDragLayer;
    private DragController mDragController;
    private View mQsbContainer;

    private AppWidgetManagerCompat mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    private int[] mTmpAddItemCellCoordinates = new int[2];

    @Thunk
    Hotseat mHotseat;
    private ViewGroup mOverviewPanel;

    private View mAllAppsHandle;
    private View mWidgetsButton;

    private DropTargetBar mDropTargetBar;

    private MultiHashMap mAllWidgets;
    private Runnable mBindAllWidgetsRunnable = new BindAllWidgetsRunnable();

    // Main container view for the all apps screen.
    @Thunk
    AllAppsContainerView mAppsView;
    AllAppsTransitionController mAllAppsController;

    // Main container view and the model for the widget tray screen.
    @Thunk
    WidgetsContainerView mWidgetsView;
    @Thunk
    WidgetsModel mWidgetsModel;

    private Bundle mSavedState;
    // We set the state in both onCreate and then onNewIntent in some cases, which causes both
    // scroll issues (because the workspace may not have been measured yet) and extra work.
    // Instead, just save the state that we need to restore Launcher to, and commit it in onResume.
    private State mOnResumeState = State.NONE;

    private SpannableStringBuilder mDefaultKeySsb = null;

    @Thunk
    boolean mWorkspaceLoading = true;

    private boolean mPaused = true;
    private boolean mOnResumeNeedsLoad;
    private boolean mSnowfallEnabled;
    private boolean mPlanesEnabled;
    private ObjectAnimator mPlanesAnimator;

    private ArrayList<Runnable> mBindOnResumeCallbacks = new ArrayList<>();
    private ArrayList<Runnable> mOnResumeCallbacks = new ArrayList<>();
    private ViewOnDrawExecutor mPendingExecutor;

    private LauncherModel mModel;
    private IconCache mIconCache;
    private ExtractedColors mExtractedColors;
    private LauncherAccessibilityDelegate mAccessibilityDelegate;
    private boolean mIsResumeFromActionScreenOff;
    @Thunk
    boolean mUserPresent = true;
    private boolean mVisible;
    private boolean mHasFocus;
    private boolean mAttached;

    private boolean kill;
    private boolean reloadIcons;
    private boolean updateWallpaper = true;

    /**
     * Maps launcher activity components to their list of shortcut ids.
     */
    private MultiHashMap<ComponentKey, String> mDeepShortcutMap = new MultiHashMap<>();

    private View.OnTouchListener mHapticFeedbackTouchListener;

    // Related to the auto-advancing of widgets
    private final int ADVANCE_MSG = 1;
    private static final int ADVANCE_INTERVAL = 20000;
    private static final int ADVANCE_STAGGER = 250;

    private boolean mAutoAdvanceRunning = false;
    private long mAutoAdvanceSentTime;
    private long mAutoAdvanceTimeLeft = -1;
    @Thunk
    HashMap<View, AppWidgetProviderInfo> mWidgetsToAdvance = new HashMap<>();

    private final ArrayList<Integer> mSynchronouslyBoundPages = new ArrayList<>();

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private IPreferenceProvider mSharedPrefs;

    // Holds the page that we need to animate to, and the icon views that we need to animate up
    // when we scroll to that page on resume.
    @Thunk
    ImageView mFolderIconImageView;
    private Bitmap mFolderIconBitmap;
    private Canvas mFolderIconCanvas;
    private Rect mRectForFolderAnimation = new Rect();

    private DeviceProfile mDeviceProfile;

    // This is set to the view that launched the activity that navigated the user away from
    // launcher. Since there is no callback for when the activity has finished launching, enable
    // the press state and keep this reference to reset the press state when we return to launcher.
    private BubbleTextView mWaitingForResume;


    // Exiting spring loaded mode happens with a delay. This runnable object triggers the
    // state transition. If another state transition happened during this delay,
    // simply unregister this runnable.
    private Runnable mExitSpringLoadedModeRunnable;

    private LauncherTab mLauncherTab;

    private PopupDataProvider mPopupDataProvider;

    private boolean mDisableEditing;

    @Thunk
    Runnable mBuildLayersRunnable = new Runnable() {
        @Override
        public void run() {
            if (mWorkspace != null) {
                mWorkspace.buildPageHardwareLayers();
            }
        }
    };

    // Activity result which needs to be processed after workspace has loaded.
    private ActivityResultInfo mPendingActivityResult;
    /**
     * Holds extra information required to handle a result from an external call, like
     * {@link #startActivityForResult(Intent, int)} or {@link #requestPermissions(String[], int)}
     */
    private PendingRequestArgs mPendingRequestArgs;

    public ViewGroupFocusHelper mFocusHandler;
    private BlurWallpaperProvider mBlurWallpaperProvider;
    private LauncherDialog mCurrentDialog;
    private EditableItemInfo mEditingItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FeatureFlags.INSTANCE.loadThemePreference(this);
        Utilities.setupPirateLocale(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !Utilities.hasStoragePermission(this)) {
            Utilities.requestStoragePermission(this);
        }

        super.onCreate(savedInstanceState);

        setScreenOrientation();

        LauncherAppState app = LauncherAppState.getInstance();
        app.setMLauncher(this);

        // Load configuration-specific DeviceProfile
        mDeviceProfile = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE ?
                app.getInvariantDeviceProfile().landscapeProfile
                : app.getInvariantDeviceProfile().portraitProfile;

        mSharedPrefs = PreferenceProvider.INSTANCE.getPreferences(this);
        mIsSafeModeEnabled = getPackageManager().isSafeMode();
        mModel = app.setLauncher(this);
        mIconCache = app.getIconCache();
        mAccessibilityDelegate = new LauncherAccessibilityDelegate(this);

        mDragController = new DragController(this);
        mBlurWallpaperProvider = new BlurWallpaperProvider(this);
        mAllAppsController = new AllAppsTransitionController(this);
        mStateTransitionAnimation = new LauncherStateTransitionAnimation(this, mAllAppsController);

        mAppWidgetManager = AppWidgetManagerCompat.getInstance(this);

        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        // If we are getting an onCreate, we can actually preempt onResume and unset mPaused here,
        // this also ensures that any synchronous binding below doesn't re-trigger another
        // LauncherModel load.
        mPaused = false;

        setContentView(R.layout.launcher);

        mSnowfallEnabled = Utilities.getPrefs(this).getEnableSnowfall();
        mPlanesEnabled = Utilities.getPrefs(this).getEnablePlanes();
        setupViews();
        mDeviceProfile.layout(this, false /* notifyListeners */);
        mExtractedColors = new ExtractedColors();
        loadExtractedColorsAndColorItems();
        mPopupDataProvider = new PopupDataProvider(this);
        ((AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE))
                .addAccessibilityStateChangeListener(this);

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        // We only load the page synchronously if the user rotates (or triggers a
        // configuration change) while launcher is in the foreground
        if (!mModel.startLoader(mWorkspace.getRestorePage())) {
            // If we are not binding synchronously, show a fade in animation when
            // the first page bind completes.
            mDragLayer.setAlpha(0);
        } else {
            setWorkspaceLoading(true);
        }

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);

        IntentFilter filter = new IntentFilter(ACTION_APPWIDGET_HOST_RESET);
        registerReceiver(mUiBroadcastReceiver, filter);

        mLauncherTab = new LauncherTab(this);
        Utilities.showOutdatedLawnfeedPopup(this);

        Window window = getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.systemUiVisibility |= (View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(0);
        window.setNavigationBarColor(0);

        Settings.init(this);

        Utilities.showChangelog(this);
    }


    private void setScreenOrientation() {
        if (Utilities.getPrefs(this).getEnableScreenRotation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    public void scheduleKill() {
        if (mPaused)
            kill = true;
        else
            Utilities.restartLauncher(getApplicationContext());
    }

    public void scheduleUpdateWallpaper() {
        updateWallpaper = true;
    }

    public void scheduleReloadIcons() {
        reloadIcons = true;
    }

    @Override
    public void onExtractedColorsChanged() {
        loadExtractedColorsAndColorItems();
    }

    private void loadExtractedColorsAndColorItems() {
        mExtractedColors.load(this);
        mHotseat.updateColor(mExtractedColors, !mPaused);
        mWorkspace.getPageIndicator().updateColor(mExtractedColors);
        // It's possible that All Apps is visible when this is run,
        // so always use light status bar in that case.
        activateLightStatusBar(isAllAppsVisible());
    }

    public ExtractedColors getExtractedColors() {
        return mExtractedColors;
    }

    public AllAppsTransitionController getAllAppsController() {
        return mAllAppsController;
    }

    public void activateLightSystemBars(boolean activate, boolean statusBar, boolean navigationBar) {
        if (statusBar)
            activateLightStatusBar(activate);
        if (navigationBar)
            activateLightNavigationBar(activate);
    }

    /**
     * Sets the status bar to be light or not. Light status bar means dark icons.
     *
     * @param activate if true, make sure the status bar is light, otherwise base on wallpaper.
     */
    public void activateLightStatusBar(boolean activate) {
        int oldSystemUiFlags = getWindow().getDecorView().getSystemUiVisibility();
        int newSystemUiFlags = oldSystemUiFlags;
        if (activate) {
            newSystemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            newSystemUiFlags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (newSystemUiFlags != oldSystemUiFlags) {
            final int systemUiFlags = newSystemUiFlags;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getWindow().getDecorView().setSystemUiVisibility(systemUiFlags);
                }
            });
        }
    }

    public void activateLightNavigationBar(boolean activate) {
        int oldSystemUiFlags = getWindow().getDecorView().getSystemUiVisibility();
        int newSystemUiFlags = oldSystemUiFlags;
        if (activate) {
            newSystemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            newSystemUiFlags &= ~(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        if (newSystemUiFlags != oldSystemUiFlags) {
            final int systemUiFlags = newSystemUiFlags;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getWindow().getDecorView().setSystemUiVisibility(systemUiFlags);
                }
            });
        }
    }

    public void onInsetsChanged(Rect insets) {
        mDeviceProfile.updateInsets(insets);
        mDeviceProfile.layout(this, true /* notifyListeners */);
    }

    @Override
    public void onLauncherProviderChange() {
    }

    public boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !isWorkspaceLoading();
    }

    public int getViewIdForItem(ItemInfo info) {
        // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
        // This cast is safe as long as the id < 0x00FFFFFF
        // Since we jail all the dynamically generated views, there should be no clashes
        // with any other views.
        return (int) info.id;
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private long completeAdd(
            int requestCode, Intent intent, int appWidgetId, PendingRequestArgs info) {
        long screenId = info.screenId;
        if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            // When the screen id represents an actual screen (as opposed to a rank) we make sure
            // that the drop page actually exists.
            screenId = ensurePendingDropLayoutExists(info.screenId);
        }

        switch (requestCode) {
            case REQUEST_CREATE_SHORTCUT:
                completeAddShortcut(intent, info.container, screenId, info.cellX, info.cellY, info);
                break;
            case REQUEST_CREATE_APPWIDGET:
                completeAddAppWidget(appWidgetId, info, null, null);
                break;
            case REQUEST_RECONFIGURE_APPWIDGET:
                completeRestoreAppWidget(appWidgetId, LauncherAppWidgetInfo.RESTORE_COMPLETED);
                break;
            case REQUEST_BIND_PENDING_APPWIDGET: {
                LauncherAppWidgetInfo widgetInfo =
                        completeRestoreAppWidget(appWidgetId, LauncherAppWidgetInfo.FLAG_UI_NOT_READY);
                if (widgetInfo != null) {
                    // Since the view was just bound, also launch the configure activity if needed
                    LauncherAppWidgetProviderInfo provider = mAppWidgetManager
                            .getLauncherAppWidgetInfo(appWidgetId);
                    if (provider != null && provider.configure != null) {
                        startRestoredWidgetReconfigActivity(provider, widgetInfo);
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

        Runnable exitSpringLoaded = new Runnable() {
            @Override
            public void run() {
                exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED),
                        EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
            }
        };

        if (requestCode == REQUEST_EDIT_ICON) {
            if (data != null && data.hasExtra("alternateIcon")) {
                mEditingItem.setIcon(this, data.getStringExtra("alternateIcon"));
            } else {
                mEditingItem.setIcon(this, null);
            }
            if (mEditingItem.getComponentName() != null)
                Utilities.updatePackage(this, mEditingItem.getUser(), mEditingItem.getComponentName().getPackageName());
        }

        if (requestCode == REQUEST_BIND_APPWIDGET) {
            // This is called only if the user did not previously have permissions to bind widgets
            final int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId, requestArgs);
                mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(
                        appWidgetId, requestArgs, null,
                        requestArgs.getWidgetProvider(),
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY);
            }
            return;
        } else if (requestCode == REQUEST_PICK_WALLPAPER) {
            if (resultCode == RESULT_OK && mWorkspace.isInOverviewMode()) {
                // User could have free-scrolled between pages before picking a wallpaper; make sure
                // we move to the closest one now.
                mWorkspace.setCurrentPage(mWorkspace.getPageNearestToCenterOfScreen());
                showWorkspace(false);
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
                final Runnable onComplete = new Runnable() {
                    @Override
                    public void run() {
                        exitSpringLoadedDragModeDelayed(false, 0, null);
                    }
                };

                mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
            } else {
                if (requestArgs.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    // When the screen id represents an actual screen (as opposed to a rank)
                    // we make sure that the drop page actually exists.
                    requestArgs.screenId =
                            ensurePendingDropLayoutExists(requestArgs.screenId);
                }
                final CellLayout dropLayout =
                        mWorkspace.getScreenWithId(requestArgs.screenId);

                dropLayout.setDropPending(true);
                final Runnable onComplete = new Runnable() {
                    @Override
                    public void run() {
                        completeTwoStageWidgetDrop(resultCode, appWidgetId, requestArgs);
                        dropLayout.setDropPending(false);
                    }
                };
                mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
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
                mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);

            } else if (resultCode == RESULT_CANCELED) {
                mWorkspace.removeExtraEmptyScreenDelayed(true, exitSpringLoaded,
                        ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
            }
        }
        mDragLayer.clearAnimatedView();
    }

    @Override
    protected void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        handleActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        PendingRequestArgs pendingArgs = mPendingRequestArgs;
        if (requestCode == REQUEST_PERMISSION_CALL_PHONE && pendingArgs != null
                && pendingArgs.getRequestCode() == REQUEST_PERMISSION_CALL_PHONE) {
            setWaitingForResult(null);

            View v = null;
            CellLayout layout = getCellLayout(pendingArgs.container, pendingArgs.screenId);
            if (layout != null) {
                v = layout.getChildAt(pendingArgs.cellX, pendingArgs.cellY);
            }
            Intent intent = pendingArgs.getPendingIntent();

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivitySafely(v, intent, null);
            } else {
                // TODO: Show a snack bar with link to settings
                Toast.makeText(this, getString(R.string.msg_no_phone_permission,
                        getString(R.string.app_name)), Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_PERMISSION_STORAGE_ACCESS){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
                final Launcher _this = this;
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int i) {
                        Utilities.requestStoragePermission(_this);
                    }
                };
                new AlertDialog.Builder(this).setTitle(R.string.title_storage_permission_required)
                        .setMessage(R.string.content_storage_permission_required).setPositiveButton(android.R.string.ok, listener)
                        .setCancelable(false).show();
            }
        }
    }

    /**
     * Check to see if a given screen id exists. If not, create it at the end, return the new id.
     *
     * @param screenId the screen id to check
     * @return the new screen, or screenId if it exists
     */
    private long ensurePendingDropLayoutExists(long screenId) {
        CellLayout dropLayout = mWorkspace.getScreenWithId(screenId);
        if (dropLayout == null) {
            // it's possible that the add screen was removed because it was
            // empty and a re-bind occurred
            mWorkspace.addExtraEmptyScreen();
            return mWorkspace.commitExtraEmptyScreen();
        } else {
            return screenId;
        }
    }

    @Thunk
    void completeTwoStageWidgetDrop(
            final int resultCode, final int appWidgetId, final PendingRequestArgs requestArgs) {
        CellLayout cellLayout = mWorkspace.getScreenWithId(requestArgs.screenId);
        Runnable onCompleteRunnable = null;
        int animationType = 0;

        AppWidgetHostView boundWidget = null;
        if (resultCode == RESULT_OK) {
            animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
            final AppWidgetHostView layout = mAppWidgetHost.createView(this, appWidgetId,
                    requestArgs.getWidgetProvider());
            boundWidget = layout;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    completeAddAppWidget(appWidgetId, requestArgs, layout, null);
                    exitSpringLoadedDragModeDelayed(true, EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
                }
            };
        } else if (resultCode == RESULT_CANCELED) {
            mAppWidgetHost.deleteAppWidgetId(appWidgetId);
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
        FirstFrameAnimatorHelper.setIsVisible(false);

        if (Utilities.ATLEAST_NOUGAT_MR1) {
            mAppWidgetHost.stopListening();
        }

        mLauncherTab.getClient().onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirstFrameAnimatorHelper.setIsVisible(true);

        if (Utilities.ATLEAST_NOUGAT_MR1) {
            mAppWidgetHost.startListening();
        }

        if (!isWorkspaceLoading()) {
            NotificationListener.setNotificationsChangedListener(mPopupDataProvider);
        }

        mLauncherTab.getClient().onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        setScreenOrientation();
        // Restore the previous launcher state
        if (mOnResumeState == State.WORKSPACE) {
            showWorkspace(false);
        } else if (mOnResumeState == State.APPS) {
            showAppsView(false /* animated */, mAppsView.shouldRestoreImeState() /* focusSearchBar */);
        } else if (mOnResumeState == State.WIDGETS) {
            showWidgetsView(false, false);
        }
        mOnResumeState = State.NONE;

        mPaused = false;
        if (mOnResumeNeedsLoad) {
            setWorkspaceLoading(true);
            mModel.startLoader(getCurrentWorkspaceScreen());
            mOnResumeNeedsLoad = false;
        }
        if (mBindOnResumeCallbacks.size() > 0) {
            // We might have postponed some bind calls until onResume (see waitUntilResume) --
            // execute them here

            for (int i = 0; i < mBindOnResumeCallbacks.size(); i++) {
                mBindOnResumeCallbacks.get(i).run();
            }
            mBindOnResumeCallbacks.clear();
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

        mWorkspace.onResume();

        if (!isWorkspaceLoading()) {
            // Process any items that were added while Launcher was away.
            InstallShortcutReceiver.disableAndFlushInstallQueue(this);

            // Refresh shortcuts if the permission changed.
            mModel.refreshShortcutsIfRequired();
        }

        if (shouldShowDiscoveryBounce()) {
            mAllAppsController.showDiscoveryBounce();
        }
        mIsResumeFromActionScreenOff = false;

        mLauncherTab.getClient().onResume();

        if (mCurrentDialog != null) {
            mCurrentDialog.onResume();
        }

        if (reloadIcons) {
            reloadIcons = false;
            reloadIcons();
        }

        if (kill) {
            kill = false;
            Utilities.restartLauncher(getApplicationContext());
        }

        if (updateWallpaper) {
            updateWallpaper = false;
            mBlurWallpaperProvider.updateAsync();
        }

        mDisableEditing = Utilities.getPrefs(this).getLockDesktop();
    }

    private void reloadIcons() {
        mIconCache.pip.updateIconPack();
        mIconCache.clear();
        Utilities.restartLauncher(getApplicationContext());
    }

    @Override
    protected void onPause() {
        // Ensure that items added to Launcher are queued until Launcher returns
        InstallShortcutReceiver.enableInstallQueue();

        super.onPause();
        mPaused = true;
        mDragController.cancelDrag();
        mDragController.resetLastGestureUpTime();

        mLauncherTab.getClient().onPause();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Flag the loader to stop early before switching
        if (mModel.isCurrentCallbacks(this)) {
            mModel.stopLoader();
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
        return keyCode == KeyEvent.KEYCODE_MENU && event.isLongPress() || handled;

    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // Ignore the menu key if we are currently dragging
            if (!mDragController.isDragging()) {
                // Close any open folders
                closeFolder();

                // Close any shortcuts containers
                closeFloatingContainer();

                // Stop resizing any widgets
                mWorkspace.exitWidgetResizeMode();

                // Show the overview mode if we are on the workspace
                if (mState == State.WORKSPACE && !mWorkspace.isInOverviewMode() &&
                        !mWorkspace.isSwitchingState()) {
                    mOverviewPanel.requestFocus();
                    showOverviewMode(true, false /* requestButtonFocus */);
                }
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.hasModifiers(4096)) {
            switch (keyCode) {
                case 29:
                    if (this.mState == State.WORKSPACE) {
                        showAppsView(true, true);
                        return true;
                    }
                    break;
                case 43:
                    if (new CustomActionsPopup(this, getCurrentFocus()).show()) {
                        return true;
                    }
                    break;
                case 47:
                    View currentFocus = getCurrentFocus();
                    if ((currentFocus instanceof BubbleTextView) && (currentFocus.getTag() instanceof ItemInfo) && this.mAccessibilityDelegate.performAction(currentFocus, (ItemInfo) currentFocus.getTag(), R.id.action_deep_shortcuts)) {
                        PopupContainerWithArrow.getOpen(this).requestFocus();
                        return true;
                    }
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    private String getTypedText() {
        return mDefaultKeySsb.toString();
    }

    public void clearTypedText() {
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
        for (State stateValue : stateValues) {
            if (stateValue.ordinal() == stateOrdinal) {
                state = stateValue;
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
        if (state == State.APPS || state == State.WIDGETS) {
            mOnResumeState = state;
        }

        int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN,
                PagedView.INVALID_RESTORE_PAGE);
        if (currentScreen != PagedView.INVALID_RESTORE_PAGE) {
            mWorkspace.setRestorePage(currentScreen);
        }

        PendingRequestArgs requestArgs = savedState.getParcelable(RUNTIME_STATE_PENDING_REQUEST_ARGS);
        if (requestArgs != null) {
            setWaitingForResult(requestArgs);
        }

        mPendingActivityResult = savedState.getParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT);
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        View mLauncherView = findViewById(R.id.launcher);
        mDragLayer = findViewById(R.id.drag_layer);
        mFocusHandler = mDragLayer.getFocusIndicatorHelper();
        mWorkspace = mDragLayer.findViewById(R.id.workspace);
        mQsbContainer = mDragLayer.findViewById(R.id.qsb_container);
        mWorkspace.initParentViews(mDragLayer);

        if (mSnowfallEnabled) {
            Log.d(TAG, "inflating snowfall");
            if (!Utilities.isBlacklistedAppInstalled(this)) {
                getLayoutInflater().inflate(mPlanesEnabled ? R.layout.snowfall_planes : R.layout.snowfall, (ViewGroup) findViewById(R.id.launcher_background), true);
            } else {
                getLayoutInflater().inflate(R.layout.snowfall_smiles, (ViewGroup) findViewById(R.id.launcher_background), true);
            }
        }

        if (mPlanesEnabled) {
            Log.d(TAG, "inflating planes");
            getLayoutInflater().inflate(R.layout.planes, (ViewGroup) mLauncherView, true);
        }

        mLauncherView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        // Setup the drag layer
        mDragLayer.setup(this, mDragController, mAllAppsController);

        // Setup the hotseat
        mHotseat = findViewById(R.id.hotseat);
        if (mHotseat != null) {
            mHotseat.setOnLongClickListener(this);
        }

        // Setup the overview panel
        setupOverviewPanel();

        // Setup the workspace
        mWorkspace.setOnLongClickListener(this);
        mWorkspace.setup(mDragController);
        // Until the workspace is bound, ensure that we keep the wallpaper offset locked to the
        // default state, otherwise we will update to the wrong offsets in RTL
        mWorkspace.lockWallpaperToDefaultPage();
        mWorkspace.bindAndInitFirstWorkspaceScreen(null /* recycled qsb */);
        mDragController.addDragListener(mWorkspace);

        // Get the search/delete/uninstall bar
        mDropTargetBar = mDragLayer.findViewById(R.id.drop_target_bar);

        // Setup Apps and Widgets
        mAppsView = findViewById(R.id.apps_view);
        mWidgetsView = findViewById(R.id.widgets_view);
        mAppsView.setSearchBarController(new UnicodeStrippedAppSearchController());

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        mDragController.setDragScoller(mWorkspace);
        mDragController.setScrollView(mDragLayer);
        mDragController.setMoveTarget(mWorkspace);
        mDragController.addDropTarget(mWorkspace);
        mDropTargetBar.setup(mDragController);

        mAllAppsController.setupViews(mAppsView, mHotseat, mWorkspace);
    }

    private void setupOverviewPanel() {
        mOverviewPanel = findViewById(R.id.overview_panel);

        // Long-clicking buttons in the overview panel does the same thing as clicking them.
        OnLongClickListener performClickOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return v.performClick();
            }
        };

        // Bind wallpaper button actions
        View wallpaperButton = findViewById(R.id.wallpaper_button);
        if (Utilities.isWallapaperAllowed(getApplicationContext())) {
            wallpaperButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!mWorkspace.isSwitchingState()) {
                        onClickWallpaperPicker(view);
                    }
                }
            });
            wallpaperButton.setOnLongClickListener(performClickOnLongClick);
            wallpaperButton.setOnTouchListener(getHapticFeedbackTouchListener());
        } else {
            wallpaperButton.setVisibility(View.GONE);
        }


        // Bind widget button actions
        mWidgetsButton = findViewById(R.id.widget_button);
        mWidgetsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mWorkspace.isSwitchingState()) {
                    onClickAddWidgetButton();
                }
            }
        });
        mWidgetsButton.setOnLongClickListener(performClickOnLongClick);
        mWidgetsButton.setOnTouchListener(getHapticFeedbackTouchListener());

        // Bind settings actions
        View settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mWorkspace.isSwitchingState()) {
                    onClickSettingsButton(view);
                }
            }
        });
        settingsButton.setOnLongClickListener(performClickOnLongClick);
        settingsButton.setOnTouchListener(getHapticFeedbackTouchListener());

        mOverviewPanel.setAlpha(0f);
    }

    /**
     * Sets the all apps handle.
     */
    public void setAllAppsHandle(View allAppsHandle) {
        mAllAppsHandle = allAppsHandle;
    }

    public View getStartViewForAllAppsRevealAnimation() {
        return mWorkspace.getPageIndicator();
    }

    public View getWidgetsButton() {
        return mWidgetsButton;
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     */
    View createShortcut(ShortcutInfo info) {
        return createShortcut((ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param parent The group the shortcut belongs to.
     * @param info   The data structure describing the shortcut.
     * @return A View inflated from layoutResId.
     */
    public View createShortcut(ViewGroup parent, ShortcutInfo info) {
        BubbleTextView favorite = (BubbleTextView) getLayoutInflater().inflate(R.layout.app_icon,
                parent, false);
        favorite.applyFromShortcutInfo(info);
        favorite.setCompoundDrawablePadding(mDeviceProfile.iconDrawablePaddingPx);
        favorite.setOnClickListener(this);
        favorite.setOnFocusChangeListener(mFocusHandler);
        return favorite;
    }

    /**
     * Add a shortcut to the workspace.
     *
     * @param data The intent describing the shortcut.
     */
    private void completeAddShortcut(Intent data, long container, long screenId, int cellX,
                                     int cellY, PendingRequestArgs args) {
        int[] cellXY = mTmpAddItemCellCoordinates;
        CellLayout layout = getCellLayout(container, screenId);

        ShortcutInfo info = InstallShortcutReceiver.fromShortcutIntent(this, data);
        if (info == null || args.getRequestCode() != REQUEST_CREATE_SHORTCUT ||
                args.getPendingIntent().getComponent() == null) {
            return;
        }
        if (!PackageManagerHelper.hasPermissionForActivity(
                this, info.intent, args.getPendingIntent().getComponent().getPackageName())) {
            // The app is trying to add a shortcut without sufficient permissions
            Log.e(TAG, "Ignoring malicious intent " + info.intent.toUri(0));
            return;
        }
        final View view = createShortcut(info);

        boolean foundCellSpan;
        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
            foundCellSpan = true;

            // If appropriate, either create a folder or add to an existing folder
            if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                    true, null, null)) {
                return;
            }
            DragObject dragObject = new DragObject();
            dragObject.dragInfo = info;
            if (mWorkspace.addToExistingFolderIfNecessary(layout, cellXY, 0, dragObject,
                    true)) {
                return;
            }
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
        }

        if (!foundCellSpan) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        LauncherModel.addItemToDatabase(this, info, container, screenId, cellXY[0], cellXY[1]);

        mWorkspace.addInScreen(view, container, screenId, cellXY[0], cellXY[1], 1, 1,
                isWorkspaceLocked());
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

        LauncherAppWidgetInfo launcherInfo;
        launcherInfo = new LauncherAppWidgetInfo(appWidgetId, appWidgetInfo.provider);
        launcherInfo.spanX = itemInfo.spanX;
        launcherInfo.spanY = itemInfo.spanY;
        launcherInfo.minSpanX = itemInfo.minSpanX;
        launcherInfo.minSpanY = itemInfo.minSpanY;
        launcherInfo.user = mAppWidgetManager.getUser(appWidgetInfo);

        LauncherModel.addItemToDatabase(this, launcherInfo,
                itemInfo.container, itemInfo.screenId, itemInfo.cellX, itemInfo.cellY);

        if (hostView == null) {
            // Perform actual inflation because we're live
            hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
        }
        hostView.setVisibility(View.VISIBLE);
        addAppWidgetToWorkspace(hostView, launcherInfo, appWidgetInfo, isWorkspaceLocked());
    }

    private void addAppWidgetToWorkspace(
            AppWidgetHostView hostView, LauncherAppWidgetInfo item,
            LauncherAppWidgetProviderInfo appWidgetInfo, boolean insert) {
        hostView.setTag(item);
        item.onBindAppWidget(this, hostView);

        hostView.setFocusable(true);
        hostView.setOnFocusChangeListener(mFocusHandler);

        mWorkspace.addInScreen(hostView, item.container, item.screenId,
                item.cellX, item.cellY, item.spanX, item.spanY, insert);

        addWidgetToAutoAdvanceIfNeeded(hostView, appWidgetInfo);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mUserPresent = false;
                mDragLayer.clearAllResizeFrames();
                updateAutoAdvanceState();

                // Reset AllApps to its initial state only if we are not in the middle of
                // processing a multi-step drop
                if (mAppsView != null && mWidgetsView != null && mPendingRequestArgs == null) {
                    if (!showWorkspace(false)) {
                        // If we are already on the workspace, then manually reset all apps
                        mAppsView.reset();
                    }
                }
                mIsResumeFromActionScreenOff = true;
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mUserPresent = true;
                updateAutoAdvanceState();
            }
        }
    };

    public void updateIconBadges(final Set<PackageUserKey> set) {
        Runnable anonymousClass13 = new Runnable() {
            @Override
            public void run() {
                Launcher.this.mWorkspace.updateIconBadges(set);
                Launcher.this.mAppsView.updateIconBadges(set);
                PopupContainerWithArrow open = PopupContainerWithArrow.getOpen(Launcher.this);
                if (open != null) {
                    open.updateNotificationHeader(set);
                }
            }
        };
        if (!waitUntilResume(anonymousClass13)) {
            anonymousClass13.run();
        }
    }

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

        mLauncherTab.getClient().onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;

        if (mAttached) {
            unregisterReceiver(mReceiver);
            mAttached = false;
        }
        updateAutoAdvanceState();

        mLauncherTab.getClient().onDetachedFromWindow();
    }

    public void onWindowVisibilityChanged(int visibility) {
        mVisible = visibility == View.VISIBLE;
        updateAutoAdvanceState();
        // The following code used to be in onResume, but it turns out onResume is called when
        // you're in All Apps and click home to go to the workspace. onWindowVisibilityChanged
        // is a more appropriate event to handle
        if (mVisible) {
            if (!mWorkspaceLoading) {
                final ViewTreeObserver observer = mWorkspace.getViewTreeObserver();
                // We want to let Launcher draw itself at least once before we force it to build
                // layers on all the workspace pages, so that transitioning to Launcher from other
                // apps is nice and speedy.
                observer.addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                    private boolean mStarted = false;

                    @Override
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
                            @Override
                            public void run() {
                                if (mWorkspace != null &&
                                        mWorkspace.getViewTreeObserver() != null) {
                                    mWorkspace.getViewTreeObserver().
                                            removeOnDrawListener(listener);
                                }
                            }
                        });
                    }
                });
            }
            clearTypedText();
        }
    }

    @Thunk
    void sendAdvanceMessage(long delay) {
        mHandler.removeMessages(ADVANCE_MSG);
        Message msg = mHandler.obtainMessage(ADVANCE_MSG);
        mHandler.sendMessageDelayed(msg, delay);
        mAutoAdvanceSentTime = System.currentTimeMillis();
    }

    @Thunk
    void updateAutoAdvanceState() {
        boolean autoAdvanceRunning = mVisible && mUserPresent && !mWidgetsToAdvance.isEmpty();
        if (autoAdvanceRunning != mAutoAdvanceRunning) {
            mAutoAdvanceRunning = autoAdvanceRunning;
            if (autoAdvanceRunning) {
                long delay = mAutoAdvanceTimeLeft == -1 ? ADVANCE_INTERVAL : mAutoAdvanceTimeLeft;
                sendAdvanceMessage(delay);
            } else {
                if (!mWidgetsToAdvance.isEmpty()) {
                    mAutoAdvanceTimeLeft = Math.max(0, ADVANCE_INTERVAL -
                            (System.currentTimeMillis() - mAutoAdvanceSentTime));
                }
                mHandler.removeMessages(ADVANCE_MSG);
                mHandler.removeMessages(0); // Remove messages sent using postDelayed()
            }
        }
    }

    @Thunk
    final Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == ADVANCE_MSG) {
                int i = 0;
                for (View key : mWidgetsToAdvance.keySet()) {
                    final View v = key.findViewById(mWidgetsToAdvance.get(key).autoAdvanceViewId);
                    final int delay = ADVANCE_STAGGER * i;
                    if (v instanceof Advanceable) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                ((Advanceable) v).advance();
                            }
                        }, delay);
                    }
                    i++;
                }
                sendAdvanceMessage(ADVANCE_INTERVAL);
            }
            return true;
        }
    });

    private void addWidgetToAutoAdvanceIfNeeded(View hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null || appWidgetInfo.autoAdvanceViewId == -1) return;
        View v = hostView.findViewById(appWidgetInfo.autoAdvanceViewId);
        if (v instanceof Advanceable) {
            mWidgetsToAdvance.put(hostView, appWidgetInfo);
            ((Advanceable) v).fyiWillBeAdvancedByHostKThx();
            updateAutoAdvanceState();
        }
    }

    private void removeWidgetToAutoAdvance(View hostView) {
        if (mWidgetsToAdvance.containsKey(hostView)) {
            mWidgetsToAdvance.remove(hostView);
            updateAutoAdvanceState();
        }
    }

    public void showOutOfSpaceMessage(boolean isHotseatLayout) {
        int strId = (isHotseatLayout ? R.string.hotseat_out_of_space : R.string.out_of_space);
        Toast.makeText(this, getString(strId), Toast.LENGTH_SHORT).show();
    }

    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    public AllAppsContainerView getAppsView() {
        return mAppsView;
    }

    public WidgetsContainerView getWidgetsView() {
        return mWidgetsView;
    }

    public Workspace getWorkspace() {
        return mWorkspace;
    }

    public View getQsbContainer() {
        return mQsbContainer;
    }

    public Hotseat getHotseat() {
        return mHotseat;
    }

    public ViewGroup getOverviewPanel() {
        return mOverviewPanel;
    }

    public DropTargetBar getDropTargetBar() {
        return mDropTargetBar;
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    public void closeSystemDialogs() {
        getWindow().closeAllPanels();

        // Whatever we were doing is hereby canceled.
        setWaitingForResult(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        boolean alreadyOnHome = mHasFocus && ((intent.getFlags() &
                Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

        // Check this condition before handling isActionMain, as this will get reset.
        boolean shouldMoveToDefaultScreen = alreadyOnHome &&
                mState == State.WORKSPACE && AbstractFloatingView.getTopOpenView(this) == null;

        boolean isActionMain = Intent.ACTION_MAIN.equals(intent.getAction());
        if (isActionMain) {
            // also will cancel mWaitingForResult.
            closeSystemDialogs();

            if (mWorkspace == null) {
                // Can be cases where mWorkspace is null, this prevents a NPE
                return;
            }
            // In all these cases, only animate if we're already on home
            mWorkspace.exitWidgetResizeMode();

            closeFolder(alreadyOnHome);
            closeFloatingContainer(alreadyOnHome);
            exitSpringLoadedDragMode();

            // If we are already on home, then just animate back to the workspace,
            // otherwise, just wait until onResume to set the state back to Workspace
            if (alreadyOnHome) {
                if (!Utilities.getPrefs(this).getHomeOpensDrawer() || mState != State.WORKSPACE || mWorkspace.getCurrentPage() != 0 || mOverviewPanel.getVisibility() == View.VISIBLE) {
                    showWorkspace(true);
                } else {
                    showAppsView(true, false);
                }
            } else {
                mOnResumeState = State.WORKSPACE;
            }


            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            // Reset the apps view
            if (!alreadyOnHome && mAppsView != null && !Utilities.getPrefs(this).getKeepScrollState()) {
                mAppsView.scrollToTop();
            }

            // Reset the widgets view
            if (!alreadyOnHome && mWidgetsView != null) {
                mWidgetsView.scrollToTop();
            }

            mLauncherTab.getClient().hideOverlay(true);
        }

        // Defer moving to the default screen until after we callback to the LauncherCallbacks
        // as slow logic in the callbacks eat into the time the scroller expects for the snapToPage
        // animation.
        if (isActionMain) {
            if (shouldMoveToDefaultScreen && !mWorkspace.isTouchActive()) {

                // We use this flag to suppress noisy callbacks above custom content state
                // from onResume.
                mWorkspace.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mWorkspace != null) {
                            mWorkspace.moveToDefaultScreen(true);
                        }
                    }
                });
            }
        }

        if (mPlanesEnabled && mPlanesAnimator == null) {
            int height = findViewById(R.id.launcher).getHeight();
            final View planes = findViewById(R.id.planes);
            mPlanesAnimator = ObjectAnimator.ofFloat(planes, "translationY", height, -height);
            mPlanesAnimator.setDuration(2000);
            mPlanesAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    planes.setVisibility(View.GONE);
                    mPlanesAnimator = null;
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    planes.setVisibility(View.VISIBLE);
                }
            });
            mPlanesAnimator.start();
        }

        dismissDialog();
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        for (int page : mSynchronouslyBoundPages) {
            mWorkspace.restoreInstanceStateForChild(page);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mWorkspace.getChildCount() > 0) {
            outState.putInt(RUNTIME_STATE_CURRENT_SCREEN,
                    mWorkspace.getNextPage());

        }
        super.onSaveInstanceState(outState);

        outState.putInt(RUNTIME_STATE, mState.ordinal());
        // We close any open folder since it will not be re-opened, and we need to make sure
        // this state is reflected.
        // TODO: Move folderInfo.isOpened out of the model and make it a UI state.
        closeFolder(false);
        closeFloatingContainer(false);

        if (mPendingRequestArgs != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_REQUEST_ARGS, mPendingRequestArgs);
        }
        if (mPendingActivityResult != null) {
            outState.putParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT, mPendingActivityResult);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove all pending runnables
        mHandler.removeMessages(ADVANCE_MSG);
        mHandler.removeMessages(0);
        mWorkspace.removeCallbacks(mBuildLayersRunnable);
        mWorkspace.removeFolderListeners();

        // Stop callbacks from LauncherModel
        // It's possible to receive onDestroy after a new Launcher activity has
        // been created. In this case, don't interfere with the new Launcher.
        if (mModel.isCurrentCallbacks(this)) {
            mModel.stopLoader();
            LauncherAppState.getInstance().setLauncher(null);
        }

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }
        mAppWidgetHost = null;

        mWidgetsToAdvance.clear();

        TextKeyListener.getInstance().release();

        ((AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE))
                .removeAccessibilityStateChangeListener(this);

        unregisterReceiver(mUiBroadcastReceiver);

        LauncherAnimUtils.onDestroyActivity();

        mLauncherTab.getClient().onDestroy();
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
                                           Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        try {
            super.startIntentSenderForResult(intent, requestCode,
                    fillInIntent, flagsMask, flagsValues, extraFlags, options);
        } catch (IntentSender.SendIntentException e) {
            throw new ActivityNotFoundException();
        }
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
                            Bundle appSearchData, boolean globalSearch) {

        if (initialQuery == null) {
            // Use any text typed in the launcher as the initial query
            initialQuery = getTypedText();
        }
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString("source", "launcher-search");
        }

        // Starting search from the callbacks failed. Start the default global search.
        startGlobalSearch(initialQuery, selectInitialQuery, appSearchData, null);

        // We need to show the workspace after starting the search
        showWorkspace(true);
    }

    public void onPullDownAction(int pullDownAction) {
        switch (pullDownAction) {
            case FeatureFlags.PULLDOWN_NOTIFICATIONS:
                openNotifications();
                break;
            case FeatureFlags.PULLDOWN_SEARCH:
                startActivity(new Intent("com.google.android.googlequicksearchbox.TEXT_ASSIST")
                        .addFlags(268468224)
                        .setPackage("com.google.android.googlequicksearchbox"));
                break;
            case FeatureFlags.PULLDOWN_APPS_SEARCH:
                onLongClickAllAppsHandle();
                break;
        }
    }

    @SuppressLint("PrivateApi")
    private void openNotifications() {
        try {
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @SuppressWarnings("ResourceType")
    public void closeNotifications() {
        try {
            Class.forName("android.app.StatusBarManager").getMethod("collapsePanels").invoke(getSystemService("statusbar"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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
        // Set source to package name of app that starts global search if not set already.
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
    public boolean onSearchRequested() {
        startSearch(null, false, null, true);
        // Use a custom animation for launching search
        return true;
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mPendingRequestArgs != null;
    }

    public boolean isEditingDisabled() {
        return mDisableEditing;
    }

    public boolean isWorkspaceLoading() {
        return mWorkspaceLoading;
    }

    private void setWorkspaceLoading(boolean value) {
        mWorkspaceLoading = value;
    }

    private void setWaitingForResult(PendingRequestArgs args) {
        mPendingRequestArgs = args;
    }

    void addAppWidgetFromDropImpl(int appWidgetId, ItemInfo info, AppWidgetHostView boundWidget,
                                  LauncherAppWidgetProviderInfo appWidgetInfo) {
        addAppWidgetImpl(appWidgetId, info, boundWidget, appWidgetInfo, 0);
    }

    void addAppWidgetImpl(int appWidgetId, ItemInfo info,
                          AppWidgetHostView boundWidget, LauncherAppWidgetProviderInfo appWidgetInfo,
                          int delay) {
        if (appWidgetInfo.configure != null) {
            setWaitingForResult(PendingRequestArgs.forWidgetInfo(appWidgetId, appWidgetInfo, info));

            // Launch over to configure widget, if needed
            mAppWidgetManager.startConfigActivity(appWidgetId, this,
                    mAppWidgetHost, REQUEST_CREATE_APPWIDGET);
        } else {
            // Otherwise just add it
            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    // Exit spring loaded mode if necessary after adding the widget
                    exitSpringLoadedDragModeDelayed(true, EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT,
                            null);
                }
            };
            completeAddAppWidget(appWidgetId, info, boundWidget, appWidgetInfo);
            mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete, delay, false);
        }
    }

    public void addPendingItem(PendingAddItemInfo info, long container, long screenId,
                               int[] cell, int spanX, int spanY) {
        info.container = container;
        info.screenId = screenId;
        if (cell != null) {
            info.cellX = cell[0];
            info.cellY = cell[1];
        }
        info.spanX = spanX;
        info.spanY = spanY;

        switch (info.itemType) {
            case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                addAppWidgetFromDrop((PendingAddWidgetInfo) info);
                break;
            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                processShortcutFromDrop(info);
                break;
            default:
                throw new IllegalStateException("Unknown item type: " + info.itemType);
        }
    }

    /**
     * Process a shortcut drop.
     */
    private void processShortcutFromDrop(PendingAddItemInfo info) {
        Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(info.componentName);
        setWaitingForResult(PendingRequestArgs.forIntent(REQUEST_CREATE_SHORTCUT, intent, info));
        Utilities.startActivityForResultSafely(this, intent, REQUEST_CREATE_SHORTCUT);
    }

    /**
     * Process a widget drop.
     */
    private void addAppWidgetFromDrop(PendingAddWidgetInfo info) {
        AppWidgetHostView hostView = info.boundWidget;
        int appWidgetId;
        if (hostView != null) {
            // In the case where we've prebound the widget, we remove it from the DragLayer
            getDragLayer().removeView(hostView);

            appWidgetId = hostView.getAppWidgetId();
            addAppWidgetFromDropImpl(appWidgetId, info, hostView, info.info);

            // Clear the boundWidget so that it doesn't get destroyed.
            info.boundWidget = null;
        } else {
            // In this case, we either need to start an activity to get permission to bind
            // the widget, or we need to start an activity to configure the widget, or both.
            appWidgetId = getAppWidgetHost().allocateAppWidgetId();
            Bundle options = info.bindOptions;

            boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
                    appWidgetId, info.info, options);
            if (success) {
                addAppWidgetFromDropImpl(appWidgetId, info, null, info.info);
            } else {
                setWaitingForResult(PendingRequestArgs.forWidgetInfo(appWidgetId, info.info, info));
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.componentName);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, mAppWidgetManager.getUser(info.info));
                // TODO: we need to make sure that this accounts for the options bundle.
                // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
                startActivityForResult(intent, REQUEST_BIND_APPWIDGET);
            }
        }
    }

    FolderIcon addFolder(CellLayout layout, long container, final long screenId, int cellX,
                         int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = getText(R.string.folder_name);

        // Update the model
        LauncherModel.addItemToDatabase(Launcher.this, folderInfo, container, screenId,
                cellX, cellY);

        // Create the view
        FolderIcon newFolder =
                FolderIcon.fromXml(R.layout.folder_icon, this, layout, folderInfo);
        mWorkspace.addInScreen(newFolder, container, screenId, cellX, cellY, 1, 1,
                isWorkspaceLocked());
        // Force measure the new folder icon
        CellLayout parent = mWorkspace.getParentCellLayoutForView(newFolder);
        parent.getShortcutsAndWidgets().measureChild(newFolder);
        return newFolder;
    }

    /**
     * Unbinds the view for the specified item, and removes the item and all its children.
     *
     * @param v            the view being removed.
     * @param itemInfo     the {@link ItemInfo} for this view.
     * @param deleteFromDb whether or not to delete this item from the db.
     */
    public boolean removeItem(View v, final ItemInfo itemInfo, boolean deleteFromDb) {
        if (itemInfo instanceof ShortcutInfo) {
            // Remove the shortcut from the folder before removing it from launcher
            View folderIcon = mWorkspace.getHomescreenIconByItemId(itemInfo.container);
            if (folderIcon instanceof FolderIcon) {
                ((FolderInfo) folderIcon.getTag()).remove((ShortcutInfo) itemInfo, true);
            } else {
                mWorkspace.removeWorkspaceItem(v);
            }
            if (deleteFromDb) {
                LauncherModel.deleteItemFromDatabase(this, itemInfo);
            }
        } else if (itemInfo instanceof FolderInfo) {
            final FolderInfo folderInfo = (FolderInfo) itemInfo;
            if (v instanceof FolderIcon) {
                ((FolderIcon) v).removeListeners();
            }
            mWorkspace.removeWorkspaceItem(v);
            if (deleteFromDb) {
                LauncherModel.deleteFolderAndContentsFromDatabase(this, folderInfo);
            }
        } else if (itemInfo instanceof LauncherAppWidgetInfo) {
            final LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) itemInfo;
            mWorkspace.removeWorkspaceItem(v);
            removeWidgetToAutoAdvance(v);
            if (deleteFromDb) {
                deleteWidgetInfo(widgetInfo);
            }
        } else {
            return false;
        }
        return true;
    }

    /**
     * Deletes the widget info and the widget id.
     */
    private void deleteWidgetInfo(final LauncherAppWidgetInfo widgetInfo) {
        final LauncherAppWidgetHost appWidgetHost = getAppWidgetHost();
        if (appWidgetHost != null && widgetInfo.isWidgetIdAllocated()) {
            // Deleting an app widget ID is a void call but writes to disk before returning
            // to the caller...
            new AsyncTask<Void, Void, Void>() {
                @Override
                public Void doInBackground(Void... args) {
                    appWidgetHost.deleteAppWidgetId(widgetInfo.appWidgetId);
                    return null;
                }
            }.executeOnExecutor(Utilities.THREAD_POOL_EXECUTOR);
        }
        LauncherModel.deleteItemFromDatabase(this, widgetInfo);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
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
        if (mDragController.isDragging()) {
            mDragController.cancelDrag();
            return;
        }

        AbstractFloatingView topOpenView = AbstractFloatingView.getTopOpenView(this);
        if (topOpenView != null) {
            if (topOpenView.getActiveTextView() != null) {
                topOpenView.getActiveTextView().dispatchBackKey();
            } else {
                topOpenView.close(true);
            }
        } else if (isAppsViewVisible()) {
            showWorkspace(true);
        } else if (isWidgetsViewVisible()) {
            showOverviewMode(true);
        } else if (mWorkspace.isInOverviewMode()) {
            showWorkspace(true);
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
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    @Override
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
                showWorkspace(true);
            }
            return;
        }

        if (v instanceof CellLayout) {
            if (mWorkspace.isInOverviewMode()) {
                mWorkspace.snapToPageFromOverView(mWorkspace.indexOfChild(v));
                showWorkspace(true);
            }
            return;
        }

        if (v instanceof AllAppsIconRowView)
            v = ((AllAppsIconRowView) v).icon;

        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            onClickAppShortcut(v);
        } else if (tag instanceof FolderInfo) {
            if (v instanceof FolderIcon) {
                onClickFolderIcon(v);
            }
        } else if (v == mAllAppsHandle) {
            onClickAllAppsHandle();
        } else if (tag instanceof AppInfo) {
            startAppShortcutOrInfoActivity(v);
        } else if (tag instanceof LauncherAppWidgetInfo) {
            if (v instanceof PendingAppWidgetHostView) {
                onClickPendingWidget((PendingAppWidgetHostView) v);
            }
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    /**
     * Event handler for the app widget view which has not fully restored.
     */
    public void onClickPendingWidget(final PendingAppWidgetHostView v) {
        if (mIsSafeModeEnabled) {
            Toast.makeText(this, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
            return;
        }

        final LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) v.getTag();
        if (v.isReadyForClickSetup()) {
            if (info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                if (!info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) {
                    // This should not happen, as we make sure that an Id is allocated during bind.
                    return;
                }
                LauncherAppWidgetProviderInfo appWidgetInfo =
                        mAppWidgetManager.findProvider(info.providerName, info.user);
                if (appWidgetInfo != null) {
                    setWaitingForResult(PendingRequestArgs
                            .forWidgetInfo(info.appWidgetId, appWidgetInfo, info));

                    Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, info.appWidgetId);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, appWidgetInfo.provider);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, mAppWidgetManager.getUser(appWidgetInfo));
                    startActivityForResult(intent, REQUEST_BIND_PENDING_APPWIDGET);
                }
            } else {
                LauncherAppWidgetProviderInfo appWidgetInfo =
                        mAppWidgetManager.getLauncherAppWidgetInfo(info.appWidgetId);
                if (appWidgetInfo != null) {
                    startRestoredWidgetReconfigActivity(appWidgetInfo, info);
                }
            }
        } else if (info.installProgress < 0) {
            // The install has not been queued
            final String packageName = info.providerName.getPackageName();
            showBrokenAppInstallDialog(packageName,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            startActivitySafely(v, LauncherModel.getMarketIntent(packageName), info);
                        }
                    });
        } else {
            // Download has started.
            final String packageName = info.providerName.getPackageName();
            startActivitySafely(v, LauncherModel.getMarketIntent(packageName), info);
        }
    }

    private void startRestoredWidgetReconfigActivity(
            LauncherAppWidgetProviderInfo provider, LauncherAppWidgetInfo info) {
        setWaitingForResult(PendingRequestArgs.forWidgetInfo(info.appWidgetId, provider, info));
        mAppWidgetManager.startConfigActivity(
                info.appWidgetId, this, mAppWidgetHost, REQUEST_RECONFIGURE_APPWIDGET);
    }

    protected void onClickAllAppsHandle() {
        if (!isAppsViewVisible()) {
            showAppsView(true, false);
        }
    }

    public void onLongClickAllAppsHandle() {
        if (!isAppsViewVisible()) {
            showAppsView(true, true);
        }
    }

    private void showBrokenAppInstallDialog(final String packageName,
                                            DialogInterface.OnClickListener onSearchClickListener) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.abandoned_promises_title)
                .setMessage(R.string.abandoned_promise_explanation)
                .setPositiveButton(R.string.abandoned_search, onSearchClickListener)
                .setNeutralButton(R.string.abandoned_clean_this,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                final UserHandle user = Utilities.myUserHandle();
                                mWorkspace.removeAbandonedPromise(packageName, user);
                            }
                        })
                .create().show();
    }

    /**
     * Event handler for an app shortcut click.
     *
     * @param v The view that was clicked. Must be a tagged with a {@link ShortcutInfo}.
     */
    protected void onClickAppShortcut(final View v) {
        Object tag = v.getTag();
        if (!(tag instanceof ShortcutInfo)) {
            throw new IllegalArgumentException("Input must be a Shortcut");
        }

        // Open shortcut
        final ShortcutInfo shortcut = (ShortcutInfo) tag;

        if (shortcut.isDisabled != 0) {
            if ((shortcut.isDisabled &
                    ~ShortcutInfo.FLAG_DISABLED_SUSPENDED &
                    ~ShortcutInfo.FLAG_DISABLED_QUIET_USER) == 0) {
                // If the app is only disabled because of the above flags, launch activity anyway.
                // Framework will tell the user why the app is suspended.
            } else {
                if (!TextUtils.isEmpty(shortcut.disabledMessage)) {
                    // Use a message specific to this shortcut, if it has one.
                    Toast.makeText(this, shortcut.disabledMessage, Toast.LENGTH_SHORT).show();
                    return;
                }
                // Otherwise just use a generic error message.
                int error = R.string.activity_not_available;
                if ((shortcut.isDisabled & ShortcutInfo.FLAG_DISABLED_SAFEMODE) != 0) {
                    error = R.string.safemode_shortcut_error;
                } else if ((shortcut.isDisabled & ShortcutInfo.FLAG_DISABLED_BY_PUBLISHER) != 0 ||
                        (shortcut.isDisabled & ShortcutInfo.FLAG_DISABLED_LOCKED_USER) != 0) {
                    error = R.string.shortcut_not_available;
                }
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Check for abandoned promise
        if ((v instanceof BubbleTextView)
                && shortcut.isPromise()
                && !shortcut.hasStatusFlag(ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE)) {
            showBrokenAppInstallDialog(
                    shortcut.getTargetComponent().getPackageName(),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            startAppShortcutOrInfoActivity(v);
                        }
                    });
            return;
        }

        // Start activities
        startAppShortcutOrInfoActivity(v);
    }

    private void startAppShortcutOrInfoActivity(View v) {
        ItemInfo item = (ItemInfo) v.getTag();
        Intent intent = item.getIntent();
        if (intent == null) {
            throw new IllegalArgumentException("Input must have a valid intent");
        }
        boolean success = startActivitySafely(v, intent, item);

        if (success && v instanceof BubbleTextView) {
            mWaitingForResume = (BubbleTextView) v;
            mWaitingForResume.setStayPressed(true);
        }
    }

    /**
     * Event handler for a folder icon click.
     *
     * @param v The view that was clicked. Must be an instance of {@link FolderIcon}.
     */
    protected void onClickFolderIcon(View v) {
        if (!(v instanceof FolderIcon)) {
            throw new IllegalArgumentException("Input must be a FolderIcon");
        }

        FolderIcon folderIcon = (FolderIcon) v;
        if (!folderIcon.getFolderInfo().opened && !folderIcon.getFolder().isDestroyed()) {
            // Open the requested folder
            openFolder(folderIcon);
        }
    }

    /**
     * Event handler for the (Add) Widgets button that appears after a long press
     * on the home screen.
     */
    public void onClickAddWidgetButton() {
        if (mIsSafeModeEnabled) {
            Toast.makeText(this, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
        } else {
            showWidgetsView(true /* animated */, true /* resetPageToZero */);
        }
    }

    /**
     * Event handler for the wallpaper picker button that appears after a long press
     * on the home screen.
     */
    public void onClickWallpaperPicker(View v) {
        if (!Utilities.isWallapaperAllowed(this)) {
            Toast.makeText(this, R.string.msg_disabled_by_admin, Toast.LENGTH_SHORT).show();
            return;
        }

        int pageScroll = mWorkspace.getScrollForPage(mWorkspace.getPageNearestToCenterOfScreen());
        float offset = mWorkspace.mWallpaperOffset.wallpaperOffsetForScroll(pageScroll);

        setWaitingForResult(new PendingRequestArgs(new ItemInfo()));
        startActivityForResult(getWallpaperPickerIntent(v, offset), REQUEST_PICK_WALLPAPER, getActivityLaunchOptions(v));
    }

    @NonNull
    private Intent getWallpaperPickerIntent(View v, float offset) {
        boolean useGoogleWallpaper =
                PackageManagerHelper.isAppEnabled(getPackageManager(), "com.google.android.apps.wallpaper", 0);
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, WallpaperPickerActivity.class),
                useGoogleWallpaper ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

        String pickerPackage = useGoogleWallpaper ? "com.google.android.apps.wallpaper" : getApplicationInfo().packageName;
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER)
                .setPackage(pickerPackage)
                .putExtra(Utilities.EXTRA_WALLPAPER_OFFSET, offset);
        intent.setSourceBounds(getViewBounds(v));
        return intent;
    }

    /**
     * Event handler for a click on the settings button that appears after a long press
     * on the home screen.
     */
    public void onClickSettingsButton(View v) {
        Utilities.getPrefs(this).showSettings(this, v);
    }

    public View.OnTouchListener getHapticFeedbackTouchListener() {
        if (mHapticFeedbackTouchListener == null) {
            mHapticFeedbackTouchListener = new View.OnTouchListener() {
                @SuppressLint("ClickableViewAccessibility")
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

    @Override
    public void onAccessibilityStateChanged(boolean enabled) {
        mDragLayer.onAccessibilityStateChanged(enabled);
    }

    @SuppressLint("NewApi")
    private void startShortcutIntentSafely(Intent intent, Bundle optsBundle, ItemInfo info) {
        try {
            StrictMode.VmPolicy oldPolicy = StrictMode.getVmPolicy();
            try {
                // Temporarily disable deathPenalty on all default checks. For eg, shortcuts
                // containing file Uri's would cause a crash as penaltyDeathOnFileUriExposure
                // is enabled by default on NYC.
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll()
                        .penaltyLog().build());

                if (info instanceof ShortcutInfo && ((ShortcutInfo) info).useDeepShortcutManager) {
                    String id = ((ShortcutInfo) info).getDeepShortcutId();
                    String packageName = intent.getPackage();
                    DeepShortcutManager.getInstance(this).startShortcut(
                            packageName, id, intent.getSourceBounds(), optsBundle, info.user);
                } else {
                    // Could be launching some bookkeeping activity
                    startActivity(intent, optsBundle);
                }
            } finally {
                StrictMode.setVmPolicy(oldPolicy);
            }
        } catch (SecurityException e) {
            // Due to legacy reasons, direct call shortcuts require Launchers to have the
            // corresponding permission. Show the appropriate permission prompt if that
            // is the case.
            if (intent.getComponent() == null
                    && Intent.ACTION_CALL.equals(intent.getAction())
                    && checkSelfPermission(Manifest.permission.CALL_PHONE) !=
                    PackageManager.PERMISSION_GRANTED) {

                setWaitingForResult(PendingRequestArgs
                        .forIntent(REQUEST_PERMISSION_CALL_PHONE, intent, info));
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE},
                        REQUEST_PERMISSION_CALL_PHONE);
            } else {
                // No idea why this was thrown.
                throw e;
            }
        }
    }

    public Bundle getActivityLaunchOptions(View v) {
        if (Utilities.ATLEAST_MARSHMALLOW) {
            int left = 0, top = 0;
            int width = v.getMeasuredWidth(), height = v.getMeasuredHeight();
            if (v instanceof TextView) {
                // Launch from center of icon, not entire view
                Drawable icon = Workspace.getTextViewIcon((TextView) v);
                if (icon != null) {
                    Rect bounds = icon.getBounds();
                    left = (width - bounds.width()) / 2;
                    top = v.getPaddingTop();
                    width = bounds.width();
                    height = bounds.height();
                }
            }
            return ActivityOptions.makeClipRevealAnimation(v, left, top, width, height).toBundle();
        } else if (Utilities.ATLEAST_LOLLIPOP_MR1) {
            // On L devices, we use the device default slide-up transition.
            // On L MR1 devices, we use a custom version of the slide-up transition which
            // doesn't have the delay present in the device default.
            return ActivityOptions.makeCustomAnimation(
                    this, R.anim.task_open_enter, R.anim.no_anim).toBundle();
        }
        return null;
    }

    public Rect getViewBounds(View v) {
        int[] pos = new int[2];
        v.getLocationOnScreen(pos);
        return new Rect(pos[0], pos[1], pos[0] + v.getWidth(), pos[1] + v.getHeight());
    }

    public boolean startActivitySafely(View v, Intent intent, ItemInfo item) {
        if (mIsSafeModeEnabled && !Utilities.isSystemApp(this, intent)) {
            Toast.makeText(this, R.string.safemode_shortcut_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        // Only launch using the new animation if the shortcut has not opted out (this is a
        // private contract between launcher and may be ignored in the future).
        boolean useLaunchAnimation = (v != null) &&
                !intent.hasExtra(INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION);
        Bundle optsBundle = useLaunchAnimation ? getActivityLaunchOptions(v) : null;

        UserHandle user = null;
        if (intent.hasExtra(AppInfo.EXTRA_PROFILE)) {
            long serialNumber = intent.getLongExtra(AppInfo.EXTRA_PROFILE, -1);
            user = UserManagerCompat.getInstance(this).getUserForSerialNumber(serialNumber);
        }

        // Prepare intent
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (v != null) {
            intent.setSourceBounds(getViewBounds(v));
        }
        try {
            if (Utilities.ATLEAST_MARSHMALLOW && item != null
                    && (item.itemType == Favorites.ITEM_TYPE_SHORTCUT
                    || item.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                    && ((ShortcutInfo) item).promisedIntent == null) {
                // Shortcuts need some special checks due to legacy reasons.
                startShortcutIntentSafely(intent, optsBundle, item);
            } else if (user == null || user.equals(Utilities.myUserHandle())) {
                // Could be launching some bookkeeping activity
                startActivity(intent, optsBundle);
            } else {
                LauncherAppsCompat.getInstance(this).startActivityForProfile(
                        intent.getComponent(), user, intent.getSourceBounds(), optsBundle);
            }
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + item + " intent=" + intent, e);
        }
        return false;
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
        FolderInfo info = (FolderInfo) fi.getTag();
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            CellLayout cl = (CellLayout) fi.getParent().getParent();
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) fi.getLayoutParams();
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY);
        }

        // Push an ImageView copy of the FolderIcon into the DragLayer and hide the original
        copyFolderIconToImage(fi);
        fi.setVisibility(View.INVISIBLE);

        ObjectAnimator oa = LauncherAnimUtils.ofViewAlphaAndScale(
                mFolderIconImageView, 0, 1.5f, 1.5f);
        oa.setInterpolator(new LogDecelerateInterpolator(100, 0));
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.start();
    }

    private void shrinkAndFadeInFolderIcon(final FolderIcon fi, boolean animate) {
        if (fi == null) return;
        final CellLayout cl = (CellLayout) fi.getParent().getParent();

        // We remove and re-draw the FolderIcon in-case it has changed
        mDragLayer.removeView(mFolderIconImageView);
        copyFolderIconToImage(fi);

        if (cl != null) {
            cl.clearFolderLeaveBehind();
        }

        ObjectAnimator oa = LauncherAnimUtils.ofViewAlphaAndScale(mFolderIconImageView, 1, 1, 1);
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cl != null) {
                    // Remove the ImageView copy of the FolderIcon and make the original visible.
                    mDragLayer.removeView(mFolderIconImageView);
                    fi.setVisibility(View.VISIBLE);
                }
            }
        });
        oa.start();
        if (!animate) {
            oa.end();
        }
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderIcon The FolderIcon describing the folder to open.
     */
    public void openFolder(FolderIcon folderIcon) {
        Folder folder = folderIcon.getFolder();
        Folder openFolder = mWorkspace != null ? mWorkspace.getOpenFolder() : null;
        if (openFolder != null && openFolder != folder) {
            // Close any open folder before opening a folder.
            closeFolder();
        }

        FolderInfo info = folder.mInfo;

        info.opened = true;

        // While the folder is open, the position of the icon cannot change.
        ((CellLayout.LayoutParams) folderIcon.getLayoutParams()).canReorder = false;

        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (folder.getParent() == null) {
            mDragLayer.addView(folder);
            mDragController.addDropTarget(folder);
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
        closeFolder(true);
    }

    public void closeFolder(boolean animate) {
        Folder folder = mWorkspace != null ? mWorkspace.getOpenFolder() : null;
        if (folder != null) {
            if (folder.isEditingName()) {
                folder.dismissEditingName();
            }
            closeFolder(folder, animate);
        }
    }

    public void closeFolder(Folder folder, boolean animate) {
        animate &= !Utilities.isPowerSaverOn(this);

        folder.getInfo().opened = false;

        ViewGroup parent = (ViewGroup) folder.getParent().getParent();
        if (parent != null) {
            FolderIcon fi = (FolderIcon) mWorkspace.getViewForTag(folder.mInfo);
            shrinkAndFadeInFolderIcon(fi, animate);
            if (fi != null) {
                ((CellLayout.LayoutParams) fi.getLayoutParams()).canReorder = true;
            }
        }
        if (animate) {
            folder.animateClosed();
        } else {
            folder.close(false);
        }

        // Notify the accessibility manager that this folder "window" has disappeared and no
        // longer occludes the workspace items
        getDragLayer().sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    public void closeFloatingContainer() {
        closeFloatingContainer(true);
    }

    public void closeFloatingContainer(boolean animate) {
        AbstractFloatingView topOpenView = AbstractFloatingView.getTopOpenView(this);
        if (topOpenView != null)
            topOpenView.close(animate);
    }

    public View getTopFloatingView() {
        View topView = getOpenShortcutsContainer();
        if (topView == null) {
            topView = getWorkspace().getOpenFolder();
        }
        return topView;
    }

    /**
     * @return The open shortcuts container, or null if there is none
     */
    public ShortcutsItemView getOpenShortcutsContainer() {
        // Iterate in reverse order. Shortcuts container is added later to the dragLayer,
        // and will be one of the last views.
        for (int i = mDragLayer.getChildCount() - 1; i >= 0; i--) {
            View child = mDragLayer.getChildAt(i);
            if (child instanceof ShortcutsItemView
                    && ((ShortcutsItemView) child).isOpenOrOpening()) {
                return (ShortcutsItemView) child;
            }
        }
        return null;
    }

    @Override
    public boolean onLongClick(View view) {
        CellLayout.CellInfo cellInfo = null;
        if (!isDraggingEnabled() || isWorkspaceLocked() || this.mState != State.WORKSPACE) {
            return false;
        }
        if (view != mAllAppsHandle) {
            if (!(view instanceof Workspace)) {
                View view2;
                if (view.getTag() instanceof ItemInfo) {
                    CellLayout.CellInfo cellInfo2 = new CellLayout.CellInfo(view, (ItemInfo) view.getTag());
                    view2 = cellInfo2.cell;
                    this.mPendingRequestArgs = null;
                    cellInfo = cellInfo2;
                } else {
                    view2 = null;
                }
                if (!this.mDragController.isDragging()) {
                    if (view2 == null) {
                        if (this.mWorkspace.isInOverviewMode()) {
                            this.mWorkspace.startReordering(view);
                        } else {
                            showOverviewMode(true);
                        }
                        this.mWorkspace.performHapticFeedback(0, 1);
                    } else {
                        boolean z = view2 instanceof Folder;
                        if (!z) {
                            this.mWorkspace.startDrag(cellInfo, new DragOptions());
                        }
                    }
                }
                return true;
            } else if (this.mWorkspace.isInOverviewMode() || this.mWorkspace.isTouchActive()) {
                return false;
            } else {
                showOverviewMode(true);
                this.mWorkspace.performHapticFeedback(0, 1);
                return true;
            }
        }
        onLongClickAllAppsHandle();
        return true;

    }

    boolean isHotseatLayout(View layout) {
        return mHotseat != null && layout != null &&
                (layout instanceof CellLayout) && (layout == mHotseat.getLayout());
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     */
    public CellLayout getCellLayout(long container, long screenId) {
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            if (mHotseat != null) {
                return mHotseat.getLayout();
            } else {
                return null;
            }
        } else {
            return mWorkspace.getScreenWithId(screenId);
        }
    }

    /**
     * For overridden classes.
     */
    public boolean isAllAppsVisible() {
        return isAppsViewVisible();
    }

    public boolean isAppsViewVisible() {
        return (mState == State.APPS) || (mOnResumeState == State.APPS);
    }

    public boolean isWidgetsViewVisible() {
        return (mState == State.WIDGETS) || (mOnResumeState == State.WIDGETS);
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

    public boolean showWorkspace(boolean animated) {
        return showWorkspace(animated, null);
    }

    public boolean showWorkspace(boolean animated, Runnable onCompleteRunnable) {
        boolean changed = mState != State.WORKSPACE ||
                mWorkspace.getState() != Workspace.State.NORMAL;
        if (changed || mAllAppsController.isTransitioning()) {
            mWorkspace.setVisibility(View.VISIBLE);
            mStateTransitionAnimation.startAnimationToWorkspace(mState, mWorkspace.getState(),
                    Workspace.State.NORMAL, animated, onCompleteRunnable);

            // Set focus to the AppsCustomize button
            if (mAllAppsHandle != null) {
                mAllAppsHandle.requestFocus();
            }
        }

        // Change the state *after* we've called all the transition code
        setState(State.WORKSPACE);

        // Resume the auto-advance of widgets
        mUserPresent = true;
        updateAutoAdvanceState();

        if (changed) {
            // Send an accessibility event to announce the context change
            getWindow().getDecorView()
                    .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
        mWorkspace.updateQsbVisibility();
        return changed;
    }

    /**
     * Shows the overview button.
     */
    public void showOverviewMode(boolean animated) {
        showOverviewMode(animated, false);
    }

    /**
     * Shows the overview button, and if {@param requestButtonFocus} is set, will force the focus
     * onto one of the overview panel buttons.
     */
    void showOverviewMode(boolean animated, boolean requestButtonFocus) {
        Runnable postAnimRunnable = null;
        if (requestButtonFocus) {
            postAnimRunnable = new Runnable() {
                @Override
                public void run() {
                    // Hitting the menu button when in touch mode does not trigger touch mode to
                    // be disabled, so if requested, force focus on one of the overview panel
                    // buttons.
                    mOverviewPanel.requestFocusFromTouch();
                }
            };
        }
        mWorkspace.setVisibility(View.VISIBLE);
        mStateTransitionAnimation.startAnimationToWorkspace(mState, mWorkspace.getState(),
                Workspace.State.OVERVIEW, animated, postAnimRunnable);
        setState(State.WORKSPACE);
        // If animated from long press, then don't allow any of the controller in the drag
        // layer to intercept any remaining touch.
        mWorkspace.requestDisallowInterceptTouchEvent(animated);
    }

    private void setState(State state) {
        mState = state;
        updateSoftInputMode();
    }

    private void updateSoftInputMode() {
        final int mode;
        if (isAppsViewVisible()) {
            mode = SOFT_INPUT_MODE_ALL_APPS;
        } else {
            mode = SOFT_INPUT_MODE_DEFAULT;
        }
        getWindow().setSoftInputMode(mode);
    }

    /**
     * Shows the apps view.
     */
    public void showAppsView(boolean animated, boolean focusSearchBar) {
        markAppsViewShown();
        showAppsOrWidgets(State.APPS, animated, focusSearchBar);
    }

    /**
     * Shows the widgets view.
     */
    void showWidgetsView(boolean animated, boolean resetPageToZero) {
        if (resetPageToZero) {
            mWidgetsView.scrollToTop();
        }
        showAppsOrWidgets(State.WIDGETS, animated, false);

        mWidgetsView.post(new Runnable() {
            @Override
            public void run() {
                mWidgetsView.requestFocus();
            }
        });
    }

    /**
     * Sets up the transition to show the apps/widgets view.
     *
     * @return whether the current from and to state allowed this operation
     */
    // TODO: calling method should use the return value so that when {@code false} is returned
    // the workspace transition doesn't fall into invalid state.
    private boolean showAppsOrWidgets(State toState, boolean animated, boolean focusSearchBar) {
        if (!(mState == State.WORKSPACE ||
                mState == State.APPS_SPRING_LOADED ||
                mState == State.WIDGETS_SPRING_LOADED ||
                (mState == State.APPS && mAllAppsController.isTransitioning()))) {
            return false;
        }
        if (toState != State.APPS && toState != State.WIDGETS) {
            return false;
        }

        // This is a safe and supported transition to bypass spring_loaded mode.
        if (mExitSpringLoadedModeRunnable != null) {
            mHandler.removeCallbacks(mExitSpringLoadedModeRunnable);
            mExitSpringLoadedModeRunnable = null;
        }

        if (toState == State.APPS) {
            mStateTransitionAnimation.startAnimationToAllApps(animated,
                    focusSearchBar);
        } else {
            mStateTransitionAnimation.startAnimationToWidgets(animated);
        }

        // Change the state *after* we've called all the transition code
        setState(toState);

        // Pause the auto-advance of widgets until we are out of AllApps
        mUserPresent = false;
        updateAutoAdvanceState();
        closeFolder();
        closeFloatingContainer();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        return true;
    }

    /**
     * Updates the workspace and interaction state on state change, and return the animation to this
     * new state.
     */
    public Animator startWorkspaceStateChangeAnimation(Workspace.State toState,
                                                       boolean animated, HashMap<View, Integer> layerViews) {
        return mWorkspace.setStateWithAnimation(toState, animated, layerViews);
    }

    public void enterSpringLoadedDragMode() {
        if (isStateSpringLoaded()) {
            return;
        }

        mStateTransitionAnimation.startAnimationToWorkspace(mState, mWorkspace.getState(),
                Workspace.State.SPRING_LOADED, true /* animated */,
                null /* onCompleteRunnable */);

        if (isAppsViewVisible()) {
            setState(State.APPS_SPRING_LOADED);
        } else if (isWidgetsViewVisible()) {
            setState(State.WIDGETS_SPRING_LOADED);
        } else {
            setState(State.WORKSPACE_SPRING_LOADED);
        }
    }

    public void exitSpringLoadedDragModeDelayed(final boolean successfulDrop, int delay,
                                                final Runnable onCompleteRunnable) {
        if (!isStateSpringLoaded()) return;

        if (mExitSpringLoadedModeRunnable != null) {
            mHandler.removeCallbacks(mExitSpringLoadedModeRunnable);
        }
        mExitSpringLoadedModeRunnable = new Runnable() {
            @Override
            public void run() {
                if (successfulDrop) {
                    // TODO(hyunyoungs): verify if this hack is still needed, if not, delete.
                    //
                    // Before we show workspace, hide all apps again because
                    // exitSpringLoadedDragMode made it visible. This is a bit hacky; we should
                    // clean up our state transition functions
                    mWidgetsView.setVisibility(View.GONE);
                    showWorkspace(true, onCompleteRunnable);
                } else {
                    exitSpringLoadedDragMode();
                }
                mExitSpringLoadedModeRunnable = null;
            }
        };
        mHandler.postDelayed(mExitSpringLoadedModeRunnable, delay);
    }

    boolean isStateSpringLoaded() {
        return mState == State.WORKSPACE_SPRING_LOADED || mState == State.APPS_SPRING_LOADED
                || mState == State.WIDGETS_SPRING_LOADED;
    }

    void exitSpringLoadedDragMode() {
        if (mState == State.APPS_SPRING_LOADED) {
            showAppsView(true /* animated */, false /* focusSearchBar */);
        } else if (mState == State.WIDGETS_SPRING_LOADED) {
            showWidgetsView(true, false);
        } else if (mState == State.WORKSPACE_SPRING_LOADED) {
            showWorkspace(true);
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        // Populate event with a fake title based on the current state.
        if (mState == State.APPS) {
            text.add(getString(R.string.all_apps_button_label));
        } else if (mState == State.WIDGETS) {
            text.add(getString(R.string.widget_button_text));
        } else if (mWorkspace != null) {
            text.add(mWorkspace.getCurrentPageDescription());
        } else {
            text.add(getString(R.string.all_apps_home_button_label));
        }
        return result;
    }

    /**
     * If the activity is currently paused, signal that we need to run the passed Runnable
     * in onResume.
     * <p>
     * This needs to be called from incoming places where resources might have been loaded
     * while the activity is paused. That is because the Configuration (e.g., rotation)  might be
     * wrong when we're not running, and if the activity comes back to what the configuration was
     * when we were paused, activity is not restarted.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return {@code true} if we are currently paused. The caller might be able to skip some work
     */
    @Thunk
    boolean waitUntilResume(Runnable run, boolean deletePreviousRunnables) {
        if (mPaused) {
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
     * <p>
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    @Override
    public boolean setLoadOnResume() {
        if (mPaused) {
            mOnResumeNeedsLoad = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public int getCurrentWorkspaceScreen() {
        if (mWorkspace != null) {
            return mWorkspace.getCurrentPage();
        } else {
            return 0;
        }
    }

    /**
     * Clear any pending bind callbacks. This is called when is loader is planning to
     * perform a full rebind from scratch.
     */
    @Override
    public void clearPendingBinds() {
        mBindOnResumeCallbacks.clear();
        if (mPendingExecutor != null) {
            mPendingExecutor.markCompleted();
            mPendingExecutor = null;
        }
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void startBinding() {
        setWorkspaceLoading(true);

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
        // Make sure the first screen is always at the start.
        if (Utilities.getPrefs(this).getShowPixelBar() && orderedScreenIds.indexOf(Workspace.FIRST_SCREEN_ID) != 0) {
            orderedScreenIds.remove(Workspace.FIRST_SCREEN_ID);
            orderedScreenIds.add(0, Workspace.FIRST_SCREEN_ID);
            mModel.updateWorkspaceScreenOrder(this, orderedScreenIds);
        }
        bindAddScreens(orderedScreenIds);

        // After we have added all the screens, if the wallpaper was locked to the default state,
        // then notify to indicate that it can be released and a proper wallpaper offset can be
        // computed before the next layout
        mWorkspace.unlockWallpaperFromDefaultPageOnNextLayout();
    }

    private void bindAddScreens(ArrayList<Long> orderedScreenIds) {
        int count = orderedScreenIds.size();
        for (int i = 0; i < count; i++) {
            long screenId = orderedScreenIds.get(i);
            if (screenId != Workspace.FIRST_SCREEN_ID) {
                // No need to bind the first screen, as its always bound.
                mWorkspace.insertNewWorkspaceScreenBeforeEmptyScreen(screenId);
            }
        }
    }

    @Override
    public void bindAppsAdded(final ArrayList<Long> newScreens,
                              final ArrayList<ItemInfo> addNotAnimated,
                              final ArrayList<ItemInfo> addAnimated,
                              final ArrayList<AppInfo> addedApps) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bindAppsAdded(newScreens, addNotAnimated, addAnimated, addedApps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Add the new screens
        if (newScreens != null) {
            bindAddScreens(newScreens);
        }

        // We add the items without animation on non-visible pages, and with
        // animations on the new page (which we will try and snap to).
        if (addNotAnimated != null && !addNotAnimated.isEmpty()) {
            bindItems(addNotAnimated, 0,
                    addNotAnimated.size(), false);
        }
        if (addAnimated != null && !addAnimated.isEmpty()) {
            bindItems(addAnimated, 0,
                    addAnimated.size(), true);
        }

        // Remove the extra empty screen
        mWorkspace.removeExtraEmptyScreen(false, false);

        if (addedApps != null && mAppsView != null) {
            mAppsView.addApps(addedApps);
        }
    }

    /**
     * Bind the items start-end from the list.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindItems(final ArrayList<ItemInfo> shortcuts, final int start, final int end,
                          final boolean forceAnimateIcons) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bindItems(shortcuts, start, end, forceAnimateIcons);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Get the list of added shortcuts and intersect them with the set of shortcuts here
        final AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();
        final Collection<Animator> bounceAnims = new ArrayList<>();
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

            final View view;
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                    ShortcutInfo info = (ShortcutInfo) item;
                    view = createShortcut(info);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                    view = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item);
                    break;
                default:
                    throw new RuntimeException("Invalid Item Type");
            }

             /*
             * Remove colliding items.
             */
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                CellLayout cl = mWorkspace.getScreenWithId(item.screenId);
                if (cl != null && cl.isOccupied(item.cellX, item.cellY)) {
                    View v = cl.getChildAt(item.cellX, item.cellY);
                    Object tag = v.getTag();
                    String desc = "Collision while binding workspace item: " + item
                            + ". Collides with " + tag;
                    Log.d(TAG, desc);
                    LauncherModel.deleteItemFromDatabase(this, item);
                }
            }
            workspace.addInScreenFromBind(view, item.container, item.screenId, item.cellX,
                    item.cellY, 1, 1);
            if (animateIcons) {
                // Animate all the applications up now
                view.setAlpha(0f);
                view.setScaleX(0f);
                view.setScaleY(0f);
                bounceAnims.add(createNewAppBounceAnimation(view, i));
                newShortcutsScreenId = item.screenId;
            }
        }

        if (animateIcons) {
            // Animate to the correct page
            if (newShortcutsScreenId > -1) {
                long currentScreenId = mWorkspace.getScreenIdForPageIndex(mWorkspace.getNextPage());
                final int newScreenIndex = mWorkspace.getPageIndexForScreenId(newShortcutsScreenId);
                final Runnable startBounceAnimRunnable = new Runnable() {
                    @Override
                    public void run() {
                        anim.playTogether(bounceAnims);
                        anim.start();
                    }
                };
                if (newShortcutsScreenId != currentScreenId) {
                    // We post the animation slightly delayed to prevent slowdowns
                    // when we are loading right after we return to launcher.
                    mWorkspace.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mWorkspace != null) {
                                mWorkspace.snapToPage(newScreenIndex);
                                mWorkspace.postDelayed(startBounceAnimRunnable,
                                        NEW_APPS_ANIMATION_DELAY);
                            }
                        }
                    }, NEW_APPS_PAGE_MOVE_DELAY);
                } else {
                    mWorkspace.postDelayed(startBounceAnimRunnable, NEW_APPS_ANIMATION_DELAY);
                }
            }
        }
        workspace.requestLayout();
    }

    private void bindSafeModeWidget(LauncherAppWidgetInfo item) {
        PendingAppWidgetHostView view = new PendingAppWidgetHostView(this, item, true);
        view.updateIcon(mIconCache);
        view.updateAppWidget(null);
        view.setOnClickListener(this);
        addAppWidgetToWorkspace(view, item, null, false);
        mWorkspace.requestLayout();
    }

    /**
     * Add the views for a widget to the workspace.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindAppWidget(final LauncherAppWidgetInfo item) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bindAppWidget(item);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (mIsSafeModeEnabled) {
            bindSafeModeWidget(item);
            return;
        }

        final LauncherAppWidgetProviderInfo appWidgetInfo;

        if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
            // If the provider is not ready, bind as a pending widget.
            appWidgetInfo = null;
        } else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
            // The widget id is not valid. Try to find the widget based on the provider info.
            appWidgetInfo = mAppWidgetManager.findProvider(item.providerName, item.user);
        } else {
            appWidgetInfo = mAppWidgetManager.getLauncherAppWidgetInfo(item.appWidgetId);
        }

        // If the provider is ready, but the width is not yet restored, try to restore it.
        if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) &&
                (item.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED)) {
            if (appWidgetInfo == null) {
                LauncherModel.deleteItemFromDatabase(this, item);
                return;
            }

            // If we do not have a valid id, try to bind an id.
            if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) {
                    // Id has not been allocated yet. Allocate a new id.
                    item.appWidgetId = mAppWidgetHost.allocateAppWidgetId();
                    item.restoreStatus |= LauncherAppWidgetInfo.FLAG_ID_ALLOCATED;

                    // Also try to bind the widget. If the bind fails, the user will be shown
                    // a click to setup UI, which will ask for the bind permission.
                    PendingAddWidgetInfo pendingInfo = new PendingAddWidgetInfo(this, appWidgetInfo);
                    pendingInfo.spanX = item.spanX;
                    pendingInfo.spanY = item.spanY;
                    pendingInfo.minSpanX = item.minSpanX;
                    pendingInfo.minSpanY = item.minSpanY;
                    Bundle options = WidgetHostViewLoader.getDefaultOptionsForWidget(this, pendingInfo);

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
                        // If the widget has a configure activity, it is still needs to set it up,
                        // otherwise the widget is ready to go.
                        item.restoreStatus = (appWidgetInfo.configure == null) || isDirectConfig
                                ? LauncherAppWidgetInfo.RESTORE_COMPLETED
                                : LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                    }

                    LauncherModel.updateItemInDatabase(this, item);
                }
            } else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY)
                    && (appWidgetInfo.configure == null)) {
                // The widget was marked as UI not ready, but there is no configure activity to
                // update the UI.
                item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;
                LauncherModel.updateItemInDatabase(this, item);
            }
        }

        if (item.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {

            // Verify that we own the widget
            if (appWidgetInfo == null) {
                deleteWidgetInfo(item);
                return;
            }

            item.minSpanX = appWidgetInfo.minSpanX;
            item.minSpanY = appWidgetInfo.minSpanY;
            addAppWidgetToWorkspace(
                    mAppWidgetHost.createView(this, item.appWidgetId, appWidgetInfo),
                    item, appWidgetInfo, false);
        } else {
            PendingAppWidgetHostView view = new PendingAppWidgetHostView(this, item, false);
            view.updateIcon(mIconCache);
            view.updateAppWidget(null);
            view.setOnClickListener(this);
            addAppWidgetToWorkspace(view, item, null, false);
        }
        mWorkspace.requestLayout();
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

        LauncherModel.updateItemInDatabase(this, info);
        return info;
    }

    @Override
    public void onPageBoundSynchronously(int page) {
        mSynchronouslyBoundPages.add(page);
    }

    @Override
    public void executeOnNextDraw(ViewOnDrawExecutor executor) {
        if (mPendingExecutor != null) {
            mPendingExecutor.markCompleted();
        }
        mPendingExecutor = executor;
        executor.attachTo(this);
    }

    public void clearPendingExecutor(ViewOnDrawExecutor executor) {
        if (mPendingExecutor == executor) {
            mPendingExecutor = null;
        }
    }

    @Override
    public void finishFirstPageBind(final ViewOnDrawExecutor executor) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                finishFirstPageBind(executor);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        Runnable onComplete = new Runnable() {
            @Override
            public void run() {
                if (executor != null) {
                    executor.onLoadAnimationCompleted();
                }
            }
        };
        if (mDragLayer.getAlpha() < 1) {
            mDragLayer.animate().alpha(1).withEndAction(onComplete).start();
        } else {
            onComplete.run();
        }
    }

    /**
     * Callback saying that there aren't any more items to bind.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void finishBindingItems() {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                finishBindingItems();
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                View v = mWorkspace.getChildAt(mWorkspace.getCurrentPage());
                if (v != null) v.requestFocus();
            }

            mSavedState = null;
        }

        mWorkspace.restoreInstanceStateForRemainingPages();

        setWorkspaceLoading(false);

        if (mPendingActivityResult != null) {
            handleActivityResult(mPendingActivityResult.requestCode,
                    mPendingActivityResult.resultCode, mPendingActivityResult.data);
            mPendingActivityResult = null;
        }


        NotificationListener.setNotificationsChangedListener(mPopupDataProvider);
        InstallShortcutReceiver.disableAndFlushInstallQueue(this);
    }

    private boolean canRunNewAppsAnimation() {
        long diff = System.currentTimeMillis() - mDragController.getLastGestureUpTime();
        return diff > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
    }

    private ValueAnimator createNewAppBounceAnimation(View v, int i) {
        ValueAnimator bounceAnim = LauncherAnimUtils.ofViewAlphaAndScale(v, 1, 1, 1);
        bounceAnim.setDuration(InstallShortcutReceiver.NEW_SHORTCUT_BOUNCE_DURATION);
        bounceAnim.setStartDelay(i * InstallShortcutReceiver.NEW_SHORTCUT_STAGGER_DELAY);
        bounceAnim.setInterpolator(new OvershootInterpolator(BOUNCE_ANIMATION_TENSION));
        return bounceAnim;
    }

    public boolean useVerticalBarLayout() {
        return mDeviceProfile.isVerticalBarLayout();
    }

/*    public int getSearchBarHeight() {
        if (mLauncherCallbacks != null) {
            return mLauncherCallbacks.getSearchBarHeight();
        }
        return LauncherCallbacks.SEARCH_BAR_HEIGHT_NORMAL;
    }*/

    /**
     * A runnable that we can dequeue and re-enqueue when all applications are bound (to prevent
     * multiple calls to bind the same list.)
     */
    @Thunk
    ArrayList<AppInfo> mTmpAppsList;
    private Runnable mBindAllApplicationsRunnable = new Runnable() {
        @Override
        public void run() {
            bindAllApplications(mTmpAppsList);
            mTmpAppsList = null;
        }
    };

    /**
     * Add the icons for all apps.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindAllApplications(final ArrayList<AppInfo> apps) {
        if (waitUntilResume(mBindAllApplicationsRunnable, true)) {
            mTmpAppsList = apps;
            return;
        }

        if (mAppsView != null) {
            mAppsView.setApps(apps);
        }
    }

    @Override
    public void bindAllWidgets(MultiHashMap multiHashMap) {
        if (waitUntilResume(mBindAllWidgetsRunnable, true)) {
            mAllWidgets = multiHashMap;
            return;
        }
        if (!(mWidgetsView == null || multiHashMap == null)) {
            mWidgetsView.setWidgets(multiHashMap);
            mAllWidgets = null;
        }
        AbstractFloatingView topOpenView = AbstractFloatingView.getTopOpenView(this);
        if (topOpenView != null) {
            topOpenView.onWidgetsBound();
        }
    }

    /**
     * Copies LauncherModel's map of activities to shortcut ids to Launcher's. This is necessary
     * because LauncherModel's map is updated in the background, while Launcher runs on the UI.
     */
    @Override
    public void bindDeepShortcutMap(MultiHashMap<ComponentKey, String> deepShortcutMapCopy) {
        mPopupDataProvider.setDeepShortcutMap(deepShortcutMapCopy);
    }

    /**
     * A package was updated.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindAppsUpdated(final ArrayList<AppInfo> apps) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bindAppsUpdated(apps);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        if (mAppsView != null) {
            mAppsView.updateApps(apps);
        }
    }

    @Override
    public void bindWidgetsRestored(final ArrayList<LauncherAppWidgetInfo> widgets) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bindWidgetsRestored(widgets);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        mWorkspace.widgetsRestored(widgets);
    }

    /**
     * Some shortcuts were updated in the background.
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @param updated list of shortcuts which have changed.
     * @param removed list of shortcuts which were deleted in the background. This can happen when
     *                an app gets removed from the system or some of its components are no longer
     *                available.
     */
    @Override
    public void bindShortcutsChanged(final ArrayList<ShortcutInfo> updated,
                                     final ArrayList<ShortcutInfo> removed, final UserHandle user) {
        if (!waitUntilResume(new Runnable() {
            @Override
            public void run() {
                Launcher.this.bindShortcutsChanged(updated, removed, user);
            }
        })) {
            if (!updated.isEmpty()) {
                this.mWorkspace.updateShortcuts(updated);
            }
            if (!removed.isEmpty()) {
                ItemInfoMatcher ofComponents;
                HashSet<ComponentName> hashSet = new HashSet<>();
                HashSet<ShortcutKey> hashSet2 = new HashSet<>();
                for (ShortcutInfo shortcutInfo : removed) {
                    if (shortcutInfo.itemType == 6) {
                        hashSet2.add(ShortcutKey.fromItemInfo(shortcutInfo));
                    } else {
                        hashSet.add(shortcutInfo.getTargetComponent());
                    }
                }
                if (!hashSet.isEmpty()) {
                    ofComponents = ItemInfoMatcher.ofComponents(hashSet, user);
                    this.mWorkspace.removeItemsByMatcher(ofComponents);
                    this.mDragController.onAppsRemoved(ofComponents);
                }
                if (!hashSet2.isEmpty()) {
                    ofComponents = ItemInfoMatcher.ofShortcutKeys(hashSet2);
                    this.mWorkspace.removeItemsByMatcher(ofComponents);
                    this.mDragController.onAppsRemoved(ofComponents);
                }
            }
        }
    }

    /**
     * Update the state of a package, typically related to install state.
     * <p>
     * Implementation of the method from LauncherModel.Callbacks.
     */
    @Override
    public void bindRestoreItemsChange(final HashSet<ItemInfo> updates) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bindRestoreItemsChange(updates);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        mWorkspace.updateRestoreItems(updates);
    }

    /**
     * A package was uninstalled/updated.  We take both the super set of packageNames
     * in addition to specific applications to remove, the reason being that
     * this can be called when a package is updated as well.  In that scenario,
     * we only remove specific components from the workspace and hotseat, where as
     * package-removal should clear all items by package name.
     */
    @Override
    public void bindWorkspaceComponentsRemoved(
            final HashSet<String> packageNames, final HashSet<ComponentName> components,
            final UserHandle user) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bindWorkspaceComponentsRemoved(packageNames, components, user);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }
        if (!packageNames.isEmpty()) {
            ItemInfoMatcher matcher = ItemInfoMatcher.ofPackages(packageNames, user);
            mWorkspace.removeItemsByMatcher(matcher);
            mDragController.onAppsRemoved(matcher);

        }
        if (!components.isEmpty()) {
            ItemInfoMatcher matcher = ItemInfoMatcher.ofComponents(components, user);
            mWorkspace.removeItemsByMatcher(matcher);
            mDragController.onAppsRemoved(matcher);
        }
    }

    @Override
    public void bindAppInfosRemoved(final ArrayList<AppInfo> appInfos) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                bindAppInfosRemoved(appInfos);
            }
        };
        if (waitUntilResume(r)) {
            return;
        }

        // Update AllApps
        if (mAppsView != null) {
            mAppsView.removeApps(appInfos);
        }
    }

    private Runnable mBindWidgetModelRunnable = new Runnable() {
        @Override
        public void run() {
            bindWidgetsModel(mWidgetsModel);
        }
    };

    @Override
    public void bindWidgetsModel(WidgetsModel model) {
        if (waitUntilResume(mBindWidgetModelRunnable, true)) {
            mWidgetsModel = model;
            return;
        }

        if (mWidgetsView != null && model != null) {
            mWidgetsView.setWidgets(model.getWidgetsMap());
            mWidgetsModel = null;
        }

        AbstractFloatingView topOpenView = AbstractFloatingView.getTopOpenView(this);
        if (topOpenView != null) {
            topOpenView.onWidgetsBound();
        }
    }

    public List getWidgetsForPackageUser(PackageUserKey packageUserKey) {
        return mWidgetsView.getWidgetsForPackageUser(packageUserKey);
    }

    @Override
    public void notifyWidgetProvidersChanged() {
        if (mWorkspace.getState().shouldUpdateWidget) {
            refreshAndBindWidgetsForPackageUser(null);
        }
    }

    public void refreshAndBindWidgetsForPackageUser(PackageUserKey packageUserKey) {
        this.mModel.refreshAndBindWidgetsAndShortcuts(this, this.mWidgetsView.isEmpty(), packageUserKey);
    }


    private void markAppsViewShown() {
        if (mSharedPrefs.getAppsViewShown()) {
            return;
        }
        mSharedPrefs.setAppsViewShown(true);
    }

    private boolean shouldShowDiscoveryBounce() {
        if (mState != State.WORKSPACE || !mIsResumeFromActionScreenOff) {
            return false;
        }
        return !mSharedPrefs.getAppsViewShown();
    }

    /**
     * Returns a FastBitmapDrawable with the icon, accurately sized.
     */
    public FastBitmapDrawable createIconDrawable(Bitmap icon) {
        FastBitmapDrawable d = new FastBitmapDrawable(icon);
        d.setFilterBitmap(true);
        resizeIconDrawable(d);
        return d;
    }

    /**
     * Resizes an icon drawable to the correct icon size.
     */
    public Drawable resizeIconDrawable(Drawable icon) {
        icon.setBounds(0, 0, mDeviceProfile.iconSizePx, mDeviceProfile.iconSizePx);
        return icon;
    }

    public void openDialog(LauncherDialog dialog) {
        dismissDialog();
        mCurrentDialog = dialog;
        mCurrentDialog.setOnDismissListener(this);
        mCurrentDialog.show();
    }

    private void dismissDialog() {
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        mCurrentDialog = null;
    }

    @NonNull
    public static Launcher getLauncher(Context context) {
        if (context instanceof Launcher) {
            return (Launcher) context;
        }

        return LauncherAppState.getInstance().getLauncher();
    }

    /**
     * Call this after onCreate to set or clear overlay.
     */
    public void setLauncherOverlay(LauncherOverlay overlay) {
        mWorkspace.setLauncherOverlay(overlay);
    }

    public void startEditIcon(EditableItemInfo info) {
        if (Utilities.isBlacklistedAppInstalled(this)) {
            Toast.makeText(this, R.string.unauthorized_device, Toast.LENGTH_SHORT).show();
            return;
        }

        mEditingItem = info;
        setWaitingForResult(new PendingRequestArgs((ItemInfo) mEditingItem));
        Intent intent = new Intent(this, EditIconActivity.class);
        intent.putExtra("itemInfo", info);
        startActivityForResult(intent, REQUEST_EDIT_ICON);
    }

    public ILauncherClient getClient() {
        return mLauncherTab.getClient();
    }

    public boolean isClientConnected() {
        return mLauncherTab.getClient().isConnected();
    }

    public BlurWallpaperProvider getBlurWallpaperProvider() {
        return mBlurWallpaperProvider;
    }

    public interface LauncherOverlay {

        /**
         * Touch interaction leading to overscroll has begun
         */
        void onScrollInteractionBegin();

        /**
         * Touch interaction related to overscroll has ended
         */
        void onScrollInteractionEnd();

        /**
         * Scroll progress, between 0 and 100, when the user scrolls beyond the leftmost
         * screen (or in the case of RTL, the rightmost screen).
         */
        void onScrollChange(float progress, boolean rtl);
    }

    private class BindAllWidgetsRunnable implements Runnable {
        @Override
        public void run() {
            Launcher.this.bindAllWidgets(Launcher.this.mAllWidgets);
        }
    }

    public static class LauncherDialog extends Dialog {

        public LauncherDialog(@NonNull Context context) {
            super(context);
        }

        public LauncherDialog(@NonNull Context context, int themeResId) {
            super(context, themeResId);
        }

        protected LauncherDialog(@NonNull Context context, boolean cancelable, @Nullable OnCancelListener cancelListener) {
            super(context, cancelable, cancelListener);
        }

        public void onResume() {

        }
    }
}
