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
package com.android.quickstep.fallback

import com.android.launcher3.testing.shared.TestProtocol.BACKGROUND_APP_STATE_ORDINAL
import com.android.launcher3.testing.shared.TestProtocol.NORMAL_STATE_ORDINAL
import com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_MODAL_TASK_STATE_ORDINAL
import com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_SPLIT_SELECT_ORDINAL
import com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_STATE_ORDINAL
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.LauncherMultivalentJUnit.EmulatedDevices
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(LauncherMultivalentJUnit::class)
@EmulatedDevices(["pixelTablet2023"])
class RecentsStateUtilsTest {

    @Test
    fun testRecentsStateDefault_toLauncherStateOrdinal_isOverviewStateOrdinal() {
        assertThat(RecentsState.DEFAULT.toLauncherStateOrdinal()).isEqualTo(OVERVIEW_STATE_ORDINAL)
    }

    @Test
    fun testRecentsStateModal_toLauncherStateOrdinal_isModalTaskStateOrdinal() {
        assertThat(RecentsState.MODAL_TASK.toLauncherStateOrdinal())
            .isEqualTo(OVERVIEW_MODAL_TASK_STATE_ORDINAL)
    }

    @Test
    fun testRecentsStateBackgroundApp_toLauncherStateOrdinal_isBackgroundAppStateOrdinal() {
        assertThat(RecentsState.BACKGROUND_APP.toLauncherStateOrdinal())
            .isEqualTo(BACKGROUND_APP_STATE_ORDINAL)
    }

    @Test
    fun testRecentsStateHome_toLauncherStateOrdinal_isNormalStateOrdinal() {
        assertThat(RecentsState.HOME.toLauncherStateOrdinal()).isEqualTo(NORMAL_STATE_ORDINAL)
    }

    @Test
    fun testRecentsStateBgLauncher_toLauncherStateOrdinal_isNormalStateOrdinal() {
        assertThat(RecentsState.BG_LAUNCHER.toLauncherStateOrdinal())
            .isEqualTo(NORMAL_STATE_ORDINAL)
    }

    @Test
    fun testRecentsStateOverviewSplitSelect_toLauncherStateOrdinal_isOverviewSplitSelectStateOrdinal() {
        assertThat(RecentsState.OVERVIEW_SPLIT_SELECT.toLauncherStateOrdinal())
            .isEqualTo(OVERVIEW_SPLIT_SELECT_ORDINAL)
    }
}
