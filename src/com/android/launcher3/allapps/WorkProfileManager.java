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

import static com.android.launcher3.LauncherPrefs.WORK_EDU_STEP;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.SEARCH;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.WORK;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TURN_OFF_WORK_APPS_TAP;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_HAS_SHORTCUT_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_CHANGE_PERMISSION;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;

import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Companion class for {@link ActivityAllAppsContainerView} to manage work tab and personal tab
 * related
 * logic based on {@link UserProfileState}?
 */
public class WorkProfileManager extends UserProfileManager
        implements PersonalWorkSlidingTabStrip.OnActivePageChangedListener {
    private static final String TAG = "WorkProfileManager";
    private final ActivityAllAppsContainerView<?> mAllApps;
    private WorkModeSwitch mWorkModeSwitch;
    private final Predicate<UserHandle> mWorkProfileMatcher;

    public WorkProfileManager(
            UserManager userManager, ActivityAllAppsContainerView allApps,
            StatsLogManager statsLogManager, UserCache userCache) {
        super(userManager, statsLogManager, userCache);
        mAllApps = allApps;
        mWorkProfileMatcher = (user) -> userCache.getUserInfo(user).isWork();
    }

    /**
     * Posts quite mode enable/disable call for work profile user
     */
    public void setWorkProfileEnabled(boolean enabled) {
        updateCurrentState(STATE_TRANSITION);
        setQuietMode(!enabled);
    }

    @Override
    public void onActivePageChanged(int page) {
        updateWorkFAB(page);
    }

    private void updateWorkFAB(int page) {
        if (mWorkModeSwitch != null) {
            if (page == MAIN || page == SEARCH) {
                mWorkModeSwitch.animateVisibility(false);
            } else if (page == WORK && getCurrentState() == STATE_ENABLED) {
                mWorkModeSwitch.animateVisibility(true);
            }
        }
    }

    /**
     * Requests work profile state from {@link AllAppsStore} and updates work profile related views
     */
    public void reset() {
        int quietModeFlag;
        if (Flags.enablePrivateSpace()) {
            quietModeFlag = FLAG_WORK_PROFILE_QUIET_MODE_ENABLED;
        } else {
            quietModeFlag = FLAG_QUIET_MODE_ENABLED;
        }
        boolean isEnabled = !mAllApps.getAppsStore().hasModelFlag(quietModeFlag);
        updateCurrentState(isEnabled ? STATE_ENABLED : STATE_DISABLED);
        if (mWorkModeSwitch != null) {
            // reset the position of the button and clear IME insets.
            mWorkModeSwitch.getImeInsets().setEmpty();
            mWorkModeSwitch.updateTranslationY();
        }
    }

    private void updateCurrentState(@UserProfileState int currentState) {
        setCurrentState(currentState);
        if (getAH() != null) {
            getAH().mAppsList.updateAdapterItems();
        }
        if (mWorkModeSwitch != null) {
            updateWorkFAB(mAllApps.getCurrentPage());
        }
        if (getCurrentState() == STATE_ENABLED) {
            attachWorkModeSwitch();
        } else if (getCurrentState() == STATE_DISABLED) {
            detachWorkModeSwitch();
        }
    }

    /**
     * Creates and attaches for profile toggle button to {@link ActivityAllAppsContainerView}
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
        if (mWorkModeSwitch.getParent() == null) {
            mAllApps.addView(mWorkModeSwitch);
        }
        if (mAllApps.getCurrentPage() != WORK) {
            mWorkModeSwitch.animateVisibility(false);
        }
        if (getAH() != null) {
            getAH().applyPadding();
        }
        mWorkModeSwitch.setOnClickListener(this::onWorkFabClicked);
        return true;
    }
    /**
     * Removes work profile toggle button from {@link ActivityAllAppsContainerView}
     */
    public void detachWorkModeSwitch() {
        if (mWorkModeSwitch != null && mWorkModeSwitch.getParent() == mAllApps) {
            mAllApps.removeView(mWorkModeSwitch);
        }
        mWorkModeSwitch = null;
    }

    @Nullable
    public WorkModeSwitch getWorkModeSwitch() {
        return mWorkModeSwitch;
    }

    private ActivityAllAppsContainerView.AdapterHolder getAH() {
        return mAllApps.mAH.get(WORK);
    }

    /**
     * returns whether or not work apps should be visible in work tab.
     */
    public boolean shouldShowWorkApps() {
        return getCurrentState() != WorkProfileManager.STATE_DISABLED;
    }

    public boolean hasWorkApps() {
        return Stream.of(mAllApps.getAppsStore().getApps()).anyMatch(getItemInfoMatcher());
    }

    /**
     * Adds work profile specific adapter items to adapterItems and returns number of items added
     */
    public int addWorkItems(ArrayList<AdapterItem> adapterItems) {
        if (getCurrentState() == WorkProfileManager.STATE_DISABLED) {
            //add disabled card here.
            adapterItems.add(new AdapterItem(VIEW_TYPE_WORK_DISABLED_CARD));
        } else if (getCurrentState() == WorkProfileManager.STATE_ENABLED && !isEduSeen()) {
            adapterItems.add(new AdapterItem(VIEW_TYPE_WORK_EDU_CARD));
        }
        return adapterItems.size();
    }

    private boolean isEduSeen() {
        return LauncherPrefs.get(mAllApps.getContext()).get(WORK_EDU_STEP) != 0;
    }

    private void onWorkFabClicked(View view) {
        if (getCurrentState() == STATE_ENABLED && mWorkModeSwitch.isEnabled()) {
            logEvents(LAUNCHER_TURN_OFF_WORK_APPS_TAP);
            setWorkProfileEnabled(false);
        }
    }

    public RecyclerView.OnScrollListener newScrollListener() {
        return new RecyclerView.OnScrollListener() {
            int totalDelta = 0;
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState){
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    totalDelta = 0;
                }
            }
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                WorkModeSwitch fab = getWorkModeSwitch();
                if (fab == null){
                    return;
                }
                totalDelta = Utilities.boundToRange(totalDelta,
                        -fab.getScrollThreshold(), fab.getScrollThreshold()) + dy;
                boolean isScrollAtTop = recyclerView.computeVerticalScrollOffset() == 0;
                if ((isScrollAtTop || totalDelta < -fab.getScrollThreshold())) {
                    fab.extend();
                } else if (totalDelta > fab.getScrollThreshold()) {
                    fab.shrink();
                }
            }
        };
    }

    @Override
    public Predicate<UserHandle> getUserMatcher() {
        return mWorkProfileMatcher;
    }
}
