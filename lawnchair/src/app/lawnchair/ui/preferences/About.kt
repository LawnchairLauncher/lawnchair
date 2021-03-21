package app.lawnchair.ui.preferences

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.util.preferences.getFormattedVersionName
import com.android.launcher3.R

@Composable
fun About() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.requiredHeight(24.dp))
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_home_comp),
            contentDescription = null,
            modifier = Modifier
                .width(96.dp)
                .height(96.dp)
                .clip(
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.requiredHeight(16.dp))
        Text(
            text = stringResource(id = R.string.derived_app_name),
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onBackground
        )
        CompositionLocalProvider(
            LocalContentAlpha provides ContentAlpha.medium,
            LocalContentColor provides MaterialTheme.colors.onBackground
        ) {
            Text(text = getFormattedVersionName(LocalContext.current), style = MaterialTheme.typography.body1)
        }
        Spacer(modifier = Modifier.requiredHeight(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
        ) {
            LawnchairLink(
                iconResId = R.drawable.ic_new_releases,
                label = stringResource(id = R.string.news),
                modifier = Modifier.weight(1f),
                url = "https://t.me/lawnchairci"
            )
            LawnchairLink(
                iconResId = R.drawable.ic_help,
                label = stringResource(id = R.string.support),
                modifier = Modifier.weight(1f),
                url = "https://t.me/lccommunity"
            )
            LawnchairLink(
                iconResId = R.drawable.ic_twitter,
                label = stringResource(id = R.string.twitter),
                modifier = Modifier.weight(1f),
                url = "https://twitter.com/lawnchairapp"
            )
            LawnchairLink(
                iconResId = R.drawable.ic_github,
                label = stringResource(id = R.string.github),
                modifier = Modifier.weight(1f),
                url = "https://github.com/LawnchairLauncher/Lawnchair"
            )
        }
        Spacer(modifier = Modifier.requiredHeight(16.dp))
        ContributorCard(
            name = "Kshitij Gupta",
            description = stringResource(id = R.string.kshitij_description),
            photoResId = R.drawable.kshitij_gupta,
            links = {
                ContributorLink(iconResId = R.drawable.ic_twitter, url = "https://twitter.com/Agent_Fabulous")
                ContributorLink(iconResId = R.drawable.ic_github, url = "https://github.com/AgentFabulous")
            }
        )
        ContributorCard(
            name = "Patryk Michalik",
            description = stringResource(id = R.string.patryk_description),
            includeTopPadding = true,
            photoResId = R.drawable.patryk_michalik,
            links = {
                ContributorLink(iconResId = R.drawable.ic_website, url = "https://patrykmichalik.com")
                ContributorLink(iconResId = R.drawable.ic_twitter, url = "https://twitter.com/patrykmichalik_")
                ContributorLink(iconResId = R.drawable.ic_github, url = "https://github.com/patrykmichalik")
            }
        )
        PreferenceGroup(heading = stringResource(id = R.string.team_members), useTopPadding = true) {
            ContributorRow(
                name = "David Sn",
                description = stringResource(id = R.string.devops_engineer),
                photoRes = R.drawable.david_sn,
                url = "https://codebucket.de"
            )
            ContributorRow(
                name = "Rhyse Simpson",
                description = stringResource(id = R.string.quickswitch_maintainer),
                photoRes = R.drawable.rhyse_simpson,
                url = "https://twitter.com/skittles9823"
            )
        }
    }
}