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

package com.android.wm.shell.startingsurface;

import static android.view.WindowManager.TRANSIT_OPEN;
import static android.window.TransitionInfo.FLAG_IS_BEHIND_STARTING_WINDOW;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.view.Display;
import android.window.StartingWindowRemovalInfo;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.util.function.TriConsumer;
import com.android.launcher3.icons.IconProvider;
import com.android.server.testutils.StubTransaction;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the starting window controller.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:StartingWindowControllerTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class StartingWindowControllerTests extends ShellTestCase {

    private @Mock Context mContext;
    private @Mock DisplayManager mDisplayManager;
    private @Mock DisplayInsetsController mDisplayInsetsController;
    private @Mock ShellCommandHandler mShellCommandHandler;
    private @Mock ShellTaskOrganizer mTaskOrganizer;
    private @Mock ShellExecutor mSplashScreenExecutor;
    private @Mock StartingWindowTypeAlgorithm mTypeAlgorithm;
    private @Mock IconProvider mIconProvider;
    private @Mock TransactionPool mTransactionPool;
    private @Mock UserManager mUserManager;
    private @Mock Transitions mTransitions;
    private StartingWindowController mController;
    private ShellInit mShellInit;
    private ShellController mShellController;
    private TestShellExecutor mMainExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(mock(Display.class)).when(mDisplayManager).getDisplay(anyInt());
        doReturn(mDisplayManager).when(mContext).getSystemService(eq(DisplayManager.class));
        doReturn(super.mContext.getResources()).when(mContext).getResources();
        mShellInit = spy(new ShellInit(mSplashScreenExecutor));
        mShellController = spy(new ShellController(mContext, mShellInit, mShellCommandHandler,
                mDisplayInsetsController, mUserManager, mSplashScreenExecutor));
        mMainExecutor = new TestShellExecutor();
        mController = new StartingWindowController(mContext, mShellInit, mShellController,
                mTaskOrganizer, mSplashScreenExecutor, mTypeAlgorithm, mIconProvider,
                mTransactionPool, mMainExecutor, mTransitions);
        mShellInit.init();
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), isA(StartingWindowController.class));
    }

    @Test
    public void instantiateController_addExternalInterface() {
        verify(mShellController, times(1)).addExternalInterface(
                eq(IStartingWindow.DESCRIPTOR), any(), any());
    }

    @Test
    public void testInvalidateExternalInterface_unregistersListener() {
        mController.setStartingWindowListener(new TriConsumer<Integer, Integer, Integer>() {
            @Override
            public void accept(Integer integer, Integer integer2, Integer integer3) {}
        });
        assertTrue(mController.hasStartingWindowListener());
        // Create initial interface
        mShellController.createExternalInterfaces(new Bundle());
        // Recreate the interface to trigger invalidation of the previous instance
        mShellController.createExternalInterfaces(new Bundle());
        assertFalse(mController.hasStartingWindowListener());
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_REMOVE_STARTING_IN_TRANSITION)
    public void testRemoveStartingInShell() {
        final int taskId = 1;
        final IBinder token = new Binder();
        final IBinder appToken = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        final StartingWindowRemovalInfo removalInfo = new StartingWindowRemovalInfo();
        removalInfo.taskId = taskId;
        final StubTransaction st = new StubTransaction();
        final StartingWindowController.RemoveStartingObserver observer =
                mController.mRemoveStartingObserver;

        observer.onAddingWindow(taskId, token, appToken);
        observer.onTransitionReady(token, info, st, st);
        notifyTransactionCommitted(st);
        assertTrue(observer.hasPendingRemoval());
        observer.requestRemoval(taskId, removalInfo);
        assertFalse(observer.hasPendingRemoval());

        st.clear();
        observer.onAddingWindow(taskId, token, appToken);
        observer.requestRemoval(taskId, removalInfo);
        observer.onTransitionReady(token, info, st, st);
        assertTrue(observer.hasPendingRemoval());
        notifyTransactionCommitted(st);
        assertFalse(observer.hasPendingRemoval());

        // Received second transition with FLAG_IS_BEHIND_STARTING_WINDOW
        // simulate transfer starting window.
        st.clear();
        final IBinder secondToken = new Binder();
        final TransitionInfo secondInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, FLAG_IS_BEHIND_STARTING_WINDOW).build();
        observer.onAddingWindow(taskId, token, appToken);
        observer.onTransitionReady(token, info, st, st);
        notifyTransactionCommitted(st);
        observer.onTransitionReady(secondToken, secondInfo, st, st);
        notifyTransactionCommitted(st);
        assertTrue(observer.hasPendingRemoval());
        observer.requestRemoval(taskId, removalInfo);
        assertFalse(observer.hasPendingRemoval());

        st.clear();
        observer.onAddingWindow(taskId, token, appToken);
        observer.onTransitionReady(token, info, st, st);
        notifyTransactionCommitted(st);
        observer.onTransitionReady(secondToken, secondInfo, st, st);
        observer.requestRemoval(taskId, removalInfo);
        assertTrue(observer.hasPendingRemoval());
        notifyTransactionCommitted(st);
        assertFalse(observer.hasPendingRemoval());
    }

    private void notifyTransactionCommitted(StubTransaction st) {
        st.apply();
        mMainExecutor.flushAll();
    }
}
