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

import static android.view.Display.DEFAULT_DISPLAY;

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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.OngoingStubbingKt.whenever;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.app.displaylib.fakes.FakePerDisplayRepository;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Executors;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ExecutionException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TaskAnimationManagerTest {
    private static final int EXTERNAL_DISPLAY_ID = 1;
    protected final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SystemUiProxy mSystemUiProxy;

    private TaskAnimationManager mTaskAnimationManager;
    @Mock
    private TaskAnimationManager mTaskAnimationManagerForExternalDisplay;
    private FakePerDisplayRepository<TaskAnimationManager> mPerDisplayRepository;

    @Before
    public void setUp() {
        DisplayController displayController = DisplayController.INSTANCE.get(mContext);
        mPerDisplayRepository = new FakePerDisplayRepository<>();
        mTaskAnimationManager = spy(new TaskAnimationManager(mContext, DEFAULT_DISPLAY,
                displayController, mPerDisplayRepository) {
            @Override
            SystemUiProxy getSystemUiProxy() {
                return mSystemUiProxy;
            }
        });
        mPerDisplayRepository.add(DEFAULT_DISPLAY, mTaskAnimationManager);
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
                .startRecentsTransition(any(), optionsCaptor.capture(), any(), anyBoolean(),
                        any(), anyInt());
        assertEquals(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
                optionsCaptor.getValue().getPendingIntentBackgroundActivityStartMode());
    }

    @Test
    public void startRecentsActivity_finishRecentsBeforeStartingAnother() {
        final LauncherActivityInterface activityInterface = mock(LauncherActivityInterface.class);
        final GestureState gestureState = mock(GestureState.class);
        final RecentsAnimationCallbacks.RecentsAnimationListener listener =
                mock(RecentsAnimationCallbacks.RecentsAnimationListener.class);
        doReturn(activityInterface).when(gestureState).getContainerInterface();
        mPerDisplayRepository.add(EXTERNAL_DISPLAY_ID, mTaskAnimationManagerForExternalDisplay);
        when(mTaskAnimationManager.isRecentsAnimationRunning()).thenReturn(true);
        runOnMainSync(() ->
                mTaskAnimationManager.startRecentsAnimation(gestureState, new Intent(), listener));
        ArgumentCaptor<Runnable> finishCallback = ArgumentCaptor.forClass(Runnable.class);
        verify(mTaskAnimationManager, times(1))
                .finishRunningRecentsAnimation(anyBoolean(), anyBoolean(), finishCallback.capture(),
                        any());
        runOnMainSync(() -> finishCallback.getValue().run());
        verify(mSystemUiProxy).startRecentsTransition(any(), any(), any(), anyBoolean(),
                any(), anyInt());
        verify(mTaskAnimationManagerForExternalDisplay, never())
                .finishRunningRecentsAnimation(anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    public void startRecentsActivity_finishAllRecentsBeforeStartingAnother() {
        final LauncherActivityInterface activityInterface = mock(LauncherActivityInterface.class);
        final GestureState gestureState = mock(GestureState.class);
        final RecentsAnimationCallbacks.RecentsAnimationListener listener =
                mock(RecentsAnimationCallbacks.RecentsAnimationListener.class);
        doReturn(activityInterface).when(gestureState).getContainerInterface();
        RecentsView<?, ?> recentsView = mock(RecentsView.class);
        QuickstepLauncher quickstepLauncher = mock(QuickstepLauncher.class);
        doReturn(quickstepLauncher).when(activityInterface).getCreatedContainer();
        doReturn(recentsView).when(quickstepLauncher).getOverviewPanel();
        whenever(recentsView.getRecentsAnimationController()).thenReturn(
                mock(RecentsAnimationController.class));
        whenever(activityInterface.isInLiveTileMode()).thenReturn(true);

        // Start once on default display to make sure mLastGestureState is set.
        runOnMainSync(() ->
                mTaskAnimationManager.startRecentsAnimation(gestureState, new Intent(), listener));
        verify(mSystemUiProxy).startRecentsTransition(any(), any(), any(), anyBoolean(),
                any(), eq(DEFAULT_DISPLAY));

        mPerDisplayRepository.add(EXTERNAL_DISPLAY_ID, mTaskAnimationManagerForExternalDisplay);
        whenever(mTaskAnimationManagerForExternalDisplay.isRecentsAnimationRunning()).thenReturn(
                true);
        whenever(mTaskAnimationManager.isRecentsAnimationRunning()).thenReturn(
                true);

        runOnMainSync(() ->
                mTaskAnimationManager.startRecentsAnimation(gestureState, new Intent(), listener));
        ArgumentCaptor<Runnable> screenshotFinishCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(recentsView, times(1)).switchToScreenshot(any(), screenshotFinishCaptor.capture());
        screenshotFinishCaptor.getValue().run();
        ArgumentCaptor<Runnable> finishRecentsFinishCaptor = ArgumentCaptor.forClass(
                Runnable.class);
        verify(recentsView, times(1)).finishRecentsAnimation(anyBoolean(), anyBoolean(),
                finishRecentsFinishCaptor.capture());
        finishRecentsFinishCaptor.getValue().run();
        ArgumentCaptor<Runnable> finishCallback = ArgumentCaptor.forClass(Runnable.class);
        verify(mTaskAnimationManagerForExternalDisplay, times(1))
                .finishRunningRecentsAnimation(anyBoolean(), anyBoolean(), finishCallback.capture(),
                        any());

        runOnMainSync(() -> finishCallback.getValue().run());
        verify(mSystemUiProxy, times(2)).startRecentsTransition(any(), any(), any(), anyBoolean(),
                any(), eq(DEFAULT_DISPLAY));
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
                .startRecentsTransition(any(), any(), listenerCaptor.capture(), anyBoolean(), any(),
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
                .startRecentsTransition(any(), any(), any(), anyBoolean(), any(), anyInt()))
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
        try {
            Executors.MAIN_EXECUTOR.submit(() -> null).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private GestureState buildMockGestureState() {
        final GestureState gestureState = mock(GestureState.class);

        doReturn(mock(LauncherActivityInterface.class)).when(gestureState).getContainerInterface();
        when(gestureState.getRunningTaskIds(anyBoolean())).thenReturn(new int[0]);

        return gestureState;
    }
}
