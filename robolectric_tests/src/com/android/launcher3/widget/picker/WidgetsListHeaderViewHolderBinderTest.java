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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.testing.TestActivity;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.DatabaseWidgetPreviewLoader;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

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
public final class WidgetsListHeaderViewHolderBinderTest {
    private static final String TEST_PACKAGE = "com.google.test";
    private static final String APP_NAME = "Test app";

    private Context mContext;
    private WidgetsListHeaderViewHolderBinder mViewHolderBinder;
    private InvariantDeviceProfile mTestProfile;
    // Replace ActivityController with ActivityScenario, which is the recommended way for activity
    // testing.
    private ActivityController<TestActivity> mActivityController;
    private TestActivity mTestActivity;

    @Mock
    private IconCache mIconCache;
    @Mock
    private DeviceProfile mDeviceProfile;
    @Mock
    private DatabaseWidgetPreviewLoader mWidgetPreviewLoader;
    @Mock
    private OnHeaderClickListener mOnHeaderClickListener;

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
        mViewHolderBinder = new WidgetsListHeaderViewHolderBinder(
                LayoutInflater.from(mTestActivity),
                mOnHeaderClickListener,
                new WidgetsListDrawableFactory(mTestActivity),
                widgetsListAdapter);
    }

    @After
    public void tearDown() {
        mActivityController.destroy();
    }

    @Test
    public void bindViewHolder_appWith3Widgets_shouldShowTheCorrectAppNameAndSubtitle() {
        WidgetsListHeaderHolder viewHolder = mViewHolderBinder.newViewHolder(
                new FrameLayout(mTestActivity));
        WidgetsListHeader widgetsListHeader = viewHolder.mWidgetsListHeader;
        WidgetsListHeaderEntry entry = generateSampleAppHeader(
                APP_NAME,
                TEST_PACKAGE,
                /* numOfWidgets= */ 3);
        mViewHolderBinder.bindViewHolder(viewHolder, entry, /* position= */ 0);

        TextView appTitle = widgetsListHeader.findViewById(R.id.app_title);
        TextView appSubtitle = widgetsListHeader.findViewById(R.id.app_subtitle);
        assertThat(appTitle.getText()).isEqualTo(APP_NAME);
        assertThat(appSubtitle.getText()).isEqualTo("3 widgets");
    }

    @Test
    public void bindViewHolder_shouldAttachOnHeaderClickListener() {
        WidgetsListHeaderHolder viewHolder = mViewHolderBinder.newViewHolder(
                new FrameLayout(mTestActivity));
        WidgetsListHeader widgetsListHeader = viewHolder.mWidgetsListHeader;
        WidgetsListHeaderEntry entry = generateSampleAppHeader(
                APP_NAME,
                TEST_PACKAGE,
                /* numOfWidgets= */ 3);

        mViewHolderBinder.bindViewHolder(viewHolder, entry, /* position= */ 0);
        widgetsListHeader.callOnClick();

        verify(mOnHeaderClickListener).onHeaderClicked(eq(true),
                eq(new PackageUserKey(entry.mPkgItem.packageName, entry.mPkgItem.user)));
    }

    private WidgetsListHeaderEntry generateSampleAppHeader(String appName, String packageName,
            int numOfWidgets) {
        PackageItemInfo appInfo = new PackageItemInfo(packageName);
        appInfo.title = appName;
        appInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);

        return new WidgetsListHeaderEntry(appInfo,
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
