/*
 * Copyright (C) 2020 The Android Open Source Project
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
 */package com.android.launcher3.ui;

import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;
import static com.android.launcher3.util.LauncherUIHelper.buildAndBindLauncher;
import static com.android.launcher3.util.LauncherUIHelper.doLayout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerProperties;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.FolderPagedView;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.shadows.ShadowOverrides;
import com.android.launcher3.util.LauncherLayoutBuilder;
import com.android.launcher3.util.LauncherLayoutBuilder.FolderBuilder;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.widget.WidgetsFullSheet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadows.ShadowLooper;

/**
 * Tests scroll behavior at various Launcher UI components
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class LauncherUIScrollTest {

    private Context mTargetContext;
    private InvariantDeviceProfile mIdp;
    private LauncherModelHelper mModelHelper;

    private LauncherLayoutBuilder mLayoutBuilder;

    @Before
    public void setup() throws Exception {
        mModelHelper = new LauncherModelHelper();
        mTargetContext = RuntimeEnvironment.application;
        mIdp = InvariantDeviceProfile.INSTANCE.get(mTargetContext);
        ShadowOverrides.setProvider(UserEventDispatcher.class,
                c -> mock(UserEventDispatcher.class));

        Settings.Global.putFloat(mTargetContext.getContentResolver(),
                Settings.Global.WINDOW_ANIMATION_SCALE, 0);

        mModelHelper.installApp(TEST_PACKAGE);
        // LayoutBuilder with 3 workspace pages
        mLayoutBuilder = new LauncherLayoutBuilder()
                .atWorkspace(0,  mIdp.numRows - 1, 0).putApp(TEST_PACKAGE, TEST_PACKAGE)
                .atWorkspace(0,  mIdp.numRows - 1, 1).putApp(TEST_PACKAGE, TEST_PACKAGE)
                .atWorkspace(0,  mIdp.numRows - 1, 2).putApp(TEST_PACKAGE, TEST_PACKAGE);
    }

    @Test
    public void testWorkspacePagesBound() throws Exception {
        // Verify that the workspace if bound synchronously
        Launcher launcher = loadLauncher();
        assertEquals(3, launcher.getWorkspace().getPageCount());
        assertEquals(0, launcher.getWorkspace().getCurrentPage());

        launcher.dispatchGenericMotionEvent(createScrollEvent(-1));
        assertNotEquals("Workspace was not scrolled",
                0, launcher.getWorkspace().getNextPage());
    }

    @Test
    public void testAllAppsScroll() throws Exception {
        // Install 100 apps
        for (int i = 0; i < 100; i++) {
            mModelHelper.installApp(TEST_PACKAGE + i);
        }

        // Bind and open all-apps
        Launcher launcher = loadLauncher();
        launcher.getStateManager().goToState(LauncherState.ALL_APPS, false);
        doLayout(launcher);

        int currentScroll = launcher.getAppsView().getActiveRecyclerView().getCurrentScrollY();
        launcher.dispatchGenericMotionEvent(createScrollEvent(-1));
        int newScroll = launcher.getAppsView().getActiveRecyclerView().getCurrentScrollY();

        assertNotEquals("All Apps was not scrolled", currentScroll, newScroll);
        assertEquals("Workspace was scrolled", 0, launcher.getWorkspace().getNextPage());
    }

    @Test
    public void testWidgetsListScroll() throws Exception {
        // Install 100 widgets
        for (int i = 0; i < 100; i++) {
            mModelHelper.installCustomShortcut(TEST_PACKAGE + i, "shortcutProvider");
        }

        // Bind and open widgets
        Launcher launcher = loadLauncher();
        WidgetsFullSheet widgets = WidgetsFullSheet.show(launcher, false);
        doLayout(launcher);

        int currentScroll = widgets.getRecyclerView().getCurrentScrollY();
        launcher.dispatchGenericMotionEvent(createScrollEvent(-1));
        int newScroll = widgets.getRecyclerView().getCurrentScrollY();
        assertNotEquals("Widgets was not scrolled", currentScroll, newScroll);
        assertEquals("Workspace was scrolled", 0, launcher.getWorkspace().getNextPage());
    }

    @Test
    public void testFolderPageScroll() throws Exception {
        // Add a folder with multiple icons
        FolderBuilder fb = mLayoutBuilder.atWorkspace(mIdp.numColumns / 2, mIdp.numRows / 2, 0)
                .putFolder(0);
        for (int i = 0; i < 100; i++) {
            fb.addApp(TEST_PACKAGE, TEST_PACKAGE);
        }

        // Bind and open folder
        Launcher launcher = loadLauncher();
        doLayout(launcher);
        launcher.getWorkspace().getFirstMatch((i, v) -> v instanceof FolderIcon).performClick();
        ShadowLooper.idleMainLooper();
        doLayout(launcher);
        FolderPagedView folderPages = Folder.getOpen(launcher).getContent();

        assertEquals(0, folderPages.getNextPage());
        launcher.dispatchGenericMotionEvent(createScrollEvent(-1));
        assertNotEquals("Folder page was not scrolled", 0, folderPages.getNextPage());
        assertEquals("Workspace was scrolled", 0, launcher.getWorkspace().getNextPage());
    }

    private Launcher loadLauncher() throws Exception {
        mModelHelper.setupDefaultLayoutProvider(mLayoutBuilder).loadModelSync();
        return buildAndBindLauncher();
    }

    private static MotionEvent createScrollEvent(int scroll) {
        DeviceProfile dp = InvariantDeviceProfile.INSTANCE
                .get(RuntimeEnvironment.application).portraitProfile;

        final PointerProperties[] pointerProperties = new PointerProperties[1];
        pointerProperties[0] = new PointerProperties();
        pointerProperties[0].id = 0;
        final MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, scroll);
        coords[0].x = dp.widthPx / 2;
        coords[0].y = dp.heightPx / 2;

        final long time = SystemClock.uptimeMillis();
        return MotionEvent.obtain(time, time, MotionEvent.ACTION_SCROLL, 1,
                pointerProperties, coords, 0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_CLASS_POINTER, 0);
    }

}
