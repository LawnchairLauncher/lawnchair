package app.lawnchair.predictions

import android.Manifest
import android.app.prediction.AppPredictionManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import com.android.launcher3.R

sealed class PredictionMode(
    @StringRes val nameResourceId: Int,
) {
    abstract override fun toString(): String

    abstract fun isAvailable(context: Context): Boolean

    companion object {
        fun fromString(value: String): PredictionMode = when (value) {
            "system" -> SystemPredictor
            "lawnchair" -> LawnchairPredictor
            else -> NoPredictor
        }

        fun values(): List<PredictionMode> = listOf(
            SystemPredictor,
            LawnchairPredictor,
            NoPredictor,
        )
    }
}

object SystemPredictor : PredictionMode(R.string.prediction_mode_system) {

    override fun toString() = "system"

    override fun isAvailable(context: Context): Boolean {
        if (context.getSystemService(AppPredictionManager::class.java) == null) return false
        return context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
            PackageManager.PERMISSION_GRANTED // Intended, it needs to be granted through ADB/System only, not through special app appops.
    }
}

object LawnchairPredictor : PredictionMode(R.string.prediction_mode_lawnchair) {

    override fun toString() = "lawnchair"

    override fun isAvailable(context: Context) = true
}

object NoPredictor : PredictionMode(R.string.prediction_mode_none) {

    override fun toString() = "none"

    override fun isAvailable(context: Context) = true
}
