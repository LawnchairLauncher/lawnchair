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

import android.annotation.TargetApi;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.util.Thunk;

import java.lang.ref.WeakReference;

public class LauncherAppState implements DeviceProfile.DeviceProfileCallbacks {

    private final AppFilter mAppFilter;
    private final BuildInfo mBuildInfo;
    @Thunk final LauncherModel mModel;
    private final IconCache mIconCache;
    private final WidgetPreviewLoader mWidgetCache;

    private final boolean mIsScreenLarge;
    private final float mScreenDensity;
    private final int mLongPressTimeout = 300;

    private boolean mWallpaperChangedSinceLastCheck;

    private static WeakReference<LauncherProvider> sLauncherProvider;
    private static Context sContext;

    private static LauncherAppState INSTANCE;

    private DynamicGrid mDynamicGrid;
    private LauncherAccessibilityDelegate mAccessibilityDelegate;

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

    public static void setApplicationContext(Context context) {
        if (sContext != null) {
            Log.w(Launcher.TAG, "setApplicationContext called twice! old=" + sContext + " new=" + context);
        }
        sContext = context.getApplicationContext();
    }

    private LauncherAppState() {
        if (sContext == null) {
            throw new IllegalStateException("LauncherAppState inited before app context set");
        }

        Log.v(Launcher.TAG, "LauncherAppState inited");

        if (sContext.getResources().getBoolean(R.bool.debug_memory_enabled)) {
            MemoryTracker.startTrackingMe(sContext, "L");
        }

        // set sIsScreenXLarge and mScreenDensity *before* creating icon cache
        mIsScreenLarge = isScreenLarge(sContext.getResources());
        mScreenDensity = sContext.getResources().getDisplayMetrics().density;
        mIconCache = new IconCache(sContext);
        mWidgetCache = new WidgetPreviewLoader(sContext, mIconCache);

        mAppFilter = AppFilter.loadByName(sContext.getString(R.string.app_filter_class));
        mBuildInfo = BuildInfo.loadByName(sContext.getString(R.string.build_info_class));
        mModel = new LauncherModel(this, mIconCache, mAppFilter);

        LauncherAppsCompat.getInstance(sContext).addOnAppsChangedCallback(mModel);

        // Register intent receivers
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
        filter.addAction(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED);
        // For handling managed profiles
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(LauncherAppsCompat.ACTION_MANAGED_PROFILE_REMOVED);

        sContext.registerReceiver(mModel, filter);
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
        mModel.initialize(launcher);
        mAccessibilityDelegate = ((launcher != null) && Utilities.isLmpOrAbove()) ?
            new LauncherAccessibilityDelegate(launcher) : null;
        return mModel;
    }

    public LauncherAccessibilityDelegate getAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    boolean shouldShowAppOrWidgetProvider(ComponentName componentName) {
        return mAppFilter == null || mAppFilter.shouldShowApp(componentName);
    }

    static void setLauncherProvider(LauncherProvider provider) {
        sLauncherProvider = new WeakReference<LauncherProvider>(provider);
    }

    static LauncherProvider getLauncherProvider() {
        return sLauncherProvider.get();
    }

    public static String getSharedPreferencesKey() {
        return LauncherFiles.SHARED_PREFERENCES_KEY;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    DeviceProfile initDynamicGrid(Context context) {
        mDynamicGrid = createDynamicGrid(context, mDynamicGrid);
        mDynamicGrid.getDeviceProfile().addCallback(this);
        return mDynamicGrid.getDeviceProfile();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    static DynamicGrid createDynamicGrid(Context context, DynamicGrid dynamicGrid) {
        // Determine the dynamic grid properties
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        Point realSize = new Point();
        display.getRealSize(realSize);
        DisplayMetrics dm = new DisplayMetrics();
        display.getMetrics(dm);

        if (dynamicGrid == null) {
            Point smallestSize = new Point();
            Point largestSize = new Point();
            display.getCurrentSizeRange(smallestSize, largestSize);

            dynamicGrid = new DynamicGrid(context,
                    context.getResources(),
                    Math.min(smallestSize.x, smallestSize.y),
                    Math.min(largestSize.x, largestSize.y),
                    realSize.x, realSize.y,
                    dm.widthPixels, dm.heightPixels);
        }

        // Update the icon size
        DeviceProfile grid = dynamicGrid.getDeviceProfile();
        grid.updateFromConfiguration(context, context.getResources(),
                realSize.x, realSize.y,
                dm.widthPixels, dm.heightPixels);
        return dynamicGrid;
    }

    public DynamicGrid getDynamicGrid() {
        return mDynamicGrid;
    }

    public WidgetPreviewLoader getWidgetCache() {
        return mWidgetCache;
    }

    public boolean isScreenLarge() {
        return mIsScreenLarge;
    }

    // Need a version that doesn't require an instance of LauncherAppState for the wallpaper picker
    public static boolean isScreenLarge(Resources res) {
        return res.getBoolean(R.bool.is_large_tablet);
    }

    public static boolean isScreenLandscape(Context context) {
        return context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
    }

    public float getScreenDensity() {
        return mScreenDensity;
    }

    public int getLongPressTimeout() {
        return mLongPressTimeout;
    }

    public void onWallpaperChanged() {
        mWallpaperChangedSinceLastCheck = true;
    }

    public boolean hasWallpaperChangedSinceLastCheck() {
        boolean result = mWallpaperChangedSinceLastCheck;
        mWallpaperChangedSinceLastCheck = false;
        return result;
    }

    @Override
    public void onAvailableSizeChanged(DeviceProfile grid) {
        Utilities.setIconSize(grid.iconSizePx);
    }

    public static boolean isDogfoodBuild() {
        return getInstance().mBuildInfo.isDogfoodBuild();
    }
}
