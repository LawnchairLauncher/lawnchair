/*
 * Copyright 2024, System Launcher
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

package app.lawnchair.ui.preferences.destinations

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.one.OneAPI
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import com.patrykmichalik.opto.core.firstBlocking
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout

private val AccentColor = Color(0xFF10B981)

@Composable
fun OnePreferences() {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val oneAPI = remember { OneAPI(context) }

    var apiKey by remember { mutableStateOf(oneAPI.getApiKey() ?: "") }
    var hasKey by remember { mutableStateOf(oneAPI.hasApiKey()) }

    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = "One",
    ) {
        PreferenceGroup(heading = "Interface") {
            SwitchPreference(
                adapter = prefs2.oneFloatingButton.getAdapter(),
                label = "Floating button",
                description = "Show One button on home screen",
            )
            SwitchPreference(
                adapter = prefs2.oneSwipeDown.getAdapter(),
                label = "Swipe down",
                description = "Swipe down on home screen to open One",
            )
        }

        PreferenceGroup(heading = "API Configuration") {
            Surface(
                color = Color(0xFF1E1E1E),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = Color.White
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    cursorBrush = SolidColor(AccentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    decorationBox = { innerTextField ->
                        if (apiKey.isEmpty()) {
                            Text(
                                text = if (hasKey) "API key configured" else "Enter Claude API key",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (apiKey.isNotBlank()) {
                        oneAPI.setApiKey(apiKey)
                        hasKey = true
                        Toast.makeText(context, "API key saved", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Save API Key")
            }

            if (hasKey) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        oneAPI.clearApiKey()
                        apiKey = ""
                        hasKey = false
                        Toast.makeText(context, "API key cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Clear API Key", color = Color.Gray)
                }
            }
        }

        PreferenceGroup(heading = "About") {
            Text(
                text = "One is powered by Claude Sonnet 4.\n\nDesigned with Dieter Rams principles:\nLess but better.",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                ),
                modifier = Modifier.padding(16.dp)
            )
        }

        // Easter egg
        var easterEggTaps by remember { mutableIntStateOf(0) }
        val gentleHumor = listOf(
            "Eat your vegetables.",
            "Good things come in small bunches.",
            "Minimalism is delicious.",
            "Less is more. More broccoli.",
            "Function over florets.",
            "Dieter Rams approved this vegetable.",
            "Simple. Honest. Nutritious.",
            "One broccoli at a time."
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "\uD83E\uDD66",
            style = TextStyle(
                fontSize = 32.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    easterEggTaps++
                    val message = gentleHumor[easterEggTaps % gentleHumor.size]
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                    if (easterEggTaps >= 7) {
                        val simplified = prefs2.oneSimplifiedMode
                        val current = simplified.firstBlocking()
                        simplified.set(!current)
                        val mode = if (!current) "Simplified mode enabled" else "Simplified mode disabled"
                        Toast.makeText(context, mode, Toast.LENGTH_LONG).show()
                        easterEggTaps = 0
                    }
                }
                .padding(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
