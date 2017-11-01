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

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Workspace;
import com.android.launcher3.testcomponent.WidgetConfigActivity;
import com.android.launcher3.ui.LauncherInstrumentationTestCase;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.SimpleActivityMonitor;
import com.android.launcher3.util.Wait;
import com.android.launcher3.widget.WidgetCell;

import java.util.concurrent.Callable;

/**
 * Test to verify widget configuration is properly shown.
 */
@LargeTest
public class AddConfigWidgetTest extends LauncherInstrumentationTestCase {

    private LauncherAppWidgetProviderInfo mWidgetInfo;
    private SimpleActivityMonitor mActivityMonitor;
    private MainThreadExecutor mMainThreadExecutor;
    private AppWidgetManager mAppWidgetManager;

    private int mWidgetId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWidgetInfo = findWidgetProvider(true /* hasConfigureScreen */);
        mActivityMonitor = new SimpleActivityMonitor();
        ((Application) getInstrumentation().getTargetContext().getApplicationContext())
                .registerActivityLifecycleCallbacks(mActivityMonitor);
        mMainThreadExecutor = new MainThreadExecutor();
        mAppWidgetManager = AppWidgetManager.getInstance(mTargetContext);

        grantWidgetPermission();
    }

    @Override
    protected void tearDown() throws Exception {
        ((Application) getInstrumentation().getTargetContext().getApplicationContext())
                .unregisterActivityLifecycleCallbacks(mActivityMonitor);
        super.tearDown();
    }

    public void testWidgetConfig() throws Throwable {
        runTest(false, true);
    }

    public void testWidgetConfig_rotate() throws Throwable {
        runTest(true, true);
    }

    public void testConfigCancelled() throws Throwable {
        runTest(false, false);
    }

    public void testConfigCancelled_rotate() throws Throwable {
        runTest(true, false);
    }

    /**
     * @param rotateConfig should the config screen be rotated
     * @param acceptConfig accept the config activity
     */
    private void runTest(boolean rotateConfig, boolean acceptConfig) throws Throwable {
        lockRotation(true);

        clearHomescreen();
        startLauncher();

        // Open widget tray and wait for load complete.
        final UiObject2 widgetContainer = openWidgetsTray();
        assertTrue(Wait.atMost(Condition.minChildCount(widgetContainer, 2), DEFAULT_UI_TIMEOUT));

        // Drag widget to homescreen
        WidgetConfigStartupMonitor monitor = new WidgetConfigStartupMonitor();
        UiObject2 widget = scrollAndFind(widgetContainer, By.clazz(WidgetCell.class)
                .hasDescendant(By.text(mWidgetInfo.getLabel(mTargetContext.getPackageManager()))));
        dragToWorkspace(widget, false);
        // Widget id for which the config activity was opened
        mWidgetId = monitor.getWidgetId();

        if (rotateConfig) {
            // Rotate the screen and verify that the config activity is recreated
            monitor = new WidgetConfigStartupMonitor();
            lockRotation(false);
            assertEquals(mWidgetId, monitor.getWidgetId());
        }

        // Verify that the widget id is valid and bound
        assertNotNull(mAppWidgetManager.getAppWidgetInfo(mWidgetId));

        setResult(acceptConfig);
        if (acceptConfig) {
            assertTrue(Wait.atMost(new WidgetSearchCondition(), DEFAULT_ACTIVITY_TIMEOUT));
            assertNotNull(mAppWidgetManager.getAppWidgetInfo(mWidgetId));
        } else {
            // Verify that the widget id is deleted.
            assertTrue(Wait.atMost(new Condition() {
                @Override
                public boolean isTrue() throws Throwable {
                    return mAppWidgetManager.getAppWidgetInfo(mWidgetId) == null;
                }
            }, DEFAULT_ACTIVITY_TIMEOUT));
        }
    }

    private void setResult(boolean success) {

        getInstrumentation().getTargetContext().sendBroadcast(
                WidgetConfigActivity.getCommandIntent(WidgetConfigActivity.class,
                        success ? "clickOK" : "clickCancel"));
    }

    /**
     * Condition for searching widget id
     */
    private class WidgetSearchCondition extends Condition
            implements Callable<Boolean>, Workspace.ItemOperator {

        @Override
        public boolean isTrue() throws Throwable {
            return mMainThreadExecutor.submit(this).get();
        }

        @Override
        public boolean evaluate(ItemInfo info, View view) {
            return info instanceof LauncherAppWidgetInfo &&
                    ((LauncherAppWidgetInfo) info).providerName.equals(mWidgetInfo.provider) &&
                    ((LauncherAppWidgetInfo) info).appWidgetId == mWidgetId;
        }

        @Override
        public Boolean call() throws Exception {
            // Find the resumed launcher
            Launcher launcher = null;
            for (Activity a : mActivityMonitor.resumed) {
                if (a instanceof Launcher) {
                    launcher = (Launcher) a;
                }
            }
            if (launcher == null) {
                return false;
            }
            return launcher.getWorkspace().getFirstMatch(this) != null;
        }
    }

    /**
     * Broadcast receiver for receiving widget config activity status.
     */
    private class WidgetConfigStartupMonitor extends BlockingBroadcastReceiver {

        public WidgetConfigStartupMonitor() {
            super(WidgetConfigActivity.class.getName());
        }

        public int getWidgetId() throws InterruptedException {
            Intent intent = blockingGetExtraIntent();
            assertNotNull(intent);
            assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, intent.getAction());
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    LauncherAppWidgetInfo.NO_ID);
            assertNotSame(widgetId, LauncherAppWidgetInfo.NO_ID);
            return widgetId;
        }
    }
}
