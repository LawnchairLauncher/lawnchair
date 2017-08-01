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

import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.view.View;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.Workspace.ItemOperator;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.LauncherActivityRule;
import com.android.launcher3.util.rule.ShellCommandRule;
import com.android.launcher3.widget.WidgetCell;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * Test to add widget from widget tray
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AddWidgetTest extends AbstractLauncherUiTest {

    @Rule public LauncherActivityRule mActivityMonitor = new LauncherActivityRule();
    @Rule public ShellCommandRule mGrantWidgetRule = ShellCommandRule.grandWidgetBind();

    @Test
    public void testDragIcon_portrait() throws Throwable {
        lockRotation(true);
        performTest();
    }

    @Test
    public void testDragIcon_landscape() throws Throwable {
        lockRotation(false);
        performTest();
    }

    private void performTest() throws Throwable {
        clearHomescreen();
        mActivityMonitor.startLauncher();

        final LauncherAppWidgetProviderInfo widgetInfo =
                findWidgetProvider(false /* hasConfigureScreen */);

        // Open widget tray and wait for load complete.
        final UiObject2 widgetContainer = openWidgetsTray();
        assertTrue(Wait.atMost(Condition.minChildCount(widgetContainer, 2), DEFAULT_UI_TIMEOUT));

        // Drag widget to homescreen
        UiObject2 widget = scrollAndFind(widgetContainer, By.clazz(WidgetCell.class)
                .hasDescendant(By.text(widgetInfo.getLabel(mTargetContext.getPackageManager()))));
        dragToWorkspace(widget, false);

        assertTrue(mActivityMonitor.itemExists(new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).providerName.equals(widgetInfo.provider);
            }
        }).call());
    }
}
