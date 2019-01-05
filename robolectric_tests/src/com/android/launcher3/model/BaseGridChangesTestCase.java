package com.android.launcher3.model;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.util.TestLauncherProvider;

import org.junit.Before;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowLog;

public abstract class BaseGridChangesTestCase {


    public static final int DESKTOP = LauncherSettings.Favorites.CONTAINER_DESKTOP;
    public static final int HOTSEAT = LauncherSettings.Favorites.CONTAINER_HOTSEAT;

    public static final int APP_ICON = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    public static final int SHORTCUT = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    public static final int NO__ICON = -1;

    public static final String TEST_PACKAGE = "com.android.launcher3.validpackage";

    public Context mContext;
    public TestLauncherProvider mProvider;
    public SQLiteDatabase mDb;

    @Before
    public void setUpBaseCase() {
        ShadowLog.stream = System.out;

        mContext = RuntimeEnvironment.application;
        mProvider = Robolectric.setupContentProvider(TestLauncherProvider.class);
        ShadowContentResolver.registerProviderInternal(LauncherProvider.AUTHORITY, mProvider);
        mDb = mProvider.getDb();
    }

    /**
     * Adds a dummy item in the DB.
     * @param type {@link #APP_ICON} or {@link #SHORTCUT} or >= 2 for
     *             folder (where the type represents the number of items in the folder).
     */
    public int addItem(int type, int screen, int container, int x, int y) {
        int id = LauncherSettings.Settings.call(mContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getInt(LauncherSettings.Settings.EXTRA_VALUE);

        ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites._ID, id);
        values.put(LauncherSettings.Favorites.CONTAINER, container);
        values.put(LauncherSettings.Favorites.SCREEN, screen);
        values.put(LauncherSettings.Favorites.CELLX, x);
        values.put(LauncherSettings.Favorites.CELLY, y);
        values.put(LauncherSettings.Favorites.SPANX, 1);
        values.put(LauncherSettings.Favorites.SPANY, 1);

        if (type == APP_ICON || type == SHORTCUT) {
            values.put(LauncherSettings.Favorites.ITEM_TYPE, type);
            values.put(LauncherSettings.Favorites.INTENT,
                    new Intent(Intent.ACTION_MAIN).setPackage(TEST_PACKAGE).toUri(0));
        } else {
            values.put(LauncherSettings.Favorites.ITEM_TYPE,
                    LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
            // Add folder items.
            for (int i = 0; i < type; i++) {
                addItem(APP_ICON, 0, id, 0, 0);
            }
        }

        mContext.getContentResolver().insert(LauncherSettings.Favorites.CONTENT_URI, values);
        return id;
    }

    public int[][][] createGrid(int[][][] typeArray) {
        return createGrid(typeArray, 1);
    }

    /**
     * Initializes the DB with dummy elements to represent the provided grid structure.
     * @param typeArray A 3d array of item types. {@see #addItem(int, long, long, int, int)} for
     *                  type definitions. The first dimension represents the screens and the next
     *                  two represent the workspace grid.
     * @param startScreen First screen id from where the icons will be added.
     * @return the same grid representation where each entry is the corresponding item id.
     */
    public int[][][] createGrid(int[][][] typeArray, int startScreen) {
        LauncherSettings.Settings.call(mContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        int[][][] ids = new int[typeArray.length][][];

        for (int i = 0; i < typeArray.length; i++) {
            // Add screen to DB
            int screenId = startScreen + i;

            // Keep the screen id counter up to date
            LauncherSettings.Settings.call(mContext.getContentResolver(),
                    LauncherSettings.Settings.METHOD_NEW_SCREEN_ID);

            ids[i] = new int[typeArray[i].length][];
            for (int y = 0; y < typeArray[i].length; y++) {
                ids[i][y] = new int[typeArray[i][y].length];
                for (int x = 0; x < typeArray[i][y].length; x++) {
                    if (typeArray[i][y][x] < 0) {
                        // Empty cell
                        ids[i][y][x] = -1;
                    } else {
                        ids[i][y][x] = addItem(typeArray[i][y][x], screenId, DESKTOP, x, y);
                    }
                }
            }
        }

        return ids;
    }
}
