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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.Routes
import app.lawnchair.util.pageMeta
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding

@ExperimentalAnimationApi
@Composable
fun TopBar() = pageMeta.consume { state ->
    val navController = LocalNavController.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    TopBarSurface(floating = state.topBarFloating) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarSize)
                .navigationBarsPadding(bottom = false)
        ) {
            AnimatedVisibility(visible = currentRoute != Routes.PREFERENCES && currentRoute != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .height(40.dp)
                        .width(40.dp)
                        .clip(CircleShape)
                        .clickable { navController.popBackStack() }
                ) {
                    Icon(
                        imageVector = backIcon(),
                        contentDescription = null,
                        tint = MaterialTheme.colors.onBackground
                    )
                }
            }
            Text(
                text = state.title,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colors.onSurface,
            )
        }
    }
}

val shadowColors = listOf(Color(0, 0, 0, 31), Color.Transparent)

@Composable
fun TopBarSurface(floating: Boolean, content: @Composable () -> Unit) {
    val normalColor = MaterialTheme.colors.background.copy(alpha = 0.9f)
    val floatingColor = MaterialTheme.colors.surface.copy(alpha = 0.9f)
    val color by animateColorAsState(if (floating) floatingColor else normalColor)
    val shadowAlpha by animateFloatAsState(if (floating) 1f else 0f)
    Column {
        Box(
            modifier = Modifier
                .background(color)
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
fun backIcon(): ImageVector {
    return if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        Icons.Rounded.ArrowBack
    } else {
        Icons.Rounded.ArrowForward
    }
}

val topBarSize = 56.dp
