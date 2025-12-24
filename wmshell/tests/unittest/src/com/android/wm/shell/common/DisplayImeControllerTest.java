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

package com.android.wm.shell.common;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.InsetsSource.ID_IME;
import static android.view.Surface.ROTATION_0;
import static android.view.WindowInsets.Type.ime;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.graphics.Insets;
import android.graphics.Point;
import android.os.Looper;
import android.view.IWindowManager;
import android.view.InsetsSource;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.inputmethod.ImeTracker;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/**
 * Tests for the display IME controller.
 *
 * <p> Build/Install/Run:
 *  atest WMShellUnitTests:DisplayImeControllerTest
 */
@SmallTest
public class DisplayImeControllerTest extends ShellTestCase {
    @Mock
    private SurfaceControl.Transaction mT;
    @Mock
    private ShellInit mShellInit;
    @Mock
    private IWindowManager mWm;
    private DisplayImeController mDisplayImeController;
    private DisplayImeController.PerDisplay mPerDisplay;
    private Executor mExecutor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mExecutor = spy(Runnable::run);
        mDisplayImeController = new DisplayImeController(mWm, mShellInit, null, null,
                new TransactionPool() {
                    @Override
                    public SurfaceControl.Transaction acquire() {
                        return mT;
                    }

                    @Override
                    public void release(SurfaceControl.Transaction t) {
                    }
                }, mExecutor);
        mPerDisplay = mDisplayImeController.new PerDisplay(DEFAULT_DISPLAY, ROTATION_0);
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void insetsControlChanged_schedulesNoWorkOnExecutor() {
        Looper.prepare();
        mPerDisplay.insetsControlChanged(insetsStateWithIme(false), insetsSourceControl());
        verifyNoMoreInteractions(mExecutor);
    }

    @Test
    public void insetsChanged_schedulesNoWorkOnExecutor() {
        Looper.prepare();
        mPerDisplay.insetsChanged(insetsStateWithIme(false));
        verifyNoMoreInteractions(mExecutor);
    }

    @Test
    public void showInsets_schedulesNoWorkOnExecutor() {
        mPerDisplay.showInsets(ime(), ImeTracker.Token.empty());
        verifyNoMoreInteractions(mExecutor);
    }

    @Test
    public void hideInsets_schedulesNoWorkOnExecutor() {
        mPerDisplay.hideInsets(ime(), ImeTracker.Token.empty());
        verifyNoMoreInteractions(mExecutor);
    }

    @Test
    public void insetsControlChanged_updateImeSourceControl() {
        Looper.prepare();
        mPerDisplay.insetsControlChanged(insetsStateWithIme(false), insetsSourceControl());
        assertNotNull(mPerDisplay.mImeSourceControl);

        mPerDisplay.insetsControlChanged(new InsetsState(), new InsetsSourceControl[]{});
        assertNull(mPerDisplay.mImeSourceControl);
    }

    @Test
    public void setImeInputTargetRequestedVisibility_invokeOnImeRequested() {
        var mockPp = mock(DisplayImeController.ImePositionProcessor.class);
        mDisplayImeController.addPositionProcessor(mockPp);

        mPerDisplay.setImeInputTargetRequestedVisibility(true, null /* statsToken */);
        verify(mockPp).onImeRequested(anyInt(), eq(true));

        mPerDisplay.setImeInputTargetRequestedVisibility(false, null /* statsToken */);
        verify(mockPp).onImeRequested(anyInt(), eq(false));
    }

    private InsetsSourceControl[] insetsSourceControl() {
        return new InsetsSourceControl[]{
                new InsetsSourceControl(
                        ID_IME, ime(), mock(SurfaceControl.class), false, new Point(0, 0),
                        Insets.NONE)
        };
    }

    private InsetsState insetsStateWithIme(boolean visible) {
        InsetsState state = new InsetsState();
        state.addSource(new InsetsSource(ID_IME, ime()));
        state.setSourceVisible(ID_IME, visible);
        return state;
    }

}
