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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.LauncherSettings.Favorites.CONTENT_URI;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.os.Process;
import android.provider.Settings;
import android.test.mock.MockContentResolver;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.testing.TestInformationProvider;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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

    public static final String TEST_PACKAGE = testContext().getPackageName();
    public static final String TEST_ACTIVITY = "com.android.launcher3.tests.Activity2";

    // Authority for providing a test default-workspace-layout data.
    private static final String TEST_PROVIDER_AUTHORITY =
            LauncherModelHelper.class.getName().toLowerCase();
    private static final int DEFAULT_BITMAP_SIZE = 10;
    private static final int DEFAULT_GRID_SIZE = 4;

    private final HashMap<Class, HashMap<String, Field>> mFieldCache = new HashMap<>();
    private final MockContentResolver mMockResolver = new MockContentResolver();
    public final TestLauncherProvider provider;
    public final SanboxModelContext sandboxContext;

    public final long defaultProfileId;

    private BgDataModel mDataModel;
    private AllAppsList mAllAppsList;

    public LauncherModelHelper() {
        Context context = getApplicationContext();
        // System settings cache content provider. Ensure that they are statically initialized
        Settings.Secure.getString(context.getContentResolver(), "test");
        Settings.System.getString(context.getContentResolver(), "test");
        Settings.Global.getString(context.getContentResolver(), "test");

        provider = new TestLauncherProvider();
        sandboxContext = new SanboxModelContext();
        defaultProfileId = UserCache.INSTANCE.get(sandboxContext)
                .getSerialNumberForUser(Process.myUserHandle());
        setupProvider(LauncherProvider.AUTHORITY, provider);
    }

    protected void setupProvider(String authority, ContentProvider provider) {
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = authority;
        providerInfo.applicationInfo = sandboxContext.getApplicationInfo();
        provider.attachInfo(sandboxContext, providerInfo);
        mMockResolver.addProvider(providerInfo.authority, provider);
        doReturn(providerInfo)
                .when(sandboxContext.mPm)
                .resolveContentProvider(eq(authority), anyInt());
    }

    public LauncherModel getModel() {
        return LauncherAppState.getInstance(sandboxContext).getModel();
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

    public void destroy() {
        // When destroying the context, make sure that the model thread is blocked, so that no
        // new jobs get posted while we are cleaning up
        CountDownLatch l1 = new CountDownLatch(1);
        CountDownLatch l2 = new CountDownLatch(1);
        MODEL_EXECUTOR.execute(() -> {
            l1.countDown();
            waitOrThrow(l2);
        });
        waitOrThrow(l1);
        sandboxContext.onDestroy();
        l2.countDown();
    }

    private void waitOrThrow(CountDownLatch latch) {
        try {
            latch.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            public void init(@NonNull final LauncherAppState app,
                    @NonNull final LauncherModel model, @NonNull final BgDataModel dataModel,
                    @NonNull final AllAppsList allAppsList, @NonNull final Executor uiExecutor) {
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
        BgDataModel bgDataModel = getBgDataModel();
        AllAppsList allAppsList = getAllAppsList();

        MODEL_EXECUTOR.submit(() -> {
            // Copy apk from resources to a local file and install from there.
            Resources resources = testContext().getResources();
            int resId = resources.getIdentifier(
                    resourceName, "raw", testContext().getPackageName());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    resources.openRawResource(resId)))) {
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
                            bgDataModel.addItem(sandboxContext,
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
        return addItem(type, screen, container, x, y, defaultProfileId, TEST_PACKAGE);
    }

    public int addItem(int type, int screen, int container, int x, int y, long profileId) {
        return addItem(type, screen, container, x, y, profileId, TEST_PACKAGE);
    }

    public int addItem(int type, int screen, int container, int x, int y, String packageName) {
        return addItem(type, screen, container, x, y, defaultProfileId, packageName);
    }

    public int addItem(int type, int screen, int container, int x, int y, String packageName,
            int id, Uri contentUri) {
        addItem(type, screen, container, x, y, defaultProfileId, packageName, id, contentUri);
        return id;
    }

    /**
     * Adds a mock item in the DB.
     * @param type {@link #APP_ICON} or {@link #SHORTCUT} or >= 2 for
     *             folder (where the type represents the number of items in the folder).
     */
    public int addItem(int type, int screen, int container, int x, int y, long profileId,
            String packageName) {
        int id = LauncherSettings.Settings.call(sandboxContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getInt(LauncherSettings.Settings.EXTRA_VALUE);
        addItem(type, screen, container, x, y, profileId, packageName, id, CONTENT_URI);
        return id;
    }

    public void addItem(int type, int screen, int container, int x, int y, long profileId,
            String packageName, int id, Uri contentUri) {
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

        sandboxContext.getContentResolver().insert(contentUri, values);
    }

    public void deleteItem(int itemId, @NonNull final String tableName) {
        final Uri uri = Uri.parse("content://"
                + LauncherProvider.AUTHORITY + "/" + tableName + "/" + itemId);
        sandboxContext.getContentResolver().delete(uri, null, null);
    }

    public int[][][] createGrid(int[][][] typeArray) {
        return createGrid(typeArray, 1);
    }

    public int[][][] createGrid(int[][][] typeArray, int startScreen) {
        LauncherSettings.Settings.call(sandboxContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        LauncherSettings.Settings.call(sandboxContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG);
        return createGrid(typeArray, startScreen, defaultProfileId);
    }

    /**
     * Initializes the DB with mock elements to represent the provided grid structure.
     * @param typeArray A 3d array of item types. {@see #addItem(int, long, long, int, int)} for
     *                  type definitions. The first dimension represents the screens and the next
     *                  two represent the workspace grid.
     * @param startScreen First screen id from where the icons will be added.
     * @return the same grid representation where each entry is the corresponding item id.
     */
    public int[][][] createGrid(int[][][] typeArray, int startScreen, long profileId) {
        int[][][] ids = new int[typeArray.length][][];
        for (int i = 0; i < typeArray.length; i++) {
            // Add screen to DB
            int screenId = startScreen + i;

            // Keep the screen id counter up to date
            LauncherSettings.Settings.call(sandboxContext.getContentResolver(),
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
     * Sets up a mock provider to load the provided layout by default, next time the layout loads
     */
    public LauncherModelHelper setupDefaultLayoutProvider(LauncherLayoutBuilder builder)
            throws Exception {
        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(sandboxContext);
        idp.numRows = idp.numColumns = idp.numDatabaseHotseatIcons = DEFAULT_GRID_SIZE;
        idp.iconBitmapSize = DEFAULT_BITMAP_SIZE;

        UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                "settings put secure launcher3.layout.provider " + TEST_PROVIDER_AUTHORITY);
        ContentProvider cp = new TestInformationProvider() {

            @Override
            public ParcelFileDescriptor openFile(Uri uri, String mode)
                    throws FileNotFoundException {
                try {
                    ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                    AutoCloseOutputStream outputStream = new AutoCloseOutputStream(pipe[1]);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    builder.build(new OutputStreamWriter(bos));
                    outputStream.write(bos.toByteArray());
                    outputStream.flush();
                    outputStream.close();
                    return pipe[0];
                } catch (Exception e) {
                    throw new FileNotFoundException(e.getMessage());
                }
            }
        };
        setupProvider(TEST_PROVIDER_AUTHORITY, cp);
        return this;
    }

    /**
     * Loads the model in memory synchronously
     */
    public void loadModelSync() throws ExecutionException, InterruptedException {
        Callbacks mockCb = new Callbacks() { };
        Executors.MAIN_EXECUTOR.submit(() -> getModel().addCallbacksAndLoad(mockCb)).get();

        Executors.MODEL_EXECUTOR.submit(() -> { }).get();
        Executors.MAIN_EXECUTOR.submit(() -> { }).get();
        Executors.MAIN_EXECUTOR.submit(() -> getModel().removeCallbacks(mockCb)).get();
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

    public static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    success = false;
                }
            }
        }
        return success;
    }

    public class SanboxModelContext extends SandboxContext {

        private final ArrayMap<String, Object> mSpiedServices = new ArrayMap<>();
        private final PackageManager mPm;
        private final File mDbDir;

        SanboxModelContext() {
            super(ApplicationProvider.getApplicationContext(),
                    UserCache.INSTANCE, InstallSessionHelper.INSTANCE,
                    LauncherAppState.INSTANCE, InvariantDeviceProfile.INSTANCE,
                    DisplayController.INSTANCE, CustomWidgetManager.INSTANCE,
                    SettingsCache.INSTANCE, PluginManagerWrapper.INSTANCE,
                    ItemInstallQueue.INSTANCE, WindowManagerProxy.INSTANCE);
            mPm = spy(getBaseContext().getPackageManager());
            mDbDir = new File(getCacheDir(), UUID.randomUUID().toString());
        }

        public SanboxModelContext allow(MainThreadInitializedObject object) {
            mAllowedObjects.add(object);
            return this;
        }

        @Override
        public File getDatabasePath(String name) {
            if (!mDbDir.exists()) {
                mDbDir.mkdirs();
            }
            return new File(mDbDir, name);
        }

        @Override
        public ContentResolver getContentResolver() {
            return mMockResolver;
        }

        @Override
        public void onDestroy() {
            if (deleteContents(mDbDir)) {
                mDbDir.delete();
            }
            super.onDestroy();
        }

        @Override
        public PackageManager getPackageManager() {
            return mPm;
        }

        @Override
        public Object getSystemService(String name) {
            Object service = mSpiedServices.get(name);
            return service != null ? service : super.getSystemService(name);
        }

        public <T> T spyService(Class<T> tClass) {
            String name = getSystemServiceName(tClass);
            Object service = mSpiedServices.get(name);
            if (service != null) {
                return (T) service;
            }

            T result = spy(getSystemService(tClass));
            mSpiedServices.put(name, result);
            return result;
        }
    }

    private static Context testContext() {
        return getInstrumentation().getContext();
    }
}
