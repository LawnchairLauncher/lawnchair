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

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.celllayout.FavoriteItemsTransaction;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.tapl.AddToHomeScreenPrompt;
import com.android.launcher3.testcomponent.AppWidgetNoConfig;
import com.android.launcher3.testcomponent.AppWidgetWithConfig;
import com.android.launcher3.testcomponent.RequestPinItemActivity;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.Wait.Condition;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/**
 * Test to verify pin item request flow.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class TaplRequestPinItemTest extends AbstractLauncherUiTest {

    @Rule
    public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grantWidgetBind();

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
    public void testEmpty() throws Throwable { /* needed while the broken tests are being fixed */ }

    @Test
    public void testPinWidgetNoConfig() throws Throwable {
        runTest("pinWidgetNoConfig", true, (info, view) -> info instanceof LauncherAppWidgetInfo &&
                ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                ((LauncherAppWidgetInfo) info).providerName.getClassName()
                        .equals(AppWidgetNoConfig.class.getName()));
    }

    @Test
    public void testPinWidgetNoConfig_customPreview() throws Throwable {
        // Command to set custom preview
        Intent command = RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setRemoteViewColor").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", Color.RED);

        runTest("pinWidgetNoConfig", true, (info, view) -> info instanceof LauncherAppWidgetInfo &&
                ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                ((LauncherAppWidgetInfo) info).providerName.getClassName()
                        .equals(AppWidgetNoConfig.class.getName()), command);
    }

    @Test
    public void testPinWidgetWithConfig() throws Throwable {
        runTest("pinWidgetWithConfig", true,
                (info, view) -> info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                        ((LauncherAppWidgetInfo) info).providerName.getClassName()
                                .equals(AppWidgetWithConfig.class.getName()));
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
                return info instanceof WorkspaceItemInfo &&
                        info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT &&
                        ShortcutKey.fromItemInfo(info).getId().equals(mShortcutId);
            }
        }, command);
    }

    private void runTest(String activityMethod, boolean isWidget, ItemOperator itemMatcher,
            Intent... commandIntents) throws Throwable {
        commitTransactionAndLoadHome(new FavoriteItemsTransaction(mTargetContext));

        // Open Pin item activity
        BlockingBroadcastReceiver openMonitor = new BlockingBroadcastReceiver(
                RequestPinItemActivity.class.getName());
        mLauncher.
                getWorkspace().
                switchToAllApps().
                getAppIcon("Test Pin Item").
                launch(getAppPackageName());
        assertNotNull(openMonitor.blockingGetExtraIntent());

        // Set callback
        PendingIntent callback = PendingIntent.getBroadcast(mTargetContext, 0,
                new Intent(mCallbackAction).setPackage(mTargetContext.getPackageName()),
                FLAG_ONE_SHOT | FLAG_MUTABLE);
        mTargetContext.sendBroadcast(RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setCallback").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", callback));

        for (Intent command : commandIntents) {
            mTargetContext.sendBroadcast(command);
        }

        // call the requested method to start the flow
        mTargetContext.sendBroadcast(RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, activityMethod));
        final AddToHomeScreenPrompt addToHomeScreenPrompt = mLauncher.getAddToHomeScreenPrompt();

        // Accept confirmation:
        BlockingBroadcastReceiver resultReceiver = new BlockingBroadcastReceiver(mCallbackAction);
        addToHomeScreenPrompt.addAutomatically();
        Intent result = resultReceiver.blockingGetIntent();
        assertNotNull(result);
        mAppWidgetId = result.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (isWidget) {
            assertNotSame(-1, mAppWidgetId);
        }

        // Go back to home
        mLauncher.goHome();
        Wait.atMost("", new ItemSearchCondition(itemMatcher), DEFAULT_ACTIVITY_TIMEOUT,
                mLauncher);
    }

    /**
     * Condition for for an item
     */
    private class ItemSearchCondition implements Condition {

        private final ItemOperator mOp;

        ItemSearchCondition(ItemOperator op) {
            mOp = op;
        }

        @Override
        public boolean isTrue() throws Throwable {
            return mMainThreadExecutor.submit(() -> {
                Launcher l = Launcher.ACTIVITY_TRACKER.getCreatedActivity();
                return l != null && l.getWorkspace().getFirstMatch(mOp) != null;
            }).get();
        }
    }
}
