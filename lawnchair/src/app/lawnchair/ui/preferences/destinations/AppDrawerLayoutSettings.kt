package app.lawnchair.ui.preferences.destinations

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import com.android.launcher3.R

@Composable
fun AppDrawerLayoutSettings(
    modifier: Modifier = Modifier,
    onOptionSelect: (Boolean) -> Unit = {},
) {
    val prefs = preferenceManager()
    val context = LocalContext.current
    val resources = context.resources
    var selectedOption by remember { mutableStateOf(prefs.drawerList.get()) }

    PreferenceGroupHeading(
        stringResource(R.string.layout),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ButtonSection(
            modifier = Modifier,
            label = resources.getString(R.string.feed_default),
            isSelected = selectedOption,
            onClick = {
                selectedOption = true
                prefs.drawerList.set(true)
                onOptionSelect(true)
            },
            gridLayout = {
                Column(modifier = Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }
            },
        )

        Spacer(modifier = Modifier.width(16.dp))

        ButtonSection(
            modifier = Modifier,
            label = resources.getString(R.string.caddy_beta),
            isSelected = !selectedOption,
            onClick = {
                selectedOption = false
                prefs.drawerList.set(false)
                onOptionSelect(false)
            },
            gridLayout = {
                Column(modifier = Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun ButtonSection(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gridLayout: @Composable () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(durationMillis = 300),
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 300),
    )

    Box(
        modifier = modifier
            .size(160.dp, 120.dp)
            .clip(MaterialTheme.shapes.large)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .fillMaxWidth(0.8f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            )

            gridLayout()

            Text(
                modifier = Modifier,
                text = label,
                color = textColor,
                fontSize = 14.sp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewToggleSelectionUI() {
    Surface {
        AppDrawerLayoutSettings()
    }
}
