package app.lawnchair.ui.preferences.about.acknowledgements

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.util.kotlinxJson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class AcknowledgementsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    val ossLibraries: StateFlow<List<OssLibrary>> = flow {
        val jsonString = application.resources.assets.open(ACKNOWLEDGEMENTS_FILE_PATH)
            .bufferedReader().use { it.readText() }
        val libraries = kotlinxJson.decodeFromString<List<OssLibrary>>(jsonString)
            .asSequence()
            .distinctBy { "${it.groupId}:${it.artifactId}" }
            .sortedBy { it.name }
            .toList()
        emit(libraries)
    }
        .catch { e ->
            val errorMessage = when (e) {
                is IOException -> "Error reading acknowledgements file"
                is kotlinx.serialization.SerializationException -> "Error parsing acknowledgements JSON"
                else -> "Unexpected error in ossLibraries flow"
            }
            Log.e(TAG, errorMessage, e)
            emit(emptyList())
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList(),
        )

    companion object {
        private const val ACKNOWLEDGEMENTS_FILE_PATH = "licenses.json"
        private const val TAG = "AcknowledgementsViewmModel"
    }
}
