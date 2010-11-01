
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

package com.android.launcher2;

import com.android.common.Search;
import com.android.launcher.R;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.LiveFolders;
import android.provider.Settings;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnLongClickListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Default launcher application.
 */
public final class Launcher extends Activity
        implements View.OnClickListener, OnLongClickListener, LauncherModel.Callbacks,
                   AllAppsView.Watcher, View.OnTouchListener {
    static final String TAG = "Launcher";
    static final boolean LOGD = false;

    static final boolean PROFILE_STARTUP = false;
    static final boolean DEBUG_WIDGETS = false;
    static final boolean DEBUG_USER_INTERFACE = false;

    private static final int MENU_GROUP_ADD = 1;
    private static final int MENU_GROUP_WALLPAPER = MENU_GROUP_ADD + 1;

    private static final int MENU_ADD = Menu.FIRST + 1;
    private static final int MENU_MANAGE_APPS = MENU_ADD + 1;
    private static final int MENU_WALLPAPER_SETTINGS = MENU_MANAGE_APPS + 1;
    private static final int MENU_SEARCH = MENU_WALLPAPER_SETTINGS + 1;
    private static final int MENU_NOTIFICATIONS = MENU_SEARCH + 1;
    private static final int MENU_SETTINGS = MENU_NOTIFICATIONS + 1;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_LIVE_FOLDER = 4;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPLICATION = 6;
    private static final int REQUEST_PICK_SHORTCUT = 7;
    private static final int REQUEST_PICK_LIVE_FOLDER = 8;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_PICK_WALLPAPER = 10;

    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

    static final int SCREEN_COUNT = 5;
    static final int DEFAULT_SCREEN = 2;

    static final int DIALOG_CREATE_SHORTCUT = 1;
    static final int DIALOG_RENAME_FOLDER = 2;

    private static final String PREFERENCES = "launcher.preferences";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: long
    private static final String RUNTIME_STATE_USER_FOLDERS = "launcher.user_folder";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cellX";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cellY";
    // Type: boolean
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
    // Type: long
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID = "launcher.rename_folder_id";

    // tags for the customization tabs
    private static final String WIDGETS_TAG = "widgets";
    private static final String APPLICATIONS_TAG = "applications";
    private static final String SHORTCUTS_TAG = "shortcuts";
    private static final String WALLPAPERS_TAG = "wallpapers";

    private static final String TOOLBAR_ICON_METADATA_NAME = "com.android.launcher.toolbar_icon";

    /** The different states that Launcher can be in. */
    private enum State { WORKSPACE, ALL_APPS, CUSTOMIZE, OVERVIEW };
    private State mState = State.WORKSPACE;
    private AnimatorSet mStateAnimation;

    static final int APPWIDGET_HOST_ID = 1024;

    private static final Object sLock = new Object();
    private static int sScreen = DEFAULT_SCREEN;

    private final BroadcastReceiver mCloseSystemDialogsReceiver
            = new CloseSystemDialogsIntentReceiver();
    private final ContentObserver mWidgetObserver = new AppWidgetResetObserver();

    private LayoutInflater mInflater;

    private DragController mDragController;
    private Workspace mWorkspace;

    private AppWidgetManager mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    private int mAddScreen = -1;
    private int mAddIntersectCellX = -1;
    private int mAddIntersectCellY = -1;
    private int[] mAddDropPosition;
    private int[] mTmpAddItemCellCoordinates = new int[2];

    private FolderInfo mFolderInfo;

    private DeleteZone mDeleteZone;
    private HandleView mHandleView;
    private AllAppsView mAllAppsGrid;
    private TabHost mHomeCustomizationDrawer;

    private PagedView mAllAppsPagedView = null;
    private CustomizePagedView mCustomizePagedView = null;

    private Bundle mSavedState;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mWorkspaceLoading = true;

    private boolean mPaused = true;
    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mOnResumeNeedsLoad;

    private Bundle mSavedInstanceState;

    private LauncherModel mModel;
    private IconCache mIconCache;

    private static LocaleConfiguration sLocaleConfiguration = null;

    private ArrayList<ItemInfo> mDesktopItems = new ArrayList<ItemInfo>();
    private static HashMap<Long, FolderInfo> sFolders = new HashMap<Long, FolderInfo>();

    private ImageView mPreviousView;
    private ImageView mNextView;

    // Hotseats (quick-launch icons next to AllApps)
    private String[] mHotseatConfig = null;
    private Intent[] mHotseats = null;
    private Drawable[] mHotseatIcons = null;
    private CharSequence[] mHotseatLabels = null;

    private Intent mAppMarketIntent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (LauncherApplication.isInPlaceRotationEnabled()) {
            // hide the status bar (temporary until we get the status bar design figured out)
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }

        LauncherApplication app = ((LauncherApplication)getApplication());
        mModel = app.setLauncher(this);
        mIconCache = app.getIconCache();
        mDragController = new DragController(this);
        mInflater = getLayoutInflater();

        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing(
                    Environment.getExternalStorageDirectory() + "/launcher");
        }

        loadHotseats();
        checkForLocaleChange();
        setWallpaperDimension();
        setContentView(R.layout.launcher);
        mHomeCustomizationDrawer = (TabHost) findViewById(com.android.internal.R.id.tabhost);
        if (mHomeCustomizationDrawer != null) {
            mHomeCustomizationDrawer.setup();

            // share the same customization workspace across all the tabs
            mCustomizePagedView = (CustomizePagedView) mInflater.inflate(
                    R.layout.customization_drawer, mHomeCustomizationDrawer, false);
            TabContentFactory contentFactory = new TabContentFactory() {
                public View createTabContent(String tag) {
                    return mCustomizePagedView;
                }
            };

            String widgetsLabel = getString(R.string.widgets_tab_label);
            mHomeCustomizationDrawer.addTab(mHomeCustomizationDrawer.newTabSpec(WIDGETS_TAG)
                    .setIndicator(widgetsLabel).setContent(contentFactory));
            String applicationsLabel = getString(R.string.applications_tab_label);
            mHomeCustomizationDrawer.addTab(mHomeCustomizationDrawer.newTabSpec(APPLICATIONS_TAG)
                    .setIndicator(applicationsLabel).setContent(contentFactory));
            String wallpapersLabel = getString(R.string.wallpapers_tab_label);
            mHomeCustomizationDrawer.addTab(mHomeCustomizationDrawer.newTabSpec(WALLPAPERS_TAG)
                    .setIndicator(wallpapersLabel).setContent(contentFactory));
            String shortcutsLabel = getString(R.string.shortcuts_tab_label);
            mHomeCustomizationDrawer.addTab(mHomeCustomizationDrawer.newTabSpec(SHORTCUTS_TAG)
                    .setIndicator(shortcutsLabel).setContent(contentFactory));

            mHomeCustomizationDrawer.setOnTabChangedListener(new OnTabChangeListener() {
                public void onTabChanged(String tabId) {
                    // animate the changing of the tab content by fading pages in and out
                    final int duration = 150;
                    final float alpha = mCustomizePagedView.getAlpha();
                    ValueAnimator alphaAnim = ObjectAnimator.ofFloat(mCustomizePagedView,
                            "alpha", alpha, 0.0f);
                    alphaAnim.setDuration(duration);
                    alphaAnim.addListener(new LauncherAnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEndOrCancel(Animator animation) {
                            String tag = mHomeCustomizationDrawer.getCurrentTabTag();
                            if (tag == WIDGETS_TAG) {
                                mCustomizePagedView.setCustomizationFilter(
                                    CustomizePagedView.CustomizationType.WidgetCustomization);
                            } else if (tag == APPLICATIONS_TAG) {
                                mCustomizePagedView.setCustomizationFilter(
                                        CustomizePagedView.CustomizationType.ApplicationCustomization);
                            } else if (tag == WALLPAPERS_TAG) {
                                mCustomizePagedView.setCustomizationFilter(
                                    CustomizePagedView.CustomizationType.WallpaperCustomization);
                            } else if (tag == SHORTCUTS_TAG) {
                                mCustomizePagedView.setCustomizationFilter(
                                        CustomizePagedView.CustomizationType.ShortcutCustomization);
                            }

                            final float alpha = mCustomizePagedView.getAlpha();
                            ValueAnimator alphaAnim = ObjectAnimator.ofFloat(
                                    mCustomizePagedView, "alpha", alpha, 1.0f);
                            alphaAnim.setDuration(duration);
                            alphaAnim.start();
                        }
                    });
                    alphaAnim.start();
                }
            });
        }
        setupViews();

        registerContentObservers();

        lockAllApps();

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }

        if (!mRestoring) {
            mModel.startLoader(this, true);
        }

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);

        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mCloseSystemDialogsReceiver, filter);
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
            loadHotseats();

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

    private void setWallpaperDimension() {
        WallpaperManager wpm = (WallpaperManager)getSystemService(WALLPAPER_SERVICE);

        Display display = getWindowManager().getDefaultDisplay();
        // TODO: Put back when we decide about scrolling the wallpaper
        // boolean isPortrait = display.getWidth() < display.getHeight();
        // final int width = isPortrait ? display.getWidth() : display.getHeight();
        // final int height = isPortrait ? display.getHeight() : display.getWidth();
        wpm.suggestDesiredDimensions(Math.max(display.getWidth(), display.getHeight()),
                Math.max(display.getWidth(), display.getHeight()));
    }

    // Note: This doesn't do all the client-id magic that BrowserProvider does
    // in Browser. (http://b/2425179)
    private Uri getDefaultBrowserUri() {
        String url = getString(R.string.default_browser_url);
        if (url.indexOf("{CID}") != -1) {
            url = url.replace("{CID}", "android-google");
        }
        return Uri.parse(url);
    }

    // Load the Intent templates from arrays.xml to populate the hotseats. For
    // each Intent, if it resolves to a single app, use that as the launch
    // intent & use that app's label as the contentDescription. Otherwise,
    // retain the ResolveActivity so the user can pick an app.
    private void loadHotseats() {
        if (mHotseatConfig == null) {
            mHotseatConfig = getResources().getStringArray(R.array.hotseats);
            if (mHotseatConfig.length > 0) {
                mHotseats = new Intent[mHotseatConfig.length];
                mHotseatLabels = new CharSequence[mHotseatConfig.length];
                mHotseatIcons = new Drawable[mHotseatConfig.length];
            } else {
                mHotseats = null;
                mHotseatIcons = null;
                mHotseatLabels = null;
            }

            TypedArray hotseatIconDrawables = getResources().obtainTypedArray(R.array.hotseat_icons);
            for (int i=0; i<mHotseatConfig.length; i++) {
                // load icon for this slot; currently unrelated to the actual activity
                try {
                    mHotseatIcons[i] = hotseatIconDrawables.getDrawable(i);
                } catch (ArrayIndexOutOfBoundsException ex) {
                    Log.w(TAG, "Missing hotseat_icons array item #" + i);
                    mHotseatIcons[i] = null;
                }
            }
            hotseatIconDrawables.recycle();
        }

        PackageManager pm = getPackageManager();
        for (int i=0; i<mHotseatConfig.length; i++) {
            Intent intent = null;
            if (mHotseatConfig[i].equals("*BROWSER*")) {
                // magic value meaning "launch user's default web browser"
                // replace it with a generic web request so we can see if there is indeed a default
                String defaultUri = getString(R.string.default_browser_url);
                intent = new Intent(
                        Intent.ACTION_VIEW,
                        ((defaultUri != null)
                            ? Uri.parse(defaultUri)
                            : getDefaultBrowserUri())
                    ).addCategory(Intent.CATEGORY_BROWSABLE);
                // note: if the user launches this without a default set, she
                // will always be taken to the default URL above; this is
                // unavoidable as we must specify a valid URL in order for the
                // chooser to appear, and once the user selects something, that
                // URL is unavoidably sent to the chosen app.
            } else {
                try {
                    intent = Intent.parseUri(mHotseatConfig[i], 0);
                } catch (java.net.URISyntaxException ex) {
                    Log.w(TAG, "Invalid hotseat intent: " + mHotseatConfig[i]);
                    // bogus; leave intent=null
                }
            }

            if (intent == null) {
                mHotseats[i] = null;
                mHotseatLabels[i] = getText(R.string.activity_not_found);
                continue;
            }

            if (LOGD) {
                Log.d(TAG, "loadHotseats: hotseat " + i
                    + " initial intent=["
                    + intent.toUri(Intent.URI_INTENT_SCHEME)
                    + "]");
            }

            ResolveInfo bestMatch = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            List<ResolveInfo> allMatches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (LOGD) {
                Log.d(TAG, "Best match for intent: " + bestMatch);
                Log.d(TAG, "All matches: ");
                for (ResolveInfo ri : allMatches) {
                    Log.d(TAG, "  --> " + ri);
                }
            }
            // did this resolve to a single app, or the resolver?
            if (allMatches.size() == 0 || bestMatch == null) {
                // can't find any activity to handle this. let's leave the
                // intent as-is and let Launcher show a toast when it fails
                // to launch.
                mHotseats[i] = intent;

                // set accessibility text to "Not installed"
                mHotseatLabels[i] = getText(R.string.activity_not_found);
            } else {
                boolean found = false;
                for (ResolveInfo ri : allMatches) {
                    if (bestMatch.activityInfo.name.equals(ri.activityInfo.name)
                        && bestMatch.activityInfo.applicationInfo.packageName
                            .equals(ri.activityInfo.applicationInfo.packageName)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    if (LOGD) Log.d(TAG, "Multiple options, no default yet");
                    // the bestMatch is probably the ResolveActivity, meaning the
                    // user has not yet selected a default
                    // so: we'll keep the original intent for now
                    mHotseats[i] = intent;

                    // set the accessibility text to "Select shortcut"
                    mHotseatLabels[i] = getText(R.string.title_select_shortcut);
                } else {
                    // we have an app!
                    // now reconstruct the intent to launch it through the front
                    // door
                    ComponentName com = new ComponentName(
                        bestMatch.activityInfo.applicationInfo.packageName,
                        bestMatch.activityInfo.name);
                    mHotseats[i] = new Intent(Intent.ACTION_MAIN).setComponent(com);

                    // load the app label for accessibility
                    mHotseatLabels[i] = bestMatch.activityInfo.loadLabel(pm);
                }
            }

            if (LOGD) {
                Log.d(TAG, "loadHotseats: hotseat " + i
                    + " final intent=["
                    + ((mHotseats[i] == null)
                        ? "null"
                        : mHotseats[i].toUri(Intent.URI_INTENT_SCHEME))
                    + "] label=[" + mHotseatLabels[i]
                    + "]"
                    );
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mWaitingForResult = false;

        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.

        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.

        if (resultCode == RESULT_OK && mAddScreen != -1) {
            switch (requestCode) {
                case REQUEST_PICK_APPLICATION:
                    completeAddApplication(
                            this, data, mAddScreen, mAddIntersectCellX, mAddIntersectCellY);
                    break;
                case REQUEST_PICK_SHORTCUT:
                    processShortcut(data);
                    break;
                case REQUEST_CREATE_SHORTCUT:
                    completeAddShortcut(data, mAddScreen, mAddIntersectCellX, mAddIntersectCellY);
                    break;
                case REQUEST_PICK_LIVE_FOLDER:
                    addLiveFolder(data);
                    break;
                case REQUEST_CREATE_LIVE_FOLDER:
                    completeAddLiveFolder(data, mAddScreen, mAddIntersectCellX, mAddIntersectCellY);
                    break;
                case REQUEST_PICK_APPWIDGET:
                    addAppWidgetFromPick(data);
                    break;
                case REQUEST_CREATE_APPWIDGET:
                    int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                    completeAddAppWidget(appWidgetId, mAddScreen);
                    break;
                case REQUEST_PICK_WALLPAPER:
                    // We just wanted the activity result here so we can clear mWaitingForResult
                    break;
            }
        } else if ((requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET) && resultCode == RESULT_CANCELED &&
                data != null) {
            // Clean up the appWidgetId if we canceled
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (appWidgetId != -1) {
                mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
        if (mRestoring || mOnResumeNeedsLoad) {
            mWorkspaceLoading = true;
            mModel.startLoader(this, true);
            mRestoring = false;
            mOnResumeNeedsLoad = false;
        }
        // When we resume Launcher, a different Activity might be responsible for the app
        // market intent, so refresh the icon
        updateAppMarketIcon();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Some launcher layouts don't have a previous and next view
        if (mPreviousView != null) {
            dismissPreview(mPreviousView);
        }
        if (mNextView != null) {
            dismissPreview(mNextView);
        }
        mPaused = true;
        mDragController.cancelDrag();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Flag the loader to stop early before switching
        mModel.stopLoader();
        mAllAppsGrid.surrender();
        return Boolean.TRUE;
    }

    // We can't hide the IME if it was forced open.  So don't bother
    /*
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            final InputMethodManager inputManager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            inputManager.hideSoftInputFromWindow(lp.token, 0, new android.os.ResultReceiver(new
                        android.os.Handler()) {
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            Log.d(TAG, "ResultReceiver got resultCode=" + resultCode);
                        }
                    });
            Log.d(TAG, "called hideSoftInputFromWindow from onWindowFocusChanged");
        }
    }
    */

    private boolean acceptFilter() {
        final InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        return !inputManager.isFullscreenMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = super.onKeyDown(keyCode, event);
        if (!handled && acceptFilter() && keyCode != KeyEvent.KEYCODE_ENTER) {
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

        if (state == State.ALL_APPS) {
            showAllApps(false);
        } else if (state == State.CUSTOMIZE) {
            showCustomizationDrawer(false);
        }

        final int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN, -1);
        if (currentScreen > -1) {
            mWorkspace.setCurrentPage(currentScreen);
        }

        final int addScreen = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);

        if (addScreen > -1) {
            mAddScreen = addScreen;
            mAddIntersectCellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            mAddIntersectCellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            mRestoring = true;
        }

        boolean renameFolder = savedState.getBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
        if (renameFolder) {
            long id = savedState.getLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID);
            mFolderInfo = mModel.getFolderById(this, sFolders, id);
            mRestoring = true;
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        final DragController dragController = mDragController;

        DragLayer dragLayer = (DragLayer) findViewById(R.id.drag_layer);
        dragLayer.setDragController(dragController);

        mAllAppsGrid = (AllAppsView)dragLayer.findViewById(R.id.all_apps_view);
        mAllAppsGrid.setLauncher(this);
        mAllAppsGrid.setDragController(dragController);
        ((View) mAllAppsGrid).setWillNotDraw(false); // We don't want a hole punched in our window.
        // Manage focusability manually since this thing is always visible (in non-xlarge)
        ((View) mAllAppsGrid).setFocusable(false);

        if (LauncherApplication.isScreenXLarge()) {
            // They need to be INVISIBLE initially so that they will be measured in the layout.
            // Otherwise the animations are messed up when we show them for the first time.
            ((View) mAllAppsGrid).setVisibility(View.INVISIBLE);
            mHomeCustomizationDrawer.setVisibility(View.INVISIBLE);
        }

        mWorkspace = (Workspace) dragLayer.findViewById(R.id.workspace);
        final Workspace workspace = mWorkspace;
        workspace.setHapticFeedbackEnabled(false);

        DeleteZone deleteZone = (DeleteZone) dragLayer.findViewById(R.id.delete_zone);
        mDeleteZone = deleteZone;

        View handleView = findViewById(R.id.all_apps_button);
        if (handleView != null && handleView instanceof HandleView) {
            // we don't use handle view in xlarge mode
            mHandleView = (HandleView)handleView;
            mHandleView.setLauncher(this);
            mHandleView.setOnClickListener(this);
            mHandleView.setOnLongClickListener(this);
        }

        if (mCustomizePagedView != null) {
            mCustomizePagedView.setLauncher(this);
            mCustomizePagedView.setDragController(dragController);
            mCustomizePagedView.update();
        } else {
             ImageView hotseatLeft = (ImageView) findViewById(R.id.hotseat_left);
             hotseatLeft.setContentDescription(mHotseatLabels[0]);
             hotseatLeft.setImageDrawable(mHotseatIcons[0]);
             ImageView hotseatRight = (ImageView) findViewById(R.id.hotseat_right);
             hotseatRight.setContentDescription(mHotseatLabels[1]);
             hotseatRight.setImageDrawable(mHotseatIcons[1]);

             mPreviousView = (ImageView) dragLayer.findViewById(R.id.previous_screen);
             mNextView = (ImageView) dragLayer.findViewById(R.id.next_screen);

             Drawable previous = mPreviousView.getDrawable();
             Drawable next = mNextView.getDrawable();
             mWorkspace.setIndicators(previous, next);

             mPreviousView.setHapticFeedbackEnabled(false);
             mPreviousView.setOnLongClickListener(this);
             mNextView.setHapticFeedbackEnabled(false);
             mNextView.setOnLongClickListener(this);
        }

        workspace.setOnLongClickListener(this);
        workspace.setDragController(dragController);
        workspace.setLauncher(this);

        deleteZone.setLauncher(this);
        deleteZone.setDragController(dragController);
        int deleteZoneHandleId;
        if (LauncherApplication.isScreenXLarge()) {
            deleteZoneHandleId = R.id.all_apps_button;
        } else {
            deleteZoneHandleId = R.id.all_apps_button_cluster;
        }
        deleteZone.setHandle(findViewById(deleteZoneHandleId));
        dragController.addDragListener(deleteZone);

        ApplicationInfoDropTarget infoButton = (ApplicationInfoDropTarget)findViewById(R.id.info_button);
        if (infoButton != null) {
            infoButton.setLauncher(this);
            infoButton.setHandle(findViewById(R.id.configure_button));
            infoButton.setDragColor(getResources().getColor(R.color.app_info_filter));
            dragController.addDragListener(infoButton);
        }

        dragController.setDragScoller(workspace);
        dragController.setScrollView(dragLayer);
        dragController.setMoveTarget(workspace);

        // The order here is bottom to top.
        dragController.addDropTarget(workspace);
        dragController.addDropTarget(deleteZone);
        if (infoButton != null) {
            dragController.addDropTarget(infoButton);
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void previousScreen(View v) {
        if (mState != State.ALL_APPS) {
            mWorkspace.scrollLeft();
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void nextScreen(View v) {
        if (mState != State.ALL_APPS) {
            mWorkspace.scrollRight();
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void launchHotSeat(View v) {
        if (mState == State.ALL_APPS) return;

        int index = -1;
        if (v.getId() == R.id.hotseat_left) {
            index = 0;
        } else if (v.getId() == R.id.hotseat_right) {
            index = 1;
        }

        // reload these every tap; you never know when they might change
        loadHotseats();
        if (index >= 0 && index < mHotseats.length && mHotseats[index] != null) {
            Intent intent = mHotseats[index];
            startActivitySafely(
                mHotseats[index],
                "hotseat"
            );
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
        TextView favorite = (TextView) mInflater.inflate(layoutResId, parent, false);

        Bitmap b = info.getIcon(mIconCache);

        favorite.setCompoundDrawablesWithIntrinsicBounds(null,
                new FastBitmapDrawable(b),
                null, null);
        favorite.setText(info.title);
        favorite.setTag(info);
        favorite.setOnClickListener(this);

        return favorite;
    }

    /**
     * Add an application shortcut to the workspace.
     *
     * @param data The intent describing the application.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    void completeAddApplication(Context context, Intent data, int screen,
            int intersectCellX, int intersectCellY) {
        final int[] cellXY = mTmpAddItemCellCoordinates;
        final CellLayout layout = (CellLayout) mWorkspace.getChildAt(screen);

        if (!layout.findCellForSpanThatIntersects(cellXY, 1, 1, intersectCellX, intersectCellY)) {
            showOutOfSpaceMessage();
            return;
        }

        final ShortcutInfo info = mModel.getShortcutInfo(context.getPackageManager(),
                data, context);

        if (info != null) {
            info.setActivity(data.getComponent(), Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            info.container = ItemInfo.NO_ID;
            mWorkspace.addApplicationShortcut(info, screen, cellXY[0], cellXY[1],
                    isWorkspaceLocked(), mAddIntersectCellX, mAddIntersectCellY);
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
    private void completeAddShortcut(Intent data, int screen,
            int intersectCellX, int intersectCellY) {
        final int[] cellXY = mTmpAddItemCellCoordinates;
        final CellLayout layout = (CellLayout) mWorkspace.getChildAt(screen);

        if (!layout.findCellForSpanThatIntersects(cellXY, 1, 1, intersectCellX, intersectCellY)) {
            showOutOfSpaceMessage();
            return;
        }

        final ShortcutInfo info = mModel.addShortcut(
                this, data, screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            final View view = createShortcut(info);
            mWorkspace.addInScreen(view, screen, cellXY[0], cellXY[1], 1, 1, isWorkspaceLocked());
        }
    }


    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     * @param cellInfo The position on screen where to create the widget.
     */
    private void completeAddAppWidget(int appWidgetId, int screen) {
        AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);

        // Calculate the grid spans needed to fit this widget
        CellLayout layout = (CellLayout) mWorkspace.getChildAt(screen);
        int[] spanXY = layout.rectToCell(appWidgetInfo.minWidth, appWidgetInfo.minHeight, null);

        // Try finding open space on Launcher screen
        // We have saved the position to which the widget was dragged-- this really only matters
        // if we are placing widgets on a "spring-loaded" screen
        final int[] cellXY = mTmpAddItemCellCoordinates;

        // For now, we don't save the coordinate where we dropped the icon because we're not
        // supporting spring-loaded mini-screens; however, leaving the ability to directly place
        // a widget on the home screen in case we want to add it in the future
        int[] touchXY = null;
        if (mAddDropPosition[0] > -1 && mAddDropPosition[1] > -1) {
            touchXY = mAddDropPosition;
        }
        boolean findNearestVacantAreaFailed = false;
        boolean foundCellSpan = false;
        if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            CellLayout screenLayout = (CellLayout) mWorkspace.getChildAt(screen);
            int[] result = screenLayout.findNearestVacantArea(
                    touchXY[0], touchXY[1], spanXY[0], spanXY[1], cellXY);
            findNearestVacantAreaFailed = (result == null);
            foundCellSpan = !findNearestVacantAreaFailed;
        } else {
            if (mAddIntersectCellX != -1 && mAddIntersectCellY != -1) {
                // if we long pressed on an empty cell to bring up a menu,
                // make sure we intersect the empty cell
                foundCellSpan = layout.findCellForSpanThatIntersects(cellXY, spanXY[0], spanXY[1],
                        mAddIntersectCellX, mAddIntersectCellY);
            } else {
                // if we went through the menu -> add, just find any spot
                foundCellSpan = layout.findCellForSpan(cellXY, spanXY[0], spanXY[1]);
            }
        }

        if (!foundCellSpan) {
            if (appWidgetId != -1) mAppWidgetHost.deleteAppWidgetId(appWidgetId);
            showOutOfSpaceMessage();
            return;
        }

        // Build Launcher-specific widget info and save to database
        LauncherAppWidgetInfo launcherInfo = new LauncherAppWidgetInfo(appWidgetId);
        launcherInfo.spanX = spanXY[0];
        launcherInfo.spanY = spanXY[1];

        LauncherModel.addItemToDatabase(this, launcherInfo,
                LauncherSettings.Favorites.CONTAINER_DESKTOP,
                screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            mDesktopItems.add(launcherInfo);

            // Perform actual inflation because we're live
            launcherInfo.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);

            launcherInfo.hostView.setAppWidget(appWidgetId, appWidgetInfo);
            launcherInfo.hostView.setTag(launcherInfo);

            mWorkspace.addInScreen(launcherInfo.hostView, screen, cellXY[0], cellXY[1],
                    launcherInfo.spanX, launcherInfo.spanY, isWorkspaceLocked());
        }
    }

    void showOutOfSpaceMessage() {
        Toast.makeText(this, getString(R.string.out_of_space), Toast.LENGTH_SHORT).show();
    }

    public void removeAppWidget(LauncherAppWidgetInfo launcherInfo) {
        mDesktopItems.remove(launcherInfo);
        launcherInfo.hostView = null;
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    void closeSystemDialogs() {
        getWindow().closeAllPanels();

        try {
            dismissDialog(DIALOG_CREATE_SHORTCUT);
            // Unlock the workspace if the dialog was showing
        } catch (Exception e) {
            // An exception is thrown if the dialog is not visible, which is fine
        }

        try {
            dismissDialog(DIALOG_RENAME_FOLDER);
            // Unlock the workspace if the dialog was showing
        } catch (Exception e) {
            // An exception is thrown if the dialog is not visible, which is fine
        }

        // Whatever we were doing is hereby canceled.
        mWaitingForResult = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Close the menu
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            // also will cancel mWaitingForResult.
            closeSystemDialogs();

            boolean alreadyOnHome = ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                        != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

            // in all these cases, only animate if we're already on home
            if (LauncherApplication.isScreenXLarge()) {
                mWorkspace.unshrink(alreadyOnHome);
            }
            if (!mWorkspace.isDefaultPageShowing()) {
                // on the phone, we don't animate the change to the workspace if all apps is visible
                mWorkspace.moveToDefaultScreen(alreadyOnHome &&
                        (LauncherApplication.isScreenXLarge() || mState != State.ALL_APPS));
            }
            showWorkspace(alreadyOnHome);

            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager)getSystemService(
                        INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // Do not call super here
        mSavedInstanceState = savedInstanceState;

        if (mHomeCustomizationDrawer != null) {
            String cur = savedInstanceState.getString("currentTab");
            if (cur != null) {
                mHomeCustomizationDrawer.setCurrentTabByTag(cur);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getCurrentPage());

        final ArrayList<Folder> folders = mWorkspace.getOpenFolders();
        if (folders.size() > 0) {
            final int count = folders.size();
            long[] ids = new long[count];
            for (int i = 0; i < count; i++) {
                final FolderInfo info = folders.get(i).getInfo();
                ids[i] = info.id;
            }
            outState.putLongArray(RUNTIME_STATE_USER_FOLDERS, ids);
        } else {
            super.onSaveInstanceState(outState);
        }

        outState.putInt(RUNTIME_STATE, mState.ordinal());

        if (mAddScreen > -1 && mWaitingForResult) {
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SCREEN, mAddScreen);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, mAddIntersectCellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, mAddIntersectCellY);
        }

        if (mFolderInfo != null && mWaitingForResult) {
            outState.putBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, true);
            outState.putLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID, mFolderInfo.id);
        }

        if (mHomeCustomizationDrawer != null) {
            String currentTabTag = mHomeCustomizationDrawer.getCurrentTabTag();
            if (currentTabTag != null) {
                outState.putString("currentTab", currentTabTag);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }

        TextKeyListener.getInstance().release();

        mModel.stopLoader();

        unbindDesktopItems();

        getContentResolver().unregisterContentObserver(mWidgetObserver);

        // Some launcher layouts don't have a previous and next view
        if (mPreviousView != null) {
            dismissPreview(mPreviousView);
        }
        if (mNextView != null) {
            dismissPreview(mNextView);
        }

        unregisterReceiver(mCloseSystemDialogsReceiver);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode >= 0) mWaitingForResult = true;
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {

        showWorkspace(true);

        if (initialQuery == null) {
            // Use any text typed in the launcher as the initial query
            initialQuery = getTypedText();
            clearTypedText();
        }
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString(Search.SOURCE, "launcher-search");
        }

        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchManager.startSearch(initialQuery, selectInitialQuery, getComponentName(),
            appSearchData, globalSearch);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isWorkspaceLocked()) {
            return false;
        }

        super.onCreateOptionsMenu(menu);

        menu.add(MENU_GROUP_ADD, MENU_ADD, 0, R.string.menu_add)
                .setIcon(android.R.drawable.ic_menu_add)
                .setAlphabeticShortcut('A');
        menu.add(0, MENU_MANAGE_APPS, 0, R.string.menu_manage_apps)
                .setIcon(android.R.drawable.ic_menu_manage)
                .setAlphabeticShortcut('M');
        menu.add(MENU_GROUP_WALLPAPER, MENU_WALLPAPER_SETTINGS, 0, R.string.menu_wallpaper)
                 .setIcon(android.R.drawable.ic_menu_gallery)
                 .setAlphabeticShortcut('W');
        menu.add(0, MENU_SEARCH, 0, R.string.menu_search)
                .setIcon(android.R.drawable.ic_search_category_default)
                .setAlphabeticShortcut(SearchManager.MENU_KEY);
        menu.add(0, MENU_NOTIFICATIONS, 0, R.string.menu_notifications)
                .setIcon(com.android.internal.R.drawable.ic_menu_notifications)
                .setAlphabeticShortcut('N');

        final Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        menu.add(0, MENU_SETTINGS, 0, R.string.menu_settings)
                .setIcon(android.R.drawable.ic_menu_preferences).setAlphabeticShortcut('P')
                .setIntent(settings);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // If all apps is animating, don't show the menu, because we don't know
        // which one to show.
        if (mAllAppsGrid.isAnimating()) {
            return false;
        }

        // Only show the add and wallpaper options when we're not in all apps.
        boolean visible = !mAllAppsGrid.isVisible();
        menu.setGroupVisible(MENU_GROUP_ADD, visible);
        menu.setGroupVisible(MENU_GROUP_WALLPAPER, visible);

        // Disable add if the workspace is full.
        if (visible) {
            CellLayout layout = (CellLayout) mWorkspace.getChildAt(mWorkspace.getCurrentPage());
            menu.setGroupEnabled(MENU_GROUP_ADD, layout.existsEmptyCell());
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD:
                addItems();
                return true;
            case MENU_MANAGE_APPS:
                manageApps();
                return true;
            case MENU_WALLPAPER_SETTINGS:
                startWallpaper();
                return true;
            case MENU_SEARCH:
                onSearchRequested();
                return true;
            case MENU_NOTIFICATIONS:
                showNotifications();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, true);
        return true;
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mWaitingForResult;
    }

    private void addItems() {
        if (LauncherApplication.isScreenXLarge()) {
            // Animate the widget chooser up from the bottom of the screen
            if (mState != State.CUSTOMIZE) {
                showCustomizationDrawer(true);
            }
        } else {
            showWorkspace(true);
            showAddDialog(-1, -1);
        }
    }

    private void resetAddInfo() {
        mAddScreen = -1;
        mAddIntersectCellX = -1;
        mAddIntersectCellY = -1;
        mAddDropPosition = null;
    }

    void addAppWidgetFromDrop(PendingAddWidgetInfo info, int screen, int[] position) {
        resetAddInfo();
        mAddScreen = screen;

        // only set mAddDropPosition if we dropped on home screen in "spring-loaded" manner
        mAddDropPosition = position;

        int appWidgetId = getAppWidgetHost().allocateAppWidgetId();
        AppWidgetManager.getInstance(this).bindAppWidgetId(appWidgetId, info.componentName);
        addAppWidgetImpl(appWidgetId, info);
    }

    private void manageApps() {
        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));
    }

    void addAppWidgetFromPick(Intent data) {
        // TODO: catch bad widget exception when sent
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        // TODO: Is this log message meaningful?
        if (LOGD) Log.d(TAG, "dumping extras content=" + data.getExtras());
        addAppWidgetImpl(appWidgetId, null);
    }

    void addAppWidgetImpl(int appWidgetId, PendingAddWidgetInfo info) {
        AppWidgetProviderInfo appWidget = mAppWidgetManager.getAppWidgetInfo(appWidgetId);

        if (appWidget.configure != null) {
            // Launch over to configure widget, if needed
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidget.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            if (info != null) {
                intent.putExtra(InstallWidgetReceiver.EXTRA_APPWIDGET_CONFIGURATION_DATA,
                        info.configurationData);
            }

            startActivityForResultSafely(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            // Otherwise just add it
            completeAddAppWidget(appWidgetId, mAddScreen);
        }
    }

    void processShortcutFromDrop(ComponentName componentName, int screen, int[] position) {
        resetAddInfo();
        mAddScreen = screen;
        mAddDropPosition = position;

        Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        createShortcutIntent.setComponent(componentName);
        processShortcut(createShortcutIntent);
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
            startActivityForResultSafely(pickIntent, REQUEST_PICK_APPLICATION);
        } else {
            startActivityForResultSafely(intent, REQUEST_CREATE_SHORTCUT);
        }
    }

    void processWallpaper(Intent intent) {
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    void addLiveFolderFromDrop(ComponentName componentName, int screen, int[] position) {
        resetAddInfo();
        mAddScreen = screen;
        mAddDropPosition = position;

        Intent createFolderIntent = new Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER);
        createFolderIntent.setComponent(componentName);

        addLiveFolder(createFolderIntent);
    }

    void addLiveFolder(Intent intent) { // YYY add screen intersect etc. parameters here
        // Handle case where user selected "Folder"
        String folderName = getResources().getString(R.string.group_folder);
        String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (folderName != null && folderName.equals(shortcutName)) {
            addFolder(mAddScreen, mAddIntersectCellX, mAddIntersectCellY);
        } else {
            startActivityForResultSafely(intent, REQUEST_CREATE_LIVE_FOLDER);
        }
    }

    void addFolder(int screen, int intersectCellX, int intersectCellY) {
        UserFolderInfo folderInfo = new UserFolderInfo();
        folderInfo.title = getText(R.string.folder_name);

        final CellLayout layout = (CellLayout) mWorkspace.getChildAt(screen);
        final int[] cellXY = mTmpAddItemCellCoordinates;
        if (!layout.findCellForSpanThatIntersects(cellXY, 1, 1, intersectCellX, intersectCellY)) {
            showOutOfSpaceMessage();
            return;
        }

        // Update the model
        LauncherModel.addItemToDatabase(this, folderInfo,
                LauncherSettings.Favorites.CONTAINER_DESKTOP,
                screen, cellXY[0], cellXY[1], false);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()),
                folderInfo, mIconCache);
        mWorkspace.addInScreen(newFolder, screen, cellXY[0], cellXY[1], 1, 1, isWorkspaceLocked());
    }

    void removeFolder(FolderInfo folder) {
        sFolders.remove(folder.id);
    }

    private void completeAddLiveFolder(
            Intent data, int screen, int intersectCellX, int intersectCellY) {
        final CellLayout layout = (CellLayout) mWorkspace.getChildAt(screen);
        final int[] cellXY = mTmpAddItemCellCoordinates;
        if (!layout.findCellForSpanThatIntersects(cellXY, 1, 1, intersectCellX, intersectCellY)) {
            showOutOfSpaceMessage();
            return;
        }

        final LiveFolderInfo info = addLiveFolder(this, data, screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            final View view = LiveFolderIcon.fromXml(R.layout.live_folder_icon, this,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
            mWorkspace.addInScreen(view, screen, cellXY[0], cellXY[1], 1, 1, isWorkspaceLocked());
        }
    }

    static LiveFolderInfo addLiveFolder(Context context, Intent data,
            int screen, int cellX, int cellY, boolean notify) {

        Intent baseIntent = data.getParcelableExtra(LiveFolders.EXTRA_LIVE_FOLDER_BASE_INTENT);
        String name = data.getStringExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME);

        Drawable icon = null;
        Intent.ShortcutIconResource iconResource = null;

        Parcelable extra = data.getParcelableExtra(LiveFolders.EXTRA_LIVE_FOLDER_ICON);
        if (extra != null && extra instanceof Intent.ShortcutIconResource) {
            try {
                iconResource = (Intent.ShortcutIconResource) extra;
                final PackageManager packageManager = context.getPackageManager();
                Resources resources = packageManager.getResourcesForApplication(
                        iconResource.packageName);
                final int id = resources.getIdentifier(iconResource.resourceName, null, null);
                icon = resources.getDrawable(id);
            } catch (Exception e) {
                Log.w(TAG, "Could not load live folder icon: " + extra);
            }
        }

        if (icon == null) {
            icon = context.getResources().getDrawable(R.drawable.ic_launcher_folder);
        }

        final LiveFolderInfo info = new LiveFolderInfo();
        info.icon = Utilities.createIconBitmap(icon, context);
        info.title = name;
        info.iconResource = iconResource;
        info.uri = data.getData();
        info.baseIntent = baseIntent;
        info.displayMode = data.getIntExtra(LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE,
                LiveFolders.DISPLAY_MODE_GRID);

        LauncherModel.addItemToDatabase(context, info, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                screen, cellX, cellY, notify);
        sFolders.put(info.id, info);

        return info;
    }

    private void showNotifications() {
        final StatusBarManager statusBar = (StatusBarManager) getSystemService(STATUS_BAR_SERVICE);
        if (statusBar != null) {
            statusBar.expand();
        }
    }

    private void startWallpaper() {
        showWorkspace(true);
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        Intent chooser = Intent.createChooser(pickWallpaper,
                getText(R.string.chooser_wallpaper));
        // NOTE: Adds a configure option to the chooser if the wallpaper supports it
        //       Removed in Eclair MR1
//        WallpaperManager wm = (WallpaperManager)
//                getSystemService(Context.WALLPAPER_SERVICE);
//        WallpaperInfo wi = wm.getWallpaperInfo();
//        if (wi != null && wi.getSettingsActivity() != null) {
//            LabeledIntent li = new LabeledIntent(getPackageName(),
//                    R.string.configure_wallpaper, 0);
//            li.setClassName(wi.getPackageName(), wi.getSettingsActivity());
//            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { li });
//        }
        startActivityForResult(chooser, REQUEST_PICK_WALLPAPER);
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
                    if (SystemProperties.getInt("debug.launcher2.dumpstate", 0) != 0) {
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
        if (mState == State.ALL_APPS || mState == State.CUSTOMIZE) {
            showWorkspace(true);
        } else {
            closeFolder();
        }
        // Some launcher layouts don't have a previous and next view
        if (mPreviousView != null) {
            dismissPreview(mPreviousView);
            dismissPreview(mNextView);
        }
    }

    private void closeFolder() {
        Folder folder = mWorkspace.getOpenFolder();
        if (folder != null) {
            closeFolder(folder);
        }
    }

    void closeFolder(Folder folder) {
        folder.getInfo().opened = false;
        ViewGroup parent = (ViewGroup) folder.getParent();
        if (parent != null) {
            CellLayout cl = (CellLayout) parent;
            cl.removeViewWithoutMarkingCells(folder);
            if (folder instanceof DropTarget) {
                // Live folders aren't DropTargets.
                mDragController.removeDropTarget((DropTarget)folder);
            }
        }
        folder.onClose();
    }

    /**
     * Re-listen when widgets are reset.
     */
    private void onAppWidgetReset() {
        mAppWidgetHost.startListening();
    }

    /**
     * Go through the and disconnect any of the callbacks in the drawables and the views or we
     * leak the previous Home screen on orientation change.
     */
    private void unbindDesktopItems() {
        for (ItemInfo item: mDesktopItems) {
            item.unbind();
        }
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            // Open shortcut
            final Intent intent = ((ShortcutInfo) tag).intent;
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));
            startActivitySafely(intent, tag);
        } else if (tag instanceof FolderInfo) {
            handleFolderClick((FolderInfo) tag);
        } else if (v == mHandleView) {
            if (mState == State.ALL_APPS) {
                showWorkspace(true);
            } else {
                showAllApps(true);
            }
        }
    }

    public boolean onTouch(View v, MotionEvent event) {
        // this is an intercepted event being forwarded from mWorkspace;
        // clicking anywhere on the workspace causes the customization drawer to slide down
        showWorkspace(true);
        return false;
    }

    /**
     * Event handler for the search button
     *
     * @param v The view that was clicked.
     */
    public void onClickSearchButton(View v) {
        startSearch(null, false, null, true);
    }

    /**
     * Event handler for the "gear" button that appears on the home screen, which
     * enters home screen customization mode.
     *
     * @param v The view that was clicked.
     */
    public void onClickConfigureButton(View v) {
        addItems();
    }

    /**
     * Event handler for the "grid" button that appears on the home screen, which
     * enters all apps mode.
     *
     * @param v The view that was clicked.
     */
    public void onClickAllAppsButton(View v) {
        showAllApps(true);
    }

    public void onClickAppMarketButton(View v) {
        if (mAppMarketIntent != null) {
            startActivitySafely(mAppMarketIntent, "app market");
        }
    }

    void startApplicationDetailsActivity(ComponentName componentName) {
        String packageName = componentName.getPackageName();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        startActivity(intent);
    }

    void startApplicationUninstallActivity(ApplicationInfo appInfo) {
        if ((appInfo.flags & ApplicationInfo.DOWNLOADED_FLAG) == 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            int messageId = R.string.uninstall_system_app_text;
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
        } else {
            String packageName = appInfo.componentName.getPackageName();
            String className = appInfo.componentName.getClassName();
            Intent intent = new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", packageName, className));
            startActivity(intent);
        }
    }

    void startActivitySafely(Intent intent, Object tag) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + tag + " intent=" + intent, e);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity. "
                    + "tag="+ tag + " intent=" + intent, e);
        }
    }

    void startActivityForResultSafely(Intent intent, int requestCode) {
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    private void handleFolderClick(FolderInfo folderInfo) {
        if (!folderInfo.opened) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openFolder(folderInfo);
        } else {
            // Find the open folder...
            Folder openFolder = mWorkspace.getFolderForTag(folderInfo);
            int folderScreen;
            if (openFolder != null) {
                folderScreen = mWorkspace.getPageForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentPage()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderInfo);
                }
            }
        }
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderInfo The FolderInfo describing the folder to open.
     */
    public void openFolder(FolderInfo folderInfo) {
        Folder openFolder;

        if (folderInfo instanceof UserFolderInfo) {
            openFolder = UserFolder.fromXml(this);
        } else if (folderInfo instanceof LiveFolderInfo) {
            openFolder = com.android.launcher2.LiveFolder.fromXml(this, folderInfo);
        } else {
            return;
        }

        openFolder.setDragController(mDragController);
        openFolder.setLauncher(this);

        openFolder.bind(folderInfo);
        folderInfo.opened = true;

        mWorkspace.addInFullScreen(openFolder, folderInfo.screen);

        openFolder.onOpen();
    }

    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.previous_screen:
                if (mState != State.ALL_APPS) {
                    mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    showPreviews(v);
                }
                return true;
            case R.id.next_screen:
                if (mState != State.ALL_APPS) {
                    mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    showPreviews(v);
                }
                return true;
            case R.id.all_apps_button:
                if (mState != State.ALL_APPS) {
                    mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    showPreviews(v);
                }
                return true;
        }

        if (isWorkspaceLocked()) {
            return false;
        }

        if (!(v instanceof CellLayout)) {
            v = (View) v.getParent();
        }


        resetAddInfo();
        CellLayout.CellInfo longClickCellInfo = (CellLayout.CellInfo) v.getTag();
        // This happens when long clicking an item with the dpad/trackball
        if (longClickCellInfo == null || !longClickCellInfo.valid) {
            return true;
        }

        final View itemUnderLongClick = longClickCellInfo.cell;

        if (mWorkspace.allowLongPress()) {
            if (itemUnderLongClick == null) {
                // User long pressed on empty space
                mWorkspace.setAllowLongPress(false);
                mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                if (!LauncherApplication.isScreenXLarge()) {
                    showAddDialog(longClickCellInfo.cellX, longClickCellInfo.cellY);
                }
            } else {
                if (!(itemUnderLongClick instanceof Folder)) {
                    // User long pressed on an item
                    mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    mAddIntersectCellX = longClickCellInfo.cellX;
                    mAddIntersectCellY = longClickCellInfo.cellY;
                    mWorkspace.startDrag(longClickCellInfo);
                }
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked"})
    private void dismissPreview(final View v) {
        final PopupWindow window = (PopupWindow) v.getTag();
        if (window != null) {
            window.setOnDismissListener(new PopupWindow.OnDismissListener() {
                public void onDismiss() {
                    ViewGroup group = (ViewGroup) v.getTag(R.id.workspace);
                    int count = group.getChildCount();
                    for (int i = 0; i < count; i++) {
                        ((ImageView) group.getChildAt(i)).setImageDrawable(null);
                    }
                    ArrayList<Bitmap> bitmaps = (ArrayList<Bitmap>) v.getTag(R.id.icon);
                    for (Bitmap bitmap : bitmaps) bitmap.recycle();

                    v.setTag(R.id.workspace, null);
                    v.setTag(R.id.icon, null);
                    window.setOnDismissListener(null);
                }
            });
            window.dismiss();
        }
        v.setTag(null);
    }

    private void showPreviews(View anchor) {
        showPreviews(anchor, 0, mWorkspace.getChildCount());
    }

    private void showPreviews(final View anchor, int start, int end) {
        final Resources resources = getResources();
        final Workspace workspace = mWorkspace;

        CellLayout cell = ((CellLayout) workspace.getChildAt(start));

        float max = workspace.getChildCount();

        final Rect r = new Rect();
        resources.getDrawable(R.drawable.preview_background).getPadding(r);
        int extraW = (int) ((r.left + r.right) * max);
        int extraH = r.top + r.bottom;

        int aW = cell.getWidth() - extraW;
        float w = aW / max;

        int width = cell.getWidth();
        int height = cell.getHeight();
        int x = cell.getLeftPadding();
        int y = cell.getTopPadding();
        width -= (x + cell.getRightPadding());
        height -= (y + cell.getBottomPadding());

        float scale = w / width;

        int count = end - start;

        final float sWidth = width * scale;
        float sHeight = height * scale;

        LinearLayout preview = new LinearLayout(this);

        PreviewTouchHandler handler = new PreviewTouchHandler(anchor);
        ArrayList<Bitmap> bitmaps = new ArrayList<Bitmap>(count);

        for (int i = start; i < end; i++) {
            ImageView image = new ImageView(this);
            cell = (CellLayout) workspace.getChildAt(i);

            final Bitmap bitmap = Bitmap.createBitmap((int) sWidth, (int) sHeight,
                    Bitmap.Config.ARGB_8888);

            final Canvas c = new Canvas(bitmap);
            c.scale(scale, scale);
            c.translate(-cell.getLeftPadding(), -cell.getTopPadding());
            cell.drawChildren(c);

            image.setBackgroundDrawable(resources.getDrawable(R.drawable.preview_background));
            image.setImageBitmap(bitmap);
            image.setTag(i);
            image.setOnClickListener(handler);
            image.setOnFocusChangeListener(handler);
            image.setFocusable(true);
            if (i == mWorkspace.getCurrentPage()) image.requestFocus();

            preview.addView(image,
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            bitmaps.add(bitmap);
        }

        final PopupWindow p = new PopupWindow(this);
        p.setContentView(preview);
        p.setWidth((int) (sWidth * count + extraW));
        p.setHeight((int) (sHeight + extraH));
        p.setAnimationStyle(R.style.AnimationPreview);
        p.setOutsideTouchable(true);
        p.setFocusable(true);
        p.setBackgroundDrawable(new ColorDrawable(0));
        p.showAsDropDown(anchor, 0, 0);

        p.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() {
                dismissPreview(anchor);
            }
        });

        anchor.setTag(p);
        anchor.setTag(R.id.workspace, preview);
        anchor.setTag(R.id.icon, bitmaps);
    }

    class PreviewTouchHandler implements View.OnClickListener, Runnable, View.OnFocusChangeListener {
        private final View mAnchor;

        public PreviewTouchHandler(View anchor) {
            mAnchor = anchor;
        }

        public void onClick(View v) {
            mWorkspace.snapToPage((Integer) v.getTag());
            v.post(this);
        }

        public void run() {
            dismissPreview(mAnchor);
        }

        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                mWorkspace.snapToPage((Integer) v.getTag());
            }
        }
    }

    Workspace getWorkspace() {
        return mWorkspace;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CREATE_SHORTCUT:
                return new CreateShortcut().createDialog();
            case DIALOG_RENAME_FOLDER:
                return new RenameFolder().createDialog();
        }

        return super.onCreateDialog(id);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DIALOG_CREATE_SHORTCUT:
                break;
            case DIALOG_RENAME_FOLDER:
                if (mFolderInfo != null) {
                    EditText input = (EditText) dialog.findViewById(R.id.folder_name);
                    final CharSequence text = mFolderInfo.title;
                    input.setText(text);
                    input.setSelection(0, text.length());
                }
                break;
        }
    }

    void showRenameDialog(FolderInfo info) {
        mFolderInfo = info;
        mWaitingForResult = true;
        showDialog(DIALOG_RENAME_FOLDER);
    }

    private void showAddDialog(int intersectX, int intersectY) {
        resetAddInfo();
        mAddIntersectCellX = intersectX;
        mAddIntersectCellY = intersectY;
        mAddScreen = mWorkspace.getCurrentPage();
        mWaitingForResult = true;
        showDialog(DIALOG_CREATE_SHORTCUT);
    }

    private void pickShortcut() {
        // Insert extra item to handle picking application
        Bundle bundle = new Bundle();

        ArrayList<String> shortcutNames = new ArrayList<String>();
        shortcutNames.add(getString(R.string.group_applications));
        bundle.putStringArrayList(Intent.EXTRA_SHORTCUT_NAME, shortcutNames);

        ArrayList<ShortcutIconResource> shortcutIcons = new ArrayList<ShortcutIconResource>();
        shortcutIcons.add(ShortcutIconResource.fromContext(Launcher.this,
                        R.drawable.ic_launcher_application));
        bundle.putParcelableArrayList(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcons);

        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
        pickIntent.putExtra(Intent.EXTRA_INTENT, new Intent(Intent.ACTION_CREATE_SHORTCUT));
        pickIntent.putExtra(Intent.EXTRA_TITLE, getText(R.string.title_select_shortcut));
        pickIntent.putExtras(bundle);

        startActivityForResult(pickIntent, REQUEST_PICK_SHORTCUT);
    }

    private class RenameFolder {
        private EditText mInput;

        Dialog createDialog() {
            final View layout = View.inflate(Launcher.this, R.layout.rename_folder, null);
            mInput = (EditText) layout.findViewById(R.id.folder_name);

            AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
            builder.setIcon(0);
            builder.setTitle(getString(R.string.rename_folder_title));
            builder.setCancelable(true);
            builder.setOnCancelListener(new Dialog.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    cleanup();
                }
            });
            builder.setNegativeButton(getString(R.string.cancel_action),
                new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        cleanup();
                    }
                }
            );
            builder.setPositiveButton(getString(R.string.rename_action),
                new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        changeFolderName();
                    }
                }
            );
            builder.setView(layout);

            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface dialog) {
                    mWaitingForResult = true;
                    mInput.requestFocus();
                    InputMethodManager inputManager = (InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.showSoftInput(mInput, 0);
                }
            });

            return dialog;
        }

        private void changeFolderName() {
            final String name = mInput.getText().toString();
            if (!TextUtils.isEmpty(name)) {
                // Make sure we have the right folder info
                mFolderInfo = sFolders.get(mFolderInfo.id);
                mFolderInfo.title = name;
                LauncherModel.updateItemInDatabase(Launcher.this, mFolderInfo);

                if (mWorkspaceLoading) {
                    lockAllApps();
                    mModel.startLoader(Launcher.this, false);
                } else {
                    final FolderIcon folderIcon = (FolderIcon)
                            mWorkspace.getViewForTag(mFolderInfo);
                    if (folderIcon != null) {
                        folderIcon.setText(name);
                        getWorkspace().requestLayout();
                    } else {
                        lockAllApps();
                        mWorkspaceLoading = true;
                        mModel.startLoader(Launcher.this, false);
                    }
                }
            }
            cleanup();
        }

        private void cleanup() {
            dismissDialog(DIALOG_RENAME_FOLDER);
            mWaitingForResult = false;
            mFolderInfo = null;
        }
    }

    // Now a part of LauncherModel.Callbacks. Used to reorder loading steps.
    public boolean isAllAppsVisible() {
        return mState == State.ALL_APPS;
    }

    // AllAppsView.Watcher
    public void zoomed(float zoom) {
        // In XLarge view, we zoom down the workspace below all apps so it's still visible
        if (zoom == 1.0f && !LauncherApplication.isScreenXLarge()) {
            mWorkspace.setVisibility(View.GONE);
        }
    }
    
    private void showToolbarButton(View button) {
        button.setAlpha(1.0f);
        button.setVisibility(View.VISIBLE);
        button.setFocusable(true);
        button.setClickable(true);
    }

    private void hideToolbarButton(View button) {
        button.setAlpha(0.0f);
        // We can't set it to GONE, otherwise the RelativeLayout gets screwed up
        button.setVisibility(View.INVISIBLE);
        button.setFocusable(false);
        button.setClickable(false);
    }

    /**
     * Helper function for showing or hiding a toolbar button, possibly animated.
     *
     * @param show If true, create an animation to the show the item. Otherwise, hide it.
     * @param view The toolbar button to be animated
     * @param seq A AnimatorSet that will be used to animate the transition. If null, the
     * transition will not be animated.
     */
    private void hideOrShowToolbarButton(boolean show, final View view, AnimatorSet seq) {
        final boolean showing = show;
        final boolean hiding = !show;

        final int duration = show ?
                getResources().getInteger(R.integer.config_toolbarButtonFadeInTime) :
                getResources().getInteger(R.integer.config_toolbarButtonFadeOutTime);

        if (seq != null) {
            Animator anim = ObjectAnimator.ofFloat(view, "alpha", show ? 1.0f : 0.0f);
            anim.setDuration(duration);
            anim.addListener(new LauncherAnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (showing) showToolbarButton(view);
                }
                @Override
                public void onAnimationEndOrCancel(Animator animation) {
                    if (hiding) hideToolbarButton(view);
                }
            });
            seq.play(anim);
        } else {
            if (showing) {
                showToolbarButton(view);
            } else {
                hideToolbarButton(view);
            }
        }
    }

    /**
     * Show/hide the appropriate toolbar buttons for newState.
     * If showSeq or hideSeq is null, the transition will be done immediately (not animated).
     *
     * @param newState The state that is being switched to
     * @param showSeq AnimatorSet in which to put "show" animations, or null.
     * @param hideSeq AnimatorSet in which to put "hide" animations, or null.
     */
    private void hideAndShowToolbarButtons(State newState, AnimatorSet showSeq, AnimatorSet hideSeq) {
        final View searchButton = findViewById(R.id.search_button);
        final View allAppsButton = findViewById(R.id.all_apps_button);
        final View marketButton = findViewById(R.id.market_button);
        final View configureButton = findViewById(R.id.configure_button);

        switch (newState) {
        case WORKSPACE:
            hideOrShowToolbarButton(true, searchButton, showSeq);
            hideOrShowToolbarButton(true, allAppsButton, showSeq);
            hideOrShowToolbarButton(true, configureButton, showSeq);
            hideOrShowToolbarButton(false, marketButton, hideSeq);
            mDeleteZone.setHandle(allAppsButton);
            break;
        case ALL_APPS:
            hideOrShowToolbarButton(true, configureButton, showSeq);
            hideOrShowToolbarButton(true, marketButton, showSeq);
            hideOrShowToolbarButton(false, searchButton, hideSeq);
            hideOrShowToolbarButton(false, allAppsButton, hideSeq);
            mDeleteZone.setHandle(marketButton);
            break;
        case CUSTOMIZE:
            hideOrShowToolbarButton(true, allAppsButton, showSeq);
            hideOrShowToolbarButton(false, searchButton, hideSeq);
            hideOrShowToolbarButton(false, marketButton, hideSeq);
            hideOrShowToolbarButton(false, configureButton, hideSeq);
            mDeleteZone.setHandle(allAppsButton);
            break;
        }
    }

    /**
     * Helper method for the cameraZoomIn/cameraZoomOut animations
     * @param view The view being animated
     * @param state The state that we are moving in or out of -- either ALL_APPS or CUSTOMIZE
     * @param scaleFactor The scale factor used for the zoom
     */
    private void setPivotsForZoom(View view, State state, float scaleFactor) {
        final int height = view.getHeight();
        view.setPivotX(view.getWidth() / 2.0f);
        // Set pivotY so that at the starting zoom factor, the view is off-screen by a small margin
        // Assumes that the view is normally anchored to either the top or bottom of the screen
        final int margin = getResources().getInteger(R.integer.config_allAppsVerticalOffset);
        if (state == State.ALL_APPS) {
            view.setPivotY(height + ((view.getTop() + height) / scaleFactor) + margin);
        } else {
            view.setPivotY(0.0f - (view.getTop() / scaleFactor) - margin);
        }
    }

    /**
     * Zoom the camera out from the workspace to reveal 'toView'.
     * Assumes that the view to show is anchored at either the very top or very bottom
     * of the screen.
     * @param toState The state to zoom out to. Must be ALL_APPS or CUSTOMIZE.
     */
    private void cameraZoomOut(State toState, boolean animated) {
        final Resources res = getResources();
        final int duration = res.getInteger(R.integer.config_allAppsZoomInTime);
        final float scale = (float) res.getInteger(R.integer.config_allAppsZoomScaleFactor);
        final boolean toAllApps = (toState == State.ALL_APPS);
        final View toView = toAllApps ? (View) mAllAppsGrid : mHomeCustomizationDrawer;

        setPivotsForZoom(toView, toState, scale);

        if (toAllApps) {
            mWorkspace.shrinkToBottom(animated);
        } else {
            mWorkspace.shrinkToTop(animated);
        }

        if (animated) {
            ValueAnimator scaleAnim = ObjectAnimator.ofPropertyValuesHolder(toView,
                    PropertyValuesHolder.ofFloat("scaleX", scale, 1.0f),
                    PropertyValuesHolder.ofFloat("scaleY", scale, 1.0f));
            scaleAnim.setDuration(duration);
            scaleAnim.setInterpolator(new DecelerateInterpolator());
            scaleAnim.addListener(new LauncherAnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    // Prepare the position
                    toView.setTranslationX(0.0f);
                    toView.setTranslationY(0.0f);
                    toView.setVisibility(View.VISIBLE);
                }
                @Override
                public void onAnimationEndOrCancel(Animator animation) {
                    // If we don't set the final scale values here, if this animation is cancelled
                    // it will have the wrong scale value and subsequent cameraPan animations will
                    // not fix that
                    toView.setScaleX(1.0f);
                    toView.setScaleY(1.0f);
                }
            });

            AnimatorSet toolbarHideAnim = new AnimatorSet();
            AnimatorSet toolbarShowAnim = new AnimatorSet();
            hideAndShowToolbarButtons(toState, toolbarShowAnim, toolbarHideAnim);

            // toView should appear right at the end of the workspace shrink animation
            final int startDelay = res.getInteger(R.integer.config_workspaceShrinkTime) - duration;

            if (mStateAnimation != null) mStateAnimation.cancel();
            mStateAnimation = new AnimatorSet();
            mStateAnimation.playTogether(scaleAnim, toolbarHideAnim);
            mStateAnimation.play(scaleAnim).after(startDelay);

            // Show the new toolbar buttons just as the main animation is ending
            final int fadeInTime = res.getInteger(R.integer.config_toolbarButtonFadeInTime);
            mStateAnimation.play(toolbarShowAnim).after(duration + startDelay - fadeInTime);
            mStateAnimation.start();
        } else {
            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setVisibility(View.VISIBLE);
            hideAndShowToolbarButtons(toState, null, null);
        }
    }

    /**
     * Zoom the camera back into the workspace, hiding 'fromView'.
     * This is the opposite of cameraZoomOut.
     * @param fromState The current state (must be ALL_APPS or CUSTOMIZE).
     * @param animated If true, the transition will be animated.
     */
    private void cameraZoomIn(State fromState, boolean animated) {
        Resources res = getResources();
        int duration = res.getInteger(R.integer.config_allAppsZoomOutTime);
        float scaleFactor = (float) res.getInteger(R.integer.config_allAppsZoomScaleFactor);
        final View fromView =
            (fromState == State.ALL_APPS) ? (View) mAllAppsGrid : mHomeCustomizationDrawer;

        mCustomizePagedView.endChoiceMode();
        mAllAppsPagedView.endChoiceMode();

        setPivotsForZoom(fromView, fromState, scaleFactor);

        mWorkspace.unshrink(animated);

        if (animated) {
            if (mStateAnimation != null) mStateAnimation.cancel();
            mStateAnimation = new AnimatorSet();
            ValueAnimator scaleAnim = ObjectAnimator.ofPropertyValuesHolder(fromView,
                    PropertyValuesHolder.ofFloat("scaleX", scaleFactor),
                    PropertyValuesHolder.ofFloat("scaleY", scaleFactor));
            scaleAnim.setDuration(duration);
            scaleAnim.setInterpolator(new AccelerateInterpolator());
            mStateAnimation.addListener(new LauncherAnimatorListenerAdapter() {
                @Override
                public void onAnimationEndOrCancel(Animator animation) {
                    fromView.setVisibility(View.GONE);
                }
            });

            AnimatorSet toolbarHideAnim = new AnimatorSet();
            AnimatorSet toolbarShowAnim = new AnimatorSet();
            hideAndShowToolbarButtons(State.WORKSPACE, toolbarShowAnim, toolbarHideAnim);

            mStateAnimation.playTogether(scaleAnim, toolbarHideAnim);

            // Show the new toolbar buttons at the very end of the whole animation
            final int fadeInTime = res.getInteger(R.integer.config_toolbarButtonFadeInTime);
            final int unshrinkTime = res.getInteger(R.integer.config_workspaceUnshrinkTime);
            mStateAnimation.play(toolbarShowAnim).after(unshrinkTime - fadeInTime);
            mStateAnimation.start();
        } else {
            fromView.setVisibility(View.GONE);
            hideAndShowToolbarButtons(State.WORKSPACE, null, null);
        }
    }

    /**
     * Pan the camera in the vertical plane between 'fromView' and 'toView'.
     * This is the transition used on xlarge screens to go between all apps and
     * the home customization drawer.
     * @param fromState The view to pan away from. Must be ALL_APPS or CUSTOMIZE.
     * @param toState The view to pan into the frame. Must be ALL_APPS or CUSTOMIZE.
     * @param animated If true, the transition will be animated.
     */
    private void cameraPan(State fromState, State toState, boolean animated) {
        final Resources res = getResources();
        final int duration = res.getInteger(R.integer.config_allAppsCameraPanTime);
        final int workspaceHeight = mWorkspace.getHeight();

        final boolean fromAllApps = (fromState == State.ALL_APPS);
        final View fromView = fromAllApps ? (View) mAllAppsGrid : mHomeCustomizationDrawer;
        final View toView = fromAllApps ? mHomeCustomizationDrawer : (View) mAllAppsGrid;

        final float fromViewStartY = fromAllApps ? 0.0f : fromView.getY();
        final float fromViewEndY = fromAllApps ? -fromView.getHeight() * 2 : workspaceHeight * 2;
        final float toViewStartY = fromAllApps ? workspaceHeight * 2 : -toView.getHeight() * 2;
        final float toViewEndY = fromAllApps ? workspaceHeight - toView.getHeight() : 0.0f;

        mCustomizePagedView.endChoiceMode();
        mAllAppsPagedView.endChoiceMode();

        if (toState == State.ALL_APPS) {
            mWorkspace.shrinkToBottom(animated);
        } else {
            mWorkspace.shrinkToTop(animated);
        }

        if (animated) {
            if (mStateAnimation != null) mStateAnimation.cancel();
            mStateAnimation = new AnimatorSet();
            mStateAnimation.addListener(new LauncherAnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    toView.setVisibility(View.VISIBLE);
                    toView.setY(toViewStartY);
                }
                @Override
                public void onAnimationEndOrCancel(Animator animation) {
                    fromView.setVisibility(View.GONE);
                }
            });

            AnimatorSet toolbarHideAnim = new AnimatorSet();
            AnimatorSet toolbarShowAnim = new AnimatorSet();
            hideAndShowToolbarButtons(toState, toolbarShowAnim, toolbarHideAnim);

            ObjectAnimator fromAnim = ObjectAnimator.ofFloat(fromView, "y",
                    fromViewStartY, fromViewEndY);
            fromAnim.setDuration(duration);
            ObjectAnimator toAnim = ObjectAnimator.ofPropertyValuesHolder(toView,
                    PropertyValuesHolder.ofFloat("y", toViewStartY, toViewEndY),
                    PropertyValuesHolder.ofFloat("scaleX", toView.getScaleX(), 1.0f),
                    PropertyValuesHolder.ofFloat("scaleY", toView.getScaleY(), 1.0f)
                    );
            fromAnim.setDuration(duration);
            mStateAnimation.playTogether(toolbarHideAnim, fromAnim, toAnim);

            // Show the new toolbar buttons just as the main animation is ending
            final int fadeInTime = res.getInteger(R.integer.config_toolbarButtonFadeInTime);
            mStateAnimation.play(toolbarShowAnim).after(duration - fadeInTime);
            mStateAnimation.start();
        } else {
            fromView.setY(fromViewEndY);
            fromView.setVisibility(View.GONE);
            toView.setY(toViewEndY);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setVisibility(View.VISIBLE);
            hideAndShowToolbarButtons(toState, null, null);
        }
    }

    void showAllApps(boolean animated) {
        if (mState == State.ALL_APPS) {
            return;
        }

        if (LauncherApplication.isScreenXLarge()) {
            if (mState == State.CUSTOMIZE) {
                cameraPan(State.CUSTOMIZE, State.ALL_APPS, animated);
            } else {
                cameraZoomOut(State.ALL_APPS, animated);
            }
        } else {
            mAllAppsGrid.zoom(1.0f, animated);
        }

        ((View) mAllAppsGrid).setFocusable(true);
        ((View) mAllAppsGrid).requestFocus();

        // TODO: fade these two too
        mDeleteZone.setVisibility(View.GONE);
        // Change the state *after* we've called all the transition code
        mState = State.ALL_APPS;
    }


    void showWorkspace(boolean animated) {
        showWorkspace(animated, null);
    }

    void showWorkspace(boolean animated, CellLayout layout) {
        if (layout != null && animated) {
            mWorkspace.unshrink(layout);
        } else {
            mWorkspace.unshrink(animated);
        }
        if (mState == State.ALL_APPS) {
            closeAllApps(animated);
        } else if (mState == State.CUSTOMIZE) {
            hideCustomizationDrawer(animated);
        }
        // Change the state *after* we've called all the transition code
        mState = State.WORKSPACE;
    }

    /**
     * Things to test when changing this code.
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
    void closeAllApps(boolean animated) {
        if (mState == State.ALL_APPS) {
            mWorkspace.setVisibility(View.VISIBLE);
            if (LauncherApplication.isScreenXLarge()) {
                cameraZoomIn(State.ALL_APPS, animated);
            } else {
                mAllAppsGrid.zoom(0.0f, animated);
            }
            ((View)mAllAppsGrid).setFocusable(false);
            mWorkspace.getChildAt(mWorkspace.getCurrentPage()).requestFocus();
        }
    }

    void lockAllApps() {
        // TODO
    }

    void unlockAllApps() {
        // TODO
    }

    // Show the customization drawer (only exists in x-large configuration)
    private void showCustomizationDrawer(boolean animated) {
        if (mState == State.ALL_APPS) {
            cameraPan(State.ALL_APPS, State.CUSTOMIZE, animated);
        } else {
            cameraZoomOut(State.CUSTOMIZE, animated);
        }
        // Change the state *after* we've called all the transition code
        mState = State.CUSTOMIZE;
    }

    // Hide the customization drawer (only exists in x-large configuration)
    void hideCustomizationDrawer(boolean animated) {
        if (mState == State.CUSTOMIZE) {
            cameraZoomIn(State.CUSTOMIZE, animated);
        }
    }

    void addExternalItemToScreen(ItemInfo itemInfo, CellLayout layout) {
        if (!mWorkspace.addExternalItemToScreen(itemInfo, layout)) {
            showOutOfSpaceMessage();
        }
    }

    void onWorkspaceClick(CellLayout layout) {
        showWorkspace(true, layout);
    }

    private void updateButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId) {
        ImageView button = (ImageView) findViewById(buttonId);
        Drawable toolbarIcon = null;
        try {
            PackageManager packageManager = getPackageManager();
            // Look for the toolbar icon specified in the activity meta-data
            Bundle metaData = packageManager.getActivityInfo(
                    activityName, PackageManager.GET_META_DATA).metaData;
            if (metaData != null) {
                int iconResId = metaData.getInt(TOOLBAR_ICON_METADATA_NAME);
                if (iconResId != 0) {
                    Resources res = packageManager.getResourcesForActivity(activityName);
                    toolbarIcon = res.getDrawable(iconResId);
                }
            }
        } catch (NameNotFoundException e) {
            // Do nothing
        }
        // If we were unable to find the icon via the meta-data, use a generic one
        if (toolbarIcon == null) {
            button.setImageResource(fallbackDrawableId);
        } else {
            button.setImageDrawable(toolbarIcon);
        }
    }

    private void updateGlobalSearchIcon() {
        if (LauncherApplication.isScreenXLarge()) {
            final SearchManager searchManager =
                    (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            ComponentName activityName = searchManager.getGlobalSearchActivity();
            if (activityName != null) {
                updateButtonWithIconFromExternalActivity(
                        R.id.search_button, activityName, R.drawable.search_button_generic);
            }
        }
    }

    /**
     * Sets the app market icon (shown when all apps is visible on x-large screens)
     */
    private void updateAppMarketIcon() {
        if (LauncherApplication.isScreenXLarge()) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET);
            // Find the app market activity by resolving an intent.
            // (If multiple app markets are installed, it will return the ResolverActivity.)
            ComponentName activityName = intent.resolveActivity(getPackageManager());
            if (activityName != null) {
                mAppMarketIntent = intent;
                updateButtonWithIconFromExternalActivity(
                        R.id.market_button, activityName, R.drawable.app_market_generic);
            }
        }
    }

    /**
     * Displays the shortcut creation dialog and launches, if necessary, the
     * appropriate activity.
     */
    private class CreateShortcut implements DialogInterface.OnClickListener,
            DialogInterface.OnCancelListener, DialogInterface.OnDismissListener,
            DialogInterface.OnShowListener {

        private AddAdapter mAdapter;

        Dialog createDialog() {
            mAdapter = new AddAdapter(Launcher.this);

            final AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
            builder.setTitle(getString(R.string.menu_item_add_item));
            builder.setAdapter(mAdapter, this);

            builder.setInverseBackgroundForced(true);

            AlertDialog dialog = builder.create();
            dialog.setOnCancelListener(this);
            dialog.setOnDismissListener(this);
            dialog.setOnShowListener(this);

            return dialog;
        }

        public void onCancel(DialogInterface dialog) {
            mWaitingForResult = false;
            cleanup();
        }

        public void onDismiss(DialogInterface dialog) {
        }

        private void cleanup() {
            try {
                dismissDialog(DIALOG_CREATE_SHORTCUT);
            } catch (Exception e) {
                // An exception is thrown if the dialog is not visible, which is fine
            }
        }

        /**
         * Handle the action clicked in the "Add to home" dialog.
         */
        public void onClick(DialogInterface dialog, int which) {
            Resources res = getResources();
            cleanup();

            switch (which) {
                case AddAdapter.ITEM_SHORTCUT: {
                    pickShortcut();
                    break;
                }

                case AddAdapter.ITEM_APPWIDGET: {
                    int appWidgetId = Launcher.this.mAppWidgetHost.allocateAppWidgetId();

                    Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
                    pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    // start the pick activity
                    startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
                    break;
                }

                case AddAdapter.ITEM_LIVE_FOLDER: {
                    // Insert extra item to handle inserting folder
                    Bundle bundle = new Bundle();

                    ArrayList<String> shortcutNames = new ArrayList<String>();
                    shortcutNames.add(res.getString(R.string.group_folder));
                    bundle.putStringArrayList(Intent.EXTRA_SHORTCUT_NAME, shortcutNames);

                    ArrayList<ShortcutIconResource> shortcutIcons =
                            new ArrayList<ShortcutIconResource>();
                    shortcutIcons.add(ShortcutIconResource.fromContext(Launcher.this,
                            R.drawable.ic_launcher_folder));
                    bundle.putParcelableArrayList(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, shortcutIcons);

                    Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                    pickIntent.putExtra(Intent.EXTRA_INTENT,
                            new Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER));
                    pickIntent.putExtra(Intent.EXTRA_TITLE,
                            getText(R.string.title_select_live_folder));
                    pickIntent.putExtras(bundle);

                    startActivityForResult(pickIntent, REQUEST_PICK_LIVE_FOLDER);
                    break;
                }

                case AddAdapter.ITEM_WALLPAPER: {
                    startWallpaper();
                    break;
                }
            }
        }

        public void onShow(DialogInterface dialog) {
            mWaitingForResult = true;
        }
    }

    /**
     * Receives notifications when applications are added/removed.
     */
    private class CloseSystemDialogsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            closeSystemDialogs();
            String reason = intent.getStringExtra("reason");
            if (!"homekey".equals(reason)) {
                boolean animate = true;
                if (mPaused || "lock".equals(reason)) {
                    animate = false;
                }
                showWorkspace(animate);
            }
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

    void setAllAppsPagedView(PagedView view) {
        mAllAppsPagedView = view;
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        final Workspace workspace = mWorkspace;
        int count = workspace.getChildCount();
        for (int i = 0; i < count; i++) {
            // Use removeAllViewsInLayout() to avoid an extra requestLayout() and invalidate().
            ((ViewGroup) workspace.getChildAt(i)).removeAllViewsInLayout();
        }

        if (DEBUG_USER_INTERFACE) {
            android.widget.Button finishButton = new android.widget.Button(this);
            finishButton.setText("Finish");
            workspace.addInScreen(finishButton, 1, 0, 0, 1, 1);

            finishButton.setOnClickListener(new android.widget.Button.OnClickListener() {
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end) {

        setLoadOnResume();

        final Workspace workspace = mWorkspace;

        for (int i=start; i<end; i++) {
            final ItemInfo item = shortcuts.get(i);
            mDesktopItems.add(item);
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    final View shortcut = createShortcut((ShortcutInfo)item);
                    workspace.addInScreen(shortcut, item.screen, item.cellX, item.cellY, 1, 1,
                            false);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_USER_FOLDER:
                    final FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (UserFolderInfo) item, mIconCache);
                    workspace.addInScreen(newFolder, item.screen, item.cellX, item.cellY, 1, 1,
                            false);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_LIVE_FOLDER:
                    final FolderIcon newLiveFolder = LiveFolderIcon.fromXml(
                            R.layout.live_folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (LiveFolderInfo) item);
                    workspace.addInScreen(newLiveFolder, item.screen, item.cellX, item.cellY, 1, 1,
                            false);
                    break;
            }
        }

        workspace.requestLayout();
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindFolders(HashMap<Long, FolderInfo> folders) {
        setLoadOnResume();
        sFolders.clear();
        sFolders.putAll(folders);
    }

    /**
     * Add the views for a widget to the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppWidget(LauncherAppWidgetInfo item) {
        setLoadOnResume();

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

        item.hostView.setAppWidget(appWidgetId, appWidgetInfo);
        item.hostView.setTag(item);

        workspace.addInScreen(item.hostView, item.screen, item.cellX,
                item.cellY, item.spanX, item.spanY, false);

        workspace.requestLayout();

        mDesktopItems.add(item);

        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bound widget id="+item.appWidgetId+" in "
                    + (SystemClock.uptimeMillis()-start) + "ms");
        }
    }

    /**
     * Callback saying that there aren't any more items to bind.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems() {
        setLoadOnResume();

        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentPage()).requestFocus();
            }

            final long[] userFolders = mSavedState.getLongArray(RUNTIME_STATE_USER_FOLDERS);
            if (userFolders != null) {
                for (long folderId : userFolders) {
                    final FolderInfo info = sFolders.get(folderId);
                    if (info != null) {
                        openFolder(info);
                    }
                }
                final Folder openFolder = mWorkspace.getOpenFolder();
                if (openFolder != null) {
                    openFolder.requestFocus();
                }
            }

            mSavedState = null;
        }

        if (mSavedInstanceState != null) {
            super.onRestoreInstanceState(mSavedInstanceState);
            mSavedInstanceState = null;
        }

        mWorkspaceLoading = false;
    }

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAllApplications(ArrayList<ApplicationInfo> apps) {
        mAllAppsGrid.setApps(apps);
        if (mCustomizePagedView != null) {
            mCustomizePagedView.setApps(apps);
        }
        updateAppMarketIcon();
        updateGlobalSearchIcon();
    }

    /**
     * A package was installed.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsAdded(ArrayList<ApplicationInfo> apps) {
        setLoadOnResume();
        removeDialog(DIALOG_CREATE_SHORTCUT);
        mAllAppsGrid.addApps(apps);
        if (mCustomizePagedView != null) {
            mCustomizePagedView.addApps(apps);
        }
        updateAppMarketIcon();
        updateGlobalSearchIcon();
    }

    /**
     * A package was updated.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsUpdated(ArrayList<ApplicationInfo> apps) {
        setLoadOnResume();
        removeDialog(DIALOG_CREATE_SHORTCUT);
        mWorkspace.updateShortcuts(apps);
        mAllAppsGrid.updateApps(apps);
        if (mCustomizePagedView != null) {
            mCustomizePagedView.updateApps(apps);
        }
        updateAppMarketIcon();
        updateGlobalSearchIcon();
    }

    /**
     * A package was uninstalled.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsRemoved(ArrayList<ApplicationInfo> apps, boolean permanent) {
        removeDialog(DIALOG_CREATE_SHORTCUT);
        if (permanent) {
            mWorkspace.removeItems(apps);
        }
        mAllAppsGrid.removeApps(apps);
        if (mCustomizePagedView != null) {
            mCustomizePagedView.removeApps(apps);
        }
        updateAppMarketIcon();
        updateGlobalSearchIcon();
    }

    /**
     * A number of packages were updated.
     */
    public void bindPackagesUpdated() {
        // update the customization drawer contents
        if (mCustomizePagedView != null) {
            mCustomizePagedView.update();
        }
    }

    /**
     * Prints out out state for debugging.
     */
    public void dumpState() {
        Log.d(TAG, "BEGIN launcher2 dump state for launcher " + this);
        Log.d(TAG, "mSavedState=" + mSavedState);
        Log.d(TAG, "mWorkspaceLoading=" + mWorkspaceLoading);
        Log.d(TAG, "mRestoring=" + mRestoring);
        Log.d(TAG, "mWaitingForResult=" + mWaitingForResult);
        Log.d(TAG, "mSavedInstanceState=" + mSavedInstanceState);
        Log.d(TAG, "mDesktopItems.size=" + mDesktopItems.size());
        Log.d(TAG, "sFolders.size=" + sFolders.size());
        mModel.dumpState();
        mAllAppsGrid.dumpState();
        Log.d(TAG, "END launcher2 dump state");
    }
}
