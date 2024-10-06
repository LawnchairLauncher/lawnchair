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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.view.View;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.launcher3.celllayout.FavoriteItemsTransaction;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.testcomponent.WidgetConfigActivity;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.util.Wait;
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
public class TaplAddConfigWidgetTest extends AbstractLauncherUiTest<Launcher> {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private LauncherAppWidgetProviderInfo mWidgetInfo;
    private AppWidgetManager mAppWidgetManager;

    private int mWidgetId;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mWidgetInfo = TestViewHelpers.findWidgetProvider(true /* hasConfigureScreen */);
        mAppWidgetManager = AppWidgetManager.getInstance(mTargetContext);
    }

    @Test
    @PortraitLandscape
    public void testWidgetConfig() throws Throwable {
        runTest(true);
    }

    @Test
    @PortraitLandscape
    public void testConfigCancelled() throws Throwable {
        runTest(false);
    }


    /**
     * @param acceptConfig accept the config activity
     */
    private void runTest(boolean acceptConfig) throws Throwable {
        commitTransactionAndLoadHome(new FavoriteItemsTransaction(mTargetContext));

        // Drag widget to homescreen
        WidgetConfigStartupMonitor monitor = new WidgetConfigStartupMonitor();
        mLauncher.getWorkspace()
                .openAllWidgets()
                .getWidget(mWidgetInfo.getLabel(mTargetContext.getPackageManager()))
                .dragToWorkspace(true, false);
        // Widget id for which the config activity was opened
        mWidgetId = monitor.getWidgetId();

        // Verify that the widget id is valid and bound
        assertNotNull(mAppWidgetManager.getAppWidgetInfo(mWidgetId));

        setResultAndWaitForAnimation(acceptConfig);
        if (acceptConfig) {
            Wait.atMost("", new WidgetSearchCondition(), DEFAULT_ACTIVITY_TIMEOUT, mLauncher);
            assertNotNull(mAppWidgetManager.getAppWidgetInfo(mWidgetId));
        } else {
            // Verify that the widget id is deleted.
            Wait.atMost("", () -> mAppWidgetManager.getAppWidgetInfo(mWidgetId) == null,
                    DEFAULT_ACTIVITY_TIMEOUT, mLauncher);
        }
    }

    private static void setResult(boolean success) {
        getInstrumentation().getTargetContext().sendBroadcast(
                WidgetConfigActivity.getCommandIntent(WidgetConfigActivity.class,
                        success ? "clickOK" : "clickCancel"));
    }

    private void setResultAndWaitForAnimation(boolean success) {
        if (mLauncher.isLauncher3()) {
            setResult(success);
        } else {
            mLauncher.executeAndWaitForWallpaperAnimation(
                    () -> setResult(success),
                    "setting widget coinfig result");
        }
    }

    /**
     * Condition for searching widget id
     */
    private class WidgetSearchCondition implements Wait.Condition, ItemOperator {

        @Override
        public boolean isTrue() throws Throwable {
            return mMainThreadExecutor.submit(() -> {
                Launcher l = Launcher.ACTIVITY_TRACKER.getCreatedActivity();
                return l != null && l.getWorkspace().getFirstMatch(this) != null;
            }).get();
        }

        @Override
        public boolean evaluate(ItemInfo info, View view) {
            return info instanceof LauncherAppWidgetInfo
                    && ((LauncherAppWidgetInfo) info).providerName.getClassName().equals(
                            mWidgetInfo.provider.getClassName())
                    && ((LauncherAppWidgetInfo) info).appWidgetId == mWidgetId;
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
            assertNotNull("Null EXTRA_INTENT", intent);
            assertEquals("Intent action is not ACTION_APPWIDGET_CONFIGURE",
                    AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, intent.getAction());
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    LauncherAppWidgetInfo.NO_ID);
            assertNotSame("Widget id is NO_ID", widgetId, LauncherAppWidgetInfo.NO_ID);
            return widgetId;
        }
    }
}
