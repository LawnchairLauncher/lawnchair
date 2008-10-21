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

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import com.android.internal.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Comparator;
import java.lang.ref.WeakReference;
import java.text.Collator;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher
 *
 */
public class LauncherModel {
    private static final int UI_NOTIFICATION_RATE = 4;
    private static final int DEFAULT_APPLICATIONS_NUMBER = 42;
    private static final long APPLICATION_NOT_RESPONDING_TIMEOUT = 5000;

    private final Collator sCollator = Collator.getInstance();    

    private boolean mApplicationsLoaded;
    private boolean mDesktopItemsLoaded;

    private ArrayList<ItemInfo> mDesktopItems;
    private HashMap<Long, UserFolderInfo> mUserFolders;

    private ArrayList<ApplicationInfo> mApplications;
    private ApplicationsAdapter mApplicationsAdapter;
    private ApplicationsLoader mApplicationsLoader;
    private DesktopItemsLoader mDesktopItemsLoader;
    private Thread mLoader;
    private Thread mDesktopLoader;

    void abortLoaders() {
        if (mApplicationsLoader != null && mApplicationsLoader.isRunning()) {
            mApplicationsLoader.stop();
            mApplicationsLoaded = false;
        }
        if (mDesktopItemsLoader != null && mDesktopItemsLoader.isRunning()) {
            mDesktopItemsLoader.stop();
            mDesktopItemsLoaded = false;
        }
    }

    /**
     * Loads the list of installed applications in mApplications.
     */
    void loadApplications(boolean isLaunching, Launcher launcher) {
        if (isLaunching && mApplicationsLoaded) {
            mApplicationsAdapter = new ApplicationsAdapter(launcher, mApplications);
            return;
        }

        if (mApplicationsAdapter == null || isLaunching) {
            mApplicationsAdapter = new ApplicationsAdapter(launcher,
                    mApplications = new ArrayList<ApplicationInfo>(DEFAULT_APPLICATIONS_NUMBER));
        }

        if (mApplicationsLoader != null && mApplicationsLoader.isRunning()) {
            mApplicationsLoader.stop();
            // Wait for the currently running thread to finish, this can take a little
            // time but it should be well below the timeout limit
            try {
                mLoader.join(APPLICATION_NOT_RESPONDING_TIMEOUT);
            } catch (InterruptedException e) {
                // Empty
            }
        }

        mApplicationsLoaded = false;
        mApplicationsLoader = new ApplicationsLoader(launcher);
        mLoader = new Thread(mApplicationsLoader, "Applications Loader");
        mLoader.start();
    }

    private class ApplicationsLoader implements Runnable {
        private final WeakReference<Launcher> mLauncher;

        private volatile boolean mStopped;
        private volatile boolean mRunning;

        ApplicationsLoader(Launcher launcher) {
            mLauncher = new WeakReference<Launcher>(launcher);
        }

        void stop() {
            mStopped = true;
        }

        boolean isRunning() {
            return mRunning;
        }

        public void run() {
            mRunning = true;

            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            final Launcher launcher = mLauncher.get();
            final PackageManager manager = launcher.getPackageManager();
            final List<ResolveInfo> apps = manager.queryIntentActivities(mainIntent, 0);

            if (apps != null && !mStopped) {
                final int count = apps.size();
                final ApplicationsAdapter applicationList = mApplicationsAdapter;

                ChangeNotifier action = new ChangeNotifier(applicationList);

                for (int i = 0; i < count && !mStopped; i++) {
                    ApplicationInfo application = new ApplicationInfo();
                    ResolveInfo info = apps.get(i);

                    application.title = info.loadLabel(manager);
                    if (application.title == null) {
                        application.title = info.activityInfo.name;
                    }
                    application.setActivity(new ComponentName(
                            info.activityInfo.applicationInfo.packageName,
                            info.activityInfo.name),
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    application.icon = info.activityInfo.loadIcon(manager);
                    application.container = ItemInfo.NO_ID;

                    action.add(application);
                }

                action.sort(new Comparator<ApplicationInfo>() {
                    public final int compare(ApplicationInfo a, ApplicationInfo b) {
                        return sCollator.compare(a.title, b.title);
                    }
                });

                if (!mStopped) {
                    launcher.runOnUiThread(action);
                }
            }

            if (!mStopped) {
                mApplicationsLoaded = true;
            }
            mRunning = false;
        }
    }

    private static class ChangeNotifier implements Runnable {
        private final ApplicationsAdapter mApplicationList;
        private ArrayList<ApplicationInfo> mBuffer;

        ChangeNotifier(ApplicationsAdapter applicationList) {
            mApplicationList = applicationList;
            mBuffer = new ArrayList<ApplicationInfo>(UI_NOTIFICATION_RATE);
        }

        public void run() {
            final ArrayList<ApplicationInfo> buffer = mBuffer;
            final ApplicationsAdapter applicationList = mApplicationList;
            final int count = buffer.size();

            applicationList.clear();
            for (int i = 0; i < count; i++) {
                applicationList.setNotifyOnChange(false);
                applicationList.add(buffer.get(i));
            }

            applicationList.notifyDataSetChanged();
            buffer.clear();
        }

        void add(ApplicationInfo application) {
            mBuffer.add(application);
        }

        void sort(Comparator<ApplicationInfo> comparator) {
            Collections.sort(mBuffer, comparator);
        }
    }

    boolean isDesktopLoaded() {
        return mDesktopItems != null && mDesktopItemsLoaded;
    }
    
    /**
     * Loads all of the items on the desktop, in folders, or in the dock.
     * These can be apps, shortcuts or widgets
     */
    void loadUserItems(boolean isLaunching, Launcher launcher) {
        if (isLaunching && mDesktopItems != null && mDesktopItemsLoaded) {
            // We have already loaded our data from the DB
            launcher.onDesktopItemsLoaded();
            return;
        }

        if (mDesktopItemsLoader != null && mDesktopItemsLoader.isRunning()) {
            mDesktopItemsLoader.stop();
            // Wait for the currently running thread to finish, this can take a little
            // time but it should be well below the timeout limit
            try {
                mDesktopLoader.join(APPLICATION_NOT_RESPONDING_TIMEOUT);
            } catch (InterruptedException e) {
                // Empty
            }
        }

        mDesktopItemsLoaded = false;
        mDesktopItemsLoader = new DesktopItemsLoader(launcher);
        mDesktopLoader = new Thread(mDesktopItemsLoader, "Desktop Items Loader");
        mDesktopLoader.start();
    }

    private class DesktopItemsLoader implements Runnable {
        private volatile boolean mStopped;
        private volatile boolean mRunning;

        private final WeakReference<Launcher> mLauncher;

        DesktopItemsLoader(Launcher launcher) {
            mLauncher = new WeakReference<Launcher>(launcher);
        }

        void stop() {
            mStopped = true;
        }

        boolean isRunning() {
            return mRunning;
        }

        public void run() {
            mRunning = true;

            final Launcher launcher = mLauncher.get();

            mDesktopItems = new ArrayList<ItemInfo>();
            mUserFolders = new HashMap<Long, UserFolderInfo>();

            final ArrayList<ItemInfo> desktopItems = mDesktopItems;

            final Cursor c = launcher.getContentResolver().query(Settings.Favorites.CONTENT_URI,
                    null, null, null, null);

            try {
                final int idIndex = c.getColumnIndexOrThrow(Settings.Favorites.ID);
                final int intentIndex = c.getColumnIndexOrThrow(Settings.Favorites.INTENT);
                final int titleIndex = c.getColumnIndexOrThrow(Settings.Favorites.TITLE);
                final int iconTypeIndex = c.getColumnIndexOrThrow(Settings.Favorites.ICON_TYPE);
                final int iconIndex = c.getColumnIndexOrThrow(Settings.Favorites.ICON);
                final int iconPackageIndex = c.getColumnIndexOrThrow(Settings.Favorites.ICON_PACKAGE);
                final int iconResourceIndex = c.getColumnIndexOrThrow(Settings.Favorites.ICON_RESOURCE);
                final int containerIndex = c.getColumnIndexOrThrow(Settings.Favorites.CONTAINER);
                final int itemTypeIndex = c.getColumnIndexOrThrow(Settings.Favorites.ITEM_TYPE);
                final int screenIndex = c.getColumnIndexOrThrow(Settings.Favorites.SCREEN);
                final int cellXIndex = c.getColumnIndexOrThrow(Settings.Favorites.CELLX);
                final int cellYIndex = c.getColumnIndexOrThrow(Settings.Favorites.CELLY);

                final PackageManager manager = launcher.getPackageManager();

                ApplicationInfo info;
                String intentDescription;
                Widget widgetInfo = null;
                int container;

                final HashMap<Long, UserFolderInfo> userFolders = mUserFolders;

                while (!mStopped && c.moveToNext()) {
                    try {
                        int itemType = c.getInt(itemTypeIndex);

                        switch (itemType) {
                        case Settings.Favorites.ITEM_TYPE_APPLICATION:
                        case Settings.Favorites.ITEM_TYPE_SHORTCUT:
                            intentDescription = c.getString(intentIndex);
                            Intent intent;
                            try {
                                intent = Intent.getIntent(intentDescription);
                            } catch (java.net.URISyntaxException e) {
                                continue;
                            }

                            if (itemType == Settings.Favorites.ITEM_TYPE_APPLICATION) {
                                info = getApplicationInfo(manager, intent);
                            } else {
                                info = getApplicationInfoShortcut(c, launcher, iconTypeIndex,
                                        iconPackageIndex, iconResourceIndex, iconIndex);
                            }

                            if (info == null) {
                                info = new ApplicationInfo();
                                info.icon = manager.getDefaultActivityIcon();
                            }

                            if (info != null) {
                                info.title = c.getString(titleIndex);
                                info.intent = intent;

                                info.id = c.getLong(idIndex);
                                container = c.getInt(containerIndex);
                                info.container = container;
                                info.screen = c.getInt(screenIndex);
                                info.cellX = c.getInt(cellXIndex);
                                info.cellY = c.getInt(cellYIndex);

                                switch (container) {
                                case Settings.Favorites.CONTAINER_DESKTOP:
                                    desktopItems.add(info);
                                    break;
                                default:
                                    // Item is in a user folder
                                    UserFolderInfo folderInfo =
                                            findOrMakeFolder(userFolders, container);
                                    folderInfo.add(info);
                                    break;
                                }
                            }
                            break;
                        case Settings.Favorites.ITEM_TYPE_USER_FOLDER:

                            long id = c.getLong(idIndex);
                            UserFolderInfo folderInfo = findOrMakeFolder(userFolders, id);

                            folderInfo.title = c.getString(titleIndex);

                            folderInfo.id = id;
                            container = c.getInt(containerIndex);
                            folderInfo.container = container;
                            folderInfo.screen = c.getInt(screenIndex);
                            folderInfo.cellX = c.getInt(cellXIndex);
                            folderInfo.cellY = c.getInt(cellYIndex);

                            switch (container) {
                            case Settings.Favorites.CONTAINER_DESKTOP:
                                desktopItems.add(folderInfo);
                                break;
                            default:

                            }
                            break;
                        case Settings.Favorites.ITEM_TYPE_WIDGET_CLOCK:
                        case Settings.Favorites.ITEM_TYPE_WIDGET_SEARCH:
                        case Settings.Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME:
                            switch (itemType) {
                            case Settings.Favorites.ITEM_TYPE_WIDGET_CLOCK:
                                widgetInfo = Widget.makeClock();
                                break;
                            case Settings.Favorites.ITEM_TYPE_WIDGET_SEARCH:
                                widgetInfo = Widget.makeSearch();
                                break;
                            case Settings.Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME:
                                widgetInfo = Widget.makePhotoFrame();
                                byte[] data = c.getBlob(iconIndex);
                                if (data != null) {
                                    widgetInfo.photo =
                                            BitmapFactory.decodeByteArray(data, 0, data.length);
                                }
                                break;
                            }

                            if (widgetInfo != null) {
                                container = c.getInt(containerIndex);
                                if (container != Settings.Favorites.CONTAINER_DESKTOP) {
                                    Log.e(Launcher.LOG_TAG, "Widget found where container "
                                            + "!= CONTAINER_DESKTOP -- ignoring!");
                                    continue;
                                }
                                widgetInfo.id = c.getLong(idIndex);
                                widgetInfo.screen = c.getInt(screenIndex);
                                widgetInfo.container = container;
                                widgetInfo.cellX = c.getInt(cellXIndex);
                                widgetInfo.cellY = c.getInt(cellYIndex);

                                desktopItems.add(widgetInfo);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        Log.w(Launcher.LOG_TAG, "Desktop items loading interrupted:", e);
                    }
                }
            } finally {
                c.close();
            }

            if (!mStopped) {
                launcher.runOnUiThread(new Runnable() {
                    public void run() {
                        launcher.onDesktopItemsLoaded();
                    }
                });
            }

            if (!mStopped) {
                mDesktopItemsLoaded = true;
            }
            mRunning = false;
        }
    }

    /**
     * Finds the user folder defined by the specified id.
     *
     * @param id The id of the folder to look for.
     * 
     * @return A UserFolderInfo if the folder exists or null otherwise.
     */
    UserFolderInfo findFolderById(long id) {
        return mUserFolders.get(id);
    }

    void addUserFolder(UserFolderInfo info) {
        mUserFolders.put(info.id, info);
    }

    /**
     * Return an existing UserFolderInfo object if we have encountered this ID previously, or make a
     * new one.
     */
    private UserFolderInfo findOrMakeFolder(HashMap<Long, UserFolderInfo> userFolders, long id) {
        UserFolderInfo folderInfo;
        // See if a placeholder was created for us already
        folderInfo = userFolders.get(id);
        if (folderInfo == null) {
            // No placeholder -- create a new instance
            folderInfo = new UserFolderInfo();
            userFolders.put(id, folderInfo);
        }
        return folderInfo;
    }

    /**
     * Remove the callback for the cached drawables or we leak the previous
     * Home screen on orientation change.
     */
    void unbind() {
        mApplicationsAdapter = null;
        unbindAppDrawables(mApplications);
        unbindDrawables(mDesktopItems);
    }
    
    /**
     * Remove the callback for the cached drawables or we leak the previous
     * Home screen on orientation change.
     */
    private void unbindDrawables(ArrayList<ItemInfo> desktopItems) {
        if (desktopItems != null) {
            final int count = desktopItems.size();
            for (int i = 0; i < count; i++) {
                ItemInfo item = desktopItems.get(i);
                switch (item.itemType) {
                case Settings.Favorites.ITEM_TYPE_APPLICATION:
                case Settings.Favorites.ITEM_TYPE_SHORTCUT:
                    ((ApplicationInfo)item).icon.setCallback(null);
                }
            }
        }
    }
    
    /**
     * Remove the callback for the cached drawables or we leak the previous
     * Home screen on orientation change.
     */
    private void unbindAppDrawables(ArrayList<ApplicationInfo> applications) {
        if (applications != null) {
            final int count = applications.size();
            for (int i = 0; i < count; i++) {
                applications.get(i).icon.setCallback(null);
            }
        }
    }

    /**
     * @return The current list of applications
     */
    public ArrayList<ApplicationInfo> getApplications() {
        return mApplications;
    }

    /**
     * @return The current list of applications
     */
    public ApplicationsAdapter getApplicationsAdapter() {
        return mApplicationsAdapter;
    }

    /**
     * @return The current list of desktop items
     */
    public ArrayList<ItemInfo> getDesktopItems() {
        return mDesktopItems;
    }

    /**
     * Add an item to the desktop
     * @param info
     */
    public void addDesktopItem(ItemInfo info) {
        // TODO: write to DB; also check that folder has been added to folders list
        mDesktopItems.add(info);
    }
    
    /**
     * Remove an item from the desktop
     * @param info
     */
    public void removeDesktopItem(ItemInfo info) {
        // TODO: write to DB; figure out if we should remove folder from folders list
        mDesktopItems.remove(info);
    }

    /**
     * Make an ApplicationInfo object for an application
     */
    private static ApplicationInfo getApplicationInfo(PackageManager manager, Intent intent) {
        final ResolveInfo resolveInfo = manager.resolveActivity(intent, 0);

        if (resolveInfo == null) {
            return null;
        }
        
        final ApplicationInfo info = new ApplicationInfo();
        final ActivityInfo activityInfo = resolveInfo.activityInfo;
        info.icon = activityInfo.loadIcon(manager);
        if (info.title == null || info.title.length() == 0) {
            info.title = activityInfo.loadLabel(manager);
        }
        if (info.title == null) {
            info.title = "";
        }
        info.itemType = Settings.Favorites.ITEM_TYPE_APPLICATION;
        return info;
    }
    
    /**
     * Make an ApplicationInfo object for a sortcut
     */
    private ApplicationInfo getApplicationInfoShortcut(Cursor c, Launcher launcher,
            int iconTypeIndex, int iconPackageIndex, int iconResourceIndex, int iconIndex) {

        final ApplicationInfo info = new ApplicationInfo();
        info.itemType = Settings.Favorites.ITEM_TYPE_SHORTCUT;

        int iconType = c.getInt(iconTypeIndex);
        switch (iconType) {
            case Settings.Favorites.ICON_TYPE_RESOURCE:
                String packageName = c.getString(iconPackageIndex);
                String resourceName = c.getString(iconResourceIndex);
                PackageManager packageManager = launcher.getPackageManager();
                try {
                    Resources resources = packageManager.getResourcesForApplication(packageName);
                    final int id = resources.getIdentifier(resourceName, null, null);
                    info.icon = resources.getDrawable(id);
                } catch (Exception e) {
                    info.icon = packageManager.getDefaultActivityIcon();
                }
                info.iconResource = new Intent.ShortcutIconResource();
                info.iconResource.packageName = packageName;
                info.iconResource.resourceName = resourceName;
                info.customIcon = false;
                break;
            case Settings.Favorites.ICON_TYPE_BITMAP:
                byte[] data = c.getBlob(iconIndex);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                info.icon = new BitmapDrawable(Utilities.createBitmapThumbnail(bitmap, launcher));
                info.filtered = true;
                info.customIcon = true;
                break;
            default:
                info.icon = launcher.getPackageManager().getDefaultActivityIcon();
                info.customIcon = false;
                break;
        }
        return info;
    }

    /**
     * Remove an item from the in-memory represention of a user folder. Does not change the DB.
     */
    void removeUserFolderItem(UserFolderInfo folder, ItemInfo info) {
        //noinspection SuspiciousMethodCalls
        folder.contents.remove(info);
    }
    
    /**
     * Removes a UserFolder from the in-memory list of folders. Does not change the DB.
     * @param userFolderInfo
     */
    void removeUserFolder(UserFolderInfo userFolderInfo) {
        mUserFolders.remove(userFolderInfo.id);
    }
    
    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    static void addOrMoveItemInDatabase(Context context, ItemInfo item, long container,
            int screen, int cellX, int cellY) {
        if (item.container == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(context, item, container, screen, cellX, cellY, false);
        } else {
            // From somewhere else
            moveItemInDatabase(context, item, container, screen, cellX, cellY);
        }
    }
    
    /**
     * Move an item in the DB to a new <container, screen, cellX, cellY>
     */
    static void moveItemInDatabase(Context context, ItemInfo item, long container, int screen,
            int cellX, int cellY) {
        item.container = container;
        item.screen = screen;
        item.cellX = cellX;
        item.cellY = cellY;
     
        final ContentValues values = new ContentValues();
        final ContentResolver cr = context.getContentResolver();

        values.put(Settings.Favorites.CONTAINER, item.container);
        values.put(Settings.Favorites.CELLX, item.cellX);
        values.put(Settings.Favorites.CELLY, item.cellY);
        values.put(Settings.Favorites.SCREEN, item.screen);

        cr.update(Settings.Favorites.getContentUri(item.id, false), values, null, null);
    }

    /**
     * Returns true if the shortcuts already exists in the database.
     * we identify a shortcut by its title and intent.
     */
    static boolean shortcutExists(Context context, String title, Intent intent) {
        final ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(Settings.Favorites.CONTENT_URI,
            new String[] { "title", "intent" }, "title=? and intent=?",
            new String[] { title, intent.toURI() }, null);
        boolean result = false;
        try {
            result = c.moveToFirst();
        } finally {
            c.close();
        }
        return result;
    }

    UserFolderInfo getFolderById(Context context, long id) {
        final ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(Settings.Favorites.CONTENT_URI, null, "_id=? and itemType=?",
            new String[] { String.valueOf(id),
                    String.valueOf(Settings.Favorites.ITEM_TYPE_USER_FOLDER) }, null);

        try {
            if (c.moveToFirst()) {
                final int titleIndex = c.getColumnIndexOrThrow(Settings.Favorites.TITLE);
                final int containerIndex = c.getColumnIndexOrThrow(Settings.Favorites.CONTAINER);
                final int screenIndex = c.getColumnIndexOrThrow(Settings.Favorites.SCREEN);
                final int cellXIndex = c.getColumnIndexOrThrow(Settings.Favorites.CELLX);
                final int cellYIndex = c.getColumnIndexOrThrow(Settings.Favorites.CELLY);

                UserFolderInfo folderInfo = findOrMakeFolder(mUserFolders, id);

                folderInfo.title = c.getString(titleIndex);
                folderInfo.id = id;
                folderInfo.container = c.getInt(containerIndex);
                folderInfo.screen = c.getInt(screenIndex);
                folderInfo.cellX = c.getInt(cellXIndex);
                folderInfo.cellY = c.getInt(cellYIndex);

                return folderInfo;
            }
        } finally {
            c.close();
        }

        return null;
    }

    static Widget getPhotoFrameInfo(Context context, int screen, int cellX, int cellY) {
        final ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(Settings.Favorites.CONTENT_URI,
            null, "screen=? and cellX=? and cellY=? and itemType=?",
            new String[] { String.valueOf(screen), String.valueOf(cellX), String.valueOf(cellY),
                String.valueOf(Settings.Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME) }, null);

        try {
            if (c.moveToFirst()) {
                final int idIndex = c.getColumnIndexOrThrow(Settings.Favorites.ID);
                final int containerIndex = c.getColumnIndexOrThrow(Settings.Favorites.CONTAINER);
                final int screenIndex = c.getColumnIndexOrThrow(Settings.Favorites.SCREEN);
                final int cellXIndex = c.getColumnIndexOrThrow(Settings.Favorites.CELLX);
                final int cellYIndex = c.getColumnIndexOrThrow(Settings.Favorites.CELLY);

                Widget widgetInfo = Widget.makePhotoFrame();
                widgetInfo.id = c.getLong(idIndex);
                widgetInfo.screen = c.getInt(screenIndex);
                widgetInfo.container = c.getInt(containerIndex);
                widgetInfo.cellX = c.getInt(cellXIndex);
                widgetInfo.cellY = c.getInt(cellYIndex);

                return widgetInfo;
            }
        } finally {
            c.close();
        }

        return null;
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    static void addItemToDatabase(Context context, ItemInfo item, long container,
            int screen, int cellX, int cellY, boolean notify) {
        item.container = container;
        item.screen = screen;
        item.cellX = cellX;
        item.cellY = cellY;
        
        final ContentValues values = new ContentValues();
        final ContentResolver cr = context.getContentResolver();
        
        item.onAddToDatabase(values);
        
        Uri result = cr.insert(notify ? Settings.Favorites.CONTENT_URI :
                Settings.Favorites.CONTENT_URI_NO_NOTIFICATION, values);

        if (result != null) {
            item.id = Integer.parseInt(result.getPathSegments().get(1));
        }
    }

    /**
     * Update an item to the database in a specified container.
     */
    static void updateItemInDatabase(Context context, ItemInfo item) {
        final ContentValues values = new ContentValues();
        final ContentResolver cr = context.getContentResolver();

        item.onAddToDatabase(values);

        cr.update(Settings.Favorites.getContentUri(item.id, false), values, null, null);
    }
    
    /**
     * Removes the specified item from the database
     * @param context
     * @param item
     */
    static void deleteItemFromDatabase(Context context, ItemInfo item) {
        final ContentResolver cr = context.getContentResolver();

        cr.delete(Settings.Favorites.getContentUri(item.id, false), null, null);
    }


    /**
     * Remove the contents of the specified folder from the database
     */
    static void deleteUserFolderContentsFromDatabase(Context context, UserFolderInfo info) {
        final ContentResolver cr = context.getContentResolver();

        cr.delete(Settings.Favorites.getContentUri(info.id, false), null, null);
        cr.delete(Settings.Favorites.CONTENT_URI, Settings.Favorites.CONTAINER + "=" + info.id, 
                null);
    }
}
