package app.lawnchair.widget.worldclock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.text.format.DateFormat
import java.util.TimeZone

/** [label] is the city, [region] the line under it, e.g. "Lincoln" and "Nebraska, U.S.A." */
data class City(val zone: String, val label: String, val region: String = "")

/** A locked widget ignores taps; it is edited from the launcher's widget settings button. */
data class WorldClockConfig(val cities: List<City>, val use24h: Boolean, val locked: Boolean = true)

/** Per-widget settings, keyed by appWidgetId. */
class WorldClockStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("world_clock_widgets", Context.MODE_PRIVATE)

    fun load(widgetId: Int): WorldClockConfig {
        if (!prefs.contains(citiesKey(widgetId))) adoptOrphan(widgetId)
        // A new widget starts from the most recently saved settings
        val id = if (prefs.contains(citiesKey(widgetId))) widgetId else LAST_SAVED
        val cities = prefs.getString(citiesKey(id), null)
            ?.lines()
            ?.mapNotNull { line ->
                val parts = line.split('\t')
                val zone = parts[0]
                if (zone.isBlank() || TimeZone.getTimeZone(zone).id != zone) return@mapNotNull null
                City(zone, parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: labelFor(zone), parts.getOrNull(2).orEmpty())
            }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(TimeZone.getDefault().id.let { City(it, labelFor(it)) })
        val use24h = prefs.getBoolean(use24hKey(id), DateFormat.is24HourFormat(context))
        val locked = prefs.getBoolean(lockedKey(id), true)
        return WorldClockConfig(cities, use24h, locked)
    }

    fun save(widgetId: Int, config: WorldClockConfig) {
        val cities = config.cities.joinToString("\n") {
            listOf(it.zone, it.label.clean(), it.region.clean()).joinToString("\t")
        }
        val editor = prefs.edit()
        for (id in intArrayOf(widgetId, LAST_SAVED)) {
            editor.putString(citiesKey(id), cities)
                .putBoolean(use24hKey(id), config.use24h)
                .putBoolean(lockedKey(id), config.locked)
        }
        editor.apply()
    }

    fun delete(widgetId: Int) {
        prefs.edit().remove(citiesKey(widgetId)).remove(use24hKey(widgetId)).remove(lockedKey(widgetId)).apply()
    }

    /**
     * A restore or re-bind gives the widget a new id. Settings left under an id that no widget
     * uses any more belong to it, so they move over instead of falling back to defaults.
     */
    private fun adoptOrphan(widgetId: Int) {
        val bound = try {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, WorldClockWidgetProvider::class.java)).toSet()
        } catch (e: RuntimeException) {
            return
        }
        val orphan = prefs.all.keys
            .mapNotNull { it.removePrefix("cities_").takeIf { _ -> it.startsWith("cities_") }?.toIntOrNull() }
            .filter { it > 0 && it != widgetId && it !in bound }
            .maxOrNull() ?: return
        prefs.edit()
            .putString(citiesKey(widgetId), prefs.getString(citiesKey(orphan), null))
            .putBoolean(use24hKey(widgetId), prefs.getBoolean(use24hKey(orphan), DateFormat.is24HourFormat(context)))
            .putBoolean(lockedKey(widgetId), prefs.getBoolean(lockedKey(orphan), true))
            .remove(citiesKey(orphan)).remove(use24hKey(orphan)).remove(lockedKey(orphan))
            .apply()
    }

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ')
    private fun citiesKey(id: Int) = "cities_$id"
    private fun use24hKey(id: Int) = "use24h_$id"
    private fun lockedKey(id: Int) = "locked_$id"

    companion object {
        /** Template for new widgets; real appWidgetIds are positive */
        private const val LAST_SAVED = 0

        fun labelFor(zone: String): String = zone.substringAfterLast('/').replace('_', ' ')

        /** Region/City zones only; drops aliases like Etc/GMT+3 and legacy SystemV ids. */
        val zones: List<String> by lazy {
            TimeZone.getAvailableIDs()
                .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
                .sortedBy { labelFor(it) }
        }

        fun offsetLabel(zone: String, now: Long = System.currentTimeMillis()): String {
            val minutes = TimeZone.getTimeZone(zone).getOffset(now) / 60_000
            val sign = if (minutes < 0) "-" else "+"
            val abs = kotlin.math.abs(minutes)
            return if (abs % 60 == 0) "UTC$sign${abs / 60}" else "UTC$sign${abs / 60}:${"%02d".format(abs % 60)}"
        }
    }
}
