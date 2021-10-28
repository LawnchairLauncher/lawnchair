package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import app.lawnchair.font.FontCache
import app.lawnchair.font.googlefonts.GoogleFontsListing
import app.lawnchair.font.toFontFamily
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.AndroidText
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.*
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
    val items by produceState(initialValue = emptyList<FontCache.Family>()) {
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
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems by derivedStateOf {
        if (searchQuery.isNotEmpty()) {
            val lowerCaseQuery = searchQuery.lowercase()
            items.filter { it.displayName.lowercase().contains(lowerCaseQuery) }
        } else items
    }

    PreferenceSearchScaffold(
        searchInput = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxSize(),
                placeholder = { Text(text = stringResource(id = R.string.label_search)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        ClickableIcon(
                            imageVector = Icons.Rounded.Clear,
                            onClick = { searchQuery = "" }
                        )
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent
                )
            )
        },
        actions = {
            OverflowMenu {
                DropdownMenuItem(onClick = {
                    fontPref.set(fontPref.defaultValue)
                    hideMenu()
                }) {
                    Text(text = stringResource(id = R.string.reset_font))
                }
            }
        },
    ) {
        PreferenceLazyColumn {
            preferenceGroupItems(
                filteredItems,
                isFirstChild = true,
                key = { _, family -> family.toString() },
                dividerStartIndent = 40.dp
            ) { _, family ->
                FontSelectionItem(
                    adapter = adapter,
                    family = family
                )
            }
        }
    }
}

@Composable
private fun FontSelectionItem(
    adapter: PreferenceAdapter<FontCache.Font>,
    family: FontCache.Family
) {
    val selected = family.variants.any { it.value == adapter.state.value }
    PreferenceTemplate(
        title = {
            Box(modifier = Modifier.height(52.dp)) {
                Text(
                    text = family.displayName,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(),
                    fontFamily = family.default.toFontFamily()?.getOrNull()
                )
            }
        },
        startWidget = {
            RadioButton(
                selected = selected,
                onClick = null
            )
        },
        endWidget = if (selected && family.variants.size > 1) {
            {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    VariantDropdown(adapter = adapter, family = family)
                }
            }
        } else null,
        modifier = Modifier.clickable { adapter.onChange(family.default) },
        verticalPadding = 0.dp
    )
}

private val VariantButtonContentPadding = PaddingValues(
    start = 8.dp,
    top = 8.dp,
    end = 0.dp,
    bottom = 8.dp
)

@Composable
private fun VariantDropdown(
    adapter: PreferenceAdapter<FontCache.Font>,
    family: FontCache.Family
) {
    val selectedFont = adapter.state.value
    var showVariants by remember { mutableStateOf(false) }

    val context = LocalContext.current
    DisposableEffect(family) {
        val fontCache = FontCache.INSTANCE.get(context)
        family.variants.forEach { fontCache.preloadFont(it.value) }
        onDispose { }
    }

    TextButton(
        onClick = { showVariants = true },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.onBackground),
        contentPadding = VariantButtonContentPadding
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
        expanded = showVariants,
        onDismissRequest = { showVariants = false }
    ) {
        family.sortedVariants.forEach { font ->
            DropdownMenuItem(onClick = {
                adapter.onChange(font)
                showVariants = false
            }) {
                Text(
                    text = font.displayName,
                    fontFamily = font.toFontFamily()?.getOrNull()
                )
            }
        }
    }
}
