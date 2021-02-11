/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
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

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class WidgetsListAdapterTest {

    private static final String TEST_PACKAGE_1 = "com.google.test.1";
    private static final String TEST_PACKAGE_2 = "com.google.test.2";

    @Mock private LayoutInflater mMockLayoutInflater;
    @Mock private WidgetPreviewLoader mMockWidgetCache;
    @Mock private RecyclerView.AdapterDataObserver mListener;
    @Mock private IconCache mIconCache;

    private WidgetsListAdapter mAdapter;
    private InvariantDeviceProfile mTestProfile;
    private Context mContext;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;
        mAdapter = new WidgetsListAdapter(mContext, mMockLayoutInflater, mMockWidgetCache,
                mIconCache, null, null);
        mAdapter.registerAdapterDataObserver(mListener);

        doAnswer(invocation -> ((ComponentWithLabel) invocation.getArgument(0))
                        .getComponent().getPackageName())
                .when(mIconCache).getTitleNoCache(any());
    }

    @Test
    public void setWidgets_shouldNotifyDataSetChanged() {
        mAdapter.setWidgets(generateSampleMap(1));

        verify(mListener).onChanged();
    }

    @Test
    public void setWidgets_withItemInserted_shouldNotifyItemInserted() {
        mAdapter.setWidgets(generateSampleMap(1));
        mAdapter.setWidgets(generateSampleMap(2));

        verify(mListener).onItemRangeInserted(eq(1), eq(1));
    }

    @Test
    public void setWidgets_withItemRemoved_shouldNotifyItemRemoved() {
        mAdapter.setWidgets(generateSampleMap(2));
        mAdapter.setWidgets(generateSampleMap(1));

        verify(mListener).onItemRangeRemoved(eq(1), eq(1));
    }

    @Test
    public void setWidgets_appIconChanged_shouldNotifyItemChanged() {
        mAdapter.setWidgets(generateSampleMap(1));
        mAdapter.setWidgets(generateSampleMap(1));

        verify(mListener).onItemRangeChanged(eq(0), eq(1), isNull());
    }

    @Test
    public void setWidgets_sameApp_moreWidgets_shouldNotifyItemChangedWithWidgetItemInfoDiff() {
        // GIVEN the adapter was first populated with test package 1 & test package 2.
        WidgetsListBaseEntry testPackage1With2WidgetsListEntry =
                generateSampleAppWithWidgets(TEST_PACKAGE_1, /* numOfWidgets= */ 2);
        WidgetsListBaseEntry testPackage2With2WidgetsListEntry =
                generateSampleAppWithWidgets(TEST_PACKAGE_2, /* numOfWidgets= */ 2);
        mAdapter.setWidgets(
                List.of(testPackage1With2WidgetsListEntry, testPackage2With2WidgetsListEntry));

        // WHEN the adapter is updated with the same list of apps but test package 2 has 3 widgets
        // now.
        WidgetsListBaseEntry testPackage1With3WidgetsListEntry =
                generateSampleAppWithWidgets(TEST_PACKAGE_2, /* numOfWidgets= */ 2);
        mAdapter.setWidgets(
                List.of(testPackage1With2WidgetsListEntry, testPackage1With3WidgetsListEntry));

        // THEN the onItemRangeChanged is invoked.
        verify(mListener).onItemRangeChanged(eq(1), eq(1), isNull());
    }

    @Test
    public void setWidgets_hodgepodge_shouldInvokeExpectedDataObserverCallbacks() {
        List<WidgetsListBaseEntry> allAppsWithWidgets = generateSampleMap(5);
        // GIVEN the current widgets list consist of [A, B, E].
        List<WidgetsListBaseEntry> currentList = List.of(
                allAppsWithWidgets.get(0), allAppsWithWidgets.get(1), allAppsWithWidgets.get(4));
        mAdapter.setWidgets(currentList);

        // WHEN the widgets list is updated to [A, C, D].
        List<WidgetsListBaseEntry> newList = List.of(
                allAppsWithWidgets.get(0), allAppsWithWidgets.get(2), allAppsWithWidgets.get(3));
        mAdapter.setWidgets(newList);

        // Computation logic                           | [Intermediate list during computation]
        // THEN B <> C < 0, removed B from index 1     | [A, E]
        verify(mListener).onItemRangeRemoved(/* positionStart= */ 1, /* itemCount= */ 1);
        // THEN E <> C > 0, C inserted to index 1      | [A, C, E]
        verify(mListener).onItemRangeInserted(/* positionStart= */ 1, /* itemCount= */ 1);
        // THEN E <> D > 0, D inserted to index 2      | [A, C, D, E]
        verify(mListener).onItemRangeInserted(/* positionStart= */ 2, /* itemCount= */ 1);
        // THEN E <> null = -1, E deleted from index 3 | [A, C, D]
        verify(mListener).onItemRangeRemoved(/* positionStart= */ 3, /* itemCount= */ 1);
    }

    /**
     * Helper method to generate the sample widget model map that can be used for the tests
     * @param num the number of WidgetItem the map should contain
     */
    private ArrayList<WidgetsListBaseEntry> generateSampleMap(int num) {
        ArrayList<WidgetsListBaseEntry> result = new ArrayList<>();
        if (num <= 0) return result;

        for (int i = 0; i < num; i++) {
            String packageName = "com.placeholder.apk" + i;

            List<WidgetItem> widgetItems = generateWidgetItems(packageName, /* numOfWidgets= */ 1);

            PackageItemInfo pInfo = new PackageItemInfo(packageName);
            pInfo.title = pInfo.packageName;
            pInfo.user = widgetItems.get(0).user;
            pInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);

            result.add(new WidgetsListContentEntry(pInfo, /* titleSectionName= */ "", widgetItems));
        }

        return result;
    }

    private WidgetsListBaseEntry generateSampleAppWithWidgets(String packageName,
            int numOfWidgets) {
        PackageItemInfo appInfo = new PackageItemInfo(packageName);
        appInfo.title = appInfo.packageName;
        appInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);

        return new WidgetsListContentEntry(appInfo,
                /* titleSectionName= */ "",
                generateWidgetItems(packageName, numOfWidgets));
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

            widgetItems.add(new WidgetItem(
                    LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, widgetInfo),
                    mTestProfile, mIconCache));
        }
        return widgetItems;
    }
}
