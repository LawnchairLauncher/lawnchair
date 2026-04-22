package app.lawnchair.util

import android.os.FileObserver
import com.android.launcher3.Utilities
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

fun File.subscribeFiles() = callbackFlow<List<File>> {
    fun sendFiles() {
        trySend(listFiles()?.toList() ?: emptyList())
    }
    sendFiles()
    val events = FileObserver.MOVED_TO or FileObserver.DELETE
    val observer = createFileObserver(this@subscribeFiles, events) { _, _ ->
        sendFiles()
    }
    observer.startWatching()
    awaitClose { observer.stopWatching() }
}

fun createFileObserver(file: File, events: Int, onEvent: (event: Int, path: String?) -> Unit): FileObserver {
    return if (Utilities.ATLEAST_Q) {
        object : FileObserver(file, events) {
            override fun onEvent(event: Int, path: String?) {
                onEvent(event, path)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        object : FileObserver(file.path, events) {
            override fun onEvent(event: Int, path: String?) {
                onEvent(event, path)
            }
        }
    }
}
