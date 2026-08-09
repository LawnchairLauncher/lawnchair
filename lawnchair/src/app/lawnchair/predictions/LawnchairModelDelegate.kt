package app.lawnchair.predictions

import android.content.Context
import androidx.annotation.WorkerThread
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.model.PredictedItemFactory
import com.android.launcher3.model.QuickstepModelDelegate
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Suppress("VisibleForTests")
class LawnchairModelDelegate @Inject constructor(
    @ApplicationContext context: Context,
    private val idp: InvariantDeviceProfile,
    userCache: UserCache,
    itemParserFactory: PredictedItemFactory.Factory,
    @Named("ICONS_DB") dbFileName: String?,
) : QuickstepModelDelegate(context, idp, userCache, itemParserFactory, dbFileName) {

    private val prefs2: PreferenceManager2 by lazy { PreferenceManager2.getInstance(context) }
    private val lawnchairPredictor: LawnchairAppPredictor by lazy {
        LawnchairAppPredictor(context)
    }

    private var prefObserverScope: CoroutineScope? = null

    @WorkerThread
    override fun workspaceLoadComplete() {
        super.workspaceLoadComplete()
        registerPredictionModeChanged()
    }

    @WorkerThread
    override fun modelLoadComplete() {
        super.modelLoadComplete()
        syncPredictor()
    }

    @WorkerThread
    override fun destroy() {
        unregisterPredictionModeChanged()
        lawnchairPredictor.unregister()
        super.destroy()
    }

    @WorkerThread
    override fun recreatePredictors() {
        lawnchairPredictor.unregister()
        when (currentPredictionMode()) {
            SystemPredictor -> super.recreatePredictors()
            LawnchairPredictor -> activateLawnchairPredictor()
            NoPredictor -> clearPredictions()
        }
    }

    @WorkerThread
    override fun validateData() {
        super.validateData()
        syncPredictor()
    }

    @WorkerThread
    private fun syncPredictor() {
        when (currentPredictionMode()) {
            SystemPredictor -> {
                mAllPredictionAppsState.requestPredictionUpdate()
                mWidgetsRecommendationState.requestPredictionUpdate()
            }

            LawnchairPredictor -> updateLawnchairPredictions()

            NoPredictor -> clearPredictions()
        }
    }

    private fun currentPredictionMode(): PredictionMode {
        if (!prefs2.enableGlobalPrediction.firstCached()) {
            return NoPredictor
        }
        return prefs2.predictionMode.firstCached()
    }

    private fun destroyPredictors() {
        mAllPredictionAppsState.destroyPredictor()
        mHotseatPredictionState.destroyPredictor()
        mWidgetsRecommendationState.destroyPredictor()
    }

    private fun activateLawnchairPredictor() {
        destroyPredictors()
        if (!mActive) return

        lawnchairPredictor.register(
            model = mModel,
            dataModel = mDataModel,
            allAppsState = mAllPredictionAppsState,
            hotseatState = mHotseatPredictionState,
            widgetsState = mWidgetsRecommendationState,
            idp = idp,
        )
        updateLawnchairPredictions()
    }

    private fun updateLawnchairPredictions() {
        lawnchairPredictor.updates(
            model = mModel,
            dataModel = mDataModel,
            allAppsState = mAllPredictionAppsState,
            hotseatState = mHotseatPredictionState,
            widgetsState = mWidgetsRecommendationState,
            idp = idp,
        )
    }

    private fun clearPredictions() {
        destroyPredictors()
        lawnchairPredictor.empty(
            model = mModel,
            allAppsState = mAllPredictionAppsState,
            hotseatState = mHotseatPredictionState,
            widgetsState = mWidgetsRecommendationState,
        )
    }

    private fun registerPredictionModeChanged() {
        prefObserverScope?.cancel()
        val observerScope = CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default +
                CoroutineName("LawnchairModelDelegate.predictionModeObserver"),
        )
        prefObserverScope = observerScope
        prefs2.enableGlobalPrediction.get()
            .combine(prefs2.predictionMode.get()) { enabled, mode ->
                if (enabled) mode else NoPredictor
            }
            .distinctUntilChanged()
            .drop(1) // Skip
            .onEach { MODEL_EXECUTOR.execute { recreatePredictors() } }
            .launchIn(observerScope)
    }

    private fun unregisterPredictionModeChanged() {
        prefObserverScope?.cancel()
        prefObserverScope = null
    }
}
