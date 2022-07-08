/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Predicate;

/**
 * Companion class for {@link BaseAllAppsContainerView} to manage work tab and personal tab
 * related
 * logic based on {@link WorkProfileState}?
 */
public class WorkProfileManager implements PersonalWorkSlidingTabStrip.OnActivePageChangedListener {
    private static final String TAG = "WorkProfileManager";


    public static final int STATE_ENABLED = 1;
    public static final int STATE_DISABLED = 2;
    public static final int STATE_TRANSITION = 3;

    private final UserManager mUserManager;

    /**
     * Work profile manager states
     */
    @IntDef(value = {
            STATE_ENABLED,
            STATE_DISABLED,
            STATE_TRANSITION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WorkProfileState {
    }

    private final BaseAllAppsContainerView<?> mAllApps;
    private final WorkAdapterProvider mAdapterProvider;
    private final Predicate<ItemInfo> mMatcher;

    private WorkModeSwitch mWorkModeSwitch;
    private final DeviceProfile mDeviceProfile;

    @WorkProfileState
    private int mCurrentState;


    public WorkProfileManager(UserManager userManager, BaseAllAppsContainerView<?> allApps,
            SharedPreferences preferences, DeviceProfile deviceProfile) {
        mUserManager = userManager;
        mAllApps = allApps;
        mDeviceProfile = deviceProfile;
        mAdapterProvider = new WorkAdapterProvider(allApps.mActivityContext, preferences);
        mMatcher = mAllApps.mPersonalMatcher.negate();
    }

    /**
     * Posts quite mode enable/disable call for work profile user
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public void setWorkProfileEnabled(boolean enabled) {
        updateCurrentState(STATE_TRANSITION);
        UI_HELPER_EXECUTOR.post(() -> {
            for (UserHandle userProfile : mUserManager.getUserProfiles()) {
                if (Process.myUserHandle().equals(userProfile)) {
                    continue;
                }
                mUserManager.requestQuietModeEnabled(!enabled, userProfile);
            }
        });
    }

    @Override
    public void onActivePageChanged(int page) {
        if (mWorkModeSwitch != null) {
            mWorkModeSwitch.onActivePageChanged(page);
        }
    }

    /**
     * Requests work profile state from {@link AllAppsStore} and updates work profile related views
     */
    public void reset() {
        boolean isEnabled = !mAllApps.getAppsStore().hasModelFlag(FLAG_QUIET_MODE_ENABLED);
        updateCurrentState(isEnabled ? STATE_ENABLED : STATE_DISABLED);
    }

    private void updateCurrentState(@WorkProfileState int currentState) {
        mCurrentState = currentState;
        mAdapterProvider.updateCurrentState(currentState);
        if (getAH() != null) {
            getAH().mAppsList.updateAdapterItems();
        }
        if (mWorkModeSwitch != null) {
            mWorkModeSwitch.updateCurrentState(currentState == STATE_ENABLED);
        }
    }

    /**
     * Creates and attaches for profile toggle button to {@link BaseAllAppsContainerView}
     */
    public boolean attachWorkModeSwitch() {
        if (!mAllApps.getAppsStore().hasModelFlag(
                FLAG_HAS_SHORTCUT_PERMISSION | FLAG_QUIET_MODE_CHANGE_PERMISSION)) {
            Log.e(TAG, "unable to attach work mode switch; Missing required permissions");
            return false;
        }
        if (mWorkModeSwitch == null) {
            mWorkModeSwitch = (WorkModeSwitch) mAllApps.getLayoutInflater().inflate(
                    R.layout.work_mode_fab, mAllApps, false);
        }
        if (mWorkModeSwitch.getParent() != mAllApps) {
            mAllApps.addView(mWorkModeSwitch);
        }
        if (getAH() != null) {
            getAH().applyPadding();
        }
        mWorkModeSwitch.updateCurrentState(mCurrentState == STATE_ENABLED);
        return true;
    }
    /**
     * Removes work profile toggle button from {@link BaseAllAppsContainerView}
     */
    public void detachWorkModeSwitch() {
        if (mWorkModeSwitch != null && mWorkModeSwitch.getParent() == mAllApps) {
            mAllApps.removeView(mWorkModeSwitch);
        }
        mWorkModeSwitch = null;
    }

    public WorkAdapterProvider getAdapterProvider() {
        return mAdapterProvider;
    }

    public Predicate<ItemInfo> getMatcher() {
        return mMatcher;
    }

    @Nullable
    public WorkModeSwitch getWorkModeSwitch() {
        return mWorkModeSwitch;
    }

    private BaseAllAppsContainerView<?>.AdapterHolder getAH() {
        return mAllApps.mAH.get(BaseAllAppsContainerView.AdapterHolder.WORK);
    }

    public int getCurrentState() {
        return mCurrentState;
    }
}
