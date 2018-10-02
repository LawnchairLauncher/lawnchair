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
import com.android.launcher3.util.rule.ShellCommandRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for dragging a deep shortcut to the home screen.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ShortcutsToHomeTest extends AbstractLauncherUiTest {

    @Rule public ShellCommandRule mDefaultLauncherRule = ShellCommandRule.setDefaultLauncher();

    @Test
    @Ignore
    public void testDragIcon_portrait() throws Throwable {
        lockRotation(true);
        performTest();
    }

    @Test
    @Ignore
    public void testDragIcon_landscape() throws Throwable {
        lockRotation(false);
        performTest();
    }

    private void performTest() throws Throwable {
        clearHomescreen();
        mActivityMonitor.startLauncher();

        LauncherActivityInfo testApp  = getSettingsApp();

        // Open all apps and wait for load complete.
        final UiObject2 appsContainer = openAllApps();
        assertTrue(Wait.atMost(Condition.minChildCount(appsContainer, 2),
                DEFAULT_UI_TIMEOUT));

        // Find the app and long press it to show shortcuts.
        UiObject2 icon = scrollAndFind(appsContainer, By.text(testApp.getLabel().toString()));
        // Press icon center until shortcuts appear
        Point iconCenter = icon.getVisibleCenter();
        sendPointer(MotionEvent.ACTION_DOWN, iconCenter);
        UiObject2 deepShortcutsContainer = findViewById(R.id.deep_shortcuts_container);
        assertNotNull(deepShortcutsContainer);
        sendPointer(MotionEvent.ACTION_UP, iconCenter);

        // Drag the first shortcut to the home screen.
        assertTrue(deepShortcutsContainer.getChildCount() > 0);
        UiObject2 shortcut = deepShortcutsContainer.getChildren().get(1)
                .findObject(getSelectorForId(R.id.bubble_text));
        String shortcutName = shortcut.getText();
        dragToWorkspace(shortcut, false);

        // Verify that the shortcut works on home screen
        // (the app opens and has the same text as the shortcut).
        mDevice.findObject(By.text(shortcutName)).click();
        assertTrue(mDevice.wait(Until.hasObject(By.pkg(
                testApp.getComponentName().getPackageName())
                .text(shortcutName)), DEFAULT_UI_TIMEOUT));
    }
}
