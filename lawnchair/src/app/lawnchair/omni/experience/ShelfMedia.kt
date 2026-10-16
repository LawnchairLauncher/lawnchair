package app.lawnchair.omni.experience

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private data class ShelfPlayback(val title: String, val playing: Boolean, val controller: MediaController)

/** Session callbacks exist only while Shelf is subscribed and notification access is already granted. */
@Composable
fun ShelfMedia(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val updates = remember(context) {
        callbackFlow<ShelfPlayback?> {
            val manager = context.getSystemService(MediaSessionManager::class.java)
            if (manager == null) {
                trySend(null)
                close()
                return@callbackFlow
            }
            var controller: MediaController? = null
            fun publish() {
                val current = controller
                if (current == null) {
                    trySend(null)
                } else {
                    trySend(
                        ShelfPlayback(
                            current.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: current.packageName,
                            current.playbackState?.state == PlaybackState.STATE_PLAYING,
                            current,
                        ),
                    )
                }
            }
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = publish()
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onSessionDestroyed() {
                    controller = null
                    publish()
                }
            }
            fun select(sessions: List<MediaController>?) {
                controller?.unregisterCallback(callback)
                controller = sessions?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions?.firstOrNull()
                controller?.registerCallback(callback, Handler(Looper.getMainLooper()))
                publish()
            }
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { select(it) }
            val component = ComponentName(context, NotificationListener::class.java)
            var registered = false
            try {
                select(manager.getActiveSessions(component))
                manager.addOnActiveSessionsChangedListener(listener, component, Handler(Looper.getMainLooper()))
                registered = true
            } catch (_: SecurityException) {
                trySend(null)
            }
            awaitClose {
                controller?.unregisterCallback(callback)
                if (registered) manager.removeOnActiveSessionsChangedListener(listener)
            }
        }.flowOn(Dispatchers.IO)
    }
    val playback by updates.collectAsStateWithLifecycle(initialValue = null)
    playback?.let { media ->
        Card(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(Modifier.padding(12.dp)) {
                Text(media.title, Modifier.weight(1f))
                TextButton(onClick = {
                    if (media.playing) media.controller.transportControls.pause() else media.controller.transportControls.play()
                }) { Text(stringResource(if (media.playing) R.string.omni_pause else R.string.omni_play)) }
                TextButton(onClick = { media.controller.transportControls.skipToNext() }) { Text(stringResource(R.string.omni_next_widget)) }
            }
        }
    }
}
