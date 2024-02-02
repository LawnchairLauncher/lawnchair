/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.allapps;

import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_ICON;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_NOTHING;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_PRIVATE_SPACE_INSTALL_APP;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SettingsCache.PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.uioverrides.ApiWrapper;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SettingsCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Companion class for {@link ActivityAllAppsContainerView} to manage private space section related
 * logic in the Personal tab.
 */
public class PrivateProfileManager extends UserProfileManager {

    // TODO (b/324573634): Fix the intent string.
    public static final Intent PRIVATE_SPACE_INTENT = new
            Intent("com.android.settings.action.PRIVATE_SPACE_SETUP_FLOW");

    private final ActivityAllAppsContainerView<?> mAllApps;
    private final Predicate<UserHandle> mPrivateProfileMatcher;
    private Set<String> mPreInstalledSystemPackages = new HashSet<>();
    private Intent mAppInstallerIntent = new Intent();
    private PrivateAppsSectionDecorator mPrivateAppsSectionDecorator;
    private boolean mPrivateSpaceSettingsAvailable;
    private Runnable mUnlockRunnable;

    public PrivateProfileManager(UserManager userManager,
            ActivityAllAppsContainerView<?> allApps,
            StatsLogManager statsLogManager,
            UserCache userCache) {
        super(userManager, statsLogManager, userCache);
        mAllApps = allApps;
        mPrivateProfileMatcher = (user) -> userCache.getUserInfo(user).isPrivate();
        UI_HELPER_EXECUTOR.post(this::initializeInBackgroundThread);
    }

    /** Adds Private Space Header to the layout. */
    public int addPrivateSpaceHeader(ArrayList<BaseAllAppsAdapter.AdapterItem> adapterItems) {
        adapterItems.add(new BaseAllAppsAdapter.AdapterItem(VIEW_TYPE_PRIVATE_SPACE_HEADER));
        mAllApps.mAH.get(MAIN).mAdapter.notifyItemInserted(adapterItems.size() - 1);
        return adapterItems.size();
    }

    /** Adds Private Space System Apps Divider to the layout. */
    public int addSystemAppsDivider(List<BaseAllAppsAdapter.AdapterItem> adapterItems) {
        adapterItems.add(new BaseAllAppsAdapter
                .AdapterItem(VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER));
        mAllApps.mAH.get(MAIN).mAdapter.notifyItemInserted(adapterItems.size() - 1);
        return adapterItems.size();
    }

    /** Adds Private Space install app button to the layout. */
    public void addPrivateSpaceInstallAppButton(List<BaseAllAppsAdapter.AdapterItem> adapterItems) {
        Context context = mAllApps.getContext();
        // Prepare bitmapInfo
        Intent.ShortcutIconResource shortcut = Intent.ShortcutIconResource.fromContext(
                context, com.android.launcher3.R.drawable.private_space_install_app_icon);
        BitmapInfo bitmapInfo = LauncherIcons.obtain(context).createIconBitmap(shortcut);

        AppInfo itemInfo = new AppInfo();
        itemInfo.title = context.getResources().getString(R.string.ps_add_button_label);
        itemInfo.intent = mAppInstallerIntent;
        itemInfo.bitmap = bitmapInfo;
        itemInfo.contentDescription = context.getResources().getString(
                com.android.launcher3.R.string.ps_add_button_content_description);
        itemInfo.runtimeStatusFlags |= FLAG_PRIVATE_SPACE_INSTALL_APP | FLAG_NOT_PINNABLE;

        BaseAllAppsAdapter.AdapterItem item = new BaseAllAppsAdapter.AdapterItem(VIEW_TYPE_ICON);
        item.itemInfo = itemInfo;
        item.decorationInfo = new SectionDecorationInfo(context, ROUND_NOTHING,
                /* decorateTogether */ true);

        adapterItems.add(item);
        mAllApps.mAH.get(MAIN).mAdapter.notifyItemInserted(adapterItems.size() - 1);
    }

    /**
     * Disables quiet mode for Private Space User Profile.
     * The runnable passed will be executed in the {@link #reset()} method,
     * when Launcher receives update about profile availability.
     * The runnable passed is only executed once, and reset after execution.
     * In case the method is called again, before the previously set runnable was executed,
     * the runnable will be updated.
     */
    public void unlockPrivateProfile(Runnable runnable) {
        enableQuietMode(false);
        mUnlockRunnable = runnable;
    }

    /** Enables quiet mode for Private Space User Profile. */
    public void lockPrivateProfile() {
        enableQuietMode(true);
    }

    /** Whether private profile should be hidden on Launcher. */
    public boolean isPrivateSpaceHidden() {
        return getCurrentState() == STATE_DISABLED && SettingsCache.INSTANCE
                    .get(mAllApps.mActivityContext).getValue(PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI, 0);
    }

    /** Resets the current state of Private Profile, w.r.t. to Launcher. */
    public void reset() {
        int previousState = getCurrentState();
        boolean isEnabled = !mAllApps.getAppsStore()
                .hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED);
        int updatedState = isEnabled ? STATE_ENABLED : STATE_DISABLED;
        setCurrentState(updatedState);
        resetPrivateSpaceDecorator(updatedState);
        if (transitioningFromLockedToUnlocked(previousState, updatedState)) {
            applyUnlockRunnable();
        }
    }

    /** Opens the Private Space Settings Page. */
    public void openPrivateSpaceSettings() {
        if (mPrivateSpaceSettingsAvailable) {
            mAllApps.getContext().startActivity(PRIVATE_SPACE_INTENT);
        }
    }

    /** Returns whether or not Private Space Settings Page is available. */
    public boolean isPrivateSpaceSettingsAvailable() {
        return mPrivateSpaceSettingsAvailable;
    }

    /** Sets whether Private Space Settings Page is available. */
    public boolean setPrivateSpaceSettingsAvailable(boolean value) {
        return mPrivateSpaceSettingsAvailable = value;
    }

    /** Initializes binder call based properties in non-main thread.
     * <p>
     * This can cause the Private Space container items to not load/respond correctly sometimes,
     * when the All Apps Container loads for the first time (device restarts, new profiles
     * added/removed, etc.), as the properties are being set in non-ui thread whereas the container
     * loads in the ui thread.
     * This case should still be ok, as locking the Private Space container and unlocking it,
     * reloads the values, fixing the incorrect UI.
     */
    private void initializeInBackgroundThread() {
        Preconditions.assertNonUiThread();
        setPreInstalledSystemPackages();
        setAppInstallerIntent();
        initializePrivateSpaceSettingsState();
    }

    private void initializePrivateSpaceSettingsState() {
        Preconditions.assertNonUiThread();
        ResolveInfo resolveInfo = mAllApps.getContext().getPackageManager()
                .resolveActivity(PRIVATE_SPACE_INTENT, PackageManager.MATCH_SYSTEM_ONLY);
        setPrivateSpaceSettingsAvailable(resolveInfo != null);
    }

    private void setPreInstalledSystemPackages() {
        Preconditions.assertNonUiThread();
        if (getProfileUser() != null) {
            mPreInstalledSystemPackages = new HashSet<>(ApiWrapper
                    .getPreInstalledSystemPackages(mAllApps.getContext(), getProfileUser()));
        }
    }

    private void setAppInstallerIntent() {
        Preconditions.assertNonUiThread();
        if (getProfileUser() != null) {
            mAppInstallerIntent = ApiWrapper.getAppMarketActivityIntent(mAllApps.getContext(),
                    BuildConfig.APPLICATION_ID, getProfileUser());
        }
    }

    @VisibleForTesting
    void resetPrivateSpaceDecorator(int updatedState) {
        ActivityAllAppsContainerView<?>.AdapterHolder mainAdapterHolder = mAllApps.mAH.get(MAIN);
        if (updatedState == STATE_ENABLED) {
            // Create a new decorator instance if not already available.
            if (mPrivateAppsSectionDecorator == null) {
                mPrivateAppsSectionDecorator = new PrivateAppsSectionDecorator(
                        mainAdapterHolder.mAppsList);
            }
            for (int i = 0; i < mainAdapterHolder.mRecyclerView.getItemDecorationCount(); i++) {
                if (mainAdapterHolder.mRecyclerView.getItemDecorationAt(i)
                        .equals(mPrivateAppsSectionDecorator)) {
                    // No need to add another decorator if one is already present in recycler view.
                    return;
                }
            }
            // Add Private Space Decorator to the Recycler view.
            mainAdapterHolder.mRecyclerView.addItemDecoration(mPrivateAppsSectionDecorator);
        } else {
            // Remove Private Space Decorator from the Recycler view.
            if (mPrivateAppsSectionDecorator != null) {
                mainAdapterHolder.mRecyclerView.removeItemDecoration(mPrivateAppsSectionDecorator);
            }
        }
    }

    /** Posts quiet mode enable/disable call for private profile. */
    private void enableQuietMode(boolean enable) {
        setQuietMode(enable);
    }

    void applyUnlockRunnable() {
        if (mUnlockRunnable != null) {
            // reset the runnable to prevent re-execution.
            MAIN_EXECUTOR.post(mUnlockRunnable);
            mUnlockRunnable = null;
        }
    }

    private boolean transitioningFromLockedToUnlocked(int previousState, int updatedState) {
        return previousState == STATE_DISABLED && updatedState == STATE_ENABLED;
    }

    @Override
    public Predicate<UserHandle> getUserMatcher() {
        return mPrivateProfileMatcher;
    }

    /**
     * Splits private apps into user installed and system apps.
     * When the list of system apps is empty, all apps are treated as system.
     */
    public Predicate<AppInfo> splitIntoUserInstalledAndSystemApps() {
        return appInfo -> !mPreInstalledSystemPackages.isEmpty()
                && (appInfo.componentName == null
                || !(mPreInstalledSystemPackages.contains(appInfo.componentName.getPackageName())));
    }
}
