/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    private val stateInternal = mutableStateOf(get())
    val state: State<T> get() = stateInternal

    fun onChange(newValue: T) {
        set(newValue)
        stateInternal.value = newValue
    }

    override fun onPreferenceChange(pref: BasePreferenceManager.PrefEntry<*>) {
        stateInternal.value = get()
    }

    operator fun getValue(thisObj: Any?, property: KProperty<*>): T = stateInternal.value
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
fun <T> BasePreferenceManager.PrefEntry<T>.getState() = getAdapter().state

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
