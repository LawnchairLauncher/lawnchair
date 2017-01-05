package com.android.launcher3.ui;

import android.content.pm.LauncherActivityInfo;
import android.os.Process;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;

/**
 * Test for verifying apps is launched from all-apps
 */
@LargeTest
public class AllAppsAppLaunchTest extends LauncherInstrumentationTestCase {

    private LauncherActivityInfo mSettingsApp;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSettingsApp = LauncherAppsCompat.getInstance(mTargetContext)
                .getActivityList("com.android.settings", Process.myUserHandle()).get(0);
    }

    public void testAppLauncher_portrait() throws Exception {
        lockRotation(true);
        performTest();
    }

    public void testAppLauncher_landscape() throws Exception {
        lockRotation(false);
        performTest();
    }

    private void performTest() throws Exception {
        startLauncher();

        // Open all apps and wait for load complete
        final UiObject2 appsContainer = openAllApps();
        assertTrue(Wait.atMost(Condition.minChildCount(appsContainer, 2), DEFAULT_UI_TIMEOUT));

        // Open settings app and verify app launched
        scrollAndFind(appsContainer, By.text(mSettingsApp.getLabel().toString())).click();
        assertTrue(mDevice.wait(Until.hasObject(By.pkg(
                mSettingsApp.getComponentName().getPackageName()).depth(0)), DEFAULT_UI_TIMEOUT));
    }
}
