package com.android.launcher3.ui;

import android.app.SearchManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherClings;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ManagedProfileHeuristic;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for all instrumentation tests providing various utility methods.
 */
public class LauncherInstrumentationTestCase extends InstrumentationTestCase {

    public static final long DEFAULT_UI_TIMEOUT = 3000;

    protected UiDevice mDevice;
    protected Context mTargetContext;
    protected String mTargetPackage;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = UiDevice.getInstance(getInstrumentation());
        mTargetContext = getInstrumentation().getTargetContext();
        mTargetPackage = mTargetContext.getPackageName();
    }

    protected void lockRotation(boolean naturalOrientation) throws RemoteException {
        Utilities.getPrefs(mTargetContext)
                .edit()
                .putBoolean(Utilities.ALLOW_ROTATION_PREFERENCE_KEY, !naturalOrientation)
                .commit();

        if (naturalOrientation) {
            mDevice.setOrientationNatural();
        } else {
            mDevice.setOrientationRight();
        }
    }

    /**
     * Starts the launcher activity in the target package and returns the Launcher instance.
     */
    protected Launcher startLauncher() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(mTargetPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return (Launcher) getInstrumentation().startActivitySync(homeIntent);
    }

    /**
     * Grants the launcher permission to bind widgets.
     */
    protected void grantWidgetPermission() throws IOException {
        // Check bind widget permission
        if (mTargetContext.getPackageManager().checkPermission(
                mTargetPackage, android.Manifest.permission.BIND_APPWIDGET)
                != PackageManager.PERMISSION_GRANTED) {
            ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation().executeShellCommand(
                    "appwidget grantbind --package " + mTargetPackage);
            // Read the input stream fully.
            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            while (fis.read() != -1);
            fis.close();
        }
    }

    /**
     * Opens all apps and returns the recycler view
     */
    protected UiObject2 openAllApps() {
        if (FeatureFlags.LAUNCHER3_ALL_APPS_PULL_UP) {
            // clicking on the page indicator brings up all apps tray on non tablets.
            findViewById(R.id.page_indicator).click();
        } else {
            mDevice.wait(Until.findObject(
                    By.desc(mTargetContext.getString(R.string.all_apps_button_label))),
                    DEFAULT_UI_TIMEOUT).click();
        }
        return findViewById(R.id.apps_list_view);
    }

    /**
     * Opens widget tray and returns the recycler view.
     */
    protected UiObject2 openWidgetsTray() {
        mDevice.pressMenu(); // Enter overview mode.
        mDevice.wait(Until.findObject(
                By.text(mTargetContext.getString(R.string.widget_button_text)
                        .toUpperCase(Locale.getDefault()))), DEFAULT_UI_TIMEOUT).click();
        return findViewById(R.id.widgets_list_view);
    }

    /**
     * Scrolls the {@param container} until it finds an object matching {@param condition}.
     * @return the matching object.
     */
    protected UiObject2 scrollAndFind(UiObject2 container, BySelector condition) {
        do {
            UiObject2 widget = container.findObject(condition);
            if (widget != null) {
                return widget;
            }
        } while (container.scroll(Direction.DOWN, 1f));
        return container.findObject(condition);
    }

    /**
     * Drags an icon to the center of homescreen.
     */
    protected void dragToWorkspace(UiObject2 icon) {
        Point center = icon.getVisibleCenter();

        // Action Down
        sendPointer(MotionEvent.ACTION_DOWN, center);

        // Wait until "Remove/Delete target is visible
        assertNotNull(findViewById(R.id.delete_target_text));

        Point moveLocation = findViewById(R.id.drag_layer).getVisibleCenter();

        // Move to center
        while(!moveLocation.equals(center)) {
            center.x = getNextMoveValue(moveLocation.x, center.x);
            center.y = getNextMoveValue(moveLocation.y, center.y);
            sendPointer(MotionEvent.ACTION_MOVE, center);
        }
        sendPointer(MotionEvent.ACTION_UP, center);

        // Wait until remove target is gone.
        mDevice.wait(Until.gone(getSelectorForId(R.id.delete_target_text)), DEFAULT_UI_TIMEOUT);
    }

    private int getNextMoveValue(int targetValue, int oldValue) {
        if (targetValue - oldValue > 10) {
            return oldValue + 10;
        } else if (targetValue - oldValue < -10) {
            return oldValue - 10;
        } else {
            return targetValue;
        }
    }

    private void sendPointer(int action, Point point) {
        MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), action, point.x, point.y, 0);
        getInstrumentation().sendPointerSync(event);
        event.recycle();
    }

    /**
     * Removes all icons from homescreen and hotseat.
     */
    public void clearHomescreen() throws Throwable {
        LauncherSettings.Settings.call(mTargetContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB);
        LauncherSettings.Settings.call(mTargetContext.getContentResolver(),
                LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG);
        LauncherClings.markFirstRunClingDismissed(mTargetContext);
        ManagedProfileHeuristic.markExistingUsersForNoFolderCreation(mTargetContext);

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Reset the loader state
                LauncherAppState.getInstance().getModel().resetLoadedState(true, true);
            }
        });
    }

    /**
     * Runs the callback on the UI thread and returns the result.
     */
    protected <T> T getOnUiThread(final Callable<T> callback) {
        final AtomicReference<T> result = new AtomicReference<>(null);
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        result.set(callback.call());
                    } catch (Exception e) { }
                }
            });
        } catch (Throwable t) { }
        return result.get();
    }

    /**
     * Finds a widget provider which can fit on the home screen.
     * @param hasConfigureScreen if true, a provider with a config screen is returned.
     */
    protected LauncherAppWidgetProviderInfo findWidgetProvider(final boolean hasConfigureScreen) {
        LauncherAppWidgetProviderInfo info = getOnUiThread(new Callable<LauncherAppWidgetProviderInfo>() {
            @Override
            public LauncherAppWidgetProviderInfo call() throws Exception {
                InvariantDeviceProfile idv =
                        LauncherAppState.getInstance().getInvariantDeviceProfile();

                ComponentName searchComponent = ((SearchManager) mTargetContext
                        .getSystemService(Context.SEARCH_SERVICE)).getGlobalSearchActivity();
                String searchPackage = searchComponent == null
                        ? null : searchComponent.getPackageName();

                for (AppWidgetProviderInfo info :
                        AppWidgetManagerCompat.getInstance(mTargetContext).getAllProviders()) {
                    if ((info.configure != null) ^ hasConfigureScreen) {
                        continue;
                    }
                    // Exclude the widgets in search package, as Launcher already binds them in
                    // QSB, so they can cause conflicts.
                    if (info.provider.getPackageName().equals(searchPackage)) {
                        continue;
                    }
                    LauncherAppWidgetProviderInfo widgetInfo = LauncherAppWidgetProviderInfo
                            .fromProviderInfo(mTargetContext, info);
                    if (widgetInfo.minSpanX >= idv.numColumns
                            || widgetInfo.minSpanY >= idv.numRows) {
                        continue;
                    }
                    return widgetInfo;
                }
                return null;
            }
        });
        if (info == null) {
            throw new IllegalArgumentException("No valid widget provider");
        }
        return info;
    }

    protected UiObject2 findViewById(int id) {
        return mDevice.wait(Until.findObject(getSelectorForId(id)), DEFAULT_UI_TIMEOUT);
    }

    protected BySelector getSelectorForId(int id) {
        String name = mTargetContext.getResources().getResourceEntryName(id);
        return By.res(mTargetPackage, name);
    }
}
