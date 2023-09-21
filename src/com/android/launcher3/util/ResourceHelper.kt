/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.util

import android.content.Context
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.util.AttributeSet
import kotlin.IntArray

/**
 * This class is a helper that can be subclassed in tests to provide a way to parse attributes
 * correctly.
 */
open class ResourceHelper(private val context: Context, private val specsFileId: Int) {
    open fun getXml(): XmlResourceParser {
        return context.resources.getXml(specsFileId)
    }

    open fun obtainStyledAttributes(attrs: AttributeSet, styleId: IntArray): TypedArray {
        return context.obtainStyledAttributes(attrs, styleId)
    }
}
