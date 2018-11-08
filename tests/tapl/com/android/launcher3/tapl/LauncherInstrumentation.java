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

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.TestProtocol;
import com.android.quickstep.SwipeUpSetting;

import org.junit.Assert;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeoutException;

/**
 * The main tapl object. The only object that can be explicitly constructed by the using code. It
 * produces all other objects.
 */
public final class LauncherInstrumentation {

    private static final String TAG = "Tapl";

    // Types for launcher containers that the user is interacting with. "Background" is a
    // pseudo-container corresponding to inactive launcher covered by another app.
    enum ContainerType {
        WORKSPACE, ALL_APPS, OVERVIEW, WIDGETS, BACKGROUND, BASE_OVERVIEW
    }

    // Base class for launcher containers.
    static abstract class VisibleContainer {
        protected final LauncherInstrumentation mLauncher;

        protected VisibleContainer(LauncherInstrumentation launcher) {
            mLauncher = launcher;
            launcher.setActiveContainer(this);
        }

        protected abstract ContainerType getContainerType();

        /**
         * Asserts that the launcher is in the mode matching 'this' object.
         *
         * @return UI object for the container.
         */
        final UiObject2 verifyActiveContainer() {
            assertTrue("Attempt to use a stale container", this == sActiveContainer.get());
            return mLauncher.verifyContainerType(getContainerType());
        }
    }

    private static final String WORKSPACE_RES_ID = "workspace";
    private static final String APPS_RES_ID = "apps_view";
    private static final String OVERVIEW_RES_ID = "overview_panel";
    private static final String WIDGETS_RES_ID = "widgets_list_view";
    static final String LAUNCHER_PKG = "com.google.android.apps.nexuslauncher";
    public static final int WAIT_TIME_MS = 60000;
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static WeakReference<VisibleContainer> sActiveContainer = new WeakReference<>(null);

    private final UiDevice mDevice;
    private final boolean mSwipeUpEnabled;
    private Boolean mSwipeUpEnabledOverride = null;
    private final Instrumentation mInstrumentation;
    private int mExpectedRotation = Surface.ROTATION_0;

    /**
     * Constructs the root of TAPL hierarchy. You get all other objects from it.
     */
    public LauncherInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
        mDevice = UiDevice.getInstance(instrumentation);
        final boolean swipeUpEnabledDefault =
                !SwipeUpSetting.isSwipeUpSettingAvailable() ||
                        SwipeUpSetting.isSwipeUpEnabledDefaultValue();
        mSwipeUpEnabled = Settings.Secure.getInt(
                instrumentation.getTargetContext().getContentResolver(),
                SWIPE_UP_SETTING_NAME,
                swipeUpEnabledDefault ? 1 : 0) == 1;

        // Launcher should run in test harness so that custom accessibility protocol between
        // Launcher and TAPL is enabled. In-process tests enable this protocol with a direct call
        // into Launcher.
        assertTrue("Device must run in a test harness",
                TestHelpers.isInLauncherProcess() || ActivityManager.isRunningInTestHarness());
    }

    // Used only by TaplTests.
    public void overrideSwipeUpEnabled(Boolean swipeUpEnabledOverride) {
        mSwipeUpEnabledOverride = swipeUpEnabledOverride;
    }

    void setActiveContainer(VisibleContainer container) {
        sActiveContainer = new WeakReference<>(container);
    }

    public boolean isSwipeUpEnabled() {
        return mSwipeUpEnabledOverride != null ? mSwipeUpEnabledOverride : mSwipeUpEnabled;
    }

    static void log(String message) {
        Log.d(TAG, message);
    }

    private static void fail(String message) {
        Assert.fail("http://go/tapl : " + message);
    }

    static void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message);
        }
    }

    static void assertNotNull(String message, Object object) {
        assertTrue(message, object != null);
    }

    static private void failEquals(String message, Object actual) {
        fail(message + ". " + "Actual: " + actual);
    }

    static public void assertEquals(String message, int expected, int actual) {
        if (expected != actual) {
            fail(message + " expected: " + expected + " but was: " + actual);
        }
    }

    static void assertNotEquals(String message, int unexpected, int actual) {
        if (unexpected == actual) {
            failEquals(message, actual);
        }
    }

    public void setExpectedRotation(int expectedRotation) {
        mExpectedRotation = expectedRotation;
    }

    private UiObject2 verifyContainerType(ContainerType containerType) {
        assertEquals("Unexpected display rotation",
                mExpectedRotation, mDevice.getDisplayRotation());
        assertTrue("Presence of recents button doesn't match isSwipeUpEnabled()",
                isSwipeUpEnabled() ==
                        (mDevice.findObject(By.res(SYSTEMUI_PACKAGE, "recent_apps")) == null));
        log("verifyContainerType: " + containerType);

        switch (containerType) {
            case WORKSPACE: {
                waitForLauncherObject(APPS_RES_ID);
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
                waitUntilGone(WORKSPACE_RES_ID);
                waitUntilGone(OVERVIEW_RES_ID);
                waitUntilGone(WIDGETS_RES_ID);
                return waitForLauncherObject(APPS_RES_ID);
            }
            case OVERVIEW: {
                if (mDevice.isNaturalOrientation()) {
                    waitForLauncherObject(APPS_RES_ID);
                } else {
                    waitUntilGone(APPS_RES_ID);
                }
                // Fall through
            }
            case BASE_OVERVIEW: {
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
                fail("Invalid state: " + containerType);
                return null;
        }
    }

    private Parcelable executeAndWaitForEvent(Runnable command,
            UiAutomation.AccessibilityEventFilter eventFilter, String message) {
        try {
            final AccessibilityEvent event =
                    mInstrumentation.getUiAutomation().executeAndWaitForEvent(
                            command, eventFilter, WAIT_TIME_MS);
            assertNotNull("executeAndWaitForEvent returned null (this can't happen)", event);
            return event.getParcelableData();
        } catch (TimeoutException e) {
            fail(message);
            return null;
        }
    }

    Bundle getAnswerFromLauncher(UiObject2 view, String requestTag) {
        // Send a fake set-text request to Launcher to initiate a response with requested data.
        final String responseTag = requestTag + TestProtocol.RESPONSE_MESSAGE_POSTFIX;
        return (Bundle) executeAndWaitForEvent(
                () -> view.setText(requestTag),
                event -> responseTag.equals(event.getClassName()),
                "Launcher didn't respond to request: " + requestTag);
    }

    /**
     * Presses nav bar home button.
     *
     * @return the Workspace object.
     */
    public Workspace pressHome() {
        // Click home, then wait for any accessibility event, then wait until accessibility events
        // stop.
        // We need waiting for any accessibility event generated after pressing Home because
        // otherwise waitForIdle may return immediately in case when there was a big enough pause in
        // accessibility events prior to pressing Home.
        executeAndWaitForEvent(
                () -> {
                    log("LauncherInstrumentation.pressHome before clicking");
                    getSystemUiObject("home").click();
                },
                event -> true,
                "Pressing Home didn't produce any events");
        mDevice.waitForIdle();
        return getWorkspace();
    }

    /**
     * Gets the Workspace object if the current state is "active home", i.e. workspace. Fails if the
     * launcher is not in that state.
     *
     * @return Workspace object.
     */
    @NonNull
    public Workspace getWorkspace() {
        return new Workspace(this);
    }

    /**
     * Gets the Workspace object if the current state is "background home", i.e. some other app is
     * active. Fails if the launcher is not in that state.
     *
     * @return Background object.
     */
    @NonNull
    public Background getBackground() {
        return new Background(this);
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
     * Gets the Base overview object if either Launcher is in overview state or the fallback
     * overview activity is showing. Fails otherwise.
     *
     * @return BaseOverview object.
     */
    @NonNull
    public BaseOverview getBaseOverview() {
        return new BaseOverview(this);
    }

    /**
     * Gets the All Apps object if the current state is showing the all apps panel opened by swiping
     * from workspace. Fails if the launcher is not in that state. Please don't call this method if
     * App Apps was opened by swiping up from Overview, as it won't fail and will return an
     * incorrect object.
     *
     * @return All Aps object.
     */
    @NonNull
    public AllApps getAllApps() {
        return new AllApps(this);
    }

    /**
     * Gets the All Apps object if the current state is showing the all apps panel opened by swiping
     * from overview. Fails if the launcher is not in that state. Please don't call this method if
     * App Apps was opened by swiping up from home, as it won't fail and will return an
     * incorrect object.
     *
     * @return All Aps object.
     */
    @NonNull
    public AllAppsFromOverview getAllAppsFromOverview() {
        return new AllAppsFromOverview(this);
    }

    private void waitUntilGone(String resId) {
        assertTrue("Unexpected launcher object visible: " + resId,
                mDevice.wait(Until.gone(getLauncherObjectSelector(resId)),
                        WAIT_TIME_MS));
    }

    @NonNull
    UiObject2 getSystemUiObject(String resId) {
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

    @NonNull
    UiObject2 waitForObjectInContainer(UiObject2 container, String resName) {
        final UiObject2 object = container.wait(
                Until.findObject(getLauncherObjectSelector(resName)),
                WAIT_TIME_MS);
        assertNotNull("Can't find a launcher object id: " + resName + " in container: " +
                container.getResourceName(), object);
        return object;
    }

    @NonNull
    UiObject2 waitForLauncherObject(String resName) {
        final UiObject2 object = mDevice.wait(Until.findObject(getLauncherObjectSelector(resName)),
                WAIT_TIME_MS);
        assertNotNull("Can't find a launcher object; id: " + resName, object);
        return object;
    }

    static BySelector getLauncherObjectSelector(String resName) {
        return By.res(LAUNCHER_PKG, resName);
    }

    @NonNull
    UiDevice getDevice() {
        return mDevice;
    }

    void longTap(int x, int y) {
        mDevice.drag(x, y, x, y, 0);
    }


    void swipe(int startX, int startY, int endX, int endY) {
        executeAndWaitForEvent(
                () -> mDevice.swipe(startX, startY, endX, endY, 60),
                event -> TestProtocol.SWITCHED_TO_STATE_MESSAGE.equals(event.getClassName()),
                "Swipe failed to receive an event for the swipe end: " + startX + ", " + startY
                        + ", " + endX + ", " + endY);
    }

    void waitForIdle() {
        mDevice.waitForIdle();
    }
}