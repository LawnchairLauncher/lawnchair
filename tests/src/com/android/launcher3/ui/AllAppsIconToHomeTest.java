package com.android.launcher3.ui;

import static org.junit.Assert.assertTrue;

import android.content.pm.LauncherActivityInfo;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for dragging an icon from all-apps to homescreen.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AllAppsIconToHomeTest extends AbstractLauncherUiTest {

    @Rule public ShellCommandRule mDefaultLauncherRule = ShellCommandRule.setDefaultLauncher();

    @Test
    public void testDragIcon_portrait() throws Throwable {
        lockRotation(true);
        performTest();
    }

    @Test
    public void testDragIcon_landscape() throws Throwable {
        lockRotation(false);
        performTest();
    }

    private void performTest() throws Throwable {
        LauncherActivityInfo settingsApp = getSettingsApp();

        clearHomescreen();
        mActivityMonitor.startLauncher();

        // Open all apps and wait for load complete.
        final UiObject2 appsContainer = openAllApps();
        assertTrue(Wait.atMost(Condition.minChildCount(appsContainer, 2), DEFAULT_UI_TIMEOUT));

        // Drag icon to homescreen.
        UiObject2 icon = scrollAndFind(appsContainer, By.text(settingsApp.getLabel().toString()));
        dragToWorkspace(icon, true);

        // Verify that the icon works on homescreen.
        mDevice.findObject(By.text(settingsApp.getLabel().toString())).click();
        assertTrue(mDevice.wait(Until.hasObject(By.pkg(
                settingsApp.getComponentName().getPackageName()).depth(0)), DEFAULT_UI_TIMEOUT));
    }
}
