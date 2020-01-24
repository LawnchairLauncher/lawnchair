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
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.StaleObjectException;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The main tapl object. The only object that can be explicitly constructed by the using code. It
 * produces all other objects.
 */
public final class LauncherInstrumentation {

    private static final String TAG = "Tapl";
    private static final int ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME = 20;
    private static final int GESTURE_STEP_MS = 16;
    private static long START_TIME = System.currentTimeMillis();

    static final Pattern EVENT_LOG_ENTRY = Pattern.compile(
            "[0-9][0-9]-[0-9][0-9] [0-9][0-9]:[0-9][0-9]:[0-9][0-9]\\.[0-9][0-9][0-9]"
                    + ".*" + TestProtocol.TAPL_EVENTS_TAG + ": (?<event>.*)");

    private static final Pattern EVENT_TOUCH_DOWN = getTouchEventPattern("ACTION_DOWN");
    private static final Pattern EVENT_TOUCH_UP = getTouchEventPattern("ACTION_UP");
    private static final Pattern EVENT_TOUCH_CANCEL = getTouchEventPattern("ACTION_CANCEL");

    // Types for launcher containers that the user is interacting with. "Background" is a
    // pseudo-container corresponding to inactive launcher covered by another app.
    public enum ContainerType {
        WORKSPACE, ALL_APPS, OVERVIEW, WIDGETS, BACKGROUND, FALLBACK_OVERVIEW
    }

    public enum NavigationModel {ZERO_BUTTON, TWO_BUTTON, THREE_BUTTON}

    // Where the gesture happens: outside of Launcher, inside or from inside to outside.
    enum GestureScope {
        OUTSIDE, INSIDE, INSIDE_TO_OUTSIDE
    }

    ;

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
    private static final String CONTEXT_MENU_RES_ID = "deep_shortcuts_container";
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

    // Not null when we are collecting expected events to compare with actual ones.
    private List<Pattern> mExpectedEvents;

    private String mTimeBeforeFirstLogEvent;
    private boolean mCheckEventsForSuccessfulGestures = false;

    private static Pattern getTouchEventPattern(String action) {
        // The pattern includes sanity checks that we don't get a multi-touch events or other
        // surprises.
        return Pattern.compile(
                "Touch event: MotionEvent.*?action=" + action + ".*?id\\[0\\]=0"
                        +
                        ".*?toolType\\[0\\]=TOOL_TYPE_FINGER.*?buttonState=0.*?pointerCount=1");
    }

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

        mInstrumentation.getUiAutomation().grantRuntimePermission(
                testPackage, "android.permission.WRITE_SECURE_SETTINGS");

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

    public void enableCheckEventsForSuccessfulGestures() {
        mCheckEventsForSuccessfulGestures = true;
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
        log("Entering context: " + piece);
        return () -> {
            log("Leaving context: " + piece);
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
            String message = "Tests are broken by a non-Launcher system error: " + anomalyMessage;
            log("Hierarchy dump for: " + message);
            dumpViewHierarchy();

            Assert.fail(formatSystemHealthMessage(message));
        }
    }

    private String getVisiblePackages() {
        return mDevice.findObjects(By.textStartsWith(""))
                .stream()
                .map(LauncherInstrumentation::getApplicationPackageSafe)
                .distinct()
                .filter(pkg -> pkg != null && !"com.android.systemui".equals(pkg))
                .collect(Collectors.joining(", "));
    }

    private static String getApplicationPackageSafe(UiObject2 object) {
        try {
            return object.getApplicationPackage();
        } catch (StaleObjectException e) {
            // We are looking at all object in the system; external ones can suddenly go away.
            return null;
        }
    }

    private String getVisibleStateMessage() {
        if (hasLauncherObject(CONTEXT_MENU_RES_ID)) return "Context Menu";
        if (hasLauncherObject(WIDGETS_RES_ID)) return "Widgets";
        if (hasLauncherObject(OVERVIEW_RES_ID)) return "Overview";
        if (hasLauncherObject(WORKSPACE_RES_ID)) return "Workspace";
        if (hasLauncherObject(APPS_RES_ID)) return "AllApps";
        return "Background (" + getVisiblePackages() + ")";
    }

    public void setSystemHealthSupplier(Function<Long, String> supplier) {
        this.mSystemHealthSupplier = supplier;
    }

    public void setOnSettledStateAction(Consumer<ContainerType> onSettledStateAction) {
        mOnSettledStateAction = onSettledStateAction;
    }

    private String formatSystemHealthMessage(String message) {
        final String testPackage = getContext().getPackageName();

        mInstrumentation.getUiAutomation().grantRuntimePermission(
                testPackage, "android.permission.READ_LOGS");
        mInstrumentation.getUiAutomation().grantRuntimePermission(
                testPackage, "android.permission.PACKAGE_USAGE_STATS");

        final String systemHealth = mSystemHealthSupplier != null
                ? mSystemHealthSupplier.apply(START_TIME)
                : TestHelpers.getSystemHealthMessage(getContext(), START_TIME);

        if (systemHealth != null) {
            return message
                    + ",\nperhaps linked to system health problems:\n<<<<<<<<<<<<<<<<<<\n"
                    + systemHealth + "\n>>>>>>>>>>>>>>>>>>";
        }

        return message;
    }

    private void fail(String message) {
        checkForAnomaly();

        message = "http://go/tapl : " + getContextDescription() + message
                + " (visible state: " + getVisibleStateMessage() + ")";
        log("Hierarchy dump for: " + message);
        dumpViewHierarchy();

        final String eventMismatch = getEventMismatchMessage(false);

        if (eventMismatch != null) {
            message = message + ", having produced " + eventMismatch;
        }

        Assert.fail(formatSystemHealthMessage(message));
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
            UiAutomation.AccessibilityEventFilter eventFilter, Supplier<String> message) {
        try {
            final AccessibilityEvent event =
                    mInstrumentation.getUiAutomation().executeAndWaitForEvent(
                            command, eventFilter, WAIT_TIME_MS);
            assertNotNull("executeAndWaitForEvent returned null (this can't happen)", event);
            final Parcelable parcelableData = event.getParcelableData();
            event.recycle();
            return parcelableData;
        } catch (TimeoutException e) {
            fail(message.get());
            return null;
        }
    }

    /**
     * Presses nav bar home button.
     *
     * @return the Workspace object.
     */
    public Workspace pressHome() {
        try (LauncherInstrumentation.Closable e = eventsCheck()) {
            // Click home, then wait for any accessibility event, then wait until accessibility
            // events stop.
            // We need waiting for any accessibility event generated after pressing Home because
            // otherwise waitForIdle may return immediately in case when there was a big enough
            // pause in accessibility events prior to pressing Home.
            final String action;
            if (getNavigationModel() == NavigationModel.ZERO_BUTTON) {
                checkForAnomaly();

                final Point displaySize = getRealDisplaySize();

                if (hasLauncherObject(CONTEXT_MENU_RES_ID)) {
                    linearGesture(
                            displaySize.x / 2, displaySize.y - 1,
                            displaySize.x / 2, 0,
                            ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME,
                            false, GestureScope.INSIDE_TO_OUTSIDE);
                    try (LauncherInstrumentation.Closable c = addContextLayer(
                            "Swiped up from context menu to home")) {
                        waitUntilGone(CONTEXT_MENU_RES_ID);
                    }
                }
                if (hasLauncherObject(WORKSPACE_RES_ID)) {
                    log(action = "already at home");
                } else {
                    log("Hierarchy before swiping up to home:");
                    dumpViewHierarchy();
                    log(action = "swiping up to home from " + getVisibleStateMessage());

                    try (LauncherInstrumentation.Closable c = addContextLayer(action)) {
                        swipeToState(
                                displaySize.x / 2, displaySize.y - 1,
                                displaySize.x / 2, 0,
                                ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME, NORMAL_STATE_ORDINAL,
                                hasLauncherObject(By.textStartsWith(""))
                                        ? GestureScope.INSIDE_TO_OUTSIDE
                                        : GestureScope.OUTSIDE);
                    }
                }
            } else {
                log("Hierarchy before clicking home:");
                dumpViewHierarchy();
                log(action = "clicking home button from " + getVisibleStateMessage());
                try (LauncherInstrumentation.Closable c = addContextLayer(action)) {
                    mDevice.waitForIdle();
                    runToState(
                            waitForSystemUiObject("home")::click,
                            NORMAL_STATE_ORDINAL,
                            !hasLauncherObject(WORKSPACE_RES_ID)
                                    && (hasLauncherObject(APPS_RES_ID)
                                    || hasLauncherObject(OVERVIEW_RES_ID)));
                    mDevice.waitForIdle();
                }
            }
            try (LauncherInstrumentation.Closable c = addContextLayer(
                    "performed action to switch to Home - " + action)) {
                return getWorkspace();
            }
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

    /**
     * Gets the Options Popup Menu object if the current state is showing the popup menu. Fails if
     * the launcher is not in that state.
     *
     * @return Options Popup Menu object.
     */
    @NonNull
    public OptionsPopupMenu getOptionsPopupMenu() {
        try (LauncherInstrumentation.Closable c = addContextLayer(
                "want to get context menu object")) {
            return new OptionsPopupMenu(this);
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

    private boolean hasLauncherObject(String resId) {
        return mDevice.hasObject(getLauncherObjectSelector(resId));
    }

    private boolean hasLauncherObject(BySelector selector) {
        return mDevice.hasObject(makeLauncherSelector(selector));
    }

    private BySelector makeLauncherSelector(BySelector selector) {
        return By.copy(selector).pkg(getLauncherPackageName());
    }

    @NonNull
    UiObject2 waitForLauncherObject(String resName) {
        return waitForObjectBySelector(getLauncherObjectSelector(resName));
    }

    @NonNull
    UiObject2 waitForLauncherObject(BySelector selector) {
        return waitForObjectBySelector(makeLauncherSelector(selector));
    }

    @NonNull
    UiObject2 tryWaitForLauncherObject(BySelector selector, long timeout) {
        return tryWaitForObjectBySelector(makeLauncherSelector(selector), timeout);
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

    private static String eventListToString(List<Integer> actualEvents) {
        if (actualEvents.isEmpty()) return "no events";

        return "["
                + actualEvents.stream()
                .map(state -> TestProtocol.stateOrdinalToString(state))
                .collect(Collectors.joining(", "))
                + "]";
    }

    void runToState(Runnable command, int expectedState, boolean requireEvent) {
        if (requireEvent) {
            runToState(command, expectedState);
        } else {
            command.run();
        }
    }

    void runToState(Runnable command, int expectedState) {
        final List<Integer> actualEvents = new ArrayList<>();
        executeAndWaitForEvent(
                command,
                event -> isSwitchToStateEvent(event, expectedState, actualEvents),
                () -> "Failed to receive an event for the state change: expected "
                        + TestProtocol.stateOrdinalToString(expectedState)
                        + ", actual: " + eventListToString(actualEvents));
    }

    private boolean isSwitchToStateEvent(
            AccessibilityEvent event, int expectedState, List<Integer> actualEvents) {
        if (!TestProtocol.SWITCHED_TO_STATE_MESSAGE.equals(event.getClassName())) return false;

        final Bundle parcel = (Bundle) event.getParcelableData();
        final int actualState = parcel.getInt(TestProtocol.STATE_FIELD);
        actualEvents.add(actualState);
        return actualState == expectedState;
    }

    void swipeToState(int startX, int startY, int endX, int endY, int steps, int expectedState,
            GestureScope gestureScope) {
        runToState(
                () -> linearGesture(startX, startY, endX, endY, steps, false, gestureScope),
                expectedState);
    }

    int getBottomGestureSize() {
        return ResourceUtils.getNavbarSize(
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, getResources()) + 1;
    }

    int getBottomGestureMarginInContainer(UiObject2 container) {
        final int bottomGestureStartOnScreen = getRealDisplaySize().y - getBottomGestureSize();
        return container.getVisibleBounds().bottom - bottomGestureStartOnScreen;
    }

    void clickLauncherObject(UiObject2 object) {
        expectEvent(LauncherInstrumentation.EVENT_TOUCH_DOWN);
        expectEvent(LauncherInstrumentation.EVENT_TOUCH_UP);
        object.click();
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
                () -> linearGesture(
                        startX, startY, endX, endY, steps, slowDown, GestureScope.INSIDE),
                event -> TestProtocol.SCROLL_FINISHED_MESSAGE.equals(event.getClassName()),
                () -> "Didn't receive a scroll end message: " + startX + ", " + startY
                        + ", " + endX + ", " + endY);
    }

    // Inject a swipe gesture. Inject exactly 'steps' motion points, incrementing event time by a
    // fixed interval each time.
    void linearGesture(int startX, int startY, int endX, int endY, int steps, boolean slowDown,
            GestureScope gestureScope) {
        log("linearGesture: " + startX + ", " + startY + " -> " + endX + ", " + endY);
        final long downTime = SystemClock.uptimeMillis();
        final Point start = new Point(startX, startY);
        final Point end = new Point(endX, endY);
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, start, gestureScope);
        final long endTime = movePointer(start, end, steps, downTime, slowDown, gestureScope);
        sendPointer(downTime, endTime, MotionEvent.ACTION_UP, end, gestureScope);
    }

    long movePointer(Point start, Point end, int steps, long downTime, boolean slowDown,
            GestureScope gestureScope) {
        long endTime = movePointer(
                downTime, downTime, steps * GESTURE_STEP_MS, start, end, gestureScope);
        if (slowDown) {
            endTime = movePointer(downTime, endTime + GESTURE_STEP_MS, 5 * GESTURE_STEP_MS, end,
                    end, gestureScope);
        }
        return endTime;
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

    void sendPointer(long downTime, long currentTime, int action, Point point,
            GestureScope gestureScope) {
        if (gestureScope != GestureScope.OUTSIDE) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    expectEvent(EVENT_TOUCH_DOWN);
                    break;
                case MotionEvent.ACTION_UP:
                    expectEvent(gestureScope == GestureScope.INSIDE
                            ? EVENT_TOUCH_UP : EVENT_TOUCH_CANCEL);
                    break;
            }
        }

        final MotionEvent event = getMotionEvent(downTime, currentTime, action, point.x, point.y);
        mInstrumentation.getUiAutomation().injectInputEvent(event, true);
        event.recycle();
    }

    long movePointer(long downTime, long startTime, long duration, Point from, Point to,
            GestureScope gestureScope) {
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

            sendPointer(downTime, currentTime, MotionEvent.ACTION_MOVE, point, gestureScope);
        }
        return currentTime;
    }

    public static int getCurrentInteractionMode(Context context) {
        return getSystemIntegerRes(context, "config_navBarInteractionMode");
    }

    @NonNull
    UiObject2 clickAndGet(@NonNull final UiObject2 target, @NonNull String resName) {
        final Point targetCenter = target.getVisibleCenter();
        final long downTime = SystemClock.uptimeMillis();
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, targetCenter, GestureScope.INSIDE);
        final UiObject2 result = waitForLauncherObject(resName);
        sendPointer(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, targetCenter,
                GestureScope.INSIDE);
        return result;
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

    public int getPid() {
        return getTestInfo(TestProtocol.REQUEST_PID).getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
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

    private List<String> getEvents() {
        final ArrayList<String> events = new ArrayList<>();
        try {
            final String logcatTimeParameter =
                    mTimeBeforeFirstLogEvent != null ? " -t " + mTimeBeforeFirstLogEvent : "";
            final String logcatEvents = mDevice.executeShellCommand(
                    "logcat -d --pid=" + getPid() + logcatTimeParameter
                            + " -s " + TestProtocol.TAPL_EVENTS_TAG);
            final Matcher matcher = EVENT_LOG_ENTRY.matcher(logcatEvents);
            while (matcher.find()) {
                events.add(matcher.group("event"));
            }
            return events;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startRecordingEvents() {
        Assert.assertTrue("Already recording events", mExpectedEvents == null);
        mExpectedEvents = new ArrayList<>();
        mTimeBeforeFirstLogEvent = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
                .format(new Date())
                .replaceAll(" ", "");
    }

    private void stopRecordingEvents() {
        mExpectedEvents = null;
    }

    Closable eventsCheck() {
        // Entering events check block.
        startRecordingEvents();

        return () -> {
            // Leaving events check block.
            if (mExpectedEvents == null) {
                return; // There was a failure. Noo need to report another one.
            }

            if (!mCheckEventsForSuccessfulGestures) {
                stopRecordingEvents();
                return;
            }

            final String message = getEventMismatchMessage(true);
            if (message != null) {
                Assert.fail(formatSystemHealthMessage(
                        "http://go/tapl : unexpected event sequence: " + message));
            }
        };
    }

    void expectEvent(Pattern expected) {
        if (mExpectedEvents != null) mExpectedEvents.add(expected);
    }

    private String getEventMismatchMessage(boolean waitForExpectedCount) {
        if (mExpectedEvents == null) return null;

        try {
            List<String> actual = getEvents();

            if (waitForExpectedCount) {
                // Wait until Launcher generates the expected number of events.
                final long endTime = SystemClock.uptimeMillis() + WAIT_TIME_MS;
                while (SystemClock.uptimeMillis() < endTime
                        && actual.size() < mExpectedEvents.size()) {
                    SystemClock.sleep(100);
                    actual = getEvents();
                }
            }

            for (int i = 0; i < mExpectedEvents.size(); ++i) {
                if (i >= actual.size()) {
                    return formatEventMismatchMessage("too few actual events", actual, i);
                }
                if (!mExpectedEvents.get(i).matcher(actual.get(i)).find()) {
                    return formatEventMismatchMessage("a mismatched event", actual, i);
                }
            }

            if (actual.size() > mExpectedEvents.size()) {
                return formatEventMismatchMessage(
                        "too many actual events", actual, mExpectedEvents.size());
            }
        } finally {
            stopRecordingEvents();
        }

        return null;
    }

    private String formatEventList(List events, int position) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < events.size(); ++i) {
            sb.append("\n| ");
            sb.append(i == position ? "---> " : "     ");
            sb.append(events.get(i).toString());
        }
        if (position == events.size()) sb.append("\n| ---> (end)");
        return sb.toString();
    }

    private String formatEventMismatchMessage(String message, List<String> actual, int position) {
        return message + ":"
                + "\nExpected:" + formatEventList(mExpectedEvents, position)
                + "\nActual:" + formatEventList(actual, position);
    }
}