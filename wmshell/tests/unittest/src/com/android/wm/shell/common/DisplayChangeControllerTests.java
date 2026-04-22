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

package com.android.wm.shell.common;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.WindowConfiguration;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Size;
import android.view.IDisplayChangeWindowCallback;
import android.view.IWindowManager;
import android.view.Surface;
import android.window.DisplayAreaInfo;
import android.window.DisplayAreaOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the display change controller.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:DisplayChangeControllerTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayChangeControllerTests extends ShellTestCase {

    private @Mock IWindowManager mWM;
    private @Mock ShellInit mShellInit;
    private @Mock ShellExecutor mMainExecutor;
    private @Mock DisplayController mDisplayController;

    private @Mock DisplayLayout mMockDisplayLayout;
    private @Mock DisplayChangeController.OnDisplayChangingListener mMockOnDisplayChangingListener;
    private @Mock IDisplayChangeWindowCallback mMockDisplayChangeWindowCallback;
    private DisplayChangeController mController;

    private static final int DISPLAY_ID = 0;
    private static final int START_ROTATION = Surface.ROTATION_0;
    private static final int END_ROTATION = Surface.ROTATION_90;
    private static final Size DISPLAY_START_SIZE = new Size(100, 100);
    private static final Size DISPLAY_END_SIZE = new Size(200, 200);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mController = spy(new DisplayChangeController(mDisplayController, mWM, mShellInit,
                mMainExecutor));
        mController.addDisplayChangeListener(mMockOnDisplayChangingListener);
    }

    @Test
    public void instantiate_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void onDisplayChange_sizeChange_updateDisplayLayout_thenContinueDisplayChange() throws
            RemoteException {
        // set up the init display layout
        when(mMockDisplayLayout.width()).thenReturn(DISPLAY_START_SIZE.getWidth());
        when(mMockDisplayLayout.height()).thenReturn(DISPLAY_START_SIZE.getHeight());
        when(mDisplayController.getDisplayLayout(eq(DISPLAY_ID))).thenReturn(mMockDisplayLayout);

        // set up the new display area info
        final Rect startBounds = new Rect(0, 0,
                DISPLAY_START_SIZE.getWidth(), DISPLAY_START_SIZE.getHeight());
        final Rect endBounds = new Rect(0, 0,
                DISPLAY_END_SIZE.getWidth(), DISPLAY_END_SIZE.getHeight());

        // create a new display area info for size change
        final DisplayAreaInfo displayAreaInfo = createDisplayAreaInfo(DISPLAY_ID, endBounds);

        mController.onDisplayChange(DISPLAY_ID, START_ROTATION, START_ROTATION, displayAreaInfo,
                mMockDisplayChangeWindowCallback);

        // verify that local display layouts are updated
        verify(mDisplayController, times(1)).updateDisplayLayout(eq(DISPLAY_ID),
                eq(startBounds), eq(endBounds), eq(START_ROTATION), eq(START_ROTATION));

        // verify that display changing callbacks are dispatched
        verify(mMockOnDisplayChangingListener, times(1)).onDisplayChange(
                eq(DISPLAY_ID), eq(START_ROTATION), eq(START_ROTATION), eq(displayAreaInfo),
                any(WindowContainerTransaction.class));

        verify(mMockDisplayChangeWindowCallback, times(1))
                .continueDisplayChange(any(WindowContainerTransaction.class));
    }

    @Test
    public void onDisplayChange_rotationChange_updateDisplayLayout_thenContinueDisplayChange()
            throws RemoteException {
        // set up the init display layout
        when(mMockDisplayLayout.width()).thenReturn(DISPLAY_START_SIZE.getWidth());
        when(mMockDisplayLayout.height()).thenReturn(DISPLAY_START_SIZE.getHeight());
        when(mDisplayController.getDisplayLayout(eq(DISPLAY_ID))).thenReturn(mMockDisplayLayout);

        // set up the new display area info
        final Rect startBounds = new Rect(0, 0,
                DISPLAY_START_SIZE.getWidth(), DISPLAY_START_SIZE.getHeight());

        // create a new display area info for size change
        final DisplayAreaInfo displayAreaInfo = createDisplayAreaInfo(DISPLAY_ID, startBounds);

        mController.onDisplayChange(DISPLAY_ID, START_ROTATION, END_ROTATION, displayAreaInfo,
                mMockDisplayChangeWindowCallback);

        // verify that local display layouts are updated
        verify(mDisplayController, times(1)).updateDisplayLayout(eq(DISPLAY_ID),
                eq(startBounds), eq(startBounds), eq(START_ROTATION), eq(END_ROTATION));

        // verify that display changing callbacks are dispatched
        verify(mMockOnDisplayChangingListener, times(1)).onDisplayChange(
                eq(DISPLAY_ID), eq(START_ROTATION), eq(END_ROTATION), eq(displayAreaInfo),
                any(WindowContainerTransaction.class));

        verify(mMockDisplayChangeWindowCallback, times(1))
                .continueDisplayChange(any(WindowContainerTransaction.class));
    }

    private DisplayAreaInfo createDisplayAreaInfo(int displayId, Rect endBounds) {
        final WindowContainerToken mMockToken = mock(WindowContainerToken.class);
        final WindowConfiguration windowConfiguration = new WindowConfiguration();
        windowConfiguration.setBounds(endBounds);

        final DisplayAreaInfo displayAreaInfo = new DisplayAreaInfo(mMockToken,
                displayId, DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER);
        displayAreaInfo.configuration.windowConfiguration.setTo(windowConfiguration);
        return displayAreaInfo;
    }
}
