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

package com.android.wm.shell.desktopmode


import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.Companion.DesktopUiEventEnum.DESKTOP_WINDOW_EDGE_DRAG_RESIZE
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for [DesktopModeUiEventLogger]
 *
 * Usage: atest WMShellUnitTests:DesktopModeUiEventLoggerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopModeUiEventLoggerTest : ShellTestCase() {
    private lateinit var uiEventLoggerFake: UiEventLoggerFake
    private lateinit var logger: DesktopModeUiEventLogger
    private val instanceIdSequence = InstanceIdSequence(/* instanceIdMax */ 1 shl 20)


    @Before
    fun setUp() {
        uiEventLoggerFake = UiEventLoggerFake()
        logger = DesktopModeUiEventLogger(uiEventLoggerFake, instanceIdSequence)
    }

    @Test
    fun log_invalidUid_eventNotLogged() {
        logger.log(-1, PACKAGE_NAME, DESKTOP_WINDOW_EDGE_DRAG_RESIZE)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
    }

    @Test
    fun log_emptyPackageName_eventNotLogged() {
        logger.log(UID, "", DESKTOP_WINDOW_EDGE_DRAG_RESIZE)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
    }

    @Test
    fun log_eventLogged() {
        val event =
            DESKTOP_WINDOW_EDGE_DRAG_RESIZE
        logger.log(UID, PACKAGE_NAME, event)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0)).isEqualTo(event.id)
        assertThat(uiEventLoggerFake[0].instanceId).isNull()
        assertThat(uiEventLoggerFake[0].uid).isEqualTo(UID)
        assertThat(uiEventLoggerFake[0].packageName).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun getNewInstanceId() {
        val first = logger.getNewInstanceId()
        assertThat(first).isNotEqualTo(logger.getNewInstanceId())
    }

    @Test
    fun logWithInstanceId_invalidUid_eventNotLogged() {
        logger.logWithInstanceId(INSTANCE_ID, -1, PACKAGE_NAME, DESKTOP_WINDOW_EDGE_DRAG_RESIZE)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
    }

    @Test
    fun logWithInstanceId_emptyPackageName_eventNotLogged() {
        logger.logWithInstanceId(INSTANCE_ID, UID, "", DESKTOP_WINDOW_EDGE_DRAG_RESIZE)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(0)
    }

    @Test
    fun logWithInstanceId_eventLogged() {
        val event =
            DESKTOP_WINDOW_EDGE_DRAG_RESIZE
        logger.logWithInstanceId(INSTANCE_ID, UID, PACKAGE_NAME, event)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0)).isEqualTo(event.id)
        assertThat(uiEventLoggerFake[0].instanceId).isEqualTo(INSTANCE_ID)
        assertThat(uiEventLoggerFake[0].uid).isEqualTo(UID)
        assertThat(uiEventLoggerFake[0].packageName).isEqualTo(PACKAGE_NAME)
    }


    companion object {
        private val INSTANCE_ID = InstanceId.fakeInstanceId(0)
        private const val UID = 10
        private const val PACKAGE_NAME = "com.foo"
    }
}
