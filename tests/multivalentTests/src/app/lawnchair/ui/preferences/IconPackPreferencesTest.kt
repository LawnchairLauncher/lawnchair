/*
 * Copyright 2026, Lawnchair
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

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import app.lawnchair.icons.DaggerLawnchairThemeManagerTestComponent
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.preferences.destinations.IconPackGrid
import app.lawnchair.ui.preferences.destinations.IconPackInfo
import app.lawnchair.util.Constants
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class IconPackPreferencesTest {
    @get:Rule(order = 1) val context = SandboxApplication()
    @get:Rule(order = 0) val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        context.initDaggerComponent(DaggerLawnchairThemeManagerTestComponent.builder())
    }

    @Test
    fun `themed icon source grid shows lawnicons and writes its package name`() {
        val adapter = RecordingPreferenceAdapter(initial = "")
        showGrid(
            iconPacks = listOf(iconPack("Regular Pack", "com.example.iconpack")),
            themedIconPacks = listOf(iconPack(LAWNICONS_NAME, Constants.LAWNICONS_PACKAGE_NAME)),
            adapter = adapter,
            isThemedIconPack = true,
            modifier = Modifier.width(180.dp),
        )

        composeTestRule.onNodeWithText("Regular Pack").assertDoesNotExist()
        composeTestRule.onNodeWithText(LAWNICONS_NAME).assertIsDisplayed().performClick()

        assertThat(adapter.state.value).isEqualTo(Constants.LAWNICONS_PACKAGE_NAME)
    }

    private fun showGrid(
        iconPacks: List<IconPackInfo>,
        themedIconPacks: List<IconPackInfo>,
        adapter: PreferenceAdapter<String>,
        isThemedIconPack: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val interactor = mock<PreferenceInteractor>()
        whenever(interactor.iconPacks).thenReturn(MutableStateFlow(iconPacks))
        whenever(interactor.themedIconPacks).thenReturn(MutableStateFlow(themedIconPacks))
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalInspectionMode provides true,
                    LocalPreferenceInteractor provides interactor,
                ) {
                    IconPackGrid(
                        adapter = adapter,
                        isThemedIconPack = isThemedIconPack,
                        modifier = modifier,
                    )
                }
            }
        }
    }

    private class RecordingPreferenceAdapter(initial: String) : PreferenceAdapter<String> {
        private val current = mutableStateOf(initial)
        override val state: State<String> = current

        override fun onChange(newValue: String) {
            current.value = newValue
        }
    }

    private fun iconPack(name: String, packageName: String) = IconPackInfo(
        name = name,
        packageName = packageName,
        icon = ColorDrawable(Color.BLACK),
    )

    private companion object {
        const val LAWNICONS_NAME = "Lawnicons"
    }
}
