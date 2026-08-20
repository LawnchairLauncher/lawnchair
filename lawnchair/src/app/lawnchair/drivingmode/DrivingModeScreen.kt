package app.lawnchair.drivingmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.LawnchairTheme

/**
 * The fullscreen driving UI. Bare-bones by design — replace the button
 * wiring with whatever shortcuts you actually want.
 */
@Composable
fun DrivingModeScreen(
    onMapsClick: () -> Unit,
    onPhoneClick: () -> Unit,
    onMusicClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitClick: () -> Unit,
) {
    LawnchairTheme {
        // The scrim fills the true full screen — including behind the status
        // and nav bars, which are transparent chrome in this launcher — so
        // the dimming effect covers them too. Only the button content itself
        // is padded away from the bars.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                DrivingModeButton("Maps", onMapsClick)
                DrivingModeButton("Phone", onPhoneClick)
                DrivingModeButton("Music", onMusicClick)
                DrivingModeButton("Settings", onSettingsClick)
                OutlinedButton(
                    onClick = onExitClick,
                    modifier = Modifier.fillMaxWidth().aspectRatio(6f),
                ) {
                    Text("Exit driving mode", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DrivingModeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(3f),
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}
