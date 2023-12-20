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

package com.android.launcher3;

import static android.content.ClipDescription.MIMETYPE_TEXT_INTENT;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.android.launcher3.dragndrop.SimpleDragLayer;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.widget.BaseWidgetSheet;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.picker.WidgetsFullSheet;

import java.util.ArrayList;

/** An Activity that can host Launcher's widget picker. */
public class WidgetPickerActivity extends BaseActivity {
    /**
     * Name of the extra that indicates that a widget being dragged.
     *
     * <p>When set to "true" in the result of startActivityForResult, the client that launched the
     * picker knows that activity was closed due to pending drag.
     */
    private static final String EXTRA_IS_PENDING_WIDGET_DRAG = "is_pending_widget_drag";

    private SimpleDragLayer<WidgetPickerActivity> mDragLayer;
    private WidgetsModel mModel;
    private final PopupDataProvider mPopupDataProvider = new PopupDataProvider(i -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);

        LauncherAppState app = LauncherAppState.getInstance(this);
        InvariantDeviceProfile idp = app.getInvariantDeviceProfile();

        mDeviceProfile = idp.getDeviceProfile(this);
        mModel = new WidgetsModel();

        setContentView(R.layout.widget_picker_activity);
        mDragLayer = findViewById(R.id.drag_layer);
        mDragLayer.recreateControllers();

        WindowInsetsController wc = mDragLayer.getWindowInsetsController();
        wc.hide(navigationBars() + statusBars());

        BaseWidgetSheet widgetSheet = WidgetsFullSheet.show(this, true);
        widgetSheet.disableNavBarScrim(true);
        widgetSheet.addOnCloseListener(this::finish);

        refreshAndBindWidgets();
    }

    @NonNull
    @Override
    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    @Override
    public SimpleDragLayer<WidgetPickerActivity> getDragLayer() {
        return mDragLayer;
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return v -> {
            final AppWidgetProviderInfo info =
                    (v instanceof WidgetCell) ? ((WidgetCell) v).getWidgetItem().widgetInfo : null;
            if (info == null || info.provider == null) {
                return;
            }

            setResult(RESULT_OK, new Intent()
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, info.provider)
                    .putExtra(Intent.EXTRA_USER, info.getProfile()));

            finish();
        };
    }

    @Override
    public View.OnLongClickListener getAllAppsItemLongClickListener() {
        return view -> {
            if (!(view instanceof WidgetCell widgetCell)) return false;

            if (widgetCell.getWidgetView().getDrawable() == null
                    && widgetCell.getAppWidgetHostViewPreview() == null) {
                // The widget preview hasn't been loaded; so, we abort the drag.
                return false;
            }

            final AppWidgetProviderInfo info = widgetCell.getWidgetItem().widgetInfo;
            if (info == null || info.provider == null) {
                return false;
            }

            ClipData clipData = new ClipData(
                    new ClipDescription(
                            /* label= */ "", // not displayed anywhere; so, set to empty.
                            new String[]{MIMETYPE_TEXT_INTENT}
                    ),
                    new ClipData.Item(new Intent()
                            .putExtra(Intent.EXTRA_USER, info.getProfile())
                            .putExtra(Intent.EXTRA_COMPONENT_NAME, info.provider))
            );

            // Set result indicating activity was closed due a widget being dragged.
            setResult(RESULT_OK, new Intent()
                    .putExtra(EXTRA_IS_PENDING_WIDGET_DRAG, true));

            // DRAG_FLAG_GLOBAL permits dragging data beyond app window.
            return view.startDragAndDrop(
                    clipData,
                    new View.DragShadowBuilder(view),
                    /* myLocalState= */ null,
                    View.DRAG_FLAG_GLOBAL
            );
        };
    }

    private void refreshAndBindWidgets() {
        MODEL_EXECUTOR.execute(() -> {
            LauncherAppState app = LauncherAppState.getInstance(this);
            mModel.update(app, null);
            final ArrayList<WidgetsListBaseEntry> widgets =
                    mModel.getWidgetsListForPicker(app.getContext());
            MAIN_EXECUTOR.execute(() -> mPopupDataProvider.setAllWidgets(widgets));
        });
    }
}
