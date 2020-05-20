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
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.SecureSettingsObserver.newNotificationSettingsObserver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.PredictionModel;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.InstallSessionTracker;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SecureSettingsObserver;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.widget.custom.CustomWidgetManager;

public class LauncherAppState {

    public static final String ACTION_FORCE_ROLOAD = "force-reload-launcher";

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<LauncherAppState> INSTANCE =
            new MainThreadInitializedObject<>(LauncherAppState::new);

    private final Context mContext;
    private final LauncherModel mModel;
    private final IconCache mIconCache;
    private final WidgetPreviewLoader mWidgetCache;
    private final InvariantDeviceProfile mInvariantDeviceProfile;
    private final PredictionModel mPredictionModel;

    private SecureSettingsObserver mNotificationDotsObserver;
    private InstallSessionTracker mInstallSessionTracker;
    private SimpleBroadcastReceiver mModelChangeReceiver;
    private SafeCloseable mCalendarChangeTracker;
    private SafeCloseable mUserChangeListener;

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

        mModelChangeReceiver = new SimpleBroadcastReceiver(mModel::onBroadcastIntent);

        mContext.getSystemService(LauncherApps.class).registerCallback(mModel);
        mModelChangeReceiver.register(mContext, Intent.ACTION_LOCALE_CHANGED,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
        if (FeatureFlags.IS_STUDIO_BUILD) {
            mModelChangeReceiver.register(mContext, ACTION_FORCE_ROLOAD);
        }

        mCalendarChangeTracker = IconProvider.registerIconChangeListener(mContext,
                mModel::onAppIconChanged, MODEL_EXECUTOR.getHandler());

        // TODO: remove listener on terminate
        FeatureFlags.APP_SEARCH_IMPROVEMENTS.addChangeListener(context, mModel::forceReload);
        CustomWidgetManager.INSTANCE.get(mContext)
                .setWidgetRefreshCallback(mModel::refreshAndBindWidgetsAndShortcuts);

        mUserChangeListener = UserCache.INSTANCE.get(mContext)
                .addUserChangeListener(mModel::forceReload);

        mInvariantDeviceProfile.addOnChangeListener(this::onIdpChanged);
        new Handler().post( () -> mInvariantDeviceProfile.verifyConfigChangedInBackground(context));

        mInstallSessionTracker = InstallSessionHelper.INSTANCE.get(context)
                .registerInstallTracker(mModel, MODEL_EXECUTOR);

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

    public LauncherAppState(Context context, @Nullable String iconCacheFileName) {
        Log.v(Launcher.TAG, "LauncherAppState initiated");
        Preconditions.assertUIThread();
        mContext = context;

        mInvariantDeviceProfile = InvariantDeviceProfile.INSTANCE.get(context);
        mIconCache = new IconCache(mContext, mInvariantDeviceProfile, iconCacheFileName);
        mWidgetCache = new WidgetPreviewLoader(mContext, mIconCache);
        mModel = new LauncherModel(this, mIconCache, AppFilter.newInstance(mContext));
        mPredictionModel = PredictionModel.newInstance(mContext);
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
        if (mModelChangeReceiver != null) {
            mContext.unregisterReceiver(mModelChangeReceiver);
        }
        mContext.getSystemService(LauncherApps.class).unregisterCallback(mModel);
        if (mInstallSessionTracker != null) {
            mInstallSessionTracker.unregister();
        }
        if (mCalendarChangeTracker != null) {
            mCalendarChangeTracker.close();
        }
        if (mUserChangeListener != null) {
            mUserChangeListener.close();
        }
        CustomWidgetManager.INSTANCE.get(mContext).setWidgetRefreshCallback(null);

        if (mNotificationDotsObserver != null) {
            mNotificationDotsObserver.unregister();
        }
    }

    public IconCache getIconCache() {
        return mIconCache;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    public PredictionModel getPredictionModel() {
        return mPredictionModel;
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
}
