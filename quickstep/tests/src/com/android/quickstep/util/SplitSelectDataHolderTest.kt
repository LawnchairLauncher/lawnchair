/*
 *  Copyright (C) 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.quickstep.util

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.UserHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.ui.AbstractLauncherUiTest
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_PENDINGINTENT_PENDINGINTENT
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_PENDINGINTENT_TASK
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_SHORTCUT_TASK
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_SINGLE_INTENT_FULLSCREEN
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_SINGLE_SHORTCUT_FULLSCREEN
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_SINGLE_TASK_FULLSCREEN
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_TASK_PENDINGINTENT
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_TASK_SHORTCUT
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SPLIT_TASK_TASK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SplitSelectDataHolderTest {
    private lateinit var splitSelectDataHolder: SplitSelectDataHolder

    private val context: Context =
        ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext)
    private val sampleTaskInfo = RunningTaskInfo()
    private val sampleTaskId = 10
    private val sampleTaskId2 = 11
    private val sampleUser = UserHandle(0)
    private val sampleIntent = Intent()
    private val sampleIntent2 = Intent()
    private val sampleShortcut = Intent()
    private val sampleShortcut2 = Intent()
    private val sampleItemInfo = ItemInfo()
    private val samplePackage =
        AbstractLauncherUiTest.resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR)

    @Before
    fun setup() {
        splitSelectDataHolder = SplitSelectDataHolder(context)

        sampleTaskInfo.taskId = sampleTaskId
        sampleItemInfo.user = sampleUser
        sampleIntent.setPackage(samplePackage)
        sampleIntent2.setPackage(samplePackage)
        sampleShortcut.setPackage(samplePackage)
        sampleShortcut2.setPackage(samplePackage)
        sampleShortcut.putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, "sampleShortcut")
        sampleShortcut2.putExtra(ShortcutKey.EXTRA_SHORTCUT_ID, "sampleShortcut2")
    }

    @Test
    fun setInitialAsTask() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            null,
            null
        )
        assertTrue(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun setInitialAsIntent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        assertTrue(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun setInitialAsIntentWithAlreadyRunningTask() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            sampleTaskId
        )
        assertTrue(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun setInitialAsShortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        assertTrue(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun setSecondAsTask() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        splitSelectDataHolder.setSecondTask(sampleTaskId)
        assertTrue(splitSelectDataHolder.isBothSplitAppsConfirmed())
    }

    @Test
    fun setSecondAsIntent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            null,
            null
        )
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser)
        assertTrue(splitSelectDataHolder.isBothSplitAppsConfirmed())
    }

    @Test
    fun setSecondAsShortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        splitSelectDataHolder.setSecondTask(sampleShortcut, sampleUser)
        assertTrue(splitSelectDataHolder.isBothSplitAppsConfirmed())
    }

    @Test
    fun generateLaunchData_Task_Task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null
        )
        splitSelectDataHolder.setSecondTask(sampleTaskId2)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_TASK_TASK)

        // should contain a valid task ID for first app, and no intent or shortcut
        assertNotEquals(launchData.initialTaskId, INVALID_TASK_ID)
        assertNull(launchData.initialPendingIntent)
        assertNull(launchData.initialShortcut)

        // should contain a valid task ID for second app, and no intent or shortcut
        assertNotEquals(launchData.secondTaskId, INVALID_TASK_ID)
        assertNull(launchData.secondPendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Task_Intent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null
        )
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_TASK_PENDINGINTENT)

        // should contain a valid task ID for first app, and no intent or shortcut
        assertNotEquals(launchData.initialTaskId, INVALID_TASK_ID)
        assertNull(launchData.initialPendingIntent)
        assertNull(launchData.initialShortcut)

        // should contain a valid intent for second app, and no task ID or shortcut
        assertNotNull(launchData.secondPendingIntent)
        assertEquals(launchData.secondTaskId, INVALID_TASK_ID)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Task_Shortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null
        )
        splitSelectDataHolder.setSecondTask(sampleShortcut, sampleUser)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_TASK_SHORTCUT)

        // should contain a valid task ID for first app, and no intent or shortcut
        assertNotEquals(launchData.initialTaskId, INVALID_TASK_ID)
        assertNull(launchData.initialPendingIntent)
        assertNull(launchData.initialShortcut)

        // should contain a valid shortcut and intent for second app, and no task ID
        assertNotNull(launchData.secondShortcut)
        assertNotNull(launchData.secondPendingIntent)
        assertEquals(launchData.secondTaskId, INVALID_TASK_ID)
    }

    @Test
    fun generateLaunchData_Intent_Task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        splitSelectDataHolder.setSecondTask(sampleTaskId)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_PENDINGINTENT_TASK)

        // should contain a valid intent for first app, and no task ID or shortcut
        assertNotNull(launchData.initialPendingIntent)
        assertEquals(launchData.initialTaskId, INVALID_TASK_ID)
        assertNull(launchData.initialShortcut)

        // should contain a valid task ID for second app, and no intent or shortcut
        assertNotEquals(launchData.secondTaskId, INVALID_TASK_ID)
        assertNull(launchData.secondPendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Shortcut_Task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        splitSelectDataHolder.setSecondTask(sampleTaskId)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_SHORTCUT_TASK)

        // should contain a valid shortcut and intent for first app, and no task ID
        assertNotNull(launchData.initialShortcut)
        assertNotNull(launchData.initialPendingIntent)
        assertEquals(launchData.initialTaskId, INVALID_TASK_ID)

        // should contain a valid task ID for second app, and no intent or shortcut
        assertNotEquals(launchData.secondTaskId, INVALID_TASK_ID)
        assertNull(launchData.secondPendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Intent_Intent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        splitSelectDataHolder.setSecondTask(sampleIntent2, sampleUser)
        val launchData = splitSelectDataHolder.getSplitLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_PENDINGINTENT_PENDINGINTENT)

        // should contain a valid intent for first app, and no task ID or shortcut
        assertNotNull(launchData.initialPendingIntent)
        assertEquals(launchData.initialTaskId, INVALID_TASK_ID)
        assertNull(launchData.initialShortcut)

        // should contain a valid intent for second app, and no task ID or shortcut
        assertNotNull(launchData.secondPendingIntent)
        assertEquals(launchData.secondTaskId, INVALID_TASK_ID)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Single_Task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null
        )
        val launchData = splitSelectDataHolder.getFullscreenLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_SINGLE_TASK_FULLSCREEN)

        // should contain a valid task ID for first app, and no intent or shortcut
        assertNotEquals(launchData.initialTaskId, INVALID_TASK_ID)
        assertNull(launchData.initialPendingIntent)
        assertNull(launchData.initialShortcut)

        // should contain no task ID, intent, or shortcut for second app
        assertEquals(launchData.secondTaskId, INVALID_TASK_ID)
        assertNull(launchData.secondPendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Single_Intent() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleIntent,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        val launchData = splitSelectDataHolder.getFullscreenLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_SINGLE_INTENT_FULLSCREEN)

        // should contain a valid intent for first app, and no task ID or shortcut
        assertNotNull(launchData.initialPendingIntent)
        assertEquals(launchData.initialTaskId, INVALID_TASK_ID)
        assertNull(launchData.initialShortcut)

        // should contain no task ID, intent, or shortcut for second app
        assertEquals(launchData.secondTaskId, INVALID_TASK_ID)
        assertNull(launchData.secondPendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun generateLaunchData_Single_Shortcut() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleShortcut,
            STAGE_POSITION_TOP_OR_LEFT,
            sampleItemInfo,
            null,
            INVALID_TASK_ID
        )
        val launchData = splitSelectDataHolder.getFullscreenLaunchData()

        assertEquals(launchData.splitLaunchType, SPLIT_SINGLE_SHORTCUT_FULLSCREEN)

        // should contain a valid shortcut and intent for first app, and no task ID
        assertNotNull(launchData.initialShortcut)
        assertNotNull(launchData.initialPendingIntent)
        assertEquals(launchData.initialTaskId, INVALID_TASK_ID)

        // should contain no task ID, intent, or shortcut for second app
        assertEquals(launchData.secondTaskId, INVALID_TASK_ID)
        assertNull(launchData.secondPendingIntent)
        assertNull(launchData.secondShortcut)
    }

    @Test
    fun clearState_task() {
        splitSelectDataHolder.setInitialTaskSelect(
            sampleTaskInfo,
            STAGE_POSITION_TOP_OR_LEFT,
            null,
            null
        )
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser)
        splitSelectDataHolder.resetState()
        assertFalse(splitSelectDataHolder.isSplitSelectActive())
    }

    @Test
    fun clearState_intent() {
        splitSelectDataHolder.setInitialTaskSelect(
                sampleIntent,
                STAGE_POSITION_TOP_OR_LEFT,
                sampleItemInfo,
                null,
                INVALID_TASK_ID
        )
        splitSelectDataHolder.setSecondTask(sampleIntent, sampleUser)
        splitSelectDataHolder.resetState()
        assertFalse(splitSelectDataHolder.isSplitSelectActive())
    }
}
