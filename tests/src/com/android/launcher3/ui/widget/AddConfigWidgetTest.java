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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import android.appwidget.AppWidgetManager;
import android.content.Intent;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.tapl.Widget;
import com.android.launcher3.tapl.WidgetResizeFrame;
import com.android.launcher3.tapl.Widgets;
import com.android.launcher3.testcomponent.WidgetConfigActivity;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to verify widget configuration is properly shown.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AddConfigWidgetTest extends AbstractLauncherUiTest {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

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
    @PortraitLandscape
    public void testWidgetConfig() throws Throwable {
        runTest(true);
    }

    @Test
    @PortraitLandscape
    @ScreenRecord // b/215672979
    public void testConfigCancelled() throws Throwable {
        runTest(false);
    }


    /**
     * @param acceptConfig accept the config activity
     */
    private void runTest(boolean acceptConfig) throws Throwable {
        clearHomescreen();
        mDevice.pressHome();

        final Widgets widgets = mLauncher.getWorkspace().openAllWidgets();

        // Drag widget to homescreen
        WidgetConfigStartupMonitor monitor = new WidgetConfigStartupMonitor();
        WidgetResizeFrame resizeFrame =
                widgets.getWidget(mWidgetInfo.getLabel(mTargetContext.getPackageManager()))
                        .dragConfigWidgetToWorkspace(acceptConfig);
        // Widget id for which the config activity was opened
        mWidgetId = monitor.getWidgetId();

        // Verify that the widget id is valid and bound
        assertNotNull(mAppWidgetManager.getAppWidgetInfo(mWidgetId));

        if (acceptConfig) {
            assertNotNull("Widget resize frame not shown after widget added", resizeFrame);
            resizeFrame.dismiss();

            final Widget widget =
                    mLauncher.getWorkspace().tryGetWidget(mWidgetInfo.label, DEFAULT_UI_TIMEOUT);
            assertNotNull("Widget not found on the workspace", widget);
        } else {
            final Widget widget =
                    mLauncher.getWorkspace().tryGetWidget(mWidgetInfo.label, DEFAULT_UI_TIMEOUT);
            assertNull("Widget unexpectedly found on the workspace", widget);
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
