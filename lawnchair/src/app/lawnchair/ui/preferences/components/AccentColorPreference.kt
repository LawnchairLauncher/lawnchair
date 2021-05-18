package app.lawnchair.ui.preferences.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.util.preferences.getAdapter
import app.lawnchair.util.preferences.preferenceManager
import app.lawnchair.util.systemAccentColor
import com.android.launcher3.R
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.launch

@ExperimentalAnimationApi
@ExperimentalMaterialApi
@Composable
fun AccentColorPreference(showDivider: Boolean = true) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    var accentColor by preferenceManager().accentColor.getAdapter()

    val presets = listOf(
        0,
        0xFFF44336.toInt(),
        0xFF673AB7.toInt(),
        0xFF2196F3.toInt(),
        0xFF4CAF50.toInt(),
        0xFFFF9800.toInt()
    )

    BottomSheet(
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .padding(start = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(40.dp)
                            .clip(CircleShape)
                            .clickable { scope.launch { sheetState.hide() } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.requiredWidth(8.dp))
                    Text(text = stringResource(id = R.string.accent_color), style = MaterialTheme.typography.h6)
                }
                Spacer(modifier = Modifier.requiredHeight(8.dp))
                Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    presets.forEachIndexed { index, it ->
                        ColorSwatch(
                            color = it,
                            onClick = { accentColor = it },
                            modifier = Modifier.weight(1F),
                            isSelected = it == accentColor
                        )
                        if (index != presets.size - 1) Spacer(modifier = Modifier.requiredWidth(12.dp))
                    }
                }
            }
        },
        sheetState = sheetState
    ) { showSheet ->
        ClickablePreference(
            label = stringResource(id = R.string.accent_color),
            showDivider = showDivider,
            onClick = { scope.launch { showSheet() } }
        )
    }
}

@ExperimentalAnimationApi
@Composable
fun ColorSwatch(color: Int, onClick: (Int) -> Unit, modifier: Modifier = Modifier, isSelected: Boolean) {
    val context = LocalContext.current

    Box(modifier = modifier
        .aspectRatio(1F)
        .clip(CircleShape)
        .background(color = Color(if (color == 0) context.systemAccentColor else color))
        .clickable { onClick(color) }, contentAlignment = Alignment.Center
    ) {
        AnimatedCheck(
            visible = isSelected, colorFilter = ColorFilter.tint(Color.White)
        )
    }
}