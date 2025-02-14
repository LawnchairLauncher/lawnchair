package app.lawnchair.ui.preferences.destinations

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.lawnchair.deck.LawndeckManager
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.layout.ButtonSection
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
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
    val resources = context.resources
    val deskLayout = prefs2.deckLayout.getAdapter()
    val addNewAppToHome = prefs.addIconToHome.getAdapter()
    val gesture = prefs2.swipeUpGestureHandler.getAdapter()
    var selectedOption by remember { mutableStateOf(prefs2.deckLayout.firstBlocking()) }
    val deckManager = remember { LawndeckManager(context) }

    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    PreferenceGroupHeading(stringResource(R.string.layout))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ButtonSectionWithLayout(
            modifier = Modifier.weight(1f),
            label = resources.getString(R.string.feed_default),
            isSelected = !selectedOption,
            rowNumber = 1,
            isLoading = isLoading,
            onClick = {
                isLoading = true
                coroutineScope.launch {
                    deskLayout.onChange(false)
                    gesture.onChange(GestureHandlerConfig.OpenAppDrawer)
                    withContext(Dispatchers.IO) {
                        deckManager.disableLawndeck()
                        isLoading = false
                    }
                }
                selectedOption = false
            },
            isBoxFirst = true,
        )

        Spacer(modifier = Modifier.width(16.dp))

        ButtonSectionWithLayout(
            modifier = Modifier.weight(1f),
            label = resources.getString(R.string.home_lawn_deck_label_beta),
            isSelected = selectedOption,
            rowNumber = 2,
            isLoading = isLoading,
            onClick = {
                isLoading = true
                coroutineScope.launch {
                    gesture.onChange(GestureHandlerConfig.NoOp)
                    deskLayout.onChange(true)
                    addNewAppToHome.onChange(true)
                    prefs
                    withContext(Dispatchers.IO) {
                        deckManager.enableLawndeck()
                        isLoading = false
                    }
                }
                selectedOption = true
            },
            isBoxFirst = false,
        )
    }
}

@Composable
fun ButtonSectionWithLayout(
    label: String,
    isSelected: Boolean,
    rowNumber: Int,
    isBoxFirst: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ButtonSection(
        modifier = modifier,
        label = label,
        isSelected = isSelected,
        onClick = {
            if (!isLoading) onClick()
        },
        gridLayout = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                if (isBoxFirst) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .fillMaxWidth(0.8f)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp),
                            ),
                    )

                    Column(modifier = Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier,
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
                } else {
                    Column(modifier = Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                        repeat(rowNumber) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier,
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
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PreviewToggleSelectionUI() {
    Surface {
        HomeLayoutSettings()
    }
}
