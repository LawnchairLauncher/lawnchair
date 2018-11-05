package com.android.launcher3.ui;

import android.content.pm.LauncherActivityInfo;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.tapl.AppIconMenuItem;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for dragging a deep shortcut to the home screen.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ShortcutsToHomeTest extends AbstractLauncherUiTest {

    @Test
    @PortraitLandscape
    public void testDragIcon() throws Throwable {
        clearHomescreen();
        mActivityMonitor.startLauncher();

        LauncherActivityInfo testApp = getSettingsApp();

        // 1. Open all apps and wait for load complete.
        // 2. Find the app and long press it to show shortcuts.
        // 3. Press icon center until shortcuts appear
        final AppIconMenuItem menuItem = mLauncher.
                getWorkspace().
                switchToAllApps().
                getAppIcon(testApp.getLabel().toString()).
                openMenu().
                getMenuItem(0);
        final String shortcutName = menuItem.getText();

        // 4. Drag the first shortcut to the home screen.
        // 5. Verify that the shortcut works on home screen
        //    (the app opens and has the same text as the shortcut).
        menuItem.
                dragToWorkspace().
                getWorkspaceAppIcon(shortcutName).
                launch(testApp.getComponentName().getPackageName(), shortcutName);
    }
}
