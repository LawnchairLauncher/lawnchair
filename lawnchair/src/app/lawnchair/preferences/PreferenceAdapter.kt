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

package app.lawnchair.preferences

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import kotlin.reflect.KProperty

interface PreferenceAdapter<T> {
    val state: State<T>
    fun onChange(newValue: T)

    operator fun getValue(thisObj: Any?, property: KProperty<*>): T = state.value
    operator fun setValue(thisObj: Any?, property: KProperty<*>, newValue: T) {
        onChange(newValue)
    }
}

class PreferenceAdapterImpl<T>(
    private val get: () -> T,
    private val set: (T) -> Unit
) : PreferenceAdapter<T>, PreferenceChangeListener {
    private val stateInternal = mutableStateOf(get())
    override val state: State<T> get() = stateInternal

    override fun onChange(newValue: T) {
        set(newValue)
        stateInternal.value = newValue
    }

    override fun onPreferenceChange() {
        stateInternal.value = get()
    }
}

@Composable
fun BasePreferenceManager.IdpIntPref.getAdapter(): PreferenceAdapter<Int> {
    val context = LocalContext.current
    val idp = remember { InvariantDeviceProfile.INSTANCE.get(context) }
    val defaultGrid = idp.closestProfile
    return getAdapter(
        this,
        { get(defaultGrid) },
        { newValue -> set(newValue, defaultGrid) }
    )
}

@Composable
fun <T> PrefEntry<T>.getAdapter(): PreferenceAdapter<T> {
    return getAdapter(this, ::get, ::set)
}

@Composable
fun <T> PrefEntry<T>.getState() = getAdapter().state

@Composable
fun <T> PrefEntry<T>.observeAsState(): State<T> {
    return getAdapter().state
}

@Composable
private fun <P, T> getAdapter(
    pref: PrefEntry<P>,
    get: () -> T,
    set: (T) -> Unit
): PreferenceAdapter<T> {
    val adapter = remember { PreferenceAdapterImpl(get, set) }
    DisposableEffect(pref) {
        pref.addListener(adapter)
        onDispose { pref.removeListener(adapter) }
    }
    return adapter
}

@Composable
fun <T, R>rememberTransformAdapter(
    adapter: PreferenceAdapter<T>,
    transformGet: (T) -> R,
    transformSet: (R) -> T
): PreferenceAdapter<R> {
    return remember(adapter) {
        TransformPreferenceAdapter(adapter, transformGet, transformSet)
    }
}

private class TransformPreferenceAdapter<T, R>(
    private val parent: PreferenceAdapter<T>,
    private val transformGet: (T) -> R,
    private val transformSet: (R) -> T
) : PreferenceAdapter<R> {
    override val state = derivedStateOf { transformGet(parent.state.value) }

    override fun onChange(newValue: R) {
        parent.onChange(transformSet(newValue))
    }
}
