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
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_SCROLL;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.MotionEvent.AXIS_GESTURE_SWIPE_FINGER_COUNT;
import static android.view.Surface.ROTATION_90;

import static com.android.launcher3.tapl.Folder.FOLDER_CONTENT_RES_ID;
import static com.android.launcher3.tapl.TestHelpers.getOverviewPackageName;
import static com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_GET_SPLIT_SELECTION_ACTIVE;
import static com.android.launcher3.testing.shared.TestProtocol.REQUEST_NUM_ALL_APPS_COLUMNS;
import static com.android.launcher3.testing.shared.TestProtocol.TEST_DRAG_APP_ICON_TO_MULTIPLE_WORKSPACES_FAILURE;
import static com.android.launcher3.testing.shared.TestProtocol.TEST_INFO_RESPONSE_FIELD;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.testing.shared.TestInformationRequest;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.systemui.shared.system.QuickStepContract;

import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The main tapl object. The only object that can be explicitly constructed by the using code. It
 * produces all other objects.
 */
public final class LauncherInstrumentation {

    private static final String TAG = "Tapl";
    private static final int ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME = 15;
    private static final int GESTURE_STEP_MS = 16;

    static final Pattern EVENT_PILFER_POINTERS = Pattern.compile("pilferPointers");
    static final Pattern EVENT_START = Pattern.compile("start:");
    private static final Pattern EVENT_KEY_BACK_UP =
            getKeyEventPattern("ACTION_UP", "KEYCODE_BACK");
    private static final Pattern EVENT_ON_BACK_INVOKED = Pattern.compile("onBackInvoked");

    private final String mLauncherPackage;
    private Boolean mIsLauncher3;
    private long mTestStartTime = -1;

    // Types for launcher containers that the user is interacting with. "Background" is a
    // pseudo-container corresponding to inactive launcher covered by another app.
    public enum ContainerType {
        WORKSPACE, HOME_ALL_APPS, OVERVIEW, SPLIT_SCREEN_SELECT, WIDGETS, FALLBACK_OVERVIEW,
        LAUNCHED_APP, TASKBAR_ALL_APPS
    }

    public enum NavigationModel {ZERO_BUTTON, THREE_BUTTON}

    // Defines whether the gesture recognition triggers pilfer.
    public enum GestureScope {
        DONT_EXPECT_PILFER,
        EXPECT_PILFER,
    }

    public enum TrackpadGestureType {
        NONE,
        TWO_FINGER,
        THREE_FINGER,
        FOUR_FINGER
    }

    // Base class for launcher containers.
    abstract static class VisibleContainer {
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

    public interface Closable extends AutoCloseable {
        void close();
    }

    static final String WORKSPACE_RES_ID = "workspace";
    private static final String APPS_RES_ID = "apps_view";
    private static final String OVERVIEW_RES_ID = "overview_panel";
    private static final String WIDGETS_RES_ID = "primary_widgets_list_view";
    private static final String CONTEXT_MENU_RES_ID = "popup_container";
    private static final String OPEN_FOLDER_RES_ID = "folder_content";
    static final String TASKBAR_RES_ID = "taskbar_view";
    private static final String SPLIT_PLACEHOLDER_RES_ID = "split_placeholder";
    static final String KEYBOARD_QUICK_SWITCH_RES_ID = "keyboard_quick_switch_view";
    public static final int WAIT_TIME_MS = 30000;
    static final long DEFAULT_POLL_INTERVAL = 1000;
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String ANDROID_PACKAGE = "android";
    private static final String ASSISTANT_PACKAGE = "com.google.android.googlequicksearchbox";
    private static final String ASSISTANT_GO_HOME_RES_ID = "home_icon";

    private static WeakReference<VisibleContainer> sActiveContainer = new WeakReference<>(null);

    private final UiDevice mDevice;
    private final Instrumentation mInstrumentation;
    private Integer mExpectedRotation = null;
    private boolean mExpectedRotationCheckEnabled = true;
    private final Uri mTestProviderUri;
    private final Deque<String> mDiagnosticContext = new LinkedList<>();
    private Function<Long, String> mSystemHealthSupplier;

    private boolean mIgnoreTaskbarVisibility = false;

    private LogEventChecker mEventChecker;

    // UI anomaly checker provided by the test.
    private Runnable mTestAnomalyChecker;

    private boolean mCheckEventsForSuccessfulGestures = false;
    private Runnable mOnLauncherCrashed;

    private TrackpadGestureType mTrackpadGestureType = TrackpadGestureType.NONE;
    private int mPointerCount = 0;

    private static Pattern getKeyEventPattern(String action, String keyCode) {
        return Pattern.compile("Key event: KeyEvent.*action=" + action + ".*keyCode=" + keyCode);
    }

    /**
     * Constructs the root of TAPL hierarchy. You get all other objects from it.
     */
    public LauncherInstrumentation() {
        this(InstrumentationRegistry.getInstrumentation(), false);
    }

    /**
     * Constructs the root of TAPL hierarchy. You get all other objects from it.
     */
    public LauncherInstrumentation(boolean isLauncherTest) {
        this(InstrumentationRegistry.getInstrumentation(), isLauncherTest);
    }

    /**
     * Constructs the root of TAPL hierarchy. You get all other objects from it.
     *
     * @deprecated use the constructor without Instrumentation parameter instead.
     */
    @Deprecated
    public LauncherInstrumentation(Instrumentation instrumentation) {
        this(instrumentation, false);
    }

    /**
     * Constructs the root of TAPL hierarchy. You get all other objects from it.
     *
     * @deprecated use the constructor without Instrumentation parameter instead.
     */
    @Deprecated
    public LauncherInstrumentation(Instrumentation instrumentation, boolean isLauncherTest) {
        mInstrumentation = instrumentation;
        mDevice = UiDevice.getInstance(instrumentation);

        // Launcher should run in test harness so that custom accessibility protocol between
        // Launcher and TAPL is enabled. In-process tests enable this protocol with a direct call
        // into Launcher.
        assertTrue("Device must run in a test harness. "
                        + "Run `adb shell setprop ro.test_harness 1` to enable it.",
                TestHelpers.isInLauncherProcess() || ActivityManager.isRunningInTestHarness());

        final String testPackage = getContext().getPackageName();
        final String targetPackage = mInstrumentation.getTargetContext().getPackageName();

        // Launcher package. As during inproc tests the tested launcher may not be selected as the
        // current launcher, choosing target package for inproc. For out-of-proc, use the installed
        // launcher package.
        mLauncherPackage = testPackage.equals(targetPackage) || isGradleInstrumentation()
                ? getLauncherPackageName()
                : targetPackage;

        String testProviderAuthority = mLauncherPackage + ".TestInfo";
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

        final int iterations = isLauncherTest ? 300 : 100;

        if (pm.getComponentEnabledSetting(cn) != COMPONENT_ENABLED_STATE_ENABLED) {
            if (TestHelpers.isInLauncherProcess()) {
                pm.setComponentEnabledSetting(cn, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
            } else {
                try {
                    final int userId = getContext().getUserId();
                    final String launcherPidCommand = "pidof " + pi.packageName;
                    final String initialPid = mDevice.executeShellCommand(launcherPidCommand);

                    mDevice.executeShellCommand(
                            "pm enable --user " + userId + " " + cn.flattenToString());

                    // Wait for Launcher restart after enabling test provider.
                    for (int i = 0; i < iterations; ++i) {
                        final String currentPid = mDevice.executeShellCommand(launcherPidCommand)
                                .replaceAll("\\s", "");
                        if (!currentPid.isEmpty() && !currentPid.equals(initialPid)) break;
                        if (i == iterations - 1) {
                            fail("Launcher didn't restart after enabling test provider");
                        }
                        SystemClock.sleep(100);
                    }
                } catch (IOException e) {
                    fail(e.toString());
                }
            }

            // Wait for Launcher content provider to become enabled.
            for (int i = 0; i < iterations; ++i) {
                final ContentProviderClient testProvider = getContext().getContentResolver()
                        .acquireContentProviderClient(mTestProviderUri);
                if (testProvider != null) {
                    testProvider.close();
                    break;
                }
                if (i == iterations - 1) {
                    fail("Launcher content provider is still not enabled");
                }
                SystemClock.sleep(100);
            }
        }
    }

    /**
     * Gradle only supports out of process instrumentation. The test package is automatically
     * generated by appending `.test` to the target package.
     */
    private boolean isGradleInstrumentation() {
        final String testPackage = getContext().getPackageName();
        final String targetPackage = mInstrumentation.getTargetContext().getPackageName();
        final String testSuffix = ".test";

        return testPackage.endsWith(testSuffix) && testPackage.length() > testSuffix.length()
                && testPackage.substring(0, testPackage.length() - testSuffix.length())
                .equals(targetPackage);
    }

    public void enableCheckEventsForSuccessfulGestures() {
        mCheckEventsForSuccessfulGestures = true;
    }

    public void setOnLauncherCrashed(Runnable onLauncherCrashed) {
        mOnLauncherCrashed = onLauncherCrashed;
    }

    Context getContext() {
        return mInstrumentation.getContext();
    }

    Bundle getTestInfo(String request) {
        return getTestInfo(request, /*arg=*/ null);
    }

    Bundle getTestInfo(String request, String arg) {
        return getTestInfo(request, arg, null);
    }

    Bundle getTestInfo(String request, String arg, Bundle extra) {
        try (ContentProviderClient client = getContext().getContentResolver()
                .acquireContentProviderClient(mTestProviderUri)) {
            return client.call(request, arg, extra);
        } catch (DeadObjectException e) {
            fail("Launcher crashed");
            return null;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    Bundle getTestInfo(TestInformationRequest request) {
        Bundle extra = new Bundle();
        extra.putParcelable(TestProtocol.TEST_INFO_REQUEST_FIELD, request);
        return getTestInfo(request.getRequestName(), null, extra);
    }

    Insets getTargetInsets() {
        return getTestInfo(TestProtocol.REQUEST_TARGET_INSETS)
                .getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    Insets getWindowInsets() {
        return getTestInfo(TestProtocol.REQUEST_WINDOW_INSETS)
                .getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    Insets getSystemGestureRegion() {
        return getTestInfo(TestProtocol.REQUEST_SYSTEM_GESTURE_REGION)
                .getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public int getNumAllAppsColumns() {
        return getTestInfo(REQUEST_NUM_ALL_APPS_COLUMNS).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public boolean isTablet() {
        return getTestInfo(TestProtocol.REQUEST_IS_TABLET)
                .getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    private boolean isPredictiveBackSwipeEnabled() {
        return getTestInfo(TestProtocol.REQUEST_IS_PREDICTIVE_BACK_SWIPE_ENABLED)
                .getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public boolean isTaskbarNavbarUnificationEnabled() {
        return getTestInfo(TestProtocol.REQUEST_ENABLE_TASKBAR_NAVBAR_UNIFICATION)
                .getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public boolean isTwoPanels() {
        return getTestInfo(TestProtocol.REQUEST_IS_TWO_PANELS)
                .getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    int getCellLayoutBoarderHeight() {
        return getTestInfo(TestProtocol.REQUEST_CELL_LAYOUT_BOARDER_HEIGHT)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    int getFocusedTaskHeightForTablet() {
        return getTestInfo(TestProtocol.REQUEST_GET_FOCUSED_TASK_HEIGHT_FOR_TABLET).getInt(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    Rect getGridTaskRectForTablet() {
        return ((Rect) getTestInfo(TestProtocol.REQUEST_GET_GRID_TASK_SIZE_RECT_FOR_TABLET)
                .getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD));
    }

    int getOverviewPageSpacing() {
        return getTestInfo(TestProtocol.REQUEST_GET_OVERVIEW_PAGE_SPACING)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public int getOverviewCurrentPageIndex() {
        return getTestInfo(TestProtocol.REQUEST_GET_OVERVIEW_CURRENT_PAGE_INDEX)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    float getExactScreenCenterX() {
        return getRealDisplaySize().x / 2f;
    }

    public void setEnableRotation(boolean on) {
        getTestInfo(TestProtocol.REQUEST_ENABLE_ROTATION, Boolean.toString(on));
    }

    public void setEnableSuggestion(boolean enableSuggestion) {
        getTestInfo(TestProtocol.REQUEST_ENABLE_SUGGESTION, Boolean.toString(enableSuggestion));
    }

    public boolean hadNontestEvents() {
        return getTestInfo(TestProtocol.REQUEST_GET_HAD_NONTEST_EVENTS)
                .getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    void setActiveContainer(VisibleContainer container) {
        sActiveContainer = new WeakReference<>(container);
    }

    /**
     * Sets the accesibility interactive timeout to be effectively indefinite (UI using this
     * accesibility timeout will not automatically dismiss if true).
     */
    void setIndefiniteAccessibilityInteractiveUiTimeout(boolean indefiniteTimeout) {
        final String cmd = indefiniteTimeout
                ? "settings put secure accessibility_interactive_ui_timeout_ms 10000"
                : "settings delete secure accessibility_interactive_ui_timeout_ms";
        logShellCommand(cmd);
    }

    /**
     * Retrieves a resource value from context that defines if nav bar can change position or if it
     * is fixed position regardless of device orientation.
     */
    private boolean getNavBarCanMove() {
        final Context baseContext = mInstrumentation.getTargetContext();
        try {
            final Context ctx = getLauncherContext(baseContext);
            return getNavBarCanMove(ctx);
        } catch (Exception e) {
            fail(e.toString());
        }
        return false;
    }

    public NavigationModel getNavigationModel() {
        final Context baseContext = mInstrumentation.getTargetContext();
        try {
            final Context ctx = getLauncherContext(baseContext);
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

    public void dumpViewHierarchy() {
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

    public String getSystemAnomalyMessage(
            boolean ignoreNavmodeChangeStates, boolean ignoreOnlySystemUiViews) {
        try {
            {
                final StringBuilder sb = new StringBuilder();

                UiObject2 object =
                        mDevice.findObject(By.res("android", "alertTitle").pkg("android"));
                if (object != null) {
                    sb.append("TITLE: ").append(object.getText());
                }

                object = mDevice.findObject(By.res("android", "message").pkg("android"));
                if (object != null) {
                    sb.append(" PACKAGE: ").append(object.getApplicationPackage())
                            .append(" MESSAGE: ").append(object.getText());
                }

                if (sb.length() != 0) {
                    return "System alert popup is visible: " + sb;
                }
            }

            if (hasSystemUiObject("keyguard_status_view")) return "Phone is locked";

            if (!ignoreOnlySystemUiViews) {
                final String visibleApps = mDevice.findObjects(getAnyObjectSelector())
                        .stream()
                        .map(LauncherInstrumentation::getApplicationPackageSafe)
                        .distinct()
                        .filter(pkg -> pkg != null)
                        .collect(Collectors.joining(","));
                if (SYSTEMUI_PACKAGE.equals(visibleApps)) return "Only System UI views are visible";
            }
            if (!ignoreNavmodeChangeStates) {
                if (!mDevice.wait(Until.hasObject(getAnyObjectSelector()), WAIT_TIME_MS)) {
                    return "Screen is empty";
                }
            }

            final String navigationModeError = getNavigationModeMismatchError(true);
            if (navigationModeError != null) return navigationModeError;
        } catch (Throwable e) {
            Log.w(TAG, "getSystemAnomalyMessage failed", e);
        }

        return null;
    }

    private void checkForAnomaly() {
        checkForAnomaly(false, false);
    }

    /**
     * Allows the test to provide a pluggable anomaly checker. It’s supposed to throw an exception
     * if the check fails. The test may provide its own anomaly checker, for example, if it wants to
     * check for an anomaly that’s recognized by the standard TAPL anomaly checker, but wants a
     * custom error message, such as adding information whether the keyguard is seen for the first
     * time during the shard execution.
     */
    public void setAnomalyChecker(Runnable anomalyChecker) {
        mTestAnomalyChecker = anomalyChecker;
    }

    /**
     * Verifies that there are no visible UI anomalies. An "anomaly" is a state of UI that should
     * never happen during the text execution. Anomaly is something different from just “regular”
     * unexpected state of the Launcher such as when we see Workspace after swiping up to All Apps.
     * Workspace is a normal state. We can contrast this with an anomaly, when, for example, we see
     * a lock screen. Launcher tests can never bring the lock screen, so the very presence of the
     * lock screen is an indication that something went very wrong, and perhaps is caused by reasons
     * outside of the Launcher and its tests, perhaps, by a crash in System UI. Diagnosing anomalies
     * helps to understand faster whether the problem is in the Launcher or its tests, or outside.
     */
    public void checkForAnomaly(
            boolean ignoreNavmodeChangeStates, boolean ignoreOnlySystemUiViews) {
        if (mTestAnomalyChecker != null) mTestAnomalyChecker.run();

        final String systemAnomalyMessage =
                getSystemAnomalyMessage(ignoreNavmodeChangeStates, ignoreOnlySystemUiViews);
        if (systemAnomalyMessage != null) {
            Assert.fail(formatSystemHealthMessage(formatErrorWithEvents(
                    "http://go/tapl : Tests are broken by a non-Launcher system error: "
                            + systemAnomalyMessage, false)));
        }
    }

    private String getVisiblePackages() {
        final String apps = mDevice.findObjects(getAnyObjectSelector())
                .stream()
                .map(LauncherInstrumentation::getApplicationPackageSafe)
                .distinct()
                .filter(pkg -> pkg != null && !SYSTEMUI_PACKAGE.equals(pkg))
                .collect(Collectors.joining(", "));
        return !apps.isEmpty()
                ? "active app: " + apps
                : "the test doesn't see views from any app, including Launcher";
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
        if (hasLauncherObject(OPEN_FOLDER_RES_ID)) return "Open Folder";
        if (hasLauncherObject(WIDGETS_RES_ID)) return "Widgets";
        if (hasSystemLauncherObject(OVERVIEW_RES_ID)) return "Overview";
        if (hasLauncherObject(WORKSPACE_RES_ID)) return "Workspace";
        if (hasLauncherObject(APPS_RES_ID)) return "AllApps";
        if (hasLauncherObject(TASKBAR_RES_ID)) return "Taskbar";
        if (hasLauncherObject("wallpaper_carousel")) return "Launcher Settings Popup";
        if (mDevice.hasObject(By.pkg(getLauncherPackageName()).depth(0))) {
            return "<Launcher in invalid state>";
        }
        return "LaunchedApp (" + getVisiblePackages() + ")";
    }

    public void setSystemHealthSupplier(Function<Long, String> supplier) {
        this.mSystemHealthSupplier = supplier;
    }

    public void onTestStart() {
        mTestStartTime = System.currentTimeMillis();
    }

    public void onTestFinish() {
        mTestStartTime = -1;
    }

    private String formatSystemHealthMessage(String message) {
        final String testPackage = getContext().getPackageName();

        mInstrumentation.getUiAutomation().grantRuntimePermission(
                testPackage, "android.permission.READ_LOGS");
        mInstrumentation.getUiAutomation().grantRuntimePermission(
                testPackage, "android.permission.PACKAGE_USAGE_STATS");

        if (mTestStartTime > 0) {
            final String systemHealth = mSystemHealthSupplier != null
                    ? mSystemHealthSupplier.apply(mTestStartTime)
                    : TestHelpers.getSystemHealthMessage(getContext(), mTestStartTime);

            if (systemHealth != null) {
                message += ";\nPerhaps linked to system health problems:\n<<<<<<<<<<<<<<<<<<\n"
                        + systemHealth + "\n>>>>>>>>>>>>>>>>>>";
            }
        }
        Log.d(TAG, "About to throw the error: " + message, new Exception());
        return message;
    }

    private String formatErrorWithEvents(String message, boolean checkEvents) {
        if (mEventChecker != null) {
            final LogEventChecker eventChecker = mEventChecker;
            mEventChecker = null;
            if (checkEvents) {
                final String eventMismatch = eventChecker.verify(0, false);
                if (eventMismatch != null) {
                    message = message + ";\n" + eventMismatch;
                }
            } else {
                eventChecker.finishNoWait();
            }
        }

        dumpDiagnostics(message);

        log("Hierarchy dump for: " + message);
        dumpViewHierarchy();

        return message;
    }

    private void dumpDiagnostics(String message) {
        log("Diagnostics for failure: " + message);
        log("Input:");
        logShellCommand("dumpsys input");
        log("TIS:");
        logShellCommand("dumpsys activity service TouchInteractionService");
    }

    private void logShellCommand(String command) {
        try {
            for (String line : mDevice.executeShellCommand(command).split("\\n")) {
                SystemClock.sleep(10);
                log(line);
            }
        } catch (IOException e) {
            log("Failed to execute " + command);
        }
    }

    void fail(String message) {
        checkForAnomaly();
        Assert.fail(formatSystemHealthMessage(formatErrorWithEvents(
                "http://go/tapl test failure: " + message + ";\nContext: " + getContextDescription()
                        + "; now visible state is " + getVisibleStateMessage(), true)));
    }

    private String getContextDescription() {
        return mDiagnosticContext.isEmpty()
                ? "(no context)" : String.join(", ", mDiagnosticContext);
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

    /**
     * Whether to ignore verifying the task bar visibility during instrumenting.
     *
     * @param ignoreTaskbarVisibility {@code true} will ignore the instrumentation implicitly
     *                                verifying the task bar visibility with
     *                                {@link VisibleContainer#verifyActiveContainer}.
     *                                {@code false} otherwise.
     */
    public void setIgnoreTaskbarVisibility(boolean ignoreTaskbarVisibility) {
        mIgnoreTaskbarVisibility = ignoreTaskbarVisibility;
    }

    /**
     * Set the trackpad gesture type of the interaction.
     *
     * @param trackpadGestureType whether it's not from trackpad, two-finger, three-finger, or
     *                            four-finger gesture.
     */
    public void setTrackpadGestureType(TrackpadGestureType trackpadGestureType) {
        mTrackpadGestureType = trackpadGestureType;
    }

    TrackpadGestureType getTrackpadGestureType() {
        return mTrackpadGestureType;
    }

    /**
     * Sets expected rotation.
     * TAPL periodically checks that Launcher didn't suddenly change the rotation to unexpected one.
     * Null parameter disables checks. The initial state is "no checks".
     */
    public void setExpectedRotation(Integer expectedRotation) {
        mExpectedRotation = expectedRotation;
    }

    public void setExpectedRotationCheckEnabled(boolean expectedRotationCheckEnabled) {
        mExpectedRotationCheckEnabled = expectedRotationCheckEnabled;
    }

    public boolean getExpectedRotationCheckEnabled() {
        return mExpectedRotationCheckEnabled;
    }

    public String getNavigationModeMismatchError(boolean waitForCorrectState) {
        final int waitTime = waitForCorrectState ? WAIT_TIME_MS : 0;
        final NavigationModel navigationModel = getNavigationModel();
        String resPackage = getNavigationButtonResPackage();
        if (navigationModel == NavigationModel.THREE_BUTTON) {
            if (!mDevice.wait(Until.hasObject(By.res(resPackage, "recent_apps")), waitTime)) {
                return "Recents button not present in 3-button mode";
            }
        } else {
            if (!mDevice.wait(Until.gone(By.res(resPackage, "recent_apps")), waitTime)) {
                return "Recents button is present in non-3-button mode";
            }
        }

        if (navigationModel == NavigationModel.ZERO_BUTTON) {
            if (!mDevice.wait(Until.gone(By.res(resPackage, "home")), waitTime)) {
                return "Home button is present in gestural mode";
            }
        } else {
            if (!mDevice.wait(Until.hasObject(By.res(resPackage, "home")), waitTime)) {
                return "Home button not present in non-gestural mode";
            }
        }
        return null;
    }

    private String getNavigationButtonResPackage() {
        return isTablet() || isTaskbarNavbarUnificationEnabled()
                ? getLauncherPackageName() : SYSTEMUI_PACKAGE;
    }

    UiObject2 verifyContainerType(ContainerType containerType) {
        waitForLauncherInitialized();

        if (mExpectedRotationCheckEnabled && mExpectedRotation != null) {
            assertEquals("Unexpected display rotation",
                    mExpectedRotation, mDevice.getDisplayRotation());
        }

        final String error = getNavigationModeMismatchError(true);
        assertTrue(error, error == null);

        log("verifyContainerType: " + containerType);

        final UiObject2 container = verifyVisibleObjects(containerType);

        return container;
    }

    private UiObject2 verifyVisibleObjects(ContainerType containerType) {
        try (Closable c = addContextLayer(
                "but the current state is not " + containerType.name())) {
            switch (containerType) {
                case WORKSPACE: {
                    waitUntilLauncherObjectGone(APPS_RES_ID);
                    waitUntilLauncherObjectGone(WIDGETS_RES_ID);
                    waitUntilSystemLauncherObjectGone(OVERVIEW_RES_ID);
                    waitUntilSystemLauncherObjectGone(SPLIT_PLACEHOLDER_RES_ID);
                    waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);
                    waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);

                    return waitForLauncherObject(WORKSPACE_RES_ID);
                }
                case WIDGETS: {
                    waitUntilLauncherObjectGone(WORKSPACE_RES_ID);
                    waitUntilLauncherObjectGone(APPS_RES_ID);
                    waitUntilSystemLauncherObjectGone(OVERVIEW_RES_ID);
                    waitUntilSystemLauncherObjectGone(SPLIT_PLACEHOLDER_RES_ID);
                    waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);
                    waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);

                    return waitForLauncherObject(WIDGETS_RES_ID);
                }
                case TASKBAR_ALL_APPS: {
                    waitUntilLauncherObjectGone(WORKSPACE_RES_ID);
                    waitUntilLauncherObjectGone(WIDGETS_RES_ID);
                    waitUntilSystemLauncherObjectGone(OVERVIEW_RES_ID);
                    waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);
                    waitUntilSystemLauncherObjectGone(SPLIT_PLACEHOLDER_RES_ID);
                    waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);

                    return waitForLauncherObject(APPS_RES_ID);
                }
                case HOME_ALL_APPS: {
                    waitUntilLauncherObjectGone(WORKSPACE_RES_ID);
                    waitUntilLauncherObjectGone(WIDGETS_RES_ID);
                    waitUntilSystemLauncherObjectGone(OVERVIEW_RES_ID);
                    waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);

                    if (is3PLauncher() && isTablet() && !isTransientTaskbar()) {
                        waitForSystemLauncherObject(TASKBAR_RES_ID);
                    } else {
                        waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);
                    }

                    boolean splitSelectionActive = getTestInfo(REQUEST_GET_SPLIT_SELECTION_ACTIVE)
                            .getBoolean(TEST_INFO_RESPONSE_FIELD);
                    if (!splitSelectionActive) {
                        waitUntilSystemLauncherObjectGone(SPLIT_PLACEHOLDER_RES_ID);
                    } // do nothing, we expect that view

                    return waitForLauncherObject(APPS_RES_ID);
                }
                case OVERVIEW:
                case FALLBACK_OVERVIEW: {
                    waitUntilLauncherObjectGone(APPS_RES_ID);
                    waitUntilLauncherObjectGone(WORKSPACE_RES_ID);
                    waitUntilLauncherObjectGone(WIDGETS_RES_ID);
                    if (isTablet() && !is3PLauncher()) {
                        waitForSystemLauncherObject(TASKBAR_RES_ID);
                    } else {
                        waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);
                    }
                    waitUntilSystemLauncherObjectGone(SPLIT_PLACEHOLDER_RES_ID);
                    waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);

                    return waitForSystemLauncherObject(OVERVIEW_RES_ID);
                }
                case SPLIT_SCREEN_SELECT: {
                    waitUntilLauncherObjectGone(APPS_RES_ID);
                    waitUntilLauncherObjectGone(WORKSPACE_RES_ID);
                    waitUntilLauncherObjectGone(WIDGETS_RES_ID);
                    if (isTablet()) {
                        waitForSystemLauncherObject(TASKBAR_RES_ID);
                    } else {
                        waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);
                    }

                    waitForSystemLauncherObject(SPLIT_PLACEHOLDER_RES_ID);
                    waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);
                    return waitForSystemLauncherObject(OVERVIEW_RES_ID);
                }
                case LAUNCHED_APP: {
                    waitUntilLauncherObjectGone(WORKSPACE_RES_ID);
                    waitUntilLauncherObjectGone(APPS_RES_ID);
                    waitUntilLauncherObjectGone(WIDGETS_RES_ID);
                    waitUntilSystemLauncherObjectGone(OVERVIEW_RES_ID);
                    waitUntilSystemLauncherObjectGone(SPLIT_PLACEHOLDER_RES_ID);
                    waitUntilLauncherObjectGone(KEYBOARD_QUICK_SWITCH_RES_ID);

                    if (mIgnoreTaskbarVisibility) {
                        return null;
                    }

                    if (isTablet()) {
                        // Only check that Persistent Taskbar is visible, since Transient Taskbar
                        // may or may not be visible by design.
                        if (!isTransientTaskbar()) {
                            waitForSystemLauncherObject(TASKBAR_RES_ID);
                        }
                    } else {
                        waitUntilSystemLauncherObjectGone(TASKBAR_RES_ID);
                    }
                    return null;
                }
                default:
                    fail("Invalid state: " + containerType);
                    return null;
            }
        }
    }

    public void waitForModelQueueCleared() {
        getTestInfo(TestProtocol.REQUEST_MODEL_QUEUE_CLEARED);
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
        checkForAnomaly();
        fail("Launcher didn't initialize");
    }

    public boolean isLauncherActivityStarted() {
        return getTestInfo(
                TestProtocol.REQUEST_IS_LAUNCHER_LAUNCHER_ACTIVITY_STARTED).
                getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    Parcelable executeAndWaitForLauncherEvent(Runnable command,
            UiAutomation.AccessibilityEventFilter eventFilter, Supplier<String> message,
            String actionName) {
        return executeAndWaitForEvent(
                command,
                e -> mLauncherPackage.equals(e.getPackageName()) && eventFilter.accept(e),
                message, actionName);
    }

    Parcelable executeAndWaitForEvent(Runnable command,
            UiAutomation.AccessibilityEventFilter eventFilter, Supplier<String> message,
            String actionName) {
        try (LauncherInstrumentation.Closable c = addContextLayer(actionName)) {
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
    }

    void executeAndWaitForLauncherStop(Runnable command, String actionName) {
        executeAndWaitForLauncherEvent(
                () -> command.run(),
                event -> TestProtocol.LAUNCHER_ACTIVITY_STOPPED_MESSAGE
                        .equals(event.getClassName().toString()),
                () -> "Launcher activity didn't stop", actionName);
    }

    /**
     * Get the resource ID of visible floating view.
     */
    private Optional<String> getFloatingResId() {
        if (hasLauncherObject(CONTEXT_MENU_RES_ID)) {
            return Optional.of(CONTEXT_MENU_RES_ID);
        }
        if (hasLauncherObject(FOLDER_CONTENT_RES_ID)) {
            return Optional.of(FOLDER_CONTENT_RES_ID);
        }
        return Optional.empty();
    }

    /**
     * Using swiping up gesture to dismiss closable floating views, such as Menu or Folder Content.
     */
    private void swipeUpToCloseFloatingView() {
        final Point displaySize = getRealDisplaySize();

        final Optional<String> floatingRes = getFloatingResId();

        if (!floatingRes.isPresent()) {
            return;
        }

        if (isLauncher3()) {
            gestureToDismissPopup(displaySize);
        } else {
            runToState(() -> gestureToDismissPopup(displaySize), NORMAL_STATE_ORDINAL, "swiping");
        }

        try (LauncherInstrumentation.Closable c1 = addContextLayer(
                String.format("Swiped up from floating view %s to home", floatingRes.get()))) {
            waitUntilLauncherObjectGone(floatingRes.get());
            waitForLauncherObject(getAnyObjectSelector());
        }
    }

    private void gestureToDismissPopup(Point displaySize) {
        linearGesture(
                displaySize.x / 2, displaySize.y - 1,
                displaySize.x / 2, 0,
                ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME,
                false, GestureScope.EXPECT_PILFER);
    }

    /**
     * @return the Workspace object.
     * @deprecated use goHome().
     * Presses nav bar home button.
     */
    @Deprecated
    public Workspace pressHome() {
        return goHome();
    }

    /**
     * Goes to home from immersive fullscreen app by first swiping up to bring navbar, and then
     * performing {@code goHome()} action.
     *
     * @return the Workspace object.
     */
    public Workspace goHomeFromImmersiveFullscreenApp() {
        final boolean navBarCanMove = getNavBarCanMove();
        if (getNavigationModel() == NavigationModel.ZERO_BUTTON || !navBarCanMove) {
            // in gesture nav we can swipe up at the bottom to bring the navbar handle
            final Point displaySize = getRealDisplaySize();
            linearGesture(
                    displaySize.x / 2, displaySize.y - 1,
                    displaySize.x / 2, 0,
                    ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME,
                    false, GestureScope.EXPECT_PILFER);
        } else {
            // in 3 button nav we swipe up on the side edge of the screen to bring the navbar
            final boolean rotated90degrees = mDevice.getDisplayRotation() == ROTATION_90;
            final Point displaySize = getRealDisplaySize();
            final int startX = rotated90degrees ? displaySize.x : 0;
            final int endX = rotated90degrees ? 0 : displaySize.x;
            linearGesture(
                    startX, displaySize.y / 2,
                    endX, displaySize.y / 2,
                    ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME,
                    false, GestureScope.EXPECT_PILFER);
        }
        return goHome();
    }

    /**
     * Goes to home by swiping up in zero-button mode or pressing Home button.
     * Calling it after another TAPL call is safe because all TAPL methods wait for the animations
     * to finish.
     * When calling it after a non-TAPL method, make sure that all animations have already
     * completed, otherwise it may detect the current state (for example "Application" or "Home")
     * incorrectly.
     * The method expects either app or Launcher to be active when it's called. Other states, such
     * as visible notification shade are not supported.
     *
     * @return the Workspace object.
     */
    public Workspace goHome() {
        try (LauncherInstrumentation.Closable e = eventsCheck();
             LauncherInstrumentation.Closable c = addContextLayer("want to switch to home")) {
            waitForLauncherInitialized();
            // Click home, then wait for any accessibility event, then wait until accessibility
            // events stop.
            // We need waiting for any accessibility event generated after pressing Home because
            // otherwise waitForIdle may return immediately in case when there was a big enough
            // pause in accessibility events prior to pressing Home.
            boolean isThreeFingerTrackpadGesture =
                    mTrackpadGestureType == TrackpadGestureType.THREE_FINGER;
            final String action;
            if (getNavigationModel() == NavigationModel.ZERO_BUTTON
                    || isThreeFingerTrackpadGesture) {
                checkForAnomaly(false, true);

                final Point displaySize = getRealDisplaySize();

                // CLose floating views before going back to home.
                swipeUpToCloseFloatingView();

                if (hasLauncherObject(WORKSPACE_RES_ID)) {
                    log(action = "already at home");
                } else {
                    action = "swiping up to home";

                    int startY = isThreeFingerTrackpadGesture ? displaySize.y * 3 / 4
                            : displaySize.y - 1;
                    int endY = isThreeFingerTrackpadGesture ? displaySize.y / 4 : displaySize.y / 2;
                    swipeToState(
                            displaySize.x / 2, startY,
                            displaySize.x / 2, endY,
                            ZERO_BUTTON_STEPS_FROM_BACKGROUND_TO_HOME, NORMAL_STATE_ORDINAL,
                            GestureScope.EXPECT_PILFER);
                }
            } else {
                log("Hierarchy before clicking home:");
                dumpViewHierarchy();
                action = "clicking home button";
                Log.d(TEST_DRAG_APP_ICON_TO_MULTIPLE_WORKSPACES_FAILURE,
                        "LauncherInstrumentation.goHome: isThreeFingerTrackpadGesture: "
                                + isThreeFingerTrackpadGesture
                                + "getNavigationModel() == NavigationModel.ZERO_BUTTON: " + (
                                getNavigationModel() == NavigationModel.ZERO_BUTTON));
                runToState(
                        getHomeButton()::click,
                        NORMAL_STATE_ORDINAL,
                        !hasLauncherObject(WORKSPACE_RES_ID)
                                && (hasLauncherObject(APPS_RES_ID)
                                || hasSystemLauncherObject(OVERVIEW_RES_ID)),
                        action);
            }
            try (LauncherInstrumentation.Closable c1 = addContextLayer(
                    "performed action to switch to Home - " + action)) {
                return getWorkspace();
            }
        }
    }

    /**
     * Press navbar back button or swipe back if in gesture navigation mode.
     */
    public void pressBack() {
        try (Closable e = eventsCheck(); Closable c = addContextLayer("want to press back")) {
            pressBackImpl();
        }
    }

    void pressBackImpl() {
        waitForLauncherInitialized();
        final boolean launcherVisible =
                isTablet() ? isLauncherContainerVisible() : isLauncherVisible();
        boolean isThreeFingerTrackpadGesture =
                mTrackpadGestureType == TrackpadGestureType.THREE_FINGER;
        if (getNavigationModel() == NavigationModel.ZERO_BUTTON
                || isThreeFingerTrackpadGesture) {
            final Point displaySize = getRealDisplaySize();
            // TODO(b/225505986): change startY and endY back to displaySize.y / 2 once the
            //  issue is solved.
            int startX = isThreeFingerTrackpadGesture ? displaySize.x / 4 : 0;
            int endX = isThreeFingerTrackpadGesture ? displaySize.x * 3 / 4 : displaySize.x / 2;
            linearGesture(startX, displaySize.y / 4, endX, displaySize.y / 4,
                    10, false, GestureScope.DONT_EXPECT_PILFER);
        } else {
            waitForNavigationUiObject("back").click();
        }
        if (launcherVisible) {
            if (isPredictiveBackSwipeEnabled()) {
                expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_ON_BACK_INVOKED);
            } else {
                expectEvent(TestProtocol.SEQUENCE_MAIN, EVENT_KEY_BACK_UP);
            }
        }
    }

    private static BySelector getAnyObjectSelector() {
        return By.textStartsWith("");
    }

    boolean isLauncherVisible() {
        mDevice.waitForIdle();
        return hasLauncherObject(getAnyObjectSelector());
    }

    boolean isLauncherContainerVisible() {
        final String[] containerResources = {WORKSPACE_RES_ID, OVERVIEW_RES_ID, APPS_RES_ID};
        return Arrays.stream(containerResources).anyMatch(
                r -> r.equals(OVERVIEW_RES_ID) ? hasSystemLauncherObject(r) : hasLauncherObject(r));
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
     * Gets the LaunchedApp object if another app is active. Fails if the launcher is not in that
     * state.
     *
     * @return LaunchedApp object.
     */
    @NonNull
    public LaunchedAppState getLaunchedAppState() {
        return new LaunchedAppState(this);
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
     * Gets the homescreen All Apps object if the current state is showing the all apps panel opened
     * by swiping from workspace. Fails if the launcher is not in that state. Please don't call this
     * method if App Apps was opened by swiping up from Overview, as it won't fail and will return
     * an incorrect object.
     *
     * @return Home All Apps object.
     */
    @NonNull
    public HomeAllApps getAllApps() {
        try (LauncherInstrumentation.Closable c = addContextLayer("want to get all apps object")) {
            return new HomeAllApps(this);
        }
    }

    LaunchedAppState assertAppLaunched(@NonNull String expectedPackageName) {
        BySelector packageSelector = By.pkg(expectedPackageName);
        assertTrue("App didn't start: (" + packageSelector + ")",
                mDevice.wait(Until.hasObject(packageSelector),
                        LauncherInstrumentation.WAIT_TIME_MS));
        return new LaunchedAppState(this);
    }

    void waitUntilLauncherObjectGone(String resId) {
        waitUntilGoneBySelector(getLauncherObjectSelector(resId));
    }

    void waitUntilOverviewObjectGone(String resId) {
        waitUntilGoneBySelector(getOverviewObjectSelector(resId));
    }

    void waitUntilSystemLauncherObjectGone(String resId) {
        if (is3PLauncher()) {
            waitUntilOverviewObjectGone(resId);
        } else {
            waitUntilLauncherObjectGone(resId);
        }
    }

    void waitUntilLauncherObjectGone(BySelector selector) {
        waitUntilGoneBySelector(makeLauncherSelector(selector));
    }

    private void waitUntilGoneBySelector(BySelector launcherSelector) {
        assertTrue("Unexpected launcher object visible: " + launcherSelector,
                mDevice.wait(Until.gone(launcherSelector),
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
    UiObject2 waitForSystemUiObject(BySelector selector) {
        final UiObject2 object = TestHelpers.wait(
                Until.findObject(selector), WAIT_TIME_MS);
        assertNotNull("Can't find a systemui object with selector: " + selector, object);
        return object;
    }

    @NonNull
    private UiObject2 getHomeButton() {
        UiModeManager uiManager =
                (UiModeManager) getContext().getSystemService(Context.UI_MODE_SERVICE);
        if (uiManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR) {
            return waitForAssistantHomeButton();
        } else {
            return waitForNavigationUiObject("home");
        }
    }

    /* Assistant Home button is present when system is in car mode. */
    @NonNull
    UiObject2 waitForAssistantHomeButton() {
        final UiObject2 object = mDevice.wait(
                Until.findObject(By.res(ASSISTANT_PACKAGE, ASSISTANT_GO_HOME_RES_ID)),
                WAIT_TIME_MS);
        assertNotNull(
                "Can't find an assistant UI object with id: " + ASSISTANT_GO_HOME_RES_ID, object);
        return object;
    }

    @NonNull
    UiObject2 waitForNavigationUiObject(String resId) {
        String resPackage = getNavigationButtonResPackage();
        final UiObject2 object = mDevice.wait(
                Until.findObject(By.res(resPackage, resId)), WAIT_TIME_MS);
        assertNotNull("Can't find a navigation UI object with id: " + resId, object);
        return object;
    }

    @Nullable
    UiObject2 findObjectInContainer(UiObject2 container, String resName) {
        try {
            return container.findObject(getLauncherObjectSelector(resName));
        } catch (StaleObjectException e) {
            fail("The container disappeared from screen");
            return null;
        }
    }

    @Nullable
    UiObject2 findObjectInContainer(UiObject2 container, BySelector selector) {
        try {
            return container.findObject(selector);
        } catch (StaleObjectException e) {
            fail("The container disappeared from screen");
            return null;
        }
    }

    @NonNull
    List<UiObject2> getObjectsInContainer(UiObject2 container, String resName) {
        try {
            return container.findObjects(getLauncherObjectSelector(resName));
        } catch (StaleObjectException e) {
            fail("The container disappeared from screen");
            return null;
        }
    }

    @NonNull
    UiObject2 waitForObjectInContainer(UiObject2 container, String resName) {
        try {
            final UiObject2 object = container.wait(
                    Until.findObject(getLauncherObjectSelector(resName)),
                    WAIT_TIME_MS);
            assertNotNull("Can't find a view in Launcher, id: " + resName + " in container: "
                    + container.getResourceName(), object);
            return object;
        } catch (StaleObjectException e) {
            fail("The container disappeared from screen");
            return null;
        }
    }

    void waitForObjectEnabled(UiObject2 object, String waitReason) {
        try {
            assertTrue("Timed out waiting for object to be enabled for " + waitReason + " "
                            + object.getResourceName(),
                    object.wait(Until.enabled(true), WAIT_TIME_MS));
        } catch (StaleObjectException e) {
            fail("The object disappeared from screen");
        }
    }

    void waitForObjectFocused(UiObject2 object, String waitReason) {
        try {
            assertTrue("Timed out waiting for object to be focused for " + waitReason + " "
                            + object.getResourceName(),
                    object.wait(Until.focused(true), WAIT_TIME_MS));
        } catch (StaleObjectException e) {
            fail("The object disappeared from screen");
        }
    }

    @NonNull
    UiObject2 waitForObjectInContainer(UiObject2 container, BySelector selector) {
        return waitForObjectsInContainer(container, selector).get(0);
    }

    @NonNull
    List<UiObject2> waitForObjectsInContainer(
            UiObject2 container, BySelector selector) {
        try {
            final List<UiObject2> objects = container.wait(
                    Until.findObjects(selector),
                    WAIT_TIME_MS);
            assertNotNull("Can't find views in Launcher, id: " + selector + " in container: "
                    + container.getResourceName(), objects);
            assertTrue("Can't find views in Launcher, id: " + selector + " in container: "
                    + container.getResourceName(), objects.size() > 0);
            return objects;
        } catch (StaleObjectException e) {
            fail("The container disappeared from screen");
            return null;
        }
    }

    List<UiObject2> getChildren(UiObject2 container) {
        try {
            return container.getChildren();
        } catch (StaleObjectException e) {
            fail("The container disappeared from screen");
            return null;
        }
    }

    private boolean hasLauncherObject(String resId) {
        return mDevice.hasObject(getLauncherObjectSelector(resId));
    }

    private boolean hasSystemLauncherObject(String resId) {
        return mDevice.hasObject(is3PLauncher() ? getOverviewObjectSelector(resId)
                : getLauncherObjectSelector(resId));
    }

    boolean hasLauncherObject(BySelector selector) {
        return mDevice.hasObject(makeLauncherSelector(selector));
    }

    private BySelector makeLauncherSelector(BySelector selector) {
        return By.copy(selector).pkg(getLauncherPackageName());
    }

    @NonNull
    UiObject2 waitForOverviewObject(String resName) {
        return waitForObjectBySelector(getOverviewObjectSelector(resName));
    }

    @NonNull
    UiObject2 waitForLauncherObject(String resName) {
        Log.d(TEST_DRAG_APP_ICON_TO_MULTIPLE_WORKSPACES_FAILURE,
                "LauncherInstrumentation.waitForLauncherObject");
        return waitForObjectBySelector(getLauncherObjectSelector(resName));
    }

    @NonNull
    UiObject2 waitForSystemLauncherObject(String resName) {
        return is3PLauncher() ? waitForOverviewObject(resName)
                : waitForLauncherObject(resName);
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
    UiObject2 waitForAndroidObject(String resId) {
        final UiObject2 object = TestHelpers.wait(
                Until.findObject(By.res(ANDROID_PACKAGE, resId)), WAIT_TIME_MS);
        assertNotNull("Can't find a android object with id: " + resId, object);
        return object;
    }

    @NonNull
    List<UiObject2> waitForObjectsBySelector(BySelector selector) {
        Log.d(TEST_DRAG_APP_ICON_TO_MULTIPLE_WORKSPACES_FAILURE,
                "LauncherInstrumentation.waitForObjectsBySelector");
        final List<UiObject2> objects = mDevice.wait(Until.findObjects(selector), WAIT_TIME_MS);
        assertNotNull("Can't find any view in Launcher, selector: " + selector, objects);
        return objects;
    }

    private UiObject2 waitForObjectBySelector(BySelector selector) {
        Log.d(TEST_DRAG_APP_ICON_TO_MULTIPLE_WORKSPACES_FAILURE,
                "LauncherInstrumentation.waitForObjectBySelector");
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

    boolean is3PLauncher() {
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

    void runToState(Runnable command, int expectedState, boolean requireEvent, String actionName) {
        if (requireEvent) {
            Log.d(TEST_DRAG_APP_ICON_TO_MULTIPLE_WORKSPACES_FAILURE,
                    "LauncherInstrumentation.runToState: command: " + command + " expectedState: "
                            + expectedState + " actionName: " + actionName + "requireEvent: true");
            runToState(command, expectedState, actionName);
        } else {
            command.run();
        }
    }

    /** Run an action and wait for the specified Launcher state. */
    public void runToState(Runnable command, int expectedState, String actionName) {
        final List<Integer> actualEvents = new ArrayList<>();
        executeAndWaitForLauncherEvent(
                command,
                event -> isSwitchToStateEvent(event, expectedState, actualEvents),
                () -> "Failed to receive an event for the state change: expected ["
                        + TestProtocol.stateOrdinalToString(expectedState)
                        + "], actual: " + eventListToString(actualEvents),
                actionName);
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
                expectedState,
                "swiping");
    }

    int getBottomGestureSize() {
        return Math.max(getWindowInsets().bottom, ResourceUtils.getNavbarSize(
                ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE, getResources())) + 1;
    }

    int getBottomGestureMarginInContainer(UiObject2 container) {
        final int bottomGestureStartOnScreen = getBottomGestureStartOnScreen();
        return getVisibleBounds(container).bottom - bottomGestureStartOnScreen;
    }

    int getRightGestureMarginInContainer(UiObject2 container) {
        final int rightGestureStartOnScreen = getRightGestureStartOnScreen();
        return getVisibleBounds(container).right - rightGestureStartOnScreen;
    }

    int getBottomGestureStartOnScreen() {
        return getRealDisplaySize().y - getBottomGestureSize();
    }

    int getRightGestureStartOnScreen() {
        return getRealDisplaySize().x - getWindowInsets().right - 1;
    }

    /**
     * Click on the ui object right away without waiting for animation.
     *
     * [UiObject2.click] would wait for all animations finished before clicking. Not waiting for
     * animations because in some scenarios there is a playing animations when the click is
     * attempted.
     */
    void clickObject(UiObject2 uiObject) {
        final long clickTime = SystemClock.uptimeMillis();
        final Point center = uiObject.getVisibleCenter();
        sendPointer(clickTime, clickTime, MotionEvent.ACTION_DOWN, center,
                GestureScope.DONT_EXPECT_PILFER);
        sendPointer(clickTime, clickTime, MotionEvent.ACTION_UP, center,
                GestureScope.DONT_EXPECT_PILFER);
    }

    void clickLauncherObject(UiObject2 object) {
        clickObject(object);
    }

    void scrollToLastVisibleRow(
            UiObject2 container, Rect bottomVisibleIconBounds, int topPaddingInContainer,
            int appsListBottomPadding) {
        final int itemRowCurrentTopOnScreen = bottomVisibleIconBounds.top;
        final Rect containerRect = getVisibleBounds(container);
        final int itemRowNewTopOnScreen = containerRect.top + topPaddingInContainer;
        final int distance = itemRowCurrentTopOnScreen - itemRowNewTopOnScreen + getTouchSlop();

        scrollDownByDistance(container, distance, appsListBottomPadding);
    }

    void scrollDownByDistance(UiObject2 container, int distance) {
        scrollDownByDistance(container, distance, 0);
    }

    void scrollDownByDistance(UiObject2 container, int distance, int bottomPadding) {
        final Rect containerRect = getVisibleBounds(container);
        final int bottomGestureMarginInContainer = getBottomGestureMarginInContainer(container);
        scroll(
                container,
                Direction.DOWN,
                new Rect(
                        0,
                        containerRect.height() - distance - bottomGestureMarginInContainer,
                        0,
                        bottomGestureMarginInContainer + bottomPadding),
                /* steps= */ 10,
                /* slowDown= */ true);
    }

    void scrollLeftByDistance(UiObject2 container, int distance) {
        final Rect containerRect = getVisibleBounds(container);
        final int rightGestureMarginInContainer = getRightGestureMarginInContainer(container);
        final int leftGestureMargin = getTargetInsets().left + getEdgeSensitivityWidth();
        scroll(
                container,
                Direction.LEFT,
                new Rect(leftGestureMargin,
                        0,
                        Math.max(containerRect.width() - distance - leftGestureMargin,
                                rightGestureMarginInContainer),
                        0),
                10,
                true);
    }

    void scroll(
            UiObject2 container, Direction direction, Rect margins, int steps, boolean slowDown) {
        final Rect rect = getVisibleBounds(container);
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

        executeAndWaitForLauncherEvent(
                () -> linearGesture(
                        startX, startY, endX, endY, steps, slowDown,
                        GestureScope.DONT_EXPECT_PILFER),
                event -> TestProtocol.SCROLL_FINISHED_MESSAGE.equals(event.getClassName()),
                () -> "Didn't receive a scroll end message: " + startX + ", " + startY
                        + ", " + endX + ", " + endY,
                "scrolling");
    }

    void pointerScroll(float pointerX, float pointerY, Direction direction) {
        executeAndWaitForLauncherEvent(
                () -> injectEvent(getPointerMotionEvent(
                        ACTION_SCROLL, pointerX, pointerY, direction)),
                event -> TestProtocol.SCROLL_FINISHED_MESSAGE.equals(event.getClassName()),
                () -> "Didn't receive a scroll end message: " + direction + " scroll from ("
                        + pointerX + ", " + pointerY + ")",
                "scrolling");
    }

    // Inject a swipe gesture. Inject exactly 'steps' motion points, incrementing event time by a
    // fixed interval each time.
    public void linearGesture(int startX, int startY, int endX, int endY, int steps,
            boolean slowDown, GestureScope gestureScope) {
        log("linearGesture: " + startX + ", " + startY + " -> " + endX + ", " + endY);
        final long downTime = SystemClock.uptimeMillis();
        final Point start = new Point(startX, startY);
        final Point end = new Point(endX, endY);
        long endTime = downTime;
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, start, gestureScope);
        try {

            if (mTrackpadGestureType != TrackpadGestureType.NONE) {
                sendPointer(downTime, downTime,
                        getPointerAction(MotionEvent.ACTION_POINTER_DOWN, 1),
                        start, gestureScope);
                if (mTrackpadGestureType == TrackpadGestureType.THREE_FINGER
                        || mTrackpadGestureType == TrackpadGestureType.FOUR_FINGER) {
                    sendPointer(downTime, downTime,
                            getPointerAction(MotionEvent.ACTION_POINTER_DOWN, 2),
                            start, gestureScope);
                    if (mTrackpadGestureType == TrackpadGestureType.FOUR_FINGER) {
                        sendPointer(downTime, downTime,
                                getPointerAction(MotionEvent.ACTION_POINTER_DOWN, 3),
                                start, gestureScope);
                    }
                }
            }
            endTime = movePointer(
                    start, end, steps, false, downTime, downTime, slowDown, gestureScope);
        } finally {
            if (mTrackpadGestureType != TrackpadGestureType.NONE) {
                for (int i = mPointerCount; i >= 2; i--) {
                    sendPointer(downTime, downTime,
                            getPointerAction(MotionEvent.ACTION_POINTER_UP, i - 1),
                            start, gestureScope);
                }
            }
            sendPointer(downTime, endTime, MotionEvent.ACTION_UP, end, gestureScope);
        }
    }

    private static int getPointerAction(int action, int index) {
        return action + (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }

    long movePointer(Point start, Point end, int steps, boolean isDecelerating, long downTime,
            long startTime, boolean slowDown, GestureScope gestureScope) {
        long endTime = movePointer(downTime, startTime, steps * GESTURE_STEP_MS,
                isDecelerating, start, end, gestureScope);
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

    private static MotionEvent getPointerMotionEvent(
            int action, float x, float y, Direction direction) {
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[1];
        coordinates[0] = new MotionEvent.PointerCoords();
        coordinates[0].x = x;
        coordinates[0].y = y;
        boolean isVertical = direction == Direction.UP || direction == Direction.DOWN;
        boolean isForward = direction == Direction.RIGHT || direction == Direction.DOWN;
        coordinates[0].setAxisValue(
                isVertical ? MotionEvent.AXIS_VSCROLL : MotionEvent.AXIS_HSCROLL,
                isForward ? 1f : -1f);

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        properties[0] = new MotionEvent.PointerProperties();
        properties[0].id = 0;
        properties[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

        final long downTime = SystemClock.uptimeMillis();
        return MotionEvent.obtain(
                downTime,
                downTime,
                action,
                /* pointerCount= */ 1,
                properties,
                coordinates,
                /* metaState= */ 0,
                /* buttonState= */ 0,
                /* xPrecision= */ 1f,
                /* yPrecision= */ 1f,
                /* deviceId= */ 0,
                /* edgeFlags= */ 0,
                InputDevice.SOURCE_CLASS_POINTER,
                /* flags= */ 0);
    }

    private static MotionEvent getTrackpadMotionEvent(long downTime, long eventTime,
            int action, float x, float y, int pointerCount, TrackpadGestureType gestureType) {
        MotionEvent.PointerProperties[] pointerProperties =
                new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[pointerCount];
        boolean isMultiFingerGesture = gestureType != TrackpadGestureType.TWO_FINGER;
        for (int i = 0; i < pointerCount; i++) {
            pointerProperties[i] = getPointerProperties(i);
            pointerCoords[i] = getPointerCoords(x, y);
            if (isMultiFingerGesture) {
                pointerCoords[i].setAxisValue(AXIS_GESTURE_SWIPE_FINGER_COUNT,
                        gestureType == TrackpadGestureType.THREE_FINGER ? 3 : 4);
            }
        }
        return MotionEvent.obtain(downTime, eventTime, action, pointerCount, pointerProperties,
                pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_CLASS_POINTER, 0, 0,
                isMultiFingerGesture ? MotionEvent.CLASSIFICATION_MULTI_FINGER_SWIPE
                        : MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE);
    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action,
            float x, float y, int source, int toolType) {
        return MotionEvent.obtain(downTime, eventTime, action, 1,
                new MotionEvent.PointerProperties[]{getPointerProperties(0, toolType)},
                new MotionEvent.PointerCoords[]{getPointerCoords(x, y)},
                0, 0, 1.0f, 1.0f, 0, 0, source, 0);
    }

    private static MotionEvent.PointerProperties getPointerProperties(int pointerId) {
        return getPointerProperties(pointerId, Configurator.getInstance().getToolType());
    }

    private static MotionEvent.PointerProperties getPointerProperties(int pointerId, int toolType) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = pointerId;
        properties.toolType = toolType;
        return properties;
    }

    private static MotionEvent.PointerCoords getPointerCoords(float x, float y) {
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.pressure = 1;
        coords.size = 1;
        coords.x = x;
        coords.y = y;
        return coords;
    }

    private boolean hasTIS() {
        return getTestInfo(TestProtocol.REQUEST_HAS_TIS).getBoolean(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public boolean isGridOnlyOverviewEnabled() {
        return getTestInfo(TestProtocol.REQUEST_FLAG_ENABLE_GRID_ONLY_OVERVIEW).getBoolean(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    boolean isAppPairsEnabled() {
        return getTestInfo(TestProtocol.REQUEST_FLAG_ENABLE_APP_PAIRS).getBoolean(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public void sendPointer(long downTime, long currentTime, int action, Point point,
            GestureScope gestureScope) {
        sendPointer(downTime, currentTime, action, point, gestureScope,
                InputDevice.SOURCE_TOUCHSCREEN, false);
    }

    private void injectEvent(InputEvent event) {
        assertTrue("injectInputEvent failed: event=" + event,
                mInstrumentation.getUiAutomation().injectInputEvent(event, true, false));
    }

    public void sendPointer(long downTime, long currentTime, int action, Point point,
            GestureScope gestureScope, int source) {
        sendPointer(downTime, currentTime, action, point, gestureScope, source, false);
    }

    public void sendPointer(long downTime, long currentTime, int action, Point point,
            GestureScope gestureScope, int source, boolean isRightClick) {
        sendPointer(
                downTime,
                currentTime,
                action,
                point,
                gestureScope,
                source,
                isRightClick,
                Configurator.getInstance().getToolType());
    }

    public void sendPointer(long downTime, long currentTime, int action, Point point,
            GestureScope gestureScope, int source, boolean isRightClick, int toolType) {
        final boolean hasTIS = hasTIS();
        int pointerCount = mPointerCount;

        boolean isTrackpadGesture = mTrackpadGestureType != TrackpadGestureType.NONE;
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                if (isTrackpadGesture) {
                    mPointerCount = 1;
                    pointerCount = mPointerCount;
                }
                Log.d(TEST_DRAG_APP_ICON_TO_MULTIPLE_WORKSPACES_FAILURE,
                        "LauncherInstrumentation.sendPointer: ACTION_DOWN");
                break;
            case MotionEvent.ACTION_UP:
                if (hasTIS && gestureScope == GestureScope.EXPECT_PILFER) {
                    expectEvent(TestProtocol.SEQUENCE_PILFER, EVENT_PILFER_POINTERS);
                }
                Log.d(TEST_DRAG_APP_ICON_TO_MULTIPLE_WORKSPACES_FAILURE,
                        "LauncherInstrumentation.sendPointer: ACTION_UP");
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mPointerCount++;
                pointerCount = mPointerCount;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                // When the gesture is handled outside, it's cancelled within launcher.
                mPointerCount--;
                break;
        }

        final MotionEvent event = isTrackpadGesture
                ? getTrackpadMotionEvent(
                downTime, currentTime, action, point.x, point.y, pointerCount,
                mTrackpadGestureType)
                : getMotionEvent(downTime, currentTime, action, point.x, point.y, source, toolType);
        if (action == MotionEvent.ACTION_BUTTON_PRESS
                || action == MotionEvent.ACTION_BUTTON_RELEASE) {
            event.setActionButton(MotionEvent.BUTTON_PRIMARY);
        }
        if (isRightClick) {
            event.setButtonState(event.getButtonState() | MotionEvent.BUTTON_SECONDARY);
        }
        injectEvent(event);
    }

    private KeyEvent createKeyEvent(int keyCode, int metaState, boolean actionDown) {
        long eventTime = SystemClock.uptimeMillis();
        return KeyEvent.obtain(
                eventTime,
                eventTime,
                actionDown ? ACTION_DOWN : ACTION_UP,
                keyCode,
                /* repeat= */ 0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                /* scancode= */ 0,
                /* flags= */ 0,
                InputDevice.SOURCE_KEYBOARD,
                /* characters =*/ null);
    }

    /**
     * Sends a {@link KeyEvent} with {@link ACTION_DOWN} for the given key codes without sending
     * a {@link KeyEvent} with {@link ACTION_UP}.
     */
    public void pressAndHoldKeyCode(int keyCode, int metaState) {
        injectEvent(createKeyEvent(keyCode, metaState, true));
    }


    /**
     * Sends a {@link KeyEvent} with {@link ACTION_UP} for the given key codes.
     */
    public void unpressKeyCode(int keyCode, int metaState) {
        injectEvent(createKeyEvent(keyCode, metaState, false));
    }

    public long movePointer(long downTime, long startTime, long duration, Point from, Point to,
            GestureScope gestureScope) {
        return movePointer(downTime, startTime, duration, false, from, to, gestureScope);
    }

    public long movePointer(long downTime, long startTime, long duration, boolean isDecelerating,
            Point from, Point to, GestureScope gestureScope) {
        log("movePointer: " + from + " to " + to);
        final Point point = new Point();
        long steps = duration / GESTURE_STEP_MS;

        long currentTime = startTime;

        if (isDecelerating) {
            // formula: V = V0 - D*T, assuming V = 0 when T = duration

            // vx0: initial speed at the x-dimension, set as twice the avg speed
            // dx: the constant deceleration at the x-dimension
            double vx0 = 2.0 * (to.x - from.x) / duration;
            double dx = vx0 / duration;
            // vy0: initial speed at the y-dimension, set as twice the avg speed
            // dy: the constant deceleration at the y-dimension
            double vy0 = 2.0 * (to.y - from.y) / duration;
            double dy = vy0 / duration;

            for (long i = 0; i < steps; ++i) {
                sleep(GESTURE_STEP_MS);
                currentTime += GESTURE_STEP_MS;

                // formula: P = P0 + V0*T - (D*T^2/2)
                final double t = (i + 1) * GESTURE_STEP_MS;
                point.x = from.x + (int) (vx0 * t - 0.5 * dx * t * t);
                point.y = from.y + (int) (vy0 * t - 0.5 * dy * t * t);

                sendPointer(downTime, currentTime, MotionEvent.ACTION_MOVE, point, gestureScope);
            }
        } else {
            for (long i = 0; i < steps; ++i) {
                sleep(GESTURE_STEP_MS);
                currentTime += GESTURE_STEP_MS;

                final float progress = (currentTime - startTime) / (float) duration;
                point.x = from.x + (int) (progress * (to.x - from.x));
                point.y = from.y + (int) (progress * (to.y - from.y));

                sendPointer(downTime, currentTime, MotionEvent.ACTION_MOVE, point, gestureScope);

            }
        }

        return currentTime;
    }

    public static int getCurrentInteractionMode(Context context) {
        return getSystemIntegerRes(context, "config_navBarInteractionMode");
    }

    /**
     * Retrieve the resource value that defines if nav bar can moved or if it is fixed position.
     */
    private static boolean getNavBarCanMove(Context context) {
        return getSystemBooleanRes(context, "config_navBarCanMove");
    }

    @NonNull
    UiObject2 clickAndGet(
            @NonNull final UiObject2 target, @NonNull String resName, Pattern longClickEvent) {
        final Point targetCenter = target.getVisibleCenter();
        final long downTime = SystemClock.uptimeMillis();
        // Use stylus secondary button press to prevent using the exteded long press timeout rule
        // unnecessarily
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, targetCenter,
                GestureScope.DONT_EXPECT_PILFER, InputDevice.SOURCE_TOUCHSCREEN,
                /* isRightClick= */ true, MotionEvent.TOOL_TYPE_STYLUS);
        try {
            expectEvent(TestProtocol.SEQUENCE_MAIN, longClickEvent);
            final UiObject2 result = waitForLauncherObject(resName);
            return result;
        } finally {
            sendPointer(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, targetCenter,
                    GestureScope.DONT_EXPECT_PILFER, InputDevice.SOURCE_TOUCHSCREEN,
                    /* isRightClick= */ true, MotionEvent.TOOL_TYPE_STYLUS);
        }
    }

    @NonNull
    UiObject2 rightClickAndGet(
            @NonNull final UiObject2 target, @NonNull String resName, Pattern rightClickEvent) {
        final Point targetCenter = target.getVisibleCenter();
        final long downTime = SystemClock.uptimeMillis();
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, targetCenter,
                GestureScope.DONT_EXPECT_PILFER, InputDevice.SOURCE_MOUSE,
                /* isRightClick= */ true);
        try {
            expectEvent(TestProtocol.SEQUENCE_MAIN, rightClickEvent);
            final UiObject2 result = waitForLauncherObject(resName);
            return result;
        } finally {
            sendPointer(downTime, SystemClock.uptimeMillis(), ACTION_UP, targetCenter,
                    GestureScope.DONT_EXPECT_PILFER, InputDevice.SOURCE_MOUSE,
                    /* isRightClick= */ true);
        }
    }

    private static boolean getSystemBooleanRes(Context context, String resName) {
        Resources res = context.getResources();
        int resId = res.getIdentifier(resName, "bool", "android");

        if (resId != 0) {
            return res.getBoolean(resId);
        } else {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
            return false;
        }
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

    /** Returns the bounds of the display as a Point where x is width and y is height. */
    Point getRealDisplaySize() {
        final Rect displayBounds = getContext().getSystemService(WindowManager.class)
                .getMaximumWindowMetrics()
                .getBounds();
        return new Point(displayBounds.width(), displayBounds.height());
    }

    public void enableDebugTracing() {
        getTestInfo(TestProtocol.REQUEST_ENABLE_DEBUG_TRACING);
    }

    private void disableSensorRotation() {
        getTestInfo(TestProtocol.REQUEST_MOCK_SENSOR_ROTATION);
    }

    public void disableDebugTracing() {
        getTestInfo(TestProtocol.REQUEST_DISABLE_DEBUG_TRACING);
    }

    public void forceGc() {
        // GC the system & sysui first before gc'ing launcher
        logShellCommand("cmd statusbar run-gc");
        getTestInfo(TestProtocol.REQUEST_FORCE_GC);
    }

    public Integer getPid() {
        final Bundle testInfo = getTestInfo(TestProtocol.REQUEST_PID);
        return testInfo != null ? testInfo.getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD) : null;
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

    /** Reinitializes the workspace to its default layout. */
    public void reinitializeLauncherData() {
        getTestInfo(TestProtocol.REQUEST_REINITIALIZE_DATA);
    }

    /** Clears the workspace, leaving it empty. */
    public void clearLauncherData() {
        getTestInfo(TestProtocol.REQUEST_CLEAR_DATA);
    }

    /** Shows the taskbar if it is hidden, otherwise does nothing. */
    public void showTaskbarIfHidden() {
        getTestInfo(TestProtocol.REQUEST_UNSTASH_TASKBAR_IF_STASHED);
    }

    /** Shows the bubble bar if it is stashed, otherwise this does nothing. */
    public void showBubbleBarIfHidden() {
        getTestInfo(TestProtocol.REQUEST_UNSTASH_BUBBLE_BAR_IF_STASHED);
    }

    /** Blocks the taskbar from automatically stashing based on time. */
    public void enableBlockTimeout(boolean enable) {
        getTestInfo(enable
                ? TestProtocol.REQUEST_ENABLE_BLOCK_TIMEOUT
                : TestProtocol.REQUEST_DISABLE_BLOCK_TIMEOUT);
    }

    public boolean isTransientTaskbar() {
        return getTestInfo(TestProtocol.REQUEST_IS_TRANSIENT_TASKBAR)
                .getBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public boolean isImeDocked() {
        return getTestInfo(TestProtocol.REQUEST_TASKBAR_IME_DOCKED).getBoolean(
                TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    /** Enables transient taskbar for testing purposes only. */
    public void enableTransientTaskbar(boolean enable) {
        getTestInfo(enable
                ? TestProtocol.REQUEST_ENABLE_TRANSIENT_TASKBAR
                : TestProtocol.REQUEST_DISABLE_TRANSIENT_TASKBAR);
    }

    /**
     * Recreates the taskbar (outside of tests this is done for certain configuration changes).
     * The expected behavior is that the taskbar retains its current state after being recreated.
     * For example, if taskbar is currently stashed, it should still be stashed after recreating.
     */
    public void recreateTaskbar() {
        getTestInfo(TestProtocol.REQUEST_RECREATE_TASKBAR);
    }

    // TODO(b/270393900): Remove with ENABLE_ALL_APPS_SEARCH_IN_TASKBAR flag cleanup.

    /** Refreshes the known overview target in TIS. */
    public void refreshOverviewTarget() {
        getTestInfo(TestProtocol.REQUEST_REFRESH_OVERVIEW_TARGET);
    }

    public List<String> getHotseatIconNames() {
        return getTestInfo(TestProtocol.REQUEST_HOTSEAT_ICON_NAMES)
                .getStringArrayList(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    private String[] getActivities() {
        return getTestInfo(TestProtocol.REQUEST_GET_ACTIVITIES)
                .getStringArray(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public String getRootedActivitiesList() {
        return String.join(", ", getActivities());
    }

    /** Returns whether no leaked activities are detected. */
    public boolean noLeakedActivities(boolean requireOneActiveActivity) {
        final String[] activities = getActivities();

        for (String activity : activities) {
            if (activity.contains("(destroyed)")) {
                return false;
            }
        }
        return activities.length <= (requireOneActiveActivity ? 1 : 2);
    }

    public int getActivitiesCreated() {
        return getTestInfo(TestProtocol.REQUEST_GET_ACTIVITIES_CREATED_COUNT)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    public Closable eventsCheck() {
        Assert.assertTrue("Nested event checking", mEventChecker == null);
        disableSensorRotation();
        final Integer initialPid = getPid();
        final LogEventChecker eventChecker = new LogEventChecker(this);
        if (eventChecker.start()) mEventChecker = eventChecker;

        return () -> {
            if (initialPid != null && initialPid.intValue() != getPid()) {
                if (mOnLauncherCrashed != null) mOnLauncherCrashed.run();
                checkForAnomaly();
                Assert.fail(
                        formatSystemHealthMessage(
                                formatErrorWithEvents("Launcher crashed", false)));
            }

            if (mEventChecker != null) {
                mEventChecker = null;
                if (mCheckEventsForSuccessfulGestures) {
                    final String message = eventChecker.verify(WAIT_TIME_MS, true);
                    if (message != null) {
                        dumpDiagnostics(message);
                        checkForAnomaly();
                        Assert.fail(formatSystemHealthMessage(
                                "http://go/tapl : successful gesture produced " + message));
                    }
                } else {
                    eventChecker.finishNoWait();
                }
            }
        };
    }

    /** Returns whether the Launcher is a Launcher3 one */
    public boolean isLauncher3() {
        if (mIsLauncher3 == null) {
            mIsLauncher3 = "com.android.launcher3".equals(getLauncherPackageName());
        }
        return mIsLauncher3;
    }

    void expectEvent(String sequence, Pattern expected) {
        if (mEventChecker != null) {
            mEventChecker.expectPattern(sequence, expected);
        } else {
            Log.d(TAG, "Expecting: " + sequence + " / " + expected);
        }
    }

    Rect getVisibleBounds(UiObject2 object) {
        try {
            return object.getVisibleBounds();
        } catch (StaleObjectException e) {
            fail("Object disappeared from screen");
            return null;
        } catch (Throwable t) {
            fail(t.toString());
            return null;
        }
    }

    float getWindowCornerRadius() {
        // TODO(b/197326121): Check if the touch is overlapping with the corners by offsetting
        final float tmpBuffer = 100f;
        final Resources resources = getResources();
        if (!supportsRoundedCornersOnWindows(resources)) {
            Log.d(TAG, "No rounded corners");
            return tmpBuffer;
        }

        // Radius that should be used in case top or bottom aren't defined.
        float defaultRadius = ResourceUtils.getDimenByName("rounded_corner_radius", resources, 0);

        float topRadius = ResourceUtils.getDimenByName("rounded_corner_radius_top", resources, 0);
        if (topRadius == 0f) {
            topRadius = defaultRadius;
        }
        float bottomRadius = ResourceUtils.getDimenByName(
                "rounded_corner_radius_bottom", resources, 0);
        if (bottomRadius == 0f) {
            bottomRadius = defaultRadius;
        }

        // Always use the smallest radius to make sure the rounded corners will
        // completely cover the display.
        Log.d(TAG, "Rounded corners top: " + topRadius + " bottom: " + bottomRadius);
        return Math.max(topRadius, bottomRadius) + tmpBuffer;
    }

    private Context getLauncherContext(Context baseContext)
            throws PackageManager.NameNotFoundException {
        // Workaround, use constructed context because both the instrumentation context and the
        // app context are not constructed with resources that take overlays into account
        return baseContext.createPackageContext(getLauncherPackageName(), 0);
    }

    private static boolean supportsRoundedCornersOnWindows(Resources resources) {
        return ResourceUtils.getBoolByName(
                "config_supportsRoundedCornersOnWindows", resources, false);
    }

    /**
     * Taps outside container to dismiss, centered vertically and halfway to the edge of the screen.
     *
     * @param container container to be dismissed
     * @param tapRight  tap on the right of the container if true, or left otherwise
     */
    void touchOutsideContainer(UiObject2 container, boolean tapRight) {
        touchOutsideContainer(container, tapRight, true);
    }

    /**
     * Taps outside the container, to the right or left, and centered vertically.
     *
     * @param tapRight      if true touches to the right of the container, otherwise touches on left
     * @param halfwayToEdge if true touches halfway to the screen edge, if false touches 1 px from
     *                      container
     */
    void touchOutsideContainer(UiObject2 container, boolean tapRight, boolean halfwayToEdge) {
        try (LauncherInstrumentation.Closable c = addContextLayer(
                "want to tap outside container on the " + (tapRight ? "right" : "left"))) {
            Rect containerBounds = getVisibleBounds(container);

            int x;
            if (halfwayToEdge) {
                x = tapRight
                        ? (containerBounds.right + getRealDisplaySize().x) / 2
                        : containerBounds.left / 2;
            } else {
                x = tapRight
                        ? containerBounds.right + 1
                        : containerBounds.left - 1;
            }
            // If IME is visible and overlaps the container bounds, touch above it.
            final Insets systemGestureRegion = getSystemGestureRegion();
            int bottomBound = Math.min(
                    containerBounds.bottom,
                    getRealDisplaySize().y - systemGestureRegion.bottom);
            int y = (bottomBound + containerBounds.top) / 2;
            // Do not tap in the status bar.
            y = Math.max(y, systemGestureRegion.top);

            final long downTime = SystemClock.uptimeMillis();
            final Point tapTarget = new Point(x, y);
            sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, tapTarget,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
            sendPointer(downTime, downTime, MotionEvent.ACTION_UP, tapTarget,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
        }
    }

    /**
     * Waits until a particular condition is true. Based on WaitMixin.
     */
    boolean waitAndGet(BooleanSupplier condition, long timeout, long interval) {
        long startTime = SystemClock.uptimeMillis();

        boolean result = condition.getAsBoolean();
        for (long elapsedTime = 0; !result; elapsedTime = SystemClock.uptimeMillis() - startTime) {
            if (elapsedTime >= timeout) {
                break;
            }
            SystemClock.sleep(interval);
            result = condition.getAsBoolean();
        }
        return result;
    }

    /** Executes a runnable and waits for the wallpaper-open animation completion. */
    public void executeAndWaitForWallpaperAnimation(Runnable r, String actionName) {
        executeAndWaitForLauncherEvent(
                () -> r.run(),
                event -> TestProtocol.WALLPAPER_OPEN_ANIMATION_FINISHED_MESSAGE
                        .equals(event.getClassName().toString()),
                () -> "Didn't detect finishing wallpaper-open animation",
                actionName);
    }
}
