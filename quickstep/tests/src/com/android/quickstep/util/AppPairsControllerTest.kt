/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.quickstep.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_30_70
import com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_50_50
import com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_70_30
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class AppPairsControllerTest {
    @Mock lateinit var context: Context
    @Mock lateinit var splitSelectStateController: SplitSelectStateController
    @Mock lateinit var statsLogManager: StatsLogManager

    private lateinit var appPairsController: AppPairsController

    private val left30: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_TOP_OR_LEFT, SNAP_TO_30_70)
    }
    private val left50: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_TOP_OR_LEFT, SNAP_TO_50_50)
    }
    private val left70: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_TOP_OR_LEFT, SNAP_TO_70_30)
    }
    private val right30: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_BOTTOM_OR_RIGHT, SNAP_TO_30_70)
    }
    private val right50: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_BOTTOM_OR_RIGHT, SNAP_TO_50_50)
    }
    private val right70: Int by lazy {
        appPairsController.encodeRank(STAGE_POSITION_BOTTOM_OR_RIGHT, SNAP_TO_70_30)
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        appPairsController =
            AppPairsController(context, splitSelectStateController, statsLogManager)
    }

    @Test
    fun shouldEncodeRankCorrectly() {
        assertEquals("left + 30-70 should encode as 0 (0b0)", 0, left30)
        assertEquals("left + 50-50 should encode as 1 (0b1)", 1, left50)
        assertEquals("left + 70-30 should encode as 2 (0b10)", 2, left70)
        // See AppPairsController#BITMASK_SIZE and BITMASK_FOR_SNAP_POSITION for context
        assertEquals("right + 30-70 should encode as 1 followed by 16 0s", 1 shl 16, right30)
        assertEquals("right + 50-50 should encode as the above value + 1", (1 shl 16) + 1, right50)
        assertEquals("right + 70-30 should encode as the above value + 2", (1 shl 16) + 2, right70)
    }

    @Test
    fun shouldDecodeRankCorrectly() {
        assertEquals(
            "left + 30-70 should decode to left",
            STAGE_POSITION_TOP_OR_LEFT,
            AppPairsController.convertRankToStagePosition(left30),
        )
        assertEquals(
            "left + 30-70 should decode to 30-70",
            SNAP_TO_30_70,
            AppPairsController.convertRankToSnapPosition(left30),
        )

        assertEquals(
            "left + 50-50 should decode to left",
            STAGE_POSITION_TOP_OR_LEFT,
            AppPairsController.convertRankToStagePosition(left50),
        )
        assertEquals(
            "left + 50-50 should decode to 50-50",
            SNAP_TO_50_50,
            AppPairsController.convertRankToSnapPosition(left50),
        )

        assertEquals(
            "left + 70-30 should decode to left",
            STAGE_POSITION_TOP_OR_LEFT,
            AppPairsController.convertRankToStagePosition(left70),
        )
        assertEquals(
            "left + 70-30 should decode to 70-30",
            SNAP_TO_70_30,
            AppPairsController.convertRankToSnapPosition(left70),
        )

        assertEquals(
            "right + 30-70 should decode to right",
            STAGE_POSITION_BOTTOM_OR_RIGHT,
            AppPairsController.convertRankToStagePosition(right30),
        )
        assertEquals(
            "right + 30-70 should decode to 30-70",
            SNAP_TO_30_70,
            AppPairsController.convertRankToSnapPosition(right30),
        )

        assertEquals(
            "right + 50-50 should decode to right",
            STAGE_POSITION_BOTTOM_OR_RIGHT,
            AppPairsController.convertRankToStagePosition(right50),
        )
        assertEquals(
            "right + 50-50 should decode to 50-50",
            SNAP_TO_50_50,
            AppPairsController.convertRankToSnapPosition(right50),
        )

        assertEquals(
            "right + 70-30 should decode to right",
            STAGE_POSITION_BOTTOM_OR_RIGHT,
            AppPairsController.convertRankToStagePosition(right70),
        )
        assertEquals(
            "right + 70-30 should decode to 70-30",
            SNAP_TO_70_30,
            AppPairsController.convertRankToSnapPosition(right70),
        )
    }
}
