package app.lawnchair.backup.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.backup.NovaBackupConverter
import app.lawnchair.backup.NovaBackupInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RestoreNovaBackupUiState {
    val isLoading: Boolean

    data class Success(val converter: NovaBackupConverter, val info: NovaBackupInfo) : RestoreNovaBackupUiState {
        override val isLoading: Boolean = false
    }

    data object Loading : RestoreNovaBackupUiState {
        override val isLoading: Boolean = true
    }

    data object Error : RestoreNovaBackupUiState {
        override val isLoading: Boolean = false
    }
}

internal class RestoreNovaBackupViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private var initialized = false

    private val _uiState = MutableStateFlow<RestoreNovaBackupUiState>(RestoreNovaBackupUiState.Loading)
    val uiState: StateFlow<RestoreNovaBackupUiState> = _uiState.asStateFlow()

    fun loadBackup(backupUri: Uri) {
        if (initialized) return
        initialized = true

        val converter = NovaBackupConverter(getApplication(), backupUri)
        viewModelScope.launch {
            try {
                val info = converter.parseInfo()
                _uiState.value = RestoreNovaBackupUiState.Success(converter, info)
            } catch (t: Throwable) {
                Log.e("RestoreNovaBackupViewModel", "failed to parse Nova backup", t)
                _uiState.value = RestoreNovaBackupUiState.Error
            }
        }
    }
}
