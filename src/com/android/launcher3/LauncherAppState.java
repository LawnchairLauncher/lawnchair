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

import static com.android.launcher3.Utilities.getDevicePrefs;
import static com.android.launcher3.config.FeatureFlags.ENABLE_THEMED_ICONS;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.InstallSessionTracker;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.util.Themes;
import com.android.launcher3.widget.custom.CustomWidgetManager;

public class LauncherAppState implements SafeCloseable {

    public static final String ACTION_FORCE_ROLOAD = "force-reload-launcher";
    private static final String KEY_ICON_STATE = "pref_icon_shape_path";

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<LauncherAppState> INSTANCE =
            new MainThreadInitializedObject<>(LauncherAppState::new);

    private final Context mContext;
    private final LauncherModel mModel;
    private final IconProvider mIconProvider;
    private final IconCache mIconCache;
    private final InvariantDeviceProfile mInvariantDeviceProfile;
    private final RunnableList mOnTerminateCallback = new RunnableList();

    public static LauncherAppState getInstance(final Context context) {
        return INSTANCE.get(context);
    }

    public static LauncherAppState getInstanceNoCreate() {
        return INSTANCE.getNoCreate();
    }

    public Context getContext() {
        return mContext;
    }

    public LauncherAppState(Context context) {
        this(context, LauncherFiles.APP_ICONS_DB);
        Log.v(Launcher.TAG, "LauncherAppState initiated");
        Preconditions.assertUIThread();

        mInvariantDeviceProfile.addOnChangeListener((modelPropertiesChanged, taskbarChanged) -> {
            if (modelPropertiesChanged) {
                refreshAndReloadLauncher();
            }
        });

        mContext.getSystemService(LauncherApps.class).registerCallback(mModel);

        SimpleBroadcastReceiver modelChangeReceiver =
                new SimpleBroadcastReceiver(mModel::onBroadcastIntent);
        modelChangeReceiver.register(mContext, Intent.ACTION_LOCALE_CHANGED,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
        if (FeatureFlags.IS_STUDIO_BUILD) {
            modelChangeReceiver.register(mContext, ACTION_FORCE_ROLOAD);
        }
        mOnTerminateCallback.add(() -> mContext.unregisterReceiver(modelChangeReceiver));

        CustomWidgetManager.INSTANCE.get(mContext)
                .setWidgetRefreshCallback(mModel::refreshAndBindWidgetsAndShortcuts);

        SafeCloseable userChangeListener = UserCache.INSTANCE.get(mContext)
                .addUserChangeListener(mModel::forceReload);
        mOnTerminateCallback.add(userChangeListener::close);

        IconObserver observer = new IconObserver();
        SafeCloseable iconChangeTracker = mIconProvider.registerIconChangeListener(
                observer, MODEL_EXECUTOR.getHandler());
        mOnTerminateCallback.add(iconChangeTracker::close);
        MODEL_EXECUTOR.execute(observer::verifyIconChanged);
        if (ENABLE_THEMED_ICONS.get()) {
            SharedPreferences prefs = Utilities.getPrefs(mContext);
            prefs.registerOnSharedPreferenceChangeListener(observer);
            mOnTerminateCallback.add(
                    () -> prefs.unregisterOnSharedPreferenceChangeListener(observer));
        }

        InstallSessionTracker installSessionTracker =
                InstallSessionHelper.INSTANCE.get(context).registerInstallTracker(mModel);
        mOnTerminateCallback.add(installSessionTracker::unregister);

        // Register an observer to rebind the notification listener when dots are re-enabled.
        SettingsCache settingsCache = SettingsCache.INSTANCE.get(mContext);
        SettingsCache.OnChangeListener notificationLister = this::onNotificationSettingsChanged;
        settingsCache.register(NOTIFICATION_BADGING_URI, notificationLister);
        onNotificationSettingsChanged(settingsCache.getValue(NOTIFICATION_BADGING_URI));
        mOnTerminateCallback.add(() ->
                settingsCache.unregister(NOTIFICATION_BADGING_URI, notificationLister));
    }

    public LauncherAppState(Context context, @Nullable String iconCacheFileName) {
        mContext = context;

        mInvariantDeviceProfile = InvariantDeviceProfile.INSTANCE.get(context);
        mIconProvider = new IconProvider(context, Themes.isThemedIconEnabled(context));
        mIconCache = new IconCache(mContext, mInvariantDeviceProfile,
                iconCacheFileName, mIconProvider);
        mModel = new LauncherModel(context, this, mIconCache, new AppFilter(mContext),
                iconCacheFileName != null);
        mOnTerminateCallback.add(mIconCache::close);
    }

    private void onNotificationSettingsChanged(boolean areNotificationDotsEnabled) {
        if (areNotificationDotsEnabled) {
            NotificationListener.requestRebind(new ComponentName(
                    mContext, NotificationListener.class));
        }
    }

    private void refreshAndReloadLauncher() {
        LauncherIcons.clearPool();
        mIconCache.updateIconParams(
                mInvariantDeviceProfile.fillResIconDpi, mInvariantDeviceProfile.iconBitmapSize);
        mModel.forceReload();
    }

    /**
     * Call from Application.onTerminate(), which is not guaranteed to ever be called.
     */
    @Override
    public void close() {
        mModel.destroy();
        mContext.getSystemService(LauncherApps.class).unregisterCallback(mModel);
        CustomWidgetManager.INSTANCE.get(mContext).setWidgetRefreshCallback(null);
        mOnTerminateCallback.executeAllAndDestroy();
    }

    public IconProvider getIconProvider() {
        return mIconProvider;
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    public LauncherModel getModel() {
        return mModel;
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

    private class IconObserver
            implements IconProvider.IconChangeListener, OnSharedPreferenceChangeListener {

        @Override
        public void onAppIconChanged(String packageName, UserHandle user) {
            mModel.onAppIconChanged(packageName, user);
        }

        @Override
        public void onSystemIconStateChanged(String iconState) {
            IconShape.init(mContext);
            refreshAndReloadLauncher();
            getDevicePrefs(mContext).edit().putString(KEY_ICON_STATE, iconState).apply();
        }

        void verifyIconChanged() {
            String iconState = mIconProvider.getSystemIconState();
            if (!iconState.equals(getDevicePrefs(mContext).getString(KEY_ICON_STATE, ""))) {
                onSystemIconStateChanged(iconState);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (Themes.KEY_THEMED_ICONS.equals(key)) {
                mIconProvider.setIconThemeSupported(Themes.isThemedIconEnabled(mContext));
                verifyIconChanged();
            }
        }
    }
}
