package app.lawnchair.predictions

import android.content.pm.PackageManager
import app.lawnchair.util.isPackageInstalled
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking
import com.patrykmichalik.opto.domain.Preference

/**
 * Prediction store that supports both ordered and unordered modes.
 *
 * @param preference Opto preference.
 * @param isOrdered Should the entries be recorded in most recent order or form a distinct set. (Note: Duplicates are ignored in unordered mode)
 * @param maxSize Maximum number of entries stored (only enforced in ordered mode).
 */
class LawnchairPredictionStore(
    val preference: Preference<List<String>, String, *>,
    private val isOrdered: Boolean = false,
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    private val lock = Any()
    private var cache: MutableList<String> = load()

    /**
     * Adds a key to the store.
     *
     * In ordered mode, the key is prepended by most recent order and the list is trimmed to [maxSize].
     *
     * In unordered mode, duplicates are ignored.
     */
    fun add(key: String) {
        if (key.isEmpty()) return
        synchronized(lock) {
            if (isOrdered) {
                cache.add(0, key)
                if (cache.size > maxSize) {
                    cache = cache.take(maxSize).toMutableList()
                }
            } else {
                if (cache.contains(key)) return
                cache.add(key)
            }
            save()
        }
    }

    /**
     * Removes a key from the store.
     *
     * @return `true` if the key was present and removed.
     */
    fun remove(key: String): Boolean = synchronized(lock) {
        val removed = cache.remove(key)
        if (removed) save()
        removed
    }

    /**
     * Returns the current entries.
     *
     * In ordered mode, this is the raw list.
     *
     * In unordered mode, this is the distinct set of entries.
     */
    fun getEntries(): List<String> = synchronized(lock) {
        cache.filter { it.isNotEmpty() }
    }

    /**
     * Returns entries ranked by most recent order.
     *
     * This is only useful for store with ordered mode.
     */
    fun getRanked(): List<String> = synchronized(lock) {
        cache
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    /**
     * Removes entries whose package is no longer installed.
     */
    fun pruneUninstalled(pm: PackageManager) {
        synchronized(lock) {
            val changed = cache.removeAll { key ->
                if (key.isEmpty()) return@removeAll true
                !pm.isPackageInstalled(PredictionAppKey.packageName(key))
            }
            if (changed) save()
        }
    }

    /**
     * Replaces all entries with the given set.
     *
     * Useful for bulk updates (e.g. resetting dismissed apps from the UI).
     */
    fun setEntries(entries: Collection<String>) {
        synchronized(lock) {
            cache = entries.filter { it.isNotEmpty() }.toMutableList()
            save()
        }
    }

    private fun load(): MutableList<String> {
        return preference.firstBlocking()
            .filter { it.isNotEmpty() }
            .toMutableList()
    }

    private fun save() {
        synchronized(lock) {
            preference.setBlocking(cache)
        }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 250
    }
}
