package app.lawnchair.smartspace.provider

import android.app.Activity
import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.patrykmichalik.preferencemanager.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

abstract class SmartspaceDataSource(
    val context: Context,
    val providerName: Int,
    getEnabledPref: PreferenceManager2.() -> Preference<Boolean, Boolean>
) {
    val enabled = getEnabledPref(PreferenceManager2.getInstance(context))

    protected abstract val internalTargets: Flow<List<SmartspaceTarget>>
    open val disabledTargets: List<SmartspaceTarget> = emptyList()

    private val restartSignal = MutableStateFlow(0)
    private val enabledTargets get() = internalTargets
        .onStart {
            if (requiresSetup()) throw RequiresSetupException()
        }
        .map { State(targets = it) }
        .catch {
            if (it is RequiresSetupException) {
                emit(State(
                    targets = disabledTargets,
                    requiresSetup = listOf(this@SmartspaceDataSource))
                )
            } else {
                throw it
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val targets = enabled.get()
        .distinctUntilChanged()
        .flatMapLatest {
            if (it)
                restartSignal.flatMapLatest { enabledTargets }
            else
                flowOf(State(targets = disabledTargets))
        }

    open suspend fun requiresSetup(): Boolean = false

    open suspend fun startSetup(activity: Activity) {}

    suspend fun onSetupDone() {
        if (!requiresSetup()) {
            restart()
        } else {
            enabled.set(false)
        }
    }

    fun restart() {
        restartSignal.value++
    }

    private class RequiresSetupException : RuntimeException()

    data class State(
        val targets: List<SmartspaceTarget> = emptyList(),
        val requiresSetup: List<SmartspaceDataSource> = emptyList()
    ) {
        operator fun plus(other: State): State {
            return State(
                targets = this.targets + other.targets,
                requiresSetup = this.requiresSetup + other.requiresSetup
            )
        }
    }
}
