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
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import app.lawnchair.ui.theme.LawnchairTheme
import com.google.accompanist.insets.ProvideWindowInsets

@ExperimentalMaterialApi
@ExperimentalAnimationApi
class PreferenceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LawnchairTheme {
                ProvideWindowInsets {
                    Preferences()
                }
            }
        }
    }

    companion object {

        fun createIntent(context: Context, destination: String): Intent {
            val uri = "android-app://androidx.navigation/$destination".toUri()
            return Intent(Intent.ACTION_VIEW, uri, context, PreferenceActivity::class.java)
        }
    }
}
