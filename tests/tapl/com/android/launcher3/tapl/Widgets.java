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

import static org.junit.Assert.fail;

import android.graphics.Point;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.ResourceUtils;

/**
 * All widgets container.
 */
public final class Widgets extends LauncherInstrumentation.VisibleContainer {
    private static final int FLING_SPEED = 1500;

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
            widgetsContainer.setGestureMargins(0, 0, 0,
                    ResourceUtils.getNavbarSize(ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE,
                            mLauncher.getResources()) + 1);
            widgetsContainer.fling(Direction.DOWN,
                    (int) (FLING_SPEED * mLauncher.getDisplayDensity()));
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("flung forward")) {
                verifyActiveContainer();
            }
            LauncherInstrumentation.log("Widgets.flingForward exit");
        }
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to fling backwards in widgets")) {
            LauncherInstrumentation.log("Widgets.flingBackward enter");
            final UiObject2 widgetsContainer = verifyActiveContainer();
            widgetsContainer.setGestureMargin(100);
            widgetsContainer.fling(Direction.UP,
                    (int) (FLING_SPEED * mLauncher.getDisplayDensity()));
            mLauncher.waitForIdle();
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

    public Widget getWidget(String label) {
        final int margin = ResourceUtils.getNavbarSize(
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, mLauncher.getResources()) + 1;
        final UiObject2 widgetsContainer = verifyActiveContainer();
        widgetsContainer.setGestureMargins(0, 0, 0, margin);

        final Point displaySize = mLauncher.getRealDisplaySize();

        int i = 0;
        final BySelector selector = By.
                clazz("com.android.launcher3.widget.WidgetCell").
                hasDescendant(By.text(label));

        for (; ; ) {
            final UiObject2 widget = mLauncher.tryWaitForLauncherObject(selector, 300);
            if (widget != null && widget.getVisibleBounds().bottom <= displaySize.y - margin) {
                return new Widget(mLauncher, widget);
            }
            if (++i > 40) fail("Too many attempts");
            widgetsContainer.scroll(Direction.DOWN, 1f);
        }
    }
}
