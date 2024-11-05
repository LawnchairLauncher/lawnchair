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

package com.android.wm.shell.pip.phone;

import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.lang.Integer.MAX_VALUE;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.RemoteException;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.WindowManagerShellWrapper;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TabletopModeController;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.PhonePipKeepClearAlgorithm;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.onehanded.OneHandedController;
import com.android.wm.shell.pip.PipAnimationController;
import com.android.wm.shell.pip.PipParamsChangedForwarder;
import com.android.wm.shell.pip.PipTaskOrganizer;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip.PipTransitionState;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.sysui.ShellSharedConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.Set;

/**
 * Unit tests for {@link PipController}
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class PipControllerTest extends ShellTestCase {
    private PipController mPipController;
    private ShellInit mShellInit;
    private ShellController mShellController;

    @Mock private ShellCommandHandler mMockShellCommandHandler;
    @Mock private DisplayController mMockDisplayController;
    @Mock private PhonePipMenuController mMockPhonePipMenuController;
    @Mock private PipAnimationController mMockPipAnimationController;
    @Mock private PipAppOpsListener mMockPipAppOpsListener;
    @Mock private PipBoundsAlgorithm mMockPipBoundsAlgorithm;
    @Mock private PhonePipKeepClearAlgorithm mMockPipKeepClearAlgorithm;
    @Mock private PipSnapAlgorithm mMockPipSnapAlgorithm;
    @Mock private PipMediaController mMockPipMediaController;
    @Mock private PipTaskOrganizer mMockPipTaskOrganizer;
    @Mock private PipTransitionState mMockPipTransitionState;
    @Mock private PipTransitionController mMockPipTransitionController;
    @Mock private PipTouchHandler mMockPipTouchHandler;
    @Mock private PipMotionHelper mMockPipMotionHelper;
    @Mock private WindowManagerShellWrapper mMockWindowManagerShellWrapper;
    @Mock private PipBoundsState mMockPipBoundsState;
    @Mock private PipDisplayLayoutState mMockPipDisplayLayoutState;
    @Mock private TaskStackListenerImpl mMockTaskStackListener;
    @Mock private ShellExecutor mMockExecutor;
    @Mock private Optional<OneHandedController> mMockOneHandedController;
    @Mock private PipParamsChangedForwarder mMockPipParamsChangedForwarder;
    @Mock private DisplayInsetsController mMockDisplayInsetsController;
    @Mock private TabletopModeController mMockTabletopModeController;

    @Mock private DisplayLayout mMockDisplayLayout1;
    @Mock private DisplayLayout mMockDisplayLayout2;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mMockExecutor).execute(any());
        mShellInit = spy(new ShellInit(mMockExecutor));
        mShellController = spy(new ShellController(mContext, mShellInit, mMockShellCommandHandler,
                mMockDisplayInsetsController, mMockExecutor));
        mPipController = new PipController(mContext, mShellInit, mMockShellCommandHandler,
                mShellController, mMockDisplayController, mMockPipAnimationController,
                mMockPipAppOpsListener, mMockPipBoundsAlgorithm, mMockPipKeepClearAlgorithm,
                mMockPipBoundsState, mMockPipDisplayLayoutState,
                mMockPipMotionHelper, mMockPipMediaController, mMockPhonePipMenuController,
                mMockPipTaskOrganizer, mMockPipTransitionState, mMockPipTouchHandler,
                mMockPipTransitionController, mMockWindowManagerShellWrapper,
                mMockTaskStackListener, mMockPipParamsChangedForwarder,
                mMockDisplayInsetsController, mMockTabletopModeController,
                mMockOneHandedController, mMockExecutor);
        mShellInit.init();
        when(mMockPipBoundsAlgorithm.getSnapAlgorithm()).thenReturn(mMockPipSnapAlgorithm);
        when(mMockPipTouchHandler.getMotionHelper()).thenReturn(mMockPipMotionHelper);
    }

    @Test
    public void instantiatePipController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), eq(mPipController));
    }

    @Test
    public void instantiateController_registerDumpCallback() {
        verify(mMockShellCommandHandler, times(1)).addDumpCallback(any(), eq(mPipController));
    }

    @Test
    public void instantiatePipController_registerConfigChangeListener() {
        verify(mShellController, times(1)).addConfigurationChangeListener(any());
    }

    @Test
    public void instantiatePipController_registerKeyguardChangeListener() {
        verify(mShellController, times(1)).addKeyguardChangeListener(any());
    }

    @Test
    public void instantiatePipController_registerExternalInterface() {
        verify(mShellController, times(1)).addExternalInterface(
                eq(ShellSharedConstants.KEY_EXTRA_SHELL_PIP), any(), eq(mPipController));
    }

    @Test
    public void instantiatePipController_registerUserChangeListener() {
        verify(mShellController, times(1)).addUserChangeListener(any());
    }

    @Test
    public void instantiatePipController_registerMediaListener() {
        verify(mMockPipMediaController, times(1)).registerSessionListenerForCurrentUser();
    }

    @Test
    public void instantiatePipController_registersPipTransitionCallback() {
        verify(mMockPipTransitionController).registerPipTransitionCallback(any());
    }

    @Test
    public void instantiatePipController_addsDisplayChangingController() {
        verify(mMockDisplayController).addDisplayChangingController(any());
    }

    @Test
    public void instantiatePipController_addsDisplayWindowListener() {
        verify(mMockDisplayController).addDisplayWindowListener(any());
    }

    @Test
    public void testInvalidateExternalInterface_unregistersListener() {
        mPipController.setPinnedStackAnimationListener(new PipController.PipAnimationListener() {
            @Override
            public void onPipAnimationStarted() {}
            @Override
            public void onPipResourceDimensionsChanged(int cornerRadius, int shadowRadius) {}
            @Override
            public void onExpandPip() {}
        });
        assertTrue(mPipController.hasPinnedStackAnimationListener());
        // Create initial interface
        mShellController.createExternalInterfaces(new Bundle());
        // Recreate the interface to trigger invalidation of the previous instance
        mShellController.createExternalInterfaces(new Bundle());
        assertFalse(mPipController.hasPinnedStackAnimationListener());
    }

    @Test
    public void createPip_notSupported_returnsNull() {
        Context spyContext = spy(mContext);
        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mockPackageManager.hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)).thenReturn(false);
        when(spyContext.getPackageManager()).thenReturn(mockPackageManager);

        ShellInit shellInit = new ShellInit(mMockExecutor);
        assertNull(PipController.create(spyContext, shellInit, mMockShellCommandHandler,
                mShellController, mMockDisplayController, mMockPipAnimationController,
                mMockPipAppOpsListener, mMockPipBoundsAlgorithm, mMockPipKeepClearAlgorithm,
                mMockPipBoundsState, mMockPipDisplayLayoutState,
                mMockPipMotionHelper, mMockPipMediaController, mMockPhonePipMenuController,
                mMockPipTaskOrganizer, mMockPipTransitionState, mMockPipTouchHandler,
                mMockPipTransitionController, mMockWindowManagerShellWrapper,
                mMockTaskStackListener, mMockPipParamsChangedForwarder,
                mMockDisplayInsetsController, mMockTabletopModeController,
                mMockOneHandedController, mMockExecutor));
    }

    @Test
    public void onActivityHidden_isLastPipComponentName_clearLastPipComponent() {
        final ComponentName component1 = new ComponentName(mContext, "component1");
        when(mMockPipBoundsState.getLastPipComponentName()).thenReturn(component1);

        mPipController.mPinnedTaskListener.onActivityHidden(component1);

        verify(mMockPipBoundsState).setLastPipComponentName(null);
    }

    @Test
    public void onActivityHidden_isNotLastPipComponentName_lastPipComponentNotCleared() {
        final ComponentName component1 = new ComponentName(mContext, "component1");
        final ComponentName component2 = new ComponentName(mContext, "component2");
        when(mMockPipBoundsState.getLastPipComponentName()).thenReturn(component1);

        mPipController.mPinnedTaskListener.onActivityHidden(component2);

        verify(mMockPipBoundsState, never()).setLastPipComponentName(null);
    }

    @Test
    public void saveReentryState_savesPipBoundsState() {
        final Rect bounds = new Rect(0, 0, 10, 10);
        when(mMockPipBoundsAlgorithm.getSnapFraction(bounds)).thenReturn(1.0f);

        mPipController.saveReentryState(bounds);

        verify(mMockPipBoundsState).saveReentryState(1.0f);
    }

    @Test
    public void onDisplayConfigurationChanged_inPip_movePip() {
        final int displayId = 1;
        final Rect bounds = new Rect(0, 0, 10, 10);
        when(mMockPipBoundsAlgorithm.getDefaultBounds()).thenReturn(bounds);
        when(mMockPipBoundsState.getBounds()).thenReturn(bounds);
        when(mMockPipBoundsState.getMinSize()).thenReturn(new Point(1, 1));
        when(mMockPipBoundsState.getMaxSize()).thenReturn(new Point(MAX_VALUE, MAX_VALUE));
        when(mMockPipBoundsState.getBounds()).thenReturn(bounds);
        when(mMockPipDisplayLayoutState.getDisplayId()).thenReturn(displayId);
        when(mMockPipDisplayLayoutState.getDisplayLayout()).thenReturn(mMockDisplayLayout1);
        when(mMockDisplayController.getDisplayLayout(displayId)).thenReturn(mMockDisplayLayout2);

        when(mMockPipTaskOrganizer.isInPip()).thenReturn(true);
        mPipController.mDisplaysChangedListener.onDisplayConfigurationChanged(
                displayId, new Configuration());

        verify(mMockPipTaskOrganizer).scheduleFinishResizePip(any(Rect.class));
    }

    @Test
    public void onDisplayConfigurationChanged_notInPip_doesNotMovePip() {
        final int displayId = 1;
        final Rect bounds = new Rect(0, 0, 10, 10);
        when(mMockPipBoundsAlgorithm.getDefaultBounds()).thenReturn(bounds);
        when(mMockPipDisplayLayoutState.getDisplayId()).thenReturn(displayId);
        when(mMockPipDisplayLayoutState.getDisplayLayout()).thenReturn(mMockDisplayLayout1);
        when(mMockDisplayController.getDisplayLayout(displayId)).thenReturn(mMockDisplayLayout2);

        when(mMockPipTaskOrganizer.isInPip()).thenReturn(false);
        mPipController.mDisplaysChangedListener.onDisplayConfigurationChanged(
                displayId, new Configuration());

        verify(mMockPipTaskOrganizer, never()).scheduleFinishResizePip(any(Rect.class));
    }

    @Test
    public void onKeepClearAreasChanged_updatesPipBoundsState() {
        final int displayId = 1;
        final Rect keepClearArea = new Rect(0, 0, 10, 10);
        when(mMockPipDisplayLayoutState.getDisplayId()).thenReturn(displayId);

        mPipController.mDisplaysChangedListener.onKeepClearAreasChanged(
                displayId, Set.of(keepClearArea), Set.of());

        verify(mMockPipBoundsState).setKeepClearAreas(Set.of(keepClearArea), Set.of());
    }

    @Test
    public void onUserChangeRegisterMediaListener() {
        reset(mMockPipMediaController);
        mShellController.asShell().onUserChanged(100, mContext);
        verify(mMockPipMediaController, times(1)).registerSessionListenerForCurrentUser();
    }
}
