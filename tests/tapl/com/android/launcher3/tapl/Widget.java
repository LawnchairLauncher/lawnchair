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

import com.android.launcher3.testing.TestProtocol;

import java.util.regex.Pattern;

/**
 * Widget in workspace or a widget list.
 */
public final class Widget extends Launchable implements WorkspaceDragSource {

    private static final Pattern LONG_CLICK_EVENT = Pattern.compile("Widgets.onLongClick");

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
        return dragWidgetToWorkspace(/* configurable= */ false, /* acceptsConfig= */ false);
    }

    /**
     * Drags a configurable widget from the widgets container to the workspace, either accepts or
     * cancels the configuration based on {@code acceptsConfig}, and returns the resize frame that
     * is shown if the widget is added.
     */
    @Nullable
    public WidgetResizeFrame dragConfigWidgetToWorkspace(boolean acceptsConfig) {
        return dragWidgetToWorkspace(/* configurable= */ true, acceptsConfig);
    }

    /**
     * Drags a widget from the widgets container to the workspace and returns the resize frame that
     * is shown after the widget is added.
     *
     * <p> If {@code configurable} is true, then either accepts or cancels the configuration based
     * on {@code acceptsConfig}.
     */
    @Nullable
    private WidgetResizeFrame dragWidgetToWorkspace(
            boolean configurable, boolean acceptsConfig) {
        dragToWorkspace(/* startsActivity= */ configurable, /* isWidgetShortcut= */ false);

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
