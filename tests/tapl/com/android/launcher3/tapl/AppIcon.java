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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.regex.Pattern;

/**
 * App icon, whether in all apps, workspace or the taskbar.
 */
public abstract class AppIcon extends Launchable {

    AppIcon(LauncherInstrumentation launcher, UiObject2 icon) {
        super(launcher, icon);
    }

    /**
     * Find an app icon with the given name.
     *
     * @param appName app icon to look for
     */
    static BySelector getAppIconSelector(String appName) {
        // focusable=true to avoid matching folder labels
        return By.clazz(TextView.class).text(makeMultilinePattern(appName)).focusable(true);
    }

    /**
     * Find an app icon with the given name.
     *
     * @param appName  app icon to look for
     * @param launcher (optional) - only match ui elements from Launcher's package
     */
    static BySelector getAppIconSelector(String appName, LauncherInstrumentation launcher) {
        return getAppIconSelector(appName).pkg(launcher.getLauncherPackageName());
    }

    static BySelector getMenuItemSelector(String text, LauncherInstrumentation launcher) {
        return By.clazz(TextView.class).text(text).pkg(launcher.getLauncherPackageName());
    }

    static BySelector getAnyAppIconSelector() {
        return By.clazz(TextView.class);
    }

    protected abstract Pattern getLongClickEvent();

    /**
     * Long-clicks the icon to open its menu.
     */
    public AppIconMenu openMenu() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            return createMenu(mLauncher.clickAndGet(
                    mObject, /* resName= */ "popup_container", getLongClickEvent()));
        }
    }

    /**
     * Long-clicks the icon to open its menu, and looks at the deep shortcuts container only.
     */
    public AppIconMenu openDeepShortcutMenu() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            return createMenu(mLauncher.clickAndGet(
                    mObject, /* resName= */ "deep_shortcuts_container", getLongClickEvent()));
        }
    }

    protected abstract AppIconMenu createMenu(UiObject2 menu);

    @Override
    protected void addExpectedEventsForLongClick() {
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, getLongClickEvent());
    }

    @Override
    protected void waitForLongPressConfirmation() {
        mLauncher.waitForLauncherObject("popup_container");
    }

    @Override
    protected void expectActivityStartEvents() {
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LauncherInstrumentation.EVENT_START);
    }

    @Override
    protected String launchableType() {
        return "app icon";
    }

    /** Return the app name of a icon */
    @NonNull
    public String getIconName() {
        return getObject().getText();
    }

    /**
     * Return the app name of a icon by the content description. This should be used when trying to
     * get the name of an app where the text of it is multiline.
     */
    @NonNull
    String getAppName() {
        return getObject().getContentDescription();
    }

    /**
     * @return the center coordinates of the icon
     */
    @NonNull
    public Point getVisibleCenter() {
        return getObject().getVisibleCenter();
    }

    /**
     * Create a regular expression pattern that matches strings containing all of the non-whitespace
     * characters of the app name, with any amount of whitespace added between characters (e.g.
     * newline for multiline app labels).
     */
    static Pattern makeMultilinePattern(String appName) {
        // Remove any existing whitespace.
        appName = appName.replaceAll("\\s", "");
        // Allow whitespace between characters, e.g. newline for 2 line app label.
        StringBuilder regexBuldier = new StringBuilder("\\s*");
        appName.chars().forEach(letter -> regexBuldier.append((char) letter).append("\\s*"));
        return Pattern.compile(regexBuldier.toString());
    }
}
