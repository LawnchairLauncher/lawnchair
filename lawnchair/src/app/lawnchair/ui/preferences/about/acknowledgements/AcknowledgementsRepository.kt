package app.lawnchair.ui.preferences.about.acknowledgements

import android.content.Context
import android.util.Log
import app.lawnchair.util.MainThreadInitializedObject
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.util.SafeCloseable
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AcknowledgementsRepository private constructor(
    private val applicationContext: Context,
) : SafeCloseable {

    val ossLibraries: Flow<List<OssLibrary>> = flow {
        try {
            val jsonString = applicationContext.resources.assets.open(ACKNOWLEDGEMENTS_FILE_PATH)
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

    override fun close() {
    }

    companion object {
        private const val ACKNOWLEDGEMENTS_FILE_PATH = "app/cash/licensee/artifacts.json"
        private const val TAG = "AcknowledgementsRepo"

        @JvmField
        val INSTANCE = MainThreadInitializedObject(::AcknowledgementsRepository)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!
    }
}
