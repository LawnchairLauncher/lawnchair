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
import app.lawnchair.util.preferences.getAdapter
import app.lawnchair.util.preferences.preferenceManager
import app.lawnchair.util.systemAccentColor
import com.android.launcher3.R
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.launch

@Composable
@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun AccentColorPreference(showDivider: Boolean = true) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(ModalBottomSheetValue.Hidden)
    var accentColor by preferenceManager().accentColor.getAdapter()
    val context = LocalContext.current
    val initialNewAccentColor = if (accentColor == 0) context.systemAccentColor else accentColor
    var newAccentColor by remember { mutableStateOf(initialNewAccentColor) }

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

    BottomSheet(
        sheetState = sheetState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
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
                OutlinedButton(
                    modifier = Modifier
                        .padding(top = 24.dp, end = 16.dp, bottom = 16.dp)
                        .align(Alignment.End),
                    onClick = {
                        applyAccentColor(newAccentColor)
                        scope.launch { sheetState.hide() }
                    }
                ) {
                    Text(text = stringResource(id = R.string.apply))
                }
            }
        },
    )
    ClickablePreference(
        label = stringResource(id = R.string.accent_color),
        showDivider = showDivider,
        onClick = { scope.launch { sheetState.show() } }
    )
}

@Composable
@ExperimentalAnimationApi
fun ColorSwatch(color: Int, onClick: (Int) -> Unit, modifier: Modifier = Modifier, isSelected: Boolean) {
    Box(modifier = modifier
        .aspectRatio(1F)
        .clip(CircleShape)
        .background(color = Color(color))
        .clickable { onClick(color) }, contentAlignment = Alignment.Center
    ) {
        AnimatedCheck(
            visible = isSelected, tint = Color.White
        )
    }
}