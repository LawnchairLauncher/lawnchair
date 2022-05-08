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
package com.android.launcher3.widget.picker;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.UserHandle;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.model.WidgetsListContentEntry;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;
import com.android.launcher3.widget.picker.WidgetsListAdapter.WidgetListBaseRowEntryComparator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsDiffReporterTest {
    private static final String TEST_PACKAGE_PREFIX = "com.android.test";
    private static final WidgetListBaseRowEntryComparator COMPARATOR =
            new WidgetListBaseRowEntryComparator();

    @Mock private IconCache mIconCache;
    @Mock private RecyclerView.Adapter mAdapter;

    private InvariantDeviceProfile mTestProfile;
    private WidgetsDiffReporter mWidgetsDiffReporter;
    private Context mContext;
    private WidgetsListHeaderEntry mHeaderA;
    private WidgetsListHeaderEntry mHeaderB;
    private WidgetsListHeaderEntry mHeaderC;
    private WidgetsListHeaderEntry mHeaderD;
    private WidgetsListHeaderEntry mHeaderE;
    private WidgetsListContentEntry mContentC;
    private WidgetsListContentEntry mContentE;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;

        doAnswer(invocation -> ((ComponentWithLabel) invocation.getArgument(0))
                .getComponent().getPackageName())
                .when(mIconCache).getTitleNoCache(any());

        mContext = getApplicationContext();
        mWidgetsDiffReporter = new WidgetsDiffReporter(mIconCache, mAdapter);
        mHeaderA = createWidgetsHeaderEntry(TEST_PACKAGE_PREFIX + "A",
                /* appName= */ "A", /* numOfWidgets= */ 3);
        mHeaderB = createWidgetsHeaderEntry(TEST_PACKAGE_PREFIX + "B",
                /* appName= */ "B", /* numOfWidgets= */ 3);
        mHeaderC = createWidgetsHeaderEntry(TEST_PACKAGE_PREFIX + "C",
                /* appName= */ "C", /* numOfWidgets= */ 3);
        mContentC = createWidgetsContentEntry(TEST_PACKAGE_PREFIX + "C",
                /* appName= */ "C", /* numOfWidgets= */ 3);
        mHeaderD = createWidgetsHeaderEntry(TEST_PACKAGE_PREFIX + "D",
                /* appName= */ "D", /* numOfWidgets= */ 3);
        mHeaderE = createWidgetsHeaderEntry(TEST_PACKAGE_PREFIX + "E",
                /* appName= */ "E", /* numOfWidgets= */ 3);
        mContentE = createWidgetsContentEntry(TEST_PACKAGE_PREFIX + "E",
                /* appName= */ "E", /* numOfWidgets= */ 3);
    }

    @Test
    public void listNotChanged_shouldNotInvokeAnyCallbacks() {
        // GIVEN the current list has app headers [A, B, C].
        ArrayList<WidgetsListBaseEntry> currentList = new ArrayList<>(
                List.of(mHeaderA, mHeaderB, mHeaderC));

        // WHEN computing the list difference.
        mWidgetsDiffReporter.process(currentList, currentList, COMPARATOR);

        // THEN there is no adaptor callback.
        verifyZeroInteractions(mAdapter);
        // THEN the current list contains the same entries.
        assertThat(currentList).containsExactly(mHeaderA, mHeaderB, mHeaderC);
    }

    @Test
    public void headersOnly_emptyListToNonEmpty_shouldInvokeNotifyDataSetChanged() {
        // GIVEN the current list has app headers [A, B, C].
        ArrayList<WidgetsListBaseEntry> currentList = new ArrayList<>();

        List<WidgetsListBaseEntry> newList = List.of(
                createWidgetsHeaderEntry(TEST_PACKAGE_PREFIX + "A", "A", 3),
                createWidgetsHeaderEntry(TEST_PACKAGE_PREFIX + "B", "B", 3),
                createWidgetsHeaderEntry(TEST_PACKAGE_PREFIX + "C", "C", 3));

        // WHEN computing the list difference.
        mWidgetsDiffReporter.process(currentList, newList, COMPARATOR);

        // THEN notifyDataSetChanged is called
        verify(mAdapter).notifyDataSetChanged();
        // THEN the current list contains all elements from the new list.
        assertThat(currentList).containsExactlyElementsIn(newList);
    }

    @Test
    public void headersOnly_nonEmptyToEmptyList_shouldInvokeNotifyDataSetChanged() {
        // GIVEN the current list has app headers [A, B, C].
        ArrayList<WidgetsListBaseEntry> currentList = new ArrayList<>(
                List.of(mHeaderA, mHeaderB, mHeaderC));
        // GIVEN the new list is empty.
        List<WidgetsListBaseEntry> newList = List.of();

        // WHEN computing the list difference.
        mWidgetsDiffReporter.process(currentList, newList, COMPARATOR);

        // THEN notifyDataSetChanged is called.
        verify(mAdapter).notifyDataSetChanged();
        // THEN the current list isEmpty.
        assertThat(currentList).isEmpty();
    }

    @Test
    public void headersOnly_itemAddedAndRemovedInTheNewList_shouldInvokeCorrectCallbacks() {
        // GIVEN the current list has app headers [A, B, D].
        ArrayList<WidgetsListBaseEntry> currentList = new ArrayList<>(
                List.of(mHeaderA, mHeaderB, mHeaderD));
        // GIVEN the new list has app headers [A, C, E].
        List<WidgetsListBaseEntry> newList = List.of(mHeaderA, mHeaderC, mHeaderE);

        // WHEN computing the list difference.
        mWidgetsDiffReporter.process(currentList, newList, COMPARATOR);

        // THEN "B" is removed from position 1.
        verify(mAdapter).notifyItemRemoved(/* position= */ 1);
        // THEN "D" is removed from position 2.
        verify(mAdapter).notifyItemRemoved(/* position= */ 2);
        // THEN "C" is inserted at position 1.
        verify(mAdapter).notifyItemInserted(/* position= */ 1);
        // THEN "E" is inserted at position 2.
        verify(mAdapter).notifyItemInserted(/* position= */ 2);
        // THEN the current list contains all elements from the new list.
        assertThat(currentList).containsExactlyElementsIn(newList);
    }

    @Test
    public void headersContentsMix_itemAddedAndRemovedInTheNewList_shouldInvokeCorrectCallbacks() {
        // GIVEN the current list has app headers [A, B, E content].
        ArrayList<WidgetsListBaseEntry> currentList = new ArrayList<>(
                List.of(mHeaderA, mHeaderB, mContentE));
        // GIVEN the new list has app headers [A, C content, D].
        List<WidgetsListBaseEntry> newList = List.of(mHeaderA, mContentC, mHeaderD);

        // WHEN computing the list difference.
        mWidgetsDiffReporter.process(currentList, newList, COMPARATOR);

        // THEN "B" is removed from position 1.
        verify(mAdapter).notifyItemRemoved(/* position= */ 1);
        // THEN "C content" is inserted at position 1.
        verify(mAdapter).notifyItemInserted(/* position= */ 1);
        // THEN "D" is inserted at position 2.
        verify(mAdapter).notifyItemInserted(/* position= */ 2);
        // THEN "E content" is removed from position 3.
        verify(mAdapter).notifyItemRemoved(/* position= */ 3);
        // THEN the current list contains all elements from the new list.
        assertThat(currentList).containsExactlyElementsIn(newList);
    }

    @Test
    public void headersContentsMix_userInteractWithHeader_shouldInvokeCorrectCallbacks() {
        // GIVEN the current list has app headers [A, B, E content].
        ArrayList<WidgetsListBaseEntry> currentList = new ArrayList<>(
                List.of(mHeaderA, mHeaderB, mContentE));
        // GIVEN the new list has app headers [A, B, E content] and the user has interacted with B.
        List<WidgetsListBaseEntry> newList =
                List.of(mHeaderA, mHeaderB.withWidgetListShown(), mContentE);

        // WHEN computing the list difference.
        mWidgetsDiffReporter.process(currentList, newList, COMPARATOR);

        // THEN notify "B" has been changed.
        verify(mAdapter).notifyItemChanged(/* position= */ 1);
        // THEN the current list contains all elements from the new list.
        assertThat(currentList).containsExactlyElementsIn(newList);
    }

    @Test
    public void headersContentsMix_headerWidgetsModified_shouldInvokeCorrectCallbacks() {
        // GIVEN the current list has app headers [A, B, E content].
        ArrayList<WidgetsListBaseEntry> currentList = new ArrayList<>(
                List.of(mHeaderA, mHeaderB, mContentE));
        // GIVEN the new list has one of the headers widgets list modified.
        List<WidgetsListBaseEntry> newList = List.of(
                new WidgetsListHeaderEntry(
                        mHeaderA.mPkgItem, mHeaderA.mTitleSectionName,
                        mHeaderA.mWidgets.subList(0, 1)),
                mHeaderB, mContentE);

        // WHEN computing the list difference.
        mWidgetsDiffReporter.process(currentList, newList, COMPARATOR);

        // THEN notify "A" has been changed.
        verify(mAdapter).notifyItemChanged(/* position= */ 0);
        // THEN the current list contains all elements from the new list.
        assertThat(currentList).containsExactlyElementsIn(newList);
    }

    @Test
    public void headersContentsMix_contentMaxSpanSizeModified_shouldInvokeCorrectCallbacks() {
        // GIVEN the current list has app headers [A, B, E content].
        ArrayList<WidgetsListBaseEntry> currentList = new ArrayList<>(
                List.of(mHeaderA, mHeaderB, mContentE));
        // GIVEN the new list has max span size in "E content" modified.
        List<WidgetsListBaseEntry> newList = List.of(
                mHeaderA,
                mHeaderB,
                new WidgetsListContentEntry(
                        mContentE.mPkgItem,
                        mContentE.mTitleSectionName,
                        mContentE.mWidgets,
                        mContentE.getMaxSpanSizeInCells() + 1));

        // WHEN computing the list difference.
        mWidgetsDiffReporter.process(currentList, newList, COMPARATOR);

        // THEN notify "E content" has been changed.
        verify(mAdapter).notifyItemChanged(/* position= */ 2);
        // THEN the current list contains all elements from the new list.
        assertThat(currentList).containsExactlyElementsIn(newList);
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
                    mTestProfile, mIconCache);
            widgetItems.add(widgetItem);
        }
        return widgetItems;
    }
}
