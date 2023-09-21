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

import static androidx.test.InstrumentationRegistry.getTargetContext;

import static com.android.launcher3.util.WidgetUtils.createWidgetInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.RemoteViews;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.tapl.Widget;
import com.android.launcher3.tapl.Workspace;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.WidgetManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

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

    // Objects created during test, which should be cleaned up in the end.
    private Cursor mCursor;
    // App install session id.
    private int mSessionId = -1;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mResolver = mTargetContext.getContentResolver();

        // Clear all existing data
        LauncherSettings.Settings.call(mResolver, LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        LauncherSettings.Settings.call(mResolver,
                LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG);
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
        LauncherAppWidgetInfo item = createWidgetInfo(info, getTargetContext(), true);

        addItemToScreen(item);
        verifyWidgetPresent(info);
    }

    @Test
    public void testBindNormalWidget_withoutConfig() {
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, false);
        LauncherAppWidgetInfo item = createWidgetInfo(info, getTargetContext(), true);

        addItemToScreen(item);
        verifyWidgetPresent(info);
    }

    @Test
    public void testUnboundWidget_removed() {
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, false);
        LauncherAppWidgetInfo item = createWidgetInfo(info, getTargetContext(), false);
        item.appWidgetId = -33;

        addItemToScreen(item);

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
        LauncherAppWidgetInfo item = createWidgetInfo(info, getTargetContext(), false);
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID;

        addItemToScreen(item);
        verifyWidgetPresent(info);
    }

    @Test
    public void testPendingWidget_withConfigScreen() {
        // A non-restored widget with config screen get bound and shows a 'Click to setup' UI.
        LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(this, true);

        // Do not bind the widget
        LauncherAppWidgetInfo item = createWidgetInfo(info, getTargetContext(), false);
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID;

        addItemToScreen(item);
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

        // send OPTION_APPWIDGET_RESTORE_COMPLETED
        int appWidgetId = mCursor.getInt(
                mCursor.getColumnIndex(LauncherSettings.Favorites.APPWIDGET_ID));
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mTargetContext);

        Bundle b = new Bundle();
        b.putBoolean(WidgetManagerHelper.WIDGET_OPTION_RESTORE_COMPLETED, true);
        RemoteViews remoteViews = new RemoteViews(mTargetPackage, R.layout.appwidget_not_ready);
        appWidgetManager.updateAppWidgetOptions(appWidgetId, b);
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews);


        // verify changes are reflected
        waitForLauncherCondition("App widget options did not update",
                l -> appWidgetManager.getAppWidgetOptions(appWidgetId).getBoolean(
                        WidgetManagerHelper.WIDGET_OPTION_RESTORE_COMPLETED));
        executeOnLauncher(l -> l.getAppWidgetHolder().startListening());
        verifyWidgetPresent(info);
        assertNull(mLauncher.getWorkspace().tryGetPendingWidget(100));
    }

    @Test
    public void testPendingWidget_notRestored_removed() {
        LauncherAppWidgetInfo item = getInvalidWidgetInfo();
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                | LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;

        addItemToScreen(item);

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

        addItemToScreen(item);
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

        addItemToScreen(item);
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

    private void verifyWidgetPresent(LauncherAppWidgetProviderInfo info) {
        final Widget widget = mLauncher.getWorkspace().tryGetWidget(info.label, DEFAULT_UI_TIMEOUT);
        assertTrue("Widget is not present",
                widget != null);
    }

    private void verifyPendingWidgetPresent() {
        final Widget widget = mLauncher.getWorkspace().tryGetPendingWidget(DEFAULT_UI_TIMEOUT);
        assertTrue("Pending widget is not present",
                widget != null);
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
            InstallSessionHelper.INSTANCE.get(mTargetContext).getActiveSessions()
                    .keySet().forEach(packageUserKey -> packages.add(packageUserKey.mPackageName));
            return packages;
        });
        while (true) {
            try {
                mTargetContext.getPackageManager().getPackageInfo(
                        pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
            } catch (Exception e) {
                if (!activePackage.contains(pkg)) {
                    break;
                }
            }
            pkg = invalidPackage + count;
            count++;
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
