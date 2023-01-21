/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.about

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavGraphBuilder
import app.lawnchair.ui.preferences.about.acknowledgements.licensesGraph
import app.lawnchair.ui.preferences.components.ClickablePreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.preferenceGraph
import app.lawnchair.ui.preferences.subRoute
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

private enum class Role(val descriptionResId: Int) {
    Development(descriptionResId = R.string.development),
    DevOps(descriptionResId = R.string.devops),
    QuickSwitchMaintenance(descriptionResId = R.string.quickswitch_maintenance),
    Support(descriptionResId = R.string.support),
    SupportAndPr(descriptionResId = R.string.support_and_pr),
}

private data class TeamMember(
    val name: String,
    val role: Role,
    val photoUrl: String,
    val socialUrl: String,
)

private data class Link(
    @DrawableRes val iconResId: Int,
    @StringRes val labelResId: Int,
    val url: String,
)

private val product = listOf(
    TeamMember(
        name = "Amogh Lele",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/31761843",
        socialUrl = "https://www.linkedin.com/in/amogh-lele/",
    ),
    TeamMember(
        name = "Antonio J. Roa Valverde",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/914983",
        socialUrl = "https://twitter.com/6020peaks",
    ),
    TeamMember(
        name = "David Sn",
        role = Role.DevOps,
        photoUrl = "https://i.imgur.com/b65akTl.png",
        socialUrl = "https://codebucket.de",
    ),
    TeamMember(
        name = "Goooler",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/10363352",
        socialUrl = "https://github.com/Goooler",
    ),
    TeamMember(
        name = "Harsh Shandilya",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/13348378",
        socialUrl = "https://github.com/msfjarvis",
    ),
    TeamMember(
        name = "Kshitij Gupta",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/18647641",
        socialUrl = "https://twitter.com/Agent_Fabulous",
    ),
    TeamMember(
        name = "Manuel Lorenzo",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/183264",
        socialUrl = "https://twitter.com/noloman",
    ),
    TeamMember(
        name = "paphonb",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/8080853",
        socialUrl = "https://twitter.com/paphonb",
    ),
    TeamMember(
        name = "raphtlw",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/47694127",
        socialUrl = "https://twitter.com/raphtlw",
    ),
    TeamMember(
        name = "Rhyse Simpson",
        role = Role.QuickSwitchMaintenance,
        photoUrl = "https://avatars.githubusercontent.com/u/7065700",
        socialUrl = "https://twitter.com/skittles9823",
    ),
    TeamMember(
        name = "Yasan",
        role = Role.Development,
        photoUrl = "https://avatars.githubusercontent.com/u/41836211",
        socialUrl = "https:/yasan.dev",
    ),
)

private val supportAndPr = listOf(
    TeamMember(
        name = "Daniel Souza",
        role = Role.Support,
        photoUrl = "https://avatars.githubusercontent.com/u/32078304",
        socialUrl = "https://github.com/DanGLES3",
    ),
    TeamMember(
        name = "Giuseppe Longobardo",
        role = Role.Support,
        photoUrl = "https://avatars.githubusercontent.com/u/49398464",
        socialUrl = "https://github.com/joseph-20",
    ),
    TeamMember(
        name = "Rik Koedoot",
        role = Role.SupportAndPr,
        photoUrl = "https://avatars.githubusercontent.com/u/29402532",
        socialUrl = "https://twitter.com/rikkoedoot",
    ),
    TeamMember(
        name = "SuperDragonXD",
        role = Role.Support,
        photoUrl = "https://avatars.githubusercontent.com/u/70206496",
        socialUrl = "https://github.com/SuperDragonXD",
    ),
)

private val links = listOf(
    Link(
        iconResId = R.drawable.ic_new_releases,
        labelResId = R.string.news,
        url = "https://t.me/lawnchairci",
    ),
    Link(
        iconResId = R.drawable.ic_help,
        labelResId = R.string.support,
        url = "https://t.me/lccommunity",
    ),
    Link(
        iconResId = R.drawable.ic_twitter,
        labelResId = R.string.twitter,
        url = "https://twitter.com/lawnchairapp",
    ),
    Link(
        iconResId = R.drawable.ic_github,
        labelResId = R.string.github,
        url = "https://github.com/LawnchairLauncher/Lawnchair",
    ),
    Link(
        iconResId = R.drawable.ic_discord,
        labelResId = R.string.discord,
        url = "https://discord.com/invite/3x8qNWxgGZ",
    ),
)

object AboutRoutes {
    const val LICENSES = "licenses"
}

fun NavGraphBuilder.aboutGraph(route: String) {
    preferenceGraph(route, { About() }) { subRoute ->
        licensesGraph(route = subRoute(AboutRoutes.LICENSES))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun About() {
    val context = LocalContext.current

    PreferenceLayout(
        horizontalAlignment = Alignment.CenterHorizontally,
        label = stringResource(id = R.string.about_label),
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_home_comp),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.derived_app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = BuildConfig.VERSION_DISPLAY_NAME,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current.copy(alpha = ContentAlpha.medium),
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = {
                        val commitUrl = "https://github.com/LawnchairLauncher/lawnchair/commit/${BuildConfig.COMMIT_HASH}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, commitUrl.toUri()))
                    }
                )
            )
            Spacer(modifier = Modifier.requiredHeight(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                links.forEach { link ->
                    LawnchairLink(
                        iconResId = link.iconResId,
                        label = stringResource(id = link.labelResId),
                        modifier = Modifier.weight(weight = 1f),
                        url = link.url,
                    )
                }
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.product)) {
            product.forEach {
                ContributorRow(
                    name = it.name,
                    description = stringResource(it.role.descriptionResId),
                    url = it.socialUrl,
                    photoUrl = it.photoUrl,
                )
            }
        }
        PreferenceGroup(heading = stringResource(id = R.string.support_and_pr)) {
            supportAndPr.forEach {
                ContributorRow(
                    name = it.name,
                    description = stringResource(it.role.descriptionResId),
                    url = it.socialUrl,
                    photoUrl = it.photoUrl,
                )
            }
        }
        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(id = R.string.acknowledgements),
                destination = subRoute(name = AboutRoutes.LICENSES),
            )
            ClickablePreference(
                label = stringResource(id = R.string.translate),
                onClick = {
                    val webpage = Uri.parse(CROWDIN_URL)
                    val intent = Intent(Intent.ACTION_VIEW, webpage)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                },
            )
        }
    }
}

private const val CROWDIN_URL = "https://lawnchair.crowdin.com/lawnchair"
