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

package com.android.wm.shell.splitscreen

import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.icons.IconProvider
import com.android.wm.shell.Flags.enableFlexibleSplit
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_33_66
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_66_33
import com.android.wm.shell.splitscreen.StageTaskListener.StageListenerCallbacks
import com.android.wm.shell.windowdecor.WindowDecorViewModel
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import java.util.Optional

@SmallTest
@RunWith(AndroidJUnit4::class)
class StageOrderOperatorTests : ShellTestCase() {

    @Mock
    lateinit var mTaskOrganizer: ShellTaskOrganizer
    @Mock
    lateinit var mSyncQueue: SyncTransactionQueue
    @Mock
    lateinit var stageListenerCallbacks: StageListenerCallbacks
    @Mock
    lateinit var iconProvider: IconProvider
    @Mock
    lateinit var windowDecorViewModel: Optional<WindowDecorViewModel>

    lateinit var stageOrderOperator: StageOrderOperator

    @Before
    fun setup() {
        stageOrderOperator = StageOrderOperator(
            context,
            mTaskOrganizer,
            DEFAULT_DISPLAY,
            stageListenerCallbacks,
            mSyncQueue,
            iconProvider,
            windowDecorViewModel,
            )
        assert(stageOrderOperator.activeStages.size == 0)
    }

    @Test
    fun activeStages_2_2app_50_50_split() {
        assumeTrue(enableFlexibleSplit())

        stageOrderOperator.onEnteringSplit(SNAP_TO_2_50_50)
        assert(stageOrderOperator.activeStages.size == 2)
    }

    @Test
    fun activeStages_2_2app_33_66_split() {
        assumeTrue(enableFlexibleSplit())

        stageOrderOperator.onEnteringSplit(SNAP_TO_2_33_66)
        assert(stageOrderOperator.activeStages.size == 2)
    }

    @Test
    fun activeStages_2_2app_66_33_split() {
        assumeTrue(enableFlexibleSplit())

        stageOrderOperator.onEnteringSplit(SNAP_TO_2_66_33)
        assert(stageOrderOperator.activeStages.size == 2)
    }

    @Test
    fun activateSameCountStage_noOp() {
        assumeTrue(enableFlexibleSplit())

        stageOrderOperator.onEnteringSplit(SNAP_TO_2_66_33)
        stageOrderOperator.onEnteringSplit(SNAP_TO_2_66_33)
        assert(stageOrderOperator.activeStages.size == 2)
    }

    @Test
    fun deactivate_emptyActiveStages() {
        assumeTrue(enableFlexibleSplit())

        stageOrderOperator.onEnteringSplit(SNAP_TO_2_66_33)
        stageOrderOperator.onExitingSplit()
        assert(stageOrderOperator.activeStages.isEmpty())
    }

    @Test
    fun swapDividerPos_twoApps() {
        assumeTrue(enableFlexibleSplit())

        stageOrderOperator.onEnteringSplit(SNAP_TO_2_66_33)
        val stageIndex0: StageTaskListener = stageOrderOperator.activeStages[0]
        val stageIndex1: StageTaskListener = stageOrderOperator.activeStages[1]

        stageOrderOperator.onDoubleTappedDivider()
        val newStageIndex0: StageTaskListener = stageOrderOperator.activeStages[0]
        val newStageIndex1: StageTaskListener = stageOrderOperator.activeStages[1]

        assert(stageIndex0 == newStageIndex1)
        assert(stageIndex1 == newStageIndex0)
    }

    @Test
    fun swapDividerPos_twiceNoOp_twoApps() {
        assumeTrue(enableFlexibleSplit())

        stageOrderOperator.onEnteringSplit(SNAP_TO_2_66_33)
        val stageIndex0: StageTaskListener = stageOrderOperator.activeStages[0]
        val stageIndex1: StageTaskListener = stageOrderOperator.activeStages[1]

        stageOrderOperator.onDoubleTappedDivider()
        stageOrderOperator.onDoubleTappedDivider()
        val newStageIndex0: StageTaskListener = stageOrderOperator.activeStages[0]
        val newStageIndex1: StageTaskListener = stageOrderOperator.activeStages[1]

        assert(stageIndex0 == newStageIndex0)
        assert(stageIndex1 == newStageIndex1)
    }
}
