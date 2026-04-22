/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.logging

import androidx.core.util.isEmpty
import androidx.test.filters.SmallTest
import com.android.launcher3.logging.StartupLatencyLogger.ColdRebootStartupLogger
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION
import com.android.launcher3.logging.StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.Companion.LAUNCHER_LATENCY_PACKAGE_ID
import com.android.launcher3.logging.StatsLogManager.StatsLatencyLogger.LatencyType
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.TestUtil
import com.android.launcher3.views.ActivityContext
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers.RETURNS_SELF
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Unit test for [ColdRebootStartupLogger]. */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class StartupLatencyLoggerTest {

    @Mock lateinit var ctx: ActivityContext
    @Mock lateinit var timeProvider: () -> Long
    @Mock lateinit var statsLogManager: StatsLogManager

    private val trackedLoggers = mutableMapOf<LauncherLatencyEvent, StatsLatencyLogger>()

    private val underTest by lazy { ColdRebootStartupLogger(ctx, timeProvider) }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        doReturn(statsLogManager).whenever(ctx).statsLogManager
        doAnswer {
                mock<StatsLatencyLogger>(defaultAnswer = RETURNS_SELF).apply {
                    doAnswer { invocation -> trackedLoggers[invocation.getArgument(0)] = this }
                        .whenever(this)
                        .log(any())
                }
            }
            .whenever(statsLogManager)
            .latencyLogger()
    }

    @Test
    fun logTotalDurationStart() {
        doReturn(100).whenever(timeProvider).invoke()

        val startTime = underTest.startTimeByEvent.get(LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.id)
        assertThat(startTime).isEqualTo(100)
        assertThat(underTest.endTimeByEvent.isEmpty()).isTrue()
    }

    @Test
    fun logTotalDurationEnd() {
        whenever(timeProvider.invoke()).thenReturn(100).thenReturn(101)

        underTest.logEnd(LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION)

        val endTime = underTest.endTimeByEvent.get(LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.id)
        assertThat(endTime).isEqualTo(101)
    }

    @Test
    fun logStartOfOtherEvents_afterLogStartOfTotalDuration_logged() {
        whenever(timeProvider.invoke())
            .thenReturn(100)
            .thenReturn(101)
            .thenReturn(102)
            .thenReturn(103)

        underTest.apply {
            logStart(LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE)
            logStart(LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION)
            logStart(LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC)
        }

        assertThat(underTest.startTimeByEvent.size()).isEqualTo(4)
    }

    @Test
    fun finishLogs_commits_logs() {
        whenever(timeProvider.invoke()).thenReturn(100).thenReturn(102)
        underTest.finishLogs(10, true)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        assertThat(trackedLoggers).hasSize(1)
        LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.verifyLoggedEvent(2)
    }

    @Test
    fun finishLogs_returns_noop_for_followup() {
        whenever(timeProvider.invoke()).thenReturn(100)
        val followup = underTest.finishLogs(10, true)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertThat(followup).isEqualTo(StartupLatencyLogger.NoOpLogger)
    }

    @Test
    fun finishLogs_commits_all_sent_events() {
        whenever(timeProvider.invoke())
            .thenReturn(100)
            .thenReturn(200)
            .thenReturn(210)
            .thenReturn(230)
        underTest.logStart(LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE)
        underTest.logEnd(LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE)

        underTest.finishLogs(10, true)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        assertThat(trackedLoggers).hasSize(2)
        LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.verifyLoggedEvent(130)
        LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE.verifyLoggedEvent(10)
    }

    @Test
    fun finishLogs_logs_workspace_async_load_on_async_bind() {
        whenever(timeProvider.invoke()).thenReturn(100).thenReturn(200).thenReturn(250)
        underTest.logWorkspaceLoadStartTime()

        underTest.finishLogs(30, false)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        assertThat(trackedLoggers).hasSize(2)
        LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.verifyLoggedEvent(150, 30)
        LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC.verifyLoggedEvent(50, 30)
    }

    @Test
    fun finishLogs_does_not_log_workspace_async_load_on_sync_bind() {
        whenever(timeProvider.invoke()).thenReturn(100).thenReturn(200).thenReturn(250)
        underTest.logWorkspaceLoadStartTime()

        underTest.finishLogs(30, true)
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        assertThat(trackedLoggers).hasSize(1)
        LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.verifyLoggedEvent(150)
    }

    private fun LauncherLatencyEvent.verifyLoggedEvent(latency: Long, cardinality: Int = -1) {
        val logger = trackedLoggers[this]!!
        verify(logger).withLatency(latency)
        verify(logger).withPackageId(LAUNCHER_LATENCY_PACKAGE_ID)
        verify(logger).withType(LatencyType.COLD_DEVICE_REBOOTING)
        verify(logger).withCardinality(cardinality)
    }
}
