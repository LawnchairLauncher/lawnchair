package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.theme.preferenceGroupColor
import app.lawnchair.ui.util.addIf

@Composable
fun SwitchPreferenceWithPreview(
    label: String,
    adapter: PreferenceAdapter<Boolean>,
    disabledLabel: String,
    disabledContent: @Composable ColumnScope.() -> Unit,
    enabledLabel: String,
    enabledContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SwitchPreferenceWithPreview(
        label = label,
        checked = adapter.state.value,
        onCheckedChange = { adapter.onChange(it) },
        disabledLabel = disabledLabel,
        disabledContent = disabledContent,
        enabledLabel = enabledLabel,
        enabledContent = enabledContent,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
fun SwitchPreferenceWithPreview(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    disabledLabel: String,
    disabledContent: @Composable ColumnScope.() -> Unit,
    enabledLabel: String,
    enabledContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier) {
        PreferenceGroupHeading(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SwitchPreferencePreviewCard(
                label = disabledLabel,
                isSelected = !checked,
                onClick = { onCheckedChange(false) },
                enabled = enabled,

            ) {
                disabledContent()
            }
            Spacer(Modifier.width(16.dp))
            SwitchPreferencePreviewCard(
                label = enabledLabel,
                isSelected = checked,
                onClick = { onCheckedChange(true) },
                enabled = enabled,
            ) {
                enabledContent()
            }
        }
    }
}

@Composable
fun SwitchPreferencePreviewCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else preferenceGroupColor(),
        animationSpec = tween(durationMillis = 300),
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 300),
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp, 120.dp)
                .clip(MaterialTheme.shapes.large)
                .background(backgroundColor)
                .clickable { if (enabled) onClick() }
                .addIf(!enabled) {
                    alpha(0.38f)
                }
                .padding(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
        )
    }
}
