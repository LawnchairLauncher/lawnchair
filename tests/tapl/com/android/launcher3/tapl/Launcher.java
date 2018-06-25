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

import static com.android.systemui.shared.system.SettingsCompat.SWIPE_UP_SETTING_NAME;

import android.content.res.Resources;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The main tapl object. The only object that can be explicitly constructed by the using code. It
 * produces all other objects.
 */
public final class Launcher {

    private static final String WORKSPACE_RES_ID = "workspace";
    private static final String APPS_RES_ID = "apps_view";
    private static final String OVERVIEW_RES_ID = "overview_panel";
    private static final String WIDGETS_RES_ID = "widgets_list_view";

    enum State {HOME, ALL_APPS, OVERVIEW, WIDGETS, BACKGROUND}

    static final String LAUNCHER_PKG = "com.google.android.apps.nexuslauncher";
    static final int APP_LAUNCH_TIMEOUT_MS = 10000;
    private static final int UI_OBJECT_WAIT_TIMEOUT_MS = 10000;
    private static final String SWIPE_UP_SETTING_AVAILABLE_RES_NAME =
            "config_swipe_up_gesture_setting_available";
    private static final String SWIPE_UP_ENABLED_DEFAULT_RES_NAME =
            "config_swipe_up_gesture_default";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String TAG = "tapl.Launcher";
    private final UiDevice mDevice;
    private final boolean mSwipeUpEnabled;

    /**
     * Constructs the root of TAPL hierarchy. You get all other object from it.
     */
    public Launcher(UiDevice device) {
        mDevice = device;
        final boolean swipeUpEnabledDefault =
                !getSystemBooleanRes(SWIPE_UP_SETTING_AVAILABLE_RES_NAME) ||
                        getSystemBooleanRes(SWIPE_UP_ENABLED_DEFAULT_RES_NAME);
        mSwipeUpEnabled = Settings.Secure.getInt(
                InstrumentationRegistry.getTargetContext().getContentResolver(),
                SWIPE_UP_SETTING_NAME,
                swipeUpEnabledDefault ? 1 : 0) == 1;
    }

    private boolean getSystemBooleanRes(String resName) {
        final Resources res = Resources.getSystem();
        final int resId = res.getIdentifier(resName, "bool", "android");
        assertTrue("Resource not found: " + resName, resId != 0);
        return res.getBoolean(resId);
    }

    private void dumpViewHierarchy() {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            mDevice.dumpWindowHierarchy(stream);
            stream.flush();
            stream.close();
            for (String line : stream.toString().split("\\r?\\n")) {
                Log.e(TAG, line.trim());
            }
        } catch (IOException e) {
            Log.e(TAG, "error dumping XML to logcat", e);
        }
    }

    void fail(String message) {
        dumpViewHierarchy();
        Assert.fail(message);
    }

    void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message);
        }
    }

    void assertNotNull(String message, Object object) {
        assertTrue(message, object != null);
    }

    private void failEquals(String message, Object actual) {
        String formatted = "Values should be different. ";
        if (message != null) {
            formatted = message + ". ";
        }

        formatted += "Actual: " + actual;
        fail(formatted);
    }

    void assertNotEquals(String message, int unexpected, int actual) {
        if (unexpected == actual) {
            failEquals(message, actual);
        }
    }

    boolean isSwipeUpEnabled() {
        return mSwipeUpEnabled;
    }

    UiObject2 assertState(State state) {
        switch (state) {
            case HOME: {
                //waitUntilGone(APPS_RES_ID);
                waitUntilGone(OVERVIEW_RES_ID);
                waitUntilGone(WIDGETS_RES_ID);
                return waitForLauncherObject(WORKSPACE_RES_ID);
            }
            case WIDGETS: {
                waitUntilGone(WORKSPACE_RES_ID);
                waitUntilGone(APPS_RES_ID);
                waitUntilGone(OVERVIEW_RES_ID);
                return waitForLauncherObject(WIDGETS_RES_ID);
            }
            case ALL_APPS: {
                waitUntilGone(OVERVIEW_RES_ID);
                waitUntilGone(WORKSPACE_RES_ID);
                waitUntilGone(WIDGETS_RES_ID);
                return waitForLauncherObject(APPS_RES_ID);
            }
            case OVERVIEW: {
                //waitForLauncherObject(APPS_RES_ID);
                waitUntilGone(WORKSPACE_RES_ID);
                waitUntilGone(WIDGETS_RES_ID);
                return waitForLauncherObject(OVERVIEW_RES_ID);
            }
            case BACKGROUND: {
                waitUntilGone(WORKSPACE_RES_ID);
                waitUntilGone(APPS_RES_ID);
                waitUntilGone(OVERVIEW_RES_ID);
                waitUntilGone(WIDGETS_RES_ID);
                return null;
            }
            default:
                fail("Invalid state: " + state);
                return null;
        }
    }

    /**
     * Presses nav bar home button.
     *
     * @return the Home object.
     */
    public Home pressHome() {
        getSystemUiObject("home").click();
        return getHome();
    }

    /**
     * Gets the Home object if the current state is "active home", i.e. workspace. Fails if the
     * launcher is not in that state.
     *
     * @return Home object.
     */
    @NonNull
    public Home getHome() {
        return new Home(this);
    }

    /**
     * Gets the Widgets object if the current state is showing all widgets. Fails if the launcher is
     * not in that state.
     *
     * @return Widgets object.
     */
    @NonNull
    public Widgets getAllWidgets() {
        return new Widgets(this);
    }

    /**
     * Gets the Overview object if the current state is showing the overview panel. Fails if the
     * launcher is not in that state.
     *
     * @return Overview object.
     */
    @NonNull
    public Overview getOverview() {
        return new Overview(this);
    }

    /**
     * Gets the All Apps object if the current state is showing the all apps panel. Fails if the
     * launcher is not in that state.
     *
     * @return All Aps object.
     */
    @NonNull
    public AllAppsFromHome getAllApps() {
        return new AllAppsFromHome(this);
    }

    /**
     * Gets the All Apps object if the current state is showing the all apps panel. Returns null if
     * the launcher is not in that state.
     *
     * @return All Aps object or null.
     */
    @Nullable
    public AllAppsFromHome tryGetAllApps() {
        return tryGetLauncherObject(APPS_RES_ID) != null ? getAllApps() : null;
    }

    private void waitUntilGone(String resId) {
//        assertTrue("Unexpected launcher object visible: " + resId,
//                mDevice.wait(Until.gone(getLauncherObjectSelector(resId)),
//                        UI_OBJECT_WAIT_TIMEOUT_MS));
    }

    @NonNull
    UiObject2 getSystemUiObject(String resId) {
        try {
            mDevice.wakeUp();
        } catch (RemoteException e) {
            fail("Failed to wake up the device: " + e);
        }
        final UiObject2 object = mDevice.findObject(By.res(SYSTEMUI_PACKAGE, resId));
        assertNotNull("Can't find a systemui object with id: " + resId, object);
        return object;
    }

    @NonNull
    UiObject2 getObjectInContainer(UiObject2 container, BySelector selector) {
        final UiObject2 object = container.findObject(selector);
        assertNotNull("Can't find an object with selector: " + selector, object);
        return object;
    }

    @Nullable
    private UiObject2 tryGetLauncherObject(String resName) {
        return mDevice.findObject(getLauncherObjectSelector(resName));
    }

    @NonNull
    UiObject2 waitForObjectInContainer(UiObject2 container, String resName) {
        final UiObject2 object = container.wait(
                Until.findObject(getLauncherObjectSelector(resName)),
                UI_OBJECT_WAIT_TIMEOUT_MS);
        assertNotNull("Can find a launcher object id: " + resName + " in container: " +
                container.getResourceName(), object);
        return object;
    }

    @NonNull
    UiObject2 waitForLauncherObject(String resName) {
        final UiObject2 object = mDevice.wait(Until.findObject(getLauncherObjectSelector(resName)),
                UI_OBJECT_WAIT_TIMEOUT_MS);
        assertNotNull("Can find a launcher object; id: " + resName, object);
        return object;
    }

    static BySelector getLauncherObjectSelector(String resName) {
        return By.res(LAUNCHER_PKG, resName);
    }

    @NonNull
    UiDevice getDevice() {
        return mDevice;
    }

    void swipe(int startX, int startY, int endX, int endY, int steps) {
        mDevice.swipe(startX, startY, endX, endY, steps);
        waitForIdle();
    }

    void waitForIdle() {
        mDevice.waitForIdle();
    }
}