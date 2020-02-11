package com.android.launcher3.ui;

import android.util.Log;
import android.view.Surface;

import com.android.launcher3.tapl.TestHelpers;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

class PortraitLandscapeRunner implements TestRule {
    private static final String TAG = "PortraitLandscapeRunner";
    private AbstractLauncherUiTest mTest;

    public PortraitLandscapeRunner(AbstractLauncherUiTest test) {
        mTest = test;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        if (!TestHelpers.isInLauncherProcess() ||
                description.getAnnotation(AbstractLauncherUiTest.PortraitLandscape.class) == null) {
            return base;
        }

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    mTest.mDevice.pressHome();
                    mTest.waitForLauncherCondition("Launcher activity wasn't created",
                            launcher -> launcher != null);

                    mTest.executeOnLauncher(launcher ->
                            launcher.getRotationHelper().forceAllowRotationForTesting(
                                    true));

                    evaluateInPortrait();
                    evaluateInLandscape();
                } catch (Exception e) {
                    Log.e(TAG, "Exception", e);
                    throw e;
                } finally {
                    mTest.mDevice.setOrientationNatural();
                    mTest.executeOnLauncher(launcher ->
                    {
                        if (launcher != null) {
                            launcher.getRotationHelper().forceAllowRotationForTesting(false);
                        }
                    });
                    mTest.mLauncher.setExpectedRotation(Surface.ROTATION_0);
                }
            }

            private void evaluateInPortrait() throws Throwable {
                mTest.mDevice.setOrientationNatural();
                mTest.mLauncher.setExpectedRotation(Surface.ROTATION_0);
                base.evaluate();
                mTest.getDevice().pressHome();
            }

            private void evaluateInLandscape() throws Throwable {
                mTest.mDevice.setOrientationLeft();
                mTest.mLauncher.setExpectedRotation(Surface.ROTATION_90);
                base.evaluate();
                mTest.getDevice().pressHome();
            }
        };
    }
}
