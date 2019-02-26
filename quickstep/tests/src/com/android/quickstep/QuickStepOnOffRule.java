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

import static com.android.quickstep.QuickStepOnOffRule.Mode.BOTH;
import static com.android.quickstep.QuickStepOnOffRule.Mode.OFF;
import static com.android.quickstep.QuickStepOnOffRule.Mode.ON;

import androidx.test.InstrumentationRegistry;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.tapl.TestHelpers;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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
                    try {
                        if (mode == ON || mode == BOTH) {
                            evaluateWithQuickstepOn();
                        }
                        if (mode == OFF || mode == BOTH) {
                            evaluateWithQuickstepOff();
                        }
                    } finally {
                        overrideSwipeUpEnabled(null);
                    }
                }

                private void evaluateWithQuickstepOff() throws Throwable {
                    overrideSwipeUpEnabled(false);
                    base.evaluate();
                }

                private void evaluateWithQuickstepOn() throws Throwable {
                    overrideSwipeUpEnabled(true);
                    base.evaluate();
                }

                private void overrideSwipeUpEnabled(Boolean swipeUpEnabledOverride)
                        throws Throwable {
                    mLauncher.overrideSwipeUpEnabled(swipeUpEnabledOverride);
                    mMainThreadExecutor.execute(() -> OverviewInteractionState.INSTANCE.get(
                            InstrumentationRegistry.getInstrumentation().getTargetContext()).
                            notifySwipeUpSettingChanged(mLauncher.isSwipeUpEnabled()));
                    // TODO(b/124236673): avoid using sleep().
                    mLauncher.getDevice().waitForIdle();
                    Thread.sleep(2000);
                    mLauncher.getDevice().waitForIdle();
                }
            };
        } else {
            return base;
        }
    }
}
