package com.android.launcher3.logging

import androidx.core.util.isEmpty
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit test for [StartupLatencyLogger]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class StartupLatencyLoggerTest {

    private val underTest: StartupLatencyLogger =
        StartupLatencyLogger(StatsLogManager.StatsLatencyLogger.LatencyType.COLD)

    @Before
    fun setup() {
        underTest.setIsInTest()
    }

    @Test
    @UiThreadTest
    fun logTotalDurationStart() {
        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            100
        )

        val startTime =
            underTest.startTimeByEvent.get(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.id
            )
        assertThat(startTime).isEqualTo(100)
        assertThat(underTest.endTimeByEvent.isEmpty()).isTrue()
    }

    @Test
    @UiThreadTest
    fun logTotalDurationEnd() {
        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            100
        )

        underTest.logEnd(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            100
        )

        val endTime =
            underTest.endTimeByEvent.get(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.id
            )
        assertThat(endTime).isEqualTo(100)
    }

    @Test
    @UiThreadTest
    fun logStartOfOtherEvents_withoutLogStartOfTotalDuration_noOp() {
        underTest
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE,
                100
            )
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION,
                101
            )
            .logStart(
                StatsLogManager.LauncherLatencyEvent
                    .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC,
                102
            )

        assertThat(underTest.startTimeByEvent.isEmpty()).isTrue()
    }

    @Test
    @UiThreadTest
    fun logStartOfOtherEvents_afterLogStartOfTotalDuration_logged() {
        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            100
        )

        underTest
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE,
                100
            )
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION,
                101
            )
            .logStart(
                StatsLogManager.LauncherLatencyEvent
                    .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC,
                102
            )

        assertThat(underTest.startTimeByEvent.size()).isEqualTo(4)
    }

    @Test
    @UiThreadTest
    fun logDuplicatedStartEvent_2ndEvent_notLogged() {
        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            100
        )

        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            101
        )

        assertThat(underTest.startTimeByEvent.size()).isEqualTo(1)
        assertThat(
                underTest.startTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION.id
                )
            )
            .isEqualTo(100)
    }

    @Test
    @UiThreadTest
    fun loadStartOfWorkspace_thenEndWithAsync_logAsyncStart() {
        underTest
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
                100
            )
            .logWorkspaceLoadStartTime(111)

        underTest.logEnd(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC,
            120
        )

        assertThat(underTest.startTimeByEvent.size()).isEqualTo(2)
        assertThat(
                underTest.startTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent
                        .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC
                        .id
                )
            )
            .isEqualTo(111)
    }

    @Test
    @UiThreadTest
    fun loadStartOfWorkspace_thenEndWithSync_logSyncStart() {
        underTest
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
                100
            )
            .logWorkspaceLoadStartTime(111)

        underTest.logEnd(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC,
            120
        )

        assertThat(underTest.startTimeByEvent.size()).isEqualTo(2)
        assertThat(
                underTest.startTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent
                        .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC
                        .id
                )
            )
            .isEqualTo(111)
    }

    @Test
    @UiThreadTest
    fun loadStartOfWorkspaceLoadSync_thenAsync_asyncNotLogged() {
        underTest
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
                100
            )
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC,
                110
            )

        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC,
            111
        )

        assertThat(underTest.startTimeByEvent.size()).isEqualTo(2)
        assertThat(
                underTest.startTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent
                        .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC
                        .id
                )
            )
            .isEqualTo(110)
        assertThat(
                underTest.startTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent
                        .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC
                        .id
                )
            )
            .isEqualTo(0)
    }

    @Test
    @UiThreadTest
    fun loadStartOfWorkspaceLoadAsync_thenSync_syncNotLogged() {
        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            100
        )
        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC,
            111
        )

        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC,
            112
        )

        assertThat(underTest.startTimeByEvent.size()).isEqualTo(2)
        assertThat(
                underTest.startTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent
                        .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC
                        .id
                )
            )
            .isEqualTo(111)
        assertThat(
                underTest.startTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent
                        .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC
                        .id
                )
            )
            .isEqualTo(0)
    }

    @Test
    @UiThreadTest
    fun logEndOfEvent_withoutStartEvent_notLogged() {
        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            100
        )

        underTest.logEnd(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC,
            120
        )

        assertThat(underTest.endTimeByEvent.size()).isEqualTo(0)
        assertThat(
                underTest.endTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent
                        .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC
                        .id
                )
            )
            .isEqualTo(0)
    }

    @Test
    @UiThreadTest
    fun logEndOfEvent_afterEndOfTotalDuration_notLogged() {
        underTest
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
                100
            )
            .logEnd(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
                120
            )

        underTest.logEnd(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC,
            121
        )

        assertThat(underTest.endTimeByEvent.size()).isEqualTo(1)
        assertThat(
                underTest.endTimeByEvent.get(
                    StatsLogManager.LauncherLatencyEvent
                        .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_SYNC
                        .id
                )
            )
            .isEqualTo(0)
    }

    @Test
    @UiThreadTest
    fun logCardinality_setCardinality() {
        underTest.logCardinality(-1)
        underTest.logStart(
            StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
            100
        )

        underTest.logCardinality(235)

        assertThat(underTest.cardinality).isEqualTo(235)
    }

    @Test
    @UiThreadTest
    fun reset_clearState() {
        underTest
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
                100
            )
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE,
                100
            )
            .logStart(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION,
                110
            )
            .logEnd(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_VIEW_INFLATION,
                115
            )
            .logWorkspaceLoadStartTime(120)
            .logCardinality(235)
            .logEnd(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_ACTIVITY_ON_CREATE,
                100
            )
            .logEnd(
                StatsLogManager.LauncherLatencyEvent
                    .LAUNCHER_LATENCY_STARTUP_WORKSPACE_LOADER_ASYNC,
                140
            )
            .logEnd(
                StatsLogManager.LauncherLatencyEvent.LAUNCHER_LATENCY_STARTUP_TOTAL_DURATION,
                160
            )
        assertThat(underTest.startTimeByEvent.size()).isEqualTo(4)
        assertThat(underTest.endTimeByEvent.size()).isEqualTo(4)
        assertThat(underTest.cardinality).isEqualTo(235)

        underTest.reset()

        assertThat(underTest.startTimeByEvent.isEmpty()).isTrue()
        assertThat(underTest.endTimeByEvent.isEmpty()).isTrue()
        assertThat(underTest.cardinality).isEqualTo(StartupLatencyLogger.UNSET_INT)
        assertThat(underTest.workspaceLoadStartTime).isEqualTo(StartupLatencyLogger.UNSET_LONG)
    }
}
