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

package com.android.launcher3.widget.picker.search;

import static android.os.Looper.getMainLooper;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.robolectric.Shadows.shadowOf;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.UserHandle;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.model.WidgetsListSearchHeaderEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SimpleWidgetsSearchPipelineTest {
    @Mock private IconCache mIconCache;

    private InvariantDeviceProfile mTestProfile;
    private WidgetsListHeaderEntry mCalendarHeaderEntry;
    private WidgetsListContentEntry mCalendarContentEntry;
    private WidgetsListHeaderEntry mCameraHeaderEntry;
    private WidgetsListContentEntry mCameraContentEntry;
    private WidgetsListHeaderEntry mClockHeaderEntry;
    private WidgetsListContentEntry mClockContentEntry;
    private Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doAnswer(invocation -> {
            ComponentWithLabel componentWithLabel = (ComponentWithLabel) invocation.getArgument(0);
            return componentWithLabel.getComponent().getShortClassName();
        }).when(mIconCache).getTitleNoCache(any());
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;
        mContext = RuntimeEnvironment.application;

        mCalendarHeaderEntry =
                createWidgetsHeaderEntry("com.example.android.Calendar", "Calendar", 2);
        mCalendarContentEntry =
                createWidgetsContentEntry("com.example.android.Calendar", "Calendar", 2);
        mCameraHeaderEntry = createWidgetsHeaderEntry("com.example.android.Camera", "Camera", 11);
        mCameraContentEntry = createWidgetsContentEntry("com.example.android.Camera", "Camera", 11);
        mClockHeaderEntry = createWidgetsHeaderEntry("com.example.android.Clock", "Clock", 3);
        mClockContentEntry = createWidgetsContentEntry("com.example.android.Clock", "Clock", 3);
    }

    @Test
    public void query_shouldMatchOnAppName() {
        SimpleWidgetsSearchPipeline pipeline = new SimpleWidgetsSearchPipeline(
                List.of(mCalendarHeaderEntry, mCalendarContentEntry, mCameraHeaderEntry,
                        mCameraContentEntry, mClockHeaderEntry, mClockContentEntry));

        pipeline.query("Ca", results ->
                assertEquals(results,
                        List.of(
                                new WidgetsListSearchHeaderEntry(
                                        mCalendarHeaderEntry.mPkgItem,
                                        mCalendarHeaderEntry.mTitleSectionName,
                                        mCalendarHeaderEntry.mWidgets),
                                mCalendarContentEntry,
                                new WidgetsListSearchHeaderEntry(
                                        mCameraHeaderEntry.mPkgItem,
                                        mCameraHeaderEntry.mTitleSectionName,
                                        mCameraHeaderEntry.mWidgets),
                                mCameraContentEntry)));
        shadowOf(getMainLooper()).idle();
    }

    @Test
    public void query_shouldMatchOnWidgetLabel() {
        SimpleWidgetsSearchPipeline pipeline = new SimpleWidgetsSearchPipeline(
                List.of(mCalendarHeaderEntry, mCalendarContentEntry, mCameraHeaderEntry,
                        mCameraContentEntry));

        pipeline.query("Widget1", results ->
                assertEquals(results,
                        List.of(
                                new WidgetsListSearchHeaderEntry(
                                        mCalendarHeaderEntry.mPkgItem,
                                        mCalendarHeaderEntry.mTitleSectionName,
                                        mCalendarHeaderEntry.mWidgets.subList(1, 2)),
                                new WidgetsListContentEntry(
                                        mCalendarHeaderEntry.mPkgItem,
                                        mCalendarHeaderEntry.mTitleSectionName,
                                        mCalendarHeaderEntry.mWidgets.subList(1, 2)),
                                new WidgetsListSearchHeaderEntry(
                                        mCameraHeaderEntry.mPkgItem,
                                        mCameraHeaderEntry.mTitleSectionName,
                                        mCameraHeaderEntry.mWidgets.subList(1, 3)),
                                new WidgetsListContentEntry(
                                        mCameraHeaderEntry.mPkgItem,
                                        mCameraHeaderEntry.mTitleSectionName,
                                        mCameraHeaderEntry.mWidgets.subList(1, 3)))));
        shadowOf(getMainLooper()).idle();
    }

    private WidgetsListHeaderEntry createWidgetsHeaderEntry(String packageName, String appName,
            int numOfWidgets) {
        List<WidgetItem> widgetItems = generateWidgetItems(packageName, numOfWidgets);
        PackageItemInfo pInfo = createPackageItemInfo(packageName, appName,
                widgetItems.get(0).user);

        return new WidgetsListHeaderEntry(pInfo, /* titleSectionName= */ "", widgetItems);
    }

    private WidgetsListContentEntry createWidgetsContentEntry(String packageName, String appName,
            int numOfWidgets) {
        List<WidgetItem> widgetItems = generateWidgetItems(packageName, numOfWidgets);
        PackageItemInfo pInfo = createPackageItemInfo(packageName, appName,
                widgetItems.get(0).user);

        return new WidgetsListContentEntry(pInfo, /* titleSectionName= */ "", widgetItems);
    }

    private PackageItemInfo createPackageItemInfo(String packageName, String appName,
            UserHandle userHandle) {
        PackageItemInfo pInfo = new PackageItemInfo(packageName);
        pInfo.title = appName;
        pInfo.user = userHandle;
        pInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);
        return pInfo;
    }

    private List<WidgetItem> generateWidgetItems(String packageName, int numOfWidgets) {
        ShadowPackageManager packageManager = shadowOf(mContext.getPackageManager());
        ArrayList<WidgetItem> widgetItems = new ArrayList<>();
        for (int i = 0; i < numOfWidgets; i++) {
            ComponentName cn = ComponentName.createRelative(packageName, ".SampleWidget" + i);
            AppWidgetProviderInfo widgetInfo = new AppWidgetProviderInfo();
            widgetInfo.provider = cn;
            ReflectionHelpers.setField(widgetInfo, "providerInfo",
                    packageManager.addReceiverIfNotPresent(cn));

            WidgetItem widgetItem = new WidgetItem(
                    LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, widgetInfo),
                    mTestProfile, mIconCache);
            widgetItems.add(widgetItem);
        }
        return widgetItems;
    }
}
