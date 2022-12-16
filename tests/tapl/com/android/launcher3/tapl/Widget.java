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
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

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
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            return dragWidgetToWorkspace(/* configurable= */ false, /* acceptsConfig= */ false, -1,
                    -1);
        }
    }

    /**
     * Drags a non-configurable widget from the widgets container to the workspace at cellX and
     * cellY and returns the resize frame that is shown after the widget is added.
     */
    @NonNull
    public WidgetResizeFrame dragWidgetToWorkspace(int cellX, int cellY) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "Dragging widget to workspace cell " + cellX + "," + cellY)) {
            return dragWidgetToWorkspace(/* configurable= */ false, /* acceptsConfig= */ false,
                    cellX, cellY);
        }
    }

    /**
     * Drags a configurable widget from the widgets container to the workspace, either accepts or
     * cancels the configuration based on {@code acceptsConfig}, and returns the resize frame that
     * is shown if the widget is added.
     */
    @Nullable
    public WidgetResizeFrame dragConfigWidgetToWorkspace(boolean acceptsConfig) {
        // TODO(b/239438337, fransebas) add correct event checking for this case
        //try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
        return dragWidgetToWorkspace(/* configurable= */ true, acceptsConfig, -1, -1);
        //}
    }

    /**
     * Drags an object to the center of homescreen.
     *
     * @param startsActivity   whether it's expected to start an activity.
     * @param isWidgetShortcut whether we drag a widget shortcut
     * @param cellX            X position in the CellLayout
     * @param cellY            Y position in the CellLayout
     */
    private void dragToWorkspace(boolean startsActivity, boolean isWidgetShortcut, int cellX,
            int cellY) {
        Launchable launchable = getLaunchable();
        LauncherInstrumentation launcher = launchable.mLauncher;
        Workspace.dragIconToWorkspace(
                launcher,
                launchable,
                () -> Workspace.getCellCenter(launchable.mLauncher, cellX, cellY),
                startsActivity,
                isWidgetShortcut,
                launchable::addExpectedEventsForLongClick);

    }

    /**
     * Drags a widget from the widgets container to the workspace and returns the resize frame that
     * is shown after the widget is added.
     *
     * <p> If {@code configurable} is true, then either accepts or cancels the configuration based
     * on {@code acceptsConfig}.
     * <p> If either {@code cellX} or {@code cellY} are negative, then a default location would be
     * chosen
     *
     * @param configurable  if the widget has a configuration activity.
     * @param acceptsConfig if the widget has a configuration, then if we should accept it or
     *                      cancel it
     * @param cellX         X position to drop the widget in the workspace
     * @param cellY         Y position to drop the widget in the workspace
     * @return returns the given resize frame of the widget after being dropped, if
     * configurable is true and acceptsConfig is false then the widget would not be places and will
     * be cancel and it returns null.
     */
    @Nullable
    private WidgetResizeFrame dragWidgetToWorkspace(boolean configurable, boolean acceptsConfig,
            int cellX, int cellY) {
        if (cellX == -1 || cellY == -1) {
            internalDragToWorkspace(/* startsActivity= */ configurable, /* isWidgetShortcut= */
                    false);
        } else {
            dragToWorkspace(/* startsActivity= */ configurable, /* isWidgetShortcut= */ false,
                    cellX, cellY);
        }

        if (configurable) {
            // Configure the widget.
            BySelector selector = By.text(acceptsConfig ? "OK" : "Cancel");
            mLauncher.getDevice()
                    .wait(Until.findObject(selector), LauncherInstrumentation.WAIT_TIME_MS)
                    .click();

            // If the widget configuration was cancelled, then the widget wasn't added to the home
            // screen. In that case, we cannot return a resize frame.
            if (!acceptsConfig) {
                return null;
            }
        }

        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get widget resize frame")) {
            return new WidgetResizeFrame(mLauncher);
        }
    }
}
