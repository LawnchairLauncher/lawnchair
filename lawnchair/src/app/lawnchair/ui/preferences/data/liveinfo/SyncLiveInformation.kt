package app.lawnchair.ui.preferences.data.liveinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SyncLiveInformation(
    liveInformationManager: LiveInformationManager = liveInformationManager(),
) {
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            getLiveInformation()?.let { liveInformation ->
                liveInformationManager.announcements.set(liveInformation.announcements)
            }
        }
    }
}
