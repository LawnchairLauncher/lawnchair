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
package com.android.launcher3.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.os.ParcelFileDescriptor;
import android.os.Process;
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

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.testcomponent.AppWidgetNoConfig;
import com.android.launcher3.testcomponent.AppWidgetWithConfig;
import com.android.launcher3.util.ManagedProfileHeuristic;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Base class for all instrumentation tests providing various utility methods.
 */
public class LauncherInstrumentationTestCase extends InstrumentationTestCase {

    public static final long DEFAULT_ACTIVITY_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    public static final long DEFAULT_BROADCAST_TIMEOUT_SECS = 5;

    public static final long DEFAULT_UI_TIMEOUT = 3000;
    public static final long DEFAULT_WORKER_TIMEOUT_SECS = 5;

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
        return (Launcher) getInstrumentation().startActivitySync(getHomeIntent());
    }

    protected Intent getHomeIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(mTargetPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * Grants the launcher permission to bind widgets.
     */
    protected void grantWidgetPermission() throws IOException {
        // Check bind widget permission
        if (mTargetContext.getPackageManager().checkPermission(
                mTargetPackage, android.Manifest.permission.BIND_APPWIDGET)
                != PackageManager.PERMISSION_GRANTED) {
            runShellCommand("appwidget grantbind --package " + mTargetPackage);
        }
    }

    /**
     * Sets the target launcher as default launcher.
     */
    protected void setDefaultLauncher() throws IOException {
        ActivityInfo launcher = mTargetContext.getPackageManager()
                .queryIntentActivities(getHomeIntent(), 0).get(0).activityInfo;
        runShellCommand("cmd package set-home-activity " +
                new ComponentName(launcher.packageName, launcher.name).flattenToString());
    }

    protected void runShellCommand(String command) throws IOException {
        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command);

        // Read the input stream fully.
        FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        while (fis.read() != -1);
        fis.close();
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
    protected void dragToWorkspace(UiObject2 icon, boolean expectedToShowShortcuts) {
        Point center = icon.getVisibleCenter();

        // Action Down
        sendPointer(MotionEvent.ACTION_DOWN, center);

        UiObject2 dragLayer = findViewById(R.id.drag_layer);

        if (expectedToShowShortcuts) {
            // Make sure shortcuts show up, and then move a bit to hide them.
            assertNotNull(findViewById(R.id.deep_shortcuts_container));

            Point moveLocation = new Point(center);
            int distanceToMove = mTargetContext.getResources().getDimensionPixelSize(
                    R.dimen.deep_shortcuts_start_drag_threshold) + 50;
            if (moveLocation.y - distanceToMove >= dragLayer.getVisibleBounds().top) {
                moveLocation.y -= distanceToMove;
            } else {
                moveLocation.y += distanceToMove;
            }
            movePointer(center, moveLocation);

            assertNull(findViewById(R.id.deep_shortcuts_container));
        }

        // Wait until Remove/Delete target is visible
        assertNotNull(findViewById(R.id.delete_target_text));

        Point moveLocation = dragLayer.getVisibleCenter();

        // Move to center
        movePointer(center, moveLocation);
        sendPointer(MotionEvent.ACTION_UP, center);

        // Wait until remove target is gone.
        mDevice.wait(Until.gone(getSelectorForId(R.id.delete_target_text)), DEFAULT_UI_TIMEOUT);
    }

    private void movePointer(Point from, Point to) {
        while(!from.equals(to)) {
            from.x = getNextMoveValue(to.x, from.x);
            from.y = getNextMoveValue(to.y, from.y);
            sendPointer(MotionEvent.ACTION_MOVE, from);
        }
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

    protected void sendPointer(int action, Point point) {
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
        resetLoaderState();
    }

    protected void resetLoaderState() {
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ManagedProfileHeuristic.markExistingUsersForNoFolderCreation(mTargetContext);
                    LauncherAppState.getInstance(mTargetContext).getModel().forceReload();
                }
            });
        } catch (Throwable t) {
            throw new IllegalArgumentException(t);
        }
    }

    /**
     * Runs the callback on the UI thread and returns the result.
     */
    protected <T> T getOnUiThread(final Callable<T> callback) {
        try {
            return new MainThreadExecutor().submit(callback).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds a widget provider which can fit on the home screen.
     * @param hasConfigureScreen if true, a provider with a config screen is returned.
     */
    protected LauncherAppWidgetProviderInfo findWidgetProvider(final boolean hasConfigureScreen) {
        LauncherAppWidgetProviderInfo info =
                getOnUiThread(new Callable<LauncherAppWidgetProviderInfo>() {
            @Override
            public LauncherAppWidgetProviderInfo call() throws Exception {
                ComponentName cn = new ComponentName(getInstrumentation().getContext(),
                        hasConfigureScreen ? AppWidgetWithConfig.class : AppWidgetNoConfig.class);
                return AppWidgetManagerCompat.getInstance(mTargetContext)
                        .findProvider(cn, Process.myUserHandle());
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


    /**
     * Broadcast receiver which blocks until the result is received.
     */
    public class BlockingBroadcastReceiver extends BroadcastReceiver {

        private final CountDownLatch latch = new CountDownLatch(1);
        private Intent mIntent;

        public BlockingBroadcastReceiver(String action) {
            mTargetContext.registerReceiver(this, new IntentFilter(action));
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mIntent = intent;
            latch.countDown();
        }

        public Intent blockingGetIntent() throws InterruptedException {
            latch.await(DEFAULT_BROADCAST_TIMEOUT_SECS, TimeUnit.SECONDS);
            mTargetContext.unregisterReceiver(this);
            return mIntent;
        }

        public Intent blockingGetExtraIntent() throws InterruptedException {
            Intent intent = blockingGetIntent();
            return intent == null ? null : (Intent) intent.getParcelableExtra(Intent.EXTRA_INTENT);
        }
    }
}
