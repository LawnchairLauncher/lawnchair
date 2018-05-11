package ch.deletescape.lawnchair.weather

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.Utilities
import java.util.*

class WeatherIconProvider(private val context: Context) {

    fun getIcon(iconID: String): Drawable {
        var resID = iconID
        if (!ID_MAP.containsKey(resID)) {
            Log.e("WeatherIconProvider", "No weather icon exists for condition: " + resID)
            resID = CONDITION_UNKNOWN
        }

        val bitmap = BitmapFactory.decodeResource(context.resources, ID_MAP[resID]!!)
        return BitmapDrawable(context.resources,
                Utilities.addShadowToIcon(bitmap, bitmap.width))
    }

    companion object {
        const val CONDITION_UNKNOWN = "-1"
        const val CONDITION_CLEAR = "01"
        const val CONDITION_FEW_CLOUDS = "02"
        const val CONDITION_CLOUDS = "03"
        const val CONDITION_MOST_CLOUDS = "04"
        const val CONDITION_SHOWERS = "09"
        const val CONDITION_RAIN = "10"
        const val CONDITION_STORM = "11"
        const val CONDITION_SNOW = "13"
        const val CONDITION_MIST = "50"

        private val ID_MAP = HashMap<String, Int>()

        init {
            ID_MAP.put("${CONDITION_CLEAR}d", R.drawable.weather_01)
            ID_MAP.put("${CONDITION_CLEAR}n", R.drawable.weather_01n)
            ID_MAP.put("${CONDITION_FEW_CLOUDS}d", R.drawable.weather_02)
            ID_MAP.put("${CONDITION_FEW_CLOUDS}n", R.drawable.weather_02n)
            ID_MAP.put("${CONDITION_CLOUDS}d", R.drawable.weather_03)
            ID_MAP.put("${CONDITION_CLOUDS}n", R.drawable.weather_03n)
            ID_MAP.put("${CONDITION_MOST_CLOUDS}d", R.drawable.weather_04)
            ID_MAP.put("${CONDITION_MOST_CLOUDS}n", R.drawable.weather_04n)
            ID_MAP.put("${CONDITION_SHOWERS}d", R.drawable.weather_09)
            ID_MAP.put("${CONDITION_SHOWERS}n", R.drawable.weather_09)
            ID_MAP.put("${CONDITION_RAIN}d", R.drawable.weather_10)
            ID_MAP.put("${CONDITION_RAIN}n", R.drawable.weather_10n)
            ID_MAP.put("${CONDITION_STORM}d", R.drawable.weather_11)
            ID_MAP.put("${CONDITION_STORM}n", R.drawable.weather_11)
            ID_MAP.put("${CONDITION_SNOW}d", R.drawable.weather_13)
            ID_MAP.put("${CONDITION_SNOW}n", R.drawable.weather_13)
            ID_MAP.put("${CONDITION_MIST}d", R.drawable.weather_50)
            ID_MAP.put("${CONDITION_MIST}n", R.drawable.weather_50)
            ID_MAP.put(CONDITION_UNKNOWN, R.drawable.weather_none_available)
        }
    }
}
