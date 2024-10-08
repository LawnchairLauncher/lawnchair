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

package app.lawnchair.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.android.launcher3.R

private val InterFontFamily = FontFamily(
    Font(R.font.inter_black, FontWeight.Black, FontStyle.Normal),
    Font(R.font.inter_black_italic, FontWeight.Black, FontStyle.Italic),
    Font(R.font.inter_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.inter_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.inter_extra_bold, FontWeight.ExtraBold, FontStyle.Normal),
    Font(R.font.inter_extra_bold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.inter_extra_light, FontWeight.ExtraLight, FontStyle.Normal),
    Font(R.font.inter_extra_light_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_light, FontWeight.Light, FontStyle.Normal),
    Font(R.font.inter_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.inter_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.inter_semi_bold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.inter_thin, FontWeight.Thin, FontStyle.Normal),
    Font(R.font.inter_thin_italic, FontWeight.Thin, FontStyle.Italic),
)

private val base = Typography()
val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = InterFontFamily),
    displayMedium = base.displayMedium.copy(fontFamily = InterFontFamily),
    displaySmall = base.displaySmall.copy(fontFamily = InterFontFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = InterFontFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = InterFontFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = InterFontFamily),
    titleLarge = base.titleLarge.copy(fontFamily = InterFontFamily),
    titleMedium = base.titleMedium.copy(fontFamily = InterFontFamily),
    titleSmall = base.titleSmall.copy(fontFamily = InterFontFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = InterFontFamily, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = InterFontFamily, letterSpacing = 0.1.sp),
    bodySmall = base.bodySmall.copy(fontFamily = InterFontFamily),
    labelLarge = base.labelLarge.copy(fontFamily = InterFontFamily),
    labelMedium = base.labelMedium.copy(fontFamily = InterFontFamily),
    labelSmall = base.labelSmall.copy(fontFamily = InterFontFamily),
)
