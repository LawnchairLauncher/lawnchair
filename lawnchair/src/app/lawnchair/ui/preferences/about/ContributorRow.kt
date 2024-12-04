/*
 * Copyright 2021, Lawnchair
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.placeholder.PlaceholderHighlight
import app.lawnchair.ui.placeholder.fade
import app.lawnchair.ui.placeholder.placeholder
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.util.kotlinxJson
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

suspend fun checkUserContribution(userName: String): String {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .addConverterFactory(kotlinxJson.asConverterFactory("application/json".toMediaType()))
        .build()

    val api = retrofit.create(GitHubApi::class.java)

    return withContext(Dispatchers.IO) {
        try {
            val events = api.getRepositoryEvents("LawnchairLauncher", "lawnchair")
            val isActive = events.any {
                it.actor.login == userName
            }
            if (isActive) "Active" else "Idle"
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}

@Composable
fun ContributorRow(
    name: String,
    description: String,
    photoUrl: String,
    url: String,
    githubUsername: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    var contributionStatus by remember { mutableStateOf("") }

    if (githubUsername != null) {
        LaunchedEffect(githubUsername) {
            coroutineScope.launch {
                contributionStatus = checkUserContribution(githubUsername)
            }
        }
    }

    PreferenceTemplate(
        title = { Text(text = name) },
        modifier = modifier
            .clickable {
                val webpage = Uri.parse(url)
                val intent = Intent(Intent.ACTION_VIEW, webpage)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            },
        description = { Text(text = "$description ${if (!contributionStatus.isBlank() or !contributionStatus.isEmpty()) "•" else ""} $contributionStatus") },
        startWidget = {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .clip(CircleShape)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .placeholder(
                                visible = true,
                                highlight = PlaceholderHighlight.fade(),
                            ),
                    )
                },
            )
        },
    )
}

interface GitHubApi {
    @GET("repos/{owner}/{repo}/events")
    suspend fun getRepositoryEvents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<GitHubEvent>
}

@Serializable
data class GitHubEvent(
    val type: String,
    val actor: Actor,
    val created_at: String,
)

@Serializable
data class Actor(
    val login: String,
)
