/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.ui.widget;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.celllayout.FavoriteItemsTransaction;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.testcomponent.WidgetConfigActivity;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.util.BaseLauncherActivityTest;
import com.android.launcher3.util.BlockingBroadcastReceiver;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.picker.WidgetsFullSheet;
import com.android.launcher3.widget.picker.WidgetsListAdapter;
import com.android.launcher3.widget.picker.WidgetsRecyclerView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to verify widget configuration is properly shown.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AddConfigWidgetTest extends BaseLauncherActivityTest<Launcher> {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

    private LauncherAppWidgetProviderInfo mWidgetInfo;
    private AppWidgetManager mAppWidgetManager;

    private int mWidgetId;

    @Before
    public void setUp() throws Exception {
        mWidgetInfo = TestViewHelpers.findWidgetProvider(true /* hasConfigureScreen */);
        mAppWidgetManager = AppWidgetManager.getInstance(targetContext());
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
        new FavoriteItemsTransaction(targetContext()).commit();
        loadLauncherSync();

        // Add widget to homescreen
        WidgetConfigStartupMonitor monitor = new WidgetConfigStartupMonitor();
        executeOnLauncher(OptionsPopupView::openWidgets);
        uiDevice.waitForIdle();

        // Select the widget header
        Context testContext = getInstrumentation().getContext();
        String packageName = testContext.getPackageName();
        executeOnLauncher(l -> {
            WidgetsRecyclerView wrv = WidgetsFullSheet.getWidgetsView(l);
            WidgetsListAdapter adapter = (WidgetsListAdapter) wrv.getAdapter();
            int pos = adapter.getItems().indexOf(
                    adapter.getItems().stream()
                            .filter(entry -> packageName.equals(entry.mPkgItem.packageName))
                            .findFirst()
                            .get());
            wrv.getLayoutManager().scrollToPosition(pos);
            adapter.onHeaderClicked(true, new PackageUserKey(packageName, Process.myUserHandle()));
        });
        uiDevice.waitForIdle();

        View widgetView = getOnceNotNull("Widget not found", l -> searchView(l.getDragLayer(), v ->
                v instanceof WidgetCell
                        && v.getTag() instanceof PendingAddWidgetInfo pawi
                        && mWidgetInfo.provider.equals(pawi.componentName)));
        addWidgetToWorkspace(widgetView);

        // Widget id for which the config activity was opened
        mWidgetId = monitor.getWidgetId();

        // Verify that the widget id is valid and bound
        assertNotNull(mAppWidgetManager.getAppWidgetInfo(mWidgetId));
        setResult(acceptConfig);

        if (acceptConfig) {
            getOnceNotNull("Widget was not added", l -> {
                // Close the resize frame before searching for widget
                AbstractFloatingView.closeAllOpenViews(l);
                return l.getWorkspace().getFirstMatch(new WidgetSearchCondition());
            });
            assertNotNull(mAppWidgetManager.getAppWidgetInfo(mWidgetId));
        } else {
            // Verify that the widget id is deleted.
            Wait.atMost("", () -> mAppWidgetManager.getAppWidgetInfo(mWidgetId) == null);
        }
    }

    private void setResult(boolean success) {
        getInstrumentation().getTargetContext().sendBroadcast(
                WidgetConfigActivity.getCommandIntent(WidgetConfigActivity.class,
                        success ? "clickOK" : "clickCancel"));
        uiDevice.waitForIdle();
    }

    /**
     * Condition for searching widget id
     */
    private class WidgetSearchCondition implements ItemOperator {

        @Override
        public boolean evaluate(ItemInfo info, View view) {
            return info instanceof LauncherAppWidgetInfo lawi
                    && lawi.providerName.equals(mWidgetInfo.provider)
                    && lawi.appWidgetId == mWidgetId;
        }
    }

    /**
     * Broadcast receiver for receiving widget config activity status.
     */
    private static class WidgetConfigStartupMonitor extends BlockingBroadcastReceiver {

        WidgetConfigStartupMonitor() {
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
