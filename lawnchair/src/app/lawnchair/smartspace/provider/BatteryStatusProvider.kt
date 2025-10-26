package app.lawnchair.smartspace.provider

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.BatteryManager
import androidx.core.content.getSystemService
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.util.broadcastReceiverFlow
import app.lawnchair.util.formatShortElapsedTimeRoundingUpToMinutes
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map

class BatteryStatusProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_battery_status,
        { smartspaceBatteryStatus },
    ) {

    private val batteryManager = context.getSystemService<BatteryManager>()
    private val batteryContext = context
    private data class ChargingState(
        var lastBatteryLevel: Int = -1,
        var lastChargingTime: Long = -1,
        val chargingRates: MutableList<Double> = mutableListOf(),
    )
    private val chargingState = ChargingState()
    private fun resetChargingTracking() = chargingState.apply {
        lastBatteryLevel = -1
        lastChargingTime = -1
        chargingRates.clear()
    }

    override val internalTargets =
        broadcastReceiverFlow(context, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).map { _ ->
            val level = getCurrentBatteryLevel()
            updateChargingRate(level)
            val status = getBatteryStatus()
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            if (!charging) resetChargingTracking()
            val full = status == BatteryManager.BATTERY_STATUS_FULL
            listOfNotNull(getSmartspaceTarget(charging, full, level))
        }

    private fun getSmartspaceTarget(
        charging: Boolean,
        full: Boolean,
        level: Int,
    ): SmartspaceTarget? {
        val title = when {
            full || level == 100 -> return null
            charging -> context.getString(R.string.smartspace_battery_charging)
            level <= 15 -> context.getString(R.string.smartspace_battery_low)
            else -> return null
        }
        val score = if (level <= 15) {
            SmartspaceScores.SCORE_LOW_BATTERY
        } else {
            SmartspaceScores.SCORE_BATTERY
        }
        val chargingTimeRemaining = computeChargeTimeRemaining()
        val subtitle = if (charging && chargingTimeRemaining > 0) {
            val chargingTime =
                formatShortElapsedTimeRoundingUpToMinutes(context, chargingTimeRemaining)
            context.getString(
                R.string.battery_charging_percentage_charging_time,
                level,
                chargingTime,
            )
        } else {
            context.getString(R.string.n_percent, level)
        }
        val iconResId = if (charging) R.drawable.ic_charging else R.drawable.ic_battery_low
        return SmartspaceTarget(
            id = "batteryStatus",
            headerAction = SmartspaceAction(
                id = "batteryStatusAction",
                icon = Icon.createWithResource(context, iconResId),
                title = title,
                subtitle = subtitle,
            ),
            score = score,
            featureType = SmartspaceTarget.FeatureType.FEATURE_CALENDAR,
        )
    }

    private fun updateChargingRate(newLevel: Int) {
        val now = System.currentTimeMillis()
        with(chargingState) {
            if (lastBatteryLevel != -1 && lastChargingTime != -1L) {
                val levelDiff = newLevel - lastBatteryLevel
                val mins = (now - lastChargingTime) / 60000.0
                if (levelDiff > 0 && mins >= 0.5) {
                    chargingRates.add(levelDiff / mins)
                    if (chargingRates.size > 5) chargingRates.removeAt(0)
                }
            }
            lastBatteryLevel = newLevel
            lastChargingTime = now
        }
    }
    private fun getSmoothedRate(): Double {
        val rates = chargingState.chargingRates
        if (rates.isEmpty()) return -1.0
        return rates.average()
    }

    private fun computeChargeTimeRemaining(): Long {
        if (!Utilities.ATLEAST_P) return -1
        return runCatching {
            val systemTimeRemaining = batteryManager?.computeChargeTimeRemaining() ?: -1
            if (isReasonableChargingTime(systemTimeRemaining)) return@runCatching systemTimeRemaining
            calculateCustomChargeTimeRemaining()
        }.getOrDefault(-1)
    }

    private fun isReasonableChargingTime(timeRemaining: Long): Boolean {
        if (timeRemaining <= 0) return false
        val hoursRemaining = timeRemaining / (60 * 60 * 1000.0)
        return hoursRemaining in 0.1..8.0
    }

    private fun calculateCustomChargeTimeRemaining(): Long {
        val batteryLevel = getCurrentBatteryLevel()
        if (batteryLevel == -1) return -1
        val rate = getSmoothedRate()
        if (rate > 0) return calculateTimeFromRate(batteryLevel, rate)
        return calculateFallbackTime(batteryLevel)
    }

    private fun calculateTimeFromRate(currentLevel: Int, ratePerMinute: Double): Long {
        val remainingPercentage = 100 - currentLevel
        val adjustedRate = ratePerMinute * when (currentLevel) {
            in 0..60 -> 1.0
            in 61..80 -> 0.85
            in 81..90 -> 0.6
            in 91..97 -> 0.4
            else -> 0.2
        }
        if (adjustedRate <= 0) return -1
        val estimatedMinutes = remainingPercentage / adjustedRate
        val roundedMinutes = when {
            estimatedMinutes <= 10 -> estimatedMinutes.roundToInt()
            estimatedMinutes <= 30 -> ((estimatedMinutes / 5.0).roundToInt() * 5)
            else -> ((estimatedMinutes / 10.0).roundToInt() * 10)
        }.coerceAtLeast(1)
        return (roundedMinutes * 60 * 1000).toLong()
    }

    private fun calculateFallbackTime(currentLevel: Int): Long {
        val remainingPercentage = 100 - currentLevel
        val estimatedMinutes = when {
            isFastCharging() -> {
                when (currentLevel) {
                    in 0..79 -> remainingPercentage * 1.0
                    in 80..89 -> remainingPercentage * 1.8
                    in 90..95 -> remainingPercentage * 3.0
                    else -> remainingPercentage * 5.0
                }
            }
            isNormalCharging() -> remainingPercentage * 2.5
            else -> remainingPercentage * 5.0
        }
        val roundedMinutes = ((estimatedMinutes / 5.0).roundToInt() * 5).coerceAtLeast(1)
        return (roundedMinutes * 60 * 1000).toLong()
    }

    private fun getPlugType(): Int? = runCatching {
        batteryContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
    }.getOrNull()
    private fun isFastCharging() = getPlugType() == BatteryManager.BATTERY_PLUGGED_AC
    private fun isNormalCharging() = getPlugType() == BatteryManager.BATTERY_PLUGGED_USB

    private fun getCurrentBatteryLevel(): Int = runCatching {
        batteryContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
        } ?: -1
    }.getOrDefault(-1)
    private fun getBatteryStatus(): Int = runCatching {
        batteryContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    }.getOrDefault(-1)
}
