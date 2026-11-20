package app.lawnchair.allapps.suggestions

import android.content.Context
import android.os.Process
import android.util.AtomicFile
import android.util.Log
import androidx.annotation.WorkerThread
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.util.SingletonHolder
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.pow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lawnchair's own record of app launches, which feeds the Frequent and Recent suggestion tabs
 * of the app drawer. Counting launches in the launcher needs no usage access.
 *
 * Only apps of the current user are recorded, and only while the tabs are turned on. The history
 * is a small JSON file in the app's files folder, keyed by component key, with at most
 * [MAX_ENTRIES] apps launched in the last [MAX_AGE_MS]. Disk access never runs on the main thread.
 */
class AppLaunchStore private constructor(private val context: Context) {

    /** A launch count where each launch loses half its weight every [HALF_LIFE_MS]. */
    data class Launches(val score: Double, val lastLaunched: Long) {
        fun scoreAt(nowMs: Long): Double = score * 0.5.pow((nowMs - lastLaunched).coerceAtLeast(0L).toDouble() / HALF_LIFE_MS)
    }

    private val file by lazy { AtomicFile(File(context.filesDir, FILE_NAME)) }

    /** Loaded on first use. Guarded by `this`. */
    private var entries: MutableMap<String, Launches>? = null

    /** Records one launch of [info] if it is an app of the current user. Safe on the main thread. */
    fun recordLaunch(info: ItemInfo?) {
        if (info == null || info.itemType != ITEM_TYPE_APPLICATION) return
        if (!PreferenceManager2.getInstance(context).drawerSuggestionTabs.firstCached()) return
        val key = info.componentKey ?: return
        if (key.user != Process.myUserHandle() || key.componentName.packageName == context.packageName) return
        val nowMs = System.currentTimeMillis()
        ORDERED_BG_EXECUTOR.execute {
            synchronized(this) {
                val map = load()
                val old = map[key.toString()]
                // Never move the last launch back, e.g. after the clock was changed.
                map[key.toString()] = Launches((old?.scoreAt(nowMs) ?: 0.0) + 1, maxOf(nowMs, old?.lastLaunched ?: 0L))
                prune(map, nowMs)
                save(map)
            }
        }
    }

    /** A copy of the launch history by component key string. */
    @WorkerThread
    fun getLaunches(): Map<String, Launches> = synchronized(this) { HashMap(load()) }

    private fun load(): MutableMap<String, Launches> {
        entries?.let { return it }
        val map = HashMap<String, Launches>()
        try {
            val json = JSONObject(String(file.readFully(), Charsets.UTF_8))
            json.keys().forEach { key ->
                val launch = json.getJSONArray(key)
                map[key] = Launches(launch.getDouble(0), launch.getLong(1))
            }
            prune(map, System.currentTimeMillis())
        } catch (_: FileNotFoundException) {
            // Nothing recorded yet.
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read app launch history, starting over", e)
            map.clear()
        }
        entries = map
        return map
    }

    private fun prune(map: MutableMap<String, Launches>, nowMs: Long) {
        map.values.removeAll { nowMs - it.lastLaunched > MAX_AGE_MS }
        if (map.size > MAX_ENTRIES) {
            map.entries.sortedBy { it.value.lastLaunched }.take(map.size - MAX_ENTRIES).forEach { map.remove(it.key) }
        }
    }

    private fun save(map: Map<String, Launches>) {
        val json = JSONObject()
        map.forEach { (key, launch) -> json.put(key, JSONArray().put(launch.score).put(launch.lastLaunched)) }
        val stream = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to save app launch history", e)
            return
        }
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            Log.w(TAG, "Unable to save app launch history", e)
        }
    }

    companion object : SingletonHolder<AppLaunchStore, Context>({ AppLaunchStore(it.applicationContext) }) {
        private const val TAG = "AppLaunchStore"
        private const val FILE_NAME = "app_launch_history.json"
        private const val MAX_ENTRIES = 300
        private const val MAX_AGE_MS = 180L * 24 * 60 * 60 * 1000

        /** Long enough that daily apps stay on top, short enough that dropped apps drift down. */
        const val HALF_LIFE_MS = 21L * 24 * 60 * 60 * 1000
    }
}
