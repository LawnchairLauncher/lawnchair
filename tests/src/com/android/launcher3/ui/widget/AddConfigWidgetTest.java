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

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.view.View;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Workspace;
import com.android.launcher3.tapl.Widgets;
import com.android.launcher3.testcomponent.WidgetConfigActivity;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to verify widget configuration is properly shown.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AddConfigWidgetTest extends AbstractLauncherUiTest {

    @Rule public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private LauncherAppWidgetProviderInfo mWidgetInfo;
    private AppWidgetManager mAppWidgetManager;

    private int mWidgetId;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mWidgetInfo = TestViewHelpers.findWidgetProvider(this, true /* hasConfigureScreen */);
        mAppWidgetManager = AppWidgetManager.getInstance(mTargetContext);
    }

    @Test
    public void testWidgetConfig() throws Throwable {
        runTest(false, true);
    }

    @Test
    @Ignore // b/121280703
    public void testWidgetConfig_rotate() throws Throwable {
        runTest(true, true);
    }

    @Test
    public void testConfigCancelled() throws Throwable {
        runTest(false, false);
    }

    @Test
    @Ignore // b/121280703
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
        mDevice.pressHome();

        final Widgets widgets = mLauncher.getWorkspace().openAllWidgets();

        // Drag widget to homescreen
        WidgetConfigStartupMonitor monitor = new WidgetConfigStartupMonitor();
        widgets.
                getWidget(mWidgetInfo.getLabel(mTargetContext.getPackageManager())).
                dragToWorkspace();
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
            Wait.atMost(null, new WidgetSearchCondition(), DEFAULT_ACTIVITY_TIMEOUT);
            assertNotNull(mAppWidgetManager.getAppWidgetInfo(mWidgetId));
        } else {
            // Verify that the widget id is deleted.
            Wait.atMost(null, () -> mAppWidgetManager.getAppWidgetInfo(mWidgetId) == null,
                    DEFAULT_ACTIVITY_TIMEOUT);
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
    private class WidgetSearchCondition implements Condition, Workspace.ItemOperator {

        @Override
        public boolean isTrue() throws Throwable {
            return mMainThreadExecutor.submit(mActivityMonitor.itemExists(this)).get();
        }

        @Override
        public boolean evaluate(ItemInfo info, View view) {
            return info instanceof LauncherAppWidgetInfo &&
                    ((LauncherAppWidgetInfo) info).providerName.getClassName().equals(
                            mWidgetInfo.provider.getClassName()) &&
                    ((LauncherAppWidgetInfo) info).appWidgetId == mWidgetId;
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
