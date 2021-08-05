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

package app.lawnchair.ui.preferences.components

import androidx.compose.animation.Animatable
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalElevationOverlay
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import app.lawnchair.ui.preferences.LocalNavController
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding

@ExperimentalAnimationApi
@Composable
fun TopBar(
    backArrowVisible: Boolean,
    floating: Boolean,
    label: String
) {
    val navController = LocalNavController.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    TopBarSurface(floating = floating) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarSize)
                .padding(horizontal = 8.dp)
        ) {
            if (backArrowVisible) {
                ClickableIcon(
                    imageVector = backIcon(),
                    tint = MaterialTheme.colors.onBackground,
                    onClick = { if (currentRoute != "/") navController.popBackStack() }
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

val shadowColors = listOf(Color(0, 0, 0, 31), Color.Transparent)

@Composable
fun TopBarSurface(floating: Boolean, content: @Composable () -> Unit) {
    val (normalColor, floatingColor) = topBarColors()
    val color = remember(key1 = normalColor) { Animatable(normalColor) }
    LaunchedEffect(floating) {
        color.animateTo(if (floating) floatingColor else normalColor)
    }
    val shadowAlpha by animateFloatAsState(if (floating) 1f else 0f)

    Column(
        modifier = Modifier
            .navigationBarsPadding(bottom = false)
    ) {
        Box(
            modifier = Modifier
                .background(color.value)
                .statusBarsPadding()
                .pointerInput(remember { MutableInteractionSource() }) {
                    // consume touch
                }
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .alpha(shadowAlpha)
                .background(Brush.verticalGradient(shadowColors))
        )
    }
}

@Composable
fun topBarColors(): Pair<Color, Color> {
    val elevationOverlay = LocalElevationOverlay.current
    val backgroundColor = MaterialTheme.colors.background
    val surfaceColor = MaterialTheme.colors.surface
    val floatingColor = elevationOverlay?.apply(surfaceColor, 4.dp) ?: surfaceColor
    return Pair(backgroundColor.copy(alpha = 0.9f), floatingColor.copy(alpha = 0.9f))
}

@Composable
fun backIcon(): ImageVector =
    if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        Icons.Rounded.ArrowBack
    } else {
        Icons.Rounded.ArrowForward
    }

val topBarSize = 56.dp
