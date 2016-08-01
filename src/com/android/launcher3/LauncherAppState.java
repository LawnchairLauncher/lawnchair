/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.ProviderConfig;
import com.android.launcher3.dynamicui.ExtractionUtils;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutCache;
import com.android.launcher3.util.ConfigMonitor;
import com.android.launcher3.util.TestingUtils;
import com.android.launcher3.util.Thunk;

import java.lang.ref.WeakReference;

public class LauncherAppState {

    public static final boolean PROFILE_STARTUP = ProviderConfig.IS_DOGFOOD_BUILD;

    private final AppFilter mAppFilter;
    @Thunk final LauncherModel mModel;
    private final IconCache mIconCache;
    private final WidgetPreviewLoader mWidgetCache;
    private final DeepShortcutManager mDeepShortcutManager;

    @Thunk boolean mWallpaperChangedSinceLastCheck;

    private static WeakReference<LauncherProvider> sLauncherProvider;
    private static Context sContext;

    private static LauncherAppState INSTANCE;

    private InvariantDeviceProfile mInvariantDeviceProfile;

    public static LauncherAppState getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LauncherAppState();
        }
        return INSTANCE;
    }

    public static LauncherAppState getInstanceNoCreate() {
        return INSTANCE;
    }

    public Context getContext() {
        return sContext;
    }

    static void setLauncherProvider(LauncherProvider provider) {
        if (sLauncherProvider != null) {
            Log.w(Launcher.TAG, "setLauncherProvider called twice! old=" +
                    sLauncherProvider.get() + " new=" + provider);
        }
        sLauncherProvider = new WeakReference<>(provider);

        // The content provider exists for the entire duration of the launcher main process and
        // is the first component to get created. Initializing application context here ensures
        // that LauncherAppState always exists in the main process.
        sContext = provider.getContext().getApplicationContext();
        FileLog.setDir(sContext.getFilesDir());
    }

    private LauncherAppState() {
        if (sContext == null) {
            throw new IllegalStateException("LauncherAppState inited before app context set");
        }

        Log.v(Launcher.TAG, "LauncherAppState inited");

        if (TestingUtils.MEMORY_DUMP_ENABLED) {
            TestingUtils.startTrackingMemory(sContext);
        }

        mInvariantDeviceProfile = new InvariantDeviceProfile(sContext);
        mIconCache = new IconCache(sContext, mInvariantDeviceProfile);
        mWidgetCache = new WidgetPreviewLoader(sContext, mIconCache);
        mDeepShortcutManager = new DeepShortcutManager(sContext, new ShortcutCache());

        mAppFilter = AppFilter.loadByName(sContext.getString(R.string.app_filter_class));
        mModel = new LauncherModel(this, mIconCache, mAppFilter, mDeepShortcutManager);

        LauncherAppsCompat.getInstance(sContext).addOnAppsChangedCallback(mModel);

        // Register intent receivers
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        // For handling managed profiles
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
        // For extracting colors from the wallpaper
        if (Utilities.isNycOrAbove()) {
            // TODO: add a broadcast entry to the manifest for pre-N.
            filter.addAction(Intent.ACTION_WALLPAPER_CHANGED);
        }

        sContext.registerReceiver(mModel, filter);
        UserManagerCompat.getInstance(sContext).enableAndResetCache();
        if (!Utilities.ATLEAST_KITKAT) {
            sContext.registerReceiver(new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    mWallpaperChangedSinceLastCheck = true;
                }
            }, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
        }
        new ConfigMonitor(sContext).register();

        ExtractionUtils.startColorExtractionServiceIfNecessary(sContext);
    }

    /**
     * Call from Application.onTerminate(), which is not guaranteed to ever be called.
     */
    public void onTerminate() {
        sContext.unregisterReceiver(mModel);
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(sContext);
        launcherApps.removeOnAppsChangedCallback(mModel);
        PackageInstallerCompat.getInstance(sContext).onStop();
    }

    /**
     * Reloads the workspace items from the DB and re-binds the workspace. This should generally
     * not be called as DB updates are automatically followed by UI update
     */
    public void reloadWorkspace() {
        mModel.resetLoadedState(false, true);
        mModel.startLoaderFromBackground();
    }

    LauncherModel setLauncher(Launcher launcher) {
        sLauncherProvider.get().setLauncherProviderChangeListener(launcher);
        mModel.initialize(launcher);
        return mModel;
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    public WidgetPreviewLoader getWidgetCache() {
        return mWidgetCache;
    }

    public DeepShortcutManager getShortcutManager() {
        return mDeepShortcutManager;
    }

    public boolean hasWallpaperChangedSinceLastCheck() {
        boolean result = mWallpaperChangedSinceLastCheck;
        mWallpaperChangedSinceLastCheck = false;
        return result;
    }

    public InvariantDeviceProfile getInvariantDeviceProfile() {
        return mInvariantDeviceProfile;
    }
}
