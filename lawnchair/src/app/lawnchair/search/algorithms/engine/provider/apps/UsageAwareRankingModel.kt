package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import android.util.Log
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import kotlin.math.exp

object UsageAwareRankingModel {

    const val TAG: String = "UsageAwareRankingModel"
    const val DEBUG: Boolean = false

    // Higher value = sharper drop-off for lower text search results
    const val LOW_TEXT_SCORE_PENALTY_COEFF = 1.0f

    // Higher value = steeper score increase with usage score increase
    const val USAGE_BOOST_COEFF = 0.5f

    // Higher value = higher asymptotic exponential curve for text score
    const val FUZZ_CURVE_STEEPNESS_COEFF = 2.0f

    private var currentLowTextPenaltyCoeff: Float = LOW_TEXT_SCORE_PENALTY_COEFF
    private var currentUsageBoostCoeff: Float = USAGE_BOOST_COEFF
    private var currentFuzzCurveSteepnessCoeff = FUZZ_CURVE_STEEPNESS_COEFF

    fun updateCoeffs(context: Context) {
        val prefs = PreferenceManager2.getInstance(context)

        currentLowTextPenaltyCoeff = prefs.usageAwareRankingModelLowTextPenaltyCoeff.firstCached()
        currentUsageBoostCoeff = prefs.usageAwareRankingModelUsageBoostCoeff.firstCached()
        currentFuzzCurveSteepnessCoeff =
            prefs.usageAwareRankingModelFuzzCurveSteepnessCoeff.firstCached()

        if (DEBUG) {
            Log.d(
                TAG,
                "updateCoeffs(): LowTextPenaltyCoeff=%f UsageBoostCoeff=%f FuzzCurveSteepnessCoeff=%f".format(
                    currentLowTextPenaltyCoeff,
                    currentUsageBoostCoeff,
                    currentFuzzCurveSteepnessCoeff,
                ),
            )
        }
    }

    // To match the Matlab model (and simplify the code a bit), shorter parameter names were picked:
    // F is the text score, U is the usage score
    fun run(F: Float, U: Float): Float {
        val boostedF =
            (1 - exp(-currentFuzzCurveSteepnessCoeff * F)) / (1 - exp(-currentFuzzCurveSteepnessCoeff))

        val calculated = boostedF * exp((boostedF - 1) * currentLowTextPenaltyCoeff + (currentUsageBoostCoeff * boostedF * U)) / exp(currentUsageBoostCoeff)

        if (DEBUG) Log.d(TAG, "run(F=%f, U=%f) -> %f".format(F, U, calculated))

        return calculated
    }
}
