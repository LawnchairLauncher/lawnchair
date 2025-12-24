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
import com.android.launcher3.LauncherUiState
import com.android.launcher3.SplitSelectTask
import com.android.launcher3.logging.StatsLogManager.EventEnum
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition
import com.android.launcher3.util.SplitConfigurationOptions.getOppositeStagePosition
import com.android.quickstep.util.SplitSelectDataHolder.Companion.SplitLaunchType
import java.io.PrintWriter

/**
 * Holds/transforms/signs/seals/delivers information for the transient state of the user selecting a
 * first app to start split with and then choosing a second app. This class DOES NOT associate
 * itself with drag-and-drop split screen starts because they come from the bad part of town.
 *
 * After setting the correct fields for initial/second.* variables, this converts them into the
 * correct [PendingIntent] and [ShortcutInfo] objects where applicable and sends the necessary data
 * back via [getSplitLaunchData]. Note: there should be only one "initial" field and one "second"
 * field set, with the rest remaining null. (Exception: [Intent] and [UserHandle] are always passed
 * in together as a set, and are converted to a single [PendingIntent] or
 * [ShortcutInfo]+[PendingIntent] before launch.)
 *
 * [SplitLaunchType] indicates the type of tasks/apps/intents being launched given the provided
 * state
 */
class SplitSelectDataHolder(var context: Context?) {
    val TAG = SplitSelectDataHolder::class.simpleName

    /**
     * Order of the constant indicates the order of which task/app was selected. Ex.
     * SPLIT_TASK_SHORTCUT means primary split app identified by task, secondary is shortcut
     * SPLIT_SHORTCUT_TASK means primary split app is determined by shortcut, secondary is task
     */
    companion object {
        @IntDef(
            SPLIT_TASK_TASK,
            SPLIT_TASK_PENDINGINTENT,
            SPLIT_TASK_SHORTCUT,
            SPLIT_PENDINGINTENT_TASK,
            SPLIT_PENDINGINTENT_PENDINGINTENT,
            SPLIT_SHORTCUT_TASK,
            SPLIT_SINGLE_TASK_FULLSCREEN,
            SPLIT_SINGLE_INTENT_FULLSCREEN,
            SPLIT_SINGLE_SHORTCUT_FULLSCREEN,
        )
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

    @StagePosition private var initialStagePosition: Int = STAGE_POSITION_UNDEFINED
    private var itemInfo: ItemInfo? = null
    private var secondItemInfo: ItemInfo? = null
    private var splitEvent: EventEnum? = null

    private var launcherUiState: LauncherUiState? = null

    private var initialTask = SplitSelectTask()
        set(value) {
            field = value
            launcherUiState?.setSplitSelectInitialTask(value)
        }

    private var secondTask = SplitSelectTask()
        set(value) {
            field = value
            launcherUiState?.setSplitSelectSecondTask(value)
        }

    private var widgetSecondIntent: Intent? = null
    private var initialUser: UserHandle? = null
    private var secondUser: UserHandle? = null
    private var initialShortcut: ShortcutInfo? = null
    private var secondShortcut: ShortcutInfo? = null

    fun onDestroy() {
        context = null
    }

    fun setLauncherUiState(launcherUiState: LauncherUiState) {
        this.launcherUiState = launcherUiState
    }

    /**
     * @param alreadyRunningTask if set to [android.app.ActivityTaskManager.INVALID_TASK_ID]
     *   then @param intent will be used to launch the initial task
     * @param intent will be ignored if @param alreadyRunningTask is set
     */
    fun setInitialTaskSelect(
        intent: Intent?,
        @StagePosition stagePosition: Int,
        itemInfo: ItemInfo?,
        splitEvent: EventEnum?,
        alreadyRunningTask: Int,
    ) {
        if (alreadyRunningTask != INVALID_TASK_ID) {
            initialTask = initialTask.copy(taskId = alreadyRunningTask)
        } else {
            initialTask = initialTask.copy(intent = intent!!)
            initialUser = itemInfo!!.user
        }
        setInitialData(stagePosition, splitEvent, itemInfo)
    }

    /**
     * To be called after first task selected from using a split shortcut from the fullscreen
     * running app.
     */
    fun setInitialTaskSelect(
        info: RunningTaskInfo,
        @StagePosition stagePosition: Int,
        itemInfo: ItemInfo?,
        splitEvent: EventEnum?,
    ) {
        initialTask = initialTask.copy(taskId = info.taskId)
        setInitialData(stagePosition, splitEvent, itemInfo)
    }

    private fun setInitialData(
        @StagePosition stagePosition: Int,
        event: EventEnum?,
        item: ItemInfo?,
    ) {
        itemInfo = item
        initialStagePosition = stagePosition
        splitEvent = event
    }

    /**
     * To be called as soon as user selects the second task (even if animations aren't complete)
     *
     * @param taskId The second task that will be launched.
     */
    fun setSecondTask(taskId: Int, itemInfo: ItemInfo) {
        secondTask = secondTask.copy(taskId = taskId)
        secondItemInfo = itemInfo
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete)
     *
     * @param intent The second intent that will be launched.
     * @param user The user of that intent.
     */
    fun setSecondTask(intent: Intent, user: UserHandle, itemInfo: ItemInfo) {
        secondTask = secondTask.copy(intent = intent)
        secondUser = user
        secondItemInfo = itemInfo
    }

    /**
     * To be called as soon as user selects the second app (even if animations aren't complete) Sets
     * [secondUser] from that of the pendingIntent
     *
     * @param pendingIntent The second PendingIntent that will be launched.
     */
    fun setSecondTask(pendingIntent: PendingIntent, itemInfo: ItemInfo) {
        secondTask = secondTask.copy(pendingIntent = pendingIntent)
        secondUser = pendingIntent.creatorUserHandle
        secondItemInfo = itemInfo
    }

    /**
     * Similar to [setSecondTask] except this is to be called for widgets which can pass through an
     * extra intent from their RemoteResponse. See
     * [android.widget.RemoteViews.RemoteResponse.getLaunchOptions].first
     */
    fun setSecondWidget(pendingIntent: PendingIntent, widgetIntent: Intent?, itemInfo: ItemInfo) {
        setSecondTask(pendingIntent, itemInfo)
        widgetSecondIntent = widgetIntent
    }

    private fun getShortcutInfo(intent: Intent?, user: UserHandle?): ShortcutInfo? {
        val intentPackage = intent?.getPackage() ?: return null
        val shortcutId = intent.getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID) ?: return null
        try {
            val context: Context =
                if (user != null) {
                    context!!.createPackageContextAsUser(intentPackage, 0 /* flags */, user)
                } else {
                    context!!.createPackageContext(intentPackage, 0 /* *flags */)
                }
            return ShortcutInfo.Builder(context, shortcutId).build()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to create a ShortcutInfo for " + intent.getPackage())
        }
        return null
    }

    /** Converts intents to pendingIntents, associating the [user] with the intent if provided */
    private fun getPendingIntent(intent: Intent?, user: UserHandle?): PendingIntent? {
        if (intent != initialTask.intent && intent != secondTask.intent) {
            throw IllegalStateException("Invalid intent to convert to PendingIntent")
        }

        return if (intent == null) {
            null
        } else if (user != null) {
            PendingIntent.getActivityAsUser(
                context,
                0,
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT,
                null /* options */,
                user,
            )
        } else {
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT,
            )
        }
    }

    /**
     * @return [SplitLaunchData] with the necessary fields populated as determined by
     *   [SplitLaunchData.splitLaunchType]. This is to be used for launching splitscreen
     */
    fun getSplitLaunchData(): SplitLaunchData {
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
    fun getFullscreenLaunchData(): SplitLaunchData {
        // Convert all intents to shortcut infos to determine if we launch shortcut or intent
        convertIntentsToFinalTypes()
        val splitLaunchType = getFullscreenLaunchType()

        return generateSplitLaunchData(splitLaunchType)
    }

    private fun generateSplitLaunchData(@SplitLaunchType splitLaunchType: Int): SplitLaunchData {
        return SplitLaunchData(
            splitLaunchType,
            initialTask,
            secondTask,
            widgetSecondIntent,
            initialUser?.identifier ?: -1,
            secondUser?.identifier ?: -1,
            initialShortcut,
            secondShortcut,
            itemInfo,
            splitEvent,
            initialStagePosition,
        )
    }

    /**
     * Converts our [initialTask.intent] and [secondTask.intent] into shortcuts and pendingIntents,
     * if possible.
     *
     * Note that both [initialTask.intent] and [secondTask.intent] will be nullified on method
     * return
     *
     * One caveat is that if [secondTask.pendingIntent] is set, we will use that and *not* attempt
     * to convert [secondTask.intent]. This also leaves [widgetSecondIntent] untouched.
     */
    private fun convertIntentsToFinalTypes() {
        initialShortcut = getShortcutInfo(initialTask.intent, initialUser)
        initialTask =
            initialTask.copy(
                intent = null,
                pendingIntent = getPendingIntent(initialTask.intent, initialUser),
            )

        // Only one of the two is currently allowed (secondPendingIntent directly set for widgets)
        if (secondTask.intent != null && secondTask.pendingIntent != null) {
            throw IllegalStateException("Both secondIntent and secondPendingIntent non-null")
        }
        // If secondPendingIntent already set, no need to convert. Prioritize using that
        if (secondTask.pendingIntent != null) {
            secondTask = secondTask.copy(intent = null)
            return
        }

        secondShortcut = getShortcutInfo(secondTask.intent, secondUser)
        secondTask =
            secondTask.copy(
                intent = null,
                pendingIntent = getPendingIntent(secondTask.intent, secondUser),
            )
    }

    /**
     * Only valid data fields at this point should be tasks, shortcuts, or pendingIntents Intents
     * need to be converted in [convertIntentsToFinalTypes] prior to calling this method
     */
    @VisibleForTesting
    @SplitLaunchType
    fun getSplitLaunchType(): Int {
        if (initialTask.intent != null || secondTask.intent != null) {
            throw IllegalStateException("Intents need to be converted")
        }

        // Prioritize task launches first
        if (initialTask.taskId != INVALID_TASK_ID) {
            if (secondTask.taskId != INVALID_TASK_ID) {
                return SPLIT_TASK_TASK
            }
            if (secondShortcut != null) {
                return SPLIT_TASK_SHORTCUT
            }
            if (secondTask.pendingIntent != null) {
                return SPLIT_TASK_PENDINGINTENT
            }
        }

        if (secondTask.taskId != INVALID_TASK_ID) {
            if (initialShortcut != null) {
                return SPLIT_SHORTCUT_TASK
            }
            if (initialTask.pendingIntent != null) {
                return SPLIT_PENDINGINTENT_TASK
            }
        }

        // All task+shortcut combinations are handled above, only launch left is with multiple
        // intents (and respective shortcut infos, if necessary)
        if (initialTask.pendingIntent != null && secondTask.pendingIntent != null) {
            return SPLIT_PENDINGINTENT_PENDINGINTENT
        }
        throw IllegalStateException("Unidentified split launch type")
    }

    @SplitLaunchType
    private fun getFullscreenLaunchType(): Int {
        if (initialTask.taskId != INVALID_TASK_ID) {
            return SPLIT_SINGLE_TASK_FULLSCREEN
        }

        if (initialShortcut != null) {
            return SPLIT_SINGLE_SHORTCUT_FULLSCREEN
        }

        if (initialTask.pendingIntent != null) {
            return SPLIT_SINGLE_INTENT_FULLSCREEN
        }
        throw IllegalStateException("Unidentified fullscreen launch type")
    }

    data class SplitLaunchData(
        @SplitLaunchType val splitLaunchType: Int,
        val initialTask: SplitSelectTask = SplitSelectTask(),
        val secondTask: SplitSelectTask = SplitSelectTask(),
        var widgetSecondIntent: Intent? = null,
        var initialUserId: Int = -1,
        var secondUserId: Int = -1,
        var initialShortcut: ShortcutInfo? = null,
        var secondShortcut: ShortcutInfo? = null,
        var itemInfo: ItemInfo? = null,
        var splitEvent: EventEnum? = null,
        val initialStagePosition: Int = STAGE_POSITION_UNDEFINED,
    )

    /**
     * @return `true` if first task has been selected and waiting for the second task to be chosen
     */
    fun isSplitSelectActive(): Boolean {
        return initialTask.isIntentSet && !secondTask.isIntentSet
    }

    /**
     * @return `true` if the first and second task have been chosen and split is waiting to be
     *   launched
     */
    fun isBothSplitAppsConfirmed(): Boolean {
        return initialTask.isIntentSet && secondTask.isIntentSet
    }

    fun getInitialTaskId(): Int {
        return initialTask.taskId
    }

    fun getSecondTaskId(): Int {
        return secondTask.taskId
    }

    fun getSplitEvent(): EventEnum? {
        return splitEvent
    }

    fun getInitialStagePosition(): Int {
        return initialStagePosition
    }

    fun getItemInfo(): ItemInfo? {
        return itemInfo
    }

    fun getSecondItemInfo(): ItemInfo? {
        return secondItemInfo
    }

    fun resetState() {
        initialStagePosition = STAGE_POSITION_UNDEFINED
        initialTask = SplitSelectTask()
        secondTask = SplitSelectTask()
        initialUser = null
        secondUser = null
        itemInfo = null
        splitEvent = null
        initialShortcut = null
        secondShortcut = null
    }

    fun dump(prefix: String, writer: PrintWriter) {
        writer.println("$prefix SplitSelectDataHolder")
        writer.println("$prefix\tinitialStagePosition= $initialStagePosition")
        writer.println("$prefix\tinitialTaskId= ${initialTask.taskId}")
        writer.println("$prefix\tsecondTaskId= ${secondTask.taskId}")
        writer.println("$prefix\tinitialUser= $initialUser")
        writer.println("$prefix\tsecondUser= $secondUser")
        writer.println("$prefix\tinitialIntent= ${initialTask.intent}")
        writer.println("$prefix\tsecondIntent= ${secondTask.intent}")
        writer.println("$prefix\tsecondPendingIntent= ${secondTask.pendingIntent}")
        writer.println("$prefix\titemInfo= $itemInfo")
        writer.println("$prefix\tsplitEvent= $splitEvent")
        writer.println("$prefix\tinitialShortcut= $initialShortcut")
        writer.println("$prefix\tsecondShortcut= $secondShortcut")
    }
}
