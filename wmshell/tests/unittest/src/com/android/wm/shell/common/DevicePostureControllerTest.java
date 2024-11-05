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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DevicePostureControllerTest {
    @Mock
    private Context mContext;

    @Mock
    private ShellInit mShellInit;

    @Mock
    private ShellExecutor mMainExecutor;

    @Captor
    private ArgumentCaptor<Integer> mDevicePostureCaptor;

    @Mock
    private DevicePostureController.OnDevicePostureChangedListener mOnDevicePostureChangedListener;

    private DevicePostureController mDevicePostureController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDevicePostureController = new DevicePostureController(mContext, mShellInit, mMainExecutor);
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), eq(mDevicePostureController));
    }

    @Test
    public void registerOnDevicePostureChangedListener_callbackCurrentPosture() {
        mDevicePostureController.registerOnDevicePostureChangedListener(
                mOnDevicePostureChangedListener);
        verify(mOnDevicePostureChangedListener, times(1))
                .onDevicePostureChanged(anyInt());
    }

    @Test
    public void onDevicePostureChanged_differentPosture_callbackListener() {
        mDevicePostureController.registerOnDevicePostureChangedListener(
                mOnDevicePostureChangedListener);
        verify(mOnDevicePostureChangedListener).onDevicePostureChanged(
                mDevicePostureCaptor.capture());
        clearInvocations(mOnDevicePostureChangedListener);

        int differentDevicePosture = mDevicePostureCaptor.getValue() + 1;
        mDevicePostureController.onDevicePostureChanged(differentDevicePosture);

        verify(mOnDevicePostureChangedListener, times(1))
                .onDevicePostureChanged(differentDevicePosture);
    }

    @Test
    public void onDevicePostureChanged_samePosture_doesNotCallbackListener() {
        mDevicePostureController.registerOnDevicePostureChangedListener(
                mOnDevicePostureChangedListener);
        verify(mOnDevicePostureChangedListener).onDevicePostureChanged(
                mDevicePostureCaptor.capture());
        clearInvocations(mOnDevicePostureChangedListener);

        int sameDevicePosture = mDevicePostureCaptor.getValue();
        mDevicePostureController.onDevicePostureChanged(sameDevicePosture);

        verifyZeroInteractions(mOnDevicePostureChangedListener);
    }
}
