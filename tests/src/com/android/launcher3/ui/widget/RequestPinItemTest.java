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
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace.ItemOperator;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.testcomponent.AppWidgetNoConfig;
import com.android.launcher3.testcomponent.AppWidgetWithConfig;
import com.android.launcher3.testcomponent.RequestPinItemActivity;
import com.android.launcher3.ui.LauncherInstrumentationTestCase;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.SimpleActivityMonitor;
import com.android.launcher3.util.Wait;
import com.android.launcher3.widget.WidgetCell;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Test to verify pin item request flow.
 */
@LargeTest
public class RequestPinItemTest  extends LauncherInstrumentationTestCase {

    private SimpleActivityMonitor mActivityMonitor;
    private MainThreadExecutor mMainThreadExecutor;

    private String mCallbackAction;
    private String mShortcutId;
    private int mAppWidgetId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        grantWidgetPermission();
        setDefaultLauncher();

        mActivityMonitor = new SimpleActivityMonitor();
        ((Application) getInstrumentation().getTargetContext().getApplicationContext())
                .registerActivityLifecycleCallbacks(mActivityMonitor);
        mMainThreadExecutor = new MainThreadExecutor();

        mCallbackAction = UUID.randomUUID().toString();
        mShortcutId = UUID.randomUUID().toString();
    }

    @Override
    protected void tearDown() throws Exception {
        ((Application) getInstrumentation().getTargetContext().getApplicationContext())
                .unregisterActivityLifecycleCallbacks(mActivityMonitor);
        super.tearDown();
    }

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
        if (!Utilities.isAtLeastO()) {
            return;
        }
        lockRotation(true);

        clearHomescreen();
        startLauncher();

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
        mTargetContext.startActivity(getHomeIntent());
        assertTrue(Wait.atMost(new ItemSearchCondition(itemMatcher), DEFAULT_ACTIVITY_TIMEOUT));
    }

    /**
     * Condition for for an item
     */
    private class ItemSearchCondition extends Condition implements Callable<Boolean> {

        private final ItemOperator mOp;

        ItemSearchCondition(ItemOperator op) {
            mOp = op;
        }

        @Override
        public boolean isTrue() throws Throwable {
            return mMainThreadExecutor.submit(this).get();
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
            return launcher.getWorkspace().getFirstMatch(mOp) != null;
        }
    }
}
