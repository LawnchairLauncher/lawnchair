/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchEventTracker;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * A {@link BubbleTextView} representing a single cell result in AllApps
 */
public class SearchResultIcon extends BubbleTextView implements
        AllAppsSearchBarController.SearchTargetHandler, View.OnClickListener,
        View.OnLongClickListener {
    private final Object[] mTargetInfo = createTargetInfo();
    private final Launcher mLauncher;

    private SearchTarget mSearchTarget;

    public SearchResultIcon(Context context) {
        this(context, null, 0);
    }

    public SearchResultIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setLongPressTimeoutFactor(1f);
        setOnFocusChangeListener(mLauncher.getFocusHandler());
        setOnClickListener(this);
        setOnLongClickListener(this);
        getLayoutParams().height = mLauncher.getDeviceProfile().allAppsCellHeightPx;
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        mSearchTarget = searchTarget;
        AllAppsStore appsStore = mLauncher.getAppsView().getAppsStore();
        if (searchTarget.type == SearchTarget.ItemType.APP) {
            AppInfo appInfo = appsStore.getAppFromSearchTarget(searchTarget);
            applyFromApplicationInfo(appInfo);
        }
    }

    @Override
    public Object[] getTargetInfo() {
        return mTargetInfo;
    }

    @Override
    public void handleSelection(int eventType) {
        SearchTargetEvent event = getSearchTargetEvent(mSearchTarget.type,
                eventType);
        if (mSearchTarget.type.equals(SearchTarget.ItemType.APP)) {
            event.bundle = HeroSearchResultView.getAppBundle((ItemInfo) getTag());
        }
        SearchEventTracker.INSTANCE.get(mLauncher).notifySearchTargetEvent(event);
    }

    @Override
    public void onClick(View view) {
        handleSelection(SearchTargetEvent.SELECT);
        mLauncher.getItemOnClickListener().onClick(view);
    }

    @Override
    public boolean onLongClick(View view) {
        handleSelection(SearchTargetEvent.LONG_PRESS);
        return ItemLongClickListener.INSTANCE_ALL_APPS.onLongClick(view);
    }
}
