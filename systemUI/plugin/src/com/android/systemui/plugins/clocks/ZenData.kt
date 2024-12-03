package com.android.systemui.plugins.clocks

import android.provider.Settings.Global.ZEN_MODE_ALARMS
import android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS
import android.provider.Settings.Global.ZEN_MODE_NO_INTERRUPTIONS
import android.provider.Settings.Global.ZEN_MODE_OFF

data class ZenData(
    val zenMode: ZenMode,
    val descriptionId: String?,
) {
    enum class ZenMode(val zenMode: Int) {
        OFF(ZEN_MODE_OFF),
        IMPORTANT_INTERRUPTIONS(ZEN_MODE_IMPORTANT_INTERRUPTIONS),
        NO_INTERRUPTIONS(ZEN_MODE_NO_INTERRUPTIONS),
        ALARMS(ZEN_MODE_ALARMS);

        companion object {
            fun fromInt(zenMode: Int) = values().firstOrNull { it.zenMode == zenMode }
        }
    }
}
