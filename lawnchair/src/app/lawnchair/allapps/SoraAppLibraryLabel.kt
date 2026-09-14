/*
 * Copyright 2026, Renns Project
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

package app.lawnchair.allapps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.util.DRAWER_LABEL_SHADOW_BLUR_DP
import app.lawnchair.util.DRAWER_LABEL_SHADOW_COLOR
import app.lawnchair.util.DRAWER_LABEL_SHADOW_DY_DP
import com.android.launcher3.R

/**
 * What the app drawer's search bar shows when nothing has been typed: a
 * magnifier and the name of the drawer, together in the middle of the bar.
 *
 * The bar is a destination at rest and a text field once tapped, and those want
 * opposite things. Centred, it reads as a label for what is below it; pinned to
 * the left with a caret's worth of space, it reads as an empty input waiting to
 * be filled. So the resting state is drawn as a label, and the input takes over
 * on focus.
 *
 * Nothing is drawn behind it here -- the glass capsule is a separate overlay, so
 * that it can stay fixed to the screen while the drawer slides over it.
 */
@Composable
fun SoraAppLibraryLabel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.sora_app_library)
    // The one white the drawer's folder labels wear, and the same shadow under
    // it. This sits on glass over the same frosted wallpaper they do, so taking
    // a colour from the theme instead made the bar the one thing in the drawer
    // that changed shade when the theme was switched.
    val contentColor = Color.White
    val density = LocalDensity.current
    val labelShadow = Shadow(
        color = Color(DRAWER_LABEL_SHADOW_COLOR),
        offset = with(density) { Offset(0f, DRAWER_LABEL_SHADOW_DY_DP.dp.toPx()) },
        blurRadius = with(density) { DRAWER_LABEL_SHADOW_BLUR_DP.dp.toPx() },
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // The capsule behind this is drawn by the glass overlay, which a
                // ripple in here cannot reach around; an unclipped ripple over a
                // rounded pane is worse than none.
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_qsb_search),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(shadow = labelShadow),
        )
    }
}
