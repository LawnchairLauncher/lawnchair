package app.lawnchair.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

fun <T> Flow<T>.firstBlocking() = runBlocking { first() }

@Composable
fun <T> Flow<T>.collectAsStateBlocking() = collectAsState(initial = firstBlocking())

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
