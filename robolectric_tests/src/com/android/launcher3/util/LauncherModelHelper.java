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

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;

import com.android.launcher3.AppInfo;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;

import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
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

    private final HashMap<Class, HashMap<String, Field>> mFieldCache = new HashMap<>();
    public final TestLauncherProvider provider;

    private BgDataModel mDataModel;
    private AllAppsList mAllAppsList;

    public LauncherModelHelper() {
        provider = Robolectric.setupContentProvider(TestLauncherProvider.class);
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

    /**
     * Adds a dummy item in the DB.
     * @param type {@link #APP_ICON} or {@link #SHORTCUT} or >= 2 for
     *             folder (where the type represents the number of items in the folder).
     */
    public int addItem(int type, int screen, int container, int x, int y) {
        Context context = RuntimeEnvironment.application;
        int id = LauncherSettings.Settings.call(context.getContentResolver(),
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

        context.getContentResolver().insert(LauncherSettings.Favorites.CONTENT_URI, values);
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
        Context context = RuntimeEnvironment.application;
        LauncherSettings.Settings.call(context.getContentResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
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
                        ids[i][y][x] = addItem(typeArray[i][y][x], screenId, DESKTOP, x, y);
                    }
                }
            }
        }

        return ids;
    }
}
