package app.lawnchair.ui.preferences.components.fold

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.fold.FoldHomeLayout
import app.lawnchair.fold.FoldOption
import app.lawnchair.fold.FoldScreen
import app.lawnchair.fold.FoldTransitionController
import app.lawnchair.fold.FoldTransitionStyle
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

/**
 * A foldable option with a [FoldPreview]: the row shows the selected option and its preview, and
 * opens a sheet of cards, one per option with its preview, label and description. The sheet stays
 * open on a pick, so the ring moves and the animation's large preview starts over; Done closes it.
 */
@Composable
fun <T> FoldPickerPreference(
    adapter: PreferenceAdapter<T>,
    options: List<T>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) where T : Enum<T>, T : FoldOption {
    val sheet = bottomSheetHandler
    val haptics = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    val selected = adapter.state.value
    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = modifier,
        enabled = enabled,
        description = { Text(text = stringResource(id = selected.labelResourceId)) },
        endWidget = { FoldPreview(selected, Modifier.size(76.dp, 62.dp)) },
        onClick = if (enabled) {
            {
                haptics.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                sheet.show { FoldPicker(adapter, options, label, onDone = sheet.hide) }
            }
        } else {
            null
        },
    )
}

@Composable
private fun <T> FoldPicker(
    adapter: PreferenceAdapter<T>,
    options: List<T>,
    label: String,
    onDone: () -> Unit,
) where T : Enum<T>, T : FoldOption {
    val haptics = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    val selected = adapter.state.value
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected is FoldTransitionStyle) {
                // The selected animation, large; it starts over on every pick
                FoldPreview(selected, Modifier.size(180.dp, 221.dp), time = rememberFoldPreviewTime(restartKey = selected))
            }
            options.forEach { option ->
                FoldOptionCard(option = option, selected = option == selected) {
                    haptics.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                    adapter.onChange(option)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onDone) {
                Text(text = stringResource(id = R.string.action_done))
            }
        }
    }
}

@Composable
private fun FoldOptionCard(
    option: FoldOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .border(2.dp, if (selected) colors.primary else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FoldPreview(option, Modifier.size(96.dp, 118.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(id = option.labelResourceId), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(id = option.descriptionResourceId),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * The top of the Foldable screen page: an unfold with the chosen home screen layout and animation,
 * a caption that follows the loop (folded, then unfolded) and the layout's description.
 */
@Composable
fun FoldPreviewHeader(
    home: FoldHomeLayout,
    animation: FoldTransitionStyle,
    modifier: Modifier = Modifier,
) {
    val time = rememberFoldPreviewTime()
    val unfolded = stringResource(
        id = R.string.fold_preview_unfolded,
        stringResource(id = home.labelResourceId),
        stringResource(id = animation.labelResourceId),
    )
    val captionStyle = MaterialTheme.typography.titleSmall
    val captionColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FoldPreview(home, Modifier.size(200.dp, 246.dp), animation = animation, time = time)
        Box(
            modifier = Modifier.clearAndSetSemantics { contentDescription = unfolded },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = R.string.fold_preview_folded),
                modifier = Modifier.graphicsLayer { alpha = foldedAt(time.value) },
                color = captionColor,
                style = captionStyle,
            )
            Text(
                text = unfolded,
                modifier = Modifier.graphicsLayer { alpha = 1f - foldedAt(time.value) },
                color = captionColor,
                textAlign = TextAlign.Center,
                style = captionStyle,
            )
        }
        Text(
            text = stringResource(id = home.descriptionResourceId),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        // Only on the large screen: there is no half to open on the cover
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val onLargeScreen = remember(configuration) {
            InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context)
                .deviceProperties.foldScreen == FoldScreen.LARGE
        }
        if (onLargeScreen) {
            Button(
                onClick = {
                    context.startActivity(
                        Intent(context, LawnchairLauncher::class.java)
                            .setAction(FoldTransitionController.ACTION_PREVIEW)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            ) {
                Text(text = stringResource(id = R.string.fold_preview_try))
            }
        }
    }
}
