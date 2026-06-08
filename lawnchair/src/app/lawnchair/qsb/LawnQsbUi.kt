package app.lawnchair.qsb

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.lawnchair.animateToAllApps
import app.lawnchair.launcher
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.GoogleGo
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.theme.isSelectedThemeDark
import app.lawnchair.ui.util.addIf
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

enum class QsbIconId {
    SEARCH,
    MIC,
    LENS,
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
    val transparency: Float,
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
    val onEndIconClick: ((id: QsbIconId) -> Unit),
)

fun buildQsbStyle(
    context: Context,
    themed: Boolean,
    transparency: Int,
    cornerRadius: Float,
    strokeColor: Int?,
    strokeWidth: Float,
    themedBackgroundColor: Int? = null,
) = QsbStyle(
    themed = themed,
    transparency = transparency / 100f,
    backgroundColor = if (themed) {
        themedBackgroundColor ?: Themes.getColorBackgroundFloating(context)
    } else {
        Themes.getAttrColor(context, R.attr.qsbFillColor)
    },
    strokeColor = strokeColor ?: Themes.getColorAccent(context),
    strokeWidthPx = strokeWidth,
    cornerRadiusPx = getHotseatQsbCornerRadius(context, cornerRadius),
)

@Composable
fun rememberQsbState(
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

fun getHotseatQsbCornerRadius(context: Context, cornerRadiusFactor: Float): Float {
    val resources = context.resources
    val qsbWidgetHeight = resources.getDimension(R.dimen.qsb_widget_height)
    val qsbWidgetPadding = resources.getDimension(R.dimen.qsb_widget_vertical_padding)
    val innerHeight = qsbWidgetHeight - 2 * qsbWidgetPadding
    return innerHeight / 2 * cornerRadiusFactor
}

@Composable
fun getThemedQsbBackgroundColor(): Int {
    return ColorTokens.ColorBackground.resolveColor(
        LocalContext.current,
        if (isSelectedThemeDark) UiColorMode.Dark else UiColorMode.Light,
    )
}

@Composable
fun LawnQsbUi(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    val searchProvider by prefs2.hotseatQsbProvider.asState()
    val themed by prefs2.themedHotseatQsb.asState()

    val supportsLens = searchProvider == Google || searchProvider == PixelSearch
    val voiceIntent = remember(searchProvider, context) {
        AssistantIconView.getVoiceIntent(searchProvider, context)
    }
    val lensIntent = remember(supportsLens, context) {
        if (supportsLens) LawnQsbLayout.getLensIntent(context) else null
    }

    LawnQsbUi(
        state = rememberQsbState(
            searchProvider = LawnQsbLayout.getSearchProvider(context, prefs2),
            themed = themed,
            showMic = voiceIntent != null,
            showLens = lensIntent != null,
        ),
        style = buildQsbStyle(
            context = LocalContext.current,
            themed = themed,
            transparency = prefs.hotseatQsbAlpha.observeAsState().value,
            cornerRadius = prefs.hotseatQsbCornerRadius.observeAsState().value,
            strokeColor = prefs2.strokeColorStyle.asState().value.colorPreferenceEntry.lightColor.invoke(context),
            strokeWidth = prefs.hotseatQsbStrokeWidth.observeAsState().value,
        ),
        actions = QsbActions(
            onQsbClick = {
                val launcher = context.launcher
                launcher.lifecycleScope.launch {
                    if (prefs2.matchHotseatQsbStyle.firstBlocking()) {
                        launcher.appsView.searchUiManager.editText?.showKeyboard()
                        launcher.animateToAllApps()
                    } else {
                        searchProvider.launch(launcher)
                    }
                }
            },
            onEndIconClick = { id ->
                runCatching {
                    when (id) {
                        QsbIconId.MIC -> voiceIntent?.let { context.startActivity(it) }
                        QsbIconId.LENS -> lensIntent?.let { context.startActivity(it) }
                        else -> null
                    }
                }
            },
        ),
        modifier = modifier,
    )
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
        .background(ComposeColor(style.backgroundColor).copy(alpha = style.transparency), shape)
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

    Box(modifier = containerModifier) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        ) {
            state.endIcons.forEachIndexed { index, icon ->
                if (icon.visible) {
                    QsbIcon(
                        icon = icon,
                        shape = shape,
                        onClick = { actions.onEndIconClick(icon.id) },
                        modifier = Modifier.addIf(index == state.endIcons.lastIndex) {
                            // Compensation for the extra padding on the right side of the hotseat
                            offset(x = (-6).dp)
                        },
                    )
                }
            }
        }

        Image(
            painter = rememberThemedIconPainter(
                resId = state.startIcon.resId,
                themed = state.startIcon.themed,
                method = state.startIcon.method,
            ),
            contentDescription = state.startIcon.contentDescription,
            modifier = Modifier
                .padding(start = dimensionResource(R.dimen.qsb_g_icon_marginStart))
                .align(Alignment.CenterStart)
                .size(24.dp),
        )
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
            .clickable(
                onClick = onClick,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    color = MaterialTheme.colorScheme.onSurface,
                    focusRingShape = shape,
                ),
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
            tint = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
