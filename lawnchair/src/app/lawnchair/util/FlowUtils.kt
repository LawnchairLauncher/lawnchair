package app.lawnchair.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun <T> Flow<T>.firstBlocking() = runBlocking { first() }

@Composable
fun <T> Flow<T>.collectAsStateBlocking() = collectAsState(initial = firstBlocking())

fun broadcastReceiverFlow(context: Context, filter: IntentFilter) = callbackFlow<Intent> {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent)
        }
    }
    context.registerReceiver(receiver, filter)
    awaitClose { context.unregisterReceiver(receiver) }
}
