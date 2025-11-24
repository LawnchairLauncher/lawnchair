package app.lawnchair.ui.preferences.destinations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.popup.LauncherOptionPopupItem
import app.lawnchair.ui.popup.LauncherOptionsPopup
import app.lawnchair.ui.popup.toLauncherOptions
import app.lawnchair.ui.popup.toOptionOrderString
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.DragHandle
import app.lawnchair.ui.preferences.components.DraggablePreferenceGroup
import app.lawnchair.ui.preferences.components.DraggableSwitchPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.HomeScreenPopupEditor
import app.lawnchair.ui.theme.isSelectedThemeDark
import com.android.launcher3.R

@Composable
fun LauncherPopupPreferenceItem(
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    ClickablePreference(
        modifier = modifier,
        label = stringResource(R.string.edit_menu_items),
        onClick = {
            navController.navigate(HomeScreenPopupEditor)
        },
    )
}

@Composable
fun LauncherPopupPreference(
    modifier: Modifier = Modifier,
) {
    val prefs2 = preferenceManager2()
    val optionsPref = prefs2.launcherPopupOrder.getAdapter()

    val isHomeScreenLocked = prefs2.lockHomeScreen.getAdapter().state.value

    var optionsList = optionsPref.state.value.toLauncherOptions()

    PreferenceLayout(
        label = stringResource(R.string.popup_menu),
        backArrowVisible = !LocalIsExpandedScreen.current,
        isExpandedScreen = true,
        modifier = modifier,
    ) {
        Column {
            PreferenceGroupHeading(stringResource(R.string.preview_label))
            LauncherPopupPreview(optionsList)
        }

        DraggablePreferenceGroup(
            label = stringResource(R.string.popup_menu_items),
            items = optionsList,
            defaultList = LauncherOptionsPopup.DEFAULT_ORDER,
            onOrderChange = {
                optionsList = it
                optionsPref.onChange(it.toOptionOrderString())
            },
        ) { item, index, _, onDraggingChange ->
            val metadata = LauncherOptionsPopup.getMetadataForOption(item.identifier)

            val enabled = when (item.identifier) {
                "edit_mode", "widgets" -> (!isHomeScreenLocked)
                "home_settings" -> false
                else -> true
            }

            val interactionSource = remember { MutableInteractionSource() }

            DraggableSwitchPreference(
                label = stringResource(metadata.label),
                description = if (!enabled && item.identifier != "home_settings") stringResource(R.string.home_screen_locked) else null,
                checked = if (!enabled && item.identifier != "home_settings") false else item.isEnabled,
                onCheckedChange = {
                    optionsList[index].isEnabled = it
                    optionsPref.onChange(optionsList.toOptionOrderString())
                },
                dragIndicator = {
                    DragHandle(
                        scope = this,
                        interactionSource = if (!metadata.isCarousel) interactionSource else remember { MutableInteractionSource() },
                        isDraggable = !metadata.isCarousel,
                        onDragStop = {
                            onDraggingChange(false)
                        },
                    )
                },
                enabled = enabled,
                interactionSource = interactionSource,
            )
        }
    }
}

@Composable
private fun LauncherPopupPreview(optionsList: List<LauncherOptionPopupItem>) {
    key(optionsList) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large),
            color = if (isSelectedThemeDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceDim,
        ) {
            val enabledItems = optionsList.filter { it.isEnabled }
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                optionsList.forEach {
                    val metadata = LauncherOptionsPopup.getMetadataForOption(it.identifier)

                    val isSingleItem = enabledItems.size == 1

                    val isFirst = enabledItems.indexOf(it) == 0
                    val isLast = enabledItems.indexOf(it) == enabledItems.lastIndex

                    val clipRadius = MaterialTheme.shapes.large
                    val defaultCorner = CornerSize(10)

                    val shape = when {
                        isSingleItem -> CircleShape

                        isFirst -> RoundedCornerShape(
                            clipRadius.topStart,
                            clipRadius.topEnd,
                            defaultCorner,
                            defaultCorner,
                        )

                        isLast -> RoundedCornerShape(
                            defaultCorner,
                            defaultCorner,
                            clipRadius.bottomStart,
                            clipRadius.bottomEnd,
                        )

                        else -> RoundedCornerShape(defaultCorner)
                    }

                    AnimatedVisibility(
                        it in enabledItems,
                    ) {
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceBright,
                            modifier = Modifier
                                .widthIn(max = 240.dp),
                        ) {
                            if (metadata.isCarousel) {
                                WallpaperCarouselPreview()
                            } else {
                                OptionsItemRow(
                                    icon = painterResource(metadata.icon),
                                    label = stringResource(metadata.label),
                                )
                            }
                        }
                    }
                    if (!isLast && enabledItems.indexOf(it) != -1) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperCarouselPreview(modifier: Modifier = Modifier) {
    val height = 100.dp
    val width = 50.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Box(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .size(height),
        ) {}
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .height(height)
                .width(width),
        ) {}
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .height(height)
                .width(width),
        ) {}
    }
}

@Composable
private fun OptionsItemRow(
    icon: Painter,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label)
        }
    }
}
