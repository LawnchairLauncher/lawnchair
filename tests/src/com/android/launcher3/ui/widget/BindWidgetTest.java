/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.ui.widget;

import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherAppWidgetHost;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetHostViewLoader;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Tests for bind widget flow.
 *
 * Note running these tests will clear the workspace on the device.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class BindWidgetTest extends AbstractLauncherUiTest {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private ContentResolver mResolver;
    private AppWidgetManagerCompat mWidgetManager;

    // Objects created during test, which should be cleaned up in the end.
    private Cursor mCursor;
    // App install session id.
    private int mSessionId = -1;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mResolver = mTargetContext.getContentResolver();
        mWidgetManager = AppWidgetManagerCompat.getInstance(mTargetContext);

        // Clear all existing data
        LauncherSettings.Settings.call(mResolver, LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        LauncherSettings.Settings.call(mResolver, LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG);
    }

    @After
    public void tearDown() {
        if (mCursor != null) {
            mCursor.close();
        }

        if (mSessionId > -1) {
            mTargetContext.getPackageManager().getPackageInstaller().abandonSession(mSessionId);
        }
    }

    @Test
    public void testBindNormalWidget_withConfig() {
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, true);
        LauncherAppWidgetInfo item = createWidgetInfo(info, true);

        setupContents(item);
        verifyWidgetPresent(info);
    }

    @Test
    public void testBindNormalWidget_withoutConfig() {
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, false);
        LauncherAppWidgetInfo item = createWidgetInfo(info, true);

        setupContents(item);
        verifyWidgetPresent(info);
    }

    @Test
    public void testUnboundWidget_removed() {
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, false);
        LauncherAppWidgetInfo item = createWidgetInfo(info, false);
        item.appWidgetId = -33;

        setupContents(item);

        final Workspace workspace = mLauncher.getWorkspace();
        // Item deleted from db
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        assertEquals(0, mCursor.getCount());

        // The view does not exist
        assertTrue("Widget exists", workspace.tryGetWidget(info.label, 0) == null);
    }

    @Test
    public void testPendingWidget_autoRestored() {
        // A non-restored widget with no config screen gets restored automatically.
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, false);

        // Do not bind the widget
        LauncherAppWidgetInfo item = createWidgetInfo(info, false);
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID;

        setupContents(item);
        verifyWidgetPresent(info);
    }

    @Test
    public void testPendingWidget_withConfigScreen() {
        // A non-restored widget with config screen get bound and shows a 'Click to setup' UI.
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, true);

        // Do not bind the widget
        LauncherAppWidgetInfo item = createWidgetInfo(info, false);
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID;

        setupContents(item);
        verifyPendingWidgetPresent();

        // Item deleted from db
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        mCursor.moveToNext();

        // Widget has a valid Id now.
        assertEquals(0, mCursor.getInt(mCursor.getColumnIndex(LauncherSettings.Favorites.RESTORED))
                & LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
        assertNotNull(AppWidgetManager.getInstance(mTargetContext)
                .getAppWidgetInfo(mCursor.getInt(mCursor.getColumnIndex(
                        LauncherSettings.Favorites.APPWIDGET_ID))));
    }

    @Test
    public void testPendingWidget_notRestored_removed() {
        LauncherAppWidgetInfo item = getInvalidWidgetInfo();
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                | LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;

        setupContents(item);

        assertTrue("Pending widget exists",
                mLauncher.getWorkspace().tryGetPendingWidget(0) == null);
        // Item deleted from db
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        assertEquals(0, mCursor.getCount());
    }

    @Test
    public void testPendingWidget_notRestored_brokenInstall() {
        // A widget which is was being installed once, even if its not being
        // installed at the moment is not removed.
        LauncherAppWidgetInfo item = getInvalidWidgetInfo();
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                | LauncherAppWidgetInfo.FLAG_RESTORE_STARTED
                | LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;

        setupContents(item);
        verifyPendingWidgetPresent();

        // Verify item still exists in db
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        assertEquals(1, mCursor.getCount());

        // Widget still has an invalid id.
        mCursor.moveToNext();
        assertEquals(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID,
                mCursor.getInt(mCursor.getColumnIndex(LauncherSettings.Favorites.RESTORED))
                        & LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
    }

    @Test
    public void testPendingWidget_notRestored_activeInstall() throws Exception {
        // A widget which is being installed is not removed
        LauncherAppWidgetInfo item = getInvalidWidgetInfo();
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                | LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;

        // Create an active installer session
        SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(item.providerName.getPackageName());
        PackageInstaller installer = mTargetContext.getPackageManager().getPackageInstaller();
        mSessionId = installer.createSession(params);

        setupContents(item);
        verifyPendingWidgetPresent();

        // Verify item still exists in db
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        assertEquals(1, mCursor.getCount());

        // Widget still has an invalid id.
        mCursor.moveToNext();
        assertEquals(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID,
                mCursor.getInt(mCursor.getColumnIndex(LauncherSettings.Favorites.RESTORED))
                        & LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
    }

    /**
     * Adds {@param item} on the homescreen on the 0th screen at 0,0, and verifies that the
     * widget class is displayed on the homescreen.
     */
    private void setupContents(LauncherAppWidgetInfo item) {
        int screenId = FIRST_SCREEN_ID;
        // Update the screen id counter for the provider.
        LauncherSettings.Settings.call(mResolver, LauncherSettings.Settings.METHOD_NEW_SCREEN_ID);

        if (screenId > FIRST_SCREEN_ID) {
            screenId = FIRST_SCREEN_ID;
        }

        // Insert the item
        ContentWriter writer = new ContentWriter(mTargetContext);
        item.id = LauncherSettings.Settings.call(
                mResolver, LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getInt(LauncherSettings.Settings.EXTRA_VALUE);
        item.screenId = screenId;
        item.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites._ID, item.id);
        mResolver.insert(LauncherSettings.Favorites.CONTENT_URI, writer.getValues(mTargetContext));
        resetLoaderState();

        // Launch the home activity
        mDevice.pressHome();
        waitForModelLoaded();
    }

    private void verifyWidgetPresent(LauncherAppWidgetProviderInfo info) {
        assertTrue("Widget is not present",
                mLauncher.getWorkspace().tryGetWidget(info.label, DEFAULT_UI_TIMEOUT) != null);
    }

    private void verifyPendingWidgetPresent() {
        assertTrue("Pending widget is not present",
                mLauncher.getWorkspace().tryGetPendingWidget(DEFAULT_UI_TIMEOUT) != null);
    }

    /**
     * Creates a LauncherAppWidgetInfo corresponding to {@param info}
     * @param bindWidget if true the info is bound and a valid widgetId is assigned to
     *                   the LauncherAppWidgetInfo
     */
    private LauncherAppWidgetInfo createWidgetInfo(
            LauncherAppWidgetProviderInfo info, boolean bindWidget) {
        LauncherAppWidgetInfo item = new LauncherAppWidgetInfo(
                LauncherAppWidgetInfo.NO_ID, info.provider);
        item.spanX = info.minSpanX;
        item.spanY = info.minSpanY;
        item.minSpanX = info.minSpanX;
        item.minSpanY = info.minSpanY;
        item.user = info.getProfile();
        item.cellX = 0;
        item.cellY = 1;
        item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;

        if (bindWidget) {
            PendingAddWidgetInfo pendingInfo = new PendingAddWidgetInfo(info);
            pendingInfo.spanX = item.spanX;
            pendingInfo.spanY = item.spanY;
            pendingInfo.minSpanX = item.minSpanX;
            pendingInfo.minSpanY = item.minSpanY;
            Bundle options = WidgetHostViewLoader.getDefaultOptionsForWidget(mTargetContext, pendingInfo);

            AppWidgetHost host = new LauncherAppWidgetHost(mTargetContext);
            int widgetId = host.allocateAppWidgetId();
            if (!mWidgetManager.bindAppWidgetIdIfAllowed(widgetId, info, options)) {
                host.deleteAppWidgetId(widgetId);
                throw new IllegalArgumentException("Unable to bind widget id");
            }
            item.appWidgetId = widgetId;
        }
        return item;
    }

    /**
     * Returns a LauncherAppWidgetInfo with package name which is not present on the device
     */
    private LauncherAppWidgetInfo getInvalidWidgetInfo() {
        String invalidPackage = "com.invalidpackage";
        int count = 0;
        String pkg = invalidPackage;

        Set<String> activePackage = getOnUiThread(() -> {
            Set<String> packages = new HashSet<>();
            PackageInstallerCompat.getInstance(mTargetContext).updateAndGetActiveSessionCache()
                    .keySet().forEach(packageUserKey -> packages.add(packageUserKey.mPackageName));
            return packages;
        });
        while(true) {
            try {
                mTargetContext.getPackageManager().getPackageInfo(
                        pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
            } catch (Exception e) {
                if (!activePackage.contains(pkg)) {
                    break;
                }
            }
            pkg = invalidPackage + count;
            count ++;
        }
        LauncherAppWidgetInfo item = new LauncherAppWidgetInfo(10,
                new ComponentName(pkg, "com.test.widgetprovider"));
        item.spanX = 2;
        item.spanY = 2;
        item.minSpanX = 2;
        item.minSpanY = 2;
        item.cellX = 0;
        item.cellY = 1;
        item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        return item;
    }
}
