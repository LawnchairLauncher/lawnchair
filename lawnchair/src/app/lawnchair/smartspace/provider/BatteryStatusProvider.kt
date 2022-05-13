package app.lawnchair.smartspace.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import kotlinx.coroutines.flow.MutableStateFlow

class BatteryStatusProvider(context: Context) : SmartspaceDataSource(context, { smartspaceBatteryStatus }) {

    private val batteryReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            full = status == BatteryManager.BATTERY_STATUS_FULL
            level = (100f
                    * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)).toInt()
            targetsFlow.value = listOfNotNull(getSmartspaceTarget())
        }
    }
    private var charging = false
    private var full = false
    private var level = 100

    private val targetsFlow = MutableStateFlow(emptyList<SmartspaceTarget>())
    override val internalTargets get() = targetsFlow

    init {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun getSmartspaceTarget(): SmartspaceTarget? {
        val title = when {
            full -> return null
            charging -> context.getString(R.string.smartspace_battery_charging)
            level <= 15 -> context.getString(R.string.smartspace_battery_low)
            else -> return null
        }
        val subtitle = context.getString(R.string.n_percent, level)
        return SmartspaceTarget(
            id = "batteryStatus",
            headerAction = SmartspaceAction(
                id = "batteryStatusAction",
                title = title,
                subtitle = subtitle
            ),
            score = SmartspaceScores.SCORE_BATTERY,
            featureType = SmartspaceTarget.FeatureType.FEATURE_CALENDAR
        )
    }

    override fun destroy() {
        context.unregisterReceiver(batteryReceiver)
    }
}
