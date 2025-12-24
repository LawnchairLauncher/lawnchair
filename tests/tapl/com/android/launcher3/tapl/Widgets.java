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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Point;
import android.graphics.Rect;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.Collection;
import java.util.List;

/**
 * All widgets container.
 */
public final class Widgets extends LauncherInstrumentation.VisibleContainer
        implements KeyboardQuickSwitchSource {
    private static final int FLING_STEPS = 10;
    private static final int SCROLL_STEPS = 10;

    // Number of times to scroll to find a header in the widget list.
    private static final int SCROLL_ATTEMPTS_WIDGET_LIST = 60;
    // Number of times to scroll to find a widget in the widget grid.
    private static final int SCROLL_ATTEMPTS_WIDGETS_GRID = 20;

    // Distance to scroll in the widget list to find a header.
    private static final int WIDGET_LIST_SCROLL_DISTANCE = 300;
    // Distance to scroll in the widget grid to find a widget.
    private static final int WIDGET_GRID_SCROLL_DISTANCE = 150;

    // Difference between the widget preview and the label needs to be less than this threshold
    // for the widget to be considered as a match.
    private static final int WIDGET_PREVIEW_DIFF_Y_THRESHOLD = 50;

    private static final String WIDGET_PICKER_MODULE_PACKAGE = "com.android.launcher3.widgetpicker";
    private static final String WIDGET_PICKER_V2_CONTENT_RES_ID = "widgets_catalog";
    private static final BySelector BROWSE_TAB_SELECTOR = By.res(WIDGET_PICKER_MODULE_PACKAGE,
            "personal_widgets_tab");
    private static final BySelector BROWSE_WIDGETS_LIST_SELECTOR = By.res(
            WIDGET_PICKER_MODULE_PACKAGE,
            "personal_widgets_list");
    private static final BySelector WIDGETS_PREVIEW_SELECTOR = By.res(
            WIDGET_PICKER_MODULE_PACKAGE,
            "widget_preview");

    private UiObject2 mContainer;

    Widgets(LauncherInstrumentation launcher) {
        super(launcher);
        mContainer = verifyActiveContainer();
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

    public void close() {
        final Point displaySize = mLauncher.getRealDisplaySize();
        mLauncher.linearGesture(
                /*startX=*/ displaySize.x / 2,
                /*startY=*/ displaySize.y - 1,
                /*endX=*/ displaySize.x / 2,
                /*endY=*/ displaySize.y / 2,
                /*steps=*/ 100,
                /*slowDown=*/ false,
                /*gestureScope=*/ LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
        // Wait for the workspace to be visible.
        mLauncher.getWorkspace();
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
    public Widget getWidget(CharSequence labelText) {
        return getWidget(labelText.toString(), null);
    }

    /** Get widget with supplied text and app package */
    public Widget getWidget(String labelText, @Nullable String testAppWidgetPackage) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "getting widget " + labelText + " in widgets list")) {

            if (mContainer.getResourceName().contains(WIDGET_PICKER_V2_CONTENT_RES_ID)) {
                return getWidgetFromPickerV2(labelText, testAppWidgetPackage);
            }

            final UiObject2 searchBar = findSearchBar();
            final int searchBarHeight = searchBar.getVisibleBounds().height();
            final UiObject2 fullWidgetsPicker = verifyActiveContainer();

            // Widget picker may not be scrollable if there are few items. Instead of waiting on
            // picker being scrollable, we wait on widget headers to be available.
            waitForWidgetListItems(fullWidgetsPicker);

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

                mLauncher.assertTrue("Too many attempts", ++i <= SCROLL_ATTEMPTS_WIDGET_LIST);
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

    private void waitForWidgetListItems(UiObject2 fullWidgetsPicker) {
        List<UiObject2> headers = fullWidgetsPicker.wait(Until.findObjects(
                By.res(mLauncher.getLauncherPackageName(), "widgets_list_header")), WAIT_TIME_MS);
        mLauncher.assertTrue("Widgets list is not available",
                headers != null && !headers.isEmpty());
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

    /** Gets the widget from the Widget Picker variant written in Jetpack compose. */
    private Widget getWidgetFromPickerV2(String labelText, @Nullable String testAppWidgetPackage) {
        final UiObject2 browseTab =
                mLauncher.findObjectInContainer(mContainer, BROWSE_TAB_SELECTOR);
        // With just personal profile, there is no browse tab when in a large screen layout with two
        // panes.
        boolean isSinglePane = browseTab != null;

        if (browseTab != null) {
            browseTab.click();
        }

        final UiObject2 browseList = mLauncher.waitForObjectBySelector(
                BROWSE_WIDGETS_LIST_SELECTOR);

        String packageNameToFind = getPackageNameToFind(testAppWidgetPackage);
        final BySelector headerSelector =
                By.clazz("android.widget.TextView").text(packageNameToFind);
        UiObject2 header = findWidgetHeader(
                browseList,
                headerSelector,
                isSinglePane);
        mLauncher.assertTrue("Header not found", header != null);

        mLauncher.waitForIdle();
        UiObject2 headerParent = header.getParent();
        headerParent.wait(Until.clickable(true), WAIT_TIME_MS);
        headerParent.click();

        LauncherInstrumentation.log("Clicked header");

        if (isSinglePane) {
            mLauncher.waitForObjectBySelector(headerSelector);
            mLauncher.waitForObjectsBySelector(WIDGETS_PREVIEW_SELECTOR);
        } else {
            header.wait(Until.selected(true), WAIT_TIME_MS);
        }

        final UiObject2 matchedWidgetPreview = findWidget(labelText, isSinglePane);

        mLauncher.assertTrue("Widget not found " + labelText, matchedWidgetPreview != null);
        return new Widget(mLauncher, matchedWidgetPreview);
    }

    private UiObject2 findWidgetHeader(
            UiObject2 container, BySelector headerSelector, boolean isSinglePane) {
        final Rect containerRect = mLauncher.getVisibleBounds(container);
        int startX = containerRect.centerX();
        int endX = startX;
        int startY = containerRect.centerY();
        int endY = startY - WIDGET_LIST_SCROLL_DISTANCE;

        for (int i = 0; i < SCROLL_ATTEMPTS_WIDGET_LIST; i++) {
            UiObject2 matchedAppHeader = container.findObject(headerSelector);

            if (matchedAppHeader == null) {
                LauncherInstrumentation.log("[findWidgetHeader]: no match yet, scrolling");
                mLauncher.linearGesture(
                        startX,
                        startY,
                        endX,
                        endY,
                        SCROLL_STEPS,
                        /* slowDown= */ true,
                        LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
                mLauncher.waitForObjectBySelector(BROWSE_WIDGETS_LIST_SELECTOR);
            } else {
                if (isSinglePane) {
                    // Ensure header is fully visible (e.g. not occluded by browse tabs) and
                    // scroll is finished.
                    scrollAndWait(startX, startY, endX, endY);
                } else {
                    // Wait for scroll to be stabilized
                    scrollAndWait(startX, startY, endX, startY - 1);
                }
                // Return latest matching header.
                return mLauncher.waitForObjectBySelector(headerSelector);
            }
        }
        LauncherInstrumentation.log("[findWidgetHeader]: exceeded scroll attempts");
        return null;
    }

    private void scrollAndWait(int startX, int startY, int endX, int endY) {
        mLauncher.getDevice().performActionAndWait(() -> mLauncher.linearGesture(
                        startX,
                        startY,
                        endX,
                        endY,
                        SCROLL_STEPS,
                        /* slowDown= */ true,
                        LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER),
                Until.scrollFinished(Direction.DOWN), WAIT_TIME_MS);
    }

    private UiObject2 findWidget(String labelText, boolean isSinglePane) {
        final BySelector labelTextSelector =
                By.clazz("android.widget.TextView").textContains(labelText);

        for (int scrollAttempt = 0; scrollAttempt < SCROLL_ATTEMPTS_WIDGETS_GRID; scrollAttempt++) {
            final UiObject2 container;
            if (isSinglePane) {
                container = mLauncher.waitForObjectBySelector(BROWSE_WIDGETS_LIST_SELECTOR);
            } else {
                container = mContainer;
            }

            List<UiObject2> widgetPreviews = mContainer.findObjects(WIDGETS_PREVIEW_SELECTOR);

            UiObject2 label =  mLauncher.findObjectInContainer(container, labelTextSelector);

            if (label != null) {
                Point labelCenter = label.getVisibleCenter();
                int lastYDiff = Integer.MAX_VALUE;
                UiObject2 match = null;

                for (int previewIndex = 0; previewIndex < widgetPreviews.size(); previewIndex++) {
                    Point previewCenter = widgetPreviews.get(previewIndex).getVisibleCenter();
                    int diffX = Math.abs(previewCenter.x - labelCenter.x);
                    int diffY = previewCenter.y - labelCenter.y;
                    // Negative diffY means preview is above the label - which is what we want.
                    boolean isAboveLabel = diffY < 0;

                    // Pick the preview that is above the label and is closest to the label.
                    if (diffX < WIDGET_PREVIEW_DIFF_Y_THRESHOLD
                            && isAboveLabel
                            && Math.abs(diffY) < Math.abs(lastYDiff)) {
                        match = widgetPreviews.get(previewIndex);
                        lastYDiff = diffY;
                    }
                }

                if (match != null) {
                    return match;
                }
            }

            // Keep scrolling.
            LauncherInstrumentation.log("[Finding widget] did not find label, scrolling now");
            final Rect containerRect = mLauncher.getVisibleBounds(container);
            int startX = containerRect.centerX();
            int endX = startX;
            int startY = containerRect.centerY();
            int endY = startY - WIDGET_GRID_SCROLL_DISTANCE;

            mLauncher.linearGesture(
                    startX,
                    startY,
                    endX,
                    endY,
                    SCROLL_STEPS,
                    /* slowDown= */ false,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);

        }

        return null;
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

        String packageNameToFind = getPackageNameToFind(testAppWidgetPackage);
        final BySelector targetAppSelector = By
                .clazz("android.widget.TextView")
                .text(packageNameToFind);
        final BySelector expandListButtonSelector =
                By.res(mLauncher.getLauncherPackageName(), "widget_list_expand_button");
        final BySelector widgetsContainerSelector = By.res(mLauncher.getLauncherPackageName(),
                "widgets_table");

        boolean hasHeaderExpanded = false;
        // List was expanded by clicking "Show all" button.
        boolean hasListExpanded = false;

        int scrollDistance = 0;
        for (int i = 0; i < SCROLL_ATTEMPTS_WIDGET_LIST; i++) {
            UiObject2 widgetPicker = mLauncher.waitForLauncherObject(widgetPickerSelector);
            UiObject2 widgetListView = verifyActiveContainer();

            // Press "Show all" button if it exists. Otherwise, keep scrolling to
            // find the header or show all button.
            UiObject2 expandListButton =
                    mLauncher.findObjectInContainer(widgetListView, expandListButtonSelector);
            if (expandListButton != null) {
                expandListButton.click();
                hasListExpanded = true;
                i = -1;
                continue;
            }

            UiObject2 header = mLauncher.waitForObjectInContainer(widgetListView,
                    headerSelector);
            // If a header is barely visible in the bottom edge of the screen, its height could be
            // too small for a scroll gesture. Since all header should have roughly the same height,
            // let's pick the max height we have seen so far.
            scrollDistance = Math.max(scrollDistance, header.getVisibleBounds().height());

            // Look for a header that has the test app name.
            UiObject2 headerTitle = mLauncher.findObjectInContainer(widgetListView,
                    targetAppSelector);

            final UiObject2 searchBar = findSearchBar();

            // If header's title is under or above search bar, let's not process the header yet,
            // scroll a bit more to bring the header into visible area.
            if (headerTitle != null
                    && headerTitle.getVisibleCenter().y <= searchBar.getVisibleCenter().y) {
                log("Test app's header is behind the searchbar, scrolling up");
                mLauncher.scrollUpByDistance(widgetListView, scrollDistance);
                continue;
            }

            if (headerTitle != null) {
                // If we find the header and it has not been expanded, let's click it to see the
                // widgets list. Note that we wait until the header is out of the gesture region at
                // the bottom of the screen, because tapping there in Launcher3 causes NexusLauncher
                // to briefly appear to handle the gesture, which can break our test.
                boolean isHeaderOutOfGestureRegion = headerTitle.getVisibleCenter().y
                        < mLauncher.getBottomGestureStartOnScreen();

                if (!isHeaderOutOfGestureRegion) {
                    log("Test app's header is not out of gesture region, scrolling up");
                    mLauncher.scrollDownByDistance(widgetListView, scrollDistance);
                    isHeaderOutOfGestureRegion = true;
                }


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

            if (hasListExpanded && packageNameToFind.compareToIgnoreCase(
                    getFirstHeaderTitle(widgetListView)) < 0) {
                mLauncher.scrollUpByDistance(hasHeaderExpanded && rightPane != null
                        ? rightPane
                        : widgetListView, scrollDistance);
            } else {
                mLauncher.scrollDownByDistance(hasHeaderExpanded && rightPane != null
                        ? rightPane
                        : widgetListView, scrollDistance);
            }
        }

        return null;
    }

    private String getPackageNameToFind(@Nullable String testAppWidgetPackage) {
        return (testAppWidgetPackage == null || testAppWidgetPackage.isEmpty())
                ? mLauncher.getContext().getPackageName()
                : testAppWidgetPackage;
    }

    @NonNull
    private String getFirstHeaderTitle(UiObject2 widgetListView) {
        UiObject2 firstHeader = mLauncher.getObjectsInContainer(widgetListView, "app_title").get(0);
        return firstHeader != null ? firstHeader.getText() : "";
    }
}
