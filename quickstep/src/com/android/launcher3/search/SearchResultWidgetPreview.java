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
package com.android.launcher3.search;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.widget.BaseWidgetSheet;
import com.android.launcher3.widget.PendingItemDragHelper;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.WidgetImageView;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * displays preview of a widget upon receiving {@link AppWidgetProviderInfo} from Search provider
 */
public class SearchResultWidgetPreview extends LinearLayout implements
        SearchTargetHandler, View.OnLongClickListener,
        View.OnClickListener {

    public static final String TARGET_TYPE_WIDGET_PREVIEW = "widget_preview";
    private final Launcher mLauncher;
    private final LauncherAppState mAppState;
    private WidgetCell mWidgetCell;
    private Toast mWidgetToast;

    private SearchTarget mSearchTarget;


    public SearchResultWidgetPreview(Context context) {
        this(context, null, 0);
    }

    public SearchResultWidgetPreview(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultWidgetPreview(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
        mAppState = LauncherAppState.getInstance(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWidgetCell = findViewById(R.id.widget_cell);
        mWidgetCell.setOnLongClickListener(this);
        mWidgetCell.setOnClickListener(this);
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        if (searchTarget.getExtras() == null
                || searchTarget.getExtras().getParcelable("provider") == null) {
            setVisibility(GONE);
            return;
        }
        mSearchTarget = searchTarget;
        AppWidgetProviderInfo providerInfo = searchTarget.getExtras().getParcelable("provider");
        LauncherAppWidgetProviderInfo pInfo = LauncherAppWidgetProviderInfo.fromProviderInfo(
                getContext(), providerInfo);
        MODEL_EXECUTOR.post(() -> {
            WidgetItem widgetItem = new WidgetItem(pInfo, mLauncher.getDeviceProfile().inv,
                    mAppState.getIconCache());
            MAIN_EXECUTOR.post(() -> {
                mWidgetCell.applyFromCellItem(widgetItem, mAppState.getWidgetCache());
                mWidgetCell.ensurePreview();
            });
        });

    }

    @Override
    public boolean onLongClick(View view) {
        view.cancelLongPress();
        if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;
        if (mWidgetCell.getTag() == null) return false;

        WidgetImageView imageView = mWidgetCell.getWidgetView();
        if (imageView.getBitmap() == null) {
            return false;
        }

        int[] loc = new int[2];
        mLauncher.getDragLayer().getLocationInDragLayer(imageView, loc);

        new PendingItemDragHelper(mWidgetCell).startDrag(
                imageView.getBitmapBounds(), imageView.getBitmap().getWidth(), imageView.getWidth(),
                new Point(loc[0], loc[1]), mLauncher.getAppsView(), new DragOptions());
        handleSelection(SearchTargetEvent.LONG_PRESS);
        return true;
    }

    @Override
    public void onClick(View view) {
        mWidgetToast = BaseWidgetSheet.showWidgetToast(getContext(), mWidgetToast);
        handleSelection(SearchTargetEvent.SELECT);
    }

    @Override
    public void handleSelection(int eventType) {
        SearchEventTracker.INSTANCE.get(getContext()).notifySearchTargetEvent(
                new SearchTargetEvent.Builder(mSearchTarget, eventType).build());
    }
}
