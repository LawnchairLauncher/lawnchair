package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.lawnchair.deck.LawndeckManager
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.SwitchPreferenceWithPreview
import app.lawnchair.util.BackHandler
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeLayoutSettings(
    modifier: Modifier = Modifier,
) {
    val prefs2 = preferenceManager2()
    val prefs = preferenceManager()
    val context = LocalContext.current

    val deskLayout = prefs2.deckLayout.getAdapter()
    val addNewAppToHome = prefs.addIconToHome.getAdapter()
    val swipeUpGesture = prefs2.swipeUpGestureHandler.getAdapter()

    val deckManager = remember { LawndeckManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }

    // Block back button when loading
    if (isLoading) {
        BackHandler {
            // Prevent back button during loading - do nothing
        }
    }

    // Show blocking loading dialog
    if (isLoading) {
        AlertDialog(
            onDismissRequest = {
                // Prevent dismissal during loading
            },
            title = {
                Text(
                    text = stringResource(R.string.home_lawn_deck_label_beta),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    if (loadingMessage.isNotEmpty()) {
                        Text(
                            text = loadingMessage,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = "Please wait...",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }

    SwitchPreferenceWithPreview(
        label = stringResource(R.string.layout),
        checked = deskLayout.state.value,
        onCheckedChange = { newValue ->
            isLoading = true
            loadingMessage = ""
            deskLayout.onChange(newValue)
            if (newValue) {
                coroutineScope.launch {
                    swipeUpGesture.onChange(GestureHandlerConfig.NoOp)
                    addNewAppToHome.onChange(true)
                    withContext(Dispatchers.IO) {
                        deckManager.enableLawndeck { message ->
                            // Update on main thread using coroutine scope
                            coroutineScope.launch(Dispatchers.Main) {
                                loadingMessage = message
                            }
                        }
                    }
                    isLoading = false
                    loadingMessage = ""
                }
            } else {
                coroutineScope.launch {
                    swipeUpGesture.onChange(GestureHandlerConfig.OpenAppDrawer)
                    withContext(Dispatchers.IO) {
                        deckManager.disableLawndeck()
                        isLoading = false
                        loadingMessage = ""
                    }
                }
            }
        },
        disabledLabel = stringResource(R.string.feed_default),
        disabledContent = {
            PreviewLayout(
                loading = isLoading,
            )
        },
        enabledLabel = stringResource(R.string.home_lawn_deck_label_beta),
        enabledContent = {
            PreviewLayout(
                topQsb = false,
                loading = isLoading,
            )
        },
        modifier = modifier,
    )
}

@Composable
fun PreviewLayout(
    modifier: Modifier = Modifier,
    topQsb: Boolean = true,
    loading: Boolean = false,
) {
    Column(modifier) {
        val qsbContainer = remember {
            movableContentOf {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .fillMaxWidth(0.8f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(16.dp),
                        ),
                )
            }
        }

        val iconGrid = remember {
            movableContentOf {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (topQsb) {
                qsbContainer()
                Spacer(modifier = Modifier.height(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    iconGrid()
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    repeat(2) {
                        Spacer(modifier = Modifier.height(4.dp))
                        iconGrid()
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                qsbContainer()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewToggleSelectionUI() {
    Surface {
        HomeLayoutSettings()
    }
}
