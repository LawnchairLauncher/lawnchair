package com.android.launcher3.ui;

import static org.junit.Assert.assertTrue;

import android.content.pm.LauncherActivityInfo;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.launcher3.popup.ArrowPopup;
import com.android.launcher3.tapl.AppIconMenu;
import com.android.launcher3.tapl.AppIconMenuItem;
import com.android.launcher3.views.OptionsPopupView;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for verifying that shortcuts are shown and can be launched after long pressing an app
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ShortcutsLaunchTest extends AbstractLauncherUiTest {

    private boolean isOptionsPopupVisible(Launcher launcher) {
        final ArrowPopup popup = OptionsPopupView.getOptionsPopup(launcher);
        return popup != null && popup.isShown();
    }

    @Test
    @PortraitLandscape
    public void testAppLauncher() throws Exception {
        mActivityMonitor.startLauncher();
        final LauncherActivityInfo testApp = getSettingsApp();

        final AppIconMenu menu = mLauncher.
                pressHome().
                switchToAllApps().
                getAppIcon(testApp.getLabel().toString()).
                openMenu();

        executeOnLauncher(
                launcher -> assertTrue("Launcher internal state didn't switch to Showing Menu",
                        isOptionsPopupVisible(launcher)));

        final AppIconMenuItem menuItem = menu.getMenuItem(1);
        final String itemName = menuItem.getText();

        menuItem.launch(testApp.getComponentName().getPackageName(), itemName);
    }
}
