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

    static BySelector getAppIconSelector(String appName, LauncherInstrumentation launcher) {
        return By.clazz(TextView.class).desc(makeMultilinePattern(appName))
                .pkg(launcher.getLauncherPackageName());
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
     * Create a regular expression pattern that matches strings starting with the app name, where
     * spaces in the app name are replaced with zero or more occurrences of the "\s" character
     * (which represents a whitespace character in regular expressions), followed by any characters
     * after the app name.
     */
    static Pattern makeMultilinePattern(String appName) {
        return Pattern.compile(appName.replaceAll("\\s+", "\\\\s*") + ".*",
                Pattern.DOTALL);
    }
}
