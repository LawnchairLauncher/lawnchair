package app.lawnchair.util.preferences

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.android.launcher3.LauncherAppState
import kotlin.reflect.KProperty

class PreferenceAdapter<T>(
    private val get: () -> T,
    private val set: (T) -> Unit
) : BasePreferenceManager.PreferenceChangeListener {
    val state = mutableStateOf(get())

    fun onChange(newValue: T) {
        set(newValue)
        state.value = newValue
    }

    override fun onPreferenceChange(pref: BasePreferenceManager.PrefEntry<*>) {
        state.value = get()
    }

    operator fun getValue(thisObj: Any?, property: KProperty<*>): T = state.value
    operator fun setValue(thisObj: Any?, property: KProperty<*>, newValue: T) {
        onChange(newValue)
    }
}

@Composable
fun BasePreferenceManager.IdpIntPref.getAdapter(): PreferenceAdapter<Float> {
    val context = LocalContext.current
    val idp = remember { LauncherAppState.getIDP(context) }
    return getAdapter(this, { get(idp).toFloat() }, { newValue -> set(newValue.toInt(), idp) })
}

@Composable
fun <T> BasePreferenceManager.PrefEntry<T>.getAdapter(): PreferenceAdapter<T> {
    return getAdapter(this, ::get, ::set)
}

@Composable
fun <T> BasePreferenceManager.PrefEntry<T>.observeAsState(): State<T> {
    return getAdapter().state
}

@Composable
private fun <T> getAdapter(
    pref: BasePreferenceManager.PrefEntry<*>,
    get: () -> T,
    set: (T) -> Unit
): PreferenceAdapter<T> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val adapter = remember { PreferenceAdapter(get, set) }
    DisposableEffect(pref, lifecycleOwner) {
        pref.addListener(adapter)
        onDispose { pref.removeListener(adapter) }
    }
    return adapter
}
