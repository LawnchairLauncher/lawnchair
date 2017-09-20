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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.view.View;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace.ItemOperator;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.testcomponent.AppWidgetNoConfig;
import com.android.launcher3.testcomponent.AppWidgetWithConfig;
import com.android.launcher3.testcomponent.RequestPinItemActivity;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.LauncherActivityRule;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.widget.WidgetCell;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Test to verify pin item request flow.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class RequestPinItemTest  extends AbstractLauncherUiTest {

    @Rule public LauncherActivityRule mActivityMonitor = new LauncherActivityRule();
    @Rule public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grandWidgetBind();
    @Rule public ShellCommandRule mDefaultLauncherRule = ShellCommandRule.setDefaultLauncher();

    private String mCallbackAction;
    private String mShortcutId;
    private int mAppWidgetId;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCallbackAction = UUID.randomUUID().toString();
        mShortcutId = UUID.randomUUID().toString();
    }

    @Test
    public void testPinWidgetNoConfig() throws Throwable {
        runTest("pinWidgetNoConfig", true, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                        ((LauncherAppWidgetInfo) info).providerName.getClassName()
                                .equals(AppWidgetNoConfig.class.getName());
            }
        });
    }

    @Test
    public void testPinWidgetNoConfig_customPreview() throws Throwable {
        // Command to set custom preview
        Intent command =  RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setRemoteViewColor").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", Color.RED);

        runTest("pinWidgetNoConfig", true, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                        ((LauncherAppWidgetInfo) info).providerName.getClassName()
                                .equals(AppWidgetNoConfig.class.getName());
            }
        }, command);
    }

    @Test
    public void testPinWidgetWithConfig() throws Throwable {
        runTest("pinWidgetWithConfig", true, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                        ((LauncherAppWidgetInfo) info).providerName.getClassName()
                                .equals(AppWidgetWithConfig.class.getName());
            }
        });
    }

    @Test
    public void testPinShortcut() throws Throwable {
        // Command to set the shortcut id
        Intent command = RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setShortcutId").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", mShortcutId);

        runTest("pinShortcut", false, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof ShortcutInfo &&
                        info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT &&
                        ShortcutKey.fromItemInfo(info).getId().equals(mShortcutId);
            }
        }, command);
    }

    private void runTest(String activityMethod, boolean isWidget, ItemOperator itemMatcher,
            Intent... commandIntents) throws Throwable {
        if (!Utilities.ATLEAST_OREO) {
            return;
        }
        lockRotation(true);

        clearHomescreen();
        mActivityMonitor.startLauncher();

        // Open all apps and wait for load complete
        final UiObject2 appsContainer = openAllApps();
        assertTrue(Wait.atMost(Condition.minChildCount(appsContainer, 2), DEFAULT_UI_TIMEOUT));

        // Open Pin item activity
        BlockingBroadcastReceiver openMonitor = new BlockingBroadcastReceiver(
                RequestPinItemActivity.class.getName());
        scrollAndFind(appsContainer, By.text("Test Pin Item")).click();
        assertNotNull(openMonitor.blockingGetExtraIntent());

        // Set callback
        PendingIntent callback = PendingIntent.getBroadcast(mTargetContext, 0,
                new Intent(mCallbackAction), PendingIntent.FLAG_ONE_SHOT);
        mTargetContext.sendBroadcast(RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setCallback").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", callback));

        for (Intent command : commandIntents) {
            mTargetContext.sendBroadcast(command);
        }

        // call the requested method to start the flow
        mTargetContext.sendBroadcast(RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, activityMethod));
        UiObject2 widgetCell = mDevice.wait(
                Until.findObject(By.clazz(WidgetCell.class)), DEFAULT_ACTIVITY_TIMEOUT);
        assertNotNull(widgetCell);

        // Accept confirmation:
        BlockingBroadcastReceiver resultReceiver = new BlockingBroadcastReceiver(mCallbackAction);
        mDevice.wait(Until.findObject(By.text(mTargetContext.getString(
                R.string.place_automatically).toUpperCase())), DEFAULT_UI_TIMEOUT).click();
        Intent result = resultReceiver.blockingGetIntent();
        assertNotNull(result);
        mAppWidgetId = result.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (isWidget) {
            assertNotSame(-1, mAppWidgetId);
        }

        // Go back to home
        mActivityMonitor.returnToHome();
        assertTrue(Wait.atMost(new ItemSearchCondition(itemMatcher), DEFAULT_ACTIVITY_TIMEOUT));
    }

    /**
     * Condition for for an item
     */
    private class ItemSearchCondition extends Condition {

        private final ItemOperator mOp;

        ItemSearchCondition(ItemOperator op) {
            mOp = op;
        }

        @Override
        public boolean isTrue() throws Throwable {
            return mMainThreadExecutor.submit(mActivityMonitor.itemExists(mOp)).get();
        }
    }
}
