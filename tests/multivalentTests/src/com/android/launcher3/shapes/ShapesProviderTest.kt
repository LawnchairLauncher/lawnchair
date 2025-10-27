/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.shapes

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.core.graphics.PathParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_LAUNCHER_ICON_SHAPES
import com.android.launcher3.graphics.ShapeDelegate.GenericPathShape
import com.android.launcher3.shapes.ShapesProvider.ARCH_KEY
import com.android.launcher3.shapes.ShapesProvider.CIRCLE_KEY
import com.android.launcher3.shapes.ShapesProvider.FOUR_SIDED_COOKIE_KEY
import com.android.launcher3.shapes.ShapesProvider.SEVEN_SIDED_COOKIE_KEY
import com.android.launcher3.shapes.ShapesProvider.SQUARE_KEY
import com.android.systemui.shared.Flags.FLAG_NEW_CUSTOMIZATION_PICKER_UI
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShapesProviderTest {

    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid path arch`() {
        ShapesProvider.iconShapes
            .find { it.key == ARCH_KEY }!!
            .run {
                GenericPathShape(pathString)
                PathParser.createPathFromPathData(pathString)
            }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid path 4_sided_cookie`() {
        ShapesProvider.iconShapes
            .find { it.key == FOUR_SIDED_COOKIE_KEY }!!
            .run {
                GenericPathShape(pathString)
                PathParser.createPathFromPathData(pathString)
            }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid path seven_sided_cookie`() {
        ShapesProvider.iconShapes
            .find { it.key == SEVEN_SIDED_COOKIE_KEY }!!
            .run {
                GenericPathShape(pathString)
                PathParser.createPathFromPathData(pathString)
            }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid path circle`() {
        ShapesProvider.iconShapes
            .find { it.key == CIRCLE_KEY }!!
            .run {
                GenericPathShape(pathString)
                PathParser.createPathFromPathData(pathString)
            }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid path square`() {
        ShapesProvider.iconShapes
            .find { it.key == ARCH_KEY }!!
            .run {
                GenericPathShape(pathString)
                PathParser.createPathFromPathData(pathString)
            }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid folder path clover`() {
        ShapesProvider.iconShapes
            .find { it.key == CIRCLE_KEY }!!
            .run {
                GenericPathShape(folderPathString)
                PathParser.createPathFromPathData(folderPathString)
            }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid folder path complexClover`() {
        ShapesProvider.iconShapes
            .find { it.key == FOUR_SIDED_COOKIE_KEY }!!
            .run {
                GenericPathShape(folderPathString)
                PathParser.createPathFromPathData(folderPathString)
            }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid folder path arch`() {
        ShapesProvider.iconShapes
            .find { it.key == ARCH_KEY }!!
            .run {
                GenericPathShape(folderPathString)
                PathParser.createPathFromPathData(folderPathString)
            }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_LAUNCHER_ICON_SHAPES, FLAG_NEW_CUSTOMIZATION_PICKER_UI)
    fun `verify valid folder path square`() {
        ShapesProvider.iconShapes
            .find { it.key == SQUARE_KEY }!!
            .run {
                GenericPathShape(folderPathString)
                PathParser.createPathFromPathData(folderPathString)
            }
    }
}
