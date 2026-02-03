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

package com.android.quickstep.util;

import static android.app.contextualsearch.ContextualSearchManager.FEATURE_CONTEXTUAL_SEARCH;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_ASSISTANT_FAILED_SERVICE_ERROR;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_KEYGUARD;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_NOTIFICATION_SHADE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_ATTEMPTED_SPLITSCREEN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_FAILED_NOT_AVAILABLE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_FAILED_SETTING_DISABLED;
import static com.android.quickstep.util.ContextualSearchInvoker.KEYGUARD_SHOWING_SYSUI_FLAGS;
import static com.android.quickstep.util.ContextualSearchInvoker.SHADE_EXPANDED_SYSUI_FLAGS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.contextualsearch.ContextualSearchManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.logging.StatsLogManager;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.DeviceConfigWrapper;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Robolectric unit tests for {@link ContextualSearchInvoker}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextualSearchInvokerTest {

    private static final int CONTEXTUAL_SEARCH_ENTRY_POINT = 123;

    private @Mock PackageManager mMockPackageManager;
    private @Mock ContextualSearchStateManager mMockStateManager;
    private @Mock TopTaskTracker mMockTopTaskTracker;
    private @Mock SystemUiProxy mMockSystemUiProxy;
    private @Mock StatsLogManager mMockStatsLogManager;
    private @Mock StatsLogManager.StatsLogger mMockStatsLogger;
    private @Mock ContextualSearchHapticManager mMockContextualSearchHapticManager;
    private @Mock ContextualSearchManager mMockContextualSearchManager;
    private @Mock BaseContainerInterface mMockContainerInterface;
    private @Mock RecentsViewContainer mMockRecentsViewContainer;
    private @Mock RecentsView mMockRecentsView;
    private ContextualSearchInvoker mContextualSearchInvoker;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockPackageManager.hasSystemFeature(FEATURE_CONTEXTUAL_SEARCH)).thenReturn(true);
        Context context = spy(getApplicationContext());
        doReturn(mMockPackageManager).when(context).getPackageManager();
        when(mMockSystemUiProxy.getLastSystemUiStateFlags()).thenReturn(0L);
        when(mMockTopTaskTracker.getRunningSplitTaskIds()).thenReturn(new int[]{});
        when(mMockStateManager.isContextualSearchIntentAvailable()).thenReturn(true);
        when(mMockStateManager.isContextualSearchSettingEnabled()).thenReturn(true);
        when(mMockStatsLogManager.logger()).thenReturn(mMockStatsLogger);
        when(mMockContainerInterface.getCreatedContainer()).thenReturn(mMockRecentsViewContainer);
        when(mMockRecentsViewContainer.getOverviewPanel()).thenReturn(mMockRecentsView);

        mContextualSearchInvoker = spy(new ContextualSearchInvoker(context, mMockStateManager,
                mMockTopTaskTracker, mMockSystemUiProxy, mMockStatsLogManager,
                mMockContextualSearchHapticManager, mMockContextualSearchManager
        ));
        doReturn(mMockContainerInterface).when(mContextualSearchInvoker)
                .getRecentsContainerInterface();
    }

    @Test
    public void runContextualSearchInvocationChecksAndLogFailures_contextualSearchFeatureIsNotAvailable() {
        when(mMockPackageManager.hasSystemFeature(FEATURE_CONTEXTUAL_SEARCH)).thenReturn(false);

        assertFalse("Expected invocation to fail when feature is unavailable",
                mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures());

        verify(mMockStatsLogger).log(LAUNCHER_LAUNCH_ASSISTANT_FAILED_SERVICE_ERROR);
    }

    @Test
    public void runContextualSearchInvocationChecksAndLogFailures_contextualSearchIntentIsAvailable() {
        assertTrue("Expected invocation checks to succeed",
                mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures());

        verifyNoMoreInteractions(mMockStatsLogManager);
    }

    @Test
    public void runContextualSearchInvocationChecksAndLogFailures_contextualSearchIntentIsNotAvailable() {
        when(mMockStateManager.isContextualSearchIntentAvailable()).thenReturn(false);

        assertFalse("Expected invocation to fail when feature is unavailable",
                mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures());

        verify(mMockStatsLogger).log(LAUNCHER_LAUNCH_OMNI_FAILED_NOT_AVAILABLE);
    }

    @Test
    public void runContextualSearchInvocationChecksAndLogFailures_settingDisabled() {
        when(mMockStateManager.isContextualSearchSettingEnabled()).thenReturn(false);

        assertFalse("Expected invocation checks to fail when setting is disabled",
                mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures());

        verify(mMockStatsLogger).log(LAUNCHER_LAUNCH_OMNI_FAILED_SETTING_DISABLED);
    }

    @Test
    public void runContextualSearchInvocationChecksAndLogFailures_notificationShadeIsShowing() {
        when(mMockSystemUiProxy.getLastSystemUiStateFlags()).thenReturn(SHADE_EXPANDED_SYSUI_FLAGS);

        assertFalse("Expected invocation checks to fail when notification shade is showing",
                mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures());

        verify(mMockStatsLogger).log(LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_NOTIFICATION_SHADE);
    }

    @Test
    public void runContextualSearchInvocationChecksAndLogFailures_keyguardIsShowing() {
        when(mMockSystemUiProxy.getLastSystemUiStateFlags()).thenReturn(
                KEYGUARD_SHOWING_SYSUI_FLAGS);

        assertFalse("Expected invocation checks to fail when keyguard is showing",
                mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures());

        verify(mMockStatsLogger).log(LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_KEYGUARD);
    }

    @Test
    public void runContextualSearchInvocationChecksAndLogFailures_isInSplitScreen_disallowed() {
        when(mMockStateManager.isInvocationAllowedInSplitscreen()).thenReturn(false);
        when(mMockTopTaskTracker.getRunningSplitTaskIds()).thenReturn(new int[]{1, 2, 3});

        assertFalse("Expected invocation checks to fail over split screen",
                mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures());

        // Attempt is logged regardless.
        verify(mMockStatsLogger).log(LAUNCHER_LAUNCH_OMNI_ATTEMPTED_SPLITSCREEN);
    }

    @Test
    public void runContextualSearchInvocationChecksAndLogFailures_isInSplitScreen_allowed() {
        when(mMockStateManager.isInvocationAllowedInSplitscreen()).thenReturn(true);
        when(mMockTopTaskTracker.getRunningSplitTaskIds()).thenReturn(new int[]{1, 2, 3});

        assertTrue("Expected invocation checks to succeed over split screen",
                mContextualSearchInvoker.runContextualSearchInvocationChecksAndLogFailures());

        // Attempt is logged regardless.
        verify(mMockStatsLogger).log(LAUNCHER_LAUNCH_OMNI_ATTEMPTED_SPLITSCREEN);
    }

    @Test
    public void invokeContextualSearchUncheckedWithHaptic_cssIsAvailable_commitHapticEnabled() {
        try (AutoCloseable flag = overrideSearchHapticCommitFlag(true)) {
            assertTrue("Expected invocation unchecked to succeed",
                    mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                            CONTEXTUAL_SEARCH_ENTRY_POINT));
            verify(mMockContextualSearchHapticManager).vibrateForSearch();
            verify(mMockContextualSearchManager).startContextualSearch(
                    CONTEXTUAL_SEARCH_ENTRY_POINT);
            verifyNoMoreInteractions(mMockStatsLogManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void invokeContextualSearchUncheckedWithHaptic_cssIsAvailable_commitHapticDisabled() {
        try (AutoCloseable flag = overrideSearchHapticCommitFlag(false)) {
            assertTrue("Expected invocation unchecked to succeed",
                    mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                            CONTEXTUAL_SEARCH_ENTRY_POINT));
            verify(mMockContextualSearchHapticManager, never()).vibrateForSearch();
            verify(mMockContextualSearchManager).startContextualSearch(
                    CONTEXTUAL_SEARCH_ENTRY_POINT);
            verifyNoMoreInteractions(mMockStatsLogManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void invokeContextualSearchUncheckedWithHaptic_cssIsNotAvailable_commitHapticEnabled() {
        when(mMockStateManager.isContextualSearchIntentAvailable()).thenReturn(false);

        try (AutoCloseable flag = overrideSearchHapticCommitFlag(true)) {
            // Still expect true since this method doesn't run the checks.
            assertTrue("Expected invocation unchecked to succeed",
                    mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                            CONTEXTUAL_SEARCH_ENTRY_POINT));
            // Still vibrate based on the flag.
            verify(mMockContextualSearchHapticManager).vibrateForSearch();
            verify(mMockContextualSearchManager).startContextualSearch(
                    CONTEXTUAL_SEARCH_ENTRY_POINT);
            verifyNoMoreInteractions(mMockStatsLogManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    public void invokeContextualSearchUncheckedWithHaptic_cssIsNotAvailable_commitHapticDisabled() {
        when(mMockStateManager.isContextualSearchIntentAvailable()).thenReturn(false);

        try (AutoCloseable flag = overrideSearchHapticCommitFlag(false)) {
            // Still expect true since this method doesn't run the checks.
            assertTrue("Expected ContextualSearch invocation unchecked to succeed",
                    mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                            CONTEXTUAL_SEARCH_ENTRY_POINT));
            // Still don't vibrate based on the flag.
            verify(mMockContextualSearchHapticManager, never()).vibrateForSearch();
            verify(mMockContextualSearchManager).startContextualSearch(
                    CONTEXTUAL_SEARCH_ENTRY_POINT);
            verifyNoMoreInteractions(mMockStatsLogManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void invokeContextualSearchUncheckedWithHaptic_liveTile() {
        when(mMockContainerInterface.isInLiveTileMode()).thenReturn(true);
        ArgumentCaptor<Runnable> switchToScreenshotCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> finishRecentsAnimationCaptor =
                ArgumentCaptor.forClass(Runnable.class);

        assertTrue("Expected invocation unchecked to succeed",
                mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                        CONTEXTUAL_SEARCH_ENTRY_POINT));
        verify(mMockRecentsView).switchToScreenshot(switchToScreenshotCaptor.capture());
        switchToScreenshotCaptor.getValue().run();
        verify(mMockRecentsView).finishRecentsAnimation(anyBoolean(), anyBoolean(),
                finishRecentsAnimationCaptor.capture());
        finishRecentsAnimationCaptor.getValue().run();
        verify(mMockContextualSearchManager).startContextualSearch(CONTEXTUAL_SEARCH_ENTRY_POINT);
        verifyNoMoreInteractions(mMockStatsLogManager);
    }

    @Test
    public void invokeContextualSearchUncheckedWithHaptic_liveTile_failsToSwitchToScreenshot() {
        when(mMockContainerInterface.isInLiveTileMode()).thenReturn(true);
        ArgumentCaptor<Runnable> switchToScreenshotCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> finishRecentsAnimationCaptor =
                ArgumentCaptor.forClass(Runnable.class);

        assertTrue("Expected invocation unchecked to succeed",
                mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                        CONTEXTUAL_SEARCH_ENTRY_POINT));
        verify(mMockRecentsView).switchToScreenshot(switchToScreenshotCaptor.capture());

        // Don't run switchToScreenshot's callback. Therefore, recents animation should not finish.
        verify(mMockRecentsView, never()).finishRecentsAnimation(anyBoolean(), anyBoolean(),
                finishRecentsAnimationCaptor.capture());
        // And ContextualSearch should not start.
        verify(mMockContextualSearchManager, never()).startContextualSearch(anyInt());
        verifyNoMoreInteractions(mMockStatsLogManager);
    }

    @Test
    public void invokeContextualSearchUncheckedWithHaptic_liveTile_failsToFinishRecentsAnimation() {
        when(mMockContainerInterface.isInLiveTileMode()).thenReturn(true);
        ArgumentCaptor<Runnable> switchToScreenshotCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> finishRecentsAnimationCaptor =
                ArgumentCaptor.forClass(Runnable.class);

        assertTrue("Expected invocation unchecked to succeed",
                mContextualSearchInvoker.invokeContextualSearchUncheckedWithHaptic(
                        CONTEXTUAL_SEARCH_ENTRY_POINT));
        verify(mMockRecentsView).switchToScreenshot(switchToScreenshotCaptor.capture());
        switchToScreenshotCaptor.getValue().run();
        verify(mMockRecentsView).finishRecentsAnimation(anyBoolean(), anyBoolean(),
                finishRecentsAnimationCaptor.capture());
        // Don't run finishRecentsAnimation's callback. Therefore ContextualSearch should not start.
        verify(mMockContextualSearchManager, never()).startContextualSearch(anyInt());
        verifyNoMoreInteractions(mMockStatsLogManager);
    }

    private AutoCloseable overrideSearchHapticCommitFlag(boolean value) {
        return TestExtensions.overrideNavConfigFlag(
                "ENABLE_SEARCH_HAPTIC_COMMIT",
                value,
                () -> DeviceConfigWrapper.get().getEnableSearchHapticCommit());
    }
}
