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

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AppWidgetResizeFrame;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchEventTracker;
import com.android.launcher3.allapps.search.SearchWidgetInfoContainer;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * displays live version of a widget upon receiving {@link AppWidgetProviderInfo} from Search
 * provider
 */
public class SearchResultWidget extends RelativeLayout implements
        AllAppsSearchBarController.SearchTargetHandler {

    private static final String TAG = "SearchResultWidget";

    public static final String TARGET_TYPE_WIDGET_LIVE = "widget";

    private final Launcher mLauncher;
    private final AppWidgetHostView mHostView;

    private SearchTarget mSearchTarget;
    private AppWidgetProviderInfo mProviderInfo;

    private SearchWidgetInfoContainer mInfoContainer;

    public SearchResultWidget(@NonNull Context context) {
        this(context, null, 0);
    }

    public SearchResultWidget(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultWidget(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHostView = new AppWidgetHostView(context);
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addView(mHostView);
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        if (searchTarget.getExtras() == null
                || searchTarget.getExtras().getParcelable("provider") == null) {
            setVisibility(GONE);
            return;
        }
        AppWidgetProviderInfo providerInfo = searchTarget.getExtras().getParcelable("provider");
        if (mProviderInfo != null && providerInfo.provider.equals(mProviderInfo.provider)
                && providerInfo.getProfile().equals(mProviderInfo.getProfile())) {
            return;
        }
        removeListener();

        mSearchTarget = searchTarget;
        mProviderInfo = providerInfo;

        mInfoContainer = mLauncher.getLiveSearchManager().getPlaceHolderWidget(providerInfo);
        if (mInfoContainer == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        mInfoContainer.attachWidget(mHostView);
        PendingAddWidgetInfo info = (PendingAddWidgetInfo) mHostView.getTag();
        int[] size = mLauncher.getWorkspace().estimateItemSize(info);
        mHostView.getLayoutParams().width = size[0];
        mHostView.getLayoutParams().height = size[1];
        AppWidgetResizeFrame.updateWidgetSizeRanges(mHostView, mLauncher, info.spanX,
                info.spanY);
        mHostView.requestLayout();


    }

    /**
     * Stops hostView from getting updates on a widget provider
     */
    public void removeListener() {
        if (mInfoContainer != null) {
            mInfoContainer.detachWidget(mHostView);
        }
    }

    @Override
    public void handleSelection(int eventType) {
        SearchEventTracker.INSTANCE.get(getContext()).notifySearchTargetEvent(
                new SearchTargetEvent.Builder(mSearchTarget, eventType).build());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            handleSelection(SearchTargetEvent.CHILD_SELECT);
        }
        return super.onInterceptTouchEvent(ev);
    }
}
