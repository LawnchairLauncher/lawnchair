package app.lawnchair.ui.preferences.about.licenses

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.navArgument
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.ui.preferences.preferenceGraph
import app.lawnchair.ui.preferences.subRoute
import com.android.launcher3.R
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder

@ExperimentalAnimationApi
fun NavGraphBuilder.licensesGraph(route: String) {
    preferenceGraph(route, { Licenses() }) { subRoute ->
        composable(
            route = subRoute("{licenseIndex}"),
            arguments = listOf(navArgument("licenseIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            LicensePage(index = backStackEntry.arguments!!.getInt("licenseIndex"))
        }
    }
}

@ExperimentalAnimationApi
@Composable
fun Licenses() {
    val optionalLicenses by LocalPreferenceInteractor.current.licenses
    LoadingScreen(optionalLicenses) { licenses ->
        PreferenceLayoutLazyColumn(label = stringResource(id = R.string.acknowledgements)) {
            preferenceGroupItems(licenses, isFirstChild = true) { index, license ->
                LicenseItem(license = license, index = index, showDivider = index != licenses.lastIndex)
            }
        }
    }
}

@Composable
fun LicenseItem(license: License, index: Int, showDivider: Boolean) {
    val navController = LocalNavController.current
    val destination = subRoute(name = "$index")

    PreferenceTemplate(
        height = 52.dp,
        showDivider = showDivider
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clickable { navController.navigate(route = destination) }
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = license.name,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@ExperimentalAnimationApi
@Composable
fun LicensePage(index: Int) {
    val optionalLicenses by LocalPreferenceInteractor.current.licenses
    val license = optionalLicenses?.get(index)
    val dataState = license?.let { loadLicense(license = license) }
    val data = dataState?.value

    PreferenceLayout(
        label = license?.name ?: stringResource(id = R.string.loading)
    ) {
        Crossfade(targetState = data) { it ->
            if (it != null) {
                val uriHandler = LocalUriHandler.current
                val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
                val pressIndicator = Modifier.pointerInput(Unit) {
                    detectTapGestures { pos ->
                        layoutResult.value?.let { layoutResult ->
                            val position = layoutResult.getOffsetForPosition(pos)
                            val annotation =
                                it.data.getStringAnnotations(position, position).firstOrNull()
                            if (annotation?.tag == "URL") {
                                uriHandler.openUri(annotation.item)
                            }
                        }
                    }
                }

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                        .then(pressIndicator),
                    text = it.data,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    onTextLayout = {
                        layoutResult.value = it
                    }
                )
            } else {
                Text(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                        .placeholder(
                            visible = true,
                            highlight = PlaceholderHighlight.fade(),
                        ),
                    text = "a".repeat(license?.length ?: 20),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }
    }
}
