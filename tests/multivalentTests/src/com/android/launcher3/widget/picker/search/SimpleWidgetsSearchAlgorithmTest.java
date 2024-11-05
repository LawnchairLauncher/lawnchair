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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SimpleWidgetsSearchAlgorithmTest {

    @Mock private IconCache mIconCache;

    private InvariantDeviceProfile mTestProfile;
    private WidgetsListHeaderEntry mCalendarHeaderEntry;
    private WidgetsListContentEntry mCalendarContentEntry;
    private WidgetsListHeaderEntry mCameraHeaderEntry;
    private WidgetsListContentEntry mCameraContentEntry;
    private WidgetsListHeaderEntry mClockHeaderEntry;
    private WidgetsListContentEntry mClockContentEntry;
    private Context mContext;

    private SimpleWidgetsSearchAlgorithm mSimpleWidgetsSearchAlgorithm;
    @Mock
    private PopupDataProvider mDataProvider;
    @Mock
    private SearchCallback<WidgetsListBaseEntry> mSearchCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doAnswer(invocation -> {
            ComponentWithLabel componentWithLabel = (ComponentWithLabel) invocation.getArgument(0);
            return componentWithLabel.getComponent().getShortClassName();
        }).when(mIconCache).getTitleNoCache(any());
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;
        mContext = getApplicationContext();

        mCalendarHeaderEntry =
                createWidgetsHeaderEntry("com.example.android.Calendar", "Calendar", 2);
        mCalendarContentEntry =
                createWidgetsContentEntry("com.example.android.Calendar", "Calendar", 2);
        mCameraHeaderEntry = createWidgetsHeaderEntry("com.example.android.Camera", "Camera", 11);
        mCameraContentEntry = createWidgetsContentEntry("com.example.android.Camera", "Camera", 11);
        mClockHeaderEntry = createWidgetsHeaderEntry("com.example.android.Clock", "Clock", 3);
        mClockContentEntry = createWidgetsContentEntry("com.example.android.Clock", "Clock", 3);

        mSimpleWidgetsSearchAlgorithm = MAIN_EXECUTOR.submit(
                () -> new SimpleWidgetsSearchAlgorithm(mDataProvider)).get();
        doReturn(Collections.EMPTY_LIST).when(mDataProvider).getAllWidgets();
    }

    @Test
    public void filter_shouldMatchOnAppName() {
        doReturn(List.of(mCalendarHeaderEntry, mCalendarContentEntry, mCameraHeaderEntry,
                mCameraContentEntry, mClockHeaderEntry, mClockContentEntry))
                .when(mDataProvider)
                .getAllWidgets();

        assertEquals(List.of(
                WidgetsListHeaderEntry.createForSearch(
                        mCalendarHeaderEntry.mPkgItem,
                        mCalendarHeaderEntry.mTitleSectionName,
                        mCalendarHeaderEntry.mWidgets),
                mCalendarContentEntry,
                WidgetsListHeaderEntry.createForSearch(
                        mCameraHeaderEntry.mPkgItem,
                        mCameraHeaderEntry.mTitleSectionName,
                        mCameraHeaderEntry.mWidgets),
                mCameraContentEntry),
                SimpleWidgetsSearchAlgorithm.getFilteredWidgets(mDataProvider, "Ca"));
    }

    @Test
    public void filter_shouldMatchOnWidgetLabel() {
        doReturn(List.of(mCalendarHeaderEntry, mCalendarContentEntry, mCameraHeaderEntry,
                mCameraContentEntry))
                .when(mDataProvider)
                .getAllWidgets();

        assertEquals(List.of(
                WidgetsListHeaderEntry.createForSearch(
                        mCalendarHeaderEntry.mPkgItem,
                        mCalendarHeaderEntry.mTitleSectionName,
                        mCalendarHeaderEntry.mWidgets.subList(1, 2)),
                new WidgetsListContentEntry(
                        mCalendarHeaderEntry.mPkgItem,
                        mCalendarHeaderEntry.mTitleSectionName,
                        mCalendarHeaderEntry.mWidgets.subList(1, 2)),
                WidgetsListHeaderEntry.createForSearch(
                        mCameraHeaderEntry.mPkgItem,
                        mCameraHeaderEntry.mTitleSectionName,
                        mCameraHeaderEntry.mWidgets.subList(1, 3)),
                new WidgetsListContentEntry(
                        mCameraHeaderEntry.mPkgItem,
                        mCameraHeaderEntry.mTitleSectionName,
                        mCameraHeaderEntry.mWidgets.subList(1, 3))),
                SimpleWidgetsSearchAlgorithm.getFilteredWidgets(mDataProvider, "Widget1"));
    }

    @Test
    public void doSearch_shouldInformCallback() throws Exception {
        doReturn(List.of(mCalendarHeaderEntry, mCalendarContentEntry, mCameraHeaderEntry,
                mCameraContentEntry, mClockHeaderEntry, mClockContentEntry))
                .when(mDataProvider)
                .getAllWidgets();
        mSimpleWidgetsSearchAlgorithm.doSearch("Ca", mSearchCallback);
        getInstrumentation().waitForIdleSync();
        verify(mSearchCallback).onSearchResult(
                matches("Ca"), argThat(a -> a != null && !a.isEmpty()));
    }

    private WidgetsListHeaderEntry createWidgetsHeaderEntry(String packageName, String appName,
            int numOfWidgets) {
        List<WidgetItem> widgetItems = generateWidgetItems(packageName, numOfWidgets);
        PackageItemInfo pInfo = createPackageItemInfo(packageName, appName,
                widgetItems.get(0).user);

        return WidgetsListHeaderEntry.create(pInfo, /* titleSectionName= */ "", widgetItems);
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
        PackageItemInfo pInfo = new PackageItemInfo(packageName, userHandle);
        pInfo.title = appName;
        pInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);
        return pInfo;
    }

    private List<WidgetItem> generateWidgetItems(String packageName, int numOfWidgets) {
        ArrayList<WidgetItem> widgetItems = new ArrayList<>();
        for (int i = 0; i < numOfWidgets; i++) {
            ComponentName cn = ComponentName.createRelative(packageName, ".SampleWidget" + i);
            AppWidgetProviderInfo widgetInfo = createAppWidgetProviderInfo(cn);

            WidgetItem widgetItem = new WidgetItem(
                    LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, widgetInfo),
                    mTestProfile, mIconCache, mContext);
            widgetItems.add(widgetItem);
        }
        return widgetItems;
    }
}
