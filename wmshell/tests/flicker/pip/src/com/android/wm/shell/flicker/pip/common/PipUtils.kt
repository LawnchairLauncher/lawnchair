/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.flicker.pip.common

import android.tools.flicker.subject.exceptions.ExceptionMessageBuilder
import android.tools.flicker.subject.exceptions.IncorrectRegionException
import android.tools.flicker.subject.layers.LayerSubject
import kotlin.math.abs

// TODO(b/363080056): A margin of error allowed on certain layer size calculations.
const val EPSILON = 1

internal val widthNotSmallerThan: LayerSubject.(LayerSubject) -> Unit = {
    val width = visibleRegion.region.bounds.width()
    val otherWidth = it.visibleRegion.region.bounds.width()
    if (width < otherWidth && abs(width - otherWidth) > EPSILON) {
        val errorMsgBuilder =
            ExceptionMessageBuilder()
                .forSubject(this)
                .forIncorrectRegion("width. $width smaller than $otherWidth")
                .setExpected(width)
                .setActual(otherWidth)
        throw IncorrectRegionException(errorMsgBuilder)
    }
}