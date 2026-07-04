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

/**
 * Enum representing the unique identifiers for icons displayed within the Quick Search Bar (QSB).
 */
enum class QsbIconId {
    SEARCH,
    MIC,
    LENS,
    CLEAR,
}

/**
 * Represents the state and visual configuration of an individual icon within the Quick Search Bar (QSB).
 *
 * @property id The unique identifier for the icon (e.g., SEARCH, MIC, LENS).
 * @property resId The drawable resource ID used for the icon's imagery.
 * @property themed Whether the icon should adapt its colors based on the current system or launcher theme.
 * @property contentDescription The accessibility label for the icon.
 * @property method The specific [ThemingMethod] to apply when rendering the icon (e.g., tinting or layer-based theming).
 * @property visible Whether the icon should be displayed in the UI.
 */
@Immutable
data class QsbIconState(
    val id: QsbIconId,
    @param:DrawableRes val resId: Int,
    val themed: Boolean,
    val contentDescription: String,
    val method: ThemingMethod = ThemingMethod.THEME_BY_LAYER_ID,
    val visible: Boolean = true,
)

/**
 * Defines the visual appearance and styling of the Quick Search Bar (QSB).
 *
 * @property themed Whether the background and stroke should follow the system or launcher theme colors.
 * @property backgroundAlpha The transparency level of the QSB background, ranging from 0.0 to 1.0.
 * @property backgroundColor The color value used for the QSB background fill.
 * @property strokeColor The color value used for the QSB's outline/border.
 * @property strokeWidthPx The thickness of the QSB's border in pixels.
 * @property cornerRadiusPx The radius used for the rounded corners of the QSB in pixels.
 */
@Immutable
data class QsbStyle(
    val themed: Boolean,
    val backgroundAlpha: Float,
    @param:ColorInt val backgroundColor: Int,
    @param:ColorInt val strokeColor: Int,
    val strokeWidthPx: Float,
    val cornerRadiusPx: Float,
)

/**
 * Represents the complete visual state of the Quick Search Bar (QSB).
 *
 * @property contentDescription The accessibility label for the entire search bar container.
 * @property startIcon The state of the primary icon displayed at the beginning of the bar (usually the search provider logo).
 * @property endIcons A list of icons to be displayed at the end of the bar (e.g., Microphone, Lens, or Clear).
 */
@Immutable
data class QsbState(
    val contentDescription: String,
    val startIcon: QsbIconState,
    val endIcons: List<QsbIconState>,
)

/**
 * Defines the click handlers for the various interactive elements within the Quick Search Bar (QSB).
 *
 * @property onQsbClick Callback invoked when the main body of the search bar is clicked.
 * @property onStartIconClick Optional callback invoked when the leading icon (e.g., search provider logo) is clicked.
 * @property onEndIconClick Callback invoked when one of the trailing icons (e.g., Mic, Lens, or Clear) is clicked,
 * passing the specific [QsbIconId] of the clicked icon.
 */
@Immutable
data class QsbActions(
    val onQsbClick: () -> Unit,
    val onStartIconClick: (() -> Unit)? = null,
    val onEndIconClick: ((id: QsbIconId) -> Unit),
)

/**
 * Builds a [QsbStyle] instance based on the provided parameters.
 *
 * @param context The context used to resolve colors and dimensions.
 * @param themed Whether the search bar background and components should follow the system theme.
 * @param backgroundAlpha The background opacity, provided as a percentage (0-100).
 * @param backgroundColor The integer color value for the search bar background.
 * @param cornerRadius A factor used to calculate the final corner radius.
 * @param strokeColor The color of the search bar border. If null, the theme's accent color is used.
 * @param strokeWidth The width of the search bar border in pixels.
 * @return A [QsbStyle] object containing the processed styling configuration.
 */
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

/**
 * Creates and remembers a [QsbState] tailored for the Hotseat (dock) search bar.
 *
 * This function handles the logic for selecting appropriate icons and theming based on the
 * chosen [searchProvider] and visibility settings for the microphone and lens icons.
 *
 * @param searchProvider The search engine provider currently selected.
 * @param themed Whether the icons should use themed (monochrome) variants if available.
 * @param showMic Whether the voice search (microphone) icon should be visible.
 * @param showLens Whether the Google Lens icon should be visible.
 * @return A remembered [QsbState] containing the configuration for the Hotseat QSB.
 */
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

/**
 * Creates and remembers a [QsbState] tailored for the All Apps search bar.
 *
 * This function handles the logic for selecting appropriate icons and theming based on the
 * chosen [searchProvider] and visibility settings for the microphone, lens, and clear icons.
 *
 * @param searchProvider The search engine provider currently selected.
 * @param themed Whether the icons should use themed (monochrome) variants if available.
 * @param shouldShowIcons Whether the icons should be visible.
 * @param queryEmpty Whether the search query is empty.
 * @param showMic Whether the voice search (microphone) icon should be visible.
 * @param showLens Whether the Google Lens icon should be visible.
 * @return A remembered [QsbState] containing the configuration for the All Apps QSB.
 */
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

/**
 * Calculates the corner radius for the Hotseat Quick Search Bar (QSB) based on a scaling factor.
 */
fun getHotseatQsbCornerRadius(context: Context, cornerRadiusFactor: Float): Float {
    val resources = context.resources
    val qsbWidgetHeight = resources.getDimension(R.dimen.qsb_widget_height)
    val qsbWidgetPadding = resources.getDimension(R.dimen.qsb_widget_vertical_padding)
    val innerHeight = qsbWidgetHeight - 2 * qsbWidgetPadding
    return innerHeight / 2 * cornerRadiusFactor
}

/**
 * Calculates the default background color for the Hotseat Quick Search Bar (QSB).
 */
fun getHotseatBackgroundColor(context: Context, themed: Boolean, themedBackgroundColor: Int? = null): Int {
    return if (themed) {
        themedBackgroundColor ?: Themes.getColorBackgroundFloating(context)
    } else {
        Themes.getAttrColor(context, R.attr.qsbFillColor)
    }
}

/**
 * A Composable that represents the UI for the Quick Search Bar (QSB).
 *
 * This component renders a search bar with a customizable background, optional border,
 * a starting icon (typically the search provider logo), and a set of trailing icons
 * (such as microphone or lens) based on the provided state.
 *
 * @param state The visual state of the QSB, including the icons and content descriptions.
 * @param style The styling configuration, including colors, transparency, and corner radius.
 * @param actions The click handlers for the main bar and individual icons.
 * @param modifier The [Modifier] to be applied to the QSB container.
 */
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

/**
 * A composable representing an icon button within the Quick Search Bar (QSB).
 *
 * @param icon the state defining the icon's resource, theming, and accessibility description.
 * @param shape the shape used for clipping and the ripple indication.
 * @param onClick the callback to be invoked when the icon is clicked.
 * @param modifier the [Modifier] to be applied to this icon container.
 */
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
