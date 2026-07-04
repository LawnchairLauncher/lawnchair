package app.lawnchair.ui.preferences.data.liveinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import app.lawnchair.preferences2.asState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SyncLiveInformation(
    liveInformationManager: LiveInformationManager = liveInformationManager(),
) {
    val enabled by liveInformationManager.enabled.asState()

    LaunchedEffect(enabled) {
        if (enabled) {
            withContext(Dispatchers.IO) {
                getLiveInformation()?.let { liveInformation ->
                    liveInformationManager.liveInformation.set(liveInformation)
                }
            }
        }
    }
}
