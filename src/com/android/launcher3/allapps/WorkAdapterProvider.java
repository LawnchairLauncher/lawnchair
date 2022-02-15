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

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;

/**
 * A UI expansion wrapper providing for providing work profile specific views
 */
public class WorkAdapterProvider extends BaseAdapterProvider {

    public static final String KEY_WORK_EDU_STEP = "showed_work_profile_edu";

    private static final int VIEW_TYPE_WORK_EDU_CARD = 1 << 20;
    private static final int VIEW_TYPE_WORK_DISABLED_CARD = 1 << 21;

    @WorkProfileManager.WorkProfileState
    private int mState;
    private ActivityContext mActivityContext;
    private SharedPreferences mPreferences;

    WorkAdapterProvider(ActivityContext activityContext, SharedPreferences prefs) {
        mActivityContext = activityContext;
        mPreferences = prefs;
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) {
        if (holder.itemView instanceof WorkEduCard) {
            ((WorkEduCard) holder.itemView).setPosition(position);
        }
    }

    @Override
    public AllAppsGridAdapter.ViewHolder onCreateViewHolder(LayoutInflater layoutInflater,
            ViewGroup parent, int viewType) {
        int viewId = viewType == VIEW_TYPE_WORK_DISABLED_CARD ? R.layout.work_apps_paused
                : R.layout.work_apps_edu;
        View view = layoutInflater.inflate(viewId, parent, false);
        setDeviceManagementResources(view, viewType);
        return new AllAppsGridAdapter.ViewHolder(view);
    }

    private void setDeviceManagementResources(View view, int viewType) {
        StringCache cache = mActivityContext.getStringCache();
        if (cache == null) {
            return;
        }
        if (viewType == VIEW_TYPE_WORK_DISABLED_CARD) {
            setWorkProfilePausedResources(view, cache);
        } else {
            setWorkProfileEduResources(view, cache);
        }
    }

    private void setWorkProfilePausedResources(View view, StringCache cache) {
        TextView title = view.findViewById(R.id.work_apps_paused_title);
        title.setText(cache.workProfilePausedTitle);

        TextView body = view.findViewById(R.id.work_apps_paused_content);
        body.setText(cache.workProfilePausedDescription);

        TextView button = view.findViewById(R.id.enable_work_apps);
        button.setText(cache.workProfileEnableButton);
    }

    private void setWorkProfileEduResources(View view, StringCache cache) {
        TextView title = view.findViewById(R.id.work_apps_paused_title);
        title.setText(cache.workProfileEdu);

        Button button = view.findViewById(R.id.action_btn);
        button.setText(cache.workProfileEduAccept);
    }

    /**
     * returns whether or not work apps should be visible in work tab.
     */
    public boolean shouldShowWorkApps() {
        return mState != WorkProfileManager.STATE_DISABLED;
    }

    /**
     * Adds work profile specific adapter items to adapterItems and returns number of items added
     */
    public int addWorkItems(ArrayList<AllAppsGridAdapter.AdapterItem> adapterItems) {
        if (mState == WorkProfileManager.STATE_DISABLED) {
            //add disabled card here.
            AllAppsGridAdapter.AdapterItem disabledCard = new AllAppsGridAdapter.AdapterItem();
            disabledCard.viewType = VIEW_TYPE_WORK_DISABLED_CARD;
            adapterItems.add(disabledCard);
        } else if (mState == WorkProfileManager.STATE_ENABLED && !isEduSeen()) {
            AllAppsGridAdapter.AdapterItem eduCard = new AllAppsGridAdapter.AdapterItem();
            eduCard.viewType = VIEW_TYPE_WORK_EDU_CARD;
            adapterItems.add(eduCard);
        }

        return adapterItems.size();
    }

    /**
     * Sets the current state of work profile
     */
    public void updateCurrentState(@WorkProfileManager.WorkProfileState int state) {
        mState = state;
    }

    @Override
    public boolean isViewSupported(int viewType) {
        return viewType == VIEW_TYPE_WORK_DISABLED_CARD || viewType == VIEW_TYPE_WORK_EDU_CARD;
    }

    @Override
    public int getItemsPerRow(int viewType, int appsPerRow) {
        return 1;
    }

    private boolean isEduSeen() {
        return mPreferences.getInt(KEY_WORK_EDU_STEP, 0) != 0;
    }
}
