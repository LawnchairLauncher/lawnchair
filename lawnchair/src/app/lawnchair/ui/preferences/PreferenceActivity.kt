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

package app.lawnchair.ui.preferences

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.content.edit
import app.lawnchair.smartspace.provider.OnboardingProvider
import app.lawnchair.ui.preferences.navigation.PreferenceRoute
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.LauncherPrefs
import com.google.accompanist.adaptive.calculateDisplayFeatures
import kotlinx.serialization.json.Json

class PreferenceActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialRoute: PreferenceRoute? = intent.getStringExtra(EXTRA_DESTINATION_ROUTE)?.let { routeString ->
            try {
                Json.decodeFromString<PreferenceRoute>(routeString)
            } catch (e: Exception) {
                null // Fallback to default if deserialization fails
            }
        }
        val parentRoutes: List<PreferenceRoute> = intent.getStringExtra(EXTRA_PARENT_ROUTES)?.let { routesString ->
            try {
                Json.decodeFromString<List<PreferenceRoute>>(routesString)
            } catch (e: Exception) {
                null
            }
        }.orEmpty()
        // With parents, start at the first one and stack the others plus the destination on top,
        // so that back navigates up through them instead of closing the activity.
        val startRoute = parentRoutes.firstOrNull() ?: initialRoute
        val stackedRoutes = if (initialRoute != null && parentRoutes.isNotEmpty()) {
            parentRoutes.drop(1) + initialRoute
        } else {
            emptyList()
        }

        setContent {
            LawnchairTheme {
                EdgeToEdge()
                Preferences(
                    windowSizeClass = calculateWindowSizeClass(this),
                    displayFeatures = calculateDisplayFeatures(this),
                    startDestination = startRoute,
                    stackedRoutes = stackedRoutes,
                    intent = intent,
                )
            }
        }
        LauncherPrefs.getPrefs(this).edit {
            putBoolean(
                OnboardingProvider.PREF_HAS_OPENED_SETTINGS,
                true,
            )
        }
    }

    companion object {

        private const val EXTRA_DESTINATION_ROUTE = "app.lawnchair.ui.preferences.DESTINATION_ROUTE"

        private const val EXTRA_PARENT_ROUTES = "app.lawnchair.ui.preferences.PARENT_ROUTES"

        /**
         * @param parents routes to put below [destination] in the back stack, outermost first.
         */
        @JvmStatic
        @JvmOverloads
        fun createIntent(
            context: Context,
            destination: PreferenceRoute,
            parents: List<PreferenceRoute> = emptyList(),
        ): Intent {
            val intent = Intent(context, PreferenceActivity::class.java)
            val routeString = Json.encodeToString(destination)
            intent.putExtra(EXTRA_DESTINATION_ROUTE, routeString)
            if (parents.isNotEmpty()) {
                intent.putExtra(EXTRA_PARENT_ROUTES, Json.encodeToString(parents))
            }
            return intent
        }
    }
}
