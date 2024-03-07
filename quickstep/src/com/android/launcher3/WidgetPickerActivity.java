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

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.os.Bundle;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.android.launcher3.dragndrop.SimpleDragLayer;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.widget.BaseWidgetSheet;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.picker.WidgetsFullSheet;

import java.util.ArrayList;

/** An Activity that can host Launcher's widget picker. */
public class WidgetPickerActivity extends BaseActivity {
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
