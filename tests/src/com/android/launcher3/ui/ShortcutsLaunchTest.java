package com.android.launcher3.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.LauncherActivityInfo;
import android.graphics.Point;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import android.view.MotionEvent;

import com.android.launcher3.R;
import com.android.launcher3.util.Condition;
import com.android.launcher3.util.Wait;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for verifying that shortcuts are shown and can be launched after long pressing an app
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ShortcutsLaunchTest extends AbstractLauncherUiTest {

    @Test
    @Ignore
    public void testAppLauncher_portrait() throws Exception {
        lockRotation(true);
        performTest();
    }

    @Test
    @Ignore
    public void testAppLauncher_landscape() throws Exception {
        lockRotation(false);
        performTest();
    }

    private void performTest() throws Exception {
        mActivityMonitor.startLauncher();
        LauncherActivityInfo testApp = getSettingsApp();

        // Open all apps and wait for load complete
        final UiObject2 appsContainer = TestViewHelpers.openAllApps();
        Wait.atMost(null, Condition.minChildCount(appsContainer, 2), DEFAULT_UI_TIMEOUT);

        // Find settings app and verify shortcuts appear when long pressed
        UiObject2 icon = scrollAndFind(appsContainer, By.text(testApp.getLabel().toString()));
        // Press icon center until shortcuts appear
        Point iconCenter = icon.getVisibleCenter();
        TestViewHelpers.sendPointer(MotionEvent.ACTION_DOWN, iconCenter);
        UiObject2 deepShortcutsContainer = TestViewHelpers.findViewById(
                R.id.deep_shortcuts_container);
        assertNotNull(deepShortcutsContainer);
        TestViewHelpers.sendPointer(MotionEvent.ACTION_UP, iconCenter);

        // Verify that launching a shortcut opens a page with the same text
        assertTrue(deepShortcutsContainer.getChildCount() > 0);

        // Pick second children as it starts showing shortcuts.
        UiObject2 shortcut = deepShortcutsContainer.getChildren().get(1)
                .findObject(TestViewHelpers.getSelectorForId(R.id.bubble_text));
        shortcut.click();
        assertTrue(mDevice.wait(Until.hasObject(By.pkg(
                testApp.getComponentName().getPackageName())
                .text(shortcut.getText())), DEFAULT_UI_TIMEOUT));
    }
}
