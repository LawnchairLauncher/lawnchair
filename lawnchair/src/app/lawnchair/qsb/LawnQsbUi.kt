package app.lawnchair.qsb

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.GoogleGo
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.addIf
import app.lawnchair.ui.util.preview.PreviewLawnchair
import com.android.launcher3.R
import com.android.launcher3.util.Themes

enum class QsbIconId {
    SEARCH,
    MIC,
    LENS,
    CLEAR,
}

@Immutable
data class QsbIconState(
    val id: QsbIconId,
    @param:DrawableRes val resId: Int,
    val themed: Boolean,
    val contentDescription: String,
    val method: ThemingMethod = ThemingMethod.THEME_BY_LAYER_ID,
    val visible: Boolean = true,
)

@Immutable
data class QsbStyle(
    val themed: Boolean,
    val backgroundAlpha: Float,
    @param:ColorInt val backgroundColor: Int,
    @param:ColorInt val strokeColor: Int,
    val strokeWidthPx: Float,
    val cornerRadiusPx: Float,
)

@Immutable
data class QsbState(
    val contentDescription: String,
    val startIcon: QsbIconState,
    val endIcons: List<QsbIconState>,
)

@Immutable
data class QsbActions(
    val onQsbClick: () -> Unit,
    val onStartIconClick: (() -> Unit)? = null,
    val onEndIconClick: ((id: QsbIconId) -> Unit),
)

fun buildQsbStyle(
    context: Context,
    themed: Boolean,
    backgroundAlpha: Int,
    backgroundColor: Int,
    cornerRadius: Float,
    strokeColor: Int?,
    strokeWidth: Float,
) = QsbStyle(
    themed = themed,
    backgroundAlpha = backgroundAlpha / 100f,
    backgroundColor = backgroundColor,
    strokeColor = strokeColor ?: Themes.getColorAccent(context),
    strokeWidthPx = strokeWidth,
    cornerRadiusPx = getHotseatQsbCornerRadius(context, cornerRadius),
)

fun getHotseatBackgroundColor(context: Context, themed: Boolean, themedBackgroundColor: Int? = null): Int {
    return if (themed) {
        themedBackgroundColor ?: Themes.getColorBackgroundFloating(context)
    } else {
        Themes.getAttrColor(context, R.attr.qsbFillColor)
    }
}

@Composable
fun rememberHotseatQsbState(
    searchProvider: QsbSearchProvider,
    themed: Boolean,
    showMic: Boolean,
    showLens: Boolean,
): QsbState {
    val searchLabel = stringResource(R.string.label_search)
    val voiceSearchLabel = stringResource(R.string.label_voice_search)
    val lensLabel = stringResource(R.string.label_lens)

    return remember(searchProvider, themed, showMic, showLens, searchLabel, voiceSearchLabel, lensLabel) {
        val iconRes = if (themed) searchProvider.themedIcon else searchProvider.icon
        val isGoogleProvider = searchProvider == Google || searchProvider == GoogleGo || searchProvider == PixelSearch

        QsbState(
            contentDescription = searchLabel,
            startIcon = QsbIconState(
                id = QsbIconId.SEARCH,
                resId = iconRes,
                themed = themed || iconRes == R.drawable.ic_qsb_search,
                method = searchProvider.themingMethod,
                contentDescription = searchLabel,
            ),
            endIcons = listOf(
                QsbIconState(
                    id = QsbIconId.MIC,
                    resId = if (isGoogleProvider) R.drawable.ic_mic_color else R.drawable.ic_mic_flat,
                    themed = (isGoogleProvider && themed) || !isGoogleProvider,
                    method = if (isGoogleProvider) ThemingMethod.THEME_BY_LAYER_ID else ThemingMethod.TINT,
                    contentDescription = voiceSearchLabel,
                    visible = showMic,
                ),
                QsbIconState(
                    id = QsbIconId.LENS,
                    resId = R.drawable.ic_lens_color,
                    themed = themed,
                    method = ThemingMethod.THEME_BY_LAYER_ID,
                    contentDescription = lensLabel,
                    visible = showLens,
                ),
            ),
        )
    }
}

@Composable
fun rememberAllAppsQsbState(
    searchProvider: QsbSearchProvider,
    themed: Boolean,
    shouldShowIcons: Boolean,
    queryEmpty: Boolean,
    showMic: Boolean,
    showLens: Boolean,
): QsbState {
    val searchLabel = stringResource(R.string.label_search)
    val voiceSearchLabel = stringResource(R.string.label_voice_search)
    val lensLabel = stringResource(R.string.label_lens)
    val clearLabel = stringResource(R.string.search_input_action_clear_results)

    return remember(searchProvider, themed, shouldShowIcons, queryEmpty, showMic, showLens, searchLabel, voiceSearchLabel, lensLabel, clearLabel) {
        val iconRes = if (themed && shouldShowIcons) searchProvider.themedIcon else searchProvider.icon
        val resId = if (shouldShowIcons) iconRes else R.drawable.ic_qsb_search
        val isGoogleProvider = searchProvider == Google || searchProvider == GoogleGo || searchProvider == PixelSearch

        QsbState(
            contentDescription = searchLabel,
            startIcon = QsbIconState(
                id = QsbIconId.SEARCH,
                resId = resId,
                themed = themed || resId == R.drawable.ic_qsb_search,
                method = if (shouldShowIcons) searchProvider.themingMethod else ThemingMethod.TINT,
                contentDescription = searchLabel,
            ),
            endIcons = listOf(
                QsbIconState(
                    id = QsbIconId.MIC,
                    resId = if (isGoogleProvider) R.drawable.ic_mic_color else R.drawable.ic_mic_flat,
                    themed = (isGoogleProvider && themed) || !isGoogleProvider,
                    method = if (isGoogleProvider) ThemingMethod.THEME_BY_LAYER_ID else ThemingMethod.TINT,
                    contentDescription = voiceSearchLabel,
                    visible = shouldShowIcons && showMic && queryEmpty,
                ),
                QsbIconState(
                    id = QsbIconId.LENS,
                    resId = R.drawable.ic_lens_color,
                    themed = themed,
                    method = ThemingMethod.THEME_BY_LAYER_ID,
                    contentDescription = lensLabel,
                    visible = shouldShowIcons && showLens && queryEmpty,
                ),
                QsbIconState(
                    id = QsbIconId.CLEAR,
                    resId = R.drawable.ic_remove_no_shadow,
                    themed = true,
                    method = ThemingMethod.TINT,
                    contentDescription = clearLabel,
                    visible = !queryEmpty,
                ),
            ),
        )
    }
}

fun getHotseatQsbCornerRadius(context: Context, cornerRadiusFactor: Float): Float {
    val resources = context.resources
    val qsbWidgetHeight = resources.getDimension(R.dimen.qsb_widget_height)
    val qsbWidgetPadding = resources.getDimension(R.dimen.qsb_widget_vertical_padding)
    val innerHeight = qsbWidgetHeight - 2 * qsbWidgetPadding
    return innerHeight / 2 * cornerRadiusFactor
}

@Composable
fun LawnQsbUi(
    state: QsbState,
    style: QsbStyle,
    actions: QsbActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val cornerRadius = with(density) { style.cornerRadiusPx.toDp() }
    val strokeWidth = with(density) { style.strokeWidthPx.toDp() }
    val shape = RoundedCornerShape(cornerRadius)

    val containerModifier = modifier
        .fillMaxWidth()
        .semantics { contentDescription = state.contentDescription }
        .clip(shape)
        .background(ComposeColor(style.backgroundColor).copy(alpha = style.backgroundAlpha), shape)
        .clickable(
            onClick = actions.onQsbClick,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(
                color = MaterialTheme.colorScheme.onSurface,
                focusRingShape = shape,
            ),
        )
        .addIf(style.strokeWidthPx > 0f) {
            border(strokeWidth, ComposeColor(style.strokeColor), shape)
        }

    Row(
        modifier = containerModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .requiredWidth(dimensionResource(R.dimen.qsb_icon_width))
                .fillMaxHeight()
                .then(
                    if (actions.onStartIconClick != null) {
                        Modifier
                            .clip(shape)
                            .qsbClickable(
                                onClick = actions.onStartIconClick,
                                shape = shape,
                            )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberThemedIconPainter(
                    resId = state.startIcon.resId,
                    themed = state.startIcon.themed,
                    method = state.startIcon.method,
                ),
                contentDescription = state.startIcon.contentDescription,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        state.endIcons.forEachIndexed { index, icon ->
            val isLastVisible = remember(state.endIcons, index) {
                state.endIcons.drop(index + 1).none { it.visible }
            }
            AnimatedVisibility(
                visible = icon.visible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                QsbIcon(
                    icon = icon,
                    shape = shape,
                    onClick = { actions.onEndIconClick(icon.id) },
                    modifier = Modifier.addIf(isLastVisible) {
                        offset(x = (-6).dp)
                    },
                )
            }
        }
    }
}

@Composable
fun QsbIcon(
    icon: QsbIconState,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .requiredWidth(dimensionResource(R.dimen.qsb_icon_width))
            .fillMaxHeight()
            .clip(shape)
            .qsbClickable(
                onClick = onClick,
                shape = shape,
            )
            .padding(dimensionResource(R.dimen.qsb_icon_padding)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = rememberThemedIconPainter(
                resId = icon.resId,
                themed = icon.themed,
                method = icon.method,
            ),
            contentDescription = icon.contentDescription,
            tint = ComposeColor.Unspecified,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Modifier.qsbClickable(
    onClick: () -> Unit,
    shape: Shape,
) = this.clickable(
    onClick = onClick,
    role = Role.Button,
    interactionSource = remember { MutableInteractionSource() },
    indication = ripple(
        color = MaterialTheme.colorScheme.onSurface,
        focusRingShape = shape,
    ),
)

@PreviewLawnchair
@Composable
private fun LawnQsbUiPreview() {
    LawnchairTheme {
        LawnQsbUi(
            state = QsbState(
                contentDescription = "Search",
                startIcon = QsbIconState(
                    id = QsbIconId.SEARCH,
                    resId = R.drawable.ic_qsb_search,
                    themed = false,
                    contentDescription = "Search",
                    method = ThemingMethod.TINT,
                ),
                endIcons = listOf(
                    QsbIconState(
                        id = QsbIconId.MIC,
                        resId = R.drawable.ic_mic_flat,
                        themed = false,
                        contentDescription = "Voice Search",
                        method = ThemingMethod.TINT,
                    ),
                    QsbIconState(
                        id = QsbIconId.LENS,
                        resId = R.drawable.ic_lens_color,
                        themed = false,
                        contentDescription = "Lens",
                        method = ThemingMethod.THEME_BY_LAYER_ID,
                    ),
                ),
            ),
            style = QsbStyle(
                themed = false,
                backgroundAlpha = 1f,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.toArgb(),
                strokeColor = MaterialTheme.colorScheme.outline.toArgb(),
                strokeWidthPx = 1f,
                cornerRadiusPx = 100f,
            ),
            actions = QsbActions(
                onQsbClick = {},
                onStartIconClick = {},
                onEndIconClick = {},
            ),
            modifier = Modifier
                .padding(16.dp)
                .height(52.dp),
        )
    }
}
