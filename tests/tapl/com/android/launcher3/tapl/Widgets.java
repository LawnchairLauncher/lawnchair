/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.tapl;

import static com.android.launcher3.tapl.LauncherInstrumentation.WAIT_TIME_MS;
import static com.android.launcher3.tapl.LauncherInstrumentation.log;

import android.annotation.Nullable;
import android.graphics.Rect;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.Collection;

/**
 * All widgets container.
 */
public final class Widgets extends LauncherInstrumentation.VisibleContainer
        implements KeyboardQuickSwitchSource {
    private static final int FLING_STEPS = 10;
    private static final int SCROLL_ATTEMPTS = 60;

    Widgets(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
    }

    @Override
    public LauncherInstrumentation getLauncher() {
        return mLauncher;
    }

    @Override
    public LauncherInstrumentation.ContainerType getStartingContainerType() {
        return getContainerType();
    }

    @Override
    public boolean isHomeState() {
        return true;
    }

    /**
     * Flings forward (down) and waits the fling's end.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to fling forward in widgets")) {
            log("Widgets.flingForward enter");
            final UiObject2 widgetsContainer = verifyActiveContainer();
            mLauncher.scroll(
                    widgetsContainer,
                    Direction.DOWN,
                    new Rect(0, 0, 0,
                            mLauncher.getBottomGestureMarginInContainer(widgetsContainer) + 1),
                    FLING_STEPS, false);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("flung forward")) {
                verifyActiveContainer();
            }
            log("Widgets.flingForward exit");
        }
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "want to fling backwards in widgets")) {
            log("Widgets.flingBackward enter");
            final UiObject2 widgetsContainer = verifyActiveContainer();
            mLauncher.scroll(
                    widgetsContainer,
                    Direction.UP,
                    new Rect(0, 0, 0,
                            mLauncher.getBottomGestureMarginInContainer(widgetsContainer) + 1),
                    FLING_STEPS, false);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("flung back")) {
                verifyActiveContainer();
            }
            log("Widgets.flingBackward exit");
        }
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.WIDGETS;
    }

    private int getWidgetsScroll() {
        return mLauncher.getTestInfo(
                TestProtocol.REQUEST_WIDGETS_SCROLL_Y)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    /** Get widget with supplied text. */
    public Widget getWidget(String labelText) {
        return getWidget(labelText, null);
    }

    /** Get widget with supplied text and app package */
    public Widget getWidget(String labelText, @Nullable String testAppWidgetPackage) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "getting widget " + labelText + " in widgets list")) {
            final UiObject2 searchBar = findSearchBar();
            final int searchBarHeight = searchBar.getVisibleBounds().height();
            final UiObject2 fullWidgetsPicker = verifyActiveContainer();
            mLauncher.assertTrue("Widgets container didn't become scrollable",
                    fullWidgetsPicker.wait(Until.scrollable(true), WAIT_TIME_MS));

            final UiObject2 widgetsContainer =
                    findTestAppWidgetsTableContainer(testAppWidgetPackage);
            mLauncher.assertTrue("Can't locate widgets list for the test app: "
                            + mLauncher.getLauncherPackageName(),
                    widgetsContainer != null);
            final BySelector labelSelector = By.clazz("android.widget.TextView").text(labelText);
            final BySelector previewSelector = By.res(mLauncher.getLauncherPackageName(),
                    "widget_preview");
            final int bottomGestureStartOnScreen = mLauncher.getBottomGestureStartOnScreen();
            int i = 0;
            for (; ; ) {
                final Collection<UiObject2> tableRows = mLauncher.getChildren(widgetsContainer);
                for (UiObject2 row : tableRows) {
                    final Collection<UiObject2> widgetCells = mLauncher.getChildren(row);
                    for (UiObject2 widget : widgetCells) {
                        final UiObject2 label = mLauncher.findObjectInContainer(widget,
                                labelSelector);
                        if (label == null) {
                            continue;
                        }
                        if (widget.getVisibleCenter().y >= bottomGestureStartOnScreen) {
                            continue;
                        }
                        mLauncher.assertEquals(
                                "View is not WidgetCell",
                                "com.android.launcher3.widget.WidgetCell",
                                widget.getClassName());
                        UiObject2 preview = mLauncher.waitForObjectInContainer(widget,
                                previewSelector);
                        return new Widget(mLauncher, preview);
                    }
                }

                mLauncher.assertTrue("Too many attempts", ++i <= SCROLL_ATTEMPTS);
                final int scroll = getWidgetsScroll();
                mLauncher.scrollDownByDistance(fullWidgetsPicker, searchBarHeight);
                final int newScroll = getWidgetsScroll();
                mLauncher.assertTrue(
                        "Scrolled in a wrong direction in Widgets: from " + scroll + " to "
                                + newScroll, newScroll >= scroll);
                mLauncher.assertTrue("Unable to scroll to the widget", newScroll != scroll);
            }
        }
    }

    private UiObject2 findSearchBar() {
        final BySelector searchBarContainerSelector = By.res(mLauncher.getLauncherPackageName(),
                "search_and_recommendations_container");
        final BySelector searchBarSelector = By.res(mLauncher.getLauncherPackageName(),
                "widgets_search_bar");
        final UiObject2 searchBarContainer = mLauncher.waitForLauncherObject(
                searchBarContainerSelector);
        UiObject2 searchBar = mLauncher.waitForObjectInContainer(searchBarContainer,
                searchBarSelector);
        return searchBar;
    }

    /**
     * Finds the widgets list of this test app or supplied test app package from the collapsed full
     * widgets picker.
     */
    private UiObject2 findTestAppWidgetsTableContainer(@Nullable String testAppWidgetPackage) {
        final BySelector headerSelector = By.res(mLauncher.getLauncherPackageName(),
                "widgets_list_header");
        final BySelector widgetPickerSelector = By.res(mLauncher.getLauncherPackageName(),
                "container");

        String packageName =  mLauncher.getContext().getPackageName();
        final BySelector targetAppSelector = By
                .clazz("android.widget.TextView")
                .text((testAppWidgetPackage == null || testAppWidgetPackage.isEmpty())
                                ? packageName
                                : testAppWidgetPackage);
        final BySelector widgetsContainerSelector = By.res(mLauncher.getLauncherPackageName(),
                "widgets_table");

        boolean hasHeaderExpanded = false;
        int scrollDistance = 0;
        for (int i = 0; i < SCROLL_ATTEMPTS; i++) {
            UiObject2 widgetPicker = mLauncher.waitForLauncherObject(widgetPickerSelector);
            UiObject2 widgetListView = verifyActiveContainer();
            UiObject2 header = mLauncher.waitForObjectInContainer(widgetListView,
                    headerSelector);
            // If a header is barely visible in the bottom edge of the screen, its height could be
            // too small for a scroll gesture. Since all header should have roughly the same height,
            // let's pick the max height we have seen so far.
            scrollDistance = Math.max(scrollDistance, header.getVisibleBounds().height());

            // Look for a header that has the test app name.
            UiObject2 headerTitle = mLauncher.findObjectInContainer(widgetListView,
                    targetAppSelector);
            if (headerTitle != null) {
                // If we find the header and it has not been expanded, let's click it to see the
                // widgets list. Note that we wait until the header is out of the gesture region at
                // the bottom of the screen, because tapping there in Launcher3 causes NexusLauncher
                // to briefly appear to handle the gesture, which can break our test.
                boolean isHeaderOutOfGestureRegion = headerTitle.getVisibleCenter().y
                        < mLauncher.getBottomGestureStartOnScreen();
                if (!hasHeaderExpanded && isHeaderOutOfGestureRegion) {
                    log("Header has not been expanded. Click to expand.");
                    hasHeaderExpanded = true;
                    mLauncher.clickLauncherObject(headerTitle);
                }

                // If we are in a tablet in landscape mode then we will have a two pane view and we
                // use the right pane to display the widgets table.
                UiObject2 rightPane = mLauncher.findObjectInContainer(
                        widgetPicker,
                        widgetsContainerSelector);

                // Look for a widgets list.
                UiObject2 widgetsContainer = mLauncher.findObjectInContainer(
                        rightPane != null ? rightPane : widgetListView,
                        widgetsContainerSelector);
                if (widgetsContainer != null) {
                    log("Widgets container found.");
                    return widgetsContainer;
                }
            }
            log("Finding test widget package - scroll with distance: " + scrollDistance);

            // If we are in a tablet in landscape mode then we will have a two pane view and we use
            // the right pane to display the widgets table.
            UiObject2 rightPane = mLauncher.findObjectInContainer(
                    widgetPicker,
                    widgetsContainerSelector);

            mLauncher.scrollDownByDistance(hasHeaderExpanded && rightPane != null
                    ? rightPane
                    : widgetListView, scrollDistance);
        }

        return null;
    }
}
