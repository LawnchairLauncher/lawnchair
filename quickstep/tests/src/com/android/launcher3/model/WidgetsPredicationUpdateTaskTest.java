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
package com.android.launcher3.model;

import static android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY;
import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.os.Process.myUserHandle;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.TestUtil.runOnExecutorSync;
import static com.android.launcher3.util.WidgetUtils.createAppWidgetProviderInfo;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetId;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.TextUtils;

import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.Flags;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.QuickstepModelDelegate.PredictorState;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsPredicationUpdateTaskTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private AppWidgetProviderInfo mApp1Provider1;
    private AppWidgetProviderInfo mApp1Provider2;
    private AppWidgetProviderInfo mApp2Provider1;
    private AppWidgetProviderInfo mApp4Provider1;
    private AppWidgetProviderInfo mApp4Provider2;
    private AppWidgetProviderInfo mApp5Provider1;
    private List<AppWidgetProviderInfo> allWidgets;

    private FakeBgDataModelCallback mCallback = new FakeBgDataModelCallback();
    private LauncherModelHelper mModelHelper;
    private UserHandle mUserHandle;
    private LauncherApps mLauncherApps;


    @Before
    public void setup() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_CATEGORIZED_WIDGET_SUGGESTIONS);
        mModelHelper = new LauncherModelHelper();

        mUserHandle = myUserHandle();
        mApp1Provider1 = createAppWidgetProviderInfo(
                ComponentName.createRelative("app1", "provider1"));
        mApp1Provider2 = createAppWidgetProviderInfo(
                ComponentName.createRelative("app1", "provider2"));
        mApp2Provider1 = createAppWidgetProviderInfo(
                ComponentName.createRelative("app2", "provider1"));
        mApp4Provider1 = createAppWidgetProviderInfo(
                ComponentName.createRelative("app4", "provider1"));
        mApp4Provider2 = createAppWidgetProviderInfo(
                ComponentName.createRelative("app4", ".provider2"));
        mApp5Provider1 = createAppWidgetProviderInfo(
                ComponentName.createRelative("app5", "provider1"));
        allWidgets = Arrays.asList(mApp1Provider1, mApp1Provider2, mApp2Provider1,
                mApp4Provider1, mApp4Provider2, mApp5Provider1);

        mLauncherApps = mModelHelper.sandboxContext.spyService(LauncherApps.class);
        doAnswer(i -> {
            String pkg = i.getArgument(0);
            ApplicationInfo applicationInfo = ApplicationInfoBuilder.newBuilder()
                    .setPackageName(pkg)
                    .setName("App " + pkg)
                    .build();
            applicationInfo.category = CATEGORY_PRODUCTIVITY;
            applicationInfo.flags = FLAG_INSTALLED;
            return applicationInfo;
        }).when(mLauncherApps).getApplicationInfo(anyString(), anyInt(), any());

        AppWidgetManager manager = mModelHelper.sandboxContext.spyService(AppWidgetManager.class);
        doReturn(allWidgets).when(manager).getInstalledProviders();
        doReturn(allWidgets).when(manager).getInstalledProvidersForProfile(eq(myUserHandle()));
        doAnswer(i -> {
            String pkg = i.getArgument(0);
            return TextUtils.isEmpty(pkg) ? allWidgets : allWidgets.stream()
                    .filter(a -> pkg.equals(a.provider.getPackageName()))
                    .collect(Collectors.toList());
        }).when(manager).getInstalledProvidersForPackage(any(), eq(myUserHandle()));

        LauncherLayoutBuilder builder = new LauncherLayoutBuilder()
                .atWorkspace(0, 1, 2).putWidget("app4", "provider1", 1, 1)
                .atWorkspace(0, 1, 3).putWidget("app5", "provider1", 1, 1);
        mModelHelper.setupDefaultLayoutProvider(builder);
        MAIN_EXECUTOR.submit(() -> mModelHelper.getModel().addCallbacks(mCallback)).get();
        mModelHelper.loadModelSync();
    }

    @After
    public void tearDown() {
        mModelHelper.destroy();
    }

    @Test
    public void widgetsRecommendationRan_shouldOnlyReturnNotAddedWidgetsInAppPredictionOrder() {
        // Run on model executor so that no other task runs in the middle.
        runOnExecutorSync(MODEL_EXECUTOR, () -> {
            // WHEN newPredicationTask is executed with app predication of 5 apps.
            AppTarget app1 = new AppTarget(new AppTargetId("app1"), "app1", "provider1",
                    mUserHandle);
            AppTarget app2 = new AppTarget(new AppTargetId("app2"), "app2", "provider1",
                    mUserHandle);
            AppTarget app3 = new AppTarget(new AppTargetId("app3"), "app3", "className",
                    mUserHandle);
            AppTarget app4 = new AppTarget(new AppTargetId("app4"), "app4", "provider1",
                    mUserHandle);
            AppTarget app5 = new AppTarget(new AppTargetId("app5"), "app5", "provider1",
                    mUserHandle);
            mCallback.mRecommendedWidgets = null;
            mModelHelper.getModel().enqueueModelUpdateTask(
                    newWidgetsPredicationTask(List.of(app5, app3, app2, app4, app1)));
            runOnExecutorSync(MAIN_EXECUTOR, () -> { });

            // THEN only 2 widgets are returned because
            // 1. app5/provider1 & app4/provider1 have already been added to workspace. They are
            //    excluded from the result.
            // 2. app3 doesn't have a widget.
            // 3. only 1 widget is picked from app1 because we only want to promote one widget
            // per app.
            List<PendingAddWidgetInfo> recommendedWidgets = mCallback.mRecommendedWidgets.items
                    .stream()
                    .map(itemInfo -> (PendingAddWidgetInfo) itemInfo)
                    .collect(Collectors.toList());
            assertThat(recommendedWidgets).hasSize(2);
            recommendedWidgets.forEach(pendingAddWidgetInfo ->
                    assertThat(pendingAddWidgetInfo.recommendationCategory).isNotNull()
            );
            assertWidgetInfo(recommendedWidgets.get(0).info, mApp2Provider1);
            assertWidgetInfo(recommendedWidgets.get(1).info, mApp1Provider1);
        });
    }

    @Test
    public void widgetsRecommendationRan_shouldReturnPackageWidgetsWhenEmpty() {
        runOnExecutorSync(MODEL_EXECUTOR, () -> {

            // Not installed widget
            AppTarget widget1 = new AppTarget(new AppTargetId("app1"), "app1", "provider3",
                    mUserHandle);
            // Not installed app
            AppTarget widget3 = new AppTarget(new AppTargetId("app2"), "app3", "provider1",
                    mUserHandle);
            // Workspace added widgets
            AppTarget widget4 = new AppTarget(new AppTargetId("app4"), "app4", "provider1",
                    mUserHandle);
            AppTarget widget5 = new AppTarget(new AppTargetId("app5"), "app5", "provider1",
                    mUserHandle);

            mCallback.mRecommendedWidgets = null;
            mModelHelper.getModel().enqueueModelUpdateTask(
                    newWidgetsPredicationTask(List.of(widget5, widget3, widget4, widget1)));
            runOnExecutorSync(MAIN_EXECUTOR, () -> { });

            // THEN only 2 widgets are returned because the launcher only filters out
            // non-exist widgets.
            List<PendingAddWidgetInfo> recommendedWidgets = mCallback.mRecommendedWidgets.items
                    .stream()
                    .map(itemInfo -> (PendingAddWidgetInfo) itemInfo)
                    .collect(Collectors.toList());
            assertThat(recommendedWidgets).hasSize(2);
            recommendedWidgets.forEach(pendingAddWidgetInfo ->
                    assertThat(pendingAddWidgetInfo.recommendationCategory).isNotNull()
            );
            // Another widget from the same package
            assertWidgetInfo(recommendedWidgets.get(0).info, mApp4Provider2);
            assertWidgetInfo(recommendedWidgets.get(1).info, mApp1Provider1);
        });
    }

    private void assertWidgetInfo(
            LauncherAppWidgetProviderInfo actual, AppWidgetProviderInfo expected) {
        assertThat(actual.provider).isEqualTo(expected.provider);
        assertThat(actual.getUser()).isEqualTo(expected.getProfile());
    }

    private WidgetsPredictionUpdateTask newWidgetsPredicationTask(List<AppTarget> appTargets) {
        return new WidgetsPredictionUpdateTask(
                new PredictorState(CONTAINER_WIDGETS_PREDICTION, "test_widgets_prediction"),
                appTargets);
    }

    private final class FakeBgDataModelCallback implements BgDataModel.Callbacks {

        private FixedContainerItems mRecommendedWidgets = null;

        @Override
        public void bindExtraContainerItems(FixedContainerItems item) {
            mRecommendedWidgets = item;
        }
    }
}
