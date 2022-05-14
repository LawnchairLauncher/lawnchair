package app.lawnchair.smartspace.provider

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.patrykmichalik.preferencemanager.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

abstract class SmartspaceDataSource(
    val context: Context,
    getEnabledPref: PreferenceManager2.() -> Preference<Boolean, Boolean>
) {
    private val enabledPref = getEnabledPref(PreferenceManager2.getInstance(context))

    val isEnabled = enabledPref.get()

    protected abstract val internalTargets: Flow<List<SmartspaceTarget>>
    open val disabledTargets: Flow<List<SmartspaceTarget>> = flowOf(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val targets = isEnabled
        .distinctUntilChanged()
        .flatMapLatest { if (it) internalTargets else disabledTargets }

    open fun destroy() {

    }
}
