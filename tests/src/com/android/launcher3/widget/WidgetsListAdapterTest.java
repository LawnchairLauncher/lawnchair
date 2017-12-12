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
package com.android.launcher3.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.LayoutInflater;

import com.android.launcher3.IconCache;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.util.MultiHashMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WidgetsListAdapterTest {

    private final String TAG = "WidgetsListAdapterTest";

    @Mock private LayoutInflater mMockLayoutInflater;
    @Mock private WidgetPreviewLoader mMockWidgetCache;
    @Mock private WidgetsDiffReporter.NotifyListener mListener;
    @Mock private IconCache mIconCache;

    private WidgetsListAdapter mAdapter;
    private AlphabeticIndexCompat mIndexCompat;
    private InvariantDeviceProfile mTestProfile;
    private Context mContext;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getTargetContext();
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;
        mIndexCompat = new AlphabeticIndexCompat(mContext);
        WidgetsDiffReporter reporter = new WidgetsDiffReporter(mIconCache);
        reporter.setListener(mListener);
        mAdapter = new WidgetsListAdapter(mContext, mMockLayoutInflater, mMockWidgetCache,
                mIndexCompat, null, null, reporter);
    }

    @Test
    public void test_notifyDataSetChanged() throws Exception {
        mAdapter.setWidgets(generateSampleMap(1));
        verify(mListener, times(1)).notifyDataSetChanged();
    }

    @Test
    public void test_notifyItemInserted() throws Exception {
        mAdapter.setWidgets(generateSampleMap(1));
        mAdapter.setWidgets(generateSampleMap(2));
        verify(mListener, times(1)).notifyDataSetChanged();
        verify(mListener, times(1)).notifyItemInserted(1);
    }

    @Test
    public void test_notifyItemRemoved() throws Exception {
        mAdapter.setWidgets(generateSampleMap(2));
        mAdapter.setWidgets(generateSampleMap(1));
        verify(mListener, times(1)).notifyDataSetChanged();
        verify(mListener, times(1)).notifyItemRemoved(1);
    }

    @Test
    public void testNotifyItemChanged_PackageIconDiff() throws Exception {
        mAdapter.setWidgets(generateSampleMap(1));
        mAdapter.setWidgets(generateSampleMap(1));
        verify(mListener, times(1)).notifyDataSetChanged();
        verify(mListener, times(1)).notifyItemChanged(0);
    }

    @Test
    public void testNotifyItemChanged_widgetItemInfoDiff() throws Exception {
        // TODO: same package name but item number changed
    }

    @Test
    public void testNotifyItemInsertedRemoved_hodgepodge() throws Exception {
        // TODO: insert and remove combined.          curMap
        // newMap [A, C, D]                           [A, B, E]
        // B - C < 0, removed B from index 1          [A, E]
        // E - C > 0, C inserted to index 1           [A, C, E]
        // E - D > 0, D inserted to index 2           [A, C, D, E]
        // E - null = -1, E deleted from index 3      [A, C, D]
    }

    /**
     * Helper method to generate the sample widget model map that can be used for the tests
     * @param num the number of WidgetItem the map should contain
     * @return
     */
    private MultiHashMap<PackageItemInfo, WidgetItem> generateSampleMap(int num) {
        MultiHashMap<PackageItemInfo, WidgetItem> newMap = new MultiHashMap();
        if (num <= 0) return newMap;

        PackageManager pm = mContext.getPackageManager();
        AppWidgetManagerCompat widgetManager = AppWidgetManagerCompat.getInstance(mContext);
        for (AppWidgetProviderInfo widgetInfo : widgetManager.getAllProviders(null)) {
            WidgetItem wi = new WidgetItem(LauncherAppWidgetProviderInfo
                    .fromProviderInfo(mContext, widgetInfo), pm, mTestProfile);

            PackageItemInfo pInfo = new PackageItemInfo(wi.componentName.getPackageName());
            pInfo.title = pInfo.packageName;
            pInfo.user = wi.user;
            pInfo.iconBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8);
            newMap.addToList(pInfo, wi);
            if (newMap.size() == num) {
                break;
            }
        }
        return newMap;
    }
}
