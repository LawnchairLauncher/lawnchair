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

import android.annotation.IntDef
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.util.Log
import com.android.internal.annotations.VisibleForTesting
import com.android.launcher3.logging.StatsLogManager.EventEnum
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.launcher3.util.SplitConfigurationOptions.getOppositeStagePosition
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SplitLaunchType
import java.io.PrintWriter

/**
 * Holds/transforms/signs/seals/delivers information for the transient state of the user
 * selecting a first app to start split with and then choosing a second app.
 * This class DOES NOT associate itself with drag-and-drop split screen starts because they come
 * from the bad part of town.
 *
 * After setting the correct fields for initial/second.* variables, this converts them into the
 * correct [PendingIntent] and [ShortcutInfo] objects where applicable and sends the necessary
 * data back via [getSplitLaunchData].
 * [SplitLaunchType] indicates the type of tasks/apps/intents being launched given the provided
 * state
 */
class SplitSelectDataHolder(
        val context: Context
) {
    val TAG = SplitSelectDataHolder::class.simpleName

    /**
     * Order of the constant indicates the order of which task/app was selected.
     * Ex. SPLIT_TASK_SHORTCUT means primary split app identified by task, secondary is shortcut
     * SPLIT_SHORTCUT_TASK means primary split app is determined by shortcut, secondary is task
     */
    companion object {
        @IntDef(SPLIT_TASK_TASK, SPLIT_TASK_PENDINGINTENT, SPLIT_TASK_SHORTCUT,
                SPLIT_PENDINGINTENT_TASK, SPLIT_PENDINGINTENT_PENDINGINTENT, SPLIT_SHORTCUT_TASK,
                SPLIT_SINGLE_TASK_FULLSCREEN, SPLIT_SINGLE_INTENT_FULLSCREEN,
                SPLIT_SINGLE_SHORTCUT_FULLSCREEN)
        @Retention(AnnotationRetention.SOURCE)
        annotation class SplitLaunchType

        const val SPLIT_TASK_TASK = 0
        const val SPLIT_TASK_PENDINGINTENT = 1
        const val SPLIT_TASK_SHORTCUT = 2
        const val SPLIT_PENDINGINTENT_TASK = 3
        const val SPLIT_SHORTCUT_TASK = 4
        const val SPLIT_PENDINGINTENT_PENDINGINTENT = 5

        // Non-split edge case of launching the initial selected task as a fullscreen task
        const val SPLIT_SINGLE_TASK_FULLSCREEN = 6
        const val SPLIT_SINGLE_INTENT_FULLSCREEN = 7
        const val SPLIT_SINGLE_SHORTCUT_FULLSCREEN = 8
    }


    @StagePosition
    private var initialStagePosition: Int = STAGE_POSITION_UNDEFINED
    private var initialTaskId: Int = INVALID_TASK_ID
    private var secondTaskId: Int = INVALID_TASK_ID
    private var initialUser: UserHandle? = null
    private var secondUser: UserHandle? = null
    private var initialIntent: Intent? = null
    private var secondIntent: Intent? = null
    private var secondPendingIntent: PendingIntent? = null
    private var itemInfo: ItemInfo? = null
    private var splitEvent: EventEnum? = null
    private var initialShortcut: ShortcutInfo? = null
    private var secondShortcut: ShortcutInfo? = null
    private var initialPendingIntent: PendingIntent? = null

    /**
     * @param alreadyRunningTask if set to [android.app.ActivityTaskManager.INVALID_TASK_ID]
     * then @param intent will be used to launch the initial task
     * @param intent will be ignored if @param alreadyRunningTask is set
     */
    fun setInitialTaskSelect(intent: Intent?, @StagePosition stagePosition: Int,
                             itemInfo: ItemInfo?, splitEvent: EventEnum?,
                             alreadyRunningTask: Int) {
        if (alreadyRunningTask != INVALID_TASK_ID) {
            initialTaskId = alreadyRunningTask
        } else {
            initialIntent = intent!!
            initialUser = itemInfo!!.user
        }
        setInitialData(stagePosition, splitEvent, itemInfo)
    }

    /**
     * To be called after first task selected from using a split shortcut from the fullscreen
     * running app.
     */
    fun setInitialTaskSelect(info: RunningTaskInfo,
                             @StagePosition stagePosition: Int, itemInfo: ItemInfo?,
                             splitEvent: EventEnum?) {
        initialTaskId = info.taskId
        setInitialData(stagePosition, splitEvent, itemInfo)
    }

    private fun setInitialData(@StagePosition stagePosition: Int,
                               event: EventEnum?, item: ItemInfo?) {
        itemInfo = item
        initialStagePosition = stagePosition
        splitEvent = event
    }

    /**
     * To be called as soon as user selects the second task (even if animations aren't complete)
     * @param taskId The second task that will be launched.
     */
    fun setSecondTask(taskId: Int) {
        secondTaskId = taskId
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * @param intent The second intent that will be launched.
     * @param user The user of that intent.
     */
    fun setSecondTask(intent: Intent, user: UserHandle) {
        secondIntent = intent
        secondUser = user
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     * Sets [secondUser] from that of the pendingIntent
     * @param pendingIntent The second PendingIntent that will be launched.
     */
    fun setSecondTask(pendingIntent: PendingIntent) {
        secondPendingIntent = pendingIntent
        secondUser = pendingIntent.creatorUserHandle
    }

    private fun getShortcutInfo(intent: Intent?, user: UserHandle?): ShortcutInfo? {
        val intentPackage = intent?.getPackage() ?: return null
        val shortcutId = intent.getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID)
                ?: return null
        try {
            val context: Context =
                if (user != null) {
                    context.createPackageContextAsUser(intentPackage, 0 /* flags */, user)
                } else {
                    context.createPackageContext(intentPackage, 0 /* *flags */)
                }
            return ShortcutInfo.Builder(context, shortcutId).build()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to create a ShortcutInfo for " + intent.getPackage())
        }
        return null
    }

    /**
     * Converts intents to pendingIntents, associating the [user] with the intent if provided
     */
    private fun getPendingIntent(intent: Intent?, user: UserHandle?): PendingIntent? {
        if (intent != initialIntent && intent != secondIntent) {
            throw IllegalStateException("Invalid intent to convert to PendingIntent")
        }

        return if (intent == null) {
            null
        } else if (user != null) {
            PendingIntent.getActivityAsUser(context, 0, intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT,
                    null /* options */, user)
        } else {
            PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT)
        }
    }

    /**
     * @return [SplitLaunchData] with the necessary fields populated as determined by
     *   [SplitLaunchData.splitLaunchType]. This is to be used for launching splitscreen
     */
    fun getSplitLaunchData() : SplitLaunchData {
        // Convert all intents to shortcut infos to see if determine if we launch shortcut or intent
        convertIntentsToFinalTypes()
        val splitLaunchType = getSplitLaunchType()
        if (splitLaunchType == SPLIT_TASK_PENDINGINTENT || splitLaunchType == SPLIT_TASK_SHORTCUT) {
            // need to get opposite stage position
            initialStagePosition = getOppositeStagePosition(initialStagePosition)
        }

        return generateSplitLaunchData(splitLaunchType)
    }

    /**
     * @return [SplitLaunchData] with the necessary fields populated as determined by
     *   [SplitLaunchData.splitLaunchType]. This is to be used for launching an initially selected
     *   split task in fullscreen
     */
    fun getFullscreenLaunchData() : SplitLaunchData {
        // Convert all intents to shortcut infos to see if determine if we launch shortcut or intent
        convertIntentsToFinalTypes()
        val splitLaunchType = getFullscreenLaunchType()

        return generateSplitLaunchData(splitLaunchType)
    }

    private fun generateSplitLaunchData(@SplitLaunchType splitLaunchType: Int) : SplitLaunchData {
        return SplitLaunchData(
                splitLaunchType,
                initialTaskId,
                secondTaskId,
                initialPendingIntent,
                secondPendingIntent,
                initialUser?.identifier ?: -1,
                secondUser?.identifier ?: -1,
                initialShortcut,
                secondShortcut,
                itemInfo,
                splitEvent,
                initialStagePosition)
    }

    /**
     * Converts our [initialIntent] and [secondIntent] into shortcuts and pendingIntents, if
     * possible.
     *
     * Note that both [initialIntent] and [secondIntent] will be nullified on method return
     *
     * One caveat is that if [secondPendingIntent] is set, we will use that and *not* attempt to
     * convert [secondIntent]
     */
    private fun convertIntentsToFinalTypes() {
        initialShortcut = getShortcutInfo(initialIntent, checkNotNull(initialUser))
        initialPendingIntent = getPendingIntent(initialIntent, initialUser)
        initialIntent = null

        // Only one of the two is currently allowed (secondPendingIntent directly set for widgets)
        if (secondIntent != null && secondPendingIntent != null) {
            throw IllegalStateException("Both secondIntent and secondPendingIntent non-null")
        }
        // If secondPendingIntent already set, no need to convert. Prioritize using that
        if (secondPendingIntent != null) {
            secondIntent = null
            return
        }

        secondShortcut = getShortcutInfo(secondIntent, checkNotNull(secondUser))
        secondPendingIntent = getPendingIntent(secondIntent, secondUser)
        secondIntent = null
    }

    /**
     * Only valid data fields at this point should be tasks, shortcuts, or pendingIntents
     * Intents need to be converted in [convertIntentsToFinalTypes] prior to calling this method
     */
    @VisibleForTesting
    @SplitLaunchType
    fun getSplitLaunchType(): Int {
        if (initialIntent != null || secondIntent != null) {
            throw IllegalStateException("Intents need to be converted")
        }

        // Prioritize task launches first
        if (initialTaskId != INVALID_TASK_ID) {
            if (secondTaskId != INVALID_TASK_ID) {
                return SPLIT_TASK_TASK
            }
            if (secondShortcut != null) {
                return SPLIT_TASK_SHORTCUT
            }
            if (secondPendingIntent != null) {
                return SPLIT_TASK_PENDINGINTENT
            }
        }

        if (secondTaskId != INVALID_TASK_ID) {
            if (initialShortcut != null) {
                return SPLIT_SHORTCUT_TASK
            }
            if (initialPendingIntent != null) {
                return SPLIT_PENDINGINTENT_TASK
            }
        }

        // All task+shortcut combinations are handled above, only launch left is with multiple
        // intents (and respective shortcut infos, if necessary)
        if (initialPendingIntent != null && secondPendingIntent != null) {
            return SPLIT_PENDINGINTENT_PENDINGINTENT
        }
        throw IllegalStateException("Unidentified split launch type")
    }

    @SplitLaunchType
    private fun getFullscreenLaunchType(): Int {
        if (initialTaskId != INVALID_TASK_ID) {
            return SPLIT_SINGLE_TASK_FULLSCREEN
        }

        if (initialShortcut != null) {
            return SPLIT_SINGLE_SHORTCUT_FULLSCREEN
        }

        if (initialPendingIntent != null) {
            return SPLIT_SINGLE_INTENT_FULLSCREEN
        }
        throw IllegalStateException("Unidentified fullscreen launch type")
    }

    data class SplitLaunchData(
            @SplitLaunchType
            val splitLaunchType: Int,
            var initialTaskId: Int = INVALID_TASK_ID,
            var secondTaskId: Int = INVALID_TASK_ID,
            var initialPendingIntent: PendingIntent? = null,
            var secondPendingIntent: PendingIntent? = null,
            var initialUserId: Int = -1,
            var secondUserId: Int = -1,
            var initialShortcut: ShortcutInfo? = null,
            var secondShortcut: ShortcutInfo? = null,
            var itemInfo: ItemInfo? = null,
            var splitEvent: EventEnum? = null,
            val initialStagePosition: Int = STAGE_POSITION_UNDEFINED
    )

    /**
     * @return `true` if first task has been selected and waiting for the second task to be
     * chosen
     */
    fun isSplitSelectActive(): Boolean {
        return isInitialTaskIntentSet() && !isSecondTaskIntentSet()
    }

    /**
     * @return `true` if the first and second task have been chosen and split is waiting to
     * be launched
     */
    fun isBothSplitAppsConfirmed(): Boolean {
        return isInitialTaskIntentSet() && isSecondTaskIntentSet()
    }

    private fun isInitialTaskIntentSet(): Boolean {
        return initialTaskId != INVALID_TASK_ID || initialIntent != null
    }

    fun getInitialTaskId(): Int {
        return initialTaskId
    }

    fun getSecondTaskId(): Int {
        return secondTaskId
    }

    private fun isSecondTaskIntentSet(): Boolean {
        return secondTaskId != INVALID_TASK_ID || secondIntent != null
                || secondPendingIntent != null
    }

    fun resetState() {
        initialStagePosition = STAGE_POSITION_UNDEFINED
        initialTaskId = INVALID_TASK_ID
        secondTaskId = INVALID_TASK_ID
        initialUser = null
        secondUser = null
        initialIntent = null
        secondIntent = null
        secondPendingIntent = null
        itemInfo = null
        splitEvent = null
        initialShortcut = null
        secondShortcut = null
    }

    fun dump(prefix: String, writer: PrintWriter) {
        writer.println("$prefix ${javaClass.simpleName}")
        writer.println("$prefix\tinitialStagePosition= $initialStagePosition")
        writer.println("$prefix\tinitialTaskId= $initialTaskId")
        writer.println("$prefix\tsecondTaskId= $secondTaskId")
        writer.println("$prefix\tinitialUser= $initialUser")
        writer.println("$prefix\tsecondUser= $secondUser")
        writer.println("$prefix\tinitialIntent= $initialIntent")
        writer.println("$prefix\tsecondIntent= $secondIntent")
        writer.println("$prefix\tsecondPendingIntent= $secondPendingIntent")
        writer.println("$prefix\titemInfo= $itemInfo")
        writer.println("$prefix\tsplitEvent= $splitEvent")
        writer.println("$prefix\tinitialShortcut= $initialShortcut")
        writer.println("$prefix\tsecondShortcut= $secondShortcut")
    }
}
