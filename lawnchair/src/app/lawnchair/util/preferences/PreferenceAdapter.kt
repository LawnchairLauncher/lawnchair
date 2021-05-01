package app.lawnchair.util.preferences

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.android.launcher3.LauncherAppState

class PreferenceAdapter<T>(
    private val get: () -> T,
    private val set: (T) -> Unit
) : BasePreferenceManager.PreferenceChangeListener {
    val state = mutableStateOf(get())

    fun onChange(newValue: T) {
        set(newValue)
        state.value = newValue
    }

    override fun onPreferenceChange(pref: BasePreferenceManager.BasePref<*>) {
        state.value = get()
    }
}

@Composable
fun BasePreferenceManager.IdpIntPref.getAdapter(): PreferenceAdapter<Float> {
    val context = LocalContext.current
    val idp = remember { LauncherAppState.getIDP(context) }
    return getAdapter(this, { get(idp).toFloat() }, { newValue -> set(newValue.toInt(), idp) })
}

@Composable
fun <T> BasePreferenceManager.BasePref<T>.getAdapter(): PreferenceAdapter<T> {
    return getAdapter(this, ::get, ::set)
}

@Composable
fun <T> BasePreferenceManager.BasePref<T>.observeAsState(): State<T> {
    return getAdapter().state
}

@Composable
private fun <T> getAdapter(
    pref: BasePreferenceManager.BasePref<*>,
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
