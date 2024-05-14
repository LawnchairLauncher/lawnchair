/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.content.pm.ApplicationInfo.CATEGORY_AUDIO;
import static android.content.pm.ApplicationInfo.CATEGORY_IMAGE;
import static android.content.pm.ApplicationInfo.CATEGORY_NEWS;
import static android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY;
import static android.content.pm.ApplicationInfo.CATEGORY_SOCIAL;
import static android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED;
import static android.content.pm.ApplicationInfo.CATEGORY_VIDEO;
import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.os.Process;

import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.util.Executors;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WidgetRecommendationCategoryProviderTest {
    private static final String TEST_PACKAGE = "com.foo.test";
    private static final String TEST_APP_NAME = "foo";
    private static final WidgetRecommendationCategory PRODUCTIVITY =
            new WidgetRecommendationCategory(
                    R.string.productivity_widget_recommendation_category_label,
                    /*order=*/0);
    private static final WidgetRecommendationCategory NEWS =
            new WidgetRecommendationCategory(
                    R.string.news_widget_recommendation_category_label, /*order=*/1);
    private static final WidgetRecommendationCategory SUGGESTED_FOR_YOU =
            new WidgetRecommendationCategory(
                    R.string.others_widget_recommendation_category_label, /*order=*/2);
    private static final WidgetRecommendationCategory SOCIAL =
            new WidgetRecommendationCategory(
                    R.string.social_widget_recommendation_category_label,
                    /*order=*/3);
    private static final WidgetRecommendationCategory ENTERTAINMENT =
            new WidgetRecommendationCategory(
                    R.string.entertainment_widget_recommendation_category_label,
                    /*order=*/4);

    private final ApplicationInfo mTestAppInfo = ApplicationInfoBuilder.newBuilder().setPackageName(
            TEST_PACKAGE).setName(TEST_APP_NAME).build();
    private Context mContext;
    @Mock
    private IconCache mIconCache;

    private WidgetItem mTestWidgetItem;
    @Mock
    private LauncherApps mLauncherApps;
    private InvariantDeviceProfile mTestProfile;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = new ContextWrapper(getInstrumentation().getTargetContext()) {
            @Override
            public Object getSystemService(String name) {
                return LAUNCHER_APPS_SERVICE.equals(name) ? mLauncherApps : super.getSystemService(
                        name);
            }
        };
        mTestAppInfo.flags = FLAG_INSTALLED;
        mTestProfile = new InvariantDeviceProfile();
        mTestProfile.numRows = 5;
        mTestProfile.numColumns = 5;
        createTestWidgetItem();
    }

    @Test
    public void getWidgetRecommendationCategory_returnsMappedCategory() throws Exception {
        ImmutableMap<Integer, WidgetRecommendationCategory> testCategories = ImmutableMap.of(
                CATEGORY_PRODUCTIVITY, PRODUCTIVITY,
                CATEGORY_NEWS, NEWS,
                CATEGORY_SOCIAL, SOCIAL,
                CATEGORY_AUDIO, ENTERTAINMENT,
                CATEGORY_IMAGE, ENTERTAINMENT,
                CATEGORY_VIDEO, ENTERTAINMENT,
                CATEGORY_UNDEFINED, SUGGESTED_FOR_YOU);

        for (Map.Entry<Integer, WidgetRecommendationCategory> testCategory :
                testCategories.entrySet()) {

            mTestAppInfo.category = testCategory.getKey();
            when(mLauncherApps.getApplicationInfo(/*packageName=*/ eq(TEST_PACKAGE),
                    /*flags=*/ eq(0),
                    /*user=*/ eq(Process.myUserHandle())))
                    .thenReturn(mTestAppInfo);

            WidgetRecommendationCategory category = Executors.MODEL_EXECUTOR.submit(() ->
                    new WidgetRecommendationCategoryProvider().getWidgetRecommendationCategory(
                            mContext,
                            mTestWidgetItem)).get();

            assertThat(category).isEqualTo(testCategory.getValue());
        }
    }

    private void createTestWidgetItem() {
        String widgetLabel = "Foo Widget";
        String widgetClassName = ".mWidget";

        doAnswer(invocation -> widgetLabel).when(mIconCache).getTitleNoCache(any());

        AppWidgetProviderInfo providerInfo = AppWidgetManager.getInstance(getApplicationContext())
                .getInstalledProvidersForPackage(
                        getInstrumentation().getContext().getPackageName(), Process.myUserHandle())
                .get(0);
        providerInfo.provider = ComponentName.createRelative(TEST_PACKAGE, widgetClassName);

        LauncherAppWidgetProviderInfo launcherAppWidgetProviderInfo =
                LauncherAppWidgetProviderInfo.fromProviderInfo(mContext, providerInfo);
        launcherAppWidgetProviderInfo.spanX = 2;
        launcherAppWidgetProviderInfo.spanY = 2;
        launcherAppWidgetProviderInfo.label = widgetLabel;
        mTestWidgetItem = new WidgetItem(launcherAppWidgetProviderInfo, mTestProfile, mIconCache,
                mContext
        );
    }
}
