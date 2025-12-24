/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.graphics.Rect
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.WindowContainerToken

/**
 * Creates a [LetterboxController] which is the composition of other two [LetterboxController]. It
 * basically invokes the method on both of them.
 */
infix fun LetterboxController.append(other: LetterboxController) =
    object : LetterboxController {
        override fun createLetterboxSurface(
            key: LetterboxKey,
            transaction: Transaction,
            parentLeash: SurfaceControl,
            token: WindowContainerToken?,
        ) {
            this@append.createLetterboxSurface(key, transaction, parentLeash, token)
            other.createLetterboxSurface(key, transaction, parentLeash, token)
        }

        override fun destroyLetterboxSurface(key: LetterboxKey, transaction: Transaction) {
            this@append.destroyLetterboxSurface(key, transaction)
            other.destroyLetterboxSurface(key, transaction)
        }

        override fun updateLetterboxSurfaceVisibility(
            key: LetterboxKey,
            transaction: Transaction,
            visible: Boolean,
        ) {
            this@append.updateLetterboxSurfaceVisibility(key, transaction, visible)
            other.updateLetterboxSurfaceVisibility(key, transaction, visible)
        }

        override fun updateLetterboxSurfaceBounds(
            key: LetterboxKey,
            transaction: Transaction,
            taskBounds: Rect,
            activityBounds: Rect,
        ) {
            this@append.updateLetterboxSurfaceBounds(key, transaction, taskBounds, activityBounds)
            other.updateLetterboxSurfaceBounds(key, transaction, taskBounds, activityBounds)
        }

        override fun dump() {
            this@append.dump()
            other.dump()
        }
    }

object LetterboxUtils {
    // Utility methods about Maps usage in Letterbox.
    object Maps {
        /*
         * Executes [onFound] on the [item] for a given [key] if present or
         * [onMissed] if the [key] is not present.
         */
        fun <V, K> MutableMap<K, V>.runOnItem(
            key: K,
            onFound: (V) -> Unit = { _ -> },
            onMissed: (K, MutableMap<K, V>) -> Unit = { _, _ -> },
        ) {
            this[key]?.let {
                return onFound(it)
            }
            return onMissed(key, this)
        }

        /*
         * Executes [onItem] on the [item] for the [key]s for a given [filter] predicate.
         */
        fun <V, K> MutableMap<K, V>.runOnFilteredItem(
            filter: (K) -> Boolean,
            onItem: (K, V) -> Unit = { _, _ -> },
        ) {
            this.forEach { k, v ->
                if (filter(k)) {
                    onItem(k, v)
                }
            }
        }
    }

    // Utility methods about Transaction usage in Letterbox.
    object Transactions {
        // Sets position and crops in one method. The surface is hidden if the crop Rect is empty.
        fun Transaction.moveAndCrop(surface: SurfaceControl, rect: Rect): Transaction =
            setPosition(surface, rect.left.toFloat(), rect.top.toFloat())
                .setWindowCrop(surface, rect.width(), rect.height())
                .apply { setVisibility(surface, !rect.isEmpty) }
    }
}
