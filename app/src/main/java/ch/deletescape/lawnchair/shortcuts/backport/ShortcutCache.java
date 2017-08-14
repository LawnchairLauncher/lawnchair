package ch.deletescape.lawnchair.shortcuts.backport;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ch.deletescape.lawnchair.LauncherFiles;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.shortcuts.ShortcutInfoCompat;
import ch.deletescape.lawnchair.util.MapHashMap;
import ch.deletescape.lawnchair.util.MultiHashMap;
import ch.deletescape.lawnchair.util.SQLiteCacheHelper;

public class ShortcutCache {

    private static final String TAG = "ShortcutCache";
    private static final String[] PROJECTION = new String[]{ShortcutDB.COLUMN_COMPONENT, ShortcutDB.COLUMN_PACKAGE, ShortcutDB.COLUMN_SHORTCUT_XML};
    private final ShortcutDB mShortcutDB;
    private List<ShortcutInfoCompat> mShortcutList = new ArrayList<>();
    private MultiHashMap<String, ShortcutInfoCompat> mShortcutsMap = new MultiHashMap<>();
    private MapHashMap<String, String, ShortcutInfoCompat> mIdsMap = new MapHashMap<>();

    public ShortcutCache(Context context, LauncherApps launcherApps) {
        mShortcutDB = new ShortcutDB(context);
        long startTime = System.currentTimeMillis();
        Cursor c = mShortcutDB.query(PROJECTION, null, null);
        if (c.getCount() == 0) {
            List<LauncherActivityInfo> infoList = launcherApps.getActivityList(null, Utilities.myUserHandle());
            for (LauncherActivityInfo info : infoList) {
                String packageName = info.getComponentName().getPackageName();
                addPackage(context, packageName);
            }
        } else {
            while (c.moveToNext()) {
                ComponentName componentName = ComponentName
                        .unflattenFromString(c.getString(c.getColumnIndexOrThrow(ShortcutDB.COLUMN_COMPONENT)));
                String packageName = c.getString(c.getColumnIndexOrThrow(ShortcutDB.COLUMN_PACKAGE));
                int shortcutXml = c.getInt(c.getColumnIndexOrThrow(ShortcutDB.COLUMN_SHORTCUT_XML));
                //Log.d(TAG, componentName.flattenToString() + " -> " + shortcutXml);
                parseShortcut(context, packageName, componentName, shortcutXml);
            }
        }
        Log.d(TAG, "Took " + (System.currentTimeMillis() - startTime) + "ms to parse shortcuts");
    }

    private void parseShortcut(Context context, String packageName, ComponentName componentName, int shortcutXml) {
        try {
            Resources res = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
                    .getResources();
            ArrayList<ShortcutInfoCompat> shortcuts =
                    new ShortcutParser(context, res, packageName, componentName, shortcutXml).getShortcutsList();
            mShortcutList.addAll(shortcuts);
            mShortcutsMap.addAllToList(packageName, shortcuts);
            mIdsMap.putAllToMap(packageName, extractIds(shortcuts));
        } catch (PackageManager.NameNotFoundException ignored) {

        }
    }

    public void addPackage(Context context, String packageName) {
        //Log.d(TAG, "adding package " + packageName);
        parsePackage(context, packageName);
    }

    public boolean parsePackage(Context context, String packageName) {
        Log.d(TAG, "parsing full package " + packageName);
        try {
            ShortcutPackage shortcutPackage = new ShortcutPackage(context, packageName);
            ArrayList<ShortcutInfoCompat> shortcuts = shortcutPackage.getAllShortcuts();
            if (!shortcuts.isEmpty()) {
                mShortcutList.addAll(shortcuts);
                mShortcutsMap.put(packageName, shortcuts);
                mIdsMap.put(packageName, extractIds(shortcuts));
                Map<ComponentName, ShortcutParser> shortcutMap = shortcutPackage.getShortcutsMap();
                for (Map.Entry<ComponentName, ShortcutParser> entry : shortcutMap.entrySet()) {
                    addToDB(packageName, entry.getKey(), entry.getValue().getResId());
                }
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.d(TAG, "can't parse package " + packageName, e);
            return false;
        }
    }

    public void removePackage(String packageName) {
        Log.d(TAG, "removing package " + packageName);
        Iterator<ShortcutInfoCompat> it = mShortcutList.iterator();
        while (it.hasNext()) {
            if (packageName.equals(it.next().getPackage()))
                it.remove();
        }
        mShortcutsMap.remove(packageName);
        mIdsMap.remove(packageName);

        deleteFromDB(packageName);
    }

    private void addToDB(String packageName, ComponentName componentName, int shortcutXml) {
        ContentValues values = new ContentValues();
        values.put(ShortcutDB.COLUMN_COMPONENT, componentName.flattenToString());
        values.put(ShortcutDB.COLUMN_PACKAGE, packageName);
        values.put(ShortcutDB.COLUMN_SHORTCUT_XML, shortcutXml);
        mShortcutDB.insertOrReplace(values);
    }

    private void deleteFromDB(String packageName) {
        String selection = ShortcutDB.COLUMN_PACKAGE + " = ?";
        String[] selectionArgs = {packageName};
        mShortcutDB.delete(selection, selectionArgs);
    }

    public List<ShortcutInfoCompat> query(String packageName) {
        if (packageName == null) {
            return mShortcutList;
        } else if (mShortcutsMap.containsKey(packageName)) {
            return mShortcutsMap.get(packageName);
        } else {
            return Collections.emptyList();
        }
    }

    public ShortcutInfoCompat getShortcut(String packageName, String id) {
        return mIdsMap.get(packageName).get(id);
    }

    private HashMap<String, ShortcutInfoCompat> extractIds(List<ShortcutInfoCompat> list) {
        HashMap<String, ShortcutInfoCompat> map = new HashMap<>();
        for (ShortcutInfoCompat item : list) {
            map.put(item.getId(), item);
        }
        return map;
    }

    private static final class ShortcutDB extends SQLiteCacheHelper {
        private final static String TABLE_NAME = "shortcuts";
        private final static String COLUMN_COMPONENT = "componentName";
        private final static String COLUMN_PACKAGE = "packageName";
        private final static String COLUMN_SHORTCUT_XML = "shortcutXml";

        public ShortcutDB(Context context) {
            super(context, LauncherFiles.APP_SHORTCUTS_DB,
                    2,
                    TABLE_NAME);
        }

        @Override
        protected void onCreateTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_COMPONENT + " TEXT NOT NULL, " +
                    COLUMN_PACKAGE + " TEXT NOT NULL, " +
                    COLUMN_SHORTCUT_XML + " INTEGER NOT NULL, " +
                    "PRIMARY KEY (" + COLUMN_COMPONENT + ") " +
                    ");");
        }
    }
}
