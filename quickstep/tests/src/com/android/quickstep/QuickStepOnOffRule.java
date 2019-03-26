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

import static com.android.quickstep.QuickStepOnOffRule.Mode.BOTH;
import static com.android.quickstep.QuickStepOnOffRule.Mode.OFF;
import static com.android.quickstep.QuickStepOnOffRule.Mode.ON;
import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_2BUTTON_OVERLAY;
import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_3BUTTON_OVERLAY;
import static com.android.systemui.shared.system.QuickStepContract.NAV_BAR_MODE_GESTURAL_OVERLAY;

import android.content.Context;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;
import com.android.systemui.shared.system.QuickStepContract;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;

/**
 * Test rule that allows executing a test with Quickstep on and then Quickstep off.
 * The test should be annotated with @QuickstepOnOff.
 */
public class QuickStepOnOffRule implements TestRule {

    static final String TAG = "QuickStepOnOffRule";

    public enum Mode {
        ON, OFF, BOTH
    }

    // Annotation for tests that need to be run with quickstep enabled and disabled.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface QuickstepOnOff {
        Mode mode() default BOTH;
    }

    private final Executor mMainThreadExecutor;
    private final LauncherInstrumentation mLauncher;

    public QuickStepOnOffRule(Executor mainThreadExecutor, LauncherInstrumentation launcher) {
        mLauncher = launcher;
        this.mMainThreadExecutor = mainThreadExecutor;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (TestHelpers.isInLauncherProcess() &&
                description.getAnnotation(QuickstepOnOff.class) != null) {
            Mode mode = description.getAnnotation(QuickstepOnOff.class).mode();
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    final Context context = getInstrumentation().getContext();
                    final String prevOverlayPkg = QuickStepContract.isGesturalMode(context)
                            ? NAV_BAR_MODE_GESTURAL_OVERLAY
                            : QuickStepContract.isSwipeUpMode(context)
                                    ? NAV_BAR_MODE_2BUTTON_OVERLAY
                                    : NAV_BAR_MODE_3BUTTON_OVERLAY;
                    try {
                        if (mode == ON || mode == BOTH) {
                            evaluateWithQuickstepOn();
                        }
                        if (mode == OFF || mode == BOTH) {
                            evaluateWithQuickstepOff();
                        }
                    } finally {
                        setActiveOverlay(prevOverlayPkg);
                    }
                }

                public void evaluateWithoutChangingSetting(Statement base) throws Throwable {
                    base.evaluate();
                }

                private void evaluateWithQuickstepOff() throws Throwable {
                    setActiveOverlay(NAV_BAR_MODE_3BUTTON_OVERLAY);
                    evaluateWithoutChangingSetting(base);
                }

                private void evaluateWithQuickstepOn() throws Throwable {
                    setActiveOverlay(NAV_BAR_MODE_2BUTTON_OVERLAY);
                    base.evaluate();
                }

                private void setActiveOverlay(String overlayPackage) {
                    setOverlayPackageEnabled(NAV_BAR_MODE_3BUTTON_OVERLAY,
                            overlayPackage == NAV_BAR_MODE_3BUTTON_OVERLAY);
                    setOverlayPackageEnabled(NAV_BAR_MODE_2BUTTON_OVERLAY,
                            overlayPackage == NAV_BAR_MODE_2BUTTON_OVERLAY);
                    setOverlayPackageEnabled(NAV_BAR_MODE_GESTURAL_OVERLAY,
                            overlayPackage == NAV_BAR_MODE_GESTURAL_OVERLAY);

                    // TODO: Wait until nav bar mode has applied
                }

                private void setOverlayPackageEnabled(String overlayPackage, boolean enable) {
                    Log.d(TAG, "setOverlayPackageEnabled: " + overlayPackage + " " + enable);
                    final String action = enable ? "enable" : "disable";
                    try {
                        UiDevice.getInstance(getInstrumentation()).executeShellCommand(
                                "cmd overlay " + action + " " + overlayPackage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
        } else {
            return base;
        }
    }
}
