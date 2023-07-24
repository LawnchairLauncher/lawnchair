/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.ui.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.filters.LargeTest;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.icons.ThemedIconDrawable;
import com.android.launcher3.tapl.HomeAllApps;
import com.android.launcher3.tapl.HomeAppIcon;
import com.android.launcher3.tapl.HomeAppIconMenuItem;
import com.android.launcher3.ui.AbstractLauncherUiTest;
import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.util.Executors;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Tests for theme icon support in Launcher
 *
 * Note running these tests will clear the workspace on the device.
 */
@LargeTest
public class ThemeIconsTest extends AbstractLauncherUiTest {

    private static final String APP_NAME = "IconThemedActivity";
    private static final String SHORTCUT_APP_NAME = "LauncherTestApp";
    private static final String SHORTCUT_NAME = "Shortcut 1";

    @Test
    public void testIconWithoutTheme() throws Exception {
        setThemeEnabled(false);
        TaplTestsLauncher3.initialize(this);

        HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();

        try {
            HomeAppIcon icon = allApps.getAppIcon(APP_NAME);
            executeOnLauncher(l -> verifyIconTheme(APP_NAME, l.getAppsView(), false));
            icon.dragToWorkspace(false, false);
            executeOnLauncher(l -> verifyIconTheme(APP_NAME, l.getWorkspace(), false));
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    public void testShortcutIconWithoutTheme() throws Exception {
        setThemeEnabled(false);
        TaplTestsLauncher3.initialize(this);

        HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();

        try {
            HomeAppIcon icon = allApps.getAppIcon(SHORTCUT_APP_NAME);
            HomeAppIconMenuItem shortcutItem =
                    (HomeAppIconMenuItem) icon.openDeepShortcutMenu().getMenuItem(SHORTCUT_NAME);
            shortcutItem.dragToWorkspace(false, false);
            executeOnLauncher(l -> verifyIconTheme(SHORTCUT_NAME, l.getWorkspace(), false));
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    public void testIconWithTheme() throws Exception {
        setThemeEnabled(true);
        TaplTestsLauncher3.initialize(this);

        HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();

        try {
            HomeAppIcon icon = allApps.getAppIcon(APP_NAME);
            executeOnLauncher(l -> verifyIconTheme(APP_NAME, l.getAppsView(), false));
            icon.dragToWorkspace(false, false);
            executeOnLauncher(l -> verifyIconTheme(APP_NAME, l.getWorkspace(), true));
        } finally {
            allApps.unfreeze();
        }
    }

    @Test
    public void testShortcutIconWithTheme() throws Exception {
        setThemeEnabled(true);
        TaplTestsLauncher3.initialize(this);

        HomeAllApps allApps = mLauncher.getWorkspace().switchToAllApps();
        allApps.freeze();

        try {
            HomeAppIcon icon = allApps.getAppIcon(SHORTCUT_APP_NAME);
            HomeAppIconMenuItem shortcutItem =
                    (HomeAppIconMenuItem) icon.openDeepShortcutMenu().getMenuItem(SHORTCUT_NAME);
            shortcutItem.dragToWorkspace(false, false);
            executeOnLauncher(l -> verifyIconTheme(SHORTCUT_NAME, l.getWorkspace(), true));
        } finally {
            allApps.unfreeze();
        }
    }

    private void verifyIconTheme(String title, ViewGroup parent, boolean isThemed) {
        // Wait for Launcher model to be completed
        try {
            Executors.MODEL_EXECUTOR.submit(() -> { }).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Find the app icon
        Queue<View> viewQueue = new ArrayDeque<>();
        viewQueue.add(parent);
        BubbleTextView icon = null;
        while (!viewQueue.isEmpty()) {
            View view = viewQueue.poll();
            if (view instanceof ViewGroup) {
                parent = (ViewGroup) view;
                for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                    viewQueue.add(parent.getChildAt(i));
                }
            } else if (view instanceof BubbleTextView btv) {
                if (title.equals(btv.getContentDescription().toString())) {
                    icon = btv;
                    break;
                }
            }
        }

        assertNotNull(icon.getIcon());
        assertEquals(isThemed, icon.getIcon() instanceof ThemedIconDrawable);
    }

    private void setThemeEnabled(boolean isEnabled) throws Exception {
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(mTargetPackage + ".grid_control")
                .appendPath("set_icon_themed")
                .build();
        ContentValues values = new ContentValues();
        values.put("boolean_value", isEnabled);
        try (ContentProviderClient client = mTargetContext.getContentResolver()
                .acquireContentProviderClient(uri)) {
            int result = client.update(uri, values, null);
            assertTrue(result > 0);
        }
    }
}
