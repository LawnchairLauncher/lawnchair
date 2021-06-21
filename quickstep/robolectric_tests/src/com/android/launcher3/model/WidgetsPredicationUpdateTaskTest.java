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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.robolectric.Shadows.shadowOf;

import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetId;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.QuickstepModelDelegate.PredictorState;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shadows.ShadowDeviceFlag;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAppWidgetManager;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
public final class WidgetsPredicationUpdateTaskTest {

    private AppWidgetProviderInfo mApp1Provider1 = new AppWidgetProviderInfo();
    private AppWidgetProviderInfo mApp1Provider2 = new AppWidgetProviderInfo();
    private AppWidgetProviderInfo mApp2Provider1 = new AppWidgetProviderInfo();
    private AppWidgetProviderInfo mApp4Provider1 = new AppWidgetProviderInfo();
    private AppWidgetProviderInfo mApp4Provider2 = new AppWidgetProviderInfo();
    private AppWidgetProviderInfo mApp5Provider1 = new AppWidgetProviderInfo();

    private FakeBgDataModelCallback mCallback = new FakeBgDataModelCallback();
    private Context mContext;
    private LauncherModelHelper mModelHelper;
    private UserHandle mUserHandle;
    private InvariantDeviceProfile mTestProfile;

    @Mock
    private IconCache mIconCache;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        doAnswer(invocation -> {
            ComponentWithLabel componentWithLabel = invocation.getArgument(0);
            return componentWithLabel.getComponent().getShortClassName();
        }).when(mIconCache).getTitleNoCache(any());

        mContext = RuntimeEnvironment.application;
        mModelHelper = new LauncherModelHelper();
        mUserHandle = Process.myUserHandle();
        mTestProfile = new InvariantDeviceProfile();
        // 2 widgets, app4/provider1 & app5/provider1, have already been added to the workspace.
        mModelHelper.initializeData("/widgets_predication_update_task_data.txt");

        ShadowPackageManager packageManager = shadowOf(mContext.getPackageManager());
        mApp1Provider1.provider = ComponentName.createRelative("app1", "provider1");
        ReflectionHelpers.setField(mApp1Provider1, "providerInfo",
                packageManager.addReceiverIfNotPresent(mApp1Provider1.provider));
        mApp1Provider2.provider = ComponentName.createRelative("app1", "provider2");
        ReflectionHelpers.setField(mApp1Provider2, "providerInfo",
                packageManager.addReceiverIfNotPresent(mApp1Provider2.provider));
        mApp2Provider1.provider = ComponentName.createRelative("app2", "provider1");
        ReflectionHelpers.setField(mApp2Provider1, "providerInfo",
                packageManager.addReceiverIfNotPresent(mApp2Provider1.provider));
        mApp4Provider1.provider = ComponentName.createRelative("app4", "provider1");
        ReflectionHelpers.setField(mApp4Provider1, "providerInfo",
                packageManager.addReceiverIfNotPresent(mApp4Provider1.provider));
        mApp4Provider2.provider = ComponentName.createRelative("app4", ".provider2");
        ReflectionHelpers.setField(mApp4Provider2, "providerInfo",
                packageManager.addReceiverIfNotPresent(mApp4Provider2.provider));
        mApp5Provider1.provider = ComponentName.createRelative("app5", "provider1");
        ReflectionHelpers.setField(mApp5Provider1, "providerInfo",
                packageManager.addReceiverIfNotPresent(mApp5Provider1.provider));

        ShadowAppWidgetManager shadowAppWidgetManager =
                shadowOf(mContext.getSystemService(AppWidgetManager.class));
        shadowAppWidgetManager.addInstalledProvider(mApp1Provider1);
        shadowAppWidgetManager.addInstalledProvider(mApp1Provider2);
        shadowAppWidgetManager.addInstalledProvider(mApp2Provider1);
        shadowAppWidgetManager.addInstalledProvider(mApp4Provider1);
        shadowAppWidgetManager.addInstalledProvider(mApp4Provider2);
        shadowAppWidgetManager.addInstalledProvider(mApp5Provider1);

        mModelHelper.getModel().addCallbacks(mCallback);

        MODEL_EXECUTOR.post(() -> mModelHelper.getBgDataModel().widgetsModel.update(
                LauncherAppState.getInstance(mContext), /* packageUser= */ null));
        waitUntilIdle();
    }


    @Test
    public void widgetsRecommendationRan_shouldOnlyReturnNotAddedWidgetsInAppPredictionOrder()
            throws Exception {
        // WHEN newPredicationTask is executed with app predication of 5 apps.
        AppTarget app1 = new AppTarget(new AppTargetId("app1"), "app1", "className",
                mUserHandle);
        AppTarget app2 = new AppTarget(new AppTargetId("app2"), "app2", "className",
                mUserHandle);
        AppTarget app3 = new AppTarget(new AppTargetId("app3"), "app3", "className",
                mUserHandle);
        AppTarget app4 = new AppTarget(new AppTargetId("app4"), "app4", "className",
                mUserHandle);
        AppTarget app5 = new AppTarget(new AppTargetId("app5"), "app5", "className",
                mUserHandle);
        mModelHelper.executeTaskForTest(
                newWidgetsPredicationTask(List.of(app5, app3, app2, app4, app1)))
                .forEach(Runnable::run);

        // THEN only 3 widgets are returned because
        // 1. app5/provider1 & app4/provider1 have already been added to workspace. They are
        //    excluded from the result.
        // 2. app3 doesn't have a widget.
        // 3. only 1 widget is picked from app1 because we only want to promote one widget per app.
        List<PendingAddWidgetInfo> recommendedWidgets = mCallback.mRecommendedWidgets.items
                .stream()
                .map(itemInfo -> (PendingAddWidgetInfo) itemInfo)
                .collect(Collectors.toList());
        assertThat(recommendedWidgets).hasSize(3);
        assertWidgetInfo(recommendedWidgets.get(0).info, mApp2Provider1);
        assertWidgetInfo(recommendedWidgets.get(1).info, mApp4Provider2);
        assertWidgetInfo(recommendedWidgets.get(2).info, mApp1Provider1);
    }

    @Test
    public void widgetsRecommendationRan_localFilterDisabled_shouldReturnWidgetsInPredicationOrder()
            throws Exception {
        ShadowDeviceFlag shadowDeviceFlag = Shadow.extract(
                FeatureFlags.ENABLE_LOCAL_RECOMMENDED_WIDGETS_FILTER);
        shadowDeviceFlag.setValue(false);

        // WHEN newPredicationTask is executed with 5 predicated widgets.
        AppTarget widget1 = new AppTarget(new AppTargetId("app1"), "app1", "provider1",
                mUserHandle);
        AppTarget widget2 = new AppTarget(new AppTargetId("app1"), "app1", "provider2",
                mUserHandle);
        // Not installed app
        AppTarget widget3 = new AppTarget(new AppTargetId("app2"), "app3", "provider1",
                mUserHandle);
        // Not installed widget
        AppTarget widget4 = new AppTarget(new AppTargetId("app4"), "app4", "provider3",
                mUserHandle);
        AppTarget widget5 = new AppTarget(new AppTargetId("app5"), "app5", "provider1",
                mUserHandle);
        mModelHelper.executeTaskForTest(
                newWidgetsPredicationTask(List.of(widget5, widget3, widget2, widget4, widget1)))
                .forEach(Runnable::run);

        // THEN only 3 widgets are returned because the launcher only filters out non-exist widgets.
        List<PendingAddWidgetInfo> recommendedWidgets = mCallback.mRecommendedWidgets.items
                .stream()
                .map(itemInfo -> (PendingAddWidgetInfo) itemInfo)
                .collect(Collectors.toList());
        assertThat(recommendedWidgets).hasSize(3);
        assertWidgetInfo(recommendedWidgets.get(0).info, mApp5Provider1);
        assertWidgetInfo(recommendedWidgets.get(1).info, mApp1Provider2);
        assertWidgetInfo(recommendedWidgets.get(2).info, mApp1Provider1);
    }

    private void assertWidgetInfo(
            LauncherAppWidgetProviderInfo actual, AppWidgetProviderInfo expected) {
        assertThat(actual.provider).isEqualTo(expected.provider);
        assertThat(actual.getUser()).isEqualTo(expected.getProfile());
    }

    private void waitUntilIdle() {
        shadowOf(MODEL_EXECUTOR.getLooper()).idle();
        shadowOf(MAIN_EXECUTOR.getLooper()).idle();
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

        @Override
        public int getPageToBindSynchronously() {
            return 0;
        }

        @Override
        public void clearPendingBinds() { }

        @Override
        public void startBinding() { }

        @Override
        public void bindItems(List<ItemInfo> shortcuts, boolean forceAnimateIcons) { }

        @Override
        public void bindScreens(IntArray orderedScreenIds) { }

        @Override
        public void finishFirstPageBind(ViewOnDrawExecutor executor) { }

        @Override
        public void finishBindingItems(int pageBoundFirst) { }

        @Override
        public void preAddApps() { }

        @Override
        public void bindAppsAdded(IntArray newScreens, ArrayList<ItemInfo> addNotAnimated,
                ArrayList<ItemInfo> addAnimated) { }

        @Override
        public void bindIncrementalDownloadProgressUpdated(AppInfo app) { }

        @Override
        public void bindWorkspaceItemsChanged(List<WorkspaceItemInfo> updated) { }

        @Override
        public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets) { }

        @Override
        public void bindRestoreItemsChange(HashSet<ItemInfo> updates) { }

        @Override
        public void bindWorkspaceComponentsRemoved(ItemInfoMatcher matcher) { }

        @Override
        public void bindAllWidgets(List<WidgetsListBaseEntry> widgets) { }

        @Override
        public void onPageBoundSynchronously(int page) { }

        @Override
        public void executeOnNextDraw(ViewOnDrawExecutor executor) { }

        @Override
        public void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMap) { }

        @Override
        public void bindAllApplications(AppInfo[] apps, int flags) { }
    }
}
