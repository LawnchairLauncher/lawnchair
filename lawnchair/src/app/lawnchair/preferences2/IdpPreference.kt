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

class IdpPreference(
    val defaultSelector: InvariantDeviceProfile.GridOption.() -> Int,
    val key: Preferences.Key<Int>,
    private val preferencesDataStore: DataStore<Preferences>,
    val onSet: (Int) -> Unit = {},
) {

    fun get(gridOption: InvariantDeviceProfile.GridOption) = preferencesDataStore.data.map { preferences ->
        val value = preferences[key]
        if (value == null || value == -1) {
            defaultSelector(gridOption)
        } else {
            value
        }
    }

    suspend fun set(value: Int, gridOption: InvariantDeviceProfile.GridOption) {
        preferencesDataStore.edit { mutablePreferences ->
            val defaultValue = defaultSelector(gridOption)
            if (value == defaultValue) {
                mutablePreferences.remove(key)
            } else {
                mutablePreferences[key] = value
            }
        }
        onSet(value)
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
    val value = cached[key]
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
