/*
 * Copyright (C) 2019 The Android Open Source Project
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

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.regex.Pattern;

/**
 * Widget in workspace or a widget list.
 */
public final class Widget extends Launchable implements WorkspaceDragSource {

    static final Pattern LONG_CLICK_EVENT = Pattern.compile("Widgets.onLongClick");

    Widget(LauncherInstrumentation launcher, UiObject2 icon) {
        super(launcher, icon);
    }

    @Override
    protected void waitForLongPressConfirmation() {
        mLauncher.waitForLauncherObject("drop_target_bar");
    }

    @Override
    protected void expectActivityStartEvents() {
    }

    @Override
    protected void addExpectedEventsForLongClick() {
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT);
    }

    @Override
    protected String launchableType() {
        return "widget";
    }

    /** This method requires public access, however should not be called in tests. */
    @Override
    public Launchable getLaunchable() {
        return this;
    }

    /**
     * Drags a non-configurable widget from the widgets container to the workspace and returns the
     * resize frame that is shown after the widget is added.
     */
    @NonNull
    public WidgetResizeFrame dragWidgetToWorkspace() {
        return dragWidgetToWorkspace(-1, -1, 1, 1);
    }

    /**
     * Drags a non-configurable widget from the widgets container to the workspace at cellX and
     * cellY and returns the resize frame that is shown after the widget is added.
     */
    @NonNull
    public WidgetResizeFrame dragWidgetToWorkspace(int cellX, int cellY, int spanX, int spanY) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "Dragging widget to workspace cell " + cellX + "," + cellY)) {
            if (cellX == -1 || cellY == -1) {
                internalDragToWorkspace(/* startsActivity= */ false, /* isWidgetShortcut= */
                        false);
            } else {
                dragToWorkspaceCellPosition(/* startsActivity= */ false, /* isWidgetShortcut= */
                        false, cellX, cellY, spanX, spanY);
            }

            try (LauncherInstrumentation.Closable closable = mLauncher.addContextLayer(
                    "want to get widget resize frame")) {
                return new WidgetResizeFrame(mLauncher);
            }
        }
    }

    /**
     * Drags an object to the center of homescreen.
     *
     * @param startsActivity   whether it's expected to start an activity.
     * @param isWidgetShortcut whether we drag a widget shortcut
     * @param cellX            X position in the CellLayout
     * @param cellY            Y position in the CellLayout
     */
    private void dragToWorkspaceCellPosition(boolean startsActivity, boolean isWidgetShortcut,
            int cellX, int cellY, int spanX, int spanY) {
        Launchable launchable = getLaunchable();
        LauncherInstrumentation launcher = launchable.mLauncher;
        Workspace.dragIconToWorkspaceCellPosition(
                launcher,
                launchable,
                cellX, cellY, spanX, spanY,
                startsActivity,
                isWidgetShortcut,
                launchable::addExpectedEventsForLongClick);
    }
}
