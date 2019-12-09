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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.setField;

import android.content.ComponentName;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageInstaller.SessionParams;
import android.net.Uri;
import android.provider.Settings;

import com.android.launcher3.FolderInfo;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.shadows.LShadowLauncherApps;
import com.android.launcher3.shadows.LShadowUserManager;
import com.android.launcher3.shadows.ShadowLooperExecutor;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.widget.custom.CustomWidgetManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Tests for layout parser for remote layout
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = {LShadowUserManager.class, LShadowLauncherApps.class, ShadowLooperExecutor.class})
@LooperMode(Mode.PAUSED)
public class DefaultLayoutProviderTest extends BaseModelUpdateTaskTestCase {

    private static final String SETTINGS_APP = "com.android.settings";
    private static final String TEST_PROVIDER_AUTHORITY =
            DefaultLayoutProviderTest.class.getName().toLowerCase();

    private static final int BITMAP_SIZE = 10;
    private static final int GRID_SIZE = 4;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        InvariantDeviceProfile.INSTANCE.initializeForTesting(idp);
        CustomWidgetManager.INSTANCE.initializeForTesting(mock(CustomWidgetManager.class));

        idp.numRows = idp.numColumns = idp.numHotseatIcons = GRID_SIZE;
        idp.iconBitmapSize = BITMAP_SIZE;

        provider.setAllowLoadDefaultFavorites(true);
        Settings.Secure.putString(targetContext.getContentResolver(),
                "launcher3.layout.provider", TEST_PROVIDER_AUTHORITY);

        ShadowPackageManager spm = shadowOf(targetContext.getPackageManager());
        spm.addProviderIfNotPresent(new ComponentName("com.test", "Dummy")).authority =
                TEST_PROVIDER_AUTHORITY;
        spm.addActivityIfNotPresent(new ComponentName(SETTINGS_APP, SETTINGS_APP));
    }

    @After
    public void cleanup() {
        InvariantDeviceProfile.INSTANCE.initializeForTesting(null);
        CustomWidgetManager.INSTANCE.initializeForTesting(null);
    }

    @Test
    public void testCustomProfileLoaded_with_icon_on_hotseat() throws Exception {
        writeLayoutAndLoad(new LauncherLayoutBuilder().atHotseat(0)
                .putApp(SETTINGS_APP, SETTINGS_APP));

        // Verify one item in hotseat
        assertEquals(1, bgDataModel.workspaceItems.size());
        ItemInfo info = bgDataModel.workspaceItems.get(0);
        assertEquals(LauncherSettings.Favorites.CONTAINER_HOTSEAT, info.container);
        assertEquals(LauncherSettings.Favorites.ITEM_TYPE_APPLICATION, info.itemType);
    }

    @Test
    public void testCustomProfileLoaded_with_folder() throws Exception {
        writeLayoutAndLoad(new LauncherLayoutBuilder().atHotseat(0).putFolder(android.R.string.copy)
                .addApp(SETTINGS_APP, SETTINGS_APP)
                .addApp(SETTINGS_APP, SETTINGS_APP)
                .addApp(SETTINGS_APP, SETTINGS_APP)
                .build());

        // Verify folder
        assertEquals(1, bgDataModel.workspaceItems.size());
        ItemInfo info = bgDataModel.workspaceItems.get(0);
        assertEquals(LauncherSettings.Favorites.ITEM_TYPE_FOLDER, info.itemType);
        assertEquals(3, ((FolderInfo) info).contents.size());
    }

    @Test
    public void testCustomProfileLoaded_with_widget() throws Exception {
        String pendingAppPkg = "com.test.pending";

        // Add a dummy session info so that the widget exists
        SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pendingAppPkg);

        PackageInstaller installer = targetContext.getPackageManager().getPackageInstaller();
        int sessionId = installer.createSession(params);
        SessionInfo sessionInfo = installer.getSessionInfo(sessionId);
        setField(sessionInfo, "installerPackageName", "com.test");
        setField(sessionInfo, "appIcon", BitmapInfo.LOW_RES_ICON);

        writeLayoutAndLoad(new LauncherLayoutBuilder().atWorkspace(0, 1, 0)
                .putWidget(pendingAppPkg, "DummyWidget", 2, 2));

        // Verify widget
        assertEquals(1, bgDataModel.appWidgets.size());
        ItemInfo info = bgDataModel.appWidgets.get(0);
        assertEquals(LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET, info.itemType);
        assertEquals(2, info.spanX);
        assertEquals(2, info.spanY);
    }

    private void writeLayoutAndLoad(LauncherLayoutBuilder builder) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        builder.build(new OutputStreamWriter(bos));

        Uri layoutUri = LauncherProvider.getLayoutUri(TEST_PROVIDER_AUTHORITY, targetContext);
        shadowOf(targetContext.getContentResolver()).registerInputStream(layoutUri,
                new ByteArrayInputStream(bos.toByteArray()));

        LoaderResults results = new LoaderResults(appState, bgDataModel, allAppsList, 0,
                new WeakReference<>(callbacks));
        LoaderTask task = new LoaderTask(appState, allAppsList, bgDataModel, results);
        Executors.MODEL_EXECUTOR.submit(() -> task.loadWorkspace(new ArrayList<>())).get();
    }
}
