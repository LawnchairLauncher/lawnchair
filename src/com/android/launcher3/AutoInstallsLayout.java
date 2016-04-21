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
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.util.Thunk;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Layout parsing code for auto installs layout
 */
public class AutoInstallsLayout {
    private static final String TAG = "AutoInstalls";
    private static final boolean LOGD = false;

    /** Marker action used to discover a package which defines launcher customization */
    static final String ACTION_LAUNCHER_CUSTOMIZATION =
            "android.autoinstalls.config.action.PLAY_AUTO_INSTALL";

    /**
     * Layout resource which also includes grid size and hotseat count, e.g., default_layout_6x6_h5
     */
    private static final String FORMATTED_LAYOUT_RES_WITH_HOSTEAT = "default_layout_%dx%d_h%s";
    private static final String FORMATTED_LAYOUT_RES = "default_layout_%dx%d";
    private static final String LAYOUT_RES = "default_layout";

    static AutoInstallsLayout get(Context context, AppWidgetHost appWidgetHost,
            LayoutParserCallback callback) {
        Pair<String, Resources> customizationApkInfo = Utilities.findSystemApk(
                ACTION_LAUNCHER_CUSTOMIZATION, context.getPackageManager());
        if (customizationApkInfo == null) {
            return null;
        }
        return get(context, customizationApkInfo.first, customizationApkInfo.second,
                appWidgetHost, callback);
    }

    static AutoInstallsLayout get(Context context, String pkg, Resources targetRes,
            AppWidgetHost appWidgetHost, LayoutParserCallback callback) {
        InvariantDeviceProfile grid = LauncherAppState.getInstance().getInvariantDeviceProfile();

        // Try with grid size and hotseat count
        String layoutName = String.format(Locale.ENGLISH, FORMATTED_LAYOUT_RES_WITH_HOSTEAT,
                (int) grid.numColumns, (int) grid.numRows, (int) grid.numHotseatIcons);
        int layoutId = targetRes.getIdentifier(layoutName, "xml", pkg);

        // Try with only grid size
        if (layoutId == 0) {
            Log.d(TAG, "Formatted layout: " + layoutName
                    + " not found. Trying layout without hosteat");
            layoutName = String.format(Locale.ENGLISH, FORMATTED_LAYOUT_RES,
                    (int) grid.numColumns, (int) grid.numRows);
            layoutId = targetRes.getIdentifier(layoutName, "xml", pkg);
        }

        // Try the default layout
        if (layoutId == 0) {
            Log.d(TAG, "Formatted layout: " + layoutName + " not found. Trying the default layout");
            layoutId = targetRes.getIdentifier(LAYOUT_RES, "xml", pkg);
        }

        if (layoutId == 0) {
            Log.e(TAG, "Layout definition not found in package: " + pkg);
            return null;
        }
        return new AutoInstallsLayout(context, appWidgetHost, callback, targetRes, layoutId,
                TAG_WORKSPACE);
    }

    // Object Tags
    private static final String TAG_INCLUDE = "include";
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

    // x and y can be specified as negative integers, in which case -1 represents the
    // last row / column, -2 represents the second last, and so on.
    private static final String ATTR_X = "x";
    private static final String ATTR_Y = "y";

    private static final String ATTR_SPAN_X = "spanX";
    private static final String ATTR_SPAN_Y = "spanY";
    private static final String ATTR_ICON = "icon";
    private static final String ATTR_URL = "url";

    // Attrs for "Include"
    private static final String ATTR_WORKSPACE = "workspace";

    // Style attrs -- "Extra"
    private static final String ATTR_KEY = "key";
    private static final String ATTR_VALUE = "value";

    private static final String HOTSEAT_CONTAINER_NAME =
            Favorites.containerToString(Favorites.CONTAINER_HOTSEAT);

    private static final String ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE =
            "com.android.launcher.action.APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE";

    @Thunk final Context mContext;
    @Thunk final AppWidgetHost mAppWidgetHost;
    protected final LayoutParserCallback mCallback;

    protected final PackageManager mPackageManager;
    protected final Resources mSourceRes;
    protected final int mLayoutId;

    private final int mHotseatAllAppsRank;
    private final int mRowCount;
    private final int mColumnCount;

    private final long[] mTemp = new long[2];
    @Thunk final ContentValues mValues;
    protected final String mRootTag;

    protected SQLiteDatabase mDb;

    public AutoInstallsLayout(Context context, AppWidgetHost appWidgetHost,
            LayoutParserCallback callback, Resources res,
            int layoutId, String rootTag) {
        mContext = context;
        mAppWidgetHost = appWidgetHost;
        mCallback = callback;

        mPackageManager = context.getPackageManager();
        mValues = new ContentValues();
        mRootTag = rootTag;

        mSourceRes = res;
        mLayoutId = layoutId;

        InvariantDeviceProfile idp = LauncherAppState.getInstance().getInvariantDeviceProfile();
        mHotseatAllAppsRank = idp.hotseatAllAppsRank;
        mRowCount = idp.numRows;
        mColumnCount = idp.numColumns;
    }

    /**
     * Loads the layout in the db and returns the number of entries added on the desktop.
     */
    public int loadLayout(SQLiteDatabase db, ArrayList<Long> screenIds) {
        mDb = db;
        try {
            return parseLayout(mLayoutId, screenIds);
        } catch (Exception e) {
            Log.w(TAG, "Got exception parsing layout.", e);
            return -1;
        }
    }

    /**
     * Parses the layout and returns the number of elements added on the homescreen.
     */
    protected int parseLayout(int layoutId, ArrayList<Long> screenIds)
            throws XmlPullParserException, IOException {
        XmlResourceParser parser = mSourceRes.getXml(layoutId);
        beginDocument(parser, mRootTag);
        final int depth = parser.getDepth();
        int type;
        HashMap<String, TagParser> tagParserMap = getLayoutElementsMap();
        int count = 0;

        while (((type = parser.next()) != XmlPullParser.END_TAG ||
                parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            count += parseAndAddNode(parser, tagParserMap, screenIds);
        }
        return count;
    }

    /**
     * Parses container and screenId attribute from the current tag, and puts it in the out.
     * @param out array of size 2.
     */
    protected void parseContainerAndScreen(XmlResourceParser parser, long[] out) {
        if (HOTSEAT_CONTAINER_NAME.equals(getAttributeValue(parser, ATTR_CONTAINER))) {
            out[0] = Favorites.CONTAINER_HOTSEAT;
            // Hack: hotseat items are stored using screen ids
            long rank = Long.parseLong(getAttributeValue(parser, ATTR_RANK));
            out[1] = (rank < mHotseatAllAppsRank) ? rank : (rank + 1);
        } else {
            out[0] = Favorites.CONTAINER_DESKTOP;
            out[1] = Long.parseLong(getAttributeValue(parser, ATTR_SCREEN));
        }
    }

    /**
     * Parses the current node and returns the number of elements added.
     */
    protected int parseAndAddNode(
            XmlResourceParser parser,
            HashMap<String, TagParser> tagParserMap,
            ArrayList<Long> screenIds)
                    throws XmlPullParserException, IOException {

        if (TAG_INCLUDE.equals(parser.getName())) {
            final int resId = getAttributeResourceValue(parser, ATTR_WORKSPACE, 0);
            if (resId != 0) {
                // recursively load some more favorites, why not?
                return parseLayout(resId, screenIds);
            } else {
                return 0;
            }
        }

        mValues.clear();
        parseContainerAndScreen(parser, mTemp);
        final long container = mTemp[0];
        final long screenId = mTemp[1];

        mValues.put(Favorites.CONTAINER, container);
        mValues.put(Favorites.SCREEN, screenId);

        mValues.put(Favorites.CELLX,
                convertToDistanceFromEnd(getAttributeValue(parser, ATTR_X), mColumnCount));
        mValues.put(Favorites.CELLY,
                convertToDistanceFromEnd(getAttributeValue(parser, ATTR_Y), mRowCount));

        TagParser tagParser = tagParserMap.get(parser.getName());
        if (tagParser == null) {
            if (LOGD) Log.d(TAG, "Ignoring unknown element tag: " + parser.getName());
            return 0;
        }
        long newElementId = tagParser.parseAndAdd(parser);
        if (newElementId >= 0) {
            // Keep track of the set of screens which need to be added to the db.
            if (!screenIds.contains(screenId) &&
                    container == Favorites.CONTAINER_DESKTOP) {
                screenIds.add(screenId);
            }
            return 1;
        }
        return 0;
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
        parsers.put(TAG_SHORTCUT, new ShortcutParser(mSourceRes));
        return parsers;
    }

    protected HashMap<String, TagParser> getLayoutElementsMap() {
        HashMap<String, TagParser> parsers = new HashMap<String, TagParser>();
        parsers.put(TAG_APP_ICON, new AppShortcutParser());
        parsers.put(TAG_AUTO_INSTALL, new AutoInstallParser());
        parsers.put(TAG_FOLDER, new FolderParser());
        parsers.put(TAG_APPWIDGET, new AppWidgetParser());
        parsers.put(TAG_SHORTCUT, new ShortcutParser(mSourceRes));
        return parsers;
    }

    protected interface TagParser {
        /**
         * Parses the tag and adds to the db
         * @return the id of the row added or -1;
         */
        long parseAndAdd(XmlResourceParser parser)
                throws XmlPullParserException, IOException;
    }

    /**
     * App shortcuts: required attributes packageName and className
     */
    protected class AppShortcutParser implements TagParser {

        @Override
        public long parseAndAdd(XmlResourceParser parser) {
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
                    Log.e(TAG, "Unable to add favorite: " + packageName + "/" + className, e);
                }
                return -1;
            } else {
                return invalidPackageOrClass(parser);
            }
        }

        /**
         * Helper method to allow extending the parser capabilities
         */
        protected long invalidPackageOrClass(XmlResourceParser parser) {
            Log.w(TAG, "Skipping invalid <favorite> with no component");
            return -1;
        }
    }

    /**
     * AutoInstall: required attributes packageName and className
     */
    protected class AutoInstallParser implements TagParser {

        @Override
        public long parseAndAdd(XmlResourceParser parser) {
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

    /**
     * Parses a web shortcut. Required attributes url, icon, title
     */
    protected class ShortcutParser implements TagParser {

        private final Resources mIconRes;

        public ShortcutParser(Resources iconRes) {
            mIconRes = iconRes;
        }

        @Override
        public long parseAndAdd(XmlResourceParser parser) {
            final int titleResId = getAttributeResourceValue(parser, ATTR_TITLE, 0);
            final int iconId = getAttributeResourceValue(parser, ATTR_ICON, 0);

            if (titleResId == 0 || iconId == 0) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut");
                return -1;
            }

            final Intent intent = parseIntent(parser);
            if (intent == null) {
                return -1;
            }

            Drawable icon = mIconRes.getDrawable(iconId);
            if (icon == null) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut, can't load icon");
                return -1;
            }

            ItemInfo.writeBitmap(mValues, Utilities.createIconBitmap(icon, mContext));
            mValues.put(Favorites.ICON_TYPE, Favorites.ICON_TYPE_RESOURCE);
            mValues.put(Favorites.ICON_PACKAGE, mIconRes.getResourcePackageName(iconId));
            mValues.put(Favorites.ICON_RESOURCE, mIconRes.getResourceName(iconId));

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return addShortcut(mSourceRes.getString(titleResId),
                    intent, Favorites.ITEM_TYPE_SHORTCUT);
        }

        protected Intent parseIntent(XmlResourceParser parser) {
            final String url = getAttributeValue(parser, ATTR_URL);
            if (TextUtils.isEmpty(url) || !Patterns.WEB_URL.matcher(url).matches()) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut, invalid url: " + url);
                return null;
            }
            return new Intent(Intent.ACTION_VIEW, null).setData(Uri.parse(url));
        }
    }

    /**
     * AppWidget parser: Required attributes packageName, className, spanX and spanY.
     * Options child nodes: <extra key=... value=... />
     */
    protected class AppWidgetParser implements TagParser {

        @Override
        public long parseAndAdd(XmlResourceParser parser)
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

    protected class FolderParser implements TagParser {
        private final HashMap<String, TagParser> mFolderElements;

        public FolderParser() {
            this(getFolderElementsMap());
        }

        public FolderParser(HashMap<String, TagParser> elements) {
            mFolderElements = elements;
        }

        @Override
        public long parseAndAdd(XmlResourceParser parser)
                throws XmlPullParserException, IOException {
            final String title;
            final int titleResId = getAttributeResourceValue(parser, ATTR_TITLE, 0);
            if (titleResId != 0) {
                title = mSourceRes.getString(titleResId);
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
            int rank = 0;
            while ((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > folderDepth) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                mValues.clear();
                mValues.put(Favorites.CONTAINER, folderId);
                mValues.put(Favorites.RANK, rank);

                TagParser tagParser = mFolderElements.get(parser.getName());
                if (tagParser != null) {
                    final long id = tagParser.parseAndAdd(parser);
                    if (id >= 0) {
                        folderItems.add(id);
                        rank++;
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
                Uri uri = Favorites.getContentUri(folderId);
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

    protected static final void beginDocument(XmlPullParser parser, String firstElementName)
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

    private static String convertToDistanceFromEnd(String value, int endValue) {
        if (!TextUtils.isEmpty(value)) {
            int x = Integer.parseInt(value);
            if (x < 0) {
                return Integer.toString(endValue + x);
            }
        }
        return value;
    }

    /**
     * Return attribute value, attempting launcher-specific namespace first
     * before falling back to anonymous attribute.
     */
    protected static String getAttributeValue(XmlResourceParser parser, String attribute) {
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
    protected static int getAttributeResourceValue(XmlResourceParser parser, String attribute,
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

    @Thunk static void copyInteger(ContentValues from, ContentValues to, String key) {
        to.put(key, from.getAsInteger(key));
    }
}
