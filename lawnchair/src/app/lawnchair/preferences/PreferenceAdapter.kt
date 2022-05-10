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
import app.lawnchair.preferences2.IdpPreference
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.firstBlocking
import com.android.launcher3.InvariantDeviceProfile
import com.patrykmichalik.preferencemanager.Preference
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty

interface PreferenceAdapter<T> {
    val state: State<T>
    fun onChange(newValue: T)

    operator fun getValue(thisObj: Any?, property: KProperty<*>): T = state.value
    operator fun setValue(thisObj: Any?, property: KProperty<*>, newValue: T) {
        onChange(newValue)
    }
}

private class MutableStatePreferenceAdapter<T>(
    private val mutableState: MutableState<T>
) : PreferenceAdapter<T> {
    override val state = mutableState

    override fun onChange(newValue: T) {
        mutableState.value = newValue
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

private class StatePreferenceAdapter<T>(
    override val state: State<T>,
    private val set: (T) -> Unit
) : PreferenceAdapter<T> {

    override fun onChange(newValue: T) {
        set(newValue)
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
fun <T> PrefEntry<T>.getAdapter() = getAdapter(this, ::get, ::set)

@Composable
fun <T> PrefEntry<T>.getState() = getAdapter().state

@Composable
fun <T> PrefEntry<T>.observeAsState() = getAdapter().state

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
fun <T> Preference<T, *>.getAdapter(): PreferenceAdapter<T> {
    return createStateAdapter(state = asState(), set = this::set)
}

@Composable
fun IdpPreference.getAdapter(): PreferenceAdapter<Int> {
    val context = LocalContext.current
    val idp = remember { InvariantDeviceProfile.INSTANCE.get(context) }
    val defaultGrid = idp.closestProfile
    val state = get(defaultGrid).collectAsState(initial = firstBlocking(defaultGrid))
    return createStateAdapter(state = state, set = this::set)
}

@Composable
private fun <T> createStateAdapter(
    state: State<T>,
    set: suspend (T) -> Unit
) : PreferenceAdapter<T> {
    val scope = rememberCoroutineScope()
    return remember {
        StatePreferenceAdapter(state) {
            scope.launch { set(it) }
        }
    }
}

@Composable
fun <T, R> rememberTransformAdapter(
    adapter: PreferenceAdapter<T>,
    transformGet: (T) -> R,
    transformSet: (R) -> T
): PreferenceAdapter<R> = remember(adapter) {
    TransformPreferenceAdapter(adapter, transformGet, transformSet)
}

@Composable
fun <T> MutableState<T>.asPreferenceAdapter(): PreferenceAdapter<T> {
    return remember(this) { MutableStatePreferenceAdapter(this) }
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

@Composable
fun <T> customPreferenceAdapter(value: T, onValueChange: (T) -> Unit): PreferenceAdapter<T> {
    val state = remember { mutableStateOf(value) }
    state.value = value
    return object : PreferenceAdapter<T> {
        override val state = state
        override fun onChange(newValue: T) {
            onValueChange(newValue)
        }
    }
}

@Composable
operator fun PreferenceAdapter<Boolean>.not(): PreferenceAdapter<Boolean> {
    return rememberTransformAdapter(adapter = this, transformGet = { !it }, transformSet = { !it })
}
