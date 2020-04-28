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

import static com.android.launcher3.InvariantDeviceProfile.CHANGE_FLAG_ICON_PARAMS;
import static com.android.launcher3.util.SecureSettingsObserver.newNotificationSettingsObserver;

import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SecureSettingsObserver;
import com.android.launcher3.widget.custom.CustomWidgetManager;

public class LauncherAppState {

    public static final String ACTION_FORCE_ROLOAD = "force-reload-launcher";

    // We do not need any synchronization for this variable as its only written on UI thread.
    private static final MainThreadInitializedObject<LauncherAppState> INSTANCE =
            new MainThreadInitializedObject<>(LauncherAppState::new);

    private final Context mContext;
    private final LauncherModel mModel;
    private final IconCache mIconCache;
    private final WidgetPreviewLoader mWidgetCache;
    private final InvariantDeviceProfile mInvariantDeviceProfile;
    private final SecureSettingsObserver mNotificationDotsObserver;

    public static LauncherAppState getInstance(final Context context) {
        return INSTANCE.get(context);
    }

    public static LauncherAppState getInstanceNoCreate() {
        return INSTANCE.getNoCreate();
    }

    public Context getContext() {
        return mContext;
    }

    private LauncherAppState(Context context) {
        if (getLocalProvider(context) == null) {
            throw new RuntimeException(
                    "Initializing LauncherAppState in the absence of LauncherProvider");
        }
        Log.v(Launcher.TAG, "LauncherAppState initiated");
        Preconditions.assertUIThread();
        mContext = context;

        mInvariantDeviceProfile = InvariantDeviceProfile.INSTANCE.get(mContext);
        mIconCache = new IconCache(mContext, mInvariantDeviceProfile);
        mWidgetCache = new WidgetPreviewLoader(mContext, mIconCache);
        mModel = new LauncherModel(this, mIconCache, AppFilter.newInstance(mContext));

        LauncherAppsCompat.getInstance(mContext).addOnAppsChangedCallback(mModel);

        // Register intent receivers
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        // For handling managed profiles
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
        filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);

        if (FeatureFlags.IS_DOGFOOD_BUILD) {
            filter.addAction(ACTION_FORCE_ROLOAD);
        }
        FeatureFlags.APP_SEARCH_IMPROVEMENTS.addChangeListener(context, mModel::forceReload);

        mContext.registerReceiver(mModel, filter);
        UserManagerCompat.getInstance(mContext).enableAndResetCache();
        mInvariantDeviceProfile.addOnChangeListener(this::onIdpChanged);
        new Handler().post( () -> mInvariantDeviceProfile.verifyConfigChangedInBackground(context));

        if (!mContext.getResources().getBoolean(R.bool.notification_dots_enabled)) {
            mNotificationDotsObserver = null;
        } else {
            // Register an observer to rebind the notification listener when dots are re-enabled.
            mNotificationDotsObserver =
                    newNotificationSettingsObserver(mContext, this::onNotificationSettingsChanged);
            mNotificationDotsObserver.register();
            mNotificationDotsObserver.dispatchOnChange();
        }
    }

    protected void onNotificationSettingsChanged(boolean areNotificationDotsEnabled) {
        if (areNotificationDotsEnabled) {
            NotificationListener.requestRebind(new ComponentName(
                    mContext, NotificationListener.class));
        }
    }

    private void onIdpChanged(int changeFlags, InvariantDeviceProfile idp) {
        if (changeFlags == 0) {
            return;
        }

        if ((changeFlags & CHANGE_FLAG_ICON_PARAMS) != 0) {
            LauncherIcons.clearPool();
            mIconCache.updateIconParams(idp.fillResIconDpi, idp.iconBitmapSize);
            mWidgetCache.refresh();
        }

        mModel.forceReload();
    }

    /**
     * Call from Application.onTerminate(), which is not guaranteed to ever be called.
     */
    public void onTerminate() {
        mContext.unregisterReceiver(mModel);
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(mContext);
        launcherApps.removeOnAppsChangedCallback(mModel);
        PackageInstallerCompat.getInstance(mContext).onStop();
        if (mNotificationDotsObserver != null) {
            mNotificationDotsObserver.unregister();
        }
    }

    LauncherModel setLauncher(Launcher launcher) {
        getLocalProvider(mContext).setLauncherProviderChangeListener(launcher);
        mModel.initialize(launcher);
        CustomWidgetManager.INSTANCE.get(launcher)
                .setWidgetRefreshCallback(mModel::refreshAndBindWidgetsAndShortcuts);
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

    public InvariantDeviceProfile getInvariantDeviceProfile() {
        return mInvariantDeviceProfile;
    }

    /**
     * Shorthand for {@link #getInvariantDeviceProfile()}
     */
    public static InvariantDeviceProfile getIDP(Context context) {
        return InvariantDeviceProfile.INSTANCE.get(context);
    }

    private static LauncherProvider getLocalProvider(Context context) {
        try (ContentProviderClient cl = context.getContentResolver()
                .acquireContentProviderClient(LauncherProvider.AUTHORITY)) {
            return (LauncherProvider) cl.getLocalContentProvider();
        }
    }
}
