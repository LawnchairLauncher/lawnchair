package app.lawnchair.ui.preferences

import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavGraphBuilder
import app.lawnchair.font.FontCache
import app.lawnchair.font.googlefonts.GoogleFontsListing
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import app.lawnchair.ui.preferences.components.preferenceGroupItems
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
    val items = produceState(initialValue = emptyList<FontCache.Font>()) {
        val list = mutableListOf<FontCache.Font>()
        list.add(FontCache.SystemFont("sans-serif"))
        list.add(FontCache.SystemFont("sans-serif-medium"))
        list.add(FontCache.SystemFont("sans-serif-condensed"))
        GoogleFontsListing.INSTANCE.get(context).getFonts().mapTo(list) { font ->
            val variantsMap = HashMap<String, FontCache.Font>()
            val variants = font.variants.toTypedArray()
            font.variants.forEach { variant ->
                variantsMap[variant] = FontCache.GoogleFont(context, font.family, variant, variants)
            }
            variantsMap.getOrElse("regular") { variantsMap.values.first() }
        }
        value = list
    }
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
        preferenceGroupItems(items.value, isFirstChild = true) { index, font ->
            PreferenceTemplate(
                height = 52.dp,
                showDivider = index != 0,
                dividerIndent = 40.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { fontPref.set(font) }
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp)
                ) {
                    RadioButton(
                        selected = fontPref.getAdapter().state.value == font,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(
                            unselectedColor = MaterialTheme.colors.onBackground.copy(alpha = 0.48F)
                        )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    FontPreview(font = font)
                }
            }
        }
    }
}

@Composable
fun FontPreview(font: FontCache.Font) {
    val context = LocalContext.current
    val textView = remember { AppCompatTextView(context) }
    LaunchedEffect(font) {
        textView.text = font.fullDisplayName
        textView.typeface = font.load()
    }
    AndroidView(factory = { textView })
}
