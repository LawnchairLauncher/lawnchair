/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.common.DevicePostureController.DEVICE_POSTURE_CLOSED;
import static com.android.wm.shell.common.DevicePostureController.DEVICE_POSTURE_HALF_OPENED;
import static com.android.wm.shell.common.DevicePostureController.DEVICE_POSTURE_OPENED;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link TabletopModeController}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class TabletopModeControllerTest extends ShellTestCase {
    // It's considered tabletop mode if the display rotation angle matches what's in this array.
    // It's defined as com.android.internal.R.array.config_deviceTabletopRotations on real devices.
    private static final int[] TABLETOP_MODE_ROTATIONS = new int[] {
            90 /* Surface.ROTATION_90 */,
            270 /* Surface.ROTATION_270 */
    };

    private TestShellExecutor mMainExecutor;

    private Configuration mConfiguration;

    private TabletopModeController mPipTabletopController;

    @Mock
    private Context mContext;

    @Mock
    private ShellInit mShellInit;

    @Mock
    private Resources mResources;

    @Mock
    private DevicePostureController mDevicePostureController;

    @Mock
    private DisplayController mDisplayController;

    @Mock
    private TabletopModeController.OnTabletopModeChangedListener mOnTabletopModeChangedListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mResources.getIntArray(com.android.internal.R.array.config_deviceTabletopRotations))
                .thenReturn(TABLETOP_MODE_ROTATIONS);
        when(mContext.getResources()).thenReturn(mResources);
        mMainExecutor = new TestShellExecutor();
        mConfiguration = new Configuration();
        mPipTabletopController = new TabletopModeController(mContext, mShellInit,
                mDevicePostureController, mDisplayController, mMainExecutor);
        mPipTabletopController.onInit();
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), eq(mPipTabletopController));
    }

    @Test
    public void registerOnTabletopModeChangedListener_notInTabletopMode_callbackFalse() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_0);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.registerOnTabletopModeChangedListener(
                mOnTabletopModeChangedListener);

        verify(mOnTabletopModeChangedListener, times(1))
                .onTabletopModeChanged(false);
    }

    @Test
    public void registerOnTabletopModeChangedListener_inTabletopMode_callbackTrue() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.registerOnTabletopModeChangedListener(
                mOnTabletopModeChangedListener);

        verify(mOnTabletopModeChangedListener, times(1))
                .onTabletopModeChanged(true);
    }

    @Test
    public void registerOnTabletopModeChangedListener_notInTabletopModeTwice_callbackOnce() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.registerOnTabletopModeChangedListener(
                mOnTabletopModeChangedListener);
        clearInvocations(mOnTabletopModeChangedListener);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_0);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        verifyZeroInteractions(mOnTabletopModeChangedListener);
    }

    // Test cases starting from folded state (DEVICE_POSTURE_CLOSED)
    @Test
    public void foldedRotation90_halfOpen_scheduleTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);

        assertTrue(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    @Test
    public void foldedRotation0_halfOpen_noScheduleTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_0);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);

        assertFalse(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    @Test
    public void foldedRotation90_halfOpenThenUnfold_cancelTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_OPENED);

        assertFalse(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    @Test
    public void foldedRotation90_halfOpenThenFold_cancelTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);

        assertFalse(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    @Test
    public void foldedRotation90_halfOpenThenRotate_cancelTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_0);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        assertFalse(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    // Test cases starting from unfolded state (DEVICE_POSTURE_OPENED)
    @Test
    public void unfoldedRotation90_halfOpen_scheduleTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_OPENED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);

        assertTrue(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    @Test
    public void unfoldedRotation0_halfOpen_noScheduleTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_OPENED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_0);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);

        assertFalse(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    @Test
    public void unfoldedRotation90_halfOpenThenUnfold_cancelTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_OPENED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_OPENED);

        assertFalse(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    @Test
    public void unfoldedRotation90_halfOpenThenFold_cancelTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_OPENED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_CLOSED);

        assertFalse(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }

    @Test
    public void unfoldedRotation90_halfOpenThenRotate_cancelTabletopModeChange() {
        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_OPENED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_90);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        mPipTabletopController.onDevicePostureChanged(DEVICE_POSTURE_HALF_OPENED);
        mConfiguration.windowConfiguration.setDisplayRotation(Surface.ROTATION_0);
        mPipTabletopController.onDisplayConfigurationChanged(DEFAULT_DISPLAY, mConfiguration);

        assertFalse(mMainExecutor.hasCallback(mPipTabletopController.mOnEnterTabletopModeCallback));
    }
}
