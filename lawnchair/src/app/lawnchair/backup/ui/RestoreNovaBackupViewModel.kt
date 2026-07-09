package app.lawnchair.backup.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.backup.NovaBackupConverter
import app.lawnchair.backup.NovaBackupConverter.NovaBackupInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class RestoreNovaBackupViewModel(
    application: Application,
) : AndroidViewModel(application) {

    sealed interface Event {
        data object RestoreSuccess : Event
        data object RestoreError : Event
    }

    sealed interface State {
        data class Success(
            val info: NovaBackupInfo,
            val isRestoring: Boolean = false,
        ) : State

        data object Loading : State

        data object Error : State
    }

    private var initialized = false
    private lateinit var converter: NovaBackupConverter

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun init(backupUri: Uri) {
        if (initialized) return
        initialized = true
        converter = NovaBackupConverter(getApplication(), backupUri)

        viewModelScope.launch {
            try {
                val info = converter.parseInfo()
                _state.value = State.Success(info)
            } catch (t: Throwable) {
                Log.e("RestoreNovaBackupViewModel", "failed to parse Nova backup", t)
                _state.value = State.Error
            }
        }
    }

    fun restore() {
        val state = _state.value as? State.Success ?: return
        if (state.isRestoring) return

        viewModelScope.launch {
            _state.value = state.copy(isRestoring = true)
            try {
                converter.convertAndRestore(state.info)
                _events.send(Event.RestoreSuccess)
            } catch (t: Throwable) {
                Log.e("RestoreNovaBackup", "failed to restore Nova backup", t)
                _state.value = state.copy(isRestoring = false)
                _events.send(Event.RestoreError)
            }
        }
    }
}
