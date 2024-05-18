/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.quickstep;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.quickstep.NavigationModeSwitchRule.Mode.ALL;
import static com.android.quickstep.NavigationModeSwitchRule.Mode.THREE_BUTTON;
import static com.android.quickstep.NavigationModeSwitchRule.Mode.ZERO_BUTTON;
import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_3BUTTON_OVERLAY;
import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_GESTURAL_OVERLAY;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.FailureWatcher;
import com.android.systemui.shared.system.QuickStepContract;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test rule that allows executing a test with Quickstep on and then Quickstep off.
 * The test should be annotated with @NavigationModeSwitch.
 */
public class NavigationModeSwitchRule implements TestRule {

    static final String TAG = "QuickStepOnOffRule";

    public static final int WAIT_TIME_MS = 10000;

    public enum Mode {
        THREE_BUTTON, ZERO_BUTTON, ALL
    }

    // Annotation for tests that need to be run with quickstep enabled and disabled.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface NavigationModeSwitch {
        Mode mode() default ALL;
    }

    private final LauncherInstrumentation mLauncher;

    static final DisplayController DISPLAY_CONTROLLER =
            DisplayController.INSTANCE.get(getInstrumentation().getTargetContext());

    public NavigationModeSwitchRule(LauncherInstrumentation launcher) {
        mLauncher = launcher;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (TestHelpers.isInLauncherProcess() &&
                description.getAnnotation(NavigationModeSwitch.class) != null) {
            Mode mode = description.getAnnotation(NavigationModeSwitch.class).mode();
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    mLauncher.enableDebugTracing();
                    final Context context = getInstrumentation().getContext();
                    final int currentInteractionMode =
                            LauncherInstrumentation.getCurrentInteractionMode(context);
                    final String prevOverlayPkg = getCurrentOverlayPackage(currentInteractionMode);
                    final LauncherInstrumentation.NavigationModel originalMode =
                            mLauncher.getNavigationModel();
                    try {
                        if (mode == ZERO_BUTTON || mode == ALL) {
                            evaluateWithZeroButtons();
                        }
                        if (mode == THREE_BUTTON || mode == ALL) {
                            evaluateWithThreeButtons();
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "Error", e);
                        throw e;
                    } finally {
                        Log.d(TAG, "In Finally block");
                        assertTrue(mLauncher, "Couldn't set overlay",
                                setActiveOverlay(mLauncher, prevOverlayPkg, originalMode,
                                        description), description);
                    }
                }

                private void evaluateWithThreeButtons() throws Throwable {
                    if (setActiveOverlay(mLauncher, NAV_BAR_MODE_3BUTTON_OVERLAY,
                            LauncherInstrumentation.NavigationModel.THREE_BUTTON, description)) {
                        base.evaluate();
                    }
                }

                private void evaluateWithZeroButtons() throws Throwable {
                    if (setActiveOverlay(mLauncher, NAV_BAR_MODE_GESTURAL_OVERLAY,
                            LauncherInstrumentation.NavigationModel.ZERO_BUTTON, description)) {
                        base.evaluate();
                    }
                }
            };
        } else {
            return base;
        }
    }

    public static String getCurrentOverlayPackage(int currentInteractionMode) {
        return QuickStepContract.isGesturalMode(currentInteractionMode)
                ? NAV_BAR_MODE_GESTURAL_OVERLAY
                : NAV_BAR_MODE_3BUTTON_OVERLAY;
    }

    private static LauncherInstrumentation.NavigationModel currentSysUiNavigationMode() {
        return LauncherInstrumentation.getNavigationModel(
                DisplayController.getNavigationMode(
                        getInstrumentation().
                                getTargetContext()).
                        resValue);
    }

    public static boolean setActiveOverlay(LauncherInstrumentation launcher, String overlayPackage,
            LauncherInstrumentation.NavigationModel expectedMode, Description description)
            throws Exception {
        if (!packageExists(overlayPackage)) {
            Log.d(TAG, "setActiveOverlay: " + overlayPackage + " pkg does not exist");
            return false;
        }

        Log.d(TAG, "setActiveOverlay: " + overlayPackage + "...");
        UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                "cmd overlay enable-exclusive --category " + overlayPackage);

        if (currentSysUiNavigationMode() != expectedMode) {
            final CountDownLatch latch = new CountDownLatch(1);
            final Context targetContext = getInstrumentation().getTargetContext();
            final DisplayController.DisplayInfoChangeListener listener =
                    (context, info, flags) -> {
                        if (LauncherInstrumentation.getNavigationModel(info.navigationMode.resValue)
                                == expectedMode) {
                            latch.countDown();
                        }
                    };
            targetContext.getMainExecutor().execute(() ->
                    DISPLAY_CONTROLLER.addChangeListener(listener));
            latch.await(60, TimeUnit.SECONDS);
            targetContext.getMainExecutor().execute(() ->
                    DISPLAY_CONTROLLER.removeChangeListener(listener));

            assertTrue(launcher, "Navigation mode didn't change to " + expectedMode,
                    currentSysUiNavigationMode() == expectedMode, description);

        }

        Wait.atMost("Couldn't switch to " + overlayPackage,
                () -> launcher.getNavigationModel() == expectedMode, WAIT_TIME_MS, launcher);

        Wait.atMost(() -> "Switching nav mode: "
                        + launcher.getNavigationModeMismatchError(false),
                () -> launcher.getNavigationModeMismatchError(false) == null,
                WAIT_TIME_MS, launcher);
        AbstractLauncherUiTest.checkDetectedLeaks(launcher, false);
        return true;
    }

    private static boolean packageExists(String packageName) {
        try {
            PackageManager pm = getInstrumentation().getContext().getPackageManager();
            if (pm.getApplicationInfo(packageName, 0 /* flags */) == null) {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    private static void assertTrue(LauncherInstrumentation launcher, String message,
            boolean condition, Description description) {
        launcher.checkForAnomaly(true, true);
        if (!condition) {
            final AssertionError assertionError = new AssertionError(message);
            if (description != null) {
                FailureWatcher.onError(launcher, description, assertionError);
            }
            throw assertionError;
        }
    }
}
