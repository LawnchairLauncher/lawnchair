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

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import com.android.launcher3.TestProtocol;
import com.android.systemui.shared.system.QuickStepContract;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;

/**
 * The main tapl object. The only object that can be explicitly constructed by the using code. It
 * produces all other objects.
 */
public final class LauncherInstrumentation {

    private static final String TAG = "Tapl";
    private static final String NAV_BAR_INTERACTION_MODE_RES_NAME =
            "config_navBarInteractionMode";
    private static final int ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME = 20;

    // Types for launcher containers that the user is interacting with. "Background" is a
    // pseudo-container corresponding to inactive launcher covered by another app.
    enum ContainerType {
        WORKSPACE, ALL_APPS, OVERVIEW, WIDGETS, BACKGROUND, BASE_OVERVIEW
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
    public static final int WAIT_TIME_MS = 60000;
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static WeakReference<VisibleContainer> sActiveContainer = new WeakReference<>(null);

    private final UiDevice mDevice;
    private final Instrumentation mInstrumentation;
    private int mExpectedRotation = Surface.ROTATION_0;
    private final Uri mTestProviderUri;
    private final Deque<String> mDiagnosticContext = new LinkedList<>();

    /**
     * Constructs the root of TAPL hierarchy. You get all other objects from it.
     */
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

        mTestProviderUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authorityPackage + ".TestInfo")
                .build();

        try {
            mDevice.executeShellCommand("pm grant " + testPackage +
                    " android.permission.WRITE_SECURE_SETTINGS");
        } catch (IOException e) {
            fail(e.toString());
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
            final Context ctx = baseContext.createPackageContext("android", 0);
            if (isGesturalMode(ctx)) {
                return NavigationModel.ZERO_BUTTON;
            } else if (isSwipeUpMode(ctx)) {
                return NavigationModel.TWO_BUTTON;
            } else if (isLegacyMode(ctx)) {
                return NavigationModel.THREE_BUTTON;
            } else {
                fail("Can't detect navigation mode");
            }
        } catch (PackageManager.NameNotFoundException e) {
            fail(e.toString());
        }
        return NavigationModel.THREE_BUTTON;
    }

    static boolean needSlowGestures() {
        return Build.MODEL.contains("Cuttlefish");
    }

    static void log(String message) {
        Log.d(TAG, message);
    }

    Closable addContextLayer(String piece) {
        mDiagnosticContext.addLast(piece);
        return () -> mDiagnosticContext.removeLast();
    }

    private void fail(String message) {
        final String ctxt = mDiagnosticContext.isEmpty() ? "" : String.join(", ",
                mDiagnosticContext) + "; ";
        Assert.fail("http://go/tapl : " + ctxt + message);
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

    private void assertEquals(String message, String expected, String actual) {
        if (!TextUtils.equals(expected, actual)) {
            fail(message + " expected: '" + expected + "' but was: '" + actual + "'");
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

    private UiObject2 verifyContainerType(ContainerType containerType) {
        assertEquals("Unexpected display rotation",
                mExpectedRotation, mDevice.getDisplayRotation());
        final NavigationModel navigationModel = getNavigationModel();
        assertTrue("Presence of recents button doesn't match the interaction mode",
                (navigationModel == NavigationModel.THREE_BUTTON) ==
                        mDevice.hasObject(By.res(SYSTEMUI_PACKAGE, "recent_apps")));
        assertTrue("Presence of home button doesn't match the interaction mode",
                (navigationModel != NavigationModel.ZERO_BUTTON) ==
                        mDevice.hasObject(By.res(SYSTEMUI_PACKAGE, "home")));
        log("verifyContainerType: " + containerType);

        try (Closable c = addContextLayer(
                "but the current state is not " + containerType.name())) {
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
        final String action;
        if (getNavigationModel() == NavigationModel.ZERO_BUTTON) {
            if (hasLauncherObject(WORKSPACE_RES_ID)) {
                log(action = "0-button: already in workspace");
            } else if (hasLauncherObject(OVERVIEW_RES_ID)) {
                log(action = "0-button: from overview");
                mDevice.pressHome();
            } else if (hasLauncherObject(WIDGETS_RES_ID)) {
                log(action = "0-button: from widgets");
                mDevice.pressHome();
            } else if (hasLauncherObject(APPS_RES_ID)) {
                log(action = "0-button: from all apps");
                mDevice.pressHome();
            } else {
                log(action = "0-button: from another app");
                assertTrue("Launcher is visible, don't know how to go home",
                        !mDevice.hasObject(By.pkg(getLauncherPackageName())));
                mDevice.pressHome();
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

            // Temporarily press home twice as the first click sometimes gets ignored  (b/124239413)
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

    @NonNull
    UiObject2 waitForSystemUiObject(String resId) {
        final UiObject2 object = mDevice.wait(
                Until.findObject(By.res(SYSTEMUI_PACKAGE, resId)), WAIT_TIME_MS);
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
    List<UiObject2> getObjectsInContainer(UiObject2 container, String resName) {
        return container.findObjects(getLauncherObjectSelector(resName));
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

    @Nullable
    private boolean hasLauncherObject(String resId) {
        return mDevice.hasObject(getLauncherObjectSelector(resId));
    }

    @NonNull
    UiObject2 waitForLauncherObject(String resName) {
        final BySelector selector = getLauncherObjectSelector(resName);
        final UiObject2 object = mDevice.wait(Until.findObject(selector), WAIT_TIME_MS);
        assertNotNull("Can't find a launcher object; selector: " + selector, object);
        return object;
    }

    BySelector getLauncherObjectSelector(String resName) {
        return By.res(getLauncherPackageName(), resName);
    }

    String getLauncherPackageName() {
        return mDevice.getLauncherPackageName();
    }

    @NonNull
    public UiDevice getDevice() {
        return mDevice;
    }

    void swipe(int startX, int startY, int endX, int endY, int expectedState) {
        swipe(startX, startY, endX, endY, expectedState, 60);
    }

    void swipe(int startX, int startY, int endX, int endY, int expectedState, int steps) {
        final Bundle parcel = (Bundle) executeAndWaitForEvent(
                () -> mDevice.swipe(startX, startY, endX, endY, steps),
                event -> TestProtocol.SWITCHED_TO_STATE_MESSAGE.equals(event.getClassName()),
                "Swipe failed to receive an event for the swipe end: " + startX + ", " + startY
                        + ", " + endX + ", " + endY);
        assertEquals("Swipe switched launcher to a wrong state;",
                TestProtocol.stateOrdinalToString(expectedState),
                TestProtocol.stateOrdinalToString(parcel.getInt(TestProtocol.STATE_FIELD)));
    }

    void waitForIdle() {
        mDevice.waitForIdle();
    }

    float getDisplayDensity() {
        return mInstrumentation.getTargetContext().getResources().getDisplayMetrics().density;
    }

    int getTouchSlop() {
        return ViewConfiguration.get(getContext()).getScaledTouchSlop();
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

    void movePointer(long downTime, long duration, Point from, Point to) {
        final Point point = new Point();
        for (; ; ) {
            sleep(16);

            final long currentTime = SystemClock.uptimeMillis();
            final float progress = (currentTime - downTime) / (float) duration;
            if (progress > 1) return;

            point.x = from.x + (int) (progress * (to.x - from.x));
            point.y = from.y + (int) (progress * (to.y - from.y));

            sendPointer(downTime, currentTime, MotionEvent.ACTION_MOVE, point);
        }
    }

    public static boolean isGesturalMode(Context context) {
        return QuickStepContract.isGesturalMode(
                getSystemIntegerRes(context, NAV_BAR_INTERACTION_MODE_RES_NAME));
    }

    public static boolean isSwipeUpMode(Context context) {
        return QuickStepContract.isSwipeUpMode(
                getSystemIntegerRes(context, NAV_BAR_INTERACTION_MODE_RES_NAME));
    }

    public static boolean isLegacyMode(Context context) {
        return QuickStepContract.isLegacyMode(
                getSystemIntegerRes(context, NAV_BAR_INTERACTION_MODE_RES_NAME));
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

    static void sleep(int duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
        }
    }
}