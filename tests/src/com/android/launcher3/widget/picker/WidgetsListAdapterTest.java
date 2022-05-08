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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Process;
import android.os.UserHandle;
import android.view.LayoutInflater;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.WidgetUtils;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for WidgetsListAdapter
 * Note that all indices matching are shifted by 1 to account for the empty space at the start.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsListAdapterTest {
    private static final String TEST_PACKAGE_PLACEHOLDER = "com.google.test";

    @Mock private LayoutInflater mMockLayoutInflater;
    @Mock private RecyclerView.AdapterDataObserver mListener;
    @Mock private IconCache mIconCache;

    private WidgetsListAdapter mAdapter;
    private InvariantDeviceProfile mTestProfile;
    private UserHandle mUserHandle;
    private Context mContext;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = new ActivityContextWrapper(getApplicationContext());
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;
        mUserHandle = Process.myUserHandle();
        mAdapter = new WidgetsListAdapter(mContext, mMockLayoutInflater,
                mIconCache, () -> 0, null, null);
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

        verify(mListener).onItemRangeInserted(eq(2), eq(1));
    }

    @Test
    public void setWidgets_withItemRemoved_shouldNotifyItemRemoved() {
        mAdapter.setWidgets(generateSampleMap(2));
        mAdapter.setWidgets(generateSampleMap(1));

        verify(mListener).onItemRangeRemoved(eq(2), eq(1));
    }

    @Test
    public void setWidgets_appIconChanged_shouldNotifyItemChanged() {
        mAdapter.setWidgets(generateSampleMap(1));
        mAdapter.setWidgets(generateSampleMap(1));

        verify(mListener).onItemRangeChanged(eq(1), eq(1), isNull());
    }

    @Test
    public void headerClick_expanded_shouldNotifyItemChange() {
        // GIVEN a list of widgets entries:
        // [com.google.test0, com.google.test0 content,
        //  com.google.test1, com.google.test1 content,
        //  com.google.test2, com.google.test2 content]
        // The visible widgets entries: [com.google.test0, com.google.test1, com.google.test2].
        mAdapter.setWidgets(generateSampleMap(3));

        // WHEN com.google.test.1 header is expanded.
        mAdapter.onHeaderClicked(/* showWidgets= */ true,
                new PackageUserKey(TEST_PACKAGE_PLACEHOLDER + 1, mUserHandle));

        // THEN the visible entries list becomes:
        // [com.google.test0, com.google.test1, com.google.test1 content, com.google.test2]
        // com.google.test.1 content is inserted into position 2.
        verify(mListener).onItemRangeInserted(eq(3), eq(1));
    }

    @Test
    public void setWidgets_expandedApp_moreWidgets_shouldNotifyItemChangedWithWidgetItemInfoDiff() {
        // GIVEN the adapter was first populated with com.google.test0 & com.google.test1. Each app
        // has one widget.
        ArrayList<WidgetsListBaseEntry> allEntries = generateSampleMap(2);
        mAdapter.setWidgets(allEntries);
        // GIVEN test com.google.test1 is expanded.
        // Visible entries in the adapter are:
        // [com.google.test0, com.google.test1, com.google.test1 content]
        mAdapter.onHeaderClicked(/* showWidgets= */ true,
                new PackageUserKey(TEST_PACKAGE_PLACEHOLDER + 1, mUserHandle));
        Mockito.reset(mListener);

        // WHEN the adapter is updated with the same list of apps but com.google.test1 has 2 widgets
        // now.
        WidgetsListContentEntry testPackage1ContentEntry =
                (WidgetsListContentEntry) allEntries.get(3);
        WidgetItem widgetItem = testPackage1ContentEntry.mWidgets.get(0);
        WidgetsListContentEntry newTestPackage1ContentEntry = new WidgetsListContentEntry(
                testPackage1ContentEntry.mPkgItem,
                testPackage1ContentEntry.mTitleSectionName, List.of(widgetItem, widgetItem));
        allEntries.set(3, newTestPackage1ContentEntry);
        mAdapter.setWidgets(allEntries);

        // THEN the onItemRangeChanged is invoked for "com.google.test1 content" at index 2.
        verify(mListener).onItemRangeChanged(eq(3), eq(1), isNull());
    }

    @Test
    public void setWidgets_hodgepodge_shouldInvokeExpectedDataObserverCallbacks() {
        // GIVEN a widgets entry list:
        // Index:  0|   1      | 2|      3   | 4|     5    | 6|     7    | 8|     9    |
        //        [A, A content, B, B content, C, C content, D, D content, E, E content]
        List<WidgetsListBaseEntry> allAppsWithWidgets = generateSampleMap(5);
        // GIVEN the current widgets list consist of [A, A content, B, B content, E, E content].
        // GIVEN the visible widgets list consist of [A, B, E]
        List<WidgetsListBaseEntry> currentList = List.of(
                // A & A content
                allAppsWithWidgets.get(0), allAppsWithWidgets.get(1),
                // B & B content
                allAppsWithWidgets.get(2), allAppsWithWidgets.get(3),
                // E & E content
                allAppsWithWidgets.get(8), allAppsWithWidgets.get(9));
        mAdapter.setWidgets(currentList);

        // WHEN the widgets list is updated to [A, A content, C, C content, D, D content].
        // WHEN the visible widgets list is updated to [A, C, D].
        List<WidgetsListBaseEntry> newList = List.of(
                // A & A content
                allAppsWithWidgets.get(0), allAppsWithWidgets.get(1),
                // C & C content
                allAppsWithWidgets.get(4), allAppsWithWidgets.get(5),
                // D & D content
                allAppsWithWidgets.get(6), allAppsWithWidgets.get(7));
        mAdapter.setWidgets(newList);

        // Account for 1st items as empty space
        // Computation logic                           | [Intermediate list during computation]
        // THEN B <> C < 0, removed B from index 1     | [A, E]
        verify(mListener).onItemRangeRemoved(/* positionStart= */ 2, /* itemCount= */ 1);
        // THEN E <> C > 0, C inserted to index 1      | [A, C, E]
        verify(mListener).onItemRangeInserted(/* positionStart= */ 2, /* itemCount= */ 1);
        // THEN E <> D > 0, D inserted to index 2      | [A, C, D, E]
        verify(mListener).onItemRangeInserted(/* positionStart= */ 3, /* itemCount= */ 1);
        // THEN E <> null = -1, E deleted from index 3 | [A, C, D]
        verify(mListener).onItemRangeRemoved(/* positionStart= */ 4, /* itemCount= */ 1);
    }

    @Test
    public void setWidgetsOnSearch_expandedApp_shouldResetExpandedApp() {
        // GIVEN a list of widgets entries:
        // [Empty item
        //  com.google.test0,
        //  com.google.test0 content,
        //  com.google.test1,
        //  com.google.test1 content,
        //  com.google.test2,
        //  com.google.test2 content]
        // The visible widgets entries:
        // [Empty item,
        // com.google.test0,
        // com.google.test1,
        // com.google.test2].
        ArrayList<WidgetsListBaseEntry> allEntries = generateSampleMap(3);
        mAdapter.setWidgetsOnSearch(allEntries);
        // GIVEN com.google.test.1 header is expanded. The visible entries list becomes:
        // [Empty item, com.google.test0, com.google.test1, com.google.test1 content,
        // com.google.test2]
        mAdapter.onHeaderClicked(/* showWidgets= */ true,
                new PackageUserKey(TEST_PACKAGE_PLACEHOLDER + 1, mUserHandle));
        Mockito.reset(mListener);

        // WHEN same widget entries are set again.
        mAdapter.setWidgetsOnSearch(allEntries);

        // THEN expanded app is reset and the visible entries list becomes:
        // [Empty item, com.google.test0, com.google.test1, com.google.test2]
        verify(mListener).onItemRangeChanged(eq(2), eq(1), isNull());
        verify(mListener).onItemRangeRemoved(/* positionStart= */ 3, /* itemCount= */ 1);
    }

    /**
     * Generates a list of sample widget entries.
     *
     * <p>Each sample app has 1 widget only. An app is represented by 2 entries,
     * {@link WidgetsListHeaderEntry} & {@link WidgetsListContentEntry}. Only
     * {@link WidgetsListHeaderEntry} is always visible in the {@link WidgetsListAdapter}.
     * {@link WidgetsListContentEntry} is only shown upon clicking the corresponding app's
     * {@link WidgetsListHeaderEntry}. Only at most one {@link WidgetsListContentEntry} is shown at
     * a time.
     *
     * @param num the number of apps that have widgets.
     */
    private ArrayList<WidgetsListBaseEntry> generateSampleMap(int num) {
        ArrayList<WidgetsListBaseEntry> result = new ArrayList<>();
        if (num <= 0) return result;

        for (int i = 0; i < num; i++) {
            String packageName = TEST_PACKAGE_PLACEHOLDER + i;

            List<WidgetItem> widgetItems = generateWidgetItems(packageName, /* numOfWidgets= */ 1);

            PackageItemInfo pInfo = new PackageItemInfo(packageName, widgetItems.get(0).user);
            pInfo.title = pInfo.packageName;
            pInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);

            result.add(new WidgetsListHeaderEntry(pInfo, /* titleSectionName= */ "", widgetItems));
            result.add(new WidgetsListContentEntry(pInfo, /* titleSectionName= */ "", widgetItems));
        }

        return result;
    }

    private List<WidgetItem> generateWidgetItems(String packageName, int numOfWidgets) {
        ArrayList<WidgetItem> widgetItems = new ArrayList<>();
        for (int i = 0; i < numOfWidgets; i++) {
            ComponentName cn = ComponentName.createRelative(packageName, ".SampleWidget" + i);
            AppWidgetProviderInfo widgetInfo = WidgetUtils.createAppWidgetProviderInfo(cn);

            widgetItems.add(new WidgetItem(
                    LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, widgetInfo),
                    mTestProfile, mIconCache));
        }
        return widgetItems;
    }
}
