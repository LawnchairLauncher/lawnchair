/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Patterns;

import com.android.launcher3.LauncherProvider.SqlArguments;
import com.android.launcher3.LauncherProvider.WorkspaceLoader;
import com.android.launcher3.LauncherSettings.Favorites;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class contains contains duplication of functionality as found in
 * LauncherProvider#DatabaseHelper. It has been isolated and differentiated in order
 * to cleanly and separately represent AutoInstall default layout format and policy.
 */
public class AutoInstallsLayout implements WorkspaceLoader {
    private static final String TAG = "AutoInstalls";
    private static final boolean LOGD = true;

    /** Marker action used to discover a package which defines launcher customization */
    static final String ACTION_LAUNCHER_CUSTOMIZATION =
            "android.autoinstalls.config.action.PLAY_AUTO_INSTALL";

    private static final String LAYOUT_RES = "default_layout";

    static AutoInstallsLayout get(Context context, AppWidgetHost appWidgetHost,
            LayoutParserCallback callback) {
        Pair<String, Resources> customizationApkInfo = Utilities.findSystemApk(
                ACTION_LAUNCHER_CUSTOMIZATION, context.getPackageManager());
        if (customizationApkInfo == null) {
            return null;
        }

        String pkg = customizationApkInfo.first;
        Resources res = customizationApkInfo.second;
        int layoutId = res.getIdentifier(LAYOUT_RES, "xml", pkg);
        if (layoutId == 0) {
            Log.e(TAG, "Layout definition not found in package: " + pkg);
            return null;
        }
        return new AutoInstallsLayout(context, appWidgetHost, callback, pkg, res, layoutId);
    }

    // Object Tags
    private static final String TAG_WORKSPACE = "workspace";
    private static final String TAG_APP_ICON = "appicon";
    private static final String TAG_AUTO_INSTALL = "autoinstall";
    private static final String TAG_FOLDER = "folder";
    private static final String TAG_APPWIDGET = "appwidget";
    private static final String TAG_SHORTCUT = "shortcut";
    private static final String TAG_EXTRA = "extra";

    private static final String ATTR_CONTAINER = "container";
    private static final String ATTR_RANK = "rank";

    private static final String ATTR_PACKAGE_NAME = "packageName";
    private static final String ATTR_CLASS_NAME = "className";
    private static final String ATTR_TITLE = "title";
    private static final String ATTR_SCREEN = "screen";
    private static final String ATTR_X = "x";
    private static final String ATTR_Y = "y";
    private static final String ATTR_SPAN_X = "spanX";
    private static final String ATTR_SPAN_Y = "spanY";
    private static final String ATTR_ICON = "icon";
    private static final String ATTR_URL = "url";

    // Style attrs -- "Extra"
    private static final String ATTR_KEY = "key";
    private static final String ATTR_VALUE = "value";

    private static final String HOTSEAT_CONTAINER_NAME =
            Favorites.containerToString(Favorites.CONTAINER_HOTSEAT);

    private static final String ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE =
            "com.android.launcher.action.APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE";

    private final Context mContext;
    private final AppWidgetHost mAppWidgetHost;
    private final LayoutParserCallback mCallback;

    private final PackageManager mPackageManager;
    private final ContentValues mValues;

    private final Resources mRes;
    private final int mLayoutId;

    private SQLiteDatabase mDb;

    public AutoInstallsLayout(Context context, AppWidgetHost appWidgetHost,
            LayoutParserCallback callback, String packageName, Resources res, int layoutId) {
        mContext = context;
        mAppWidgetHost = appWidgetHost;
        mCallback = callback;

        mPackageManager = context.getPackageManager();
        mValues = new ContentValues();

        mRes = res;
        mLayoutId = layoutId;
    }

    @Override
    public int loadLayout(SQLiteDatabase db, ArrayList<Long> screenIds) {
        mDb = db;
        try {
            return parseLayout(mRes, mLayoutId, screenIds);
        } catch (XmlPullParserException | IOException | RuntimeException e) {
            Log.w(TAG, "Got exception parsing layout.", e);
            return -1;
        }
    }

    private int parseLayout(Resources res, int layoutId, ArrayList<Long> screenIds)
            throws XmlPullParserException, IOException {
        final int hotseatAllAppsRank = LauncherAppState.getInstance()
                .getDynamicGrid().getDeviceProfile().hotseatAllAppsRank;

        XmlResourceParser parser = res.getXml(layoutId);
        beginDocument(parser, TAG_WORKSPACE);
        final int depth = parser.getDepth();
        int type;
        HashMap<String, TagParser> tagParserMap = getLayoutElementsMap();
        int count = 0;

        while (((type = parser.next()) != XmlPullParser.END_TAG ||
                parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            mValues.clear();
            final int container;
            final long screenId;

            if (HOTSEAT_CONTAINER_NAME.equals(getAttributeValue(parser, ATTR_CONTAINER))) {
                container = Favorites.CONTAINER_HOTSEAT;

                // Hack: hotseat items are stored using screen ids
                long rank = Long.parseLong(getAttributeValue(parser, ATTR_RANK));
                screenId = (rank < hotseatAllAppsRank) ? rank : (rank + 1);

            } else {
                container = Favorites.CONTAINER_DESKTOP;
                screenId = Long.parseLong(getAttributeValue(parser, ATTR_SCREEN));

                mValues.put(Favorites.CELLX, getAttributeValue(parser, ATTR_X));
                mValues.put(Favorites.CELLY, getAttributeValue(parser, ATTR_Y));
            }

            mValues.put(Favorites.CONTAINER, container);
            mValues.put(Favorites.SCREEN, screenId);

            TagParser tagParser = tagParserMap.get(parser.getName());
            if (tagParser == null) {
                if (LOGD) Log.d(TAG, "Ignoring unknown element tag: " + parser.getName());
                continue;
            }
            long newElementId = tagParser.parseAndAdd(parser, res);
            if (newElementId >= 0) {
                // Keep track of the set of screens which need to be added to the db.
                if (!screenIds.contains(screenId) &&
                        container == Favorites.CONTAINER_DESKTOP) {
                    screenIds.add(screenId);
                }
                count++;
            }
        }
        return count;
    }

    protected long addShortcut(String title, Intent intent, int type) {
        long id = mCallback.generateNewItemId();
        mValues.put(Favorites.INTENT, intent.toUri(0));
        mValues.put(Favorites.TITLE, title);
        mValues.put(Favorites.ITEM_TYPE, type);
        mValues.put(Favorites.SPANX, 1);
        mValues.put(Favorites.SPANY, 1);
        mValues.put(Favorites._ID, id);
        if (mCallback.insertAndCheck(mDb, mValues) < 0) {
            return -1;
        } else {
            return id;
        }
    }

    protected HashMap<String, TagParser> getFolderElementsMap() {
        HashMap<String, TagParser> parsers = new HashMap<String, TagParser>();
        parsers.put(TAG_APP_ICON, new AppShortcutParser());
        parsers.put(TAG_AUTO_INSTALL, new AutoInstallParser());
        parsers.put(TAG_SHORTCUT, new ShortcutParser());
        return parsers;
    }

    protected HashMap<String, TagParser> getLayoutElementsMap() {
        HashMap<String, TagParser> parsers = new HashMap<String, TagParser>();
        parsers.put(TAG_APP_ICON, new AppShortcutParser());
        parsers.put(TAG_AUTO_INSTALL, new AutoInstallParser());
        parsers.put(TAG_FOLDER, new FolderParser());
        parsers.put(TAG_APPWIDGET, new AppWidgetParser());
        parsers.put(TAG_SHORTCUT, new ShortcutParser());
        return parsers;
    }

    private interface TagParser {
        /**
         * Parses the tag and adds to the db
         * @return the id of the row added or -1;
         */
        long parseAndAdd(XmlResourceParser parser, Resources res)
                throws XmlPullParserException, IOException;
    }

    private class AppShortcutParser implements TagParser {

        @Override
        public long parseAndAdd(XmlResourceParser parser, Resources res) {
            final String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            final String className = getAttributeValue(parser, ATTR_CLASS_NAME);

            if (!TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(className)) {
                ActivityInfo info;
                try {
                    ComponentName cn;
                    try {
                        cn = new ComponentName(packageName, className);
                        info = mPackageManager.getActivityInfo(cn, 0);
                    } catch (PackageManager.NameNotFoundException nnfe) {
                        String[] packages = mPackageManager.currentToCanonicalPackageNames(
                                new String[] { packageName });
                        cn = new ComponentName(packages[0], className);
                        info = mPackageManager.getActivityInfo(cn, 0);
                    }
                    final Intent intent = new Intent(Intent.ACTION_MAIN, null)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(cn)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                    return addShortcut(info.loadLabel(mPackageManager).toString(),
                            intent, Favorites.ITEM_TYPE_APPLICATION);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Unable to add favorite: " + packageName + "/" + className, e);
                }
                return -1;
            } else {
                if (LOGD) Log.d(TAG, "Skipping invalid <favorite> with no component or uri");
                return -1;
            }
        }
    }

    private class AutoInstallParser implements TagParser {

        @Override
        public long parseAndAdd(XmlResourceParser parser, Resources res) {
            final String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            final String className = getAttributeValue(parser, ATTR_CLASS_NAME);
            if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(className)) {
                if (LOGD) Log.d(TAG, "Skipping invalid <favorite> with no component");
                return -1;
            }

            mValues.put(Favorites.RESTORED, ShortcutInfo.FLAG_AUTOINTALL_ICON);
            final Intent intent = new Intent(Intent.ACTION_MAIN, null)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(packageName, className))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return addShortcut(mContext.getString(R.string.package_state_unknown), intent,
                    Favorites.ITEM_TYPE_APPLICATION);
        }
    }

    private class ShortcutParser implements TagParser {

        @Override
        public long parseAndAdd(XmlResourceParser parser, Resources res) {
            final String url = getAttributeValue(parser, ATTR_URL);
            final int titleResId = getAttributeResourceValue(parser, ATTR_TITLE, 0);
            final int iconId = getAttributeResourceValue(parser, ATTR_ICON, 0);

            if (titleResId == 0 || iconId == 0) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut");
                return -1;
            }

            if (TextUtils.isEmpty(url) || !Patterns.WEB_URL.matcher(url).matches()) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut, invalid url: " + url);
                return -1;
            }
            Drawable icon = res.getDrawable(iconId);
            if (icon == null) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut, can't load icon");
                return -1;
            }

            ItemInfo.writeBitmap(mValues, Utilities.createIconBitmap(icon, mContext));
            final Intent intent = new Intent(Intent.ACTION_VIEW, null)
                .setData(Uri.parse(url))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return addShortcut(res.getString(titleResId), intent, Favorites.ITEM_TYPE_SHORTCUT);
        }
    }

    private class AppWidgetParser implements TagParser {

        @Override
        public long parseAndAdd(XmlResourceParser parser, Resources res)
                throws XmlPullParserException, IOException {
            final String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            final String className = getAttributeValue(parser, ATTR_CLASS_NAME);
            if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(className)) {
                if (LOGD) Log.d(TAG, "Skipping invalid <favorite> with no component");
                return -1;
            }

            ComponentName cn = new ComponentName(packageName, className);
            try {
                mPackageManager.getReceiverInfo(cn, 0);
            } catch (Exception e) {
                String[] packages = mPackageManager.currentToCanonicalPackageNames(
                        new String[] { packageName });
                cn = new ComponentName(packages[0], className);
                try {
                    mPackageManager.getReceiverInfo(cn, 0);
                } catch (Exception e1) {
                    if (LOGD) Log.d(TAG, "Can't find widget provider: " + className);
                    return -1;
                }
            }

            mValues.put(Favorites.SPANX, getAttributeValue(parser, ATTR_SPAN_X));
            mValues.put(Favorites.SPANY, getAttributeValue(parser, ATTR_SPAN_Y));

            // Read the extras
            Bundle extras = new Bundle();
            int widgetDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > widgetDepth) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }

                if (TAG_EXTRA.equals(parser.getName())) {
                    String key = getAttributeValue(parser, ATTR_KEY);
                    String value = getAttributeValue(parser, ATTR_VALUE);
                    if (key != null && value != null) {
                        extras.putString(key, value);
                    } else {
                        throw new RuntimeException("Widget extras must have a key and value");
                    }
                } else {
                    throw new RuntimeException("Widgets can contain only extras");
                }
            }

            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            long insertedId = -1;
            try {
                int appWidgetId = mAppWidgetHost.allocateAppWidgetId();

                if (!appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, cn)) {
                    if (LOGD) Log.e(TAG, "Unable to bind app widget id " + cn);
                    return -1;
                }

                mValues.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET);
                mValues.put(Favorites.APPWIDGET_ID, appWidgetId);
                mValues.put(Favorites.APPWIDGET_PROVIDER, cn.flattenToString());
                mValues.put(Favorites._ID, mCallback.generateNewItemId());
                insertedId = mCallback.insertAndCheck(mDb, mValues);
                if (insertedId < 0) {
                    mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                    return insertedId;
                }

                // Send a broadcast to configure the widget
                if (!extras.isEmpty()) {
                    Intent intent = new Intent(ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE);
                    intent.setComponent(cn);
                    intent.putExtras(extras);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    mContext.sendBroadcast(intent);
                }
            } catch (RuntimeException ex) {
                if (LOGD) Log.e(TAG, "Problem allocating appWidgetId", ex);
            }
            return insertedId;
        }
    }

    private class FolderParser implements TagParser {
        private final HashMap<String, TagParser> mFolderElements = getFolderElementsMap();

        @Override
        public long parseAndAdd(XmlResourceParser parser, Resources res)
                throws XmlPullParserException, IOException {
            final String title;
            final int titleResId = getAttributeResourceValue(parser, ATTR_TITLE, 0);
            if (titleResId != 0) {
                title = res.getString(titleResId);
            } else {
                title = mContext.getResources().getString(R.string.folder_name);
            }

            mValues.put(Favorites.TITLE, title);
            mValues.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER);
            mValues.put(Favorites.SPANX, 1);
            mValues.put(Favorites.SPANY, 1);
            mValues.put(Favorites._ID, mCallback.generateNewItemId());
            long folderId = mCallback.insertAndCheck(mDb, mValues);
            if (folderId < 0) {
                if (LOGD) Log.e(TAG, "Unable to add folder");
                return -1;
            }

            final ContentValues myValues = new ContentValues(mValues);
            ArrayList<Long> folderItems = new ArrayList<Long>();

            int type;
            int folderDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > folderDepth) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                mValues.clear();
                mValues.put(Favorites.CONTAINER, folderId);

                TagParser tagParser = mFolderElements.get(parser.getName());
                if (tagParser != null) {
                    final long id = tagParser.parseAndAdd(parser, res);
                    if (id >= 0) {
                        folderItems.add(id);
                    }
                } else {
                    throw new RuntimeException("Invalid folder item " + parser.getName());
                }
            }

            long addedId = folderId;

            // We can only have folders with >= 2 items, so we need to remove the
            // folder and clean up if less than 2 items were included, or some
            // failed to add, and less than 2 were actually added
            if (folderItems.size() < 2) {
                // Delete the folder
                Uri uri = Favorites.getContentUri(folderId, false);
                SqlArguments args = new SqlArguments(uri, null, null);
                mDb.delete(args.table, args.where, args.args);
                addedId = -1;

                // If we have a single item, promote it to where the folder
                // would have been.
                if (folderItems.size() == 1) {
                    final ContentValues childValues = new ContentValues();
                    copyInteger(myValues, childValues, Favorites.CONTAINER);
                    copyInteger(myValues, childValues, Favorites.SCREEN);
                    copyInteger(myValues, childValues, Favorites.CELLX);
                    copyInteger(myValues, childValues, Favorites.CELLY);

                    addedId = folderItems.get(0);
                    mDb.update(LauncherProvider.TABLE_FAVORITES, childValues,
                            Favorites._ID + "=" + addedId, null);
                }
            }
            return addedId;
        }
    }

    private static final void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT);

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                    ", expected " + firstElementName);
        }
    }

    /**
     * Return attribute value, attempting launcher-specific namespace first
     * before falling back to anonymous attribute.
     */
    private static String getAttributeValue(XmlResourceParser parser, String attribute) {
        String value = parser.getAttributeValue(
                "http://schemas.android.com/apk/res-auto/com.android.launcher3", attribute);
        if (value == null) {
            value = parser.getAttributeValue(null, attribute);
        }
        return value;
    }

    /**
     * Return attribute resource value, attempting launcher-specific namespace
     * first before falling back to anonymous attribute.
     */
    private static int getAttributeResourceValue(XmlResourceParser parser, String attribute,
            int defaultValue) {
        int value = parser.getAttributeResourceValue(
                "http://schemas.android.com/apk/res-auto/com.android.launcher3", attribute,
                defaultValue);
        if (value == defaultValue) {
            value = parser.getAttributeResourceValue(null, attribute, defaultValue);
        }
        return value;
    }

    public static interface LayoutParserCallback {
        long generateNewItemId();

        long insertAndCheck(SQLiteDatabase db, ContentValues values);
    }

    private static void copyInteger(ContentValues from, ContentValues to, String key) {
        to.put(key, from.getAsInteger(key));
    }
}
