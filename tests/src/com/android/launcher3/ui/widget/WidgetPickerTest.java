/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.BaseLauncherActivityTest;
import com.android.launcher3.util.rule.ScreenRecordRule;
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.widget.picker.WidgetsFullSheet;
import com.android.launcher3.widget.picker.WidgetsRecyclerView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Make sure the basic interactions with the WidgetPicker works.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WidgetPickerTest extends BaseLauncherActivityTest<Launcher> {

    @Rule
    public TestRule screenRecordRule = new ScreenRecordRule();

    private WidgetsRecyclerView getWidgetsView(Launcher launcher) {
        return WidgetsFullSheet.getWidgetsView(launcher);
    }

    private int getWidgetsScroll(Launcher launcher) {
        return getWidgetsView(launcher).computeVerticalScrollOffset();
    }

    /**
     * Open Widget picker, make sure the widget picker can scroll and then go to home screen.
     */
    @Test
    @ScreenRecord
    @PortraitLandscape
    public void testWidgets() {
        loadLauncherSync();
        // Test opening widgets.
        executeOnLauncher(launcher ->
                assertTrue("Widgets is initially opened", getWidgetsView(launcher) == null));
        assertNotNull("openAllWidgets() returned null",
                getFromLauncher(OptionsPopupView::openWidgets));
        WidgetsRecyclerView widgets = getFromLauncher(this::getWidgetsView);
        assertNotNull("getAllWidgets() returned null", widgets);
        executeOnLauncher(launcher -> assertTrue("Widgets is not shown", widgets.isShown()));
        executeOnLauncher(launcher -> assertEquals("Widgets is scrolled upon opening",
                0, getWidgetsScroll(launcher)));

        executeOnLauncher(AbstractFloatingView::closeAllOpenViews);
        uiDevice.waitForIdle();

        waitForLauncherCondition("Widgets were not closed",
                launcher -> getWidgetsView(launcher) == null);
    }
}
