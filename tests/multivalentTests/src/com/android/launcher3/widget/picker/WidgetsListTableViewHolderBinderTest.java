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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import static java.util.Collections.EMPTY_LIST;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.UserHandle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.FrameLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.ActivityContextWrapper;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.WidgetUtils;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.WidgetManagerHelper;
import com.android.launcher3.widget.model.WidgetsListContentEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsListTableViewHolderBinderTest {
    private static final String TEST_PACKAGE = "com.google.test";
    private static final String APP_NAME = "Test app";

    private Context mContext;
    private WidgetsListTableViewHolderBinder mViewHolderBinder;
    private InvariantDeviceProfile mTestProfile;

    @Mock
    private OnLongClickListener mOnLongClickListener;
    @Mock
    private OnClickListener mOnIconClickListener;
    @Mock
    private IconCache mIconCache;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = new ActivityContextWrapper(getApplicationContext());
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;

        doAnswer(invocation -> {
            ComponentWithLabel componentWithLabel = (ComponentWithLabel) invocation.getArgument(0);
            return componentWithLabel.getComponent().getShortClassName();
        }).when(mIconCache).getTitleNoCache(any());

        mViewHolderBinder = new WidgetsListTableViewHolderBinder(
                mContext,
                LayoutInflater.from(new ContextThemeWrapper(mContext,
                        com.android.launcher3.R.style.WidgetContainerTheme)),
                mOnIconClickListener,
                mOnLongClickListener);
    }

    @Test
    public void bindViewHolder_appWith3Widgets_shouldHave3Widgets() throws Exception {
        WidgetsRowViewHolder viewHolder = mViewHolderBinder.newViewHolder(
                new FrameLayout(mContext));
        WidgetsListContentEntry entry = generateSampleAppWithWidgets(
                APP_NAME,
                TEST_PACKAGE,
                /* numOfWidgets= */ 3);
        mViewHolderBinder.bindViewHolder(viewHolder, entry, /* position= */ 0, EMPTY_LIST);
        Executors.MAIN_EXECUTOR.submit(() -> { }).get();

        // THEN the table container has one row, which contains 3 widgets.
        // View:  .SampleWidget0 | .SampleWidget1 | .SampleWidget2
        assertThat(viewHolder.tableContainer.getChildCount()).isEqualTo(1);
        TableRow row = (TableRow) viewHolder.tableContainer.getChildAt(0);
        assertThat(row.getChildCount()).isEqualTo(3);
        // Widget 0 label is .SampleWidget0.
        assertWidgetCellWithLabel(row.getChildAt(0), ".SampleWidget0");
        // Widget 1 label is .SampleWidget1.
        assertWidgetCellWithLabel(row.getChildAt(1), ".SampleWidget1");
        // Widget 2 label is .SampleWidget2.
        assertWidgetCellWithLabel(row.getChildAt(2), ".SampleWidget2");
    }

    private WidgetsListContentEntry generateSampleAppWithWidgets(String appName, String packageName,
            int numOfWidgets) {
        PackageItemInfo appInfo = new PackageItemInfo(packageName, UserHandle.CURRENT);
        appInfo.title = appName;
        appInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);

        return new WidgetsListContentEntry(appInfo,
                /* titleSectionName= */ "",
                generateWidgetItems(packageName, numOfWidgets),
                Integer.MAX_VALUE);
    }

    private List<WidgetItem> generateWidgetItems(String packageName, int numOfWidgets) {
        WidgetManagerHelper widgetManager = new WidgetManagerHelper(mContext);
        ArrayList<WidgetItem> widgetItems = new ArrayList<>();
        for (int i = 0; i < numOfWidgets; i++) {
            ComponentName cn = ComponentName.createRelative(packageName, ".SampleWidget" + i);
            AppWidgetProviderInfo widgetInfo = WidgetUtils.createAppWidgetProviderInfo(cn);

            widgetItems.add(new WidgetItem(
                    LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, widgetInfo),
                    mTestProfile, mIconCache, mContext, widgetManager));
        }
        return widgetItems;
    }

    private void assertWidgetCellWithLabel(View view, String label) {
        assertThat(view).isInstanceOf(WidgetCell.class);
        TextView widgetLabel = (TextView) view.findViewById(R.id.widget_name);
        assertThat(widgetLabel.getText()).isEqualTo(label);
    }
}
