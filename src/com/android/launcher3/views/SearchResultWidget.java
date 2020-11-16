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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AppWidgetResizeFrame;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchEventTracker;
import com.android.launcher3.allapps.search.SearchWidgetInfoContainer;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * displays live version of a widget upon receiving {@link AppWidgetProviderInfo} from Search
 * provider
 */
public class SearchResultWidget extends RelativeLayout implements
        AllAppsSearchBarController.SearchTargetHandler, DraggableView, View.OnLongClickListener {

    private static final String TAG = "SearchResultWidget";

    public static final String TARGET_TYPE_WIDGET_LIVE = "widget";

    private final Rect mWidgetOffset = new Rect();

    private final Launcher mLauncher;
    private final CheckLongPressHelper mLongPressHelper;
    private final GestureDetector mClickDetector;
    private final AppWidgetHostView mHostView;
    private final float mScaleToFit;

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
        mLauncher = Launcher.getLauncher(context);
        mHostView = new AppWidgetHostView(context);
        DeviceProfile grid = mLauncher.getDeviceProfile();
        mScaleToFit = Math.min(grid.appWidgetScale.x, grid.appWidgetScale.y);

        // detect tap event on widget container for search target event reporting
        mClickDetector = new GestureDetector(context,
                new ClickListener(() -> handleSelection(SearchTargetEvent.CHILD_SELECT)));

        mLongPressHelper = new CheckLongPressHelper(this);
        mLongPressHelper.setLongPressTimeoutFactor(1);
        setOnLongClickListener(this);
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
        setTag(info);
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
        mLongPressHelper.onTouchEvent(ev);
        mClickDetector.onTouchEvent(ev);
        if (ev.getAction() == MotionEvent.ACTION_UP && !mLongPressHelper.hasPerformedLongPress()) {
            handleSelection(SearchTargetEvent.CHILD_SELECT);
        }
        return super.onInterceptTouchEvent(ev);
    }


    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    @Override
    public int getViewType() {
        return DraggableView.DRAGGABLE_WIDGET;
    }

    @Override
    public void getSourceVisualDragBounds(Rect bounds) {
        mHostView.getHitRect(mWidgetOffset);
        int width = (int) (mHostView.getMeasuredWidth() * mScaleToFit);
        int height = (int) (mHostView.getMeasuredHeight() * mScaleToFit);
        bounds.set(mWidgetOffset.left,
                mWidgetOffset.top,
                width + mWidgetOffset.left,
                height + mWidgetOffset.top);
    }

    @Override
    public boolean onLongClick(View view) {
        ItemLongClickListener.INSTANCE_ALL_APPS.onLongClick(view);
        handleSelection(SearchTargetEvent.LONG_PRESS);
        return false;
    }

    static class ClickListener extends GestureDetector.SimpleOnGestureListener {
        private final Runnable mCb;

        ClickListener(Runnable cb) {
            mCb = cb;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            mCb.run();
            return super.onSingleTapConfirmed(e);
        }
    }
}
