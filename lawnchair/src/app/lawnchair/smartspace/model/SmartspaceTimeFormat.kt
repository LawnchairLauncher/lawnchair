package app.lawnchair.smartspace.model

import androidx.annotation.StringRes
import com.android.launcher3.R

sealed class SmartspaceTimeFormat(@StringRes val nameResourceId: Int) {

    companion object {

        fun fromString(value: String): SmartspaceTimeFormat = when (value) {
            "12_hour_format" -> TwelveHourFormat
            "24_hour_format" -> TwentyFourHourFormat
            else -> FollowSystem
        }

        /**
         * @return The list of all time format options.
         */
        fun values() = listOf(FollowSystem, TwelveHourFormat, TwentyFourHourFormat)
    }

    object FollowSystem : SmartspaceTimeFormat(
        nameResourceId = R.string.smartspace_time_follow_system,
    ) {
        override fun toString() = "system"
    }

    object TwelveHourFormat : SmartspaceTimeFormat(
        nameResourceId = R.string.smartspace_time_12_hour_format,
    ) {
        override fun toString() = "12_hour_format"
    }

    object TwentyFourHourFormat : SmartspaceTimeFormat(
        nameResourceId = R.string.smartspace_time_24_hour_format,
    ) {
        override fun toString() = "24_hour_format"
    }

}
