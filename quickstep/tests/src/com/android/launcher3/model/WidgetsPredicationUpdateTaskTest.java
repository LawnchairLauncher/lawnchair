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
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
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
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.Flags;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.ModelTestExtensions;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.rule.LayoutProviderRule;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class WidgetsPredicationUpdateTaskTest {

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule public SandboxApplication mContext = new SandboxApplication().withModelDependency();
    @Rule public LayoutProviderRule mLayoutProvider = new LayoutProviderRule(mContext);


    private AppWidgetProviderInfo mApp1Provider1;
    private AppWidgetProviderInfo mApp1Provider2;
    private AppWidgetProviderInfo mApp2Provider1;
    private AppWidgetProviderInfo mApp4Provider1;
    private AppWidgetProviderInfo mApp4Provider2;
    private AppWidgetProviderInfo mApp5Provider1;
    private AppWidgetProviderInfo mApp6PinOnlyProvider1;
    private List<AppWidgetProviderInfo> allWidgets;

    private FakeBgDataModelCallback mCallback = new FakeBgDataModelCallback();
    private UserHandle mUserHandle;
    private LauncherApps mLauncherApps;


    @Before
    public void setup() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_CATEGORIZED_WIDGET_SUGGESTIONS);

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
        mApp6PinOnlyProvider1 = createAppWidgetProviderInfo(
                ComponentName.createRelative("app6", "provider1"),
                /*hideFromPicker=*/ true
        );


        allWidgets = Arrays.asList(mApp1Provider1, mApp1Provider2, mApp2Provider1,
                mApp4Provider1, mApp4Provider2, mApp5Provider1, mApp6PinOnlyProvider1);

        mLauncherApps = mContext.spyService(LauncherApps.class);
        doAnswer(i -> {
            String pkg = i.getArgument(0);
            ApplicationInfo applicationInfo = new ApplicationInfo();
            applicationInfo.packageName = pkg;
            applicationInfo.name = "App " + pkg;
            applicationInfo.uid = Process.myUid();
            applicationInfo.category = CATEGORY_PRODUCTIVITY;
            applicationInfo.flags = FLAG_INSTALLED;
            return applicationInfo;
        }).when(mLauncherApps).getApplicationInfo(anyString(), anyInt(), any());

        AppWidgetManager manager = mContext.spyService(AppWidgetManager.class);
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
        mLayoutProvider.setupDefaultLayoutProvider(builder);
        MAIN_EXECUTOR.submit(() -> getModel().addCallbacks(mCallback)).get();
        ModelTestExtensions.INSTANCE.loadModelSync(getModel());
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TIERED_WIDGETS_BY_DEFAULT_IN_PICKER) // Flag off
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
            getModel().enqueueModelUpdateTask(
                    newWidgetsPredicationTask(List.of(app5, app3, app2, app4, app1)));
            runOnExecutorSync(MAIN_EXECUTOR, () -> { });

            // THEN only 2 widgets are returned because
            // 1. app5/provider1 & app4/provider1 have already been added to workspace. They are
            //    excluded from the result.
            // 2. app3 doesn't have a widget.
            // 3. only 1 widget is picked from app1 because we only want to promote one widget
            // per app.
            List<PendingAddWidgetInfo> recommendedWidgets = mCallback.mRecommendedWidgets
                    .getContents()
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
    @DisableFlags(Flags.FLAG_ENABLE_TIERED_WIDGETS_BY_DEFAULT_IN_PICKER) // Flag off
    public void widgetsRecommendationRan_shouldReturnEmptyWidgetsWhenEmpty() {
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
            getModel().enqueueModelUpdateTask(
                    newWidgetsPredicationTask(List.of(widget5, widget3, widget4, widget1)));
            runOnExecutorSync(MAIN_EXECUTOR, () -> { });

            // Only widgets suggested by prediction system are returned.
            List<PendingAddWidgetInfo> recommendedWidgets = mCallback.mRecommendedWidgets
                    .getContents()
                    .stream()
                    .map(itemInfo -> (PendingAddWidgetInfo) itemInfo)
                    .collect(Collectors.toList());
            assertThat(recommendedWidgets).hasSize(0);
        });
    }

    @Test
    public void widgetsRecommendations_excludesWidgetsHiddenForPicker() {
        runOnExecutorSync(MODEL_EXECUTOR, () -> {

            // Not installed widget - hence eligible
            AppTarget widget1 = new AppTarget(new AppTargetId("app1"), "app1", "provider1",
                    mUserHandle);
            // Provider marked as hidden from picker - hence not eligible
            AppTarget widget6 = new AppTarget(new AppTargetId("app6"), "app6", "provider1",
                    mUserHandle);

            mCallback.mRecommendedWidgets = null;
            getModel().enqueueModelUpdateTask(
                    newWidgetsPredicationTask(List.of(widget1, widget6)));
            runOnExecutorSync(MAIN_EXECUTOR, () -> { });

            // Only widget 1 (and no widget 6 as its meant to be hidden from picker).
            List<PendingAddWidgetInfo> recommendedWidgets = mCallback.mRecommendedWidgets
                    .getContents()
                    .stream()
                    .map(itemInfo -> (PendingAddWidgetInfo) itemInfo)
                    .collect(Collectors.toList());
            assertThat(recommendedWidgets).hasSize(1);
            assertThat(recommendedWidgets.get(0).componentName.getPackageName()).isEqualTo("app1");
        });
    }

    private void assertWidgetInfo(
            LauncherAppWidgetProviderInfo actual, AppWidgetProviderInfo expected) {
        assertThat(actual.provider).isEqualTo(expected.provider);
        assertThat(actual.getUser()).isEqualTo(expected.getProfile());
    }

    private WidgetsPredictionUpdateTask newWidgetsPredicationTask(List<AppTarget> appTargets) {
        return new WidgetsPredictionUpdateTask(
                new PredictorState(CONTAINER_WIDGETS_PREDICTION, "test_widgets_prediction",
                        DEFAULT_LOOKUP_FLAG),
                appTargets);
    }

    private LauncherModel getModel() {
        return LauncherAppState.getInstance(mContext).getModel();
    }

    private final class FakeBgDataModelCallback implements BgDataModel.Callbacks {

        private PredictedContainerInfo mRecommendedWidgets = null;

        @Override
        public void bindItemsUpdated(@NonNull Set<ItemInfo> updates) {
            for (ItemInfo update : updates) {
                if (update.id == CONTAINER_WIDGETS_PREDICTION
                        && update instanceof PredictedContainerInfo pci) {
                    mRecommendedWidgets = pci;
                }
            }
        }
    }
}
