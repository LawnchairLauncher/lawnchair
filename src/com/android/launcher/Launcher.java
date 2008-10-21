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
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Contacts;
import android.telephony.PhoneNumberUtils;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Config;
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
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.IWallpaperService;

import com.android.internal.provider.Settings;
import com.android.internal.widget.SlidingDrawer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Default launcher application.
 */
public final class Launcher extends Activity implements View.OnClickListener, OnLongClickListener {
    static final String LOG_TAG = "Launcher";

    private static final boolean PROFILE_STARTUP = false;
    private static final boolean DEBUG_USER_INTERFACE = false;

    private static final String USE_OPENGL_BY_DEFAULT = "false";

    private static final boolean REMOVE_SHORTCUT_ON_PACKAGE_REMOVE = false;    

    // Type: boolean
    private static final String PROPERTY_USE_OPENGL = "launcher.opengl";
    // Type: boolean
    private static final String PROPERTY_USE_SENSORS = "launcher.sensors";

    private static final boolean USE_OPENGL = true;
    private static final boolean USE_SENSORS = false;

    private static final int WALLPAPER_SCREENS_SPAN = 2;

    private static final int MENU_GROUP_ADD = 1;
    private static final int MENU_ADD = Menu.FIRST + 1;
    private static final int MENU_WALLPAPER_SETTINGS = MENU_ADD + 1;
    private static final int MENU_SEARCH = MENU_WALLPAPER_SETTINGS + 1;
    private static final int MENU_NOTIFICATIONS = MENU_SEARCH + 1;
    private static final int MENU_SETTINGS = MENU_NOTIFICATIONS + 1;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CHOOSE_PHOTO = 2;
    private static final int REQUEST_UPDATE_PHOTO = 3;

    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

    static final int DEFAULT_SCREN = 1;
    static final int NUMBER_CELLS_X = 4;
    static final int NUMBER_CELLS_Y = 4;    

    private static final int DIALOG_CREATE_SHORTCUT = 1;
    static final int DIALOG_RENAME_FOLDER = 2;    

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

    // Indicates whether the OpenGL pipeline was enabled, either through
    // USE_OPENGL_BY_DEFAULT or the system property launcher.opengl
    static boolean sOpenGlEnabled;

    private static final Object sLock = new Object();
    private static int sScreen = DEFAULT_SCREN;

    private static WallpaperIntentReceiver sWallpaperReceiver;

    private final BroadcastReceiver mApplicationsReceiver = new ApplicationsIntentReceiver();
    private final ContentObserver mObserver = new FavoritesChangeObserver();

    private final Handler mHandler = new Handler();
    private LayoutInflater mInflater;

    private SensorManager mSensorManager;
    private SensorHandler mSensorHandler;

    private DragLayer mDragLayer;
    private Workspace mWorkspace;

    private CellLayout.CellInfo mAddItemCellInfo;
    private CellLayout.CellInfo mMenuAddInfo;
    private final int[] mCellCoordinates = new int[2];
    private UserFolderInfo mFolderInfo;

    private SlidingDrawer mDrawer;
    private TransitionDrawable mHandleIcon;
    private AllAppsGridView mAllAppsGrid;

    private boolean mDesktopLocked = true;
    private Bundle mSavedState;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mDestroyed;

    private boolean mRestoring;
    private boolean mWaitingForResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        dalvik.system.VMRuntime.getRuntime().setMinimumHeapSize(4 * 1024 * 1024);

        super.onCreate(savedInstanceState);
        mInflater = getLayoutInflater();

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing("/sdcard/launcher");
        }

        setWallpaperDimension();

        enableSensors();
        enableOpenGL();

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
        sModel.loadApplications(true, this);
        sModel.loadUserItems(true, this);
        mRestoring = false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // When MMC/MNC changes, so can applications, so we reload them
        sModel.loadApplications(false, Launcher.this);
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

    private void enableSensors() {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (USE_SENSORS || "true".equals(SystemProperties.get(PROPERTY_USE_SENSORS, "false"))) {
            if (Config.LOGD) {
                Log.d(LOG_TAG, "Launcher activating sensors");
            }
            mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSensorHandler = new SensorHandler();
        }
    }

    private void enableOpenGL() {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (USE_OPENGL && "true".equals(SystemProperties.get(PROPERTY_USE_OPENGL,
                USE_OPENGL_BY_DEFAULT))) {
            if (Config.LOGD) {
                Log.d(LOG_TAG, "Launcher starting in OpenGL");
            }
            //requestWindowFeature(Window.FEATURE_OPENGL);
            //sOpenGlEnabled = true;
        } else {
            sOpenGlEnabled = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && mAddItemCellInfo != null) {
            switch (requestCode) {
                case REQUEST_CREATE_SHORTCUT:
                    completeAddShortcut(data, mAddItemCellInfo, !mDesktopLocked);
                    break;
                case REQUEST_CHOOSE_PHOTO:
                    completeAddPhotoFrame(data, mAddItemCellInfo);
                    break;
                case REQUEST_UPDATE_PHOTO:
                    completeUpdatePhotoFrame(data, mAddItemCellInfo);
                    break;
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

        if (mSensorManager != null) {
            mSensorManager.registerListener(mSensorHandler, SensorManager.SENSOR_ACCELEROMETER);
        }
    }

    @Override
    protected void onStop() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mSensorHandler);
        }

        super.onStop();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean handled = super.onKeyUp(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            handled = mWorkspace.snapToSearch();
        }
        return handled;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = super.onKeyDown(keyCode, event);
        if (!handled && keyCode != KeyEvent.KEYCODE_ENTER) {
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

        final ImageView handleIcon = (ImageView) drawer.findViewById(R.id.all_apps);
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
        if (sOpenGlEnabled) {
            grid.setScrollingCacheEnabled(false);
            grid.setFadingEdgeLength(0);
        }

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

    void addApplicationShortcut(ApplicationInfo info) {
        mAddItemCellInfo.screen = mWorkspace.getCurrentScreen();
        mWorkspace.addApplicationShortcut(info, mAddItemCellInfo);
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
        final ApplicationInfo info = addShortcut(this, data, cellInfo, false);

        if (!mRestoring) {
            sModel.addDesktopItem(info);

            final View view = createShortcut(info);
            mWorkspace.addInCurrentScreen(view, cellInfo.cellX, cellInfo.cellY, 1, 1, insertAtFirst);
        } else if (sModel.isDesktopLoaded()) {
            sModel.addDesktopItem(info);
        }
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
            icon = new BitmapDrawable(Utilities.createBitmapThumbnail(bitmap, context));
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

        LauncherModel.addItemToDatabase(context, info, Settings.Favorites.CONTAINER_DESKTOP,
                cellInfo.screen, cellInfo.cellX, cellInfo.cellY, notify);
        return info;
    }

    /**
     * Add a PhotFrame to the workspace.
     *
     * @param data The intent describing the photo.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    private void completeAddPhotoFrame(Intent data, CellLayout.CellInfo cellInfo) {
        final Bundle extras = data.getExtras();
        if (extras != null) {
            Bitmap photo = extras.getParcelable("data");

            Widget info = Widget.makePhotoFrame();
            info.photo = photo;

            final int[] xy = mCellCoordinates;
            if (!findSlot(cellInfo, xy, info.spanX, info.spanY)) return;

            LauncherModel.addItemToDatabase(this, info, Settings.Favorites.CONTAINER_DESKTOP,
                    mWorkspace.getCurrentScreen(), xy[0], xy[1], false);

            if (!mRestoring) {
                sModel.addDesktopItem(info);

                final PhotoFrame view = (PhotoFrame) mInflater.inflate(info.layoutResource, null);
                view.setImageBitmap(photo);
                view.setTag(info);

                mWorkspace.addInCurrentScreen(view, xy[0], xy[1], info.spanX, info.spanY);
            } else if (sModel.isDesktopLoaded()) {
                sModel.addDesktopItem(info);
            }
        }
    }

    /**
     * Updates a workspace PhotoFrame.
     *
     * @param data The intent describing the photo.
     * @param cellInfo The position on screen of the PhotoFrame to update.
     */
    private void completeUpdatePhotoFrame(Intent data, CellLayout.CellInfo cellInfo) {
        final Bundle extras = data.getExtras();
        if (extras != null) {
            Widget info;
            Bitmap photo = extras.getParcelable("data");

            if (!mRestoring) {
                final CellLayout layout = (CellLayout) mWorkspace.getChildAt(cellInfo.screen);
                final PhotoFrame view = (PhotoFrame) layout.findCell(cellInfo.cellX, cellInfo.cellY,
                        cellInfo.spanX, cellInfo.spanY, null);
                view.setImageBitmap(photo);
                info = (Widget) view.getTag();
            } else {
                info = LauncherModel.getPhotoFrameInfo(this, cellInfo.screen,
                        cellInfo.cellX, cellInfo.cellY);
            }

            info.photo = photo;
            LauncherModel.updateItemInDatabase(this, info);
        }
    }

    /**
     * Starts a new Intent to let the user update the PhotoFrame defined by the
     * specified Widget.
     *
     * @param widget The Widget info defining which PhotoFrame to update.
     */
    void updatePhotoFrame(Widget widget) {
        CellLayout.CellInfo info = new CellLayout.CellInfo();
        info.screen = widget.screen;
        info.cellX = widget.cellX;
        info.cellY = widget.cellY;
        info.spanX = widget.spanX;
        info.spanY = widget.spanY;
        mAddItemCellInfo = info;

        startActivityForResult(createPhotoPickIntent(), Launcher.REQUEST_UPDATE_PHOTO);
    }

    /**
     * Creates an Intent used to let the user pick a photo for a PhotoFrame.
     *
     * @return The Intent to pick a photo suited for a PhotoFrame.
     */
    private static Intent createPhotoPickIntent() {
        // TODO: Move this method to PhotoFrame?
        // TODO: get these values from constants somewhere
        // TODO: Adjust the PhotoFrame's image size to avoid on the fly scaling
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 192);
        intent.putExtra("outputY", 192);
        intent.putExtra("noFaceDetection", true);
        intent.putExtra("return-data", true);
        return intent;
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
            } else {
                closeDrawer(false);
            }
        }
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
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mDesktopLocked) return false;

        super.onCreateOptionsMenu(menu);
        menu.add(MENU_GROUP_ADD, MENU_ADD, 0, R.string.menu_add)
                .setIcon(android.R.drawable.ic_menu_add)
                .setAlphabeticShortcut('A');
        menu.add(0, MENU_WALLPAPER_SETTINGS, 0, R.string.menu_wallpaper)
                 .setIcon(R.drawable.ic_menu_gallery)
                 .setAlphabeticShortcut('W');
        menu.add(0, MENU_SEARCH, 0, R.string.menu_search)
                .setIcon(android.R.drawable.ic_search_category_default)
                .setAlphabeticShortcut(SearchManager.MENU_KEY);
        menu.add(0, MENU_NOTIFICATIONS, 0, R.string.menu_notifications)
                .setIcon(R.drawable.ic_menu_notifications)
                .setAlphabeticShortcut('N');

        final Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        menu.add(0, MENU_SETTINGS, 0, R.string.menu_settings)
                .setIcon(R.drawable.ic_menu_preferences).setAlphabeticShortcut('P')
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
                if (!mWorkspace.snapToSearch()) {
                    onSearchRequested();
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
            android.util.Log.d(LOG_TAG, packageName);
            mWorkspace.removeShortcutsForPackage(packageName);
        }
    }

    void addShortcut(Intent intent) {
        startActivityForResult(intent, REQUEST_CREATE_SHORTCUT);
    }

    void addFolder() {
        UserFolderInfo folderInfo = new UserFolderInfo();
        folderInfo.title = getText(R.string.folder_name);
        int cellX = mAddItemCellInfo.cellX;
        int cellY = mAddItemCellInfo.cellY;

        // Update the model
        LauncherModel.addItemToDatabase(this, folderInfo, Settings.Favorites.CONTAINER_DESKTOP,
                mWorkspace.getCurrentScreen(), cellX, cellY, false);
        sModel.addDesktopItem(folderInfo);
        sModel.addUserFolder(folderInfo);

        // Create the view
        FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentScreen()), folderInfo);
        mWorkspace.addInCurrentScreen(newFolder, cellX, cellY, 1, 1);
    }

    void getPhotoForPhotoFrame() {
        startActivityForResult(createPhotoPickIntent(), REQUEST_CHOOSE_PHOTO);
    }

    void addClock() {
        final Widget info = Widget.makeClock();
        addWidget(info);
    }

    void addSearch() {
        final Widget info = Widget.makeSearch();
        addWidget(info);
    }
    
    private void addWidget(final Widget info) {
        final CellLayout.CellInfo cellInfo = mAddItemCellInfo;

        final int[] xy = mCellCoordinates;
        final int spanX = info.spanX;
        final int spanY = info.spanY;

        if (!findSlot(cellInfo, xy, spanX, spanY)) return;

        sModel.addDesktopItem(info);
        LauncherModel.addItemToDatabase(this, info, Settings.Favorites.CONTAINER_DESKTOP,
                mWorkspace.getCurrentScreen(), xy[0], xy[1], false);

        final View view = mInflater.inflate(info.layoutResource, null);
        view.setTag(info);

        mWorkspace.addInCurrentScreen(view, xy[0], xy[1], info.spanX, spanY);
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
        resolver.registerContentObserver(Settings.Favorites.CONTENT_URI, true, mObserver);
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
        sModel.loadUserItems(false, this);
    }

    void onDesktopItemsLoaded() {
        if (mDestroyed) return;

        bindDesktopItems();
        mAllAppsGrid.setAdapter(Launcher.getModel().getApplicationsAdapter());

        if (mSavedState != null) {
            mWorkspace.getChildAt(mWorkspace.getCurrentScreen()).requestFocus();

            final long[] userFolders = mSavedState.getLongArray(RUNTIME_STATE_USER_FOLDERS);
            if (userFolders != null) {
                for (long folderId : userFolders) {
                    final UserFolderInfo info = sModel.findFolderById(folderId);
                    if (info != null) {
                        openUserFolder(info);
                    }
                }
                final Folder openFolder = mWorkspace.getOpenFolder();
                if (openFolder != null) {
                    openFolder.requestFocus();
                } else {
                    mWorkspace.getChildAt(mWorkspace.getCurrentScreen()).requestFocus();
                }
            }

            final boolean allApps = mSavedState.getBoolean(RUNTIME_STATE_ALL_APPS_FOLDER, false);
            if (allApps) {
                mDrawer.open();
                mDrawer.requestFocus();
            }

            mSavedState = null;
        }

        mDesktopLocked = false;
        mDrawer.unlock();
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     */
    private void bindDesktopItems() {
        final ArrayList<ItemInfo> shortcuts = sModel.getDesktopItems();
        if (shortcuts == null) {
            return;
        }

        final Workspace workspace = mWorkspace;
        int count = workspace.getChildCount();
        for (int i = 0; i < count; i++) {
            ((ViewGroup) workspace.getChildAt(i)).removeAllViewsInLayout();
        }

        count = shortcuts.size();
        for (int i = 0; i < count; i++) {
            final ItemInfo item = shortcuts.get(i);
            switch (item.itemType) {
            case Settings.Favorites.ITEM_TYPE_APPLICATION:
            case Settings.Favorites.ITEM_TYPE_SHORTCUT:
                final View shortcut = createShortcut((ApplicationInfo) item);
                workspace.addInScreen(shortcut, item.screen, item.cellX, item.cellY, 1, 1,
                        !mDesktopLocked);
                break;
            case Settings.Favorites.ITEM_TYPE_USER_FOLDER:
                final FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                        (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentScreen()),
                        ((UserFolderInfo) item));
                workspace.addInScreen(newFolder, item.screen, item.cellX, item.cellY, 1, 1,
                        !mDesktopLocked);
                break;
            default:
                final Widget widget = (Widget)item;
                final View view = createWidget(mInflater, widget);
                view.setTag(widget);
                workspace.addWidget(view, widget, !mDesktopLocked);
            }
        }

        workspace.requestLayout();
    }

    private View createWidget(LayoutInflater inflater, Widget widget) {
        final Workspace workspace = mWorkspace;
        final int screen = workspace.getCurrentScreen();
        View v = inflater.inflate(widget.layoutResource,
                (ViewGroup) workspace.getChildAt(screen), false);
        if (widget.itemType == Settings.Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME) {
            ((ImageView)v).setImageBitmap(widget.photo);
        }
        return v;
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
        } else if (tag instanceof UserFolderInfo) {
            handleFolderClick((UserFolderInfo) tag);
        }
    }

    void startActivitySafely(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleFolderClick(FolderInfo folderInfo) {
        if (!folderInfo.opened) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openUserFolder(folderInfo);
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
                    openUserFolder(folderInfo);
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
     * @param tag The UserFolderInfo describing the folder to open.
     */
    private void openUserFolder(Object tag) {
        UserFolder openFolder = UserFolder.fromXml(this);
        openFolder.setDragger(mDragLayer);
        openFolder.setLauncher(this);

        UserFolderInfo folderInfo = (UserFolderInfo) tag;
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
            return false;
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

    void showRenameDialog(UserFolderInfo info) {
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
                    sModel.loadUserItems(false, Launcher.this);
                } else {
                    final FolderIcon folderIcon = (FolderIcon) mWorkspace.getViewForTag(mFolderInfo);
                    if (folderIcon != null) {
                        folderIcon.setText(name);
                        getWorkspace().requestLayout();
                    } else {
                        mDesktopLocked = true;
                        mDrawer.lock();
                        sModel.loadUserItems(false, Launcher.this);
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
    private class CreateShortcut implements ExpandableListView.OnChildClickListener,
            DialogInterface.OnCancelListener, ExpandableListView.OnGroupExpandListener {
        private AddAdapter mAdapter;
        private ExpandableListView mList;

        Dialog createDialog() {
            mWaitingForResult = true;
            mAdapter = new AddAdapter(Launcher.this, false);
            
            final AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
            builder.setTitle(getString(R.string.menu_item_add_item));
            builder.setIcon(0);

            mList = (ExpandableListView)
                    View.inflate(Launcher.this, R.layout.create_shortcut_list, null);
            mList.setAdapter(mAdapter);
            mList.setOnChildClickListener(this);
            mList.setOnGroupExpandListener(this);
            builder.setView(mList);
            builder.setInverseBackgroundForced(true);

            AlertDialog dialog = builder.create();
            dialog.setOnCancelListener(this);

            WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
            attributes.gravity = Gravity.TOP;
            dialog.onWindowAttributesChanged(attributes);

            return dialog;
        }

        public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                int childPosition, long id) {
            mAdapter.performAction(groupPosition, childPosition);
            cleanup();
            return true;
        }

        public void onCancel(DialogInterface dialog) {
            mWaitingForResult = false;
            cleanup();
        }

        private void cleanup() {
            mWorkspace.unlock();
            dismissDialog(DIALOG_CREATE_SHORTCUT);
        }

        public void onGroupExpand(int groupPosition) {
            long packged = ExpandableListView.getPackedPositionForGroup(groupPosition);
            int position = mList.getFlatListPosition(packged);
            mList.setSelectionFromTop(position, 0);
        }
    }

    /**
     * Receives notifications when applications are added/removed.
     */
    private class ApplicationsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //noinspection ConstantConditions
            if (REMOVE_SHORTCUT_ON_PACKAGE_REMOVE &&
                    Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                removeShortcutsForPackage(intent.getData().getSchemeSpecificPart());
            }
            removeDialog(DIALOG_CREATE_SHORTCUT);
            sModel.loadApplications(false, Launcher.this);
        }
    }

    /**
     * Receives notifications whenever the user favorites have changed.
     */
    private class FavoritesChangeObserver extends ContentObserver {
        public FavoritesChangeObserver() {
            super(mHandler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onFavoritesChanged();
        }
    }

    private class SensorHandler implements SensorListener {
        private long mLastNegativeShake;
        private long mLastPositiveShake;

        public void onSensorChanged(int sensor, float[] values) {
            if (sensor == SensorManager.SENSOR_ACCELEROMETER) {
                float shake = values[0];
                if (shake <= -SensorManager.STANDARD_GRAVITY) {
                    mLastNegativeShake = SystemClock.uptimeMillis();
                } else if (shake >= SensorManager.STANDARD_GRAVITY) {
                    mLastPositiveShake = SystemClock.uptimeMillis();
                }

                final long difference = mLastPositiveShake - mLastNegativeShake;
                if (difference <= -80 && difference >= -180) {
                    mWorkspace.scrollLeft();
                    mLastNegativeShake = mLastPositiveShake = 0;
                } else if (difference >= 80 && difference <= 180) {
                    mWorkspace.scrollRight();
                    mLastNegativeShake = mLastPositiveShake = 0;
                }
            }
        }

        public void onAccuracyChanged(int sensor, int accuracy) {
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
                mOpen = true;
            }
        }

        public void onDrawerClosed() {
            if (mOpen) {
                mHandleIcon.reverseTransition(150);
                mOpen = false;
            }
            mAllAppsGrid.setSelection(0);
            mAllAppsGrid.clearTextFilter();
            mWorkspace.clearChildrenCache();
        }

        public void onScrollStarted() {
            mWorkspace.enableChildrenCache();
        }

        public void onScrollEnded() {
        }
    }
}
