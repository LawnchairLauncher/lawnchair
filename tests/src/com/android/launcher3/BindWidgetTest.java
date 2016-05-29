package com.android.launcher3;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetHost;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.test.uiautomator.UiSelector;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.ui.LauncherInstrumentationTestCase;
import com.android.launcher3.util.ManagedProfileHeuristic;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetHostViewLoader;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for bind widget flow.
 *
 * Note running these tests will clear the workspace on the device.
 */
@LargeTest
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BindWidgetTest extends LauncherInstrumentationTestCase {

    private ContentResolver mResolver;
    private AppWidgetManagerCompat mWidgetManager;

    // Objects created during test, which should be cleaned up in the end.
    private Cursor mCursor;
    // App install session id.
    private int mSessionId = -1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResolver = mTargetContext.getContentResolver();
        mWidgetManager = AppWidgetManagerCompat.getInstance(mTargetContext);
        grantWidgetPermission();

        // Clear all existing data
        LauncherSettings.Settings.call(mResolver, LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        LauncherSettings.Settings.call(mResolver, LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mCursor != null) {
            mCursor.close();
        }

        if (mSessionId > -1) {
            mTargetContext.getPackageManager().getPackageInstaller().abandonSession(mSessionId);
        }
    }

    public void testBindNormalWidget_withConfig() {
        LauncherAppWidgetProviderInfo info = findWidgetProvider(true);
        LauncherAppWidgetInfo item = createWidgetInfo(info, true);

        setupAndVerifyContents(item, LauncherAppWidgetHostView.class, info.label);
    }

    public void testBindNormalWidget_withoutConfig() {
        LauncherAppWidgetProviderInfo info = findWidgetProvider(false);
        LauncherAppWidgetInfo item = createWidgetInfo(info, true);

        setupAndVerifyContents(item, LauncherAppWidgetHostView.class, info.label);
    }

    public void testUnboundWidget_removed() throws Exception {
        LauncherAppWidgetProviderInfo info = findWidgetProvider(false);
        LauncherAppWidgetInfo item = createWidgetInfo(info, false);
        item.appWidgetId = -33;

        // Since there is no widget to verify, just wait until the workspace is ready.
        setupAndVerifyContents(item, Workspace.class, null);

        waitUntilLoaderIdle();
        // Item deleted from db
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        assertEquals(0, mCursor.getCount());

        // The view does not exist
        assertFalse(mDevice.findObject(new UiSelector().description(info.label)).exists());
    }

    public void testPendingWidget_autoRestored() {
        // A non-restored widget with no config screen gets restored automatically.
        LauncherAppWidgetProviderInfo info = findWidgetProvider(false);

        // Do not bind the widget
        LauncherAppWidgetInfo item = createWidgetInfo(info, false);
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID;

        setupAndVerifyContents(item, LauncherAppWidgetHostView.class, info.label);
    }

    public void testPendingWidget_withConfigScreen() throws Exception {
        // A non-restored widget with config screen get bound and shows a 'Click to setup' UI.
        LauncherAppWidgetProviderInfo info = findWidgetProvider(true);

        // Do not bind the widget
        LauncherAppWidgetInfo item = createWidgetInfo(info, false);
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID;

        setupAndVerifyContents(item, PendingAppWidgetHostView.class, null);
        waitUntilLoaderIdle();
        // Item deleted from db
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        mCursor.moveToNext();

        // Widget has a valid Id now.
        assertEquals(0, mCursor.getInt(mCursor.getColumnIndex(LauncherSettings.Favorites.RESTORED))
                & LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
        assertNotNull(mWidgetManager.getAppWidgetInfo(mCursor.getInt(mCursor.getColumnIndex(
                LauncherSettings.Favorites.APPWIDGET_ID))));
    }

    public void testPendingWidget_notRestored_removed() throws Exception {
        LauncherAppWidgetInfo item = getInvalidWidgetInfo();
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                | LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;

        setupAndVerifyContents(item, Workspace.class, null);
        // The view does not exist
        assertFalse(mDevice.findObject(
                new UiSelector().className(PendingAppWidgetHostView.class)).exists());
        waitUntilLoaderIdle();
        // Item deleted from db
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        assertEquals(0, mCursor.getCount());
    }

    public void testPendingWidget_notRestored_brokenInstall() throws Exception {
        // A widget which is was being installed once, even if its not being
        // installed at the moment is not removed.
        LauncherAppWidgetInfo item = getInvalidWidgetInfo();
        item.restoreStatus = LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                | LauncherAppWidgetInfo.FLAG_RESTORE_STARTED
                | LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;

        setupAndVerifyContents(item, PendingAppWidgetHostView.class, null);
        // Verify item still exists in db
        waitUntilLoaderIdle();
        mCursor = mResolver.query(LauncherSettings.Favorites.getContentUri(item.id),
                null, null, null, null, null);
        assertEquals(1, mCursor.getCount());

        // Widget still has an invalid id.
        mCursor.moveToNext();
        assertEquals(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID,
                mCursor.getInt(mCursor.getColumnIndex(LauncherSettings.Favorites.RESTORED))
                        & LauncherAppWidgetInfo.FLAG_ID_NOT_VALID);
    }

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

        setupAndVerifyContents(item, PendingAppWidgetHostView.class, null);
        // Verify item still exists in db
        waitUntilLoaderIdle();
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
     * @param widgetClass the View class which is displayed on the homescreen
     * @param desc the content description of the view or null.
     */
    private void setupAndVerifyContents(
            LauncherAppWidgetInfo item, Class<?> widgetClass, String desc) {
        long screenId = Workspace.FIRST_SCREEN_ID;
        // Update the screen id counter for the provider.
        LauncherSettings.Settings.call(mResolver, LauncherSettings.Settings.METHOD_NEW_SCREEN_ID);

        if (screenId > Workspace.FIRST_SCREEN_ID) {
            screenId = Workspace.FIRST_SCREEN_ID;
        }
        ContentValues v = new ContentValues();
        v.put(LauncherSettings.WorkspaceScreens._ID, screenId);
        v.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, 0);
        mResolver.insert(LauncherSettings.WorkspaceScreens.CONTENT_URI, v);

        // Insert the item
        v = new ContentValues();
        item.id = LauncherSettings.Settings.call(
                mResolver, LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getLong(LauncherSettings.Settings.EXTRA_VALUE);
        item.screenId = screenId;
        item.onAddToDatabase(mTargetContext, v);
        v.put(LauncherSettings.Favorites._ID, item.id);
        mResolver.insert(LauncherSettings.Favorites.CONTENT_URI, v);

        // Reset loader
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    LauncherClings.markFirstRunClingDismissed(mTargetContext);
                    ManagedProfileHeuristic.markExistingUsersForNoFolderCreation(mTargetContext);
                    LauncherAppState.getInstance().getModel().resetLoadedState(true, true);
                }
            });
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
        // Launch the home activity
        startLauncher();
        // Verify UI
        UiSelector selector = new UiSelector().packageName(mTargetContext.getPackageName())
                .className(widgetClass);
        if (desc != null) {
            selector = selector.description(desc);
        }
        assertTrue(mDevice.findObject(selector).waitForExists(DEFAULT_UI_TIMEOUT));
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
        item.user = mWidgetManager.getUser(info);
        item.cellX = 0;
        item.cellY = 1;
        item.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;

        if (bindWidget) {
            PendingAddWidgetInfo pendingInfo = new PendingAddWidgetInfo(mTargetContext, info);
            pendingInfo.spanX = item.spanX;
            pendingInfo.spanY = item.spanY;
            pendingInfo.minSpanX = item.minSpanX;
            pendingInfo.minSpanY = item.minSpanY;
            Bundle options = WidgetHostViewLoader.getDefaultOptionsForWidget(mTargetContext, pendingInfo);

            AppWidgetHost host = new AppWidgetHost(mTargetContext, Launcher.APPWIDGET_HOST_ID);
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

        Set<String> activePackage = getOnUiThread(new Callable<Set<String>>() {
            @Override
            public Set<String> call() throws Exception {
                return PackageInstallerCompat.getInstance(mTargetContext)
                        .updateAndGetActiveSessionCache().keySet();
            }
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

    /**
     * Blocks the current thread until all the jobs in the main worker thread are complete.
     */
    private void waitUntilLoaderIdle() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        LauncherModel.sWorker.post(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
