package app.lawnchair.ui.preferences.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun Chip(
    label: String,
    onClick: () -> Unit,
    currentOffset: Float,
    page: Int
) {
    val selectedProgress = 1f - abs(currentOffset - page).coerceIn(0f, 1f)
    Chip(
        label = label,
        selectedProgress = selectedProgress,
        onClick = onClick
    )
}

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val selectedProgress by animateFloatAsState(targetValue = if (selected) 1f else 0f)
    Chip(
        label = label,
        selectedProgress = selectedProgress,
        onClick = onClick
    )
}

@Composable
fun Chip(
    label: String,
    selectedProgress: Float,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val textColor = lerp(
        MaterialTheme.colorScheme.onBackground,
        MaterialTheme.colorScheme.primary,
        selectedProgress
    )
    val borderColor = lerp(
        MaterialTheme.colorScheme.outline,
        Color.Transparent,
        selectedProgress
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = selectedProgress))
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}
