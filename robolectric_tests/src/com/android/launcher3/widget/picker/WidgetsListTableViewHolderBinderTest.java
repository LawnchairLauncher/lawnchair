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

import static android.os.Looper.getMainLooper;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.robolectric.Shadows.shadowOf;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.FrameLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.testing.TestActivity;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.model.WidgetsListContentEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class WidgetsListTableViewHolderBinderTest {
    private static final String TEST_PACKAGE = "com.google.test";
    private static final String APP_NAME = "Test app";

    private Context mContext;
    private WidgetsListTableViewHolderBinder mViewHolderBinder;
    private InvariantDeviceProfile mTestProfile;
    // Replace ActivityController with ActivityScenario, which is the recommended way for activity
    // testing.
    private ActivityController<TestActivity> mActivityController;
    private TestActivity mTestActivity;

    @Mock
    private OnLongClickListener mOnLongClickListener;
    @Mock
    private OnClickListener mOnIconClickListener;
    @Mock
    private IconCache mIconCache;
    @Mock
    private WidgetPreviewLoader mWidgetPreviewLoader;
    @Mock
    private DeviceProfile mDeviceProfile;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;

        mActivityController = Robolectric.buildActivity(TestActivity.class);
        mTestActivity = mActivityController.setup().get();
        mTestActivity.setDeviceProfile(mDeviceProfile);

        doAnswer(invocation -> {
            ComponentWithLabel componentWithLabel = (ComponentWithLabel) invocation.getArgument(0);
            return componentWithLabel.getComponent().getShortClassName();
        }).when(mIconCache).getTitleNoCache(any());

        WidgetsListAdapter widgetsListAdapter = new WidgetsListAdapter(mContext,
                LayoutInflater.from(mTestActivity),
                mWidgetPreviewLoader,
                mIconCache,
                /* iconClickListener= */ view -> {},
                /* iconLongClickListener= */ view -> false);
        mViewHolderBinder = new WidgetsListTableViewHolderBinder(
                mContext,
                LayoutInflater.from(mTestActivity),
                mOnIconClickListener,
                mOnLongClickListener,
                mWidgetPreviewLoader,
                new WidgetsListDrawableFactory(mTestActivity),
                widgetsListAdapter);
    }

    @After
    public void tearDown() {
        mActivityController.destroy();
    }

    @Test
    public void bindViewHolder_appWith3Widgets_shouldHave3Widgets() {
        WidgetsRowViewHolder viewHolder = mViewHolderBinder.newViewHolder(
                new FrameLayout(mTestActivity));
        WidgetsListContentEntry entry = generateSampleAppWithWidgets(
                APP_NAME,
                TEST_PACKAGE,
                /* numOfWidgets= */ 3);
        mViewHolderBinder.bindViewHolder(viewHolder, entry, /* position= */ 0);
        shadowOf(getMainLooper()).idle();

        // THEN the table container has one row, which contains 3 widgets.
        // View:  .SampleWidget0 | .SampleWidget1 | .SampleWidget2
        assertThat(viewHolder.mTableContainer.getChildCount()).isEqualTo(1);
        TableRow row = (TableRow) viewHolder.mTableContainer.getChildAt(0);
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
        PackageItemInfo appInfo = new PackageItemInfo(packageName);
        appInfo.title = appName;
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

    private void assertWidgetCellWithLabel(View view, String label) {
        assertThat(view).isInstanceOf(WidgetCell.class);
        TextView widgetLabel = (TextView) view.findViewById(R.id.widget_name);
        assertThat(widgetLabel.getText()).isEqualTo(label);
    }
}
