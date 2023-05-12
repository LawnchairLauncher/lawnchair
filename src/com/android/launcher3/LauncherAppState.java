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

import static android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED;

import static com.android.launcher3.LauncherPrefs.ICON_STATE;
import static com.android.launcher3.LauncherPrefs.THEMED_ICONS;
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
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.icons.LauncherIconProvider;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.InstallSessionTracker;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.LockedUserState;
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
    public static final String KEY_ICON_STATE = "pref_icon_shape_path";

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<LauncherAppState> INSTANCE =
            new MainThreadInitializedObject<>(LauncherAppState::new);

    private final Context mContext;
    private final LauncherModel mModel;
    private final LauncherIconProvider mIconProvider;
    private final IconCache mIconCache;
    private final InvariantDeviceProfile mInvariantDeviceProfile;
    private final RunnableList mOnTerminateCallback = new RunnableList();

    // WORKAROUND: b/269335387 remove this after widget background listener is enabled
    /* Array of RemoteViews cached by Launcher process */
    @GuardedBy("itself")
    @NonNull
    public final SparseArray<RemoteViews> mCachedRemoteViews = new SparseArray<>();

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

        mInvariantDeviceProfile.addOnChangeListener(modelPropertiesChanged -> {
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
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
                Intent.ACTION_PROFILE_INACCESSIBLE,
                ACTION_DEVICE_POLICY_RESOURCE_UPDATED);
        if (FeatureFlags.IS_STUDIO_BUILD) {
            modelChangeReceiver.register(mContext, ACTION_FORCE_ROLOAD);
        }
        mOnTerminateCallback.add(() -> mContext.unregisterReceiver(modelChangeReceiver));

        SafeCloseable userChangeListener = UserCache.INSTANCE.get(mContext)
                .addUserChangeListener(mModel::forceReload);
        mOnTerminateCallback.add(userChangeListener::close);

        LockedUserState.get(context).runOnUserUnlocked(() -> {
            CustomWidgetManager.INSTANCE.get(mContext)
                    .setWidgetRefreshCallback(mModel::refreshAndBindWidgetsAndShortcuts);

            IconObserver observer = new IconObserver();
            SafeCloseable iconChangeTracker = mIconProvider.registerIconChangeListener(
                    observer, MODEL_EXECUTOR.getHandler());
            mOnTerminateCallback.add(iconChangeTracker::close);
            MODEL_EXECUTOR.execute(observer::verifyIconChanged);
            LauncherPrefs.get(context).addListener(observer, THEMED_ICONS);
            mOnTerminateCallback.add(
                    () -> LauncherPrefs.get(mContext).removeListener(observer, THEMED_ICONS));

            InstallSessionTracker installSessionTracker =
                    InstallSessionHelper.INSTANCE.get(context).registerInstallTracker(mModel);
            mOnTerminateCallback.add(installSessionTracker::unregister);
        });

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
        mIconProvider = new LauncherIconProvider(context);
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
            LauncherPrefs.get(mContext).put(ICON_STATE, iconState);
        }

        void verifyIconChanged() {
            String iconState = mIconProvider.getSystemIconState();
            if (!iconState.equals(LauncherPrefs.get(mContext).get(ICON_STATE))) {
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
