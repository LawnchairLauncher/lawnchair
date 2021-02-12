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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.robolectric.Shadows.shadowOf;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.model.WidgetsListContentEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public final class WidgetsListContentEntryTest {
    private static final String PACKAGE_NAME = "com.google.test";
    private static final PackageItemInfo PACKAGE_ITEM_INFO = new PackageItemInfo(PACKAGE_NAME);
    private static final ComponentName WIDGET_1 = ComponentName.createRelative(PACKAGE_NAME,
            ".widget1");
    private static final ComponentName WIDGET_2 = ComponentName.createRelative(PACKAGE_NAME,
            ".widget2");
    private static final ComponentName WIDGET_3 = ComponentName.createRelative(PACKAGE_NAME,
            ".widget3");
    private static final Map<ComponentName, String> WIDGETS_TO_LABELS = new HashMap();

    static {
        WIDGETS_TO_LABELS.put(WIDGET_1, "Cat");
        WIDGETS_TO_LABELS.put(WIDGET_2, "Dog");
        WIDGETS_TO_LABELS.put(WIDGET_3, "Bird");
    }

    @Mock private IconCache mIconCache;

    private Context mContext;
    private InvariantDeviceProfile mTestProfile;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;

        doAnswer(invocation -> {
            ComponentWithLabel componentWithLabel = (ComponentWithLabel) invocation.getArgument(0);
            return WIDGETS_TO_LABELS.get(componentWithLabel.getComponent());
        }).when(mIconCache).getTitleNoCache(any());
    }

    @Test
    public void unsortedWidgets_diffLabels_shouldSortWidgetItems() {
        // GIVEN a list of widgets in unsorted order.
        // Cat 2x3
        WidgetItem widgetItem1 = createWidgetItem(WIDGET_1, /* spanX= */ 2, /* spanY= */ 3);
        // Dog 2x3
        WidgetItem widgetItem2 = createWidgetItem(WIDGET_2, /* spanX= */ 2, /* spanY= */ 3);
        // Bird 2x3
        WidgetItem widgetItem3 = createWidgetItem(WIDGET_3, /* spanX= */ 2, /* spanY= */ 3);

        // WHEN creates a WidgetsListRowEntry with the unsorted widgets.
        WidgetsListContentEntry widgetsListRowEntry = new WidgetsListContentEntry(PACKAGE_ITEM_INFO,
                /* titleSectionName= */ "T",
                List.of(widgetItem1, widgetItem2, widgetItem3));

        // THEN the widgets list is sorted by their labels alphabetically: [Bird, Cat, Dog].
        assertThat(widgetsListRowEntry.mWidgets)
                .containsExactly(widgetItem3, widgetItem1, widgetItem2)
                .inOrder();
        assertThat(widgetsListRowEntry.mTitleSectionName).isEqualTo("T");
        assertThat(widgetsListRowEntry.mPkgItem).isEqualTo(PACKAGE_ITEM_INFO);
    }

    @Test
    public void unsortedWidgets_sameLabels_differentSize_shouldSortWidgetItems() {
        // GIVEN a list of widgets in unsorted order.
        // Cat 3x3
        WidgetItem widgetItem1 = createWidgetItem(WIDGET_1, /* spanX= */ 3, /* spanY= */ 3);
        // Cat 1x2
        WidgetItem widgetItem2 = createWidgetItem(WIDGET_1, /* spanX= */ 1, /* spanY= */ 2);
        // Cat 2x2
        WidgetItem widgetItem3 = createWidgetItem(WIDGET_1, /* spanX= */ 2, /* spanY= */ 2);

        // WHEN creates a WidgetsListRowEntry with the unsorted widgets.
        WidgetsListContentEntry widgetsListRowEntry = new WidgetsListContentEntry(PACKAGE_ITEM_INFO,
                /* titleSectionName= */ "T",
                List.of(widgetItem1, widgetItem2, widgetItem3));

        // THEN the widgets list is sorted by their gird sizes in an ascending order:
        // [1x2, 2x2, 3x3].
        assertThat(widgetsListRowEntry.mWidgets)
                .containsExactly(widgetItem2, widgetItem3, widgetItem1)
                .inOrder();
        assertThat(widgetsListRowEntry.mTitleSectionName).isEqualTo("T");
        assertThat(widgetsListRowEntry.mPkgItem).isEqualTo(PACKAGE_ITEM_INFO);
    }

    @Test
    public void unsortedWidgets_hodgepodge_shouldSortWidgetItems() {
        // GIVEN a list of widgets in unsorted order.
        // Cat 3x3
        WidgetItem widgetItem1 = createWidgetItem(WIDGET_1, /* spanX= */ 3, /* spanY= */ 3);
        // Cat 1x2
        WidgetItem widgetItem2 = createWidgetItem(WIDGET_1, /* spanX= */ 1, /* spanY= */ 2);
        // Dog 2x2
        WidgetItem widgetItem3 = createWidgetItem(WIDGET_2, /* spanX= */ 2, /* spanY= */ 2);
        // Bird 2x2
        WidgetItem widgetItem4 = createWidgetItem(WIDGET_3, /* spanX= */ 2, /* spanY= */ 2);

        // WHEN creates a WidgetsListRowEntry with the unsorted widgets.
        WidgetsListContentEntry widgetsListRowEntry = new WidgetsListContentEntry(PACKAGE_ITEM_INFO,
                /* titleSectionName= */ "T",
                List.of(widgetItem1, widgetItem2, widgetItem3, widgetItem4));

        // THEN the widgets list is first sorted by labels alphabetically. Then, for widgets with
        // same labels, they are sorted by their gird sizes in an ascending order:
        // [Bird 2x2, Cat 1x2, Cat 3x3, Dog 2x2]
        assertThat(widgetsListRowEntry.mWidgets)
                .containsExactly(widgetItem4, widgetItem2, widgetItem1, widgetItem3)
                .inOrder();
        assertThat(widgetsListRowEntry.mTitleSectionName).isEqualTo("T");
        assertThat(widgetsListRowEntry.mPkgItem).isEqualTo(PACKAGE_ITEM_INFO);
    }

    private WidgetItem createWidgetItem(ComponentName componentName, int spanX, int spanY) {
        String label = WIDGETS_TO_LABELS.get(componentName);
        ShadowPackageManager packageManager = shadowOf(mContext.getPackageManager());
        AppWidgetProviderInfo widgetInfo = new AppWidgetProviderInfo();
        widgetInfo.provider = componentName;
        ReflectionHelpers.setField(widgetInfo, "providerInfo",
                packageManager.addReceiverIfNotPresent(componentName));

        LauncherAppWidgetProviderInfo launcherAppWidgetProviderInfo =
                LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, widgetInfo);
        launcherAppWidgetProviderInfo.spanX = spanX;
        launcherAppWidgetProviderInfo.spanY = spanY;
        launcherAppWidgetProviderInfo.label = label;

        return new WidgetItem(launcherAppWidgetProviderInfo, mTestProfile, mIconCache);
    }
}
