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

package com.android.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.gadget.GadgetProviderInfo;
import android.gadget.GadgetManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.Message;
import android.provider.*;
import android.telephony.PhoneNumberUtils;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnLongClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.GridView;
import android.widget.SlidingDrawer;
import android.app.IWallpaperService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Default launcher application.
 */
public final class Launcher extends Activity implements View.OnClickListener, OnLongClickListener {
    static final String LOG_TAG = "Launcher";
    static final boolean LOGD = false;

    private static final boolean PROFILE_STARTUP = false;
    private static final boolean DEBUG_USER_INTERFACE = false;

    private static final int WALLPAPER_SCREENS_SPAN = 2;

    private static final int MENU_GROUP_ADD = 1;
    private static final int MENU_ADD = Menu.FIRST + 1;
    private static final int MENU_WALLPAPER_SETTINGS = MENU_ADD + 1;
    private static final int MENU_SEARCH = MENU_WALLPAPER_SETTINGS + 1;
    private static final int MENU_NOTIFICATIONS = MENU_SEARCH + 1;
    private static final int MENU_SETTINGS = MENU_NOTIFICATIONS + 1;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_LIVE_FOLDER = 4;
    private static final int REQUEST_CREATE_GADGET = 5;
    private static final int REQUEST_PICK_APPLICATION = 6;
    private static final int REQUEST_PICK_SHORTCUT = 7;
    private static final int REQUEST_PICK_LIVE_FOLDER = 8;
    private static final int REQUEST_PICK_GADGET = 9;

    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

    static final int SCREEN_COUNT = 3;
    static final int DEFAULT_SCREN = 1;
    static final int NUMBER_CELLS_X = 4;
    static final int NUMBER_CELLS_Y = 4;    

    private static final int DIALOG_CREATE_SHORTCUT = 1;
    static final int DIALOG_RENAME_FOLDER = 2;    

    private static final String PREFERENCES = "launcher";
    private static final String KEY_LOCALE = "locale";
    private static final String KEY_MCC = "mcc";
    private static final String KEY_MNC = "mnc";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: boolean
    private static final String RUNTIME_STATE_ALL_APPS_FOLDER = "launcher.all_apps_folder";
    // Type: long
    private static final String RUNTIME_STATE_USER_FOLDERS = "launcher.user_folder";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cellX";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cellY";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_X = "launcher.add_spanX";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_Y = "launcher.add_spanY";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_COUNT_X = "launcher.add_countX";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_COUNT_Y = "launcher.add_countY";
    // Type: int[]
    private static final String RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS = "launcher.add_occupied_cells";
    // Type: boolean
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
    // Type: long
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID = "launcher.rename_folder_id";

    private static LauncherModel sModel;

    private static Bitmap sWallpaper;

    private static final Object sLock = new Object();
    private static int sScreen = DEFAULT_SCREN;

    private static WallpaperIntentReceiver sWallpaperReceiver;

    private final BroadcastReceiver mApplicationsReceiver = new ApplicationsIntentReceiver();
    private final ContentObserver mObserver = new FavoritesChangeObserver();

    private LayoutInflater mInflater;

    private DragLayer mDragLayer;
    private Workspace mWorkspace;
    
    private GadgetManager mGadgetManager;
    private LauncherGadgetHost mGadgetHost;
    
    static final int GADGET_HOST_ID = 1024;
    
    private CellLayout.CellInfo mAddItemCellInfo;
    private CellLayout.CellInfo mMenuAddInfo;
    private final int[] mCellCoordinates = new int[2];
    private FolderInfo mFolderInfo;

    private SlidingDrawer mDrawer;
    private TransitionDrawable mHandleIcon;
    private AllAppsGridView mAllAppsGrid;

    private boolean mDesktopLocked = true;
    private Bundle mSavedState;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mDestroyed;

    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mLocaleChanged;

    private Bundle mSavedInstanceState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInflater = getLayoutInflater();
        
        mGadgetManager = GadgetManager.getInstance(this);
        
        mGadgetHost = new LauncherGadgetHost(this, GADGET_HOST_ID);
        mGadgetHost.startListening();
        
        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing("/sdcard/launcher");
        }

        checkForLocaleChange();
        setWallpaperDimension();

        if (sModel == null) {
            sModel = new LauncherModel();
        }

        setContentView(R.layout.launcher);
        setupViews();

        registerIntentReceivers();
        registerContentObservers();

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }

        if (!mRestoring) {
            startLoaders();
        }

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);
    }
    
    private void checkForLocaleChange() {
        final SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        final Configuration configuration = getResources().getConfiguration();

        final String previousLocale = preferences.getString(KEY_LOCALE, null);
        final String locale = configuration.locale.toString();

        final int previousMcc = preferences.getInt(KEY_MCC, -1);
        final int mcc = configuration.mcc;

        final int previousMnc = preferences.getInt(KEY_MNC, -1);
        final int mnc = configuration.mnc;

        mLocaleChanged = !locale.equals(previousLocale) || mcc != previousMcc || mnc != previousMnc;

        if (mLocaleChanged) {
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(KEY_LOCALE, locale);
            editor.putInt(KEY_MCC, mcc);
            editor.putInt(KEY_MNC, mnc);
            editor.commit();
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

    private void startLoaders() {
        sModel.loadApplications(true, this, mLocaleChanged);
        sModel.loadUserItems(!mLocaleChanged, this, mLocaleChanged, true);
        mRestoring = false;
    }

    private void setWallpaperDimension() {
        IBinder binder = ServiceManager.getService(WALLPAPER_SERVICE);
        IWallpaperService wallpaperService = IWallpaperService.Stub.asInterface(binder);

        Display display = getWindowManager().getDefaultDisplay();
        boolean isPortrait = display.getWidth() < display.getHeight();

        final int width = isPortrait ? display.getWidth() : display.getHeight();
        final int height = isPortrait ? display.getHeight() : display.getWidth();
        try {
            wallpaperService.setDimensionHints(width * WALLPAPER_SCREENS_SPAN, height);
        } catch (RemoteException e) {
            // System is dead!
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.
        
        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.
        
        if (resultCode == RESULT_OK && mAddItemCellInfo != null) {
            switch (requestCode) {
                case REQUEST_PICK_APPLICATION:
                    completeAddApplication(this, data, mAddItemCellInfo, !mDesktopLocked);
                    break;
                case REQUEST_PICK_SHORTCUT:
                    addShortcut(data);
                    break;
                case REQUEST_CREATE_SHORTCUT:
                    completeAddShortcut(data, mAddItemCellInfo, !mDesktopLocked);
                    break;
                case REQUEST_PICK_LIVE_FOLDER:
                    addLiveFolder(data);
                    break;
                case REQUEST_CREATE_LIVE_FOLDER:
                    completeAddLiveFolder(data, mAddItemCellInfo, !mDesktopLocked);
                    break;
                case REQUEST_PICK_GADGET:
                    addGadget(data);
                    break;
                case REQUEST_CREATE_GADGET:
                    completeAddGadget(data, mAddItemCellInfo, !mDesktopLocked);
                    break;
            }
        } else if (requestCode == REQUEST_PICK_GADGET &&
                resultCode == RESULT_CANCELED && data != null) {
            // Clean up the gadgetId if we canceled
            int gadgetId = data.getIntExtra(GadgetManager.EXTRA_GADGET_ID, -1);
            if (gadgetId != -1) {
                mGadgetHost.deleteGadgetId(gadgetId);
            }
        }
        mWaitingForResult = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mRestoring) {
            startLoaders();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean handled = super.onKeyUp(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            handled = mWorkspace.snapToSearch();
            if (handled) closeDrawer(true);
        }
        return handled;
    }

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
                // something usable has been typed - dispatch it now.
                final String str = mDefaultKeySsb.toString();

                boolean isDialable = true;
                final int count = str.length();
                for (int i = 0; i < count; i++) {
                    if (!PhoneNumberUtils.isReallyDialable(str.charAt(i))) {
                        isDialable = false;
                        break;
                    }
                }
                Intent intent;
                if (isDialable) {
                    intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", str, null));
                } else {
                    intent = new Intent(Contacts.Intents.UI.FILTER_CONTACTS_ACTION);
                    intent.putExtra(Contacts.Intents.UI.FILTER_TEXT_EXTRA_KEY, str);
                }

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                try {
                    startActivity(intent);
                } catch (android.content.ActivityNotFoundException ex) {
                    // Oh well... no one knows how to filter/dial. Life goes on.
                }

                mDefaultKeySsb.clear();
                mDefaultKeySsb.clearSpans();
                Selection.setSelection(mDefaultKeySsb, 0);

                return true;
            }
        }

        return handled;
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

        final int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN, -1);
        if (currentScreen > -1) {
            mWorkspace.setCurrentScreen(currentScreen);
        }

        final int addScreen = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);
        if (addScreen > -1) {
            mAddItemCellInfo = new CellLayout.CellInfo();
            final CellLayout.CellInfo addItemCellInfo = mAddItemCellInfo;
            addItemCellInfo.valid = true;
            addItemCellInfo.screen = addScreen;
            addItemCellInfo.cellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            addItemCellInfo.cellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            addItemCellInfo.spanX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_X);
            addItemCellInfo.spanY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y);
            addItemCellInfo.findVacantCellsFromOccupied(
                    savedState.getBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS),
                    savedState.getInt(RUNTIME_STATE_PENDING_ADD_COUNT_X),
                    savedState.getInt(RUNTIME_STATE_PENDING_ADD_COUNT_Y));
            mRestoring = true;
        }

        boolean renameFolder = savedState.getBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
        if (renameFolder) {
            long id = savedState.getLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID);
            mFolderInfo = sModel.getFolderById(this, id);
            mRestoring = true;
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        mDragLayer = (DragLayer) findViewById(R.id.drag_layer);
        final DragLayer dragLayer = mDragLayer;

        mWorkspace = (Workspace) dragLayer.findViewById(R.id.workspace);
        final Workspace workspace = mWorkspace;

        mDrawer = (SlidingDrawer) dragLayer.findViewById(R.id.drawer);
        final SlidingDrawer drawer = mDrawer;

        mAllAppsGrid = (AllAppsGridView) drawer.getContent();
        final AllAppsGridView grid = mAllAppsGrid;

        final DeleteZone deleteZone = (DeleteZone) dragLayer.findViewById(R.id.delete_zone);

        final HandleView handleIcon = (HandleView) drawer.findViewById(R.id.all_apps);
        handleIcon.setLauncher(this);
        mHandleIcon = (TransitionDrawable) handleIcon.getDrawable();
        mHandleIcon.setCrossFadeEnabled(true);

        drawer.lock();
        final DrawerManager drawerManager = new DrawerManager();
        drawer.setOnDrawerOpenListener(drawerManager);
        drawer.setOnDrawerCloseListener(drawerManager);
        drawer.setOnDrawerScrollListener(drawerManager);

        grid.setTextFilterEnabled(true);
        grid.setDragger(dragLayer);
        grid.setLauncher(this);

        workspace.setOnLongClickListener(this);
        workspace.setDragger(dragLayer);
        workspace.setLauncher(this);
        loadWallpaper();

        deleteZone.setLauncher(this);
        deleteZone.setDragController(dragLayer);
        deleteZone.setHandle(handleIcon);

        dragLayer.setIgnoredDropTarget(grid);
        dragLayer.setDragScoller(workspace);
        dragLayer.setDragListener(deleteZone);
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from R.layout.application.
     */
    View createShortcut(ApplicationInfo info) {
        return createShortcut(R.layout.application,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentScreen()), info);
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
    View createShortcut(int layoutResId, ViewGroup parent, ApplicationInfo info) {
        TextView favorite = (TextView) mInflater.inflate(layoutResId, parent, false);

        if (!info.filtered) {
            info.icon = Utilities.createIconThumbnail(info.icon, this);
            info.filtered = true;
        }

        favorite.setCompoundDrawablesWithIntrinsicBounds(null, info.icon, null, null);
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
    void completeAddApplication(Context context, Intent data, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {
        cellInfo.screen = mWorkspace.getCurrentScreen();
        if (!findSingleSlot(cellInfo)) return;

        // Find details for this application
        ComponentName component = data.getComponent();
        PackageManager packageManager = context.getPackageManager();
        ActivityInfo activityInfo = null;
        try {
            activityInfo = packageManager.getActivityInfo(component, 0 /* no flags */);
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, "Couldn't find ActivityInfo for selected application", e);
        }
        
        if (activityInfo != null) {
            ApplicationInfo itemInfo = new ApplicationInfo();
            
            itemInfo.title = activityInfo.loadLabel(packageManager);
            if (itemInfo.title == null) {
                itemInfo.title = activityInfo.name;
            }
            
            itemInfo.setActivity(component, Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            itemInfo.icon = activityInfo.loadIcon(packageManager);
            itemInfo.container = ItemInfo.NO_ID;

            mWorkspace.addApplicationShortcut(itemInfo, cellInfo, insertAtFirst);
        }
    }
    
    /**
     * Add a shortcut to the workspace.
     *
     * @param data The intent describing the shortcut.
     * @param cellInfo The position on screen where to create the shortcut.
     * @param insertAtFirst
     */
    private void completeAddShortcut(Intent data, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {
        cellInfo.screen = mWorkspace.getCurrentScreen();
        if (!findSingleSlot(cellInfo)) return;
        
        final ApplicationInfo info = addShortcut(this, data, cellInfo, false);

        if (!mRestoring) {
            sModel.addDesktopItem(info);

            final View view = createShortcut(info);
            mWorkspace.addInCurrentScreen(view, cellInfo.cellX, cellInfo.cellY, 1, 1, insertAtFirst);
        } else if (sModel.isDesktopLoaded()) {
            sModel.addDesktopItem(info);
        }
    }

    
    /**
     * Add a gadget to the workspace.
     *
     * @param data The intent describing the gadgetId.
     * @param cellInfo The position on screen where to create the shortcut.
     * @param insertAtFirst
     */
    private void completeAddGadget(Intent data, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {

        Bundle extras = data.getExtras();
        int gadgetId = extras.getInt(GadgetManager.EXTRA_GADGET_ID, -1);
        
        Log.d(LOG_TAG, "dumping extras content="+extras.toString());
        
        GadgetProviderInfo gadgetInfo = mGadgetManager.getGadgetInfo(gadgetId);
        
        // Calculate the grid spans needed to fit this gadget
        CellLayout layout = (CellLayout) mWorkspace.getChildAt(cellInfo.screen);
        int[] spans = layout.rectToCell(gadgetInfo.minWidth, gadgetInfo.minHeight);
        
        // Try finding open space on Launcher screen
        final int[] xy = mCellCoordinates;
        if (!findSlot(cellInfo, xy, spans[0], spans[1])) return;

        // Build Launcher-specific Gadget info and save to database
        LauncherGadgetInfo launcherInfo = new LauncherGadgetInfo(gadgetId);
        launcherInfo.spanX = spans[0];
        launcherInfo.spanY = spans[1];
        
        LauncherModel.addItemToDatabase(this, launcherInfo,
                LauncherSettings.Favorites.CONTAINER_DESKTOP,
                mWorkspace.getCurrentScreen(), xy[0], xy[1], false);

        if (!mRestoring) {
            sModel.addDesktopGadget(launcherInfo);
            
            // Perform actual inflation because we're live
            launcherInfo.hostView = mGadgetHost.createView(this, gadgetId, gadgetInfo);
            
            launcherInfo.hostView.setGadget(gadgetId, gadgetInfo);
            launcherInfo.hostView.setTag(launcherInfo);
            
            mWorkspace.addInCurrentScreen(launcherInfo.hostView, xy[0], xy[1],
                    launcherInfo.spanX, launcherInfo.spanY, insertAtFirst);
        } else if (sModel.isDesktopLoaded()) {
            sModel.addDesktopGadget(launcherInfo);
        }
    }
    
    public LauncherGadgetHost getGadgetHost() {
        return mGadgetHost;
    }
    
    static ApplicationInfo addShortcut(Context context, Intent data,
            CellLayout.CellInfo cellInfo, boolean notify) {

        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        Bitmap bitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);

        Drawable icon = null;
        boolean filtered = false;
        boolean customIcon = false;
        Intent.ShortcutIconResource iconResource = null;

        if (bitmap != null) {
            icon = new FastBitmapDrawable(Utilities.createBitmapThumbnail(bitmap, context));
            filtered = true;
            customIcon = true;
        } else {
            Parcelable extra = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            if (extra != null && extra instanceof Intent.ShortcutIconResource) {
                try {
                    iconResource = (Intent.ShortcutIconResource) extra;
                    final PackageManager packageManager = context.getPackageManager();
                    Resources resources = packageManager.getResourcesForApplication(
                            iconResource.packageName);
                    final int id = resources.getIdentifier(iconResource.resourceName, null, null);
                    icon = resources.getDrawable(id);
                } catch (Exception e) {
                    Log.w(LOG_TAG, "Could not load shortcut icon: " + extra);
                }
            }
        }

        if (icon == null) {
            icon = context.getPackageManager().getDefaultActivityIcon();
        }

        final ApplicationInfo info = new ApplicationInfo();
        info.icon = icon;
        info.filtered = filtered;
        info.title = name;
        info.intent = intent;
        info.customIcon = customIcon;
        info.iconResource = iconResource;

        LauncherModel.addItemToDatabase(context, info, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                cellInfo.screen, cellInfo.cellX, cellInfo.cellY, notify);
        return info;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Close the menu
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            getWindow().closeAllPanels();

            try {
                dismissDialog(DIALOG_CREATE_SHORTCUT);
                // Unlock the workspace if the dialog was showing
                mWorkspace.unlock();
            } catch (Exception e) {
                // An exception is thrown if the dialog is not visible, which is fine
            }

            try {
                dismissDialog(DIALOG_RENAME_FOLDER);
                // Unlock the workspace if the dialog was showing
                mWorkspace.unlock();
            } catch (Exception e) {
                // An exception is thrown if the dialog is not visible, which is fine
            }

            // If we are already in front we go back to the default screen,
            // otherwise we don't
            if ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) !=
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) {
                if (!mWorkspace.isDefaultScreenShowing()) {
                    mWorkspace.moveToDefaultScreen();
                }
                closeDrawer();
                View v = getWindow().peekDecorView();
                if (v != null && v.getWindowToken() != null) {
                    InputMethodManager imm = (InputMethodManager)getSystemService(
                            INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            } else {
                closeDrawer(false);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // Do not call super here
        mSavedInstanceState = savedInstanceState;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getCurrentScreen());

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

        if (mDrawer.isOpened()) {
            outState.putBoolean(RUNTIME_STATE_ALL_APPS_FOLDER, true);
        }        

        if (mAddItemCellInfo != null && mAddItemCellInfo.valid && mWaitingForResult) {
            final CellLayout.CellInfo addItemCellInfo = mAddItemCellInfo;
            final CellLayout layout = (CellLayout) mWorkspace.getChildAt(addItemCellInfo.screen);

            outState.putInt(RUNTIME_STATE_PENDING_ADD_SCREEN, addItemCellInfo.screen);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, addItemCellInfo.cellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, addItemCellInfo.cellY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_X, addItemCellInfo.spanX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y, addItemCellInfo.spanY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_COUNT_X, layout.getCountX());
            outState.putInt(RUNTIME_STATE_PENDING_ADD_COUNT_Y, layout.getCountY());
            outState.putBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS,
                   layout.getOccupiedCells());
        }

        if (mFolderInfo != null && mWaitingForResult) {
            outState.putBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, true);
            outState.putLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID, mFolderInfo.id);
        }
    }

    @Override
    public void onDestroy() {
        mDestroyed = true;

        super.onDestroy();
        
        try {
            mGadgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(LOG_TAG, "problem while stopping GadgetHost during Launcher destruction", ex);
        }

        TextKeyListener.getInstance().release();

        mAllAppsGrid.clearTextFilter();
        mAllAppsGrid.setAdapter(null);
        sModel.unbind();
        sModel.abortLoaders();

        getContentResolver().unregisterContentObserver(mObserver);
        unregisterReceiver(mApplicationsReceiver);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        mWaitingForResult = true;
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, 
            Bundle appSearchData, boolean globalSearch) {
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString(SearchManager.SOURCE, "launcher-search");
        }
        super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mDesktopLocked) return false;

        super.onCreateOptionsMenu(menu);
        menu.add(MENU_GROUP_ADD, MENU_ADD, 0, R.string.menu_add)
                .setIcon(android.R.drawable.ic_menu_add)
                .setAlphabeticShortcut('A');
        menu.add(0, MENU_WALLPAPER_SETTINGS, 0, R.string.menu_wallpaper)
                 .setIcon(android.R.drawable.ic_menu_gallery)
                 .setAlphabeticShortcut('W');
        menu.add(0, MENU_SEARCH, 0, R.string.menu_search)
                .setIcon(android.R.drawable.ic_search_category_default)
                .setAlphabeticShortcut(SearchManager.MENU_KEY);
        menu.add(0, MENU_NOTIFICATIONS, 0, R.string.menu_notifications)
                .setIcon(com.android.internal.R.drawable.ic_menu_notifications)
                .setAlphabeticShortcut('N');

        final Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        menu.add(0, MENU_SETTINGS, 0, R.string.menu_settings)
                .setIcon(android.R.drawable.ic_menu_preferences).setAlphabeticShortcut('P')
                .setIntent(settings);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        mMenuAddInfo = mWorkspace.findAllVacantCells(null);
        menu.setGroupEnabled(MENU_GROUP_ADD, mMenuAddInfo != null && mMenuAddInfo.valid);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD:
                addItems();
                return true;
            case MENU_WALLPAPER_SETTINGS:
                startWallpaper();
                return true;
            case MENU_SEARCH:
                if (mWorkspace.snapToSearch()) {
                    closeDrawer(true);          // search gadget: get drawer out of the way
                } else {
                    onSearchRequested();        // no search gadget: use system search UI
                }
                return true;
            case MENU_NOTIFICATIONS:
                showNotifications();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addItems() {
        showAddDialog(mMenuAddInfo);
    }

    private void removeShortcutsForPackage(String packageName) {
        if (packageName != null && packageName.length() > 0) {
            mWorkspace.removeShortcutsForPackage(packageName);
        }
    }
    
    void addGadget(Intent data) {
        // TODO: catch bad gadget exception when sent
        int gadgetId = data.getIntExtra(GadgetManager.EXTRA_GADGET_ID, -1);
        GadgetProviderInfo gadget = mGadgetManager.getGadgetInfo(gadgetId);

        if (gadget.configure != null) {
            // Launch over to configure gadget, if needed
            Intent intent = new Intent(GadgetManager.ACTION_GADGET_CONFIGURE);
            intent.setComponent(gadget.configure);
            intent.putExtra(GadgetManager.EXTRA_GADGET_ID, gadgetId);

            startActivityForResult(intent, REQUEST_CREATE_GADGET);
        } else {
            // Otherwise just add it
            onActivityResult(REQUEST_CREATE_GADGET, Activity.RESULT_OK, data);
        }
    }
    
    void addSearch() {
        final Widget info = Widget.makeSearch();
        final CellLayout.CellInfo cellInfo = mAddItemCellInfo;
        
        final int[] xy = mCellCoordinates;
        final int spanX = info.spanX;
        final int spanY = info.spanY;
    
        if (!findSlot(cellInfo, xy, spanX, spanY)) return;
    
        sModel.addDesktopItem(info);
        LauncherModel.addItemToDatabase(this, info, LauncherSettings.Favorites.CONTAINER_DESKTOP,
        mWorkspace.getCurrentScreen(), xy[0], xy[1], false);
    
        final View view = mInflater.inflate(info.layoutResource, null);
        view.setTag(info);
    
        mWorkspace.addInCurrentScreen(view, xy[0], xy[1], info.spanX, spanY);
    }

    void addShortcut(Intent intent) {
        startActivityForResult(intent, REQUEST_CREATE_SHORTCUT);
    }

    void addLiveFolder(Intent intent) {
        startActivityForResult(intent, REQUEST_CREATE_LIVE_FOLDER);
    }

    void addFolder(boolean insertAtFirst) {
        UserFolderInfo folderInfo = new UserFolderInfo();
        folderInfo.title = getText(R.string.folder_name);

        CellLayout.CellInfo cellInfo = mAddItemCellInfo;
        cellInfo.screen = mWorkspace.getCurrentScreen();
        if (!findSingleSlot(cellInfo)) return;

        // Update the model
        LauncherModel.addItemToDatabase(this, folderInfo, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                mWorkspace.getCurrentScreen(), cellInfo.cellX, cellInfo.cellY, false);
        sModel.addDesktopItem(folderInfo);
        sModel.addFolder(folderInfo);

        // Create the view
        FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentScreen()), folderInfo);
        mWorkspace.addInCurrentScreen(newFolder,
                cellInfo.cellX, cellInfo.cellY, 1, 1, insertAtFirst);
    }
    
    private void completeAddLiveFolder(Intent data, CellLayout.CellInfo cellInfo,
            boolean insertAtFirst) {
        cellInfo.screen = mWorkspace.getCurrentScreen();
        if (!findSingleSlot(cellInfo)) return;

        final LiveFolderInfo info = addLiveFolder(this, data, cellInfo, false);

        if (!mRestoring) {
            sModel.addDesktopItem(info);

            final View view = LiveFolderIcon.fromXml(R.layout.live_folder_icon, this,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentScreen()), info);
            mWorkspace.addInCurrentScreen(view, cellInfo.cellX, cellInfo.cellY, 1, 1, insertAtFirst);
        } else if (sModel.isDesktopLoaded()) {
            sModel.addDesktopItem(info);
        }
    }

    static LiveFolderInfo addLiveFolder(Context context, Intent data,
            CellLayout.CellInfo cellInfo, boolean notify) {

        Intent baseIntent = data.getParcelableExtra(LiveFolders.EXTRA_LIVE_FOLDER_BASE_INTENT);
        String name = data.getStringExtra(LiveFolders.EXTRA_LIVE_FOLDER_NAME);

        Drawable icon = null;
        boolean filtered = false;
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
                Log.w(LOG_TAG, "Could not load live folder icon: " + extra);
            }
        }

        if (icon == null) {
            icon = context.getResources().getDrawable(R.drawable.ic_launcher_folder);
        }

        final LiveFolderInfo info = new LiveFolderInfo();
        info.icon = icon;
        info.filtered = filtered;
        info.title = name;
        info.iconResource = iconResource;
        info.uri = data.getData();
        info.baseIntent = baseIntent;
        info.displayMode = data.getIntExtra(LiveFolders.EXTRA_LIVE_FOLDER_DISPLAY_MODE,
                LiveFolders.DISPLAY_MODE_GRID);

        LauncherModel.addItemToDatabase(context, info, LauncherSettings.Favorites.CONTAINER_DESKTOP,
                cellInfo.screen, cellInfo.cellX, cellInfo.cellY, notify);
        sModel.addFolder(info);

        return info;
    }

    private boolean findSingleSlot(CellLayout.CellInfo cellInfo) {
        final int[] xy = new int[2];
        if (findSlot(cellInfo, xy, 1, 1)) {
            cellInfo.cellX = xy[0];
            cellInfo.cellY = xy[1];
            return true;
        }
        return false;
    }

    private boolean findSlot(CellLayout.CellInfo cellInfo, int[] xy, int spanX, int spanY) {
        if (!cellInfo.findCellForSpan(xy, spanX, spanY)) {
            boolean[] occupied = mSavedState != null ?
                    mSavedState.getBooleanArray(RUNTIME_STATE_PENDING_ADD_OCCUPIED_CELLS) : null;
            cellInfo = mWorkspace.findAllVacantCells(occupied);
            if (!cellInfo.findCellForSpan(xy, spanX, spanY)) {
                Toast.makeText(this, getString(R.string.out_of_space), Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void showNotifications() {
        final StatusBarManager statusBar = (StatusBarManager) getSystemService(STATUS_BAR_SERVICE);
        if (statusBar != null) {
            statusBar.expand();
        }
    }

    private void startWallpaper() {
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(pickWallpaper, getString(R.string.chooser_wallpaper)));
    }

    /**
     * Registers various intent receivers. The current implementation registers
     * only a wallpaper intent receiver to let other applications change the
     * wallpaper.
     */
    private void registerIntentReceivers() {
        if (sWallpaperReceiver == null) {
            final Application application = getApplication();

            sWallpaperReceiver = new WallpaperIntentReceiver(application, this);

            IntentFilter filter = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
            application.registerReceiver(sWallpaperReceiver, filter);
        } else {
            sWallpaperReceiver.setLauncher(this);
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(mApplicationsReceiver, filter);
    }

    /**
     * Registers various content observers. The current implementation registers
     * only a favorites observer to keep track of the favorites applications.
     */
    private void registerContentObservers() {
        ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(LauncherSettings.Favorites.CONTENT_URI, true, mObserver);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK:
                    mWorkspace.dispatchKeyEvent(event); 
                    closeFolder();
                    closeDrawer();
                    return true;
                case KeyEvent.KEYCODE_HOME:
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void closeDrawer() {
        closeDrawer(true);
    }

    private void closeDrawer(boolean animated) {
        if (mDrawer.isOpened()) {
            if (animated) {
                mDrawer.animateClose();
            } else {
                mDrawer.close();
            }
            if (mDrawer.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentScreen()).requestFocus();
            }
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
            parent.removeView(folder);
        }
        folder.onClose();
    }

    /**
     * When the notification that favorites have changed is received, requests
     * a favorites list refresh.
     */
    private void onFavoritesChanged() {
        mDesktopLocked = true;
        mDrawer.lock();
        sModel.loadUserItems(false, this, false, false);
    }

    void onDesktopItemsLoaded() {
        if (mDestroyed) return;

        mAllAppsGrid.setAdapter(Launcher.getModel().getApplicationsAdapter());
        bindDesktopItems();
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     */
    private void bindDesktopItems() {
        final ArrayList<ItemInfo> shortcuts = sModel.getDesktopItems();
        final ArrayList<LauncherGadgetInfo> gadgets = sModel.getDesktopGadgets();
        if (shortcuts == null || gadgets == null) {
            return;
        }

        final Workspace workspace = mWorkspace;
        int count = workspace.getChildCount();
        for (int i = 0; i < count; i++) {
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

        final DesktopBinder binder = new DesktopBinder(this, shortcuts, gadgets);
        binder.startBindingItems();
    }

    private void bindItems(Launcher.DesktopBinder binder,
            ArrayList<ItemInfo> shortcuts, int start, int count) {

        final Workspace workspace = mWorkspace;
        final boolean desktopLocked = mDesktopLocked;

        final int end = Math.min(start + DesktopBinder.ITEMS_COUNT, count);
        int i = start;

        for ( ; i < end; i++) {
            final ItemInfo item = shortcuts.get(i);
            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    final View shortcut = createShortcut((ApplicationInfo) item);
                    workspace.addInScreen(shortcut, item.screen, item.cellX, item.cellY, 1, 1,
                            !desktopLocked);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_USER_FOLDER:
                    final FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentScreen()),
                            (UserFolderInfo) item);
                    workspace.addInScreen(newFolder, item.screen, item.cellX, item.cellY, 1, 1,
                            !desktopLocked);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_LIVE_FOLDER:
                    final FolderIcon newLiveFolder = LiveFolderIcon.fromXml(
                            R.layout.live_folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentScreen()),
                            (LiveFolderInfo) item);
                    workspace.addInScreen(newLiveFolder, item.screen, item.cellX, item.cellY, 1, 1,
                            !desktopLocked);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_WIDGET_SEARCH:
                    final int screen = workspace.getCurrentScreen();
                    final View view = mInflater.inflate(R.layout.widget_search,
                            (ViewGroup) workspace.getChildAt(screen), false);
                    
                    final Widget widget = (Widget) item;
                    view.setTag(widget);
                    
                    workspace.addWidget(view, widget, !desktopLocked);
                    break;
            }
        }

        workspace.requestLayout();

        if (end >= count) {
            finishBindDesktopItems();
            binder.startBindingGadgets();
        } else {
            binder.obtainMessage(DesktopBinder.MESSAGE_BIND_ITEMS, i, count).sendToTarget();
        }
    }

    private void finishBindDesktopItems() {
        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentScreen()).requestFocus();
            }

            final long[] userFolders = mSavedState.getLongArray(RUNTIME_STATE_USER_FOLDERS);
            if (userFolders != null) {
                for (long folderId : userFolders) {
                    final FolderInfo info = sModel.findFolderById(folderId);
                    if (info != null) {
                        openFolder(info);
                    }
                }
                final Folder openFolder = mWorkspace.getOpenFolder();
                if (openFolder != null) {
                    openFolder.requestFocus();
                }
            }

            final boolean allApps = mSavedState.getBoolean(RUNTIME_STATE_ALL_APPS_FOLDER, false);
            if (allApps) {
                mDrawer.open();
            }

            mSavedState = null;
        }

        if (mSavedInstanceState != null) {
            super.onRestoreInstanceState(mSavedInstanceState);
            mSavedInstanceState = null;
        }

        if (mDrawer.isOpened() && !mDrawer.hasFocus()) {
            mDrawer.requestFocus();
        }

        mDesktopLocked = false;
        mDrawer.unlock();
    }

    private void bindGadgets(Launcher.DesktopBinder binder,
            ArrayList<LauncherGadgetInfo> gadgets, int start, int count) {

        final Workspace workspace = mWorkspace;
        final boolean desktopLocked = mDesktopLocked;

        final int end = Math.min(start + DesktopBinder.GADGETS_COUNT, count);
        int i = start;

        for ( ; i < end; i++) {
            final LauncherGadgetInfo item = gadgets.get(i);
                    
            final int gadgetId = item.gadgetId;
            final GadgetProviderInfo gadgetInfo = mGadgetManager.getGadgetInfo(gadgetId);
            item.hostView = mGadgetHost.createView(this, gadgetId, gadgetInfo);
            
            if (LOGD) Log.d(LOG_TAG, String.format("about to setGadget for id=%d, info=%s", gadgetId, gadgetInfo));
            
            item.hostView.setGadget(gadgetId, gadgetInfo);
            item.hostView.setTag(item);
            
            workspace.addInScreen(item.hostView, item.screen, item.cellX,
                    item.cellY, item.spanX, item.spanY, !desktopLocked);
        }

        workspace.requestLayout();

        if (end >= count) {
            finishBindDesktopGadgets();
        } else {
            binder.obtainMessage(DesktopBinder.MESSAGE_BIND_GADGETS, i, count).sendToTarget();
        }
    }
    
    private void finishBindDesktopGadgets() {
    }
    
    DragController getDragController() {
        return mDragLayer;
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof ApplicationInfo) {
            // Open shortcut
            final Intent intent = ((ApplicationInfo) tag).intent;
            startActivitySafely(intent);
        } else if (tag instanceof FolderInfo) {
            handleFolderClick((FolderInfo) tag);
        }
    }

    void startActivitySafely(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(LOG_TAG, "Launcher does not have the permission to launch " + intent +
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
                folderScreen = mWorkspace.getScreenForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentScreen()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderInfo);
                }
            }
        }
    }

    private void loadWallpaper() {
        // The first time the application is started, we load the wallpaper from
        // the ApplicationContext
        if (sWallpaper == null) {
            final Drawable drawable = getWallpaper();
            if (drawable instanceof BitmapDrawable) {
                sWallpaper = ((BitmapDrawable) drawable).getBitmap();
            } else {
                throw new IllegalStateException("The wallpaper must be a BitmapDrawable.");
            }
        }
        mWorkspace.loadWallpaper(sWallpaper);
    }

    /**
     * Opens the user fodler described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderInfo The FolderInfo describing the folder to open.
     */
    private void openFolder(FolderInfo folderInfo) {
        Folder openFolder;

        if (folderInfo instanceof UserFolderInfo) {
            openFolder = UserFolder.fromXml(this);
        } else if (folderInfo instanceof LiveFolderInfo) {
            openFolder = com.android.launcher.LiveFolder.fromXml(this, folderInfo);
        } else {
            return;
        }

        openFolder.setDragger(mDragLayer);
        openFolder.setLauncher(this);

        openFolder.bind(folderInfo);
        folderInfo.opened = true;

        mWorkspace.addInScreen(openFolder, folderInfo.screen, 0, 0, 4, 4);
        openFolder.onOpen();
    }

    /**
     * Returns true if the workspace is being loaded. When the workspace is loading,
     * no user interaction should be allowed to avoid any conflict.
     *
     * @return True if the workspace is locked, false otherwise.
     */
    boolean isWorkspaceLocked() {
        return mDesktopLocked;
    }

    public boolean onLongClick(View v) {
        if (mDesktopLocked) {
            return false;
        }

        if (!(v instanceof CellLayout)) {
            v = (View) v.getParent();
        }

        CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) v.getTag();

        // This happens when long clicking an item with the dpad/trackball
        if (cellInfo == null) {
            return true;
        }

        if (mWorkspace.allowLongPress()) {
            if (cellInfo.cell == null) {
                if (cellInfo.valid) {
                    // User long pressed on empty space
                    showAddDialog(cellInfo);
                }
            } else {
                if (!(cellInfo.cell instanceof Folder)) {
                    // User long pressed on an item
                    mWorkspace.startDrag(cellInfo);
                }
            }
        }
        return true;
    }

    static LauncherModel getModel() {
        return sModel;
    }

    void closeAllApplications() {
        mDrawer.close();
    }

    boolean isDrawerDown() {
        return !mDrawer.isMoving() && !mDrawer.isOpened();
    }

    boolean isDrawerUp() {
        return mDrawer.isOpened() && !mDrawer.isMoving();
    }

    Workspace getWorkspace() {
        return mWorkspace;
    }

    GridView getApplicationsGrid() {
        return mAllAppsGrid;
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
                mWorkspace.lock();
                break;
            case DIALOG_RENAME_FOLDER:
                mWorkspace.lock();
                EditText input = (EditText) dialog.findViewById(R.id.folder_name);
                final CharSequence text = mFolderInfo.title;
                input.setText(text);
                input.setSelection(0, text.length());                
                break;
        }
    }

    void showRenameDialog(FolderInfo info) {
        mFolderInfo = info;
        mWaitingForResult = true;
        showDialog(DIALOG_RENAME_FOLDER);
    }

    private void showAddDialog(CellLayout.CellInfo cellInfo) {
        mAddItemCellInfo = cellInfo;
        mWaitingForResult = true;
        showDialog(DIALOG_CREATE_SHORTCUT);
    }

    private class RenameFolder {
        private EditText mInput;

        Dialog createDialog() {
            mWaitingForResult = true;
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
            return builder.create();
        }

        private void changeFolderName() {
            final String name = mInput.getText().toString();
            if (!TextUtils.isEmpty(name)) {
                // Make sure we have the right folder info
                mFolderInfo = sModel.findFolderById(mFolderInfo.id);
                mFolderInfo.title = name;
                LauncherModel.updateItemInDatabase(Launcher.this, mFolderInfo);

                if (mDesktopLocked) {
                    mDrawer.lock();
                    sModel.loadUserItems(false, Launcher.this, false, false);
                } else {
                    final FolderIcon folderIcon = (FolderIcon)
                            mWorkspace.getViewForTag(mFolderInfo);
                    if (folderIcon != null) {
                        folderIcon.setText(name);
                        getWorkspace().requestLayout();
                    } else {
                        mDesktopLocked = true;
                        mDrawer.lock();
                        sModel.loadUserItems(false, Launcher.this, false, false);
                    }
                }
            }
            cleanup();
        }

        private void cleanup() {
            mWorkspace.unlock();
            dismissDialog(DIALOG_RENAME_FOLDER);
            mWaitingForResult = false;
            mFolderInfo = null;
        }
    }

    /**
     * Displays the shortcut creation dialog and launches, if necessary, the
     * appropriate activity.
     */
    private class CreateShortcut implements AdapterView.OnItemClickListener,
            DialogInterface.OnCancelListener {
        private AddAdapter mAdapter;
        private ListView mList;
        
        Dialog createDialog() {
            mWaitingForResult = true;
            
            mAdapter = new AddAdapter(Launcher.this);
            
            final AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
            builder.setTitle(getString(R.string.menu_item_add_item));
            builder.setIcon(0);

            mList = (ListView)
                    View.inflate(Launcher.this, R.layout.create_shortcut_list, null);
            mList.setAdapter(mAdapter);
            mList.setOnItemClickListener(this);
            builder.setView(mList);
            builder.setInverseBackgroundForced(true);

            AlertDialog dialog = builder.create();
            dialog.setOnCancelListener(this);

            WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
            attributes.gravity = Gravity.TOP;
            dialog.onWindowAttributesChanged(attributes);

            return dialog;
        }

        public void onCancel(DialogInterface dialog) {
            mWaitingForResult = false;
            cleanup();
        }

        private void cleanup() {
            mWorkspace.unlock();
            dismissDialog(DIALOG_CREATE_SHORTCUT);
        }

        public void onItemClick(AdapterView parent, View view, int position, long id) {
            // handle which item was clicked based on position
            // this will launch off pick intent
            
            Object tag = view.getTag();
            if (tag instanceof AddAdapter.ListItem) {
                AddAdapter.ListItem item = (AddAdapter.ListItem) tag;
                cleanup();
                switch (item.actionTag) {
                    case AddAdapter.ITEM_APPLICATION: {
                        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                        pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
                        startActivityForResult(pickIntent, REQUEST_PICK_APPLICATION);
                        break;
                    }

                    case AddAdapter.ITEM_SHORTCUT: {
                        Intent shortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);

                        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                        pickIntent.putExtra(Intent.EXTRA_INTENT, shortcutIntent);
                        pickIntent.putExtra(Intent.EXTRA_TITLE,
                                getText(R.string.title_select_shortcut));
                        startActivityForResult(pickIntent, REQUEST_PICK_SHORTCUT);
                        break;
                    }
                    
                    case AddAdapter.ITEM_SEARCH: {
                        addSearch();
                        break;
                    }
                    
                    case AddAdapter.ITEM_GADGET: {
                        int gadgetId = Launcher.this.mGadgetHost.allocateGadgetId();
                        
                        Intent pickIntent = new Intent(GadgetManager.ACTION_GADGET_PICK);
                        pickIntent.putExtra(GadgetManager.EXTRA_GADGET_ID, gadgetId);
                        startActivityForResult(pickIntent, REQUEST_PICK_GADGET);
                        break;
                    }
                    
                    case AddAdapter.ITEM_LIVE_FOLDER: {
                        Intent liveFolderIntent = new Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER);

                        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
                        pickIntent.putExtra(Intent.EXTRA_INTENT, liveFolderIntent);
                        pickIntent.putExtra(Intent.EXTRA_TITLE,
                                getText(R.string.title_select_live_folder));
                        startActivityForResult(pickIntent, REQUEST_PICK_LIVE_FOLDER);
                        break;
                    }

                    case AddAdapter.ITEM_FOLDER: {
                        addFolder(!mDesktopLocked);
                        dismissDialog(DIALOG_CREATE_SHORTCUT);
                        break;
                    }

                    case AddAdapter.ITEM_WALLPAPER: {
                        startWallpaper();
                        break;
                    }

                }                
                
            }
        }
    }

    /**
     * Receives notifications when applications are added/removed.
     */
    private class ApplicationsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean reloadWorkspace = false;
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    removeShortcutsForPackage(intent.getData().getSchemeSpecificPart());
                } else {
                    reloadWorkspace = true;
                }
            }
            removeDialog(DIALOG_CREATE_SHORTCUT);
            if (!reloadWorkspace) {
                sModel.loadApplications(false, Launcher.this, false);
            } else {
                sModel.loadUserItems(false, Launcher.this, false, true);
            }
        }
    }

    /**
     * Receives notifications whenever the user favorites have changed.
     */
    private class FavoritesChangeObserver extends ContentObserver {
        public FavoritesChangeObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            onFavoritesChanged();
        }
    }

    /**
     * Receives intents from other applications to change the wallpaper.
     */
    private static class WallpaperIntentReceiver extends BroadcastReceiver {
        private final Application mApplication;
        private WeakReference<Launcher> mLauncher;

        WallpaperIntentReceiver(Application application, Launcher launcher) {
            mApplication = application;
            setLauncher(launcher);
        }

        void setLauncher(Launcher launcher) {
            mLauncher = new WeakReference<Launcher>(launcher);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // Load the wallpaper from the ApplicationContext and store it locally
            // until the Launcher Activity is ready to use it
            final Drawable drawable = mApplication.getWallpaper();
            if (drawable instanceof BitmapDrawable) {
                sWallpaper = ((BitmapDrawable) drawable).getBitmap();
            } else {
                throw new IllegalStateException("The wallpaper must be a BitmapDrawable.");
            }

            // If Launcher is alive, notify we have a new wallpaper
            if (mLauncher != null) {
                final Launcher launcher = mLauncher.get();
                if (launcher != null) {
                    launcher.loadWallpaper();
                }
            }
        }
    }

    private class DrawerManager implements SlidingDrawer.OnDrawerOpenListener,
            SlidingDrawer.OnDrawerCloseListener, SlidingDrawer.OnDrawerScrollListener {
        private boolean mOpen;

        public void onDrawerOpened() {
            if (!mOpen) {
                mHandleIcon.reverseTransition(150);
                final Rect bounds = mWorkspace.mDrawerBounds;

                View view = mAllAppsGrid;
                view.getDrawingRect(bounds);

                while (view != mDragLayer) {
                    bounds.offset(view.getLeft(), view.getTop());
                    view = (View) view.getParent();
                }

                mOpen = true;
            }
        }

        public void onDrawerClosed() {
            if (mOpen) {
                mHandleIcon.reverseTransition(150);
                mWorkspace.mDrawerBounds.setEmpty();
                mOpen = false;
            }
            mAllAppsGrid.setSelection(0);
            mAllAppsGrid.clearTextFilter();
        }

        public void onScrollStarted() {
        }

        public void onScrollEnded() {
        }
    }

    private static class DesktopBinder extends Handler {
        static final int MESSAGE_BIND_ITEMS = 0x1;
        static final int MESSAGE_BIND_GADGETS = 0x2;
        // Number of items to bind in every pass
        static final int ITEMS_COUNT = 6;
        static final int GADGETS_COUNT = 1;

        private final ArrayList<ItemInfo> mShortcuts;
        private final ArrayList<LauncherGadgetInfo> mGadgets;
        private final WeakReference<Launcher> mLauncher;

        DesktopBinder(Launcher launcher, ArrayList<ItemInfo> shortcuts,
                ArrayList<LauncherGadgetInfo> gadgets) {

            mLauncher = new WeakReference<Launcher>(launcher);
            mShortcuts = shortcuts;
            mGadgets = gadgets;
        }
        
        public void startBindingItems() {
            obtainMessage(MESSAGE_BIND_ITEMS, 0, mShortcuts.size()).sendToTarget();
        }

        public void startBindingGadgets() {
            obtainMessage(MESSAGE_BIND_GADGETS, 0, mGadgets.size()).sendToTarget();
        }
        
        @Override
        public void handleMessage(Message msg) {
            Launcher launcher = mLauncher.get();
            if (launcher == null) {
                return;
            }
            
            switch (msg.what) {
                case MESSAGE_BIND_ITEMS: {
                    launcher.bindItems(this, mShortcuts, msg.arg1, msg.arg2);
                    break;
                }
                case MESSAGE_BIND_GADGETS: {
                    launcher.bindGadgets(this, mGadgets, msg.arg1, msg.arg2);
                    break;
                }
            }
        }
    }
}
