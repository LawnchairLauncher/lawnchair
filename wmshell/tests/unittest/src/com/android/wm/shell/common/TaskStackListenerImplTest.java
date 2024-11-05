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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.IActivityTaskManager;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.window.TaskSnapshot;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link com.android.wm.shell.common.TaskStackListenerImpl}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class TaskStackListenerImplTest extends ShellTestCase {

    @Mock
    private IActivityTaskManager mActivityTaskManager;

    @Mock
    private TaskStackListenerCallback mCallback;

    @Mock
    private TaskStackListenerCallback mOtherCallback;

    private TaskStackListenerImpl mImpl;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mImpl = new TaskStackListenerImpl(mActivityTaskManager);
        mImpl.setHandler(new ProxyToListenerImplHandler(mImpl));
        mImpl.addListener(mCallback);
        mImpl.addListener(mOtherCallback);
    }

    @Test
    public void testAddRemoveMultipleListeners_ExpectRegisterUnregisterOnce()
            throws RemoteException {
        TaskStackListenerImpl impl = new TaskStackListenerImpl(mActivityTaskManager);
        impl.setHandler(new ProxyToListenerImplHandler(impl));
        reset(mActivityTaskManager);
        impl.addListener(mCallback);
        impl.addListener(mOtherCallback);
        verify(mActivityTaskManager, times(1)).registerTaskStackListener(any());

        impl.removeListener(mOtherCallback);
        impl.removeListener(mCallback);
        verify(mActivityTaskManager, times(1)).unregisterTaskStackListener(any());
    }

    @Test
    public void testOnRecentTaskListUpdated() {
        mImpl.onRecentTaskListUpdated();
        verify(mCallback).onRecentTaskListUpdated();
        verify(mOtherCallback).onRecentTaskListUpdated();
    }

    @Test
    public void testOnRecentTaskListFrozenChanged() {
        mImpl.onRecentTaskListFrozenChanged(true);
        verify(mCallback).onRecentTaskListFrozenChanged(eq(true));
        verify(mOtherCallback).onRecentTaskListFrozenChanged(eq(true));
    }

    @Test
    public void testOnTaskStackChanged() {
        mImpl.onTaskStackChanged();
        verify(mCallback).onTaskStackChangedBackground();
        verify(mCallback).onTaskStackChanged();
        verify(mOtherCallback).onTaskStackChangedBackground();
        verify(mOtherCallback).onTaskStackChanged();
    }

    @Test
    public void testOnTaskProfileLocked() {
        ActivityManager.RunningTaskInfo info = mock(ActivityManager.RunningTaskInfo.class);
        mImpl.onTaskProfileLocked(info, 0);
        verify(mCallback).onTaskProfileLocked(eq(info), eq(0));
        verify(mOtherCallback).onTaskProfileLocked(eq(info), eq(0));
    }

    @Test
    public void testOnTaskDisplayChanged() {
        mImpl.onTaskDisplayChanged(1, 2);
        verify(mCallback).onTaskDisplayChanged(eq(1), eq(2));
        verify(mOtherCallback).onTaskDisplayChanged(eq(1), eq(2));
    }

    @Test
    public void testOnTaskCreated() {
        mImpl.onTaskCreated(1, new ComponentName("a", "b"));
        verify(mCallback).onTaskCreated(eq(1), eq(new ComponentName("a", "b")));
        verify(mOtherCallback).onTaskCreated(eq(1), eq(new ComponentName("a", "b")));
    }

    @Test
    public void testOnTaskRemoved() {
        mImpl.onTaskRemoved(123);
        verify(mCallback).onTaskRemoved(eq(123));
        verify(mOtherCallback).onTaskRemoved(eq(123));
    }

    @Test
    public void testOnTaskMovedToFront() {
        ActivityManager.RunningTaskInfo info = mock(ActivityManager.RunningTaskInfo.class);
        mImpl.onTaskMovedToFront(info);
        verify(mCallback).onTaskMovedToFront(eq(info));
        verify(mOtherCallback).onTaskMovedToFront(eq(info));
    }

    @Test
    public void testOnTaskDescriptionChanged() {
        ActivityManager.RunningTaskInfo info = mock(ActivityManager.RunningTaskInfo.class);
        mImpl.onTaskDescriptionChanged(info);
        verify(mCallback).onTaskDescriptionChanged(eq(info));
        verify(mOtherCallback).onTaskDescriptionChanged(eq(info));
    }

    @Test
    public void testOnTaskSnapshotChanged() {
        TaskSnapshot snapshot = mock(TaskSnapshot.class);
        mImpl.onTaskSnapshotChanged(123, snapshot);
        verify(mCallback).onTaskSnapshotChanged(eq(123), eq(snapshot));
        verify(mOtherCallback).onTaskSnapshotChanged(eq(123), eq(snapshot));
    }

    @Test
    public void testOnBackPressedOnTaskRoot() {
        ActivityManager.RunningTaskInfo info = mock(ActivityManager.RunningTaskInfo.class);
        mImpl.onBackPressedOnTaskRoot(info);
        verify(mCallback).onBackPressedOnTaskRoot(eq(info));
        verify(mOtherCallback).onBackPressedOnTaskRoot(eq(info));
    }

    @Test
    public void testOnActivityRestartAttempt() {
        ActivityManager.RunningTaskInfo info = mock(ActivityManager.RunningTaskInfo.class);
        mImpl.onActivityRestartAttempt(info, true, true, true);
        verify(mCallback).onActivityRestartAttempt(eq(info), eq(true), eq(true), eq(true));
        verify(mOtherCallback).onActivityRestartAttempt(eq(info), eq(true), eq(true), eq(true));
    }

    @Test
    public void testOnActivityPinned() {
        mImpl.onActivityPinned("abc", 1, 2, 3);
        verify(mCallback).onActivityPinned(eq("abc"), eq(1), eq(2), eq(3));
        verify(mOtherCallback).onActivityPinned(eq("abc"), eq(1), eq(2), eq(3));
    }

    @Test
    public void testOnActivityUnpinned() {
        mImpl.onActivityUnpinned();
        verify(mCallback).onActivityUnpinned();
        verify(mOtherCallback).onActivityUnpinned();
    }

    @Test
    public void testOnActivityForcedResizable() {
        mImpl.onActivityForcedResizable("abc", 1, 2);
        verify(mCallback).onActivityForcedResizable(eq("abc"), eq(1), eq(2));
        verify(mOtherCallback).onActivityForcedResizable(eq("abc"), eq(1), eq(2));
    }

    @Test
    public void testOnActivityDismissingDockedStack() {
        mImpl.onActivityDismissingDockedTask();
        verify(mCallback).onActivityDismissingDockedStack();
        verify(mOtherCallback).onActivityDismissingDockedStack();
    }

    @Test
    public void testOnActivityLaunchOnSecondaryDisplayFailed() {
        ActivityManager.RunningTaskInfo info = mock(ActivityManager.RunningTaskInfo.class);
        mImpl.onActivityLaunchOnSecondaryDisplayFailed(info, 1);
        verify(mCallback).onActivityLaunchOnSecondaryDisplayFailed(eq(info));
        verify(mOtherCallback).onActivityLaunchOnSecondaryDisplayFailed(eq(info));
    }

    @Test
    public void testOnActivityLaunchOnSecondaryDisplayRerouted() {
        ActivityManager.RunningTaskInfo info = mock(ActivityManager.RunningTaskInfo.class);
        mImpl.onActivityLaunchOnSecondaryDisplayRerouted(info, 1);
        verify(mCallback).onActivityLaunchOnSecondaryDisplayRerouted(eq(info));
        verify(mOtherCallback).onActivityLaunchOnSecondaryDisplayRerouted(eq(info));
    }

    @Test
    public void testOnActivityRequestedOrientationChanged() {
        mImpl.onActivityRequestedOrientationChanged(1, 2);
        verify(mCallback).onActivityRequestedOrientationChanged(eq(1), eq(2));
        verify(mOtherCallback).onActivityRequestedOrientationChanged(eq(1), eq(2));
    }

    @Test
    public void testOnActivityRotation() {
        mImpl.onActivityRotation(123);
        verify(mCallback).onActivityRotation(eq(123));
        verify(mOtherCallback).onActivityRotation(eq(123));
    }

    /**
     * Handler that synchronously calls TaskStackListenerImpl#handleMessage() when it receives a
     * message.
     */
    private class ProxyToListenerImplHandler extends Handler {
        public ProxyToListenerImplHandler(Callback callback) {
            super(callback);
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            return mImpl.handleMessage(msg);
        }
    }
}