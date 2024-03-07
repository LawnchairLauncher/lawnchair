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

import com.android.launcher3.Launcher;
import com.android.launcher3.tapl.Widgets;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.PortraitLandscapeRunner.PortraitLandscape;
import com.android.launcher3.util.rule.ScreenRecordRule.ScreenRecord;
import com.android.launcher3.widget.picker.WidgetsFullSheet;
import com.android.launcher3.widget.picker.WidgetsRecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test run in both Out of process (Oop) and in-process (Ipc).
 * Make sure the basic interactions with the WidgetPicker works.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TaplWidgetPickerTest extends AbstractLauncherUiTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        initialize(this);
    }

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
        mLauncher.goHome();
        // Test opening widgets.
        executeOnLauncher(launcher ->
                assertTrue("Widgets is initially opened", getWidgetsView(launcher) == null));
        Widgets widgets = mLauncher.getWorkspace().openAllWidgets();
        assertNotNull("openAllWidgets() returned null", widgets);
        widgets = mLauncher.getAllWidgets();
        assertNotNull("getAllWidgets() returned null", widgets);
        executeOnLauncher(launcher ->
                assertTrue("Widgets is not shown", getWidgetsView(launcher).isShown()));
        executeOnLauncher(launcher -> assertEquals("Widgets is scrolled upon opening",
                0, getWidgetsScroll(launcher)));

        // Test flinging widgets.
        widgets.flingForward();
        Integer flingForwardY = getFromLauncher(launcher -> getWidgetsScroll(launcher));
        executeOnLauncher(launcher -> assertTrue("Flinging forward didn't scroll widgets",
                flingForwardY > 0));

        widgets.flingBackward();
        executeOnLauncher(launcher -> assertTrue("Flinging backward didn't scroll widgets",
                getWidgetsScroll(launcher) < flingForwardY));

        mLauncher.goHome();
        waitForLauncherCondition("Widgets were not closed",
                launcher -> getWidgetsView(launcher) == null);
    }
}
