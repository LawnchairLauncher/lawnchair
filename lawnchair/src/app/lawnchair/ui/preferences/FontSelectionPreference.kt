package app.lawnchair.ui.preferences

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.RadioButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.lawnchair.font.FontCache
import app.lawnchair.font.googlefonts.GoogleFontsListing
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.AndroidText
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.*
import com.android.launcher3.R

fun NavGraphBuilder.fontSelectionGraph(route: String) {
    preferenceGraph(route, {}) { subRoute ->
        composable(
            route = subRoute("{prefKey}"),
            arguments = listOf(
                navArgument("prefKey") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val prefKey = args.getString("prefKey")!!
            val pref = preferenceManager().prefsMap[prefKey]
                    as? BasePreferenceManager.FontPref ?: return@composable
            FontSelection(pref)
        }
    }
}

private enum class ContentType {
    ADD_BUTTON, FONT
}

@Composable
fun FontSelection(fontPref: BasePreferenceManager.FontPref) {
    val context = LocalContext.current
    val customFonts by remember { FontCache.INSTANCE.get(context).customFonts }.collectAsState(initial = emptyList())
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
    val allItems by remember { derivedStateOf { items + customFonts } }
    val adapter = fontPref.getAdapter()
    var searchQuery by remember { mutableStateOf("") }

    val hasFilter by remember { derivedStateOf { searchQuery.isNotEmpty() } }
    val filteredItems by remember {
        derivedStateOf {
            if (hasFilter) {
                val lowerCaseQuery = searchQuery.lowercase()
                allItems.filter { it.displayName.lowercase().contains(lowerCaseQuery) }
            } else items
        }
    }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        try {
            FontCache.INSTANCE.get(context).addCustomFont(uri)
        } catch (e: FontCache.AddFontException) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    PreferenceSearchScaffold(
        searchInput = {
            SearchTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxSize(),
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.label_search),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium),
                    )
                },
                singleLine = true
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
            if (!hasFilter) {
                item(contentType = { ContentType.ADD_BUTTON }) {
                    PreferenceGroupItem(
                        modifier = Modifier.padding(top = 8.dp),
                        cutBottom = customFonts.isNotEmpty()
                    ) {
                        PreferenceTemplate(
                            modifier = Modifier.clickable {
                                val intent = Intent(Intent.ACTION_GET_CONTENT)
                                intent.addCategory(Intent.CATEGORY_OPENABLE)
                                intent.type = "*/*"
                                request.launch(intent)
                            },
                            title = { Text(stringResource(id = R.string.pref_fonts_add_fonts)) },
                            description = { Text(stringResource(id = R.string.pref_fonts_add_fonts_summary)) },
                            startWidget = {
                                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                            }
                        )
                    }
                }
                itemsIndexed(
                    items = customFonts,
                    key = { _, family -> family.toString() },
                    contentType = { _, _ -> ContentType.FONT }
                ) { index, family ->
                    PreferenceGroupItem(
                        cutTop = true,
                        cutBottom = index != customFonts.lastIndex
                    ) {
                        PreferenceDivider(startIndent = 40.dp)
                        FontSelectionItem(
                            adapter = adapter,
                            family = family,
                            onDelete = {
                                val selected = family.variants.any { it.value == adapter.state.value }
                                if (selected) {
                                    fontPref.set(fontPref.defaultValue)
                                }
                                (family.default as? FontCache.TTFFont)?.delete()
                            }
                        )
                    }
                }
            }
            preferenceGroupItems(
                filteredItems,
                isFirstChild = false,
                key = { _, family -> family.toString() },
                contentType = { ContentType.FONT },
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
    family: FontCache.Family,
    onDelete: (() -> Unit)? = null
) {
    val selected = family.variants.any { it.value == adapter.state.value }
    PreferenceTemplate(
        modifier = Modifier
            .clickable { adapter.onChange(family.default) },
        title = {
            Box(modifier = Modifier.height(52.dp)) {
                Text(
                    text = family.displayName,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(),
                    fontFamily = family.default.composeFontFamily
                )
            }
        },
        startWidget = {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
            )
        },
        endWidget = when {
            selected && family.variants.size > 1 -> {
                {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(end = 16.dp)
                    ) {
                        VariantDropdown(adapter = adapter, family = family)
                    }
                }
            }
            onDelete != null -> {
                {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(id = R.string.delete),
                            tint = LocalContentColor.current.copy(alpha = ContentAlpha.medium)
                        )
                    }
                }
            }
            else -> null
        },
        applyPaddings = false,
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
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
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
                    fontFamily = font.composeFontFamily
                )
            }
        }
    }
}
