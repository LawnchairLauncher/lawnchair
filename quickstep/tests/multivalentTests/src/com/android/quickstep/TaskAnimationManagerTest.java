/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Display;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.systemui.shared.system.RecentsAnimationControllerCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TaskAnimationManagerTest {

    protected final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Mock
    private SystemUiProxy mSystemUiProxy;

    private TaskAnimationManager mTaskAnimationManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTaskAnimationManager = new TaskAnimationManager(mContext,
                RecentsAnimationDeviceState.INSTANCE.get(mContext), Display.DEFAULT_DISPLAY) {
            @Override
            SystemUiProxy getSystemUiProxy() {
                return mSystemUiProxy;
            }
        };
    }

    @Test
    public void startRecentsActivity_allowBackgroundLaunch() {
        final LauncherActivityInterface activityInterface = mock(LauncherActivityInterface.class);
        final GestureState gestureState = mock(GestureState.class);
        final RecentsAnimationCallbacks.RecentsAnimationListener listener =
                mock(RecentsAnimationCallbacks.RecentsAnimationListener.class);
        doReturn(activityInterface).when(gestureState).getContainerInterface();
        runOnMainSync(() ->
                mTaskAnimationManager.startRecentsAnimation(gestureState, new Intent(), listener));
        final ArgumentCaptor<ActivityOptions> optionsCaptor =
                ArgumentCaptor.forClass(ActivityOptions.class);
        verify(mSystemUiProxy)
                .startRecentsActivity(any(), optionsCaptor.capture(), any(), anyBoolean());
        assertEquals(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                optionsCaptor.getValue().getPendingIntentBackgroundActivityStartMode());
    }

    @Test
    public void testLauncherDestroyed_whileRecentsAnimationStartPending_finishesAnimation() {
        final GestureState gestureState = mock(GestureState.class);
        final ArgumentCaptor<RecentsAnimationCallbacks> listenerCaptor =
                ArgumentCaptor.forClass(RecentsAnimationCallbacks.class);
        final RecentsAnimationControllerCompat controllerCompat =
                mock(RecentsAnimationControllerCompat.class);
        final RemoteAnimationTarget remoteAnimationTarget = new RemoteAnimationTarget(
                /* taskId= */ 0,
                /* mode= */ RemoteAnimationTarget.MODE_CLOSING,
                /* leash= */ new SurfaceControl(),
                /* isTranslucent= */ false,
                /* clipRect= */ null,
                /* contentInsets= */ null,
                /* prefixOrderIndex= */ 0,
                /* position= */ null,
                /* localBounds= */ null,
                /* screenSpaceBounds= */ null,
                new Configuration().windowConfiguration,
                /* isNotInRecents= */ false,
                /* startLeash= */ null,
                /* startBounds= */ null,
                /* taskInfo= */ new ActivityManager.RunningTaskInfo(),
                /* allowEnterPip= */ false);

        doReturn(mock(LauncherActivityInterface.class)).when(gestureState).getContainerInterface();
        when(mSystemUiProxy
                .startRecentsActivity(any(), any(), listenerCaptor.capture(), anyBoolean()))
                .thenReturn(true);
        when(gestureState.getRunningTaskIds(anyBoolean())).thenReturn(new int[0]);

        runOnMainSync(() -> {
            mTaskAnimationManager.startRecentsAnimation(
                    gestureState,
                    new Intent(),
                    mock(RecentsAnimationCallbacks.RecentsAnimationListener.class));
            mTaskAnimationManager.onLauncherDestroyed();
            listenerCaptor.getValue().onAnimationStart(
                    controllerCompat,
                    new RemoteAnimationTarget[] { remoteAnimationTarget },
                    new RemoteAnimationTarget[] { remoteAnimationTarget },
                    new Rect(),
                    new Rect(),
                    new Bundle(),
                    new TransitionInfo(0, 0));
        });
        runOnMainSync(() -> verify(controllerCompat)
                .finish(/* toHome= */ eq(false), anyBoolean(), any()));
    }

    protected static void runOnMainSync(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }
}
