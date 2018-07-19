package com.android.launcher3.ui;

import android.content.pm.LauncherActivityInfo;
import android.graphics.Point;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.view.MotionEvent;

import com.android.launcher3.R;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;
import com.android.launcher3.util.rule.LauncherActivityRule;
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test for verifying that shortcuts are shown and can be launched after long pressing an app
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ShortcutsLaunchTest extends AbstractLauncherUiTest {

    @Rule public LauncherActivityRule mActivityMonitor = new LauncherActivityRule();
    @Rule public ShellCommandRule mDefaultLauncherRule = ShellCommandRule.setDefaultLauncher();

    @Test
    public void testAppLauncher_portrait() throws Exception {
        lockRotation(true);
        performTest();
    }

    @Test
    public void testAppLauncher_landscape() throws Exception {
        lockRotation(false);
        performTest();
    }

    private void performTest() throws Exception {
        mActivityMonitor.startLauncher();
        LauncherActivityInfo testApp = getSettingsApp();

        // Open all apps and wait for load complete
        final UiObject2 appsContainer = openAllApps();
        assertTrue(Wait.atMost(Condition.minChildCount(appsContainer, 2),
                DEFAULT_UI_TIMEOUT));

        // Find settings app and verify shortcuts appear when long pressed
        UiObject2 icon = scrollAndFind(appsContainer, By.text(testApp.getLabel().toString()));
        // Press icon center until shortcuts appear
        Point iconCenter = icon.getVisibleCenter();
        sendPointer(MotionEvent.ACTION_DOWN, iconCenter);
        UiObject2 deepShortcutsContainer = findViewById(R.id.deep_shortcuts_container);
        assertNotNull(deepShortcutsContainer);
        sendPointer(MotionEvent.ACTION_UP, iconCenter);

        // Verify that launching a shortcut opens a page with the same text
        assertTrue(deepShortcutsContainer.getChildCount() > 0);

        // Pick second children as it starts showing shortcuts.
        UiObject2 shortcut = deepShortcutsContainer.getChildren().get(1)
                .findObject(getSelectorForId(R.id.bubble_text));
        shortcut.click();
        assertTrue(mDevice.wait(Until.hasObject(By.pkg(
                testApp.getComponentName().getPackageName())
                .text(shortcut.getText())), DEFAULT_UI_TIMEOUT));
    }
}
