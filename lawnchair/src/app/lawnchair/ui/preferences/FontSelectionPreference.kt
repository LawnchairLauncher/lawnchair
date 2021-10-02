package app.lawnchair.ui.preferences

import android.view.Gravity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import app.lawnchair.font.FontCache
import app.lawnchair.font.googlefonts.GoogleFontsListing
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.AndroidText
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import app.lawnchair.ui.preferences.components.preferenceGroupItems
import app.lawnchair.ui.preferences.views.CustomFontTextView
import app.lawnchair.ui.util.ViewPool
import app.lawnchair.ui.util.rememberViewPool
import com.android.launcher3.R

@ExperimentalAnimationApi
fun NavGraphBuilder.fontSelectionGraph(route: String) {
    preferenceGraph(route, {
        // TODO: remove hardcoded reference
        FontSelection(preferenceManager().workspaceFont)
    })
}

@ExperimentalAnimationApi
@Composable
fun FontSelection(fontPref: BasePreferenceManager.FontPref) {
    val context = LocalContext.current
    val items = produceState(initialValue = emptyList<FontCache.Family>()) {
        val list = mutableListOf<FontCache.Family>()
        list.add(FontCache.Family(FontCache.SystemFont("sans-serif")))
        list.add(FontCache.Family(FontCache.SystemFont("sans-serif-medium")))
        list.add(FontCache.Family(FontCache.SystemFont("sans-serif-condensed")))
        GoogleFontsListing.INSTANCE.get(context).getFonts().mapTo(list) { font ->
            val variantsMap = HashMap<String, FontCache.Font>()
            val variants = font.variants.toTypedArray()
            font.variants.forEach { variant ->
                variantsMap[variant] = FontCache.GoogleFont(context, font.family, variant, variants)
            }
            FontCache.Family(font.family, variantsMap)
        }
        value = list
    }
    val adapter = fontPref.getAdapter()
    val textViewPool = rememberViewPool { CustomFontTextView(it) }
    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.font_label),
        actions = {
            OverflowMenu {
                DropdownMenuItem(onClick = {
                    fontPref.set(fontPref.defaultValue)
                    hideMenu()
                }) {
                    Text(text = stringResource(id = R.string.reset_font))
                }
            }
        }
    ) {
        preferenceGroupItems(items.value, isFirstChild = true) { index, family ->
            FontSelectionItem(
                textViewPool = textViewPool,
                adapter = adapter,
                family = family,
                showDivider = index != 0
            )
        }
    }
}

@Composable
fun FontSelectionItem(
    textViewPool: ViewPool<CustomFontTextView>,
    adapter: PreferenceAdapter<FontCache.Font>,
    family: FontCache.Family,
    showDivider: Boolean
) {
    val selected = family.variants.any { it.value == adapter.state.value }
    PreferenceTemplate(
        title = {
            AndroidText(
                textView = textViewPool.rememberView().apply {
                    gravity = Gravity.CENTER_VERTICAL
                },
                modifier = Modifier
                    .height(52.dp)
                    .fillMaxWidth(),
                update = {
                    it.text = family.displayName
                    it.setFont(family.default)
                }
            )
        },
        startWidget = {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    unselectedColor = MaterialTheme.colors.onBackground.copy(alpha = 0.48F)
                )
            )
        },
        endWidget = if (selected && family.variants.size > 1) { {
            var showFamily by remember { mutableStateOf(false) }
            val selectedFont = adapter.state.value
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth()
            ) {
                TextButton(
                    onClick = { showFamily = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.onBackground)
                ) {
                    AndroidText(
                        modifier = Modifier.wrapContentWidth(),
                        update = {
                            it.text = selectedFont.displayName
                            it.setFont(selectedFont)
                        }
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = showFamily,
                    onDismissRequest = { showFamily = false }
                ) {
                    family.sortedVariants.forEach { font ->
                        DropdownMenuItem(onClick = {
                            adapter.onChange(font)
                            showFamily = false
                        }) {
                            AndroidText(
                                modifier = Modifier.wrapContentWidth(),
                                update = {
                                    it.text = font.displayName
                                    it.setFont(font)
                                }
                            )
                        }
                    }
                }
            }
        } } else null,
        modifier = Modifier.clickable { adapter.onChange(family.default) },
        showDivider = showDivider,
        dividerIndent = 40.dp,
        verticalPadding = 0.dp
    )
}
