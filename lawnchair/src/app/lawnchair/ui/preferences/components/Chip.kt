package app.lawnchair.ui.preferences.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f * selectedProgress)
    val textColor = lerp(
        MaterialTheme.colorScheme.onBackground.copy(alpha = ContentAlpha.medium),
        MaterialTheme.colorScheme.primary,
        selectedProgress
    )
    val borderColor = lerp(
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12F),
        MaterialTheme.colorScheme.primary,
        selectedProgress
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}
