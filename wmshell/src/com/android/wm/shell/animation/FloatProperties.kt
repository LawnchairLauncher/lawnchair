/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.animation

import android.graphics.Rect
import android.graphics.RectF
import androidx.dynamicanimation.animation.FloatPropertyCompat

/**
 * Helpful extra properties to use with the [PhysicsAnimator]. These allow you to animate objects
 * such as [Rect] and [RectF].
 *
 * There are additional, more basic properties available in [DynamicAnimation].
 */
class FloatProperties {
    companion object {
        /**
         * Represents the x-coordinate of a [Rect]. Typically used to animate moving a Rect
         * horizontally.
         *
         * This property's getter returns [Rect.left], and its setter uses [Rect.offsetTo], which
         * sets [Rect.left] to the new value and offsets [Rect.right] so that the width of the Rect
         * does not change.
         */
        @JvmField
        val RECT_X = object : FloatPropertyCompat<Rect>("RectX") {
            override fun setValue(rect: Rect?, value: Float) {
                rect?.offsetTo(value.toInt(), rect.top)
            }

            override fun getValue(rect: Rect?): Float {
                return rect?.left?.toFloat() ?: -Float.MAX_VALUE
            }
        }

        /**
         * Represents the y-coordinate of a [Rect]. Typically used to animate moving a Rect
         * vertically.
         *
         * This property's getter returns [Rect.top], and its setter uses [Rect.offsetTo], which
         * sets [Rect.top] to the new value and offsets [Rect.bottom] so that the height of the Rect
         * does not change.
         */
        @JvmField
        val RECT_Y = object : FloatPropertyCompat<Rect>("RectY") {
            override fun setValue(rect: Rect?, value: Float) {
                rect?.offsetTo(rect.left, value.toInt())
            }

            override fun getValue(rect: Rect?): Float {
                return rect?.top?.toFloat() ?: -Float.MAX_VALUE
            }
        }

        /**
         * Represents the width of a [Rect]. Typically used to animate resizing a Rect horizontally.
         *
         * This property's getter returns [Rect.width], and its setter changes the value of
         * [Rect.right] by adding the animated width value to [Rect.left].
         */
        @JvmField
        val RECT_WIDTH = object : FloatPropertyCompat<Rect>("RectWidth") {
            override fun getValue(rect: Rect): Float {
                return rect.width().toFloat()
            }

            override fun setValue(rect: Rect, value: Float) {
                rect.right = rect.left + value.toInt()
            }
        }

        /**
         * Represents the height of a [Rect]. Typically used to animate resizing a Rect vertically.
         *
         * This property's getter returns [Rect.height], and its setter changes the value of
         * [Rect.bottom] by adding the animated height value to [Rect.top].
         */
        @JvmField
        val RECT_HEIGHT = object : FloatPropertyCompat<Rect>("RectHeight") {
            override fun getValue(rect: Rect): Float {
                return rect.height().toFloat()
            }

            override fun setValue(rect: Rect, value: Float) {
                rect.bottom = rect.top + value.toInt()
            }
        }

        /**
         * Represents the x-coordinate of a [RectF]. Typically used to animate moving a RectF
         * horizontally.
         *
         * This property's getter returns [RectF.left], and its setter uses [RectF.offsetTo], which
         * sets [RectF.left] to the new value and offsets [RectF.right] so that the width of the
         * RectF does not change.
         */
        @JvmField
        val RECTF_X = object : FloatPropertyCompat<RectF>("RectFX") {
            override fun setValue(rect: RectF?, value: Float) {
                rect?.offsetTo(value, rect.top)
            }

            override fun getValue(rect: RectF?): Float {
                return rect?.left ?: -Float.MAX_VALUE
            }
        }

        /**
         * Represents the y-coordinate of a [RectF]. Typically used to animate moving a RectF
         * vertically.
         *
         * This property's getter returns [RectF.top], and its setter uses [RectF.offsetTo], which
         * sets [RectF.top] to the new value and offsets [RectF.bottom] so that the height of the
         * RectF does not change.
         */
        @JvmField
        val RECTF_Y = object : FloatPropertyCompat<RectF>("RectFY") {
            override fun setValue(rect: RectF?, value: Float) {
                rect?.offsetTo(rect.left, value)
            }

            override fun getValue(rect: RectF?): Float {
                return rect?.top ?: -Float.MAX_VALUE
            }
        }
    }
}