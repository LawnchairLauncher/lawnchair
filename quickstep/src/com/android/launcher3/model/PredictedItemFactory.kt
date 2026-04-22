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

package com.android.launcher3.model

import android.app.prediction.AppPredictionContext
import android.app.prediction.AppPredictionManager
import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.app.prediction.AppTargetEvent
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.VisibleForTesting
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CacheLookupFlag
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PersistedItemArray
import com.android.launcher3.util.PersistedItemArray.ItemFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** [PersistedItemArray.ItemFactory] to parse predicted items */
class PredictedItemFactory
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val pmHelper: PackageManagerHelper,
    private val userCache: UserCache,
    private val apiWrapper: ApiWrapper,
    private val iconCache: IconCache,
    @Assisted private val maxItemCount: Int,
    @Assisted private val predictorState: PredictorState,
) : ItemFactory<ItemInfo> {

    private val quietModeCache = mutableMapOf<UserHandle, Boolean>()
    // Number of items persisted can be different than what is needed if the grid changed between
    // the two operations
    private var readCount = 0

    override fun createInfo(itemType: Int, user: UserHandle, intent: Intent): ItemInfo? {
        if (readCount >= maxItemCount) {
            return null
        }
        when (itemType) {
            Favorites.ITEM_TYPE_APPLICATION -> {
                val lai =
                    context
                        .getSystemService(LauncherApps::class.java)
                        ?.resolveActivity(intent, user) ?: return null
                val info =
                    AppInfo(
                        lai,
                        userCache.getUserInfo(user),
                        apiWrapper,
                        pmHelper,
                        quietModeCache.getOrPut(user) {
                            context
                                .getSystemService(UserManager::class.java)
                                ?.isQuietModeEnabled(user) ?: true
                        },
                    )
                info.container = predictorState.containerId
                iconCache.getTitleAndIcon(info, lai, predictorState.lookupFlag)
                readCount++
                return info.makeWorkspaceItem(context)
            }

            Favorites.ITEM_TYPE_DEEP_SHORTCUT -> {
                val key = ShortcutKey.fromIntent(intent, user) ?: return null
                val si: ShortcutInfo =
                    key.buildRequest(context).query(ShortcutRequest.PINNED).getOrNull(0)
                        ?: return null
                val wii = WorkspaceItemInfo(si, context)
                wii.container = predictorState.containerId
                iconCache.getShortcutIcon(wii, si)
                readCount++
                return wii
            }
        }
        return null
    }

    @AssistedFactory
    interface Factory {
        fun newParser(maxItemCount: Int, predictorState: PredictorState): PredictedItemFactory
    }
}

/** Class to manage predicted items for a particular prediction type */
class PredictorState(
    @JvmField val containerId: Int,
    storageName: String,
    @JvmField val lookupFlag: CacheLookupFlag,
) {

    @JvmField val storage: PersistedItemArray<ItemInfo> = PersistedItemArray(storageName)

    @VisibleForTesting var predictor: AppPredictor? = null

    private var lastTargets: List<AppTarget> = emptyList()

    /** Creates and registers a predictor, and destroys any previously created predictor */
    fun registerPredictor(
        ctx: Context,
        predictionContext: AppPredictionContext,
        model: LauncherModel,
        taskFactory: (PredictorState, List<AppTarget>) -> ModelUpdateTask,
    ) {
        destroyPredictor()

        val apm = ctx.getSystemService(AppPredictionManager::class.java) ?: return
        lastTargets = emptyList()

        predictor =
            apm.createAppPredictionSession(predictionContext).apply {
                registerPredictionUpdates(MODEL_EXECUTOR) {
                    val oldTargets = lastTargets
                    lastTargets = it

                    // If no diff, skip
                    if (
                        oldTargets.size != lastTargets.size ||
                            oldTargets.zip(lastTargets).any { (a1, a2) ->
                                !areAppTargetsSame(a1, a2)
                            }
                    ) {
                        model.enqueueModelUpdateTask(taskFactory.invoke(this@PredictorState, it))
                    }
                }
                requestPredictionUpdate()
            }
    }

    /** Destroys a previously created predictor */
    fun destroyPredictor() {
        predictor?.destroy()
        predictor = null
    }

    /** see [AppPredictor.requestPredictionUpdate] */
    fun requestPredictionUpdate() = predictor?.requestPredictionUpdate()

    /** see [AppPredictor.notifyAppTargetEvent] */
    fun notifyAppTargetEvent(event: AppTargetEvent) = predictor?.notifyAppTargetEvent(event)

    /** Compares two targets for the properties which we care about */
    private fun areAppTargetsSame(t1: AppTarget, t2: AppTarget): Boolean {
        if (
            (t1.packageName != t2.packageName) ||
                (t1.user != t2.user) ||
                (t1.className != t2.className)
        ) {
            return false
        }
        return t1.shortcutInfo?.id == t2.shortcutInfo?.id
    }
}
