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

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;

import java.lang.ref.WeakReference;

public class LauncherAppState {
    private Context mContext;
    private LauncherModel mModel;
    private IconCache mIconCache;
    private WidgetPreviewLoader.CacheDb mWidgetPreviewCacheDb;
    private static boolean sIsScreenLarge;
    private static float sScreenDensity;
    private static int sLongPressTimeout = 300;
    private static final String sSharedPreferencesKey = "com.android.launcher3.prefs";
    WeakReference<LauncherProvider> mLauncherProvider;

    private static LauncherAppState INSTANCE;

    private static final LauncherAppState INSTANCE = new LauncherAppState();

    public static LauncherAppState getInstance() {
        return INSTANCE;
    }

    public static void init(Context context) {
        INSTANCE.initialize(context);
    }

    public Context getContext() {
        return mContext;
    }

    private LauncherAppState() { }

    private void initialize(Context context) {
        mContext = context;

        // set sIsScreenXLarge and sScreenDensity *before* creating icon cache
        sIsScreenLarge = context.getResources().getBoolean(R.bool.is_large_screen);
        sScreenDensity = context.getResources().getDisplayMetrics().density;

        mWidgetPreviewCacheDb = new WidgetPreviewLoader.CacheDb(context);
        mIconCache = new IconCache(context);
        mModel = new LauncherModel(context, mIconCache);

        // Register intent receivers
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        context.registerReceiver(mModel, filter);
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        context.registerReceiver(mModel, filter);
        filter = new IntentFilter();
        filter.addAction(SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED);
        context.registerReceiver(mModel, filter);
        filter = new IntentFilter();
        filter.addAction(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED);
        context.registerReceiver(mModel, filter);

        // Register for changes to the favorites
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(LauncherSettings.Favorites.CONTENT_URI, true,
                mFavoritesObserver);
    }

    /**
     * Call from Application.onTerminate(), which is not guaranteed to ever be called.
     */
    public void onTerminate() {
        mContext.unregisterReceiver(mModel);

        ContentResolver resolver = mContext.getContentResolver();
        resolver.unregisterContentObserver(mFavoritesObserver);
    }

    /**
     * Receives notifications whenever the user favorites have changed.
     */
    private final ContentObserver mFavoritesObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            // If the database has ever changed, then we really need to force a reload of the
            // workspace on the next load
            mModel.resetLoadedState(false, true);
            mModel.startLoaderFromBackground();
        }
    };

    LauncherModel setLauncher(Launcher launcher) {
        mModel.initialize(launcher);
        return mModel;
    }

    IconCache getIconCache() {
        return mIconCache;
    }

    LauncherModel getModel() {
        return mModel;
    }

    WidgetPreviewLoader.CacheDb getWidgetPreviewCacheDb() {
        return mWidgetPreviewCacheDb;
    }

   void setLauncherProvider(LauncherProvider provider) {
        mLauncherProvider = new WeakReference<LauncherProvider>(provider);
    }

    LauncherProvider getLauncherProvider() {
        return mLauncherProvider.get();
    }

    public static String getSharedPreferencesKey() {
        return sSharedPreferencesKey;
    }

    public static boolean isScreenLarge() {
        return sIsScreenLarge;
    }

    public static boolean isScreenLandscape(Context context) {
        return context.getResources().getConfiguration().orientation ==
            Configuration.ORIENTATION_LANDSCAPE;
    }

    public static float getScreenDensity() {
        return sScreenDensity;
    }

    public static int getLongPressTimeout() {
        return sLongPressTimeout;
    }
}
