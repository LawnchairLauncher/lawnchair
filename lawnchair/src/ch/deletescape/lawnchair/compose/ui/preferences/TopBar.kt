package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.KEY_ROUTE
import androidx.navigation.compose.currentBackStackEntryAsState
import com.android.launcher3.R

@ExperimentalAnimationApi
@Composable
fun TopBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.arguments?.getString(KEY_ROUTE)

    val title = when (currentRoute) {
        "top" -> stringResource(id = R.string.settings)
        "homeScreenSettings" -> stringResource(id = R.string.home_screen_label)
        "generalSettings" -> stringResource(id = R.string.general_label)
        "iconPackSettings" -> stringResource(id = R.string.icon_pack)
        "dockSettings" -> stringResource(id = R.string.dock_label)
        "appDrawerSettings" -> stringResource(id = R.string.app_drawer_label)
        "folderSettings" -> stringResource(id = R.string.folders_label)
        else -> ""
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(4.dp)
            .background(MaterialTheme.colors.surface)
    ) {
        AnimatedVisibility(visible = currentRoute != "top" && currentRoute != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .height(40.dp)
                    .width(40.dp)
                    .clip(CircleShape)
                    .clickable { navController.popBackStack() }
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier,
                    tint = MaterialTheme.colors.onBackground
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colors.onSurface
        )
    }
}