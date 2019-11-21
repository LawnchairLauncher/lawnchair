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

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.MATCH_ALL;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;

import static com.android.launcher3.tapl.TestHelpers.getOverviewPackageName;
import static com.android.launcher3.testing.TestProtocol.BACKGROUND_APP_STATE_ORDINAL;
import static com.android.launcher3.testing.TestProtocol.NORMAL_STATE_ORDINAL;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.ResourceUtils;
import com.android.launcher3.testing.TestProtocol;
import com.android.systemui.shared.system.QuickStepContract;

import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The main tapl object. The only object that can be explicitly constructed by the using code. It
 * produces all other objects.
 */
public final class LauncherInstrumentation {

    private static final String TAG = "Tapl";
    private static final int ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME = 20;
    private static final int GESTURE_STEP_MS = 16;
    private static long START_TIME = System.currentTimeMillis();

    // Types for launcher containers that the user is interacting with. "Background" is a
    // pseudo-container corresponding to inactive launcher covered by another app.
    public enum ContainerType {
        WORKSPACE, ALL_APPS, OVERVIEW, WIDGETS, BACKGROUND, FALLBACK_OVERVIEW
    }

    public enum NavigationModel {ZERO_BUTTON, TWO_BUTTON, THREE_BUTTON}

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
            mLauncher.assertTrue("Attempt to use a stale container",
                    this == sActiveContainer.get());
            return mLauncher.verifyContainerType(getContainerType());
        }
    }

    interface Closable extends AutoCloseable {
        void close();
    }

    private static final String WORKSPACE_RES_ID = "workspace";
    private static final String APPS_RES_ID = "apps_view";
    private static final String OVERVIEW_RES_ID = "overview_panel";
    private static final String WIDGETS_RES_ID = "widgets_list_view";
    public static final int WAIT_TIME_MS = 10000;
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static WeakReference<VisibleContainer> sActiveContainer = new WeakReference<>(null);

    private final UiDevice mDevice;
    private final Instrumentation mInstrumentation;
    private int mExpectedRotation = Surface.ROTATION_0;
    private final Uri mTestProviderUri;
    private final Deque<String> mDiagnosticContext = new LinkedList<>();
    private Function<Long, String> mSystemHealthSupplier;

    private Consumer<ContainerType> mOnSettledStateAction;

    /**
     * Constructs the root of TAPL hierarchy. You get all other objects from it.
     */
    public LauncherInstrumentation() {
        this(InstrumentationRegistry.getInstrumentation());
    }

    /**
     * Constructs the root of TAPL hierarchy. You get all other objects from it.
     * Deprecated: use the constructor without parameters instead.
     */
    @Deprecated
    public LauncherInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
        mDevice = UiDevice.getInstance(instrumentation);

        // Launcher should run in test harness so that custom accessibility protocol between
        // Launcher and TAPL is enabled. In-process tests enable this protocol with a direct call
        // into Launcher.
        assertTrue("Device must run in a test harness",
                TestHelpers.isInLauncherProcess() || ActivityManager.isRunningInTestHarness());

        final String testPackage = getContext().getPackageName();
        final String targetPackage = mInstrumentation.getTargetContext().getPackageName();

        // Launcher package. As during inproc tests the tested launcher may not be selected as the
        // current launcher, choosing target package for inproc. For out-of-proc, use the installed
        // launcher package.
        final String authorityPackage = testPackage.equals(targetPackage) ?
                getLauncherPackageName() :
                targetPackage;

        String testProviderAuthority = authorityPackage + ".TestInfo";
        mTestProviderUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(testProviderAuthority)
                .build();

        try {
            mDevice.executeShellCommand("pm grant " + testPackage +
                    " android.permission.WRITE_SECURE_SETTINGS");
        } catch (IOException e) {
            fail(e.toString());
        }


        PackageManager pm = getContext().getPackageManager();
        ProviderInfo pi = pm.resolveContentProvider(
                testProviderAuthority, MATCH_ALL | MATCH_DISABLED_COMPONENTS);
        assertNotNull("Cannot find content provider for " + testProviderAuthority, pi);
        ComponentName cn = new ComponentName(pi.packageName, pi.name);

        if (pm.getComponentEnabledSetting(cn) != COMPONENT_ENABLED_STATE_ENABLED) {
            if (TestHelpers.isInLauncherProcess()) {
                getContext().getPackageManager().setComponentEnabledSetting(
                        cn, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
            } else {
                try {
                    mDevice.executeShellCommand("pm enable " + cn.flattenToString());
                } catch (IOException e) {
                    fail(e.toString());
                }
            }
        }
    }

    Context getContext() {
        return mInstrumentation.getContext();
    }

    Bundle getTestInfo(String request) {
        return getContext().getContentResolver().call(mTestProviderUri, request, null, null);
    }

    void setActiveContainer(VisibleContainer container) {
        sActiveContainer = new WeakReference<>(container);
    }

    public NavigationModel getNavigationModel() {
        final Context baseContext = mInstrumentation.getTargetContext();
        try {
            // Workaround, use constructed context because both the instrumentation context and the
            // app context are not constructed with resources that take overlays into account
            final Context ctx = baseContext.createPackageContext(getLauncherPackageName(), 0);
            for (int i = 0; i < 100; ++i) {
                final int currentInteractionMode = getCurrentInteractionMode(ctx);
                final NavigationModel model = getNavigationModel(currentInteractionMode);
                log("Interaction mode = " + currentInteractionMode + " (" + model + ")");
                if (model != null) return model;
                Thread.sleep(100);
            }
            fail("Can't detect navigation mode");
        } catch (Exception e) {
            fail(e.toString());
        }
        return NavigationModel.THREE_BUTTON;
    }

    public static NavigationModel getNavigationModel(int currentInteractionMode) {
        if (QuickStepContract.isGesturalMode(currentInteractionMode)) {
            return NavigationModel.ZERO_BUTTON;
        } else if (QuickStepContract.isSwipeUpMode(currentInteractionMode)) {
            return NavigationModel.TWO_BUTTON;
        } else if (QuickStepContract.isLegacyMode(currentInteractionMode)) {
            return NavigationModel.THREE_BUTTON;
        }
        return null;
    }

    static void log(String message) {
        Log.d(TAG, message);
    }

    Closable addContextLayer(String piece) {
        mDiagnosticContext.addLast(piece);
        log("Added context: " + getContextDescription());
        return () -> {
            log("Removing context: " + getContextDescription());
            mDiagnosticContext.removeLast();
        };
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

    private String getAnomalyMessage() {
        UiObject2 object = mDevice.findObject(By.res("android", "alertTitle"));
        if (object != null) {
            return "System alert popup is visible: " + object.getText();
        }

        object = mDevice.findObject(By.res("android", "message"));
        if (object != null) {
            return "Message popup by " + object.getApplicationPackage() + " is visible: "
                    + object.getText();
        }

        if (hasSystemUiObject("keyguard_status_view")) return "Phone is locked";

        if (!mDevice.hasObject(By.textStartsWith(""))) return "Screen is empty";

        return null;
    }

    public void checkForAnomaly() {
        final String anomalyMessage = getAnomalyMessage();
        if (anomalyMessage != null) {
            failWithSystemHealth(
                    "Tests are broken by a non-Launcher system error: " + anomalyMessage);
        }
    }

    private String getVisibleStateMessage() {
        if (hasLauncherObject(WIDGETS_RES_ID)) return "Widgets";
        if (hasLauncherObject(OVERVIEW_RES_ID)) return "Overview";
        if (hasLauncherObject(WORKSPACE_RES_ID)) return "Workspace";
        if (hasLauncherObject(APPS_RES_ID)) return "AllApps";
        return "Background";
    }

    public void setSystemHealthSupplier(Function<Long, String> supplier) {
        this.mSystemHealthSupplier = supplier;
    }

    public void setOnSettledStateAction(Consumer<ContainerType> onSettledStateAction) {
        mOnSettledStateAction = onSettledStateAction;
    }

    private String getSystemHealthMessage() {
        final String testPackage = getContext().getPackageName();
        try {
            mDevice.executeShellCommand("pm grant " + testPackage +
                    " android.permission.READ_LOGS");
            mDevice.executeShellCommand("pm grant " + testPackage +
                    " android.permission.PACKAGE_USAGE_STATS");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return mSystemHealthSupplier != null
                ? mSystemHealthSupplier.apply(START_TIME)
                : TestHelpers.getSystemHealthMessage(getContext(), START_TIME);
    }

    private void fail(String message) {
        checkForAnomaly();

        failWithSystemHealth("http://go/tapl : " + getContextDescription() + message +
                " (visible state: " + getVisibleStateMessage() + ")");
    }

    private void failWithSystemHealth(String message) {
        final String systemHealth = getSystemHealthMessage();
        if (systemHealth != null) {
            message = message
                    + ", perhaps because of system health problems:\n<<<<<<<<<<<<<<<<<<\n"
                    + systemHealth + "\n>>>>>>>>>>>>>>>>>>";
        }

        log("Hierarchy dump for: " + message);
        dumpViewHierarchy();

        Assert.fail(message);
    }

    private String getContextDescription() {
        return mDiagnosticContext.isEmpty() ? "" : String.join(", ", mDiagnosticContext) + "; ";
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
        fail(message + ". " + "Actual: " + actual);
    }

    private void assertEquals(String message, int expected, int actual) {
        if (expected != actual) {
            fail(message + " expected: " + expected + " but was: " + actual);
        }
    }

    void assertEquals(String message, String expected, String actual) {
        if (!TextUtils.equals(expected, actual)) {
            fail(message + " expected: '" + expected + "' but was: '" + actual + "'");
        }
    }

    void assertEquals(String message, long expected, long actual) {
        if (expected != actual) {
            fail(message + " expected: " + expected + " but was: " + actual);
        }
    }

    void assertNotEquals(String message, int unexpected, int actual) {
        if (unexpected == actual) {
            failEquals(message, actual);
        }
    }

    public void setExpectedRotation(int expectedRotation) {
        mExpectedRotation = expectedRotation;
    }

    public String getNavigationModeMismatchError() {
        final NavigationModel navigationModel = getNavigationModel();
        final boolean hasRecentsButton = hasSystemUiObject("recent_apps");
        final boolean hasHomeButton = hasSystemUiObject("home");
        if ((navigationModel == NavigationModel.THREE_BUTTON) != hasRecentsButton) {
            return "Presence of recents button doesn't match the interaction mode, mode="
                    + navigationModel.name() + ", hasRecents=" + hasRecentsButton;
        }
        if ((navigationModel != NavigationModel.ZERO_BUTTON) != hasHomeButton) {
            return "Presence of home button doesn't match the interaction mode, mode="
                    + navigationModel.name() + ", hasHome=" + hasHomeButton;
        }
        return null;
    }

    private UiObject2 verifyContainerType(ContainerType containerType) {
        waitForLauncherInitialized();

        assertEquals("Unexpected display rotation",
                mExpectedRotation, mDevice.getDisplayRotation());

        // b/136278866
        for (int i = 0; i != 100; ++i) {
            if (getNavigationModeMismatchError() == null) break;
            sleep(100);
        }

        final String error = getNavigationModeMismatchError();
        assertTrue(error, error == null);
        log("verifyContainerType: " + containerType);

        final UiObject2 container = verifyVisibleObjects(containerType);

        if (mOnSettledStateAction != null) mOnSettledStateAction.accept(containerType);

        return container;
    }

    private UiObject2 verifyVisibleObjects(ContainerType containerType) {
        try (Closable c = addContextLayer(
                "but the current state is not " + containerType.name())) {
            switch (containerType) {
                case WORKSPACE: {
                    if (mDevice.isNaturalOrientation()) {
                        waitForLauncherObject(APPS_RES_ID);
                    } else {
                        waitUntilGone(APPS_RES_ID);
                    }
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
                    waitUntilGone(WORKSPACE_RES_ID);
                    waitUntilGone(WIDGETS_RES_ID);

                    return waitForLauncherObject(OVERVIEW_RES_ID);
                }
                case FALLBACK_OVERVIEW: {
                    return waitForFallbackLauncherObject(OVERVIEW_RES_ID);
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
    }

    public void waitForLauncherInitialized() {
        for (int i = 0; i < 100; ++i) {
            if (getTestInfo(
                    TestProtocol.REQUEST_IS_LAUNCHER_INITIALIZED).
                    getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD)) {
                return;
            }
            SystemClock.sleep(100);
        }
        fail("Launcher didn't initialize");
    }

    Parcelable executeAndWaitForEvent(Runnable command,
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
        final String action;
        if (getNavigationModel() == NavigationModel.ZERO_BUTTON) {
            checkForAnomaly();

            final Point displaySize = getRealDisplaySize();

            if (hasLauncherObject("deep_shortcuts_container")) {
                linearGesture(
                        displaySize.x / 2, displaySize.y - 1,
                        displaySize.x / 2, 0,
                        ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME,
                        false);
                try (LauncherInstrumentation.Closable c = addContextLayer(
                        "Swiped up from context menu to home")) {
                    waitUntilGone("deep_shortcuts_container");
                }
            }
            if (hasLauncherObject(WORKSPACE_RES_ID)) {
                log(action = "already at home");
            } else {
                log("Hierarchy before swiping up to home");
                dumpViewHierarchy();
                log(action = "swiping up to home from " + getVisibleStateMessage());
                final int finalState = mDevice.hasObject(By.pkg(getLauncherPackageName()))
                        ? NORMAL_STATE_ORDINAL : BACKGROUND_APP_STATE_ORDINAL;

                swipeToState(
                        displaySize.x / 2, displaySize.y - 1,
                        displaySize.x / 2, 0,
                        ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME, finalState);
            }
        } else {
            log(action = "clicking home button");
            executeAndWaitForEvent(
                    () -> {
                        log("LauncherInstrumentation.pressHome before clicking");
                        waitForSystemUiObject("home").click();
                    },
                    event -> true,
                    "Pressing Home didn't produce any events");
            mDevice.waitForIdle();
        }
        try (LauncherInstrumentation.Closable c = addContextLayer(
                "performed action to switch to Home - " + action)) {
            return getWorkspace();
        }
    }

    /**
     * Gets the Workspace object if the current state is "active home", i.e. workspace. Fails if the
     * launcher is not in that state.
     *
     * @return Workspace object.
     */
    @NonNull
    public Workspace getWorkspace() {
        try (LauncherInstrumentation.Closable c = addContextLayer("want to get workspace object")) {
            return new Workspace(this);
        }
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
        try (LauncherInstrumentation.Closable c = addContextLayer("want to get widgets")) {
            return new Widgets(this);
        }
    }

    @NonNull
    public AddToHomeScreenPrompt getAddToHomeScreenPrompt() {
        try (LauncherInstrumentation.Closable c = addContextLayer("want to get widget cell")) {
            return new AddToHomeScreenPrompt(this);
        }
    }

    /**
     * Gets the Overview object if the current state is showing the overview panel. Fails if the
     * launcher is not in that state.
     *
     * @return Overview object.
     */
    @NonNull
    public Overview getOverview() {
        try (LauncherInstrumentation.Closable c = addContextLayer("want to get overview")) {
            return new Overview(this);
        }
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
        try (LauncherInstrumentation.Closable c = addContextLayer("want to get all apps object")) {
            return new AllApps(this);
        }
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
        try (LauncherInstrumentation.Closable c = addContextLayer("want to get all apps object")) {
            return new AllAppsFromOverview(this);
        }
    }

    void waitUntilGone(String resId) {
        assertTrue("Unexpected launcher object visible: " + resId,
                mDevice.wait(Until.gone(getLauncherObjectSelector(resId)),
                        WAIT_TIME_MS));
    }

    private boolean hasSystemUiObject(String resId) {
        return mDevice.hasObject(By.res(SYSTEMUI_PACKAGE, resId));
    }

    @NonNull
    UiObject2 waitForSystemUiObject(String resId) {
        final UiObject2 object = mDevice.wait(
                Until.findObject(By.res(SYSTEMUI_PACKAGE, resId)), WAIT_TIME_MS);
        assertNotNull("Can't find a systemui object with id: " + resId, object);
        return object;
    }

    @NonNull
    List<UiObject2> getObjectsInContainer(UiObject2 container, String resName) {
        return container.findObjects(getLauncherObjectSelector(resName));
    }

    @NonNull
    UiObject2 waitForObjectInContainer(UiObject2 container, String resName) {
        final UiObject2 object = container.wait(
                Until.findObject(getLauncherObjectSelector(resName)),
                WAIT_TIME_MS);
        assertNotNull("Can't find a view in Launcher, id: " + resName + " in container: "
                + container.getResourceName(), object);
        return object;
    }

    @NonNull
    UiObject2 waitForObjectInContainer(UiObject2 container, BySelector selector) {
        final UiObject2 object = container.wait(
                Until.findObject(selector),
                WAIT_TIME_MS);
        assertNotNull("Can't find a view in Launcher, id: " + selector + " in container: "
                + container.getResourceName(), object);
        return object;
    }

    @Nullable
    private boolean hasLauncherObject(String resId) {
        return mDevice.hasObject(getLauncherObjectSelector(resId));
    }

    @NonNull
    UiObject2 waitForLauncherObject(String resName) {
        return waitForObjectBySelector(getLauncherObjectSelector(resName));
    }

    @NonNull
    UiObject2 waitForLauncherObject(BySelector selector) {
        return waitForObjectBySelector(By.copy(selector).pkg(getLauncherPackageName()));
    }

    @NonNull
    UiObject2 tryWaitForLauncherObject(BySelector selector, long timeout) {
        return tryWaitForObjectBySelector(By.copy(selector).pkg(getLauncherPackageName()), timeout);
    }

    @NonNull
    UiObject2 waitForFallbackLauncherObject(String resName) {
        return waitForObjectBySelector(getOverviewObjectSelector(resName));
    }

    private UiObject2 waitForObjectBySelector(BySelector selector) {
        final UiObject2 object = mDevice.wait(Until.findObject(selector), WAIT_TIME_MS);
        assertNotNull("Can't find a view in Launcher, selector: " + selector, object);
        return object;
    }

    private UiObject2 tryWaitForObjectBySelector(BySelector selector, long timeout) {
        return mDevice.wait(Until.findObject(selector), timeout);
    }

    BySelector getLauncherObjectSelector(String resName) {
        return By.res(getLauncherPackageName(), resName);
    }

    BySelector getOverviewObjectSelector(String resName) {
        return By.res(getOverviewPackageName(), resName);
    }

    String getLauncherPackageName() {
        return mDevice.getLauncherPackageName();
    }

    boolean isFallbackOverview() {
        return !getOverviewPackageName().equals(getLauncherPackageName());
    }

    @NonNull
    public UiDevice getDevice() {
        return mDevice;
    }

    void swipeToState(int startX, int startY, int endX, int endY, int steps, int expectedState) {
        final Bundle parcel = (Bundle) executeAndWaitForEvent(
                () -> linearGesture(startX, startY, endX, endY, steps, false),
                event -> TestProtocol.SWITCHED_TO_STATE_MESSAGE.equals(event.getClassName()),
                "Swipe failed to receive an event for the swipe end");
        assertEquals("Swipe switched launcher to a wrong state;",
                TestProtocol.stateOrdinalToString(expectedState),
                TestProtocol.stateOrdinalToString(parcel.getInt(TestProtocol.STATE_FIELD)));
    }

    int getBottomGestureSize() {
        return ResourceUtils.getNavbarSize(
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, getResources()) + 1;
    }

    int getBottomGestureMarginInContainer(UiObject2 container) {
        final int bottomGestureStartOnScreen = getRealDisplaySize().y - getBottomGestureSize();
        return container.getVisibleBounds().bottom - bottomGestureStartOnScreen;
    }

    void scrollToLastVisibleRow(
            UiObject2 container,
            Collection<UiObject2> items,
            int topPaddingInContainer) {
        final UiObject2 lowestItem = Collections.max(items, (i1, i2) ->
                Integer.compare(i1.getVisibleBounds().top, i2.getVisibleBounds().top));

        final int itemRowCurrentTopOnScreen = lowestItem.getVisibleBounds().top;
        final Rect containerRect = container.getVisibleBounds();
        final int itemRowNewTopOnScreen = containerRect.top + topPaddingInContainer;
        final int distance = itemRowCurrentTopOnScreen - itemRowNewTopOnScreen + getTouchSlop();

        final int bottomGestureMarginInContainer = getBottomGestureMarginInContainer(container);
        scroll(
                container,
                Direction.DOWN,
                new Rect(
                        0,
                        containerRect.height() - distance - bottomGestureMarginInContainer,
                        0,
                        bottomGestureMarginInContainer),
                10,
                true);
    }

    void scroll(
            UiObject2 container, Direction direction, Rect margins, int steps, boolean slowDown) {
        final Rect rect = container.getVisibleBounds();
        if (margins != null) {
            rect.left += margins.left;
            rect.top += margins.top;
            rect.right -= margins.right;
            rect.bottom -= margins.bottom;
        }

        final int startX;
        final int startY;
        final int endX;
        final int endY;

        switch (direction) {
            case UP: {
                startX = endX = rect.centerX();
                startY = rect.top;
                endY = rect.bottom - 1;
            }
            break;
            case DOWN: {
                startX = endX = rect.centerX();
                startY = rect.bottom - 1;
                endY = rect.top;
            }
            break;
            case LEFT: {
                startY = endY = rect.centerY();
                startX = rect.left;
                endX = rect.right - 1;
            }
            break;
            case RIGHT: {
                startY = endY = rect.centerY();
                startX = rect.right - 1;
                endX = rect.left;
            }
            break;
            default:
                fail("Unsupported direction");
                return;
        }

        executeAndWaitForEvent(
                () -> linearGesture(startX, startY, endX, endY, steps, slowDown),
                event -> TestProtocol.SCROLL_FINISHED_MESSAGE.equals(event.getClassName()),
                "Didn't receive a scroll end message: " + startX + ", " + startY
                        + ", " + endX + ", " + endY);
    }

    // Inject a swipe gesture. Inject exactly 'steps' motion points, incrementing event time by a
    // fixed interval each time.
    void linearGesture(int startX, int startY, int endX, int endY, int steps, boolean slowDown) {
        log("linearGesture: " + startX + ", " + startY + " -> " + endX + ", " + endY);
        final long downTime = SystemClock.uptimeMillis();
        final Point start = new Point(startX, startY);
        final Point end = new Point(endX, endY);
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, start);
        long endTime = movePointer(downTime, downTime, steps * GESTURE_STEP_MS, start, end);
        if (slowDown) {
            endTime = movePointer(downTime, endTime + GESTURE_STEP_MS, 5 * GESTURE_STEP_MS, end,
                    end);
        }
        sendPointer(downTime, endTime, MotionEvent.ACTION_UP, end);
    }

    void waitForIdle() {
        mDevice.waitForIdle();
    }

    int getTouchSlop() {
        return ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    public Resources getResources() {
        return getContext().getResources();
    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action,
            float x, float y) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = Configurator.getInstance().getToolType();

        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.pressure = 1;
        coords.size = 1;
        coords.x = x;
        coords.y = y;

        return MotionEvent.obtain(downTime, eventTime, action, 1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords},
                0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    void sendPointer(long downTime, long currentTime, int action, Point point) {
        final MotionEvent event = getMotionEvent(downTime, currentTime, action, point.x, point.y);
        mInstrumentation.getUiAutomation().injectInputEvent(event, true);
        event.recycle();
    }

    long movePointer(long downTime, long startTime, long duration, Point from, Point to) {
        log("movePointer: " + from + " to " + to);
        final Point point = new Point();
        long steps = duration / GESTURE_STEP_MS;
        long currentTime = startTime;
        for (long i = 0; i < steps; ++i) {
            sleep(GESTURE_STEP_MS);

            currentTime += GESTURE_STEP_MS;
            final float progress = (currentTime - startTime) / (float) duration;

            point.x = from.x + (int) (progress * (to.x - from.x));
            point.y = from.y + (int) (progress * (to.y - from.y));

            sendPointer(downTime, currentTime, MotionEvent.ACTION_MOVE, point);
        }
        return currentTime;
    }

    public static int getCurrentInteractionMode(Context context) {
        return getSystemIntegerRes(context, "config_navBarInteractionMode");
    }

    private static int getSystemIntegerRes(Context context, String resName) {
        Resources res = context.getResources();
        int resId = res.getIdentifier(resName, "integer", "android");

        if (resId != 0) {
            return res.getInteger(resId);
        } else {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
            return -1;
        }
    }

    private static int getSystemDimensionResId(Context context, String resName) {
        Resources res = context.getResources();
        int resId = res.getIdentifier(resName, "dimen", "android");

        if (resId != 0) {
            return resId;
        } else {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
            return -1;
        }
    }

    static void sleep(int duration) {
        SystemClock.sleep(duration);
    }

    int getEdgeSensitivityWidth() {
        try {
            final Context context = mInstrumentation.getTargetContext().createPackageContext(
                    getLauncherPackageName(), 0);
            return context.getResources().getDimensionPixelSize(
                    getSystemDimensionResId(context, "config_backGestureInset")) + 1;
        } catch (PackageManager.NameNotFoundException e) {
            fail("Can't get edge sensitivity: " + e);
            return 0;
        }
    }

    Point getRealDisplaySize() {
        final Point size = new Point();
        getContext().getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(size);
        return size;
    }

    public void enableDebugTracing() {
        getTestInfo(TestProtocol.REQUEST_ENABLE_DEBUG_TRACING);
    }

    public void disableDebugTracing() {
        getTestInfo(TestProtocol.REQUEST_DISABLE_DEBUG_TRACING);
    }

    public int getTotalPssKb() {
        return getTestInfo(TestProtocol.REQUEST_TOTAL_PSS_KB).
                getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public void produceJavaLeak() {
        getTestInfo(TestProtocol.REQUEST_JAVA_LEAK);
    }

    public void produceNativeLeak() {
        getTestInfo(TestProtocol.REQUEST_NATIVE_LEAK);
    }

    public void produceViewLeak() {
        getTestInfo(TestProtocol.REQUEST_VIEW_LEAK);
    }

    public ArrayList<ComponentName> getRecentTasks() {
        ArrayList<ComponentName> tasks = new ArrayList<>();
        ArrayList<String> components = getTestInfo(TestProtocol.REQUEST_RECENT_TASKS_LIST)
                .getStringArrayList(TestProtocol.TEST_INFO_RESPONSE_FIELD);
        for (String s : components) {
            tasks.add(ComponentName.unflattenFromString(s));
        }
        return tasks;
    }
}
