/*
 * Copyright 2026 Lawnchair
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

package com.android.launcher3.folder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import app.lawnchair.util.resolveFolderBackgroundColor
import app.lawnchair.util.resolveFolderPreviewColor
import com.android.app.animation.Interpolators
import com.android.launcher3.graphics.ShapeDelegate
import com.android.launcher3.views.BaseDragLayer
import kotlin.math.roundToInt

/**
 * Open / close animation for large folders.
 *
 * The default animations morph the folder out of the small preview drawn by a regular folder
 * icon. Here the folder instead grows out of the large folder tile: its outline morphs from the
 * tile's rounded rect to the folder's bounds (and back when closing), its background color
 * blends from the tile color, and its content fades in.
 */
class LargeFolderAnimationManager(private val folder: Folder) : FolderAnimationCreator {

    override fun createAnimatorSet(isOpening: Boolean): AnimatorSet {
        val context = folder.context
        val folderIcon = folder.mFolderIcon
        val lp = folder.layoutParams as BaseDragLayer.LayoutParams

        // The tile, in the folder's coordinates.
        val iconBounds = Rect()
        val scale = folder.mActivityContext.dragLayer
            .getDescendantRectRelativeToSelf(folderIcon, iconBounds)
        val tile = Rect()
        folderIcon.getWorkspaceVisualDragBounds(tile)
        val startRect = Rect(
            (iconBounds.left + tile.left * scale - lp.x).roundToInt(),
            (iconBounds.top + tile.top * scale - lp.y).roundToInt(),
            (iconBounds.left + tile.right * scale - lp.x).roundToInt(),
            (iconBounds.top + tile.bottom * scale - lp.y).roundToInt(),
        )
        val endRect = Rect(0, 0, lp.width, lp.height)

        val background = (folder.background as? GradientDrawable)?.apply { mutate() }
        val tileColor = resolveFolderPreviewColor(context)
        val folderColor = resolveFolderBackgroundColor(context)
        val endRadius = background?.cornerRadius ?: 0f
        val startRadius = folderIcon.largeFolderCornerRadius * scale
        val radiusRatio = if (startRect.width() > 0) {
            (startRadius / (startRect.width() / 2f)).coerceIn(0f, 1f)
        } else {
            0f
        }

        folder.alpha = 1f
        folder.scaleX = 1f
        folder.scaleY = 1f
        val contentViews = listOf<View>(folder.mContent, folder.mFooter)
        contentViews.forEach {
            it.alpha = if (isOpening) 0f else 1f
            it.pivotX = it.width / 2f
            it.pivotY = 0f
        }
        background?.setColor(if (isOpening) tileColor else folderColor)

        val mainDuration = if (isOpening) OPEN_DURATION else CLOSE_DURATION
        val mainInterpolator = if (isOpening) {
            Interpolators.EMPHASIZED_DECELERATE
        } else {
            Interpolators.EMPHASIZED
        }
        val animators = mutableListOf<Animator>(
            ShapeDelegate.RoundedSquare(radiusRatio)
                .createRevealAnimator(folder, startRect, endRect, endRadius, !isOpening)
                .apply {
                    duration = mainDuration
                    interpolator = mainInterpolator
                },
        )
        if (background != null) {
            animators += if (isOpening) {
                ObjectAnimator.ofArgb(background, "color", tileColor, folderColor)
            } else {
                ObjectAnimator.ofArgb(background, "color", folderColor, tileColor)
            }.apply {
                duration = mainDuration
                interpolator = mainInterpolator
            }
        }
        contentViews.forEach { view ->
            val fromAlpha = if (isOpening) 0f else 1f
            val toAlpha = if (isOpening) 1f else 0f
            val fromScale = if (isOpening) CONTENT_START_SCALE else 1f
            val toScale = if (isOpening) 1f else CONTENT_START_SCALE
            animators += ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat(View.ALPHA, fromAlpha, toAlpha),
                PropertyValuesHolder.ofFloat(View.SCALE_X, fromScale, toScale),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, fromScale, toScale),
            ).apply {
                if (isOpening) {
                    startDelay = CONTENT_FADE_DELAY
                    duration = OPEN_DURATION - CONTENT_FADE_DELAY
                    interpolator = Interpolators.EMPHASIZED_DECELERATE
                } else {
                    duration = CLOSE_CONTENT_DURATION
                    interpolator = Interpolators.STANDARD_ACCELERATE
                }
            }
        }

        // Note: no duration / interpolator on the set itself, they'd override the children's.
        return AnimatorSet().apply {
            playTogether(animators)
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        // Fading content is much smoother when rendered as a layer.
                        contentViews.forEach { it.setLayerType(View.LAYER_TYPE_HARDWARE, null) }
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        contentViews.forEach {
                            it.setLayerType(View.LAYER_TYPE_NONE, null)
                            it.alpha = 1f
                            it.scaleX = 1f
                            it.scaleY = 1f
                        }
                        background?.setColor(folderColor)
                    }
                },
            )
        }
    }

    private companion object {
        const val OPEN_DURATION = 420L
        const val CLOSE_DURATION = 300L
        const val CONTENT_FADE_DELAY = 50L
        const val CLOSE_CONTENT_DURATION = 150L
        const val CONTENT_START_SCALE = 0.92f
    }
}
