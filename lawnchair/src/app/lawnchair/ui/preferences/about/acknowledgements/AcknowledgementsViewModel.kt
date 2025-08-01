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
        try {
            val jsonString = application.resources.assets.open(ACKNOWLEDGEMENTS_FILE_PATH)
                .bufferedReader().use { it.readText() }
            val libraries = kotlinxJson.decodeFromString<List<OssLibrary>>(jsonString)
                .asSequence()
                .distinctBy { "${it.groupId}:${it.artifactId}" }
                .sortedBy { it.name }
                .toList()
            emit(libraries)
        } catch (e: IOException) {
            Log.e(TAG, "Error reading acknowledgements file", e)
            emit(emptyList())
        } catch (e: kotlinx.serialization.SerializationException) {
            Log.e(TAG, "Error parsing acknowledgements JSON", e)
            emit(emptyList())
        }
    }
        .catch { e ->
            Log.e(TAG, "Unexpected error in ossLibraries flow", e)
            emit(emptyList())
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList(),
        )

    companion object {
        private const val ACKNOWLEDGEMENTS_FILE_PATH = "app/cash/licensee/artifacts.json"
        private const val TAG = "AcknowledgementsViewmModel"
    }
}
