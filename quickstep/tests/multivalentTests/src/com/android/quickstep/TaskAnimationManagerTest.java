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

import static com.android.quickstep.TaskAnimationManager.RECENTS_ANIMATION_START_TIMEOUT_MS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.window.flags2.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TaskAnimationManagerTest {
    private static final int EXTERNAL_DISPLAY_ID = 1;
    protected final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Mock
    private SystemUiProxy mSystemUiProxy;

    private TaskAnimationManager mTaskAnimationManager;
    private TaskAnimationManager mTaskAnimationManagerWithExternalDisplay;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTaskAnimationManager = new TaskAnimationManager(mContext, Display.DEFAULT_DISPLAY) {
            @Override
            SystemUiProxy getSystemUiProxy() {
                return mSystemUiProxy;
            }
        };
        mTaskAnimationManagerWithExternalDisplay =
            new TaskAnimationManager(mContext, EXTERNAL_DISPLAY_ID) {
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
                .startRecentsActivity(any(), optionsCaptor.capture(), any(), anyBoolean(),
                        any(), anyInt());
        assertEquals(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                optionsCaptor.getValue().getPendingIntentBackgroundActivityStartMode());
    }

    @Test
    public void testLauncherDestroyed_whileRecentsAnimationStartPending_finishesAnimation() {
        final GestureState gestureState = buildMockGestureState();
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

        when(mSystemUiProxy
                .startRecentsActivity(any(), any(), listenerCaptor.capture(), anyBoolean(), any(),
                        anyInt()))
                .thenReturn(true);

        runOnMainSync(() -> {
            mTaskAnimationManager.startRecentsAnimation(
                    gestureState,
                    new Intent(),
                    mock(RecentsAnimationCallbacks.RecentsAnimationListener.class));

            // Simulate multiple launcher destroyed events before the recents animation start
            mTaskAnimationManager.onLauncherDestroyed();
            mTaskAnimationManager.onLauncherDestroyed();
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

        // Verify checks that finish was only called once
        runOnMainSync(() -> verify(controllerCompat)
                .finish(/* toHome= */ eq(false), anyBoolean(), any()));
    }

    @Test
    public void testRecentsAnimationStartTimeout_cleansUpRecentsAnimation() {
        final GestureState gestureState = buildMockGestureState();
        when(mSystemUiProxy
                .startRecentsActivity(any(), any(), any(), anyBoolean(), any(), anyInt()))
                .thenReturn(true);

        runOnMainSync(() -> {
            assertNull("Recents animation was started prematurely:",
                    mTaskAnimationManager.getCurrentCallbacks());

            mTaskAnimationManager.startRecentsAnimation(
                    gestureState,
                    new Intent(),
                    mock(RecentsAnimationCallbacks.RecentsAnimationListener.class));

            assertNotNull("TaskAnimationManager was cleaned up prematurely:",
                    mTaskAnimationManager.getCurrentCallbacks());
        });

        SystemClock.sleep(RECENTS_ANIMATION_START_TIMEOUT_MS);

        runOnMainSync(() -> assertNull("TaskAnimationManager was not cleaned up after the timeout:",
                mTaskAnimationManager.getCurrentCallbacks()));
    }

    protected static void runOnMainSync(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    private GestureState buildMockGestureState() {
        final GestureState gestureState = mock(GestureState.class);

        doReturn(mock(LauncherActivityInterface.class)).when(gestureState).getContainerInterface();
        when(gestureState.getRunningTaskIds(anyBoolean())).thenReturn(new int[0]);

        return gestureState;
    }

    /**
     * Invokes maybeStartHomeAction on the given TaskAnimationManager and verifies whether the
     * provided Runnable was invoked, based on the expectedResult.
     *
     * @param taskAnimationManager The TaskAnimationManager instance to test.
     * @param expectedResult True if the Runnable is expected to be invoked, false otherwise.
     */
    private void verifyCanStartHomeAction(TaskAnimationManager taskAnimationManager,
                Boolean expectedResult) {
        Runnable mockRunnable = mock(Runnable.class);
        taskAnimationManager.maybeStartHomeAction(mockRunnable);
        if (expectedResult) {
            verify(mockRunnable).run();
        } else {
            verify(mockRunnable, never()).run();
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION)
    public void maybeStartHomeAction_withRejectHomeTransitionEnabled() {
        verifyCanStartHomeAction(mTaskAnimationManager, true);
        verifyCanStartHomeAction(mTaskAnimationManagerWithExternalDisplay, false);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION)
    public void maybeStartHomeAction_withRejectHomeTransitionDisabled() {
        verifyCanStartHomeAction(mTaskAnimationManager, true);
        verifyCanStartHomeAction(mTaskAnimationManagerWithExternalDisplay, true);
    }
}
