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

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

private val base = Typography()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = GoogleSansFlex.Display.Emphasized.Large),
    displayMedium = base.displayMedium.copy(fontFamily = GoogleSansFlex.Display.Emphasized.Medium),
    displaySmall = base.displaySmall.copy(fontFamily = GoogleSansFlex.Display.Emphasized.Large),
    headlineLarge = base.headlineLarge.copy(fontFamily = GoogleSansFlex.Headline.Emphasized.Large),
    headlineMedium = base.headlineMedium.copy(fontFamily = GoogleSansFlex.Headline.Emphasized.Medium),
    headlineSmall = base.headlineSmall.copy(fontFamily = GoogleSansFlex.Headline.Emphasized.Large),
    titleLarge = base.titleLarge.copy(fontFamily = GoogleSansFlex.Title.Emphasized.Large),
    titleMedium = base.titleMedium.copy(fontFamily = GoogleSansFlex.Title.Emphasized.Medium),
    titleSmall = base.titleSmall.copy(fontFamily = GoogleSansFlex.Title.Emphasized.Small),
    bodyLarge = base.bodyLarge.copy(fontFamily = GoogleSansFlex.Body.Normal.Large, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = GoogleSansFlex.Body.Normal.Medium, letterSpacing = 0.1.sp),
    bodySmall = base.bodySmall.copy(fontFamily = GoogleSansFlex.Body.Normal.Small),
    labelLarge = base.labelLarge.copy(fontFamily = GoogleSansFlex.Label.Normal.Large),
    labelMedium = base.labelMedium.copy(fontFamily = GoogleSansFlex.Label.Normal.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = GoogleSansFlex.Label.Normal.Small),
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = GoogleSansFlex.Display.Emphasized.Large),
    displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = GoogleSansFlex.Display.Emphasized.Medium),
    displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = GoogleSansFlex.Display.Emphasized.Large),
    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = GoogleSansFlex.Headline.Emphasized.Large),
    headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = GoogleSansFlex.Headline.Emphasized.Medium),
    headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = GoogleSansFlex.Headline.Emphasized.Large),
    titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = GoogleSansFlex.Title.Emphasized.Large),
    titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = GoogleSansFlex.Title.Emphasized.Medium),
    titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = GoogleSansFlex.Title.Emphasized.Small),
    bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = GoogleSansFlex.Body.Emphasized.Large),
    bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = GoogleSansFlex.Body.Emphasized.Medium),
    bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = GoogleSansFlex.Body.Emphasized.Small),
    labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = GoogleSansFlex.Label.Emphasized.Large),
    labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = GoogleSansFlex.Label.Emphasized.Medium),
    labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = GoogleSansFlex.Label.Emphasized.Small),
)
