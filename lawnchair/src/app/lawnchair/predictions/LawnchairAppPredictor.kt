package app.lawnchair.predictions

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.WorkerThread
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherModel
import com.android.launcher3.logger.LauncherAtom.ItemInfo
import com.android.launcher3.logging.StatsLogManager.EventEnum
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_DRAGDROP
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_CONVERTED_TO_ICON
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_PREDICTION_PINNED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_REMOVE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROP_FOLDER_CREATED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_LEFT
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_RIGHT
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGET_ADD_BUTTON_TAP
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.PredictionUpdateTask
import com.android.launcher3.model.PredictorState
import com.android.launcher3.model.WidgetsPredictionUpdateTask
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.quickstep.logging.StatsLogCompatManager
import kotlin.time.Duration.Companion.hours

private val PRUNE_INTERVAL = 1.hours

/**
 * Listens to app launching events from [StatsLogCompatManager.StatsLogConsumer] and send
 * them to [LawnchairPredictionStore] depending on where the app is launched from.
 *
 * Call [register] to start receiving events, and [unregister] when the ModelDelegate is destroyed.
 *
 * Delegates event resolution to [PredictionEventResolver], usage-stats ranking
 * to [UsageStatsRanker], and target compilation to [LawnchairPredictionEngine].
 */
class LawnchairAppPredictor(private val context: Context) : StatsLogCompatManager.StatsLogConsumer {

    private val userCache = UserCache.INSTANCE.get(context)
    private val predictionManager = LawnchairPredictionManager.getInstance(context)
    private val hotseatStore = predictionManager.hotseatStore
    private val allAppsStore = predictionManager.allAppsStore
    private val dismissedAppsStore = predictionManager.dismissedAppsStore

    private val eventResolver = PredictionEventResolver(userCache)
    private val usageStatsRanker = UsageStatsRanker(context)
    private val predictionEngine = LawnchairPredictionEngine(context, usageStatsRanker)

    private var dismissedApps: MutableSet<String> = loadDismissedApps()
    private var lastPruneTime = 0L

    private var activeModel: LauncherModel? = null
    private var activeDataModel: BgDataModel? = null
    private var activeAllAppsState: PredictorState? = null
    private var activeHotseatState: PredictorState? = null
    private var activeWidgetsState: PredictorState? = null
    private var activeIdp: InvariantDeviceProfile? = null

    fun register(
        model: LauncherModel,
        dataModel: BgDataModel,
        allAppsState: PredictorState,
        hotseatState: PredictorState,
        widgetsState: PredictorState,
        idp: InvariantDeviceProfile,
    ) {
        activeModel = model
        activeDataModel = dataModel
        activeAllAppsState = allAppsState
        activeHotseatState = hotseatState
        activeWidgetsState = widgetsState
        activeIdp = idp
        StatsLogCompatManager.LOGS_CONSUMER.add(this)
    }

    fun unregister() {
        StatsLogCompatManager.LOGS_CONSUMER.remove(this)
        activeModel = null
        activeDataModel = null
        activeAllAppsState = null
        activeHotseatState = null
        activeWidgetsState = null
        activeIdp = null
    }

    @WorkerThread
    override fun consume(event: EventEnum?, atomInfo: ItemInfo?) {
        MODEL_EXECUTOR.execute { handleEvent(event, atomInfo) }
    }

    @WorkerThread
    private fun handleEvent(event: EventEnum?, atomInfo: ItemInfo?) {
        val resolvedEvent = eventResolver.resolve(atomInfo)

        val didRecord = when {
            event == null || resolvedEvent == null -> false
            isDismissEvent(event) -> recordDismissEvent(event, resolvedEvent)
            isLaunchEvent(event) -> recordLaunchEvent(resolvedEvent)
            else -> false
        }

        if (didRecord || isLayoutMutationEvent(event)) {
            activePredictionState()?.let { state ->
                updates(
                    model = state.model,
                    dataModel = state.dataModel,
                    allAppsState = state.allAppsState,
                    hotseatState = state.hotseatState,
                    widgetsState = state.widgetsState,
                    idp = state.idp,
                )
            }
        }
    }

    @WorkerThread
    fun updates(
        model: LauncherModel,
        dataModel: BgDataModel,
        allAppsState: PredictorState,
        hotseatState: PredictorState,
        widgetsState: PredictorState,
        idp: InvariantDeviceProfile,
    ) {
        val pm = context.packageManager
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPruneTime > PRUNE_INTERVAL.inWholeMilliseconds) {
            prunePredictionStores(pm)
            lastPruneTime = currentTime
        } else {
            dismissedApps = loadDismissedApps()
        }

        val hotseatRanked = hotseatStore.getRanked()
        val allAppsRanked = allAppsStore.getRanked()
        val fallbackRanked = predictionEngine.getFallbackRanked()
        val allAppsCandidateRanked = predictionEngine.mergeRanked(allAppsRanked, hotseatRanked, fallbackRanked)
        val hotseatCandidateRanked = predictionEngine.mergeRanked(hotseatRanked, allAppsRanked, fallbackRanked)
        val widgetCandidateRanked = predictionEngine.mergeRanked(hotseatRanked, allAppsRanked, fallbackRanked)
        val currentDismissedApps = dismissedApps.toSet()
        val occupiedHotseatItems = predictionEngine.getOccupiedHotseatItems(dataModel)
        val excludedHotseatItems = occupiedHotseatItems + currentDismissedApps

        val allAppsTargets = predictionEngine.compileAppTargets(
            allAppsCandidateRanked,
            idp.numDatabaseAllAppsColumns,
            currentDismissedApps,
        )
        val hotseatTargets = predictionEngine.compileAppTargets(
            hotseatCandidateRanked,
            idp.numDatabaseHotseatIcons,
            excludedHotseatItems,
        )
        val widgetTargets = predictionEngine.compileWidgetTargets(
            widgetCandidateRanked,
            dataModel,
        )

        model.enqueueModelUpdateTask(PredictionUpdateTask(allAppsState, allAppsTargets))
        model.enqueueModelUpdateTask(PredictionUpdateTask(hotseatState, hotseatTargets))
        model.enqueueModelUpdateTask(WidgetsPredictionUpdateTask(widgetsState, widgetTargets))
    }

    @WorkerThread
    fun empty(
        model: LauncherModel,
        allAppsState: PredictorState,
        hotseatState: PredictorState,
        widgetsState: PredictorState,
    ) {
        model.enqueueModelUpdateTask(PredictionUpdateTask(allAppsState, emptyList()))
        model.enqueueModelUpdateTask(PredictionUpdateTask(hotseatState, emptyList()))
        model.enqueueModelUpdateTask(WidgetsPredictionUpdateTask(widgetsState, emptyList()))
    }

    private fun recordDismissEvent(
        event: EventEnum,
        resolvedEvent: PredictionEventResolver.ResolvedEvent,
    ): Boolean {
        val componentName = resolvedEvent.componentName ?: return false
        val key = predictionEngine.toStoreKey(componentName, resolvedEvent.user)
        val changed = when (event) {
            LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST,
            LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP,
            -> dismissedApps.add(key)

            LAUNCHER_DISMISS_PREDICTION_UNDO -> dismissedApps.remove(key)

            else -> false
        }
        if (changed) {
            saveDismissedApps(dismissedApps)
        }
        return changed
    }

    private fun recordLaunchEvent(
        resolvedEvent: PredictionEventResolver.ResolvedEvent,
    ): Boolean {
        val componentName = resolvedEvent.componentName ?: return false
        val key = predictionEngine.toStoreKey(componentName, resolvedEvent.user)
        return when {
            resolvedEvent.location.startsWith("workspace") ||
                resolvedEvent.location.startsWith("hotseat") ||
                resolvedEvent.location.startsWith("folder") -> {
                hotseatStore.add(key)
                true
            }

            resolvedEvent.location.startsWith("all-apps") ||
                resolvedEvent.location == "predictions" -> {
                allAppsStore.add(key)
                true
            }

            else -> false
        }
    }

    private fun activePredictionState(): ActivePredictionState? {
        val model = activeModel ?: return null
        val dataModel = activeDataModel ?: return null
        val allAppsState = activeAllAppsState ?: return null
        val hotseatState = activeHotseatState ?: return null
        val widgetsState = activeWidgetsState ?: return null
        val idp = activeIdp ?: return null
        return ActivePredictionState(
            model,
            dataModel,
            allAppsState,
            hotseatState,
            widgetsState,
            idp,
        )
    }

    private fun prunePredictionStores(pm: PackageManager) {
        allAppsStore.pruneUninstalled(pm)
        dismissedAppsStore.pruneUninstalled(pm)
        hotseatStore.pruneUninstalled(pm)
        dismissedApps = loadDismissedApps()
    }

    private fun loadDismissedApps(): MutableSet<String> = dismissedAppsStore.getEntries().toMutableSet()

    private fun saveDismissedApps(dismissedApps: Set<String>) {
        dismissedAppsStore.setEntries(dismissedApps)
    }

    private fun isLaunchEvent(event: EventEnum): Boolean = event == LAUNCHER_APP_LAUNCH_TAP ||
        event == LAUNCHER_APP_LAUNCH_DRAGDROP ||
        event == LAUNCHER_TASK_LAUNCH_SWIPE_DOWN ||
        event == LAUNCHER_TASK_LAUNCH_TAP ||
        event == LAUNCHER_QUICKSWITCH_LEFT ||
        event == LAUNCHER_QUICKSWITCH_RIGHT

    private fun isLayoutMutationEvent(event: EventEnum?): Boolean = event == LAUNCHER_ITEM_DROP_COMPLETED ||
        event == LAUNCHER_ITEM_DROP_FOLDER_CREATED ||
        event == LAUNCHER_FOLDER_CONVERTED_TO_ICON ||
        event == LAUNCHER_ITEM_DROPPED_ON_REMOVE ||
        event == LAUNCHER_HOTSEAT_PREDICTION_PINNED ||
        event == LAUNCHER_WIDGET_ADD_BUTTON_TAP

    private fun isDismissEvent(event: EventEnum): Boolean = event == LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST ||
        event == LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP ||
        event == LAUNCHER_DISMISS_PREDICTION_UNDO

    private data class ActivePredictionState(
        val model: LauncherModel,
        val dataModel: BgDataModel,
        val allAppsState: PredictorState,
        val hotseatState: PredictorState,
        val widgetsState: PredictorState,
        val idp: InvariantDeviceProfile,
    )
}
