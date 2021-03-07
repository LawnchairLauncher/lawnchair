package ch.deletescape.lawnchair.compose.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.android.launcher3.R

sealed class Screen(
    val route: String,
    @StringRes val titleResId: Int,
    @StringRes val subtitleResId: Int? = null,
    @DrawableRes val iconResId: Int? = null
) {
    object Top : Screen(route = "top", titleResId = R.string.settings)
    object GeneralSettings : Screen(
        route = "generalSettings",
        titleResId = R.string.general_label,
        subtitleResId = R.string.general_description,
        iconResId = R.drawable.ic_general
    )
    object HomeScreenSettings : Screen(
        route = "homeScreenSettings",
        titleResId = R.string.home_screen_label,
        subtitleResId = R.string.home_screen_description,
        iconResId = R.drawable.ic_home_screen
    )
}

val screens = listOf(
    Screen.GeneralSettings,
    Screen.HomeScreenSettings
)

@ExperimentalAnimationApi
@Composable
fun Settings() {
    val navController = rememberNavController()

    Column(
        modifier = Modifier
            .background(MaterialTheme.colors.background)
            .fillMaxWidth()
    ) {
        TopBar(navController = navController)
        NavHost(navController = navController, startDestination = Screen.Top.route) {
            composable(route = Screen.Top.route) { Top(navController) }
            composable(route = Screen.HomeScreenSettings.route) { HomeScreenSettings() }
            composable(route = Screen.GeneralSettings.route) { GeneralSettings() }
        }
    }
}

@Composable
private fun Top(navController: NavController) {
    LazyColumn {
        items(screens) { screen ->
            ScreenRow(
                titleResId = screen.titleResId,
                subtitleResId = screen.subtitleResId,
                iconResId = screen.iconResId,
                onClick = { navController.navigate(screen.route) })
        }
    }
}

@ExperimentalAnimationApi
@Composable
private fun TopBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.arguments?.getString(KEY_ROUTE)

    val title = when (currentRoute) {
        "top" -> stringResource(id = R.string.settings)
        "homeScreenSettings" -> stringResource(id = R.string.home_screen_label)
        "generalSettings" -> stringResource(id = R.string.general_label)
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(4.dp)
            .background(MaterialTheme.colors.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = currentRoute != "top" && currentRoute != null) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .height(40.dp)
                    .width(40.dp)
                    .clip(shape = RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier,
                    tint = MaterialTheme.colors.onBackground
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun ScreenRow(titleResId: Int, onClick: () -> Unit, subtitleResId: Int?, iconResId: Int?) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .height(64.dp)
            .padding(start = 16.dp, end = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        iconResId?.let {
            Image(
                painter = painterResource(id = it),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
                modifier = Modifier
                    .width(32.dp)
                    .height(32.dp)
            )
        }
        Column(Modifier.padding(start = 24.dp)) {
            Text(text = stringResource(id = titleResId), style = MaterialTheme.typography.subtitle1)
            subtitleResId?.let {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(text = stringResource(id = it), style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}