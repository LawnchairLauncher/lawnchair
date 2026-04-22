package app.lawnchair.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

fun <T> Flow<T>.firstBlocking() = runBlocking { first() }

@Composable
fun <T> Flow<T>.collectAsStateBlocking() = collectAsStateWithLifecycle(initialValue = firstBlocking())

fun broadcastReceiverFlow(context: Context, filter: IntentFilter) = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent)
        }
    }
    context.registerReceiver(receiver, filter)
    awaitClose { context.unregisterReceiver(receiver) }
}

fun <T> Flow<T>.dropWhileBusy(): Flow<T> = channelFlow {
    collect { trySend(it) }
}.buffer(0)

fun <T> Flow<T>.subscribeBlocking(
    scope: CoroutineScope,
    block: (T) -> Unit,
) {
    block(firstBlocking())
    this
        .onEach { block(it) }
        .drop(1)
        .distinctUntilChanged()
        .launchIn(scope = scope)
}
