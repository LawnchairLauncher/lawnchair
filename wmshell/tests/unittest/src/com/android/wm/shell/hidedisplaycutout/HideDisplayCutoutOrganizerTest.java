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
 */

package com.android.wm.shell.hidedisplaycutout;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.DisplayAreaOrganizer.FEATURE_HIDE_DISPLAY_CUTOUT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Binder;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.DisplayAreaAppearedInfo;
import android.window.DisplayAreaInfo;
import android.window.DisplayAreaOrganizer;
import android.window.IWindowContainerToken;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class HideDisplayCutoutOrganizerTest extends ShellTestCase {
    private TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), null);

    @Mock
    private DisplayController mMockDisplayController;
    private HideDisplayCutoutOrganizer mOrganizer;

    @Mock
    private ShellExecutor mMockMainExecutor;

    private DisplayAreaInfo mDisplayAreaInfo;
    private SurfaceControl mLeash;

    @Mock
    private Display mDisplay;
    @Mock
    private DisplayLayout mDisplayLayout;
    @Mock
    private IWindowContainerToken mMockRealToken;
    private WindowContainerToken mToken;

    private final Rect mFakeDefaultBounds = new Rect(0, 0, 100, 200);
    private final Insets mFakeDefaultCutoutInsets = Insets.of(0, 10, 0, 0);
    private final int mFakeStatusBarHeightPortrait = 15;
    private final int mFakeStatusBarHeightLandscape = 10;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockDisplayController.getDisplay(anyInt())).thenReturn(mDisplay);
        when(mMockDisplayController.getDisplayLayout(anyInt())).thenReturn(mDisplayLayout);

        HideDisplayCutoutOrganizer organizer = new HideDisplayCutoutOrganizer(
                mContext, mMockDisplayController, mMockMainExecutor);
        mOrganizer = Mockito.spy(organizer);
        doNothing().when(mOrganizer).unregisterOrganizer();
        doNothing().when(mOrganizer).applyBoundsAndOffsets(any(), any(), any(), any());
        doNothing().when(mOrganizer).applyTransaction(any(), any());

        // It will be called when mDisplayAreaMap.containKey(token) is called.
        Binder binder = new Binder();
        doReturn(binder).when(mMockRealToken).asBinder();
        mToken = new WindowContainerToken(mMockRealToken);
        mLeash = new SurfaceControl();
        mDisplayAreaInfo = new DisplayAreaInfo(
                mToken, DEFAULT_DISPLAY, FEATURE_HIDE_DISPLAY_CUTOUT);
        mDisplayAreaInfo.configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        DisplayAreaAppearedInfo info = new DisplayAreaAppearedInfo(mDisplayAreaInfo, mLeash);
        ArrayList<DisplayAreaAppearedInfo> infoList = new ArrayList<>();
        infoList.add(info);
        doReturn(infoList).when(mOrganizer).registerOrganizer(
                DisplayAreaOrganizer.FEATURE_HIDE_DISPLAY_CUTOUT);
    }

    @Test
    public void testEnableHideDisplayCutout() {
        doReturn(mFakeStatusBarHeightPortrait).when(mOrganizer).getStatusBarHeight();
        mOrganizer.enableHideDisplayCutout();

        verify(mOrganizer).registerOrganizer(DisplayAreaOrganizer.FEATURE_HIDE_DISPLAY_CUTOUT);
        verify(mOrganizer).addDisplayAreaInfoAndLeashToMap(mDisplayAreaInfo, mLeash);
        verify(mOrganizer).updateBoundsAndOffsets(true);
        assertThat(mOrganizer.mDisplayAreaMap.containsKey(mDisplayAreaInfo.token)).isTrue();
        assertThat(mOrganizer.mDisplayAreaMap.containsValue(mLeash)).isTrue();
    }

    @Test
    public void testOnDisplayAreaAppeared() {
        mOrganizer.onDisplayAreaAppeared(mDisplayAreaInfo, mLeash);

        assertThat(mOrganizer.mDisplayAreaMap.containsKey(mToken)).isTrue();
        assertThat(mOrganizer.mDisplayAreaMap.containsValue(mLeash)).isTrue();
    }

    @Test
    public void testOnDisplayAreaVanished() {
        mOrganizer.mDisplayAreaMap.put(mDisplayAreaInfo.token, mLeash);
        mOrganizer.onDisplayAreaVanished(mDisplayAreaInfo);

        assertThat(mOrganizer.mDisplayAreaMap.containsKey(mDisplayAreaInfo.token)).isFalse();
    }

    @Test
    public void testToggleHideDisplayCutout_enable_rot0() {
        doReturn(mFakeDefaultBounds).when(mOrganizer).getDisplayBoundsOfNaturalOrientation();
        doReturn(mFakeDefaultCutoutInsets).when(mOrganizer)
                .getDisplayCutoutInsetsOfNaturalOrientation();
        doReturn(mFakeStatusBarHeightPortrait).when(mOrganizer).getStatusBarHeight();
        doReturn(Surface.ROTATION_0).when(mDisplayLayout).rotation();
        mOrganizer.enableHideDisplayCutout();

        verify(mOrganizer).registerOrganizer(DisplayAreaOrganizer.FEATURE_HIDE_DISPLAY_CUTOUT);
        verify(mOrganizer).addDisplayAreaInfoAndLeashToMap(mDisplayAreaInfo, mLeash);
        verify(mOrganizer).updateBoundsAndOffsets(true);
        assertThat(mOrganizer.mCurrentDisplayBounds).isEqualTo(new Rect(0, 15, 100, 200));
        assertThat(mOrganizer.mOffsetX).isEqualTo(0);
        assertThat(mOrganizer.mOffsetY).isEqualTo(15);
        assertThat(mOrganizer.mRotation).isEqualTo(Surface.ROTATION_0);
    }

    @Test
    public void testToggleHideDisplayCutout_enable_rot90() {
        doReturn(mFakeDefaultBounds).when(mOrganizer).getDisplayBoundsOfNaturalOrientation();
        doReturn(mFakeDefaultCutoutInsets).when(mOrganizer)
                .getDisplayCutoutInsetsOfNaturalOrientation();
        doReturn(mFakeStatusBarHeightLandscape).when(mOrganizer).getStatusBarHeight();
        doReturn(Surface.ROTATION_90).when(mDisplayLayout).rotation();
        mOrganizer.enableHideDisplayCutout();

        verify(mOrganizer).registerOrganizer(DisplayAreaOrganizer.FEATURE_HIDE_DISPLAY_CUTOUT);
        verify(mOrganizer).addDisplayAreaInfoAndLeashToMap(mDisplayAreaInfo, mLeash);
        verify(mOrganizer).updateBoundsAndOffsets(true);
        assertThat(mOrganizer.mCurrentDisplayBounds).isEqualTo(new Rect(10, 0, 200, 100));
        assertThat(mOrganizer.mOffsetX).isEqualTo(10);
        assertThat(mOrganizer.mOffsetY).isEqualTo(0);
        assertThat(mOrganizer.mRotation).isEqualTo(Surface.ROTATION_90);
    }

    @Test
    public void testToggleHideDisplayCutout_enable_rot270() {
        doReturn(mFakeDefaultBounds).when(mOrganizer).getDisplayBoundsOfNaturalOrientation();
        doReturn(mFakeDefaultCutoutInsets).when(mOrganizer)
                .getDisplayCutoutInsetsOfNaturalOrientation();
        doReturn(mFakeStatusBarHeightLandscape).when(mOrganizer).getStatusBarHeight();
        doReturn(Surface.ROTATION_270).when(mDisplayLayout).rotation();
        mOrganizer.enableHideDisplayCutout();

        verify(mOrganizer).registerOrganizer(DisplayAreaOrganizer.FEATURE_HIDE_DISPLAY_CUTOUT);
        verify(mOrganizer).addDisplayAreaInfoAndLeashToMap(mDisplayAreaInfo, mLeash);
        verify(mOrganizer).updateBoundsAndOffsets(true);
        assertThat(mOrganizer.mCurrentDisplayBounds).isEqualTo(new Rect(0, 0, 190, 100));
        assertThat(mOrganizer.mOffsetX).isEqualTo(0);
        assertThat(mOrganizer.mOffsetY).isEqualTo(0);
        assertThat(mOrganizer.mRotation).isEqualTo(Surface.ROTATION_270);
    }

    @Test
    public void testToggleHideDisplayCutout_disable() {
        doReturn(mFakeDefaultBounds).when(mOrganizer).getDisplayBoundsOfNaturalOrientation();
        doReturn(mFakeDefaultCutoutInsets).when(mOrganizer)
                .getDisplayCutoutInsetsOfNaturalOrientation();
        doReturn(mFakeStatusBarHeightPortrait).when(mOrganizer).getStatusBarHeight();
        mOrganizer.enableHideDisplayCutout();

        // disable hide display cutout
        mOrganizer.disableHideDisplayCutout();
        verify(mOrganizer).updateBoundsAndOffsets(false);
        verify(mOrganizer).unregisterOrganizer();
        assertThat(mOrganizer.mCurrentDisplayBounds).isEqualTo(new Rect(0, 0, 0, 0));
        assertThat(mOrganizer.mOffsetX).isEqualTo(0);
        assertThat(mOrganizer.mOffsetY).isEqualTo(0);
    }

    @Test
    public void testDisplaySizeChange() {
        doReturn(100).when(mDisplayLayout).width();
        doReturn(200).when(mDisplayLayout).height();
        doReturn(mFakeDefaultCutoutInsets).when(mOrganizer)
                .getDisplayCutoutInsetsOfNaturalOrientation();
        doReturn(mFakeStatusBarHeightPortrait).when(mOrganizer).getStatusBarHeight();
        doReturn(Surface.ROTATION_0).when(mDisplayLayout).rotation();
        mOrganizer.enableHideDisplayCutout();
        assertThat(mOrganizer.mCurrentDisplayBounds).isEqualTo(new Rect(0, 15, 100, 200));

        doReturn(200).when(mDisplayLayout).width();
        doReturn(400).when(mDisplayLayout).height();
        mOrganizer.updateBoundsAndOffsets(true);
        assertThat(mOrganizer.mCurrentDisplayBounds).isEqualTo(new Rect(0, 15, 200, 400));
    }
}
