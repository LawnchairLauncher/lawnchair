package app.mica.predictions

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import app.mica.preferences2.PreferenceManager2
import app.mica.preferences2.firstCached
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
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
class MicaModelDelegate @Inject constructor(
    @ApplicationContext context: Context,
    private val idp: InvariantDeviceProfile,
    userCache: UserCache,
    itemParserFactory: PredictedItemFactory.Factory,
    @Named("ICONS_DB") dbFileName: String?,
) : QuickstepModelDelegate(context, idp, userCache, itemParserFactory, dbFileName) {

    private val prefs2: PreferenceManager2 by lazy { PreferenceManager2.getInstance(context) }
    private val micaPredictor: MicaAppPredictor by lazy {
        MicaAppPredictor(context)
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
        micaPredictor.unregister()
        super.destroy()
    }

    @WorkerThread
    override fun recreatePredictors() {
        micaPredictor.unregister()
        when (currentPredictionMode()) {
            SystemPredictor -> super.recreatePredictors()

            MicaPredictor -> if (Utilities.ATLEAST_Q) {
                activateMicaPredictor()
            } else {
                clearPredictions()
            }

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

            MicaPredictor -> if (Utilities.ATLEAST_Q) {
                updateMicaPredictions()
            } else {
                clearPredictions()
            }

            NoPredictor -> clearPredictions()
        }
    }

    private fun currentPredictionMode(): PredictionMode {
        // All predictor targets can't be run on device older than Q
        if (!Utilities.ATLEAST_Q || !prefs2.enableGlobalPrediction.firstCached()) {
            return NoPredictor
        }
        return prefs2.predictionMode.firstCached()
    }

    private fun destroyPredictors() {
        mAllPredictionAppsState.destroyPredictor()
        mHotseatPredictionState.destroyPredictor()
        mWidgetsRecommendationState.destroyPredictor()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun activateMicaPredictor() {
        destroyPredictors()
        if (!mActive) return

        micaPredictor.register(
            model = mModel,
            dataModel = mDataModel,
            allAppsState = mAllPredictionAppsState,
            hotseatState = mHotseatPredictionState,
            widgetsState = mWidgetsRecommendationState,
            idp = idp,
        )
        updateMicaPredictions()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateMicaPredictions() {
        micaPredictor.updates(
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
        micaPredictor.empty(
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
                CoroutineName("MicaModelDelegate.predictionModeObserver"),
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
