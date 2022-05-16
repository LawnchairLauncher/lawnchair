package app.lawnchair.util

import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

fun File.subscribeFiles() = callbackFlow<List<File>> {
    fun sendFiles() {
        trySend(listFiles()?.toList() ?: emptyList())
    }
    sendFiles()
    val events = FileObserver.MOVED_TO or FileObserver.DELETE
    val observer = object : FileObserver(this@subscribeFiles, events) {
        override fun onEvent(event: Int, path: String?) {
            Log.d("FileUtils", "onEvent: event=$event, path=$path")
            sendFiles()
        }
    }
    observer.startWatching()
    awaitClose()
    observer.stopWatching()
}
