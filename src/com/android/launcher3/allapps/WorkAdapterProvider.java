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

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

import java.util.ArrayList;

/**
 * A UI expansion wrapper providing for providing work profile specific views
 */
public class WorkAdapterProvider extends BaseAdapterProvider {

    public static final String KEY_WORK_EDU_STEP = "showed_work_profile_edu";

    private static final int VIEW_TYPE_WORK_EDU_CARD = 1 << 20;
    private static final int VIEW_TYPE_WORK_DISABLED_CARD = 1 << 21;
    private final Runnable mRefreshCB;
    private final BaseDraggingActivity mLauncher;
    private boolean mEnabled;

    WorkAdapterProvider(BaseDraggingActivity launcher, Runnable refreshCallback) {
        mLauncher = launcher;
        mRefreshCB = refreshCallback;
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
        return new AllAppsGridAdapter.ViewHolder(layoutInflater.inflate(viewId, parent, false));
    }

    /**
     * returns whether or not work apps should be visible in work tab.
     */
    public boolean shouldShowWorkApps() {
        return mEnabled;
    }

    /**
     * Adds work profile specific adapter items to adapterItems and returns number of items added
     */
    public int addWorkItems(ArrayList<AllAppsGridAdapter.AdapterItem> adapterItems) {
        if (!mEnabled) {
            //add disabled card here.
            AllAppsGridAdapter.AdapterItem disabledCard = new AllAppsGridAdapter.AdapterItem();
            disabledCard.viewType = VIEW_TYPE_WORK_DISABLED_CARD;
            adapterItems.add(disabledCard);
        } else if (!isEduSeen()) {
            AllAppsGridAdapter.AdapterItem eduCard = new AllAppsGridAdapter.AdapterItem();
            eduCard.viewType = VIEW_TYPE_WORK_EDU_CARD;
            adapterItems.add(eduCard);
        }

        return adapterItems.size();
    }

    /**
     * Sets the current state of work profile
     */
    public void updateCurrentState(boolean isEnabled) {
        mEnabled = isEnabled;
        mRefreshCB.run();
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
        return Utilities.getPrefs(mLauncher).getInt(KEY_WORK_EDU_STEP, 0) != 0;
    }
}
