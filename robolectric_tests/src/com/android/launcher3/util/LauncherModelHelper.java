/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.util;

import static android.content.Intent.ACTION_CREATE_SHORTCUT;

import static com.android.launcher3.LauncherSettings.Favorites.CONTENT_URI;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;

import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Utility class to help manage Launcher Model and related objects for test.
 */
public class LauncherModelHelper {

    public static final int DESKTOP = LauncherSettings.Favorites.CONTAINER_DESKTOP;
    public static final int HOTSEAT = LauncherSettings.Favorites.CONTAINER_HOTSEAT;

    public static final int APP_ICON = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
    public static final int SHORTCUT = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    public static final int NO__ICON = -1;
    public static final String TEST_PACKAGE = "com.android.launcher3.validpackage";

    // Authority for providing a dummy default-workspace-layout data.
    private static final String TEST_PROVIDER_AUTHORITY =
            LauncherModelHelper.class.getName().toLowerCase();
    private static final int DEFAULT_BITMAP_SIZE = 10;
    private static final int DEFAULT_GRID_SIZE = 4;

    private final HashMap<Class, HashMap<String, Field>> mFieldCache = new HashMap<>();
    public final TestLauncherProvider provider;
    private final long mDefaultProfileId;

    private BgDataModel mDataModel;
    private AllAppsList mAllAppsList;

    public LauncherModelHelper() {
        provider = Robolectric.setupContentProvider(TestLauncherProvider.class);
        mDefaultProfileId = UserCache.INSTANCE.get(RuntimeEnvironment.application)
                .getSerialNumberForUser(Process.myUserHandle());
        ShadowContentResolver.registerProviderInternal(LauncherProvider.AUTHORITY, provider);
    }

    public LauncherModel getModel() {
        return LauncherAppState.getInstance(RuntimeEnvironment.application).getModel();
    }

    public synchronized BgDataModel getBgDataModel() {
        if (mDataModel == null) {
            mDataModel = ReflectionHelpers.getField(getModel(), "mBgDataModel");
        }
        return mDataModel;
    }

    public synchronized AllAppsList getAllAppsList() {
        if (mAllAppsList == null) {
            mAllAppsList = ReflectionHelpers.getField(getModel(), "mBgAllAppsList");
        }
        return mAllAppsList;
    }

    /**
     * Synchronously executes the task and returns all the UI callbacks posted.
     */
    public List<Runnable> executeTaskForTest(ModelUpdateTask task) throws Exception {
        LauncherModel model = getModel();
        if (!model.isModelLoaded()) {
            ReflectionHelpers.setField(model, "mModelLoaded", true);
        }
        Executor mockExecutor = mock(Executor.class);
        model.enqueueModelUpdateTask(new ModelUpdateTask() {
            @Override
            public void init(LauncherAppState app, LauncherModel model, BgDataModel dataModel,
                    AllAppsList allAppsList, Executor uiExecutor) {
                task.init(app, model, dataModel, allAppsList, mockExecutor);
            }

            @Override
            public void run() {
                task.run();
            }
        });
        MODEL_EXECUTOR.submit(() -> null).get();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(mockExecutor, atLeast(0)).execute(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Synchronously executes a task on the model
     */
    public <T> T executeSimpleTask(Function<BgDataModel, T> task) throws Exception {
        BgDataModel dataModel = getBgDataModel();
        return MODEL_EXECUTOR.submit(() -> task.apply(dataModel)).get();
    }

    /**
     * Initializes mock data for the test.
     */
    public void initializeData(String resourceName) throws Exception {
        Context targetContext = RuntimeEnvironment.application;
        BgDataModel bgDataModel = getBgDataModel();
        AllAppsList allAppsList = getAllAppsList();

        MODEL_EXECUTOR.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    this.getClass().getResourceAsStream(resourceName)))) {
                String line;
                HashMap<String, Class> classMap = new HashMap<>();
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    String[] commands = line.split(" ");
                    switch (commands[0]) {
                        case "classMap":
                            classMap.put(commands[1], Class.forName(commands[2]));
                            break;
                        case "bgItem":
                            bgDataModel.addItem(targetContext,
                                    (ItemInfo) initItem(classMap.get(commands[1]), commands, 2),
                                    false);
                            break;
                        case "allApps":
                            allAppsList.add((AppInfo) initItem(AppInfo.class, commands, 1), null);
                            break;
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get();
    }

    private Object initItem(Class clazz, String[] fieldDef, int startIndex) throws Exception {
        HashMap<String, Field> cache = mFieldCache.get(clazz);
        if (cache == null) {
            cache = new HashMap<>();
            Class c = clazz;
            while (c != null) {
                for (Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    cache.put(f.getName(), f);
                }
                c = c.getSuperclass();
            }
            mFieldCache.put(clazz, cache);
        }

        Object item = clazz.newInstance();
        for (int i = startIndex; i < fieldDef.length; i++) {
            String[] fieldData = fieldDef[i].split("=", 2);
            Field f = cache.get(fieldData[0]);
            Class type = f.getType();
            if (type == int.class || type == long.class) {
                f.set(item, Integer.parseInt(fieldData[1]));
            } else if (type == CharSequence.class || type == String.class) {
                f.set(item, fieldData[1]);
            } else if (type == Intent.class) {
                if (!fieldData[1].startsWith("#Intent")) {
                    fieldData[1] = "#Intent;" + fieldData[1] + ";end";
                }
                f.set(item, Intent.parseUri(fieldData[1], 0));
            } else if (type == ComponentName.class) {
                f.set(item, ComponentName.unflattenFromString(fieldData[1]));
            } else {
                throw new Exception("Added parsing logic for "
                        + f.getName() + " of type " + f.getType());
            }
        }
        return item;
    }

    public int addItem(int type, int screen, int container, int x, int y) {
        return addItem(type, screen, container, x, y, mDefaultProfileId, TEST_PACKAGE);
    }

    public int addItem(int type, int screen, int container, int x, int y, long profileId) {
        return addItem(type, screen, container, x, y, profileId, TEST_PACKAGE);
    }

    public int addItem(int type, int screen, int container, int x, int y, String packageName) {
        return addItem(type, screen, container, x, y, mDefaultProfileId, packageName);
    }

    public int addItem(int type, int screen, int container, int x, int y, String packageName,
            int id, Uri contentUri) {
        addItem(type, screen, container, x, y, mDefaultProfileId, packageName, id, contentUri);
        return id;
    }

    /**
     * Adds a dummy item in the DB.
     * @param type {@link #APP_ICON} or {@link #SHORTCUT} or >= 2 for
     *             folder (where the type represents the number of items in the folder).
     */
    public int addItem(int type, int screen, int container, int x, int y, long profileId,
            String packageName) {
        Context context = RuntimeEnvironment.application;
        int id = LauncherSettings.Settings.call(context.getContentResolver(),
                LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getInt(LauncherSettings.Settings.EXTRA_VALUE);
        addItem(type, screen, container, x, y, profileId, packageName, id, CONTENT_URI);
        return id;
    }

    public void addItem(int type, int screen, int container, int x, int y, long profileId,
            String packageName, int id, Uri contentUri) {
        Context context = RuntimeEnvironment.application;

        ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites._ID, id);
        values.put(LauncherSettings.Favorites.CONTAINER, container);
        values.put(LauncherSettings.Favorites.SCREEN, screen);
        values.put(LauncherSettings.Favorites.CELLX, x);
        values.put(LauncherSettings.Favorites.CELLY, y);
        values.put(LauncherSettings.Favorites.SPANX, 1);
        values.put(LauncherSettings.Favorites.SPANY, 1);
        values.put(LauncherSettings.Favorites.PROFILE_ID, profileId);

        if (type == APP_ICON || type == SHORTCUT) {
            values.put(LauncherSettings.Favorites.ITEM_TYPE, type);
            values.put(LauncherSettings.Favorites.INTENT,
                    new Intent(Intent.ACTION_MAIN).setPackage(packageName).toUri(0));
        } else {
            values.put(LauncherSettings.Favorites.ITEM_TYPE,
                    LauncherSettings.Favorites.ITEM_TYPE_FOLDER);
            // Add folder items.
            for (int i = 0; i < type; i++) {
                addItem(APP_ICON, 0, id, 0, 0, profileId);
            }
        }

        context.getContentResolver().insert(contentUri, values);
    }

    public int[][][] createGrid(int[][][] typeArray) {
        return createGrid(typeArray, 1);
    }

    public int[][][] createGrid(int[][][] typeArray, int startScreen) {
        final Context context = RuntimeEnvironment.application;
        LauncherSettings.Settings.call(context.getContentResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        LauncherSettings.Settings.call(context.getContentResolver(),
                LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG);
        return createGrid(typeArray, startScreen, mDefaultProfileId);
    }

    /**
     * Initializes the DB with dummy elements to represent the provided grid structure.
     * @param typeArray A 3d array of item types. {@see #addItem(int, long, long, int, int)} for
     *                  type definitions. The first dimension represents the screens and the next
     *                  two represent the workspace grid.
     * @param startScreen First screen id from where the icons will be added.
     * @return the same grid representation where each entry is the corresponding item id.
     */
    public int[][][] createGrid(int[][][] typeArray, int startScreen, long profileId) {
        Context context = RuntimeEnvironment.application;
        int[][][] ids = new int[typeArray.length][][];
        for (int i = 0; i < typeArray.length; i++) {
            // Add screen to DB
            int screenId = startScreen + i;

            // Keep the screen id counter up to date
            LauncherSettings.Settings.call(context.getContentResolver(),
                    LauncherSettings.Settings.METHOD_NEW_SCREEN_ID);

            ids[i] = new int[typeArray[i].length][];
            for (int y = 0; y < typeArray[i].length; y++) {
                ids[i][y] = new int[typeArray[i][y].length];
                for (int x = 0; x < typeArray[i][y].length; x++) {
                    if (typeArray[i][y][x] < 0) {
                        // Empty cell
                        ids[i][y][x] = -1;
                    } else {
                        ids[i][y][x] = addItem(
                                typeArray[i][y][x], screenId, DESKTOP, x, y, profileId);
                    }
                }
            }
        }

        return ids;
    }

    /**
     * Sets up a dummy provider to load the provided layout by default, next time the layout loads
     */
    public LauncherModelHelper setupDefaultLayoutProvider(LauncherLayoutBuilder builder)
            throws Exception {
        Context context = RuntimeEnvironment.application;
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
        idp.numRows = idp.numColumns = idp.numHotseatIcons = DEFAULT_GRID_SIZE;
        idp.iconBitmapSize = DEFAULT_BITMAP_SIZE;

        Settings.Secure.putString(context.getContentResolver(),
                "launcher3.layout.provider", TEST_PROVIDER_AUTHORITY);

        shadowOf(context.getPackageManager())
                .addProviderIfNotPresent(new ComponentName("com.test", "Dummy")).authority =
                TEST_PROVIDER_AUTHORITY;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        builder.build(new OutputStreamWriter(bos));
        Uri layoutUri = LauncherProvider.getLayoutUri(TEST_PROVIDER_AUTHORITY, context);
        shadowOf(context.getContentResolver()).registerInputStream(layoutUri,
                new ByteArrayInputStream(bos.toByteArray()));
        return this;
    }

    /**
     * Simulates an apk install with a default main activity with same class and package name
     */
    public void installApp(String component) throws NameNotFoundException {
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);
        installApp(component, component, filter);
    }

    /**
     * Simulates a custom shortcut install
     */
    public void installCustomShortcut(String pkg, String clazz) throws NameNotFoundException {
        installApp(pkg, clazz, new IntentFilter(ACTION_CREATE_SHORTCUT));
    }

    private void installApp(String pkg, String clazz, IntentFilter filter)
            throws NameNotFoundException {
        ShadowPackageManager spm = shadowOf(RuntimeEnvironment.application.getPackageManager());
        ComponentName cn = new ComponentName(pkg, clazz);
        spm.addActivityIfNotPresent(cn);

        filter.addCategory(Intent.CATEGORY_DEFAULT);
        spm.addIntentFilterForActivity(cn, filter);
    }

    /**
     * Loads the model in memory synchronously
     */
    public void loadModelSync() throws ExecutionException, InterruptedException {
        // Since robolectric tests run on main thread, we run the loader-UI calls on a temp thread,
        // so that we can wait appropriately for the loader to complete.
        ReflectionHelpers.setField(getModel(), "mMainExecutor", Executors.UI_HELPER_EXECUTOR);

        Callbacks mockCb = mock(Callbacks.class);
        getModel().addCallbacksAndLoad(mockCb);

        Executors.MODEL_EXECUTOR.submit(() -> { }).get();
        Executors.UI_HELPER_EXECUTOR.submit(() -> { }).get();
        ReflectionHelpers.setField(getModel(), "mMainExecutor", Executors.MAIN_EXECUTOR);
        getModel().removeCallbacks(mockCb);
    }

    /**
     * An extension of LauncherProvider backed up by in-memory database.
     */
    public static class TestLauncherProvider extends LauncherProvider {

        @Override
        public boolean onCreate() {
            return true;
        }

        public SQLiteDatabase getDb() {
            createDbIfNotExists();
            return mOpenHelper.getWritableDatabase();
        }

        public DatabaseHelper getHelper() {
            return mOpenHelper;
        }
    }
}
