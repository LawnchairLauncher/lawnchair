package app.lawnchair.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.util.bottomSheetHandler
import app.lawnchair.ui.util.rememberSheetState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.systemui.shared.system.BlurUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheetContent(
    buttons: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    onDismissRequest: (() -> Unit)? = null,
    sheetState: SheetState? = null,
    title: (@Composable () -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val handler = bottomSheetHandler
    val coroutineScope = rememberCoroutineScope()
    val finalOnDismissRequest = onDismissRequest ?: if (sheetState != null) {
        {
            coroutineScope.launch { sheetState.hide() }
            Unit
        }
    } else {
        { handler.hide() }
    }
    val bottomSheetState = sheetState ?: handler.sheetState ?: rememberSheetState()
    val animatedFraction by animateFloatAsState(
        targetValue = if (
            bottomSheetState.targetValue == SheetValue.PartiallyExpanded ||
            bottomSheetState.targetValue == SheetValue.Expanded
        ) {
            1f
        } else {
            0f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BottomSheetBlurFraction",
    )
    val scrimAlpha = .32f * animatedFraction

    ModalBottomSheet(
        onDismissRequest = finalOnDismissRequest,
        sheetState = bottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = scrimAlpha),
        contentWindowInsets = { WindowInsets(0.dp) },
        modifier = modifier,
    ) {
        val contentPadding = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding(),
        ) {
            title?.let {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                    val textStyle = MaterialTheme.typography.titleLarge
                    ProvideTextStyle(textStyle, title)
                }
            }
            text?.let {
                Box(modifier = contentPadding) {
                    val textStyle = MaterialTheme.typography.bodyMedium
                    ProvideTextStyle(textStyle, text)
                }
            }
            content?.let {
                Box(
                    modifier = Modifier.padding(
                        top = if (title != null || text != null) 16.dp else 0.dp,
                    ),
                ) {
                    content()
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                buttons()
                BottomSpacer()
            }
        }
    }
}
