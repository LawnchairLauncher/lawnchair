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

package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LONG_PRESS_NAVBAR;
import static com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.quickstep.DeviceConfigWrapper.DEFAULT_LPNH_TIMEOUT_MS;
import static com.android.quickstep.inputconsumers.NavHandleLongPressInputConsumer.MIN_TIME_TO_LOG_ABANDON_MS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.util.AllModulesForTest;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SandboxContext;
import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.NavHandle;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.util.TestExtensions;
import com.android.systemui.shared.system.InputMonitorCompat;

import dagger.BindsInstance;
import dagger.Component;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicBoolean;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NavHandleLongPressInputConsumerTest {

    private static final float TOUCH_SLOP = 10;
    private static final float SQUARED_TOUCH_SLOP = 100;

    private final AtomicBoolean mLongPressTriggered = new AtomicBoolean();
    private final Runnable mLongPressRunnable = () -> mLongPressTriggered.set(true);
    private NavHandleLongPressInputConsumer mUnderTest;
    private SandboxContext mContext;
    private float mScreenWidth;
    private long mDownTimeMs;
    @Mock InputConsumer mDelegate;
    @Mock InputMonitorCompat mInputMonitor;
    @Mock RecentsAnimationDeviceState mDeviceState;
    @Mock NavHandle mNavHandle;
    @Mock GestureState mGestureState;
    @Mock NavHandleLongPressHandler mNavHandleLongPressHandler;
    @Mock TopTaskTracker mTopTaskTracker;
    @Mock TopTaskTracker.CachedTaskInfo mTaskInfo;
    @Mock StatsLogManager mStatsLogManager;
    @Mock StatsLogManager.StatsLogger mStatsLogger;
    @Mock StatsLogManager.StatsLatencyLogger mStatsLatencyLogger;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mTopTaskTracker.getCachedTopTask(anyBoolean(), anyInt())).thenReturn(mTaskInfo);
        when(mDeviceState.getSquaredTouchSlop()).thenReturn(SQUARED_TOUCH_SLOP);
        when(mDelegate.allowInterceptByParent()).thenReturn(true);
        mLongPressTriggered.set(false);
        when(mNavHandleLongPressHandler.getLongPressRunnable(any(), anyInt())).thenReturn(
                mLongPressRunnable);
        when(mStatsLogger.withPackageName(any())).thenReturn(mStatsLogger);
        when(mStatsLatencyLogger.withInstanceId(any())).thenReturn(mStatsLatencyLogger);
        when(mStatsLatencyLogger.withLatency(anyLong())).thenReturn(mStatsLatencyLogger);
        when(mStatsLogManager.logger()).thenReturn(mStatsLogger);
        when(mStatsLogManager.latencyLogger()).thenReturn(mStatsLatencyLogger);
        initializeObjectUnderTest();
    }

    @After
    public void tearDown() throws Exception {
        MAIN_EXECUTOR.getHandler().removeCallbacks(mLongPressRunnable);
        MAIN_EXECUTOR.submit(() -> null).get();
        mContext.onDestroy();
    }

    @Test
    public void testGetType() {
        assertThat(mUnderTest.getType() & InputConsumer.TYPE_NAV_HANDLE_LONG_PRESS).isNotEqualTo(0);
    }

    @Test
    public void testDelegateDisallowsTouchIntercept() {
        when(mDelegate.allowInterceptByParent()).thenReturn(false);
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));

        verify(mDelegate).onMotionEvent(any());
        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        verify(mNavHandleLongPressHandler, never()).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
        verifyNoMoreInteractions(mStatsLogManager);
        verifyNoMoreInteractions(mStatsLogger);
        verifyNoMoreInteractions(mStatsLatencyLogger);
    }

    @Test
    public void testDelegateDisallowsTouchInterceptAfterTouchDown() {
        // Touch down and wait the minimum abandonment time.
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        sleep(MIN_TIME_TO_LOG_ABANDON_MS);

        // Delegate should still get touches unless long press is triggered.
        verify(mDelegate).onMotionEvent(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());

        // Child delegate blocks us from intercepting further motion events.
        when(mDelegate.allowInterceptByParent()).thenReturn(false);
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_MOVE));

        // Delegate should still get motion events unless long press is triggered.
        verify(mDelegate, times(2)).onMotionEvent(any());
        // But our handler should be cancelled.
        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
        verifyNoMoreInteractions(mStatsLogger);
        // Because we handled touch down before the child blocked additional events, log abandon.
        verify(mStatsLatencyLogger).log(LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON);
    }

    @Test
    public void testLongPressTriggered() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        sleep(DEFAULT_LPNH_TIMEOUT_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_ACTIVE);
        assertTrue(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
        verify(mStatsLogger).log(LAUNCHER_LONG_PRESS_NAVBAR);
        verifyNoMoreInteractions(mStatsLatencyLogger);

        // Ensure abandon latency is still not logged after long press.
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_UP));
        verifyNoMoreInteractions(mStatsLatencyLogger);
    }

    @Test
    public void testLongPressTriggeredWithSlightVerticalMovement() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        mUnderTest.onMotionEvent(generateCenteredMotionEventWithYOffset(ACTION_MOVE, 1));
        sleep(DEFAULT_LPNH_TIMEOUT_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_ACTIVE);
        assertTrue(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
        verify(mStatsLogger).log(LAUNCHER_LONG_PRESS_NAVBAR);
        verifyNoMoreInteractions(mStatsLatencyLogger);
    }

    @Test
    public void testLongPressTriggeredWithSlightHorizontalMovement() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        mUnderTest.onMotionEvent(generateMotionEvent(ACTION_MOVE, mScreenWidth / 2f + 1, 0));
        sleep(DEFAULT_LPNH_TIMEOUT_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_ACTIVE);
        assertTrue(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
        verify(mStatsLogger).log(LAUNCHER_LONG_PRESS_NAVBAR);
        verifyNoMoreInteractions(mStatsLatencyLogger);
    }

    @Test
    public void testLongPressTriggeredWithExtendedTwoStageDuration() {
        try (AutoCloseable flag = overrideTwoStageFlag(true)) {
            // Reinitialize to pick up updated flag state.
            initializeObjectUnderTest();

            mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
            mUnderTest.onMotionEvent(generateMotionEvent(ACTION_MOVE,
                    mScreenWidth / 2f - (TOUCH_SLOP - 1), 0));
            // We have entered the second stage, so the normal timeout shouldn't trigger.
            sleep(DEFAULT_LPNH_TIMEOUT_MS);

            assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
            assertFalse(mLongPressTriggered.get());
            verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
            verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());

            // After an extended time, the long press should trigger.
            float extendedDurationMultiplier =
                    (DeviceConfigWrapper.get().getTwoStageDurationPercentage() / 100f);
            sleep((long) (DEFAULT_LPNH_TIMEOUT_MS
                    * (extendedDurationMultiplier - 1)));  // -1 because we already waited 1x

            assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_ACTIVE);
            assertTrue(mLongPressTriggered.get());
            verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
            verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
            verify(mStatsLogger).log(LAUNCHER_LONG_PRESS_NAVBAR);
            verifyNoMoreInteractions(mStatsLatencyLogger);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLongPressTriggeredWithNormalDurationInFirstStage() {
        try (AutoCloseable flag = overrideTwoStageFlag(true)) {
            // Reinitialize to pick up updated flag state.
            initializeObjectUnderTest();

            mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
            // We have not entered the second stage, so the normal timeout should trigger.
            sleep(DEFAULT_LPNH_TIMEOUT_MS);

            assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_ACTIVE);
            assertTrue(mLongPressTriggered.get());
            verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
            verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
            verify(mStatsLogger).log(LAUNCHER_LONG_PRESS_NAVBAR);
            verifyNoMoreInteractions(mStatsLatencyLogger);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLongPressAbortedByTouchUp() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        sleep(MIN_TIME_TO_LOG_ABANDON_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());

        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_UP));
        // Wait past the long press timeout, to be extra sure it wouldn't have triggered.
        sleep(DEFAULT_LPNH_TIMEOUT_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
        verifyNoMoreInteractions(mStatsLogger);
        verify(mStatsLatencyLogger).log(LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON);
    }

    @Test
    public void testLongPressAbortedByTouchCancel() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        sleep(MIN_TIME_TO_LOG_ABANDON_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());

        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_CANCEL));
        // Wait past the long press timeout, to be extra sure it wouldn't have triggered.
        sleep(DEFAULT_LPNH_TIMEOUT_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
        verifyNoMoreInteractions(mStatsLogger);
        verify(mStatsLatencyLogger).log(LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON);
    }

    @Test
    public void testTouchCancelWithoutTouchDown() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_CANCEL));

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, never()).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
        verifyNoMoreInteractions(mStatsLogger);
        verifyNoMoreInteractions(mStatsLatencyLogger);
    }

    @Test
    public void testLongPressAbortedByTouchSlopPassedVertically() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        sleep(MIN_TIME_TO_LOG_ABANDON_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());

        mUnderTest.onMotionEvent(generateCenteredMotionEventWithYOffset(ACTION_MOVE,
                -(TOUCH_SLOP + 1)));
        // Wait past the long press timeout, to be extra sure it wouldn't have triggered.
        sleep(DEFAULT_LPNH_TIMEOUT_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
        verifyNoMoreInteractions(mStatsLogger);
        verify(mStatsLatencyLogger).log(LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON);
    }

    @Test
    public void testLongPressAbortedByTouchSlopPassedHorizontally() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        sleep(MIN_TIME_TO_LOG_ABANDON_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());

        mUnderTest.onMotionEvent(generateMotionEvent(ACTION_MOVE,
                mScreenWidth / 2f - (TOUCH_SLOP + 1), 0));
        // Wait past the long press timeout, to be extra sure it wouldn't have triggered.
        sleep(DEFAULT_LPNH_TIMEOUT_MS);

        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
        verifyNoMoreInteractions(mStatsLogger);
        verify(mStatsLatencyLogger).log(LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON);
    }

    @Test
    public void testLongPressAbortedByTouchSlopPassedVertically_twoStageEnabled() {
        try (AutoCloseable flag = overrideTwoStageFlag(true)) {
            // Reinitialize to pick up updated flag state.
            initializeObjectUnderTest();

            mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
            // Enter the second stage.
            mUnderTest.onMotionEvent(generateCenteredMotionEventWithYOffset(ACTION_MOVE,
                    -(TOUCH_SLOP - 1)));
            // Normal duration shouldn't trigger.
            sleep(DEFAULT_LPNH_TIMEOUT_MS);

            assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
            assertFalse(mLongPressTriggered.get());

            // Move out of the second stage.
            mUnderTest.onMotionEvent(generateCenteredMotionEventWithYOffset(ACTION_MOVE,
                    -(TOUCH_SLOP + 1)));
            // Wait past the extended long press timeout, to be sure it wouldn't have triggered.
            float extendedDurationMultiplier =
                    (DeviceConfigWrapper.get().getTwoStageDurationPercentage() / 100f);
            sleep((long) (DEFAULT_LPNH_TIMEOUT_MS
                    * (extendedDurationMultiplier - 1)));  // -1 because we already waited 1x

            assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
            assertFalse(mLongPressTriggered.get());
            verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
            // Touch cancelled.
            verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
            verifyNoMoreInteractions(mStatsLogger);
            verify(mStatsLatencyLogger).log(LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testLongPressAbortedByTouchSlopPassedHorizontally_twoStageEnabled() {
        try (AutoCloseable flag = overrideTwoStageFlag(true)) {
            // Reinitialize to pick up updated flag state.
            initializeObjectUnderTest();

            mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
            // Enter the second stage.
            mUnderTest.onMotionEvent(generateMotionEvent(ACTION_MOVE,
                    mScreenWidth / 2f - (TOUCH_SLOP - 1), 0));
            // Normal duration shouldn't trigger.
            sleep(DEFAULT_LPNH_TIMEOUT_MS);

            assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
            assertFalse(mLongPressTriggered.get());

            // Move out of the second stage.
            mUnderTest.onMotionEvent(generateMotionEvent(ACTION_MOVE,
                    mScreenWidth / 2f - (TOUCH_SLOP + 1), 0));
            // Wait past the extended long press timeout, to be sure it wouldn't have triggered.
            float extendedDurationMultiplier =
                    (DeviceConfigWrapper.get().getTwoStageDurationPercentage() / 100f);
            sleep((long) (DEFAULT_LPNH_TIMEOUT_MS
                    * (extendedDurationMultiplier - 1)));  // -1 because we already waited 1x

            assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
            assertFalse(mLongPressTriggered.get());
            verify(mNavHandleLongPressHandler, times(1)).onTouchStarted(any());
            // Touch cancelled.
            verify(mNavHandleLongPressHandler, times(1)).onTouchFinished(any(), any());
            verifyNoMoreInteractions(mStatsLogger);
            verify(mStatsLatencyLogger).log(LAUNCHER_LATENCY_CONTEXTUAL_SEARCH_LPNH_ABANDON);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testTouchOutsideNavHandleIgnored() {
        // Touch the far left side of the screen. (y=0 is top of navbar region, picked arbitrarily)
        mUnderTest.onMotionEvent(generateMotionEvent(ACTION_DOWN, 0, 0));
        sleep(DEFAULT_LPNH_TIMEOUT_MS);

        // Should be ignored because the x position was not centered in the navbar region.
        assertThat(mUnderTest.mState).isEqualTo(DelegateInputConsumer.STATE_INACTIVE);
        assertFalse(mLongPressTriggered.get());
        verify(mNavHandleLongPressHandler, never()).onTouchStarted(any());
        verify(mNavHandleLongPressHandler, never()).onTouchFinished(any(), any());
        verifyNoMoreInteractions(mStatsLogManager);
        verifyNoMoreInteractions(mStatsLogger);
        verifyNoMoreInteractions(mStatsLatencyLogger);
    }

    @Test
    public void testHoverPassedToDelegate() {
        // Regardless of whether the delegate wants us to intercept, we tell it about hover events.
        when(mDelegate.allowInterceptByParent()).thenReturn(false);
        mUnderTest.onHoverEvent(generateCenteredMotionEvent(ACTION_HOVER_ENTER));

        verify(mDelegate).onHoverEvent(any());

        when(mDelegate.allowInterceptByParent()).thenReturn(true);
        mUnderTest.onHoverEvent(generateCenteredMotionEvent(ACTION_HOVER_ENTER));

        verify(mDelegate, times(2)).onHoverEvent(any());

        verifyNoMoreInteractions(mStatsLogManager);
        verifyNoMoreInteractions(mStatsLogger);
        verifyNoMoreInteractions(mStatsLatencyLogger);
    }

    @Test
    public void testNoLogsForShortTouch() {
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_DOWN));
        sleep(10);
        mUnderTest.onMotionEvent(generateCenteredMotionEvent(ACTION_UP));
        verifyNoMoreInteractions(mStatsLogManager);
        verifyNoMoreInteractions(mStatsLogger);
        verifyNoMoreInteractions(mStatsLatencyLogger);
    }

    private void initializeObjectUnderTest() {
        if (mContext != null) {
            mContext.onDestroy();
        }
        mContext = new SandboxContext(getApplicationContext());
        mContext.initDaggerComponent(
                DaggerNavHandleLongPressInputConsumerTest_TopTaskTrackerComponent
                        .builder()
                        .bindTopTaskTracker(mTopTaskTracker));
        mScreenWidth = DisplayController.INSTANCE.get(mContext).getInfo().currentSize.x;
        mUnderTest = new NavHandleLongPressInputConsumer(mContext, mDelegate, mInputMonitor,
                mDeviceState, mNavHandle, mGestureState);
        mUnderTest.setNavHandleLongPressHandler(mNavHandleLongPressHandler);
        mUnderTest.setStatsLogManager(mStatsLogManager);
        mDownTimeMs = 0;
    }

    private static void sleep(long sleepMs) {
        SystemClock.sleep(sleepMs);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** Generate a motion event centered horizontally in the screen. */
    private MotionEvent generateCenteredMotionEvent(int motionAction) {
        return generateCenteredMotionEventWithYOffset(motionAction, 0);
    }

    /** Generate a motion event centered horizontally in the screen, with y offset. */
    private MotionEvent generateCenteredMotionEventWithYOffset(int motionAction, float y) {
        return generateMotionEvent(motionAction, mScreenWidth / 2f, y);
    }

    private MotionEvent generateMotionEvent(int motionAction, float x, float y) {
        if (motionAction == ACTION_DOWN) {
            mDownTimeMs = SystemClock.uptimeMillis();
        }
        long eventTime = SystemClock.uptimeMillis();
        return MotionEvent.obtain(mDownTimeMs, eventTime, motionAction, x, y, 0);
    }

    private static AutoCloseable overrideTwoStageFlag(boolean value) {
        return TestExtensions.overrideNavConfigFlag(
                "ENABLE_LPNH_TWO_STAGES",
                value,
                () -> DeviceConfigWrapper.get().getEnableLpnhTwoStages());
    }

    @LauncherAppSingleton
    @Component(modules = AllModulesForTest.class)
    public interface TopTaskTrackerComponent extends LauncherAppComponent {
        @Component.Builder
        interface Builder extends LauncherAppComponent.Builder {
            @BindsInstance Builder bindTopTaskTracker(TopTaskTracker topTaskTracker);

            @Override
            TopTaskTrackerComponent build();
        }
    }
}
