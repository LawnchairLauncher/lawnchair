/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.util.ui;

import static com.android.launcher3.LauncherPrefs.FIXED_LANDSCAPE_MODE;

import android.util.Log;
import android.view.Surface;
import android.view.View;

import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.tapl.TestHelpers;
import com.android.launcher3.util.rule.FailureWatcher;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class PortraitLandscapeRunner<LAUNCHER_TYPE extends Launcher, OVERVIEW_TYPE extends View>
        implements TestRule {
    private static final String TAG = "PortraitLandscapeRunner";
    private AbstractLauncherUiTest<LAUNCHER_TYPE, OVERVIEW_TYPE> mTest;

    // Annotation for tests that need to be run in portrait and landscape modes.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface PortraitLandscape {
    }

    public PortraitLandscapeRunner(AbstractLauncherUiTest<LAUNCHER_TYPE, OVERVIEW_TYPE> test) {
        mTest = test;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (!TestHelpers.isInLauncherProcess()
                || description.getAnnotation(PortraitLandscape.class) == null) {
            return base;
        }

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    try {
                        // we expect to begin unlocked...
                        AbstractLauncherUiTest.verifyKeyguardInvisible();

                        mTest.mDevice.pressHome();
                        mTest.waitForLauncherCondition("Launcher activity wasn't created",
                                Objects::nonNull,
                                TimeUnit.SECONDS.toMillis(20));

                        mTest.executeOnLauncher(launcher ->
                                launcher.getRotationHelper().forceAllowRotationForTesting(
                                        true));

                    } catch (Throwable e) {
                        FailureWatcher.onError(mTest.mLauncher, description);
                        throw e;
                    }

                    evaluateInPortrait();
                    evaluateInLandscape();
                } catch (Throwable e) {
                    Log.e(TAG, "Error", e);
                    throw e;
                } finally {

                    mTest.mDevice.setOrientationNatural();
                    mTest.executeOnLauncher(launcher ->
                    {
                        if (launcher != null) {
                            LauncherPrefs.get(launcher).put(FIXED_LANDSCAPE_MODE, false);
                            launcher.getRotationHelper().forceAllowRotationForTesting(false);
                        }
                    });
                    mTest.mLauncher.setExpectedRotation(Surface.ROTATION_0);

                    // and end unlocked...
                    AbstractLauncherUiTest.verifyKeyguardInvisible();
                }
            }

            private void evaluateInPortrait() throws Throwable {
                mTest.mDevice.setOrientationNatural();
                mTest.mLauncher.setExpectedRotation(Surface.ROTATION_0);
                AbstractLauncherUiTest.checkDetectedLeaks(mTest.mLauncher, true);
                base.evaluate();
                mTest.getDevice().pressHome();
            }

            private void evaluateInLandscape() throws Throwable {
                mTest.executeOnLauncher(launcher -> LauncherPrefs.get(launcher)
                        .put(FIXED_LANDSCAPE_MODE, shouldHaveFixedLandscape(launcher)));
                mTest.mDevice.setOrientationLeft();
                mTest.mLauncher.setExpectedRotation(Surface.ROTATION_90);
                AbstractLauncherUiTest.checkDetectedLeaks(mTest.mLauncher, true);
                base.evaluate();
                mTest.getDevice().pressHome();
            }

            private boolean shouldHaveFixedLandscape(Launcher launcher) {
                return Flags.oneGridSpecs()
                        && !launcher.getDeviceProfile().getDeviceProperties().isTablet()
                        && !launcher.getDeviceProfile().getDeviceProperties().isMultiDisplay();
            }
        };
    }
}
