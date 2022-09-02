/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.celllayout;

import static com.android.launcher3.util.WidgetUtils.createWidgetInfo;

import android.content.ComponentName;
import android.graphics.Rect;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

public class TestWorkspaceBuilder {

    private static final ComponentName APP_COMPONENT_NAME = new ComponentName(
            "com.google.android.calculator", "com.android.calculator2.Calculator");

    public AbstractLauncherUiTest mTest;

    private UserHandle mMyUser;

    public TestWorkspaceBuilder(AbstractLauncherUiTest test) {
        mTest = test;
        mMyUser = Process.myUserHandle();
    }

    private static final String TAG = "CellLayoutBoardBuilder";

    /**
     * Fills the given rect in WidgetRect with 1x1 widgets. This is useful to equalize cases.
     */
    private void fillWithWidgets(CellLayoutBoard.WidgetRect widgetRect) {
        int initX = widgetRect.getCellX();
        int initY = widgetRect.getCellY();
        for (int x = initX; x < initX + widgetRect.getSpanX(); x++) {
            for (int y = initY; y < initY + widgetRect.getSpanY(); y++) {
                try {
                    // this widgets are filling, we don't care if we can't place them
                    addWidgetInCell(
                            new CellLayoutBoard.WidgetRect(CellLayoutBoard.CellType.IGNORE,
                                    new Rect(x, y, x, y))
                    );
                } catch (Exception e) {
                    Log.d(TAG, "Unable to place filling widget at " + x + "," + y);
                }
            }
        }
    }

    private void addWidgetInCell(CellLayoutBoard.WidgetRect widgetRect) {
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(mTest, false);
        LauncherAppWidgetInfo item = createWidgetInfo(info,
                ApplicationProvider.getApplicationContext(), true);

        item.cellX = widgetRect.getCellX();
        item.cellY = widgetRect.getCellY();
        item.spanX = widgetRect.getSpanX();
        item.spanY = widgetRect.getSpanY();
        mTest.addItemToScreen(item);
    }

    private void addIconInCell(CellLayoutBoard.IconPoint iconPoint) {
        AppInfo appInfo = new AppInfo(APP_COMPONENT_NAME, "test icon", mMyUser,
                AppInfo.makeLaunchIntent(APP_COMPONENT_NAME));

        appInfo.cellX = iconPoint.getCoord().x;
        appInfo.cellY = iconPoint.getCoord().y;
        appInfo.minSpanY = appInfo.minSpanX = appInfo.spanX = appInfo.spanY = 1;
        appInfo.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        appInfo.componentName = APP_COMPONENT_NAME;

        mTest.addItemToScreen(new WorkspaceItemInfo(appInfo));
    }

    private void addCorrespondingWidgetRect(CellLayoutBoard.WidgetRect widgetRect) {
        if (widgetRect.mType == 'x') {
            fillWithWidgets(widgetRect);
        } else {
            addWidgetInCell(widgetRect);
        }
    }

    public void buildBoard(CellLayoutBoard board) {
        board.getWidgets().forEach(this::addCorrespondingWidgetRect);
        board.getIcons().forEach(this::addIconInCell);
    }
}
