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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import static java.util.Collections.EMPTY_LIST;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.UserHandle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
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
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.WidgetUtils;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.model.WidgetsListHeaderEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsListHeaderViewHolderBinderTest {
    private static final String TEST_PACKAGE = "com.google.test";
    private static final String APP_NAME = "Test app";

    private Context mContext;
    private WidgetsListHeaderViewHolderBinder mViewHolderBinder;
    private InvariantDeviceProfile mTestProfile;

    @Mock
    private IconCache mIconCache;
    @Mock
    private OnHeaderClickListener mOnHeaderClickListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = new ActivityContextWrapper(new ContextThemeWrapper(getApplicationContext(),
                R.style.WidgetContainerTheme));
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;

        doAnswer(invocation -> {
            ComponentWithLabel componentWithLabel = (ComponentWithLabel) invocation.getArgument(0);
            return componentWithLabel.getComponent().getShortClassName();
        }).when(mIconCache).getTitleNoCache(any());
        mViewHolderBinder = new WidgetsListHeaderViewHolderBinder(
                LayoutInflater.from(mContext),
                mOnHeaderClickListener,
                false);
    }

    @Test
    public void bindViewHolder_appWith3Widgets_shouldShowTheCorrectAppNameAndSubtitle() {
        WidgetsListHeaderHolder viewHolder = mViewHolderBinder.newViewHolder(
                new FrameLayout(mContext));
        WidgetsListHeader widgetsListHeader = viewHolder.mWidgetsListHeader;
        WidgetsListHeaderEntry entry = generateSampleAppHeader(
                APP_NAME,
                TEST_PACKAGE,
                /* numOfWidgets= */ 3);
        mViewHolderBinder.bindViewHolder(viewHolder, entry, /* position= */ 0, EMPTY_LIST);

        TextView appTitle = widgetsListHeader.findViewById(R.id.app_title);
        TextView appSubtitle = widgetsListHeader.findViewById(R.id.app_subtitle);
        assertThat(appTitle.getText()).isEqualTo(APP_NAME);
        assertThat(appSubtitle.getText()).isEqualTo("3 widgets");
    }

    @Test
    public void bindViewHolder_shouldAttachOnHeaderClickListener() {
        WidgetsListHeaderHolder viewHolder = mViewHolderBinder.newViewHolder(
                new FrameLayout(mContext));
        WidgetsListHeader widgetsListHeader = viewHolder.mWidgetsListHeader;
        WidgetsListHeaderEntry entry = generateSampleAppHeader(
                APP_NAME,
                TEST_PACKAGE,
                /* numOfWidgets= */ 3);

        mViewHolderBinder.bindViewHolder(viewHolder, entry, /* position= */ 0, EMPTY_LIST);
        widgetsListHeader.callOnClick();

        verify(mOnHeaderClickListener).onHeaderClicked(eq(true),
                eq(PackageUserKey.fromPackageItemInfo(entry.mPkgItem)));
    }

    private WidgetsListHeaderEntry generateSampleAppHeader(String appName, String packageName,
            int numOfWidgets) {
        PackageItemInfo appInfo = new PackageItemInfo(packageName, UserHandle.CURRENT);
        appInfo.title = appName;
        appInfo.bitmap = BitmapInfo.of(Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8), 0);

        return WidgetsListHeaderEntry.create(appInfo,
                /* titleSectionName= */ "",
                generateWidgetItems(packageName, numOfWidgets));
    }

    private List<WidgetItem> generateWidgetItems(String packageName, int numOfWidgets) {
        ArrayList<WidgetItem> widgetItems = new ArrayList<>();
        for (int i = 0; i < numOfWidgets; i++) {
            ComponentName cn = ComponentName.createRelative(packageName, ".SampleWidget" + i);
            AppWidgetProviderInfo widgetInfo = WidgetUtils.createAppWidgetProviderInfo(cn);

            widgetItems.add(new WidgetItem(
                    LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, widgetInfo),
                    mTestProfile, mIconCache, mContext));
        }
        return widgetItems;
    }
}
