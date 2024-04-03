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

package com.android.quickstep;

import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_CHANGED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;

import static com.android.launcher3.config.FeatureFlags.SEPARATE_RECENTS_ACTIVITY;
import static com.android.systemui.shared.system.PackageManagerWrapper.ACTION_PREFERRED_ACTIVITY_CHANGED;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.systemui.shared.system.PackageManagerWrapper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Class to keep track of the current overview component based off user preferences and app updates
 * and provide callers the relevant classes.
 */
public final class OverviewComponentObserver {
    private static final String TAG = "OverviewComponentObserver";

    private final SimpleBroadcastReceiver mUserPreferenceChangeReceiver =
            new SimpleBroadcastReceiver(this::updateOverviewTargets);
    private final SimpleBroadcastReceiver mOtherHomeAppUpdateReceiver =
            new SimpleBroadcastReceiver(this::updateOverviewTargets);

    private final Context mContext;
    private final RecentsAnimationDeviceState mDeviceState;
    private final Intent mCurrentHomeIntent;
    private final Intent mMyHomeIntent;
    private final Intent mFallbackIntent;
    private final SparseIntArray mConfigChangesMap = new SparseIntArray();
    private final String mSetupWizardPkg;

    private Consumer<Boolean> mOverviewChangeListener = b -> { };

    private String mUpdateRegisteredPackage;
    private BaseActivityInterface mActivityInterface;
    private Intent mOverviewIntent;
    private boolean mIsHomeAndOverviewSame;
    private boolean mIsDefaultHome;
    private boolean mIsHomeDisabled;


    public OverviewComponentObserver(Context context, RecentsAnimationDeviceState deviceState) {
        mContext = context;
        mDeviceState = deviceState;
        mCurrentHomeIntent = createHomeIntent();
        mMyHomeIntent = new Intent(mCurrentHomeIntent).setPackage(mContext.getPackageName());
        ResolveInfo info = context.getPackageManager().resolveActivity(mMyHomeIntent, 0);
        ComponentName myHomeComponent =
                new ComponentName(context.getPackageName(), info.activityInfo.name);
        mMyHomeIntent.setComponent(myHomeComponent);
        mConfigChangesMap.append(myHomeComponent.hashCode(), info.activityInfo.configChanges);
        mSetupWizardPkg = context.getString(R.string.setup_wizard_pkg);

        ComponentName fallbackComponent = new ComponentName(mContext, RecentsActivity.class);
        mFallbackIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setComponent(fallbackComponent)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            ActivityInfo fallbackInfo = context.getPackageManager().getActivityInfo(
                    mFallbackIntent.getComponent(), 0 /* flags */);
            mConfigChangesMap.append(fallbackComponent.hashCode(), fallbackInfo.configChanges);
        } catch (PackageManager.NameNotFoundException ignored) { /* Impossible */ }

        mUserPreferenceChangeReceiver.register(mContext, ACTION_PREFERRED_ACTIVITY_CHANGED);
        updateOverviewTargets();
    }

    /**
     * Sets a listener for changes in {@link #isHomeAndOverviewSame()}
     */
    public void setOverviewChangeListener(Consumer<Boolean> overviewChangeListener) {
        mOverviewChangeListener = overviewChangeListener;
    }

    public void onSystemUiStateChanged() {
        if (mDeviceState.isHomeDisabled() != mIsHomeDisabled) {
            updateOverviewTargets();
        }
    }

    private void updateOverviewTargets(Intent unused) {
        updateOverviewTargets();
    }

    /**
     * Update overview intent and {@link BaseActivityInterface} based off the current launcher home
     * component.
     */
    private void updateOverviewTargets() {
        ComponentName defaultHome = PackageManagerWrapper.getInstance()
                .getHomeActivities(new ArrayList<>());
        if (defaultHome != null && defaultHome.getPackageName().equals(mSetupWizardPkg)) {
            // Treat setup wizard as null default home, because there is a period between setup and
            // launcher being default home where it is briefly null. Otherwise, it would appear as
            // if overview targets are changing twice, giving the listener an incorrect signal.
            defaultHome = null;
        }

        mIsHomeDisabled = mDeviceState.isHomeDisabled();
        mIsDefaultHome = Objects.equals(mMyHomeIntent.getComponent(), defaultHome);

        // Set assistant visibility to 0 from launcher's perspective, ensures any elements that
        // launcher made invisible become visible again before the new activity control helper
        // becomes active.
        if (mActivityInterface != null) {
            mActivityInterface.onAssistantVisibilityChanged(0.f);
        }

        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            mIsDefaultHome = false;
            if (defaultHome == null) {
                defaultHome = mMyHomeIntent.getComponent();
            }
        }

        // TODO(b/258022658): Remove temporary logging.
        Log.i(TAG, "updateOverviewTargets: mIsHomeDisabled=" + mIsHomeDisabled
                + ", isDefaultHomeNull=" + (defaultHome == null)
                + ", mIsDefaultHome=" + mIsDefaultHome);

        if (!mIsHomeDisabled && (defaultHome == null || mIsDefaultHome)) {
            // User default home is same as out home app. Use Overview integrated in Launcher.
            mActivityInterface = LauncherActivityInterface.INSTANCE;
            mIsHomeAndOverviewSame = true;
            mOverviewIntent = mMyHomeIntent;
            mCurrentHomeIntent.setComponent(mMyHomeIntent.getComponent());

            // Remove any update listener as we don't care about other packages.
            unregisterOtherHomeAppUpdateReceiver();
        } else {
            // The default home app is a different launcher. Use the fallback Overview instead.

            mActivityInterface = FallbackActivityInterface.INSTANCE;
            mIsHomeAndOverviewSame = false;
            mOverviewIntent = mFallbackIntent;
            mCurrentHomeIntent.setComponent(defaultHome);

            // User's default home app can change as a result of package updates of this app (such
            // as uninstalling the app or removing the "Launcher" feature in an update).
            // Listen for package updates of this app (and remove any previously attached
            // package listener).
            if (defaultHome == null) {
                unregisterOtherHomeAppUpdateReceiver();
            } else if (!defaultHome.getPackageName().equals(mUpdateRegisteredPackage)) {
                unregisterOtherHomeAppUpdateReceiver();

                mUpdateRegisteredPackage = defaultHome.getPackageName();
                mOtherHomeAppUpdateReceiver.registerPkgActions(mContext, mUpdateRegisteredPackage,
                        ACTION_PACKAGE_ADDED, ACTION_PACKAGE_CHANGED, ACTION_PACKAGE_REMOVED);
            }
        }
        mOverviewChangeListener.accept(mIsHomeAndOverviewSame);
    }

    /**
     * Clean up any registered receivers.
     */
    public void onDestroy() {
        mContext.unregisterReceiver(mUserPreferenceChangeReceiver);
        unregisterOtherHomeAppUpdateReceiver();
    }

    private void unregisterOtherHomeAppUpdateReceiver() {
        if (mUpdateRegisteredPackage != null) {
            mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
            mUpdateRegisteredPackage = null;
        }
    }

    /**
     * @return {@code true} if the overview component is able to handle the configuration changes.
     */
    boolean canHandleConfigChanges(ComponentName component, int changes) {
        final int orientationChange =
                ActivityInfo.CONFIG_ORIENTATION | ActivityInfo.CONFIG_SCREEN_SIZE;
        if ((changes & orientationChange) == orientationChange) {
            // This is just an approximate guess for simple orientation change because the changes
            // may contain non-public bits (e.g. window configuration).
            return true;
        }

        int configMask = mConfigChangesMap.get(component.hashCode());
        return configMask != 0 && (~configMask & changes) == 0;
    }

    /**
     * Get the intent for overview activity. It is used when lockscreen is shown and home was died
     * in background, we still want to restart the one that will be used after unlock.
     *
     * @return the overview intent
     */
    Intent getOverviewIntentIgnoreSysUiState() {
        return mIsDefaultHome ? mMyHomeIntent : mOverviewIntent;
    }

    /**
     * Get the current intent for going to the overview activity.
     *
     * @return the overview intent
     */
    public Intent getOverviewIntent() {
        return mOverviewIntent;
    }

    /**
     * Get the current intent for going to the home activity.
     */
    public Intent getHomeIntent() {
        return mCurrentHomeIntent;
    }

    /**
     * Returns true if home and overview are same activity.
     */
    public boolean isHomeAndOverviewSame() {
        return mIsHomeAndOverviewSame;
    }

    /**
     * Get the current activity control helper for managing interactions to the overview activity.
     *
     * @return the current activity control helper
     */
    public BaseActivityInterface getActivityInterface() {
        return mActivityInterface;
    }

    public void dump(PrintWriter pw) {
        pw.println("OverviewComponentObserver:");
        pw.println("  isDefaultHome=" + mIsDefaultHome);
        pw.println("  isHomeDisabled=" + mIsHomeDisabled);
        pw.println("  homeAndOverviewSame=" + mIsHomeAndOverviewSame);
        pw.println("  overviewIntent=" + mOverviewIntent);
        pw.println("  homeIntent=" + mCurrentHomeIntent);
    }

    /**
     * Starts the intent for the current home activity.
     */
    public static void startHomeIntentSafely(@NonNull Context context, @Nullable Bundle options,
            @NonNull String reason) {
        RecentsAnimationDeviceState deviceState = new RecentsAnimationDeviceState(context);
        OverviewComponentObserver observer = new OverviewComponentObserver(context, deviceState);
        Intent intent = observer.getHomeIntent();
        observer.onDestroy();
        deviceState.destroy();
        startHomeIntentSafely(context, intent, options, reason);
    }

    /**
     * Starts the intent for the current home activity.
     */
    public static void startHomeIntentSafely(
            @NonNull Context context, @NonNull Intent homeIntent, @Nullable Bundle options,
            @NonNull String reason) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(
                "OverviewComponentObserver.startHomeIntent: ").append(reason));
        try {
            context.startActivity(homeIntent, options);
        } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
            context.startActivity(createHomeIntent(), options);
        }
    }

    private static Intent createHomeIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
