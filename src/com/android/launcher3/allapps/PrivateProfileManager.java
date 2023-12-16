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
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SettingsCache.PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Flags;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SettingsCache;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * Companion class for {@link ActivityAllAppsContainerView} to manage private space section related
 * logic in the Personal tab.
 */
public class PrivateProfileManager extends UserProfileManager {

    private static final String SAFETY_CENTER_INTENT = Intent.ACTION_SAFETY_CENTER;
    private static final String PS_SETTINGS_FRAGMENT_KEY = ":settings:fragment_args_key";
    private static final String PS_SETTINGS_FRAGMENT_VALUE = "AndroidPrivateSpace_personal";
    private static final int ANIMATION_DURATION = 2000;
    private final ActivityAllAppsContainerView<?> mAllApps;
    private final Predicate<UserHandle> mPrivateProfileMatcher;
    private PrivateAppsSectionDecorator mPrivateAppsSectionDecorator;
    private boolean mPrivateSpaceSettingsAvailable;

    public PrivateProfileManager(UserManager userManager,
            ActivityAllAppsContainerView<?> allApps,
            StatsLogManager statsLogManager,
            UserCache userCache) {
        super(userManager, statsLogManager, userCache);
        mAllApps = allApps;
        mPrivateProfileMatcher = (user) -> userCache.getUserInfo(user).isPrivate();
        UI_HELPER_EXECUTOR.post(this::setPrivateSpaceSettingsAvailable);
    }

    /** Adds Private Space Header to the layout. */
    public int addPrivateSpaceHeader(ArrayList<BaseAllAppsAdapter.AdapterItem> adapterItems) {
        adapterItems.add(new BaseAllAppsAdapter.AdapterItem(VIEW_TYPE_PRIVATE_SPACE_HEADER));
        mAllApps.mAH.get(MAIN).mAdapter.notifyItemInserted(adapterItems.size() - 1);
        return adapterItems.size();
    }

    /** Disables quiet mode for Private Space User Profile. */
    public void unlockPrivateProfile() {
        enableQuietMode(false);
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
        boolean isEnabled = !mAllApps.getAppsStore()
                .hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED);
        int updatedState = isEnabled ? STATE_ENABLED : STATE_DISABLED;
        setCurrentState(updatedState);
        resetPrivateSpaceDecorator(updatedState);
    }

    /** Opens the Private Space Settings Entry Point. */
    public void openPrivateSpaceSettings() {
        Intent psSettingsIntent = new Intent(SAFETY_CENTER_INTENT);
        psSettingsIntent.putExtra(PS_SETTINGS_FRAGMENT_KEY, PS_SETTINGS_FRAGMENT_VALUE);
        mAllApps.getContext().startActivity(psSettingsIntent);
    }

    /** Whether Private Space Settings Entry Point is available on the device. */
    public boolean isPrivateSpaceSettingsAvailable() {
        return mPrivateSpaceSettingsAvailable;
    }

    private void setPrivateSpaceSettingsAvailable() {
        if (mPrivateSpaceSettingsAvailable) {
            return;
        }
        Preconditions.assertNonUiThread();
        Intent psSettingsIntent = new Intent(SAFETY_CENTER_INTENT);
        psSettingsIntent.putExtra(PS_SETTINGS_FRAGMENT_KEY, PS_SETTINGS_FRAGMENT_VALUE);
        ResolveInfo resolveInfo = mAllApps.getContext().getPackageManager()
                .resolveActivity(psSettingsIntent, PackageManager.MATCH_SYSTEM_ONLY);
        mPrivateSpaceSettingsAvailable = resolveInfo != null;
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
            if (Flags.privateSpaceAnimation() && mAllApps.getActiveRecyclerView()
                    == mainAdapterHolder.mRecyclerView) {
                RecyclerViewAnimationController recyclerViewAnimationController =
                        new RecyclerViewAnimationController(mAllApps);
                recyclerViewAnimationController.animateToState(true /* expand */,
                        ANIMATION_DURATION, () -> {});
            }
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

    @Override
    public Predicate<UserHandle> getUserMatcher() {
        return mPrivateProfileMatcher;
    }
}
