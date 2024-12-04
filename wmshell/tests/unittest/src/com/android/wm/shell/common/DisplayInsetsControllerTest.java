/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.os.RemoteException;
import android.util.SparseArray;
import android.view.IDisplayWindowInsetsController;
import android.view.IWindowManager;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.WindowInsets;
import android.view.inputmethod.ImeTracker;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Tests for the display insets controller.
 *
 * <p> Build/Install/Run:
 *  atest WMShellUnitTests:DisplayInsetsControllerTest
 */
@SmallTest
public class DisplayInsetsControllerTest extends ShellTestCase {

    private static final int SECOND_DISPLAY = DEFAULT_DISPLAY + 10;

    @Mock
    private IWindowManager mWm;
    @Mock
    private DisplayController mDisplayController;
    @Mock
    private ShellInit mShellInit;
    private DisplayInsetsController mController;
    private SparseArray<IDisplayWindowInsetsController> mInsetsControllersByDisplayId;
    private TestShellExecutor mExecutor;

    private ArgumentCaptor<Integer> mDisplayIdCaptor;
    private ArgumentCaptor<IDisplayWindowInsetsController> mInsetsControllerCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mExecutor = new TestShellExecutor();
        mInsetsControllersByDisplayId = new SparseArray<>();
        mDisplayIdCaptor =  ArgumentCaptor.forClass(Integer.class);
        mInsetsControllerCaptor = ArgumentCaptor.forClass(IDisplayWindowInsetsController.class);
        mController = new DisplayInsetsController(mWm, mShellInit, mDisplayController, mExecutor);
        addDisplay(DEFAULT_DISPLAY);
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void testOnDisplayAdded_setsDisplayWindowInsetsControllerOnWMService()
            throws RemoteException {
        addDisplay(SECOND_DISPLAY);

        verify(mWm).setDisplayWindowInsetsController(eq(SECOND_DISPLAY), notNull());
    }

    @Test
    public void testOnDisplayRemoved_unsetsDisplayWindowInsetsControllerInWMService()
            throws RemoteException {
        addDisplay(SECOND_DISPLAY);
        removeDisplay(SECOND_DISPLAY);

        verify(mWm).setDisplayWindowInsetsController(SECOND_DISPLAY, null);
    }

    @Test
    public void testPerDisplayListenerCallback() throws RemoteException {
        TrackedListener defaultListener = new TrackedListener();
        TrackedListener secondListener = new TrackedListener();
        addDisplay(SECOND_DISPLAY);
        mController.addInsetsChangedListener(DEFAULT_DISPLAY, defaultListener);
        mController.addInsetsChangedListener(SECOND_DISPLAY, secondListener);

        mInsetsControllersByDisplayId.get(DEFAULT_DISPLAY).topFocusedWindowChanged(null,
                WindowInsets.Type.defaultVisible());
        mInsetsControllersByDisplayId.get(DEFAULT_DISPLAY).insetsChanged(null);
        mInsetsControllersByDisplayId.get(DEFAULT_DISPLAY).insetsControlChanged(null, null);
        mInsetsControllersByDisplayId.get(DEFAULT_DISPLAY).showInsets(0, false,
                ImeTracker.Token.empty());
        mInsetsControllersByDisplayId.get(DEFAULT_DISPLAY).hideInsets(0, false,
                ImeTracker.Token.empty());
        mExecutor.flushAll();

        assertTrue(defaultListener.topFocusedWindowChangedCount == 1);
        assertTrue(defaultListener.insetsChangedCount == 1);
        assertTrue(defaultListener.insetsControlChangedCount == 1);
        assertTrue(defaultListener.showInsetsCount == 1);
        assertTrue(defaultListener.hideInsetsCount == 1);

        assertTrue(secondListener.topFocusedWindowChangedCount == 0);
        assertTrue(secondListener.insetsChangedCount == 0);
        assertTrue(secondListener.insetsControlChangedCount == 0);
        assertTrue(secondListener.showInsetsCount == 0);
        assertTrue(secondListener.hideInsetsCount == 0);

        mInsetsControllersByDisplayId.get(SECOND_DISPLAY).topFocusedWindowChanged(null,
                WindowInsets.Type.defaultVisible());
        mInsetsControllersByDisplayId.get(SECOND_DISPLAY).insetsChanged(null);
        mInsetsControllersByDisplayId.get(SECOND_DISPLAY).insetsControlChanged(null, null);
        mInsetsControllersByDisplayId.get(SECOND_DISPLAY).showInsets(0, false,
                ImeTracker.Token.empty());
        mInsetsControllersByDisplayId.get(SECOND_DISPLAY).hideInsets(0, false,
                ImeTracker.Token.empty());
        mExecutor.flushAll();

        assertTrue(defaultListener.topFocusedWindowChangedCount == 1);
        assertTrue(defaultListener.insetsChangedCount == 1);
        assertTrue(defaultListener.insetsControlChangedCount == 1);
        assertTrue(defaultListener.showInsetsCount == 1);
        assertTrue(defaultListener.hideInsetsCount == 1);

        assertTrue(secondListener.topFocusedWindowChangedCount == 1);
        assertTrue(secondListener.insetsChangedCount == 1);
        assertTrue(secondListener.insetsControlChangedCount == 1);
        assertTrue(secondListener.showInsetsCount == 1);
        assertTrue(secondListener.hideInsetsCount == 1);
    }

    private void addDisplay(int displayId) throws RemoteException {
        mController.onDisplayAdded(displayId);
        verify(mWm, times(mInsetsControllersByDisplayId.size() + 1))
                .setDisplayWindowInsetsController(mDisplayIdCaptor.capture(),
                        mInsetsControllerCaptor.capture());
        List<Integer> displayIds = mDisplayIdCaptor.getAllValues();
        List<IDisplayWindowInsetsController> insetsControllers =
                mInsetsControllerCaptor.getAllValues();
        for (int i = 0; i < displayIds.size(); i++) {
            mInsetsControllersByDisplayId.put(displayIds.get(i), insetsControllers.get(i));
        }
    }

    private void removeDisplay(int displayId) {
        mController.onDisplayRemoved(displayId);
        mInsetsControllersByDisplayId.remove(displayId);
    }

    private static class TrackedListener implements
            DisplayInsetsController.OnInsetsChangedListener {
        int topFocusedWindowChangedCount = 0;
        int insetsChangedCount = 0;
        int insetsControlChangedCount = 0;
        int showInsetsCount = 0;
        int hideInsetsCount = 0;

        @Override
        public void topFocusedWindowChanged(ComponentName component, int requestedVisibleTypes) {
            topFocusedWindowChangedCount++;
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            insetsChangedCount++;
        }

        @Override
        public void insetsControlChanged(InsetsState insetsState,
                InsetsSourceControl[] activeControls) {
            insetsControlChangedCount++;
        }

        @Override
        public void showInsets(int types, boolean fromIme, @Nullable ImeTracker.Token statsToken) {
            showInsetsCount++;
        }

        @Override
        public void hideInsets(int types, boolean fromIme, @Nullable ImeTracker.Token statsToken) {
            hideInsetsCount++;
        }
    }
}
