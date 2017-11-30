/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ComponentKeyMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class PredictionRowView extends LinearLayout {

    private static final String TAG = "PredictionRowView";

    private HashMap<ComponentKey, AppInfo> mComponentToAppMap;
    private int mNumPredictedAppsPerRow;
    // The set of predicted app component names
    private final List<ComponentKeyMapper<AppInfo>> mPredictedAppComponents = new ArrayList<>();
    // The set of predicted apps resolved from the component names and the current set of apps
    private final List<AppInfo> mPredictedApps = new ArrayList<>();
    // This adapter is only used to create an identical item w/ same behavior as in the all apps RV
    private AllAppsGridAdapter mAdapter;

    public PredictionRowView(@NonNull Context context) {
        this(context, null);
    }

    public PredictionRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(LinearLayout.HORIZONTAL);
    }

    public void setup(AllAppsGridAdapter adapter,
            HashMap<ComponentKey, AppInfo> componentToAppMap, int numPredictedAppsPerRow) {
        mAdapter = adapter;
        mComponentToAppMap = componentToAppMap;
        mNumPredictedAppsPerRow = numPredictedAppsPerRow;
    }

    /**
     * Sets the number of apps per row.
     */
    public void setNumAppsPerRow(int numPredictedAppsPerRow) {
        if (mNumPredictedAppsPerRow != numPredictedAppsPerRow) {
            mNumPredictedAppsPerRow = numPredictedAppsPerRow;
            onAppsUpdated();
        }
    }

    /**
     * Returns the predicted apps.
     */
    public List<AppInfo> getPredictedApps() {
        return mPredictedApps;
    }

    /**
     * Sets the current set of predicted apps.
     *
     * This can be called before we get the full set of applications, we should merge the results
     * only in onAppsUpdated() which is idempotent.
     *
     * If the number of predicted apps is the same as the previous list of predicted apps,
     * we can optimize by swapping them in place.
     */
    public void setPredictedApps(List<ComponentKeyMapper<AppInfo>> apps) {
        mPredictedAppComponents.clear();
        mPredictedAppComponents.addAll(apps);
        mPredictedApps.addAll(processPredictedAppComponents(mPredictedAppComponents));
        onAppsUpdated();
    }

    private void onAppsUpdated() {
        if (getChildCount() != mNumPredictedAppsPerRow) {
            while (getChildCount() > mNumPredictedAppsPerRow) {
                removeViewAt(0);
            }
            while (getChildCount() < mNumPredictedAppsPerRow) {
                AllAppsGridAdapter.ViewHolder holder = mAdapter
                        .onCreateViewHolder(this, AllAppsGridAdapter.VIEW_TYPE_ICON);
                BubbleTextView icon = (BubbleTextView) holder.itemView;
                LinearLayout.LayoutParams params =
                        new LayoutParams(0, icon.getLayoutParams().height);
                params.weight = 1;
                icon.setLayoutParams(params);
                addView(icon);
            }
        }

        for (int i = 0; i < getChildCount(); i++) {
            BubbleTextView icon = (BubbleTextView) getChildAt(i);
            icon.reset();
            if (mPredictedApps.size() > i) {
                icon.setVisibility(View.VISIBLE);
                icon.applyFromApplicationInfo(mPredictedApps.get(i));
            } else {
                icon.setVisibility(View.INVISIBLE);
            }
        }
    }

    private List<AppInfo> processPredictedAppComponents(
            List<ComponentKeyMapper<AppInfo>> components) {
        if (mComponentToAppMap.isEmpty()) {
            // Apps have not been bound yet.
            return Collections.emptyList();
        }

        List<AppInfo> predictedApps = new ArrayList<>();
        for (ComponentKeyMapper<AppInfo> mapper : components) {
            AppInfo info = mapper.getItem(mComponentToAppMap);
            if (info != null) {
                predictedApps.add(info);
            } else {
                if (FeatureFlags.IS_DOGFOOD_BUILD) {
                    Log.e(TAG, "Predicted app not found: " + mapper);
                }
            }
            // Stop at the number of predicted apps
            if (predictedApps.size() == mNumPredictedAppsPerRow) {
                break;
            }
        }
        return predictedApps;
    }
}
