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
package com.android.launcher3.widget.picker.util;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.util.WidgetsTableUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsTableUtilsTest {
    private static final String TEST_PACKAGE = "com.google.test";

    @Mock
    private IconCache mIconCache;

    private Context mContext;
    private InvariantDeviceProfile mTestProfile;
    private WidgetItem mWidget1x1;
    private WidgetItem mWidget2x2;
    private WidgetItem mWidget2x3;
    private WidgetItem mWidget2x4;
    private WidgetItem mWidget4x4;

    private WidgetItem mShortcut1;
    private WidgetItem mShortcut2;
    private WidgetItem mShortcut3;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = getApplicationContext();

        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;

        initTestWidgets();
        initTestShortcuts();

        doAnswer(invocation -> ((ComponentWithLabel) invocation.getArgument(0))
                .getComponent().getPackageName())
                .when(mIconCache).getTitleNoCache(any());
    }


    @Test
    public void groupWidgetItemsIntoTableWithReordering_widgetsOnly_maxSpansPerRow5_shouldGroupWidgetsInTable() {
        List<WidgetItem> widgetItems = List.of(mWidget4x4, mWidget2x3, mWidget1x1, mWidget2x4,
                mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsIntoTableWithReordering(
                        widgetItems, /* maxSpansPerRow= */ 5);

        // Row 0: 1x1, 2x2
        // Row 1: 2x3, 2x4
        // Row 2: 4x4
        assertThat(widgetItemInTable).hasSize(3);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget1x1, mWidget2x2);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x3, mWidget2x4);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget4x4);
    }

    @Test
    public void groupWidgetItemsIntoTableWithReordering_widgetsOnly_maxSpansPerRow4_shouldGroupWidgetsInTable() {
        List<WidgetItem> widgetItems = List.of(mWidget4x4, mWidget2x3, mWidget1x1, mWidget2x4,
                mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsIntoTableWithReordering(
                        widgetItems, /* maxSpansPerRow= */ 4);

        // Row 0: 1x1, 2x2
        // Row 1: 2x3,
        // Row 2: 2x4,
        // Row 3: 4x4
        assertThat(widgetItemInTable).hasSize(4);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget1x1, mWidget2x2);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x3);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget2x4);
        assertThat(widgetItemInTable.get(3)).containsExactly(mWidget4x4);
    }

    @Test
    public void groupWidgetItemsIntoTableWithReordering_mixItems_maxSpansPerRow4_shouldGroupWidgetsInTable() {
        List<WidgetItem> widgetItems = List.of(mWidget4x4, mShortcut3, mWidget2x3, mShortcut1,
                mWidget1x1, mShortcut2, mWidget2x4, mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsIntoTableWithReordering(
                        widgetItems, /* maxSpansPerRow= */ 4);

        // Row 0: 1x1, 2x2
        // Row 1: 2x3,
        // Row 2: 2x4,
        // Row 3: 4x4
        // Row 4: shortcut3, shortcut1, shortcut2
        assertThat(widgetItemInTable).hasSize(5);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget1x1, mWidget2x2);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x3);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget2x4);
        assertThat(widgetItemInTable.get(3)).containsExactly(mWidget4x4);
        assertThat(widgetItemInTable.get(4)).containsExactly(mShortcut3, mShortcut2, mShortcut1);
    }

    @Test
    public void groupWidgetItemsIntoTableWithoutReordering_shouldMaintainTheOrder() {
        List<WidgetItem> widgetItems =
                List.of(mWidget4x4, mWidget2x3, mWidget1x1, mWidget2x4, mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsIntoTableWithoutReordering(
                        widgetItems, /* maxSpansPerRow= */ 5);

        // Row 0: 4x4
        // Row 1: 2x3, 1x1
        // Row 2: 2x4, 2x2
        assertThat(widgetItemInTable).hasSize(3);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget4x4);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x3, mWidget1x1);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget2x4, mWidget2x2);
    }

    private void initTestWidgets() {
        List<Point> widgetSizes = List.of(new Point(1, 1), new Point(2, 2), new Point(2, 3),
                new Point(2, 4), new Point(4, 4));

        ArrayList<WidgetItem> widgetItems = new ArrayList<>();
        widgetSizes.stream().forEach(
                widgetSize -> {
                    AppWidgetProviderInfo info = createAppWidgetProviderInfo(
                            ComponentName.createRelative(
                                    TEST_PACKAGE,
                                    ".WidgetProvider_" + widgetSize.x + "x" + widgetSize.y));
                    LauncherAppWidgetProviderInfo widgetInfo =
                            LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
                    widgetInfo.spanX = widgetSize.x;
                    widgetInfo.spanY = widgetSize.y;
                    widgetItems.add(new WidgetItem(widgetInfo, mTestProfile, mIconCache));
                }
        );
        mWidget1x1 = widgetItems.get(0);
        mWidget2x2 = widgetItems.get(1);
        mWidget2x3 = widgetItems.get(2);
        mWidget2x4 = widgetItems.get(3);
        mWidget4x4 = widgetItems.get(4);
    }

    private void initTestShortcuts() {
        PackageManager packageManager = mContext.getPackageManager();
        mShortcut1 = new WidgetItem(new TestShortcutConfigActivityInfo(
                ComponentName.createRelative(TEST_PACKAGE, ".shortcut1"), UserHandle.CURRENT),
                mIconCache, packageManager);
        mShortcut2 = new WidgetItem(new TestShortcutConfigActivityInfo(
                ComponentName.createRelative(TEST_PACKAGE, ".shortcut2"), UserHandle.CURRENT),
                mIconCache, packageManager);
        mShortcut3 = new WidgetItem(new TestShortcutConfigActivityInfo(
                ComponentName.createRelative(TEST_PACKAGE, ".shortcut3"), UserHandle.CURRENT),
                mIconCache, packageManager);

    }

    private final class TestShortcutConfigActivityInfo extends ShortcutConfigActivityInfo {

        TestShortcutConfigActivityInfo(ComponentName componentName, UserHandle user) {
            super(componentName, user);
        }

        @Override
        public Drawable getFullResIcon(IconCache cache) {
            return null;
        }

        @Override
        public CharSequence getLabel(PackageManager pm) {
            return null;
        }
    }
}
