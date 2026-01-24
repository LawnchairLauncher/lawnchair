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
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.config.FeatureFlags.SEPARATE_RECENTS_ACTIVITY;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.quickstep.fallback.window.RecentsWindowFlags.enableFallbackOverviewInWindow;
import static com.android.quickstep.fallback.window.RecentsWindowFlags.enableLauncherOverviewInWindow;
import static com.android.quickstep.fallback.window.RecentsWindowFlags.enableOverviewOnConnectedDisplays;
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
import androidx.annotation.UiThread;

import com.android.app.displaylib.PerDisplayRepository;
import com.android.launcher3.R;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.util.ActiveGestureProtoLogProxy;
import com.android.systemui.shared.system.PackageManagerWrapper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

/**
 * Class to keep track of the current overview component based off user preferences and app updates
 * and provide callers the relevant classes.
 */
@LauncherAppSingleton
public final class OverviewComponentObserver {
    private static final String TAG = "OverviewComponentObserver";

    public static final DaggerSingletonObject<OverviewComponentObserver> INSTANCE =
            new DaggerSingletonObject<>(LauncherAppComponent::getOverviewComponentObserver);

    // We register broadcast receivers on main thread to avoid missing updates.
    private final SimpleBroadcastReceiver mUserPreferenceChangeReceiver;
    private final SimpleBroadcastReceiver mOtherHomeAppUpdateReceiver;

    private final PerDisplayRepository<RecentsWindowManager> mRecentsWindowManagerRepository;

    private final Intent mCurrentPrimaryHomeIntent;
    private final Intent mSecondaryHomeIntent;
    private final Intent mMyPrimaryHomeIntent;
    private final Intent mFallbackIntent;
    private final SparseIntArray mConfigChangesMap = new SparseIntArray();
    private final String mSetupWizardPkg;

    private final List<OverviewChangeListener> mOverviewChangeListeners =
            new CopyOnWriteArrayList<>();

    private String mUpdateRegisteredPackage;
    private BaseContainerInterface mDefaultDisplayContainerInterface;
    private Intent mOverviewIntent;
    private boolean mIsHomeAndOverviewSame;
    private boolean mIsDefaultHome;
    private boolean mIsHomeDisabled;

    @Inject
    public OverviewComponentObserver(
            @ApplicationContext Context context,
            PerDisplayRepository<RecentsWindowManager> recentsWindowManagerRepository,
            DaggerSingletonTracker lifecycleTracker) {
        mUserPreferenceChangeReceiver =
                new SimpleBroadcastReceiver(context, MAIN_EXECUTOR, this::updateOverviewTargets);
        mOtherHomeAppUpdateReceiver =
                new SimpleBroadcastReceiver(context, MAIN_EXECUTOR, this::updateOverviewTargets);
        mRecentsWindowManagerRepository = recentsWindowManagerRepository;
        // Set up primary intents
        mCurrentPrimaryHomeIntent = createHomeIntent();
        mMyPrimaryHomeIntent = new Intent(mCurrentPrimaryHomeIntent).setPackage(
                context.getPackageName());
        ResolveInfo info = context.getPackageManager().resolveActivity(mMyPrimaryHomeIntent, 0);
        ComponentName myHomeComponent =
                new ComponentName(context.getPackageName(), info.activityInfo.name);
        mMyPrimaryHomeIntent.setComponent(myHomeComponent);
        // Set up secondary home intent
        mSecondaryHomeIntent = createSecondaryHomeIntent().setPackage(
                context.getPackageName());
        ResolveInfo secondaryInfo = context.getPackageManager().resolveActivity(
                mSecondaryHomeIntent, 0);
        if (secondaryInfo != null) {
            ComponentName secondaryComponent = new ComponentName(context,
                    secondaryInfo.activityInfo.name);
            mSecondaryHomeIntent.setComponent(secondaryComponent);
        }

        mConfigChangesMap.append(myHomeComponent.hashCode(), info.activityInfo.configChanges);
        mSetupWizardPkg = context.getString(R.string.setup_wizard_pkg);

        ComponentName fallbackComponent = new ComponentName(context, RecentsActivity.class);
        mFallbackIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setComponent(fallbackComponent)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            ActivityInfo fallbackInfo = context.getPackageManager().getActivityInfo(
                    mFallbackIntent.getComponent(), 0 /* flags */);
            mConfigChangesMap.append(fallbackComponent.hashCode(), fallbackInfo.configChanges);
        } catch (PackageManager.NameNotFoundException ignored) { /* Impossible */ }

        mUserPreferenceChangeReceiver.register(ACTION_PREFERRED_ACTIVITY_CHANGED);
        updateOverviewTargets();

        lifecycleTracker.addCloseable(this::onDestroy);
    }

    /** Adds a listener for changes in {@link #isHomeAndOverviewSame()} */
    public void addOverviewChangeListener(OverviewChangeListener overviewChangeListener) {
        mOverviewChangeListeners.add(overviewChangeListener);
    }

    /** Removes a previously added listener */
    public void removeOverviewChangeListener(OverviewChangeListener overviewChangeListener) {
        mOverviewChangeListeners.remove(overviewChangeListener);
    }

    /**
     * Called to set home enabled/disabled state via systemUI
     * @param isHomeDisabled
     */
    public void setHomeDisabled(boolean isHomeDisabled) {
        if (isHomeDisabled != mIsHomeDisabled) {
            mIsHomeDisabled = isHomeDisabled;
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
    @UiThread
    private void updateOverviewTargets() {
        ComponentName defaultHome = PackageManagerWrapper.getInstance()
                .getHomeActivities(new ArrayList<>());
        if (defaultHome != null && defaultHome.getPackageName().equals(mSetupWizardPkg)) {
            // Treat setup wizard as null default home, because there is a period between setup and
            // launcher being default home where it is briefly null. Otherwise, it would appear as
            // if overview targets are changing twice, giving the listener an incorrect signal.
            defaultHome = null;
        }

        mIsDefaultHome = Objects.equals(mMyPrimaryHomeIntent.getComponent(), defaultHome);

        // Set assistant visibility to 0 from launcher's perspective, ensures any elements that
        // launcher made invisible become visible again before the new activity control helper
        // becomes active.
        if (mDefaultDisplayContainerInterface != null) {
            mDefaultDisplayContainerInterface.onAssistantVisibilityChanged(0.f);
        }

        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            mIsDefaultHome = false;
            if (defaultHome == null) {
                defaultHome = mMyPrimaryHomeIntent.getComponent();
            }
        }

        // TODO(b/258022658): Remove temporary logging.
        Log.i(TAG, "updateOverviewTargets: mIsHomeDisabled=" + mIsHomeDisabled
                + ", isDefaultHomeNull=" + (defaultHome == null)
                + ", mIsDefaultHome=" + mIsDefaultHome);

        if (!mIsHomeDisabled && (defaultHome == null || mIsDefaultHome)) {
            // User default home is same as our home app. Use Overview integrated in Launcher.
            if (enableLauncherOverviewInWindow) {
                RecentsWindowManager recentsWindowManager = mRecentsWindowManagerRepository.get(
                        DEFAULT_DISPLAY);
                mDefaultDisplayContainerInterface =
                        recentsWindowManager != null ? recentsWindowManager.getContainerInterface()
                                : null;
            } else {
                mDefaultDisplayContainerInterface = LauncherActivityInterface.INSTANCE;
            }
            mIsHomeAndOverviewSame = true;
            mOverviewIntent = mMyPrimaryHomeIntent;
            mCurrentPrimaryHomeIntent.setComponent(mMyPrimaryHomeIntent.getComponent());

            // Remove any update listener as we don't care about other packages.
            unregisterOtherHomeAppUpdateReceiver();
        } else {
            // The default home app is a different launcher. Use the fallback Overview instead.
            if (enableFallbackOverviewInWindow) {
                RecentsWindowManager recentsWindowManager = mRecentsWindowManagerRepository.get(
                        DEFAULT_DISPLAY);
                mDefaultDisplayContainerInterface =
                        recentsWindowManager != null ? recentsWindowManager.getContainerInterface()
                                : null;
            } else {
                mDefaultDisplayContainerInterface = FallbackActivityInterface.INSTANCE;
            }
            mIsHomeAndOverviewSame = false;
            mOverviewIntent = mFallbackIntent;
            mCurrentPrimaryHomeIntent.setComponent(defaultHome);

            // User's default home app can change as a result of package updates of this app (such
            // as uninstalling the app or removing the "Launcher" feature in an update).
            // Listen for package updates of this app (and remove any previously attached
            // package listener).
            if (defaultHome == null) {
                unregisterOtherHomeAppUpdateReceiver();
            } else if (!defaultHome.getPackageName().equals(mUpdateRegisteredPackage)) {
                unregisterOtherHomeAppUpdateReceiver();

                mUpdateRegisteredPackage = defaultHome.getPackageName();
                mOtherHomeAppUpdateReceiver.registerPkgActions(
                        mUpdateRegisteredPackage, ACTION_PACKAGE_ADDED,
                        ACTION_PACKAGE_CHANGED, ACTION_PACKAGE_REMOVED);
            }
        }
        mOverviewChangeListeners.forEach(l -> l.onOverviewTargetChange(mIsHomeAndOverviewSame));
    }

    /**
     * Clean up any registered receivers.
     */
    private void onDestroy() {
        mUserPreferenceChangeReceiver.unregisterReceiverSafely();
        unregisterOtherHomeAppUpdateReceiver();
    }

    private void unregisterOtherHomeAppUpdateReceiver() {
        if (mUpdateRegisteredPackage != null) {
            mOtherHomeAppUpdateReceiver.unregisterReceiverSafely();
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
    public Intent getOverviewIntentIgnoreSysUiState() {
        return mIsDefaultHome ? mMyPrimaryHomeIntent : mOverviewIntent;
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
    public Intent getHomeIntent(int displayId) {
        if (displayId == DEFAULT_DISPLAY) {
            return mCurrentPrimaryHomeIntent;
        } else {
            return mSecondaryHomeIntent;
        }
    }

    /**
     * Returns true if home and overview are same process.
     */
    public boolean isHomeAndOverviewSame() {
        return mIsHomeAndOverviewSame;
    }

    /**
     * Get the current control helper for managing interactions to the overview container for
     * the given displayId.
     *
     * @param displayId The display id
     * @return the control helper for the given display
     */
    @Nullable
    public BaseContainerInterface<?, ?> getContainerInterface(int displayId) {
        if (enableOverviewOnConnectedDisplays() && displayId != DEFAULT_DISPLAY) {
            RecentsWindowManager recentsWindowManager = mRecentsWindowManagerRepository.get(
                    displayId);
            return recentsWindowManager != null ? recentsWindowManager.getContainerInterface()
                    : null;
        } else {
            return mDefaultDisplayContainerInterface;
        }
    }

    public void dump(PrintWriter pw) {
        pw.println("OverviewComponentObserver:");
        pw.println("  isDefaultHome=" + mIsDefaultHome);
        pw.println("  isHomeDisabled=" + mIsHomeDisabled);
        pw.println("  homeAndOverviewSame=" + mIsHomeAndOverviewSame);
        pw.println("  overviewIntent=" + mOverviewIntent);
        pw.println("  homeIntent=" + mCurrentPrimaryHomeIntent);
        pw.println("  secondaryHomeIntent=" + mSecondaryHomeIntent);
    }

    /**
     * Starts the intent for the current home activity.
     */
    public static void startHomeIntentSafely(@NonNull Context context, @Nullable Bundle options,
            @NonNull String reason, int displayId) {
        Intent intent = OverviewComponentObserver.INSTANCE.get(context).getHomeIntent(displayId);
        startHomeIntentSafely(context, intent, options, reason);
    }

    /**
     * Starts the intent for the current home activity.
     */
    public static void startHomeIntentSafely(
            @NonNull Context context,
            @NonNull Intent homeIntent,
            @Nullable Bundle options,
            @NonNull String reason) {
        ActiveGestureProtoLogProxy.logStartHomeIntent(reason);
        try {
            context.startActivity(homeIntent, options);
        } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
            context.startActivity(createHomeIntent(), options);
        }
    }

    /**
     * Interface for listening to overview changes
     */
    public interface OverviewChangeListener {

        /**
         * Called when the overview target changes
         */
        void onOverviewTargetChange(boolean isHomeAndOverviewSame);
    }

    private static Intent createHomeIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    private static Intent createSecondaryHomeIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_SECONDARY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
