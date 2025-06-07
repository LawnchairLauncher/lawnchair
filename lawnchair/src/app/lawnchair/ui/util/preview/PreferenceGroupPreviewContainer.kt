package app.lawnchair.ui.util.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup

@Composable
fun PreferenceGroupPreviewContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceDim),
    ) {
        PreferenceGroup(
            heading = "Group Heading",
            description = "Group description",
            showDescription = true,
            content = content,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}
