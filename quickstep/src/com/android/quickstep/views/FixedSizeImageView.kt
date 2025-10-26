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

package com.android.quickstep.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView

/**
 * An [ImageView] that does not requestLayout() unless setLayoutParams is called.
 *
 * This is useful, particularly during animations, for [ImageView]s that are not supposed to be
 * resized.
 */
@SuppressLint("AppCompatCustomView")
class FixedSizeImageView : ImageView {
    private var shouldRequestLayoutOnChanges = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        shouldRequestLayoutOnChanges = true
        super.setLayoutParams(params)
        shouldRequestLayoutOnChanges = false
    }

    override fun requestLayout() {
        if (shouldRequestLayoutOnChanges) {
            super.requestLayout()
        }
    }
}
