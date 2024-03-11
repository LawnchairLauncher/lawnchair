package app.lawnchair.ui.preferences.data.liveinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import app.lawnchair.preferences2.asState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SyncLiveInformation(
    liveInformationManager: LiveInformationManager = liveInformationManager(),
) {
    val enabled by liveInformationManager.enabled.asState()

    LaunchedEffect(enabled) {
        if (enabled) {
            CoroutineScope(Dispatchers.IO).launch {
                getLiveInformation()?.let { liveInformation ->
                    liveInformationManager.liveInformation.set(liveInformation)
                }
            }
        }
    }
}
