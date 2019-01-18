package com.android.launcher3.ui;

import android.content.pm.LauncherActivityInfo;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for dragging an icon from all-apps to homescreen.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AllAppsIconToHomeTest extends AbstractLauncherUiTest {

    @Test
    @PortraitLandscape
    public void testDragIcon() throws Throwable {
        LauncherActivityInfo settingsApp = getSettingsApp();

        clearHomescreen();

        final String appName = settingsApp.getLabel().toString();
        // 1. Open all apps and wait for load complete.
        // 2. Drag icon to homescreen.
        // 3. Verify that the icon works on homescreen.
        mLauncher.pressHome().
                switchToAllApps().
                getAppIcon(appName).
                dragToWorkspace().
                getWorkspaceAppIcon(appName).
                launch(settingsApp.getComponentName().getPackageName());
    }
}
