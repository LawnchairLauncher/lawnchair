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

import android.graphics.Point;
import android.graphics.Rect;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.ResourceUtils;

import java.util.Collection;
import java.util.Collections;

/**
 * All widgets container.
 */
public final class Widgets extends LauncherInstrumentation.VisibleContainer {
    private static final int FLING_STEPS = 10;

    Widgets(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
    }

    /**
     * Flings forward (down) and waits the fling's end.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to fling forward in widgets")) {
            LauncherInstrumentation.log("Widgets.flingForward enter");
            final UiObject2 widgetsContainer = verifyActiveContainer();
            mLauncher.scroll(
                    widgetsContainer,
                    Direction.DOWN,
                    1f,
                    new Rect(0, 0, 0, getBottomGestureMargin(widgetsContainer)),
                    FLING_STEPS);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("flung forward")) {
                verifyActiveContainer();
            }
            LauncherInstrumentation.log("Widgets.flingForward exit");
        }
    }

    private int getBottomGestureMargin(UiObject2 widgetsContainer) {
        return widgetsContainer.getVisibleBounds().bottom - mLauncher.getRealDisplaySize().y +
                getBottomGestureSize();
    }

    private int getBottomGestureSize() {
        return ResourceUtils.getNavbarSize(
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, mLauncher.getResources()) + 1;
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to fling backwards in widgets")) {
            LauncherInstrumentation.log("Widgets.flingBackward enter");
            final UiObject2 widgetsContainer = verifyActiveContainer();
            mLauncher.scroll(
                    widgetsContainer,
                    Direction.UP,
                    1f,
                    new Rect(0, 0, widgetsContainer.getVisibleBounds().width(), 0),
                    FLING_STEPS);
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("flung back")) {
                verifyActiveContainer();
            }
            LauncherInstrumentation.log("Widgets.flingBackward exit");
        }
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.WIDGETS;
    }

    public Widget getWidget(String labelText) {
        final UiObject2 widgetsContainer = verifyActiveContainer();
        final Point displaySize = mLauncher.getRealDisplaySize();
        final BySelector labelSelector = By.clazz("android.widget.TextView").text(labelText);

        int i = 0;
        for (; ; ) {
            final Collection<UiObject2> cells = mLauncher.getObjectsInContainer(
                    widgetsContainer, "widgets_cell_list_container");
            mLauncher.assertTrue("Widgets doesn't have 2 rows", cells.size() >= 2);
            for (UiObject2 cell : cells) {
                final UiObject2 label = cell.findObject(labelSelector);
                if (label == null) continue;

                final UiObject2 widget = label.getParent().getParent();
                mLauncher.assertEquals(
                        "View is not WidgetCell",
                        "com.android.launcher3.widget.WidgetCell",
                        widget.getClassName());

                if (widget.getVisibleBounds().bottom <= displaySize.y - getBottomGestureSize()) {
                    return new Widget(mLauncher, widget);
                }
            }

            mLauncher.assertTrue("Too many attempts", ++i <= 40);
            final UiObject2 lowestCell = Collections.max(cells, (c1, c2) ->
                    Integer.compare(c1.getVisibleBounds().top, c2.getVisibleBounds().top));

            final int gestureStart = lowestCell.getVisibleBounds().top + mLauncher.getTouchSlop();
            final int distance = gestureStart - widgetsContainer.getVisibleBounds().top;
            final int bottomMargin = widgetsContainer.getVisibleBounds().height() - distance;

            mLauncher.scroll(
                    widgetsContainer,
                    Direction.DOWN,
                    1f,
                    new Rect(
                            0,
                            0,
                            0,
                            Math.max(bottomMargin, getBottomGestureMargin(widgetsContainer))),
                    150);
        }
    }
}
