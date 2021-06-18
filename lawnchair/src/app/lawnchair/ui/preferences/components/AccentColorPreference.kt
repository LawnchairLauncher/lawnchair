package app.lawnchair.ui.preferences.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.util.systemAccentColor
import com.android.launcher3.R
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.systemBarsPadding
import kotlinx.coroutines.launch

@Composable
@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun AccentColorPreference(showDivider: Boolean) {
    val sheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var accentColor by preferenceManager().accentColor.getAdapter()

    val presets = listOf(
        context.systemAccentColor,
        0xFFF44336.toInt(),
        0xFF673AB7.toInt(),
        0xFF2196F3.toInt(),
        0xFF4CAF50.toInt(),
        0xFFFF9800.toInt()
    )

    fun applyAccentColor(newColor: Int) {
        accentColor = if (newColor == context.systemAccentColor) 0 else newColor
    }

    PreferenceTemplate(height = 52.dp, showDivider = showDivider) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clickable { scope.launch { sheetState.show() } }
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.accent_color),
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
        }
    }

    BottomSheet(sheetState = sheetState) {
        val initialNewAccentColor = if (accentColor == 0) context.systemAccentColor else accentColor
        var newAccentColor by remember { mutableStateOf(initialNewAccentColor) }

        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(56.dp)
                    .padding(start = 8.dp, end = 16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(40.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .clickable { scope.launch { sheetState.hide() } }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface
                    )
                }
                Spacer(modifier = Modifier.requiredWidth(8.dp))
                Text(
                    text = stringResource(id = R.string.accent_color),
                    style = MaterialTheme.typography.h6
                )
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                presets.forEachIndexed { index, it ->
                    ColorSwatch(
                        color = it,
                        onClick = { newAccentColor = it },
                        modifier = Modifier.weight(1F),
                        isSelected = it == newAccentColor
                    )
                    if (index != presets.size - 1) Spacer(modifier = Modifier.requiredWidth(12.dp))
                }
            }
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    applyAccentColor(newAccentColor)
                    scope.launch {
                        sheetState.hide()
                    }
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(16.dp)
            ) {
                Text(text = stringResource(id = R.string.done))
            }
        }
    }
}

@Composable
@ExperimentalAnimationApi
fun ColorSwatch(color: Int, onClick: (Int) -> Unit, modifier: Modifier = Modifier, isSelected: Boolean) {
    Box(
        modifier = modifier
            .aspectRatio(1F)
            .clip(CircleShape)
            .background(color = Color(color))
            .clickable { onClick(color) }, contentAlignment = Alignment.Center
    ) {
        AnimatedCheck(
            visible = isSelected,
            tint = Color.White
        )
    }
}