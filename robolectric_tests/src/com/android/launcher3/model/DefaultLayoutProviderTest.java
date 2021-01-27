/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.setField;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageInstaller.SessionParams;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherModelHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

/**
 * Tests for layout parser for remote layout
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class DefaultLayoutProviderTest {

    private LauncherModelHelper mModelHelper;
    private Context mTargetContext;

    @Before
    public void setUp() {
        mModelHelper = new LauncherModelHelper();
        mTargetContext = RuntimeEnvironment.application;

        shadowOf(mTargetContext.getPackageManager())
                .addActivityIfNotPresent(new ComponentName(TEST_PACKAGE, TEST_PACKAGE));
    }

    @Test
    public void testCustomProfileLoaded_with_icon_on_hotseat() throws Exception {
        writeLayoutAndLoad(new LauncherLayoutBuilder().atHotseat(0)
                .putApp(TEST_PACKAGE, TEST_PACKAGE));

        // Verify one item in hotseat
        assertEquals(1, mModelHelper.getBgDataModel().workspaceItems.size());
        ItemInfo info = mModelHelper.getBgDataModel().workspaceItems.get(0);
        assertEquals(LauncherSettings.Favorites.CONTAINER_HOTSEAT, info.container);
        assertEquals(LauncherSettings.Favorites.ITEM_TYPE_APPLICATION, info.itemType);
    }

    @Test
    public void testCustomProfileLoaded_with_folder() throws Exception {
        writeLayoutAndLoad(new LauncherLayoutBuilder().atHotseat(0).putFolder(android.R.string.copy)
                .addApp(TEST_PACKAGE, TEST_PACKAGE)
                .addApp(TEST_PACKAGE, TEST_PACKAGE)
                .addApp(TEST_PACKAGE, TEST_PACKAGE)
                .build());

        // Verify folder
        assertEquals(1, mModelHelper.getBgDataModel().workspaceItems.size());
        ItemInfo info = mModelHelper.getBgDataModel().workspaceItems.get(0);
        assertEquals(LauncherSettings.Favorites.ITEM_TYPE_FOLDER, info.itemType);
        assertEquals(3, ((FolderInfo) info).contents.size());
    }

    @Test
    public void testCustomProfileLoaded_with_folder_custom_title() throws Exception {
        writeLayoutAndLoad(new LauncherLayoutBuilder().atHotseat(0).putFolder("CustomFolder")
                .addApp(TEST_PACKAGE, TEST_PACKAGE)
                .addApp(TEST_PACKAGE, TEST_PACKAGE)
                .addApp(TEST_PACKAGE, TEST_PACKAGE)
                .build());

        // Verify folder
        assertEquals(1, mModelHelper.getBgDataModel().workspaceItems.size());
        ItemInfo info = mModelHelper.getBgDataModel().workspaceItems.get(0);
        assertEquals(LauncherSettings.Favorites.ITEM_TYPE_FOLDER, info.itemType);
        assertEquals(3, ((FolderInfo) info).contents.size());
        assertEquals("CustomFolder", info.title.toString());
    }

    @Test
    public void testCustomProfileLoaded_with_widget() throws Exception {
        String pendingAppPkg = "com.test.pending";

        // Add a dummy session info so that the widget exists
        SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pendingAppPkg);

        PackageInstaller installer = mTargetContext.getPackageManager().getPackageInstaller();
        int sessionId = installer.createSession(params);
        SessionInfo sessionInfo = installer.getSessionInfo(sessionId);
        setField(sessionInfo, "installerPackageName", "com.test");
        setField(sessionInfo, "appIcon", BitmapInfo.LOW_RES_ICON);

        writeLayoutAndLoad(new LauncherLayoutBuilder().atWorkspace(0, 1, 0)
                .putWidget(pendingAppPkg, "DummyWidget", 2, 2));

        // Verify widget
        assertEquals(1, mModelHelper.getBgDataModel().appWidgets.size());
        ItemInfo info = mModelHelper.getBgDataModel().appWidgets.get(0);
        assertEquals(LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET, info.itemType);
        assertEquals(2, info.spanX);
        assertEquals(2, info.spanY);
    }

    private void writeLayoutAndLoad(LauncherLayoutBuilder builder) throws Exception {
        mModelHelper.setupDefaultLayoutProvider(builder).loadModelSync();
    }
}
