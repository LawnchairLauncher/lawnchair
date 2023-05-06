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
package com.android.launcher3.widget.picker.model;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.model.WidgetsListContentEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsListContentEntryTest {
    private static final String PACKAGE_NAME = "com.android.test";
    private static final String PACKAGE_NAME_2 = "com.android.test2";
    private final PackageItemInfo mPackageItemInfo1 = new PackageItemInfo(PACKAGE_NAME,
            UserHandle.CURRENT);
    private final PackageItemInfo mPackageItemInfo2 = new PackageItemInfo(PACKAGE_NAME_2,
            UserHandle.CURRENT);
    private final ComponentName mWidget1 = ComponentName.createRelative(PACKAGE_NAME, ".mWidget1");
    private final ComponentName mWidget2 = ComponentName.createRelative(PACKAGE_NAME, ".mWidget2");
    private final ComponentName mWidget3 = ComponentName.createRelative(PACKAGE_NAME, ".mWidget3");
    private final Map<ComponentName, String> mWidgetsToLabels = new HashMap();

    @Mock private IconCache mIconCache;

    private InvariantDeviceProfile mTestProfile;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mWidgetsToLabels.put(mWidget1, "Cat");
        mWidgetsToLabels.put(mWidget2, "Dog");
        mWidgetsToLabels.put(mWidget3, "Bird");

        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;

        doAnswer(invocation -> {
            ComponentWithLabel componentWithLabel = (ComponentWithLabel) invocation.getArgument(0);
            return mWidgetsToLabels.get(componentWithLabel.getComponent());
        }).when(mIconCache).getTitleNoCache(any());
    }

    @Test
    public void unsortedWidgets_diffLabels_shouldSortWidgetItems() {
        // GIVEN a list of widgets in unsorted order.
        // Cat 2x3
        WidgetItem widgetItem1 = createWidgetItem(mWidget1, /* spanX= */ 2, /* spanY= */ 3);
        // Dog 2x3
        WidgetItem widgetItem2 = createWidgetItem(mWidget2, /* spanX= */ 2, /* spanY= */ 3);
        // Bird 2x3
        WidgetItem widgetItem3 = createWidgetItem(mWidget3, /* spanX= */ 2, /* spanY= */ 3);

        // WHEN creates a WidgetsListRowEntry with the unsorted widgets.
        WidgetsListContentEntry widgetsListRowEntry = new WidgetsListContentEntry(mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1, widgetItem2, widgetItem3));

        // THEN the widgets list is sorted by their labels alphabetically: [Bird, Cat, Dog].
        assertThat(widgetsListRowEntry.mWidgets)
                .containsExactly(widgetItem3, widgetItem1, widgetItem2)
                .inOrder();
        assertThat(widgetsListRowEntry.mTitleSectionName).isEqualTo("T");
        assertThat(widgetsListRowEntry.mPkgItem).isEqualTo(mPackageItemInfo1);
    }

    @Test
    public void unsortedWidgets_sameLabels_differentSize_shouldSortWidgetItems() {
        // GIVEN a list of widgets in unsorted order.
        // Cat 3x3
        WidgetItem widgetItem1 = createWidgetItem(mWidget1, /* spanX= */ 3, /* spanY= */ 3);
        // Cat 1x2
        WidgetItem widgetItem2 = createWidgetItem(mWidget1, /* spanX= */ 1, /* spanY= */ 2);
        // Cat 2x2
        WidgetItem widgetItem3 = createWidgetItem(mWidget1, /* spanX= */ 2, /* spanY= */ 2);

        // WHEN creates a WidgetsListRowEntry with the unsorted widgets.
        WidgetsListContentEntry widgetsListRowEntry = new WidgetsListContentEntry(mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1, widgetItem2, widgetItem3));

        // THEN the widgets list is sorted by their gird sizes in an ascending order:
        // [1x2, 2x2, 3x3].
        assertThat(widgetsListRowEntry.mWidgets)
                .containsExactly(widgetItem2, widgetItem3, widgetItem1)
                .inOrder();
        assertThat(widgetsListRowEntry.mTitleSectionName).isEqualTo("T");
        assertThat(widgetsListRowEntry.mPkgItem).isEqualTo(mPackageItemInfo1);
    }

    @Test
    public void unsortedWidgets_hodgepodge_shouldSortWidgetItems() {
        // GIVEN a list of widgets in unsorted order.
        // Cat 3x3
        WidgetItem widgetItem1 = createWidgetItem(mWidget1, /* spanX= */ 3, /* spanY= */ 3);
        // Cat 1x2
        WidgetItem widgetItem2 = createWidgetItem(mWidget1, /* spanX= */ 1, /* spanY= */ 2);
        // Dog 2x2
        WidgetItem widgetItem3 = createWidgetItem(mWidget2, /* spanX= */ 2, /* spanY= */ 2);
        // Bird 2x2
        WidgetItem widgetItem4 = createWidgetItem(mWidget3, /* spanX= */ 2, /* spanY= */ 2);

        // WHEN creates a WidgetsListRowEntry with the unsorted widgets.
        WidgetsListContentEntry widgetsListRowEntry = new WidgetsListContentEntry(mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1, widgetItem2, widgetItem3, widgetItem4));

        // THEN the widgets list is first sorted by labels alphabetically. Then, for widgets with
        // same labels, they are sorted by their gird sizes in an ascending order:
        // [Bird 2x2, Cat 1x2, Cat 3x3, Dog 2x2]
        assertThat(widgetsListRowEntry.mWidgets)
                .containsExactly(widgetItem4, widgetItem2, widgetItem1, widgetItem3)
                .inOrder();
        assertThat(widgetsListRowEntry.mTitleSectionName).isEqualTo("T");
        assertThat(widgetsListRowEntry.mPkgItem).isEqualTo(mPackageItemInfo1);
    }

    @Test
    public void equals_entriesWithDifferentPackageItemInfo_returnFalse() {
        WidgetItem widgetItem1 = createWidgetItem(mWidget1, /* spanX= */ 2, /* spanY= */ 3);
        WidgetsListContentEntry widgetsListRowEntry1 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 3);
        WidgetsListContentEntry widgetsListRowEntry2 = new WidgetsListContentEntry(
                mPackageItemInfo2,
                /* titleSectionName= */ "T",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 3);

        assertThat(widgetsListRowEntry1.equals(widgetsListRowEntry2)).isFalse();
    }

    @Test
    public void equals_entriesWithDifferentTitleSectionName_returnFalse() {
        WidgetItem widgetItem1 = createWidgetItem(mWidget1, /* spanX= */ 2, /* spanY= */ 3);
        WidgetsListContentEntry widgetsListRowEntry1 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 3);
        WidgetsListContentEntry widgetsListRowEntry2 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "S",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 3);

        assertThat(widgetsListRowEntry1.equals(widgetsListRowEntry2)).isFalse();
    }

    @Test
    public void equals_entriesWithDifferentWidgetsList_returnFalse() {
        WidgetItem widgetItem1 = createWidgetItem(mWidget1, /* spanX= */ 2, /* spanY= */ 3);
        WidgetItem widgetItem2 = createWidgetItem(mWidget2, /* spanX= */ 2, /* spanY= */ 3);
        WidgetsListContentEntry widgetsListRowEntry1 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 3);
        WidgetsListContentEntry widgetsListRowEntry2 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem2),
                /* maxSpanSizeInCells= */ 3);

        assertThat(widgetsListRowEntry1.equals(widgetsListRowEntry2)).isFalse();
    }

    @Test
    public void equals_entriesWithDifferentMaxSpanSize_returnFalse() {
        WidgetItem widgetItem1 = createWidgetItem(mWidget1, /* spanX= */ 2, /* spanY= */ 3);
        WidgetsListContentEntry widgetsListRowEntry1 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 3);
        WidgetsListContentEntry widgetsListRowEntry2 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 2);

        assertThat(widgetsListRowEntry1.equals(widgetsListRowEntry2)).isFalse();
    }

    @Test
    public void equals_entriesWithSameContents_returnTrue() {
        WidgetItem widgetItem1 = createWidgetItem(mWidget1, /* spanX= */ 2, /* spanY= */ 3);
        WidgetsListContentEntry widgetsListRowEntry1 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 3);
        WidgetsListContentEntry widgetsListRowEntry2 = new WidgetsListContentEntry(
                mPackageItemInfo1,
                /* titleSectionName= */ "T",
                List.of(widgetItem1),
                /* maxSpanSizeInCells= */ 3);

        assertThat(widgetsListRowEntry1.equals(widgetsListRowEntry2)).isTrue();
    }

    private WidgetItem createWidgetItem(ComponentName componentName, int spanX, int spanY) {
        String label = mWidgetsToLabels.get(componentName);
        AppWidgetProviderInfo widgetInfo = createAppWidgetProviderInfo(componentName);

        Context context = getApplicationContext();
        LauncherAppWidgetProviderInfo launcherAppWidgetProviderInfo =
                LauncherAppWidgetProviderInfo.fromProviderInfo(context, widgetInfo);
        launcherAppWidgetProviderInfo.spanX = spanX;
        launcherAppWidgetProviderInfo.spanY = spanY;
        launcherAppWidgetProviderInfo.label = label;

        return new WidgetItem(launcherAppWidgetProviderInfo, mTestProfile, mIconCache, context);
    }
}
