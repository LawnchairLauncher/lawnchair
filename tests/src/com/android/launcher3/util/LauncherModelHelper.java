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

import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.TestUtil.runOnExecutorSync;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
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
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.testing.TestInformationProvider;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Utility class to help manage Launcher Model and related objects for test.
 */
public class LauncherModelHelper {

    public static final String TEST_PACKAGE = getInstrumentation().getContext().getPackageName();
    public static final String TEST_ACTIVITY = "com.android.launcher3.tests.Activity2";
    public static final String TEST_ACTIVITY2 = "com.android.launcher3.tests.Activity3";
    public static final String TEST_ACTIVITY3 = "com.android.launcher3.tests.Activity4";
    public static final String TEST_ACTIVITY4 = "com.android.launcher3.tests.Activity5";
    public static final String TEST_ACTIVITY5 = "com.android.launcher3.tests.Activity6";
    public static final String TEST_ACTIVITY6 = "com.android.launcher3.tests.Activity7";
    public static final String TEST_ACTIVITY7 = "com.android.launcher3.tests.Activity8";
    public static final String TEST_ACTIVITY8 = "com.android.launcher3.tests.Activity9";
    public static final String TEST_ACTIVITY9 = "com.android.launcher3.tests.Activity10";
    public static final String TEST_ACTIVITY10 = "com.android.launcher3.tests.Activity11";
    public static final String TEST_ACTIVITY11 = "com.android.launcher3.tests.Activity12";
    public static final String TEST_ACTIVITY12 = "com.android.launcher3.tests.Activity13";
    public static final String TEST_ACTIVITY13 = "com.android.launcher3.tests.Activity14";
    public static final String TEST_ACTIVITY14 = "com.android.launcher3.tests.Activity15";

    // Authority for providing a test default-workspace-layout data.
    private static final String TEST_PROVIDER_AUTHORITY =
            LauncherModelHelper.class.getName().toLowerCase();
    private static final int DEFAULT_BITMAP_SIZE = 10;
    private static final int DEFAULT_GRID_SIZE = 4;

    public final SandboxModelContext sandboxContext;

    private final RunnableList mDestroyTask = new RunnableList();

    private BgDataModel mDataModel;

    public LauncherModelHelper() {
        sandboxContext = new SandboxModelContext();
    }

    public void setupProvider(String authority, ContentProvider provider) {
        sandboxContext.setupProvider(authority, provider);
    }

    public LauncherModel getModel() {
        return LauncherAppState.getInstance(sandboxContext).getModel();
    }

    public synchronized BgDataModel getBgDataModel() {
        if (mDataModel == null) {
            getModel().enqueueModelUpdateTask(new ModelUpdateTask() {
                @Override
                public void init(@NonNull LauncherAppState app, @NonNull LauncherModel model,
                        @NonNull BgDataModel dataModel, @NonNull AllAppsList allAppsList,
                        @NonNull Executor uiExecutor) {
                    mDataModel = dataModel;
                }

                @Override
                public void run() { }
            });
        }
        return mDataModel;
    }

    /**
     * Creates a installer session for the provided package.
     */
    public int createInstallerSession(String pkg) throws IOException {
        SessionParams sp = new SessionParams(MODE_FULL_INSTALL);
        sp.setAppPackageName(pkg);
        Bitmap icon = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        icon.eraseColor(Color.RED);
        sp.setAppIcon(icon);
        sp.setAppLabel(pkg);
        PackageInstaller pi = sandboxContext.getPackageManager().getPackageInstaller();
        int sessionId = pi.createSession(sp);
        mDestroyTask.add(() -> pi.abandonSession(sessionId));
        return sessionId;
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

        mDestroyTask.executeAllAndDestroy();
    }

    private void waitOrThrow(CountDownLatch latch) {
        try {
            latch.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        mDestroyTask.add(() -> runOnExecutorSync(MODEL_EXECUTOR, () ->
                UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                        "settings delete secure launcher3.layout.provider")));
        return this;
    }

    /**
     * Loads the model in memory synchronously
     */
    public void loadModelSync() throws ExecutionException, InterruptedException {
        Callbacks mockCb = new Callbacks() { };
        MAIN_EXECUTOR.submit(() -> getModel().addCallbacksAndLoad(mockCb)).get();

        Executors.MODEL_EXECUTOR.submit(() -> { }).get();
        MAIN_EXECUTOR.submit(() -> { }).get();
        MAIN_EXECUTOR.submit(() -> getModel().removeCallbacks(mockCb)).get();
    }

    public static class SandboxModelContext extends SandboxContext {

        private final MockContentResolver mMockResolver = new MockContentResolver();
        private final ArrayMap<String, Object> mSpiedServices = new ArrayMap<>();
        private final PackageManager mPm;
        private final File mDbDir;

        SandboxModelContext() {
            super(ApplicationProvider.getApplicationContext(),
                    UserCache.INSTANCE, InstallSessionHelper.INSTANCE, LauncherPrefs.INSTANCE,
                    LauncherAppState.INSTANCE, InvariantDeviceProfile.INSTANCE,
                    DisplayController.INSTANCE, CustomWidgetManager.INSTANCE,
                    SettingsCache.INSTANCE, PluginManagerWrapper.INSTANCE,
                    LockedUserState.INSTANCE, WallpaperColorHints.INSTANCE,
                    ItemInstallQueue.INSTANCE, WindowManagerProxy.INSTANCE);

            // System settings cache content provider. Ensure that they are statically initialized
            Settings.Secure.getString(
                    ApplicationProvider.getApplicationContext().getContentResolver(), "test");
            Settings.System.getString(
                    ApplicationProvider.getApplicationContext().getContentResolver(), "test");
            Settings.Global.getString(
                    ApplicationProvider.getApplicationContext().getContentResolver(), "test");

            mPm = spy(getBaseContext().getPackageManager());
            mDbDir = new File(getCacheDir(), UUID.randomUUID().toString());
        }

        @Override
        protected <T> T createObject(MainThreadInitializedObject<T> object) {
            if (object == LauncherAppState.INSTANCE) {
                return (T) new LauncherAppState(this, null /* iconCacheFileName */);
            }
            return super.createObject(object);
        }

        public SandboxModelContext allow(MainThreadInitializedObject object) {
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

        public void setupProvider(String authority, ContentProvider provider) {
            ProviderInfo providerInfo = new ProviderInfo();
            providerInfo.authority = authority;
            providerInfo.applicationInfo = getApplicationInfo();
            provider.attachInfo(this, providerInfo);
            mMockResolver.addProvider(providerInfo.authority, provider);
            doReturn(providerInfo).when(mPm).resolveContentProvider(eq(authority), anyInt());
        }

        private static boolean deleteContents(File dir) {
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
    }
}
