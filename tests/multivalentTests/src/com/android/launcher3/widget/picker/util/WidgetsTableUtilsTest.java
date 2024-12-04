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
import static org.mockito.Mockito.when;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.util.WidgetsTableUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsTableUtilsTest {
    private static final String TEST_PACKAGE = "com.google.test";

    private static final int SPACE_SIZE = 10;
    // Note - actual widget size includes SPACE_SIZE (border) + cell padding.
    private static final int CELL_SIZE = 50;
    private static final int NUM_OF_COLS = 5;
    private static final int NUM_OF_ROWS = 5;

    @Mock
    private IconCache mIconCache;

    private DeviceProfile mTestDeviceProfile;

    private Context mContext;
    private InvariantDeviceProfile mTestInvariantProfile;
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

        mContext = new ActivityContextWrapper(getApplicationContext());

        mTestInvariantProfile = new InvariantDeviceProfile();
        mTestInvariantProfile.numColumns = NUM_OF_COLS;
        mTestInvariantProfile.numRows = NUM_OF_ROWS;

        initDP();
        initTestWidgets();
        initTestShortcuts();

        doAnswer(invocation -> ((ComponentWithLabel) invocation.getArgument(0))
                .getComponent().getPackageName())
                .when(mIconCache).getTitleNoCache(any());
    }


    @Test
    public void groupWithReordering_widgetsOnly_maxSpanPxPerRow220_cellPadding0() {
        List<WidgetItem> widgetItems = List.of(mWidget4x4, mWidget2x3, mWidget1x1, mWidget2x4,
                mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsUsingRowPxWithReordering(widgetItems, mContext,
                        mTestDeviceProfile, 220, 0);

        // With reordering, rows displayed in order of increasing size.
        // Row 0: 1x1(50px)
        // Row 1: 2x2(in a 2x2 container - 110px)
        // Row 2: 2x3(in a 2x3 container - 110px), 2x4(in a 2x3 container - 110px)
        // Row 3: 4x4(in a 3x3 container in tablet - 170px; 4x3 on phone - 230px)
        assertThat(widgetItemInTable).hasSize(4);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget1x1);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x2);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget2x3, mWidget2x4);
        assertThat(widgetItemInTable.get(3)).containsExactly(mWidget4x4);
    }

    @Test
    public void groupWithReordering_widgetsOnly_maxSpanPxPerRow220_cellPadding10() {
        List<WidgetItem> widgetItems = List.of(mWidget4x4, mWidget2x3, mWidget1x1, mWidget2x4,
                mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsUsingRowPxWithReordering(widgetItems, mContext,
                        mTestDeviceProfile, 220, 10);

        // With reordering, but space taken up by cell padding, so, no grouping (even if 2x2 and 2x3
        // use same preview container).
        // Row 0: 1x1(50px)
        // Row 1: 2x2(in a 2x2 container: 130px)
        // Row 2: 2x3(in a 2x3 container: 130px)
        // Row 3: 2x4(in a 2x3 container: 130px)
        // Row 4: 4x4(in a 3x3 container in tablet - 190px; 4x3 on phone - 250px)
        assertThat(widgetItemInTable).hasSize(5);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget1x1);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x2);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget2x3);
        assertThat(widgetItemInTable.get(3)).containsExactly(mWidget2x4);
        assertThat(widgetItemInTable.get(4)).containsExactly(mWidget4x4);
    }

    @Test
    public void groupWithReordering_widgetsOnly_maxSpanPxPerRow260_cellPadding10() {
        List<WidgetItem> widgetItems = List.of(mWidget4x4, mWidget2x3, mWidget1x1, mWidget2x4,
                mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsUsingRowPxWithReordering(widgetItems, mContext,
                        mTestDeviceProfile, 260, 10);

        // With reordering, even with cellPadding, enough space to group 2x3 and 2x4 (which also use
        // same container)
        // Row 0: 1x1(50px)
        // Row 1: 2x2(in a 2x2 container: 130px)
        // Row 2: 2x3(in a 2x3 container: 130px), 2x4(in a 2x3 container: 130px)
        // Row 3: 4x4(in a 3x3 container in tablet - 190px; 4x3 on phone - 250px)
        assertThat(widgetItemInTable).hasSize(4);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget1x1);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x2);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget2x3, mWidget2x4);
        assertThat(widgetItemInTable.get(3)).containsExactly(mWidget4x4);
    }

    @Test
    public void groupWithReordering_widgetsOnly_maxSpanPxPerRow350_cellPadding0() {
        List<WidgetItem> widgetItems = List.of(mWidget4x4, mWidget2x3, mWidget1x1, mWidget2x4,
                mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsUsingRowPxWithReordering(widgetItems, mContext,
                        mTestDeviceProfile, 350, 0);

        // With reordering, rows displayed in order of increasing size.
        // Row 0: 1x1(50px)
        // Row 1: 2x2(in a 2x2 container: 110px)
        // Row 2: 2x3(in a 2x3 container: 110px), 2x4(in a 2x3 container: 110px)
        // Row 3: 4x4(in a 3x3 container in tablet - 170px; 4x3 on phone - 230px)
        assertThat(widgetItemInTable).hasSize(4);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget1x1);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x2);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget2x3, mWidget2x4);
        assertThat(widgetItemInTable.get(3)).containsExactly(mWidget4x4);
    }

    @Test
    public void groupWithReordering_mixItems_maxSpanPxPerRow350_cellPadding0() {
        List<WidgetItem> widgetItems = List.of(mWidget4x4, mShortcut3, mWidget2x3, mShortcut1,
                mWidget1x1, mShortcut2, mWidget2x4, mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsUsingRowPxWithReordering(widgetItems, mContext,
                        mTestDeviceProfile, 350, 0);

        // With reordering - rows displays in order of increasing size:
        // Row 0: 1x1(50px)
        // Row 1: 2x2(110px)
        // Row 2: 2x3 (in a 2x3 container 110px), 2x4 (in a 2x3 container 110px)
        // Row 3: 4x4 (in a 3x3 container in tablet - 170px; 4x3 on phone - 230px)
        // Row 4: shortcut3, shortcut1, shortcut2 (shortcuts are always displayed at bottom)
        assertThat(widgetItemInTable).hasSize(5);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget1x1);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x2);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget2x3, mWidget2x4);
        assertThat(widgetItemInTable.get(3)).containsExactly(mWidget4x4);
        assertThat(widgetItemInTable.get(4)).containsExactly(mShortcut3, mShortcut2, mShortcut1);
    }

    @Test
    public void groupWithoutReordering_maxSpanPxPerRow220_cellPadding0() {
        List<WidgetItem> widgetItems =
                List.of(mWidget4x4, mWidget2x3, mWidget1x1, mWidget2x4, mWidget2x2);

        List<ArrayList<WidgetItem>> widgetItemInTable =
                WidgetsTableUtils.groupWidgetItemsUsingRowPxWithoutReordering(widgetItems, mContext,
                        mTestDeviceProfile, 220, 0);

        // Without reordering, widgets are grouped only if the next one fits and uses same preview
        // container:
        // Row 0: 4x4(in a 3x3 container in tablet - 170px; 4x3 on phone - 230px)
        // Row 1: 2x3(in a 2x3 container - 110px)
        // Row 2: 1x1(50px)
        // Row 3: 2x4(in a 2x3 container - 110px)
        // Row 4: 2x2(in a 2x2 container - 110px)
        assertThat(widgetItemInTable).hasSize(5);
        assertThat(widgetItemInTable.get(0)).containsExactly(mWidget4x4);
        assertThat(widgetItemInTable.get(1)).containsExactly(mWidget2x3);
        assertThat(widgetItemInTable.get(2)).containsExactly(mWidget1x1);
        assertThat(widgetItemInTable.get(3)).containsExactly(mWidget2x4);
        assertThat(widgetItemInTable.get(4)).containsExactly(mWidget2x2);
    }

    private void initDP() {
        DeviceProfile dp = LauncherAppState.getIDP(mContext)
                .getDeviceProfile(mContext).copy(mContext);
        mTestDeviceProfile = Mockito.spy(dp);

        doAnswer(i -> {
            ((Point) i.getArgument(0)).set(CELL_SIZE, CELL_SIZE);
            return null;
        }).when(mTestDeviceProfile).getCellSize(any(Point.class));
        when(mTestDeviceProfile.getCellSize()).thenReturn(new Point(CELL_SIZE, CELL_SIZE));
        mTestDeviceProfile.cellLayoutBorderSpacePx = new Point(SPACE_SIZE, SPACE_SIZE);
        mTestDeviceProfile.widgetPadding.setEmpty();
        mTestDeviceProfile.allAppsIconSizePx = 0;
    }

    private void initTestWidgets() {
        List<Point> widgetSizes = List.of(new Point(1, 1), new Point(2, 2), new Point(2, 3),
                new Point(2, 4), new Point(4, 4));

        ArrayList<WidgetItem> widgetItems = new ArrayList<>();
        widgetSizes.stream().forEach(widgetSize -> {
            AppWidgetProviderInfo info = createAppWidgetProviderInfo(
                    ComponentName.createRelative(
                            TEST_PACKAGE,
                            ".WidgetProvider_" + widgetSize.x + "x" + widgetSize.y));
            LauncherAppWidgetProviderInfo widgetInfo =
                    LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, info);
            widgetInfo.spanX = widgetSize.x;
            widgetInfo.spanY = widgetSize.y;
            widgetItems.add(new WidgetItem(
                    widgetInfo, mTestInvariantProfile, mIconCache, mContext));
        });
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
