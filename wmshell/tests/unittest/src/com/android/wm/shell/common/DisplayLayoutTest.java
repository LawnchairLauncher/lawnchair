/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.wm.shell.common;

import static android.content.res.Configuration.UI_MODE_TYPE_NORMAL;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.DisplayCutout;
import android.view.DisplayInfo;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.internal.policy.SystemBarUtils;
import com.android.wm.shell.ShellTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/**
 * Tests for {@link DisplayLayout}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:DisplayLayoutTest
 */
@SmallTest
public class DisplayLayoutTest extends ShellTestCase {
    private MockitoSession mMockitoSession;

    @Before
    public void setup() {
        mMockitoSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.WARN)
                .mockStatic(SystemBarUtils.class)
                .startMocking();
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testInsets() {
        Resources res = createResources(40, 50, false);
        // Test empty display, no bars or anything
        DisplayInfo info = createDisplayInfo(1000, 1500, 0, ROTATION_0);
        DisplayLayout dl = new DisplayLayout(info, res, false, false);
        when(SystemBarUtils.getStatusBarHeight(eq(res), any())).thenReturn(40);
        dl.recalcInsets(res);
        assertEquals(new Rect(0, 0, 0, 0), dl.stableInsets());
        assertEquals(new Rect(0, 0, 0, 0), dl.nonDecorInsets());

        // Test with bars
        dl = new DisplayLayout(info, res, true, true);
        dl.recalcInsets(res);
        assertEquals(new Rect(0, 40, 0, 50), dl.stableInsets());
        assertEquals(new Rect(0, 0, 0, 50), dl.nonDecorInsets());

        // Test just cutout
        info = createDisplayInfo(1000, 1500, 60, ROTATION_0);
        dl = new DisplayLayout(info, res, false, false);
        dl.recalcInsets(res);
        assertEquals(new Rect(0, 60, 0, 0), dl.stableInsets());
        assertEquals(new Rect(0, 60, 0, 0), dl.nonDecorInsets());

        // Test with bars and cutout
        dl = new DisplayLayout(info, res, true, true);
        dl.recalcInsets(res);
        assertEquals(new Rect(0, 60, 0, 50), dl.stableInsets());
        assertEquals(new Rect(0, 60, 0, 50), dl.nonDecorInsets());
    }

    @Test
    public void testRotate() {
        // Basic rotate utility
        Resources res = createResources(40, 50, false);
        DisplayInfo info = createDisplayInfo(1000, 1500, 60, ROTATION_0);
        DisplayLayout dl = new DisplayLayout(info, res, true, true);
        when(SystemBarUtils.getStatusBarHeight(eq(res), any())).thenReturn(40);
        dl.recalcInsets(res);
        assertEquals(new Rect(0, 60, 0, 50), dl.stableInsets());
        assertEquals(new Rect(0, 60, 0, 50), dl.nonDecorInsets());

        // Rotate to 90
        when(SystemBarUtils.getStatusBarHeight(eq(res), any())).thenReturn(30);
        dl.rotateTo(res, ROTATION_90);
        assertEquals(new Rect(60, 30, 0, 40), dl.stableInsets());
        assertEquals(new Rect(60, 0, 0, 40), dl.nonDecorInsets());

        // Rotate with moving navbar
        res = createResources(40, 50, true);
        dl = new DisplayLayout(info, res, true, true);
        when(SystemBarUtils.getStatusBarHeight(eq(res), any())).thenReturn(30);
        dl.rotateTo(res, ROTATION_270);
        assertEquals(new Rect(40, 30, 60, 0), dl.stableInsets());
        assertEquals(new Rect(40, 0, 60, 0), dl.nonDecorInsets());
    }

    private Resources createResources(int navLand, int navPort, boolean navMoves) {
        Configuration cfg = new Configuration();
        cfg.uiMode = UI_MODE_TYPE_NORMAL;
        Resources res = mock(Resources.class);
        doReturn(navLand).when(res).getDimensionPixelSize(
                R.dimen.navigation_bar_height_landscape_car_mode);
        doReturn(navPort).when(res).getDimensionPixelSize(R.dimen.navigation_bar_height_car_mode);
        doReturn(navLand).when(res).getDimensionPixelSize(R.dimen.navigation_bar_width_car_mode);
        doReturn(navLand).when(res).getDimensionPixelSize(R.dimen.navigation_bar_height_landscape);
        doReturn(navPort).when(res).getDimensionPixelSize(R.dimen.navigation_bar_height);
        doReturn(navLand).when(res).getDimensionPixelSize(R.dimen.navigation_bar_width);
        doReturn(navMoves).when(res).getBoolean(R.bool.config_navBarCanMove);
        doReturn(cfg).when(res).getConfiguration();
        return res;
    }

    private DisplayInfo createDisplayInfo(int width, int height, int cutoutHeight, int rotation) {
        DisplayInfo info = new DisplayInfo();
        info.logicalWidth = width;
        info.logicalHeight = height;
        info.rotation = rotation;
        if (cutoutHeight > 0) {
            info.displayCutout = new DisplayCutout(
                    Insets.of(0, cutoutHeight, 0, 0) /* safeInsets */, null /* boundLeft */,
                    new Rect(width / 2 - cutoutHeight, 0, width / 2 + cutoutHeight,
                            cutoutHeight) /* boundTop */, null /* boundRight */,
                    null /* boundBottom */);
        } else {
            info.displayCutout = DisplayCutout.NO_CUTOUT;
        }
        info.logicalDensityDpi = 300;
        return info;
    }
}
