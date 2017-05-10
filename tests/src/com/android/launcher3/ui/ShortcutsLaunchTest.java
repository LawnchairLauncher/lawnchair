package com.android.launcher3.ui;

import android.content.pm.LauncherActivityInfo;
import android.graphics.Point;
import android.os.Process;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.MotionEvent;

import com.android.launcher3.R;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;

/**
 * Test for verifying that shortcuts are shown and can be launched after long pressing an app
 */
@LargeTest
public class ShortcutsLaunchTest extends LauncherInstrumentationTestCase {

    private LauncherActivityInfo mSettingsApp;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        setDefaultLauncher();

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

        // Find settings app and verify shortcuts appear when long pressed
        UiObject2 icon = scrollAndFind(appsContainer, By.text(mSettingsApp.getLabel().toString()));
        // Press icon center until shortcuts appear
        Point iconCenter = icon.getVisibleCenter();
        sendPointer(MotionEvent.ACTION_DOWN, iconCenter);
        UiObject2 deepShortcutsContainer = findViewById(R.id.deep_shortcuts_container);
        assertNotNull(deepShortcutsContainer);
        sendPointer(MotionEvent.ACTION_UP, iconCenter);

        // Verify that launching a shortcut opens a page with the same text
        assertTrue(deepShortcutsContainer.getChildCount() > 0);
        UiObject2 shortcut = deepShortcutsContainer.getChildren().get(0)
                .findObject(getSelectorForId(R.id.bubble_text));
        shortcut.click();
        assertTrue(mDevice.wait(Until.hasObject(By.pkg(
                mSettingsApp.getComponentName().getPackageName())
                .text(shortcut.getText())), DEFAULT_UI_TIMEOUT));
    }
}
