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

package com.android.launcher3.popup;

import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.launcher3.AbstractFloatingView.TYPE_SNACKBAR;
import static com.android.launcher3.Flags.FLAG_ENABLE_PRIVATE_SPACE;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TAP_TO_ADD_TO_HOME_SCREEN_FROM_ALL_APPS;
import static com.android.launcher3.model.data.WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.View;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.allapps.PrivateProfileManager;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LauncherMultivalentJUnit;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.TestActivityContext;
import com.android.launcher3.util.TestUtil;
import com.android.launcher3.util.UserIconInfo;
import com.android.launcher3.util.rule.MockUsersRule;
import com.android.launcher3.util.rule.MockUsersRule.MockUser;
import com.android.launcher3.views.Snackbar;
import com.android.launcher3.widget.picker.model.WidgetPickerDataProvider;
import com.android.launcher3.widget.picker.model.data.WidgetPickerData;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.Consumer;

@SmallTest
@RunWith(LauncherMultivalentJUnit.class)
public class SystemShortcutTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Rule public final SandboxApplication mSandboxContext = spy(new SandboxApplication());
    @Rule public final TestActivityContext mTestContext =
            spy(new TestActivityContext(mSandboxContext));
    @Rule public final MockUsersRule mMockUsers = new MockUsersRule(mSandboxContext);

    private View mView;
    private ItemInfo mItemInfo;
    private PrivateProfileManager mPrivateProfileManager;
    private WidgetPickerDataProvider mWidgetPickerDataProvider;
    private AppInfo mAppInfo;

    @Mock LauncherActivityInfo mLauncherActivityInfo;
    @Mock ApplicationInfo mApplicationInfo;
    @Mock Intent mIntent;
    @Mock StatsLogManager mStatsLogManager;
    @Mock(answer = Answers.RETURNS_SELF) StatsLogger mStatsLogger;
    @Mock LauncherAccessibilityDelegate mLauncherAccessibilityDelegate;

    @Before
    public void setUp() {
        doReturn(mLauncherAccessibilityDelegate).when(mTestContext).getAccessibilityDelegate();

        doReturn(mStatsLogManager).when(mTestContext).getStatsLogManager();

        doReturn(mStatsLogger).when(mStatsLogManager).logger();

        mView = new View(mTestContext);
        mItemInfo = new ItemInfo();

        LauncherApps mLauncherApps = mSandboxContext.spyService(LauncherApps.class);
        doReturn(mLauncherActivityInfo).when(mLauncherApps).resolveActivity(any(), any());
        when(mLauncherActivityInfo.getApplicationInfo()).thenReturn(mApplicationInfo);

        mPrivateProfileManager = mTestContext.getAppsView().getPrivateProfileManager();
        spyOn(mPrivateProfileManager);

        mWidgetPickerDataProvider = mTestContext.getWidgetPickerDataProvider();
        spyOn(mWidgetPickerDataProvider);
    }

    @Test
    public void testWidgetsForNullComponentName() {
        assertNull(mItemInfo.getTargetComponent());
        SystemShortcut systemShortcut = SystemShortcut.WIDGETS
                .getShortcut(mTestContext, mItemInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testWidgetsForEmptyWidgetList() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        assertNotNull(mAppInfo.getTargetComponent());
        doReturn(new WidgetPickerData()).when(mWidgetPickerDataProvider).get();
        spyOn(mAppInfo);
        SystemShortcut systemShortcut = SystemShortcut.WIDGETS
                .getShortcut(mTestContext, mAppInfo, mView);
        verify(mAppInfo, times(2)).getTargetComponent();
        assertNull(systemShortcut);
    }

    @Test
    public void testAppInfoShortcut() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        SystemShortcut systemShortcut = SystemShortcut.APP_INFO
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNotNull(systemShortcut);
    }


    @Test
    public void testDontSuggestAppForNonPredictedItem() {
        assertFalse(mItemInfo.isPredictedItem());
        SystemShortcut systemShortcut = SystemShortcut.DONT_SUGGEST_APP
                .getShortcut(mTestContext, mItemInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testDontSuggestAppForPredictedItemWithUndo() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_HOTSEAT_PREDICTION;
        assertTrue(mAppInfo.isPredictedItem());
        SystemShortcut systemShortcut = SystemShortcut.DONT_SUGGEST_APP
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNotNull(systemShortcut);

        TestUtil.runOnExecutorSync(MAIN_EXECUTOR, () -> systemShortcut.onClick(mView));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mStatsLogger).log(eq(LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP));

        // Undo bar shown
        Snackbar snackbar = AbstractFloatingView.getOpenView(mTestContext, TYPE_SNACKBAR);
        assertNotNull(snackbar);
        reset(mStatsLogger);
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR, snackbar.findViewById(
                R.id.action)::performClick);
        verify(mStatsLogger).log(eq(LAUNCHER_DISMISS_PREDICTION_UNDO));
    }

    @Test
    public void testPrivateProfileInstallwithTargetComponentNull() {
        assertNull(mItemInfo.getTargetComponent());
        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mItemInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testPrivateProfileInstallNotAllAppsContainer() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_HOTSEAT_PREDICTION;

        assertNotNull(mAppInfo.getTargetComponent());
        assertFalse(mAppInfo.getContainerInfo().hasAllAppsContainer());

        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testPrivateProfileInstallNullPrivateProfileManager() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_ALL_APPS;
        mPrivateProfileManager = null;

        assertNotNull(mAppInfo.getTargetComponent());
        assertTrue(mAppInfo.getContainerInfo().hasAllAppsContainer());
        assertNull(mPrivateProfileManager);

        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testPrivateProfileInstallPrivateProfileManagerDisabled() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_ALL_APPS;

        assertNotNull(mPrivateProfileManager);
        assertNotNull(mAppInfo.getTargetComponent());
        assertTrue(mAppInfo.getContainerInfo().hasAllAppsContainer());

        when(mPrivateProfileManager.isEnabled()).thenReturn(false);
        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);
        assertNull(systemShortcut);
    }

    @Test
    public void testPrivateProfileInstallNullPrivateProfileUser() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_ALL_APPS;
        when(mPrivateProfileManager.getProfileUser()).thenReturn(null);

        assertNotNull(mPrivateProfileManager);
        assertNotNull(mAppInfo.getTargetComponent());
        assertTrue(mAppInfo.getContainerInfo().hasAllAppsContainer());
        assertNull(mPrivateProfileManager.getProfileUser());

        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);

        assertNull(systemShortcut);
    }

    @Test
    @MockUser(userType = UserIconInfo.TYPE_MAIN)
    @MockUser(userType = UserIconInfo.TYPE_PRIVATE)
    public void testPrivateProfileInstallNonNullPrivateProfileUser() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.container = CONTAINER_ALL_APPS;
        when(mPrivateProfileManager.isEnabled()).thenReturn(true);

        assertNotNull(mAppInfo.getTargetComponent());
        assertTrue(mAppInfo.getContainerInfo().hasAllAppsContainer());
        assertNotNull(mPrivateProfileManager);
        assertNotNull(mPrivateProfileManager.getProfileUser());
        assertNull(mTestContext.getAppsView().getAppsStore().getApp(new ComponentKey(
                mAppInfo.getTargetComponent(), mMockUsers.findUser(UserIconInfo::isPrivate))));

        SystemShortcut systemShortcut = SystemShortcut.PRIVATE_PROFILE_INSTALL
                .getShortcut(mTestContext, mAppInfo, mView);

        verify(mPrivateProfileManager, atLeast(1)).isEnabled();
        assertNotNull(systemShortcut);
    }

    @Test
    public void testInstallGetShortcutWithNonWorkSpaceItemInfo() {
        SystemShortcut systemShortcut = SystemShortcut.INSTALL.getShortcut(
                mTestContext, mItemInfo, mView);
        Assert.assertNull(systemShortcut);
    }

    @Test
    @UiThreadTest
    public void testInstallGetShortcutWithWorkSpaceItemInfo() {
        mAppInfo = new AppInfo();
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        mAppInfo.intent = mIntent;
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo(mAppInfo);
        workspaceItemInfo.status = FLAG_SUPPORTS_WEB_UI;
        SystemShortcut systemShortcut = SystemShortcut.INSTALL.getShortcut(
                mTestContext, workspaceItemInfo, mView);
        Assert.assertNotNull(systemShortcut);
    }


    @Test
    @DisableFlags(FLAG_ENABLE_PRIVATE_SPACE)
    public void testUninstallGetShortcutWithPrivateSpaceOff() {
        SystemShortcut systemShortcut = SystemShortcut.UNINSTALL_APP.getShortcut(
                mTestContext, null, mView);
        Assert.assertNull(systemShortcut);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE)
    public void testUninstallGetShortcutWithNonPrivateItemInfo() {
        mAppInfo = new AppInfo();
        Assert.assertNull(SystemShortcut.UNINSTALL_APP.getShortcut(
                mTestContext, mAppInfo, mView));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE)
    @MockUser(userType = UserIconInfo.TYPE_MAIN)
    @MockUser(userType = UserIconInfo.TYPE_PRIVATE)
    public void testUninstallGetShortcutWithSystemItemInfo() {
        mAppInfo = new AppInfo();
        mAppInfo.user = mMockUsers.findUser(UserIconInfo::isPrivate);
        mAppInfo.itemType = ITEM_TYPE_APPLICATION;
        mAppInfo.intent = mIntent;
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        when(mLauncherActivityInfo.getComponentName()).thenReturn(mAppInfo.componentName);
        // System App
        mApplicationInfo.flags = 1;

        SystemShortcut systemShortcut = SystemShortcut.UNINSTALL_APP.getShortcut(
                mTestContext, mAppInfo, mView);
        verify(mLauncherActivityInfo, times(0)).getComponentName();
        Assert.assertNull(systemShortcut);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE)
    @MockUser(userType = UserIconInfo.TYPE_MAIN)
    @MockUser(userType = UserIconInfo.TYPE_PRIVATE)
    public void testUninstallGetShortcutWithPrivateItemInfo() {
        mAppInfo = new AppInfo();
        mAppInfo.user = mMockUsers.findUser(UserIconInfo::isPrivate);
        mAppInfo.itemType = ITEM_TYPE_APPLICATION;
        mAppInfo.intent = mIntent;
        mAppInfo.componentName = new ComponentName(mTestContext, getClass());
        when(mLauncherActivityInfo.getComponentName()).thenReturn(mAppInfo.componentName);
        // 3rd party app, not system app.
        mApplicationInfo.flags = 0;

        SystemShortcut systemShortcut = SystemShortcut.UNINSTALL_APP.getShortcut(
                mTestContext, mAppInfo, mView);

        verify(mLauncherActivityInfo).getComponentName();
        Assert.assertNotNull(systemShortcut);

        systemShortcut.onClick(mView);
        verify(mSandboxContext).startActivity(any());
    }

    @Test
    public void testAddToHomeScreenShortcutFromAllApps() {
        mAppInfo = new AppInfo();
        mAppInfo.itemType = ITEM_TYPE_APPLICATION;
        mAppInfo.container = CONTAINER_ALL_APPS;
        SystemShortcut systemShortcut = SystemShortcut.ADD_TO_HOME_SCREEN.getShortcut(
                mTestContext, mAppInfo, mView);

        assertNotNull(systemShortcut);

        // Mock the addToWorkspace method to execute the callback immediately
        doAnswer(invocation -> {
            // The callback is the third argument to the method
            Consumer<Boolean> callback = invocation.getArgument(2);
            // Execute the callback with a 'success' value of true
            callback.accept(true);
            return null; // The method returns void
        }).when(mLauncherAccessibilityDelegate).addToWorkspace(any(), eq(false), any());

        systemShortcut.onClick(mView);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mStatsLogger).log(eq(LAUNCHER_TAP_TO_ADD_TO_HOME_SCREEN_FROM_ALL_APPS));
    }

    @Test
    public void testAddToHomeScreenShortcutFromWorkspaceShouldBeNull() {
        mAppInfo = new AppInfo();
        mAppInfo.itemType = ITEM_TYPE_APPLICATION;
        mAppInfo.container = CONTAINER_DESKTOP;
        SystemShortcut systemShortcut = SystemShortcut.ADD_TO_HOME_SCREEN.getShortcut(
                mTestContext, mAppInfo, mView);

        assertNull(systemShortcut);
    }
}
