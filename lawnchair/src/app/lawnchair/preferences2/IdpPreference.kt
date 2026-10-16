package app.lawnchair.preferences2

import androidx.annotation.Discouraged
import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.LawnchairApp
import com.android.launcher3.InvariantDeviceProfile
import kotlin.jvm.JvmOverloads
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * @param handOverOnSet when true, [firstCached] returns the value just set until the preference
 * cache has it. Use it when [onSet] reloads something that reads [firstCached]: [onSet] can run
 * (e.g. a grid reload that runs inline on the main thread) before the cache collector of
 * [PreferenceManager2] has received the new value.
 */
class IdpPreference(
    val defaultSelector: InvariantDeviceProfile.GridOption.() -> Int,
    val key: Preferences.Key<Int>,
    private val preferencesDataStore: DataStore<Preferences>,
    val onSet: (Int) -> Unit = {},
    private val handOverOnSet: Boolean = false,
) {

    /** The value stored by the last [set] (null = removed), until the cache has it. */
    private class PendingValue(val stored: Int?)

    @Volatile
    private var pendingValue: PendingValue? = null

    fun get(gridOption: InvariantDeviceProfile.GridOption) = preferencesDataStore.data.map { preferences ->
        val value = preferences[key]
        if (value == null || value == -1) {
            defaultSelector(gridOption)
        } else {
            value
        }
    }

    suspend fun set(value: Int, gridOption: InvariantDeviceProfile.GridOption) {
        val stored = if (value == defaultSelector(gridOption)) null else value
        // Handed over before the write so that flows mapping this write see it too.
        if (handOverOnSet) pendingValue = PendingValue(stored)
        preferencesDataStore.edit { mutablePreferences ->
            if (stored == null) {
                mutablePreferences.remove(key)
            } else {
                mutablePreferences[key] = stored
            }
        }
        onSet(value)
    }

    /** The stored value for [cachedValue] read from the cache, or the one just set. */
    internal fun storedValue(cachedValue: Int?): Int? {
        val pending = pendingValue ?: return cachedValue
        if (pending.stored == cachedValue) {
            pendingValue = null
        }
        return pending.stored
    }
}

@Discouraged("This is a blocking read, use firstCached() for non-blocking reads")
fun IdpPreference.firstBlocking(gridOption: InvariantDeviceProfile.GridOption) = runBlocking { get(gridOption = gridOption).first() }

@JvmOverloads
fun IdpPreference.firstCached(
    gridOption: InvariantDeviceProfile.GridOption,
    prefs2: PreferenceManager2 = PreferenceManager2.getInstance(LawnchairApp.instance),
): Int {
    val cached = prefs2.getCachedPreferences()
    val value = storedValue(cached[key])
    return if (value == null || value == -1) {
        defaultSelector(gridOption)
    } else {
        value
    }
}

@Composable
fun IdpPreference.state(
    gridOption: InvariantDeviceProfile.GridOption,
    initial: Int? = null,
) = get(gridOption = gridOption).collectAsStateWithLifecycle(initialValue = initial)
