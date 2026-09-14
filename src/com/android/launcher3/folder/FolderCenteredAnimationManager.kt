/*
 * Copyright (C) 2026 Sora Launcher
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
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import com.android.launcher3.BubbleTextView
import com.android.launcher3.folder.FolderAnimationData.Factory.getAnimationData
import com.android.launcher3.folder.IconAnimationData.Factory.getIconAnimationDataList
import com.android.launcher3.views.BaseDragLayer

/**
 * Manages synchronized opening and closing animations for [Folder] in Default (Centered) mode.
 *
 * A single unified progress parameter t in [0, 1] directly drives:
 * 1. LiquidGlassPanel (mFolderGlass) pane bounds, corner radius, and blur.
 * 2. Fullscreen LiquidGlassPanel blur overlay (mBlurOverlay).
 * 3. Folder view translation and round-rect clip path.
 * 4. FolderPagedView content clip path and scale.
 * 5. Child icon scales, translations from preview plate positions, and text alpha.
 * 6. Folder title fade-in.
 */
class FolderCenteredAnimationManager(
    private val folder: Folder,
) : FolderAnimationCreator {

    override fun createAnimatorSet(isOpening: Boolean): AnimatorSet {
        val dragLayer = folder.mActivityContext.dragLayer
        val lp = folder.layoutParams as BaseDragLayer.LayoutParams
        val folderIcon = folder.folderIcon
        val previewBackground = folderIcon.mBackground

        folderIcon.previewItemManager.recomputePreviewDrawingParams()

        val iconLocation = IntArray(2)
        val overlayLocation = IntArray(2)
        folderIcon.getLocationOnScreen(iconLocation)
        val folderGlass = folder.folderGlass
        if (folderGlass != null) {
            folderGlass.screenLocation(overlayLocation)
        } else {
            dragLayer.getLocationOnScreen(overlayLocation)
        }

        // Resting icon plate in overlay coordinates
        val startLeft = (iconLocation[0] + previewBackground.offsetX - overlayLocation[0]).toFloat()
        val startTop = (iconLocation[1] + previewBackground.offsetY - overlayLocation[1]).toFloat()
        val startWidth = previewBackground.plateWidth.toFloat()
        val startHeight = previewBackground.plateHeight.toFloat()
        val startRadius = previewBackground.plateCornerRadius

        // Centered folder card in overlay coordinates
        val endLeft = lp.x.toFloat()
        val endTop = (lp.y + folder.cardTop).toFloat()
        val endWidth = lp.width.toFloat()
        val endHeight = folder.cardHeight.toFloat()
        val endRadius = Folder.FOLDER_GLASS_CORNER_RADIUS_DP * folder.resources.displayMetrics.density

        val startFolderBlur = 4f
        val targetBgBlur = 15f
        val targetFolderBlur = 19f
        val blurOverlay = folder.blurOverlay

        val folderAnimData = folder.getAnimationData(isOpening)
        val iconAnimDataList = folder.getIconAnimationDataList(folderAnimData)
        val initialContentScale = folderAnimData.initialFolderScale
        val contentHeightDiff = folderAnimData.contentHeightDifference
        val itemsOnPage = folder.getItemsOnPage(folder.content.currentPage)

        val contentPadLeft = folder.content.paddingLeft.toFloat()
        val contentPadTop = folder.content.paddingTop.toFloat()
        val contentOffsetX = contentPadLeft * initialContentScale
        val contentOffsetY = contentPadTop * initialContentScale
        val childIconDeltas = iconAnimDataList.map { iconData ->
            Triple(iconData, iconData.xDistance, iconData.yDistance)
        }

        folder.setClipPath(null)
        folder.content.setClipPath(null)
        folder.clipChildren = false
        folder.clipToPadding = false
        folder.content.clipChildren = false
        folder.content.clipToPadding = false
        folder.content.currentCellLayout?.let { cell ->
            cell.clipChildren = false
            cell.clipToPadding = false
            cell.shortcutsAndWidgets?.let { group ->
                group.clipChildren = false
                group.clipToPadding = false
            }
        }

        fun applyProgress(t: Float) {
            val curLeft = startLeft + t * (endLeft - startLeft)
            val curTop = startTop + t * (endTop - startTop)
            val curWidth = startWidth + t * (endWidth - startWidth)
            val curHeight = startHeight + t * (endHeight - startHeight)
            val curRadius = startRadius + t * (endRadius - startRadius)

            // 1. Synchronize LiquidGlassPanel (glass backdrop)
            folderGlass?.setPaneBounds(curLeft, curTop, curWidth, curHeight, 1f)
            folderGlass?.setCornerRadius(curRadius)
            folderGlass?.setBlurRadiusDp(startFolderBlur + t * (targetFolderBlur - startFolderBlur))
            blurOverlay?.setBlurRadiusDp(t * targetBgBlur)

            // Crossfade blur overlay near end of close animation to hide downscale artifacts
            val bgAlpha = if (!isOpening) (t / 0.18f).coerceIn(0f, 1f) else 1f
            blurOverlay?.alpha = bgAlpha

            // 2. Synchronize Folder view position
            val curContentOffsetX = (1f - t) * contentOffsetX
            val curContentOffsetY = (1f - t) * contentOffsetY
            folder.translationX = curLeft - curContentOffsetX - endLeft
            folder.translationY = curTop - curContentOffsetY - endTop

            // 3. Synchronize Content & Footer scale
            val curContentScale = initialContentScale + t * (1f - initialContentScale)
            folder.content.pivotX = 0f
            folder.content.pivotY = 0f
            folder.content.scaleX = curContentScale
            folder.content.scaleY = curContentScale

            folder.mFooter.pivotX = 0f
            folder.mFooter.pivotY = 0f
            folder.mFooter.scaleX = curContentScale
            folder.mFooter.scaleY = curContentScale
            folder.mFooter.translationY = -(1f - t) * contentHeightDiff

            // 4. Synchronize Child Icons
            for ((iconData, startX, startY) in childIconDeltas) {
                val icon = iconData.icon
                val curIconScale = iconData.initialIconScale + t * (1f - iconData.initialIconScale)
                icon.scaleX = curIconScale
                icon.scaleY = curIconScale
                icon.translationX = (1f - t) * startX
                icon.translationY = (1f - t) * startY
                if (!iconData.itemsInPreview.contains(icon)) {
                    icon.alpha = ((t - 0.2f) / 0.8f).coerceIn(0f, 1f)
                }
            }

            // 6. Child icons text alpha
            val textAlpha = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
            for (icon in itemsOnPage) {
                val titleText = FolderAnimationSpringBuilderManager.getBubbleTextView(icon)
                BubbleTextView.TEXT_ALPHA_PROPERTY.set(titleText, textAlpha)
            }

            // 7. Folder title alpha
            folder.folderName.alpha = ((t - 0.2f) / 0.8f).coerceIn(0f, 1f)

            // 8. Footer alpha if visible
            if (folder.mFooter.visibility == View.VISIBLE) {
                folder.mFooter.alpha = ((t - 0.3f) / 0.7f).coerceIn(0f, 1f)
            }

            // 9. Elevation
            val midT = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)
            folder.translationZ = (midT - 1f) * folder.elevation
        }

        val duration = if (isOpening) 400L else 350L
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = duration
        animator.interpolator = PathInterpolator(0.2f, 0.25f, 0.05f, 1f)

        animator.addUpdateListener { va ->
            val fraction = va.animatedValue as Float
            val t = if (isOpening) fraction else (1f - fraction)
            applyProgress(t)
        }

        // Apply initial frame 0 values immediately to eliminate any jump or flicker
        applyProgress(if (isOpening) 0f else 1f)

        val animatorSet = AnimatorSet()
        animatorSet.play(animator)
        animatorSet.addListener(FolderOpenCloseAnimationListener(folder, isOpening))
        animatorSet.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    folder.setClipPath(null)
                    folder.content.setClipPath(null)
                    for (iconData in iconAnimDataList) {
                        val icon = iconData.icon
                        icon.translationX = 0f
                        icon.translationY = 0f
                        icon.scaleX = 1f
                        icon.scaleY = 1f
                        if (!iconData.itemsInPreview.contains(icon)) {
                            icon.alpha = 1f
                        }
                    }
                    for (icon in itemsOnPage) {
                        val titleText = FolderAnimationSpringBuilderManager.getBubbleTextView(icon)
                        BubbleTextView.TEXT_ALPHA_PROPERTY.set(titleText, 1f)
                        titleText.setTextVisibility(true)
                    }
                }
            }
        )
        letContentOverflowWhileAnimating(folder, animatorSet)

        return animatorSet
    }

    private fun letContentOverflowWhileAnimating(folder: Folder, animatorSet: AnimatorSet) {
        animatorSet.addListener(
            object : AnimatorListenerAdapter() {
                private var savedFolderClipChildren = true
                private var savedFolderClipToPadding = true
                private var savedContentClipChildren = true
                private var savedContentClipToPadding = true
                private var savedCellClipChildren = true
                private var savedCellClipToPadding = true
                private var savedItemsClipChildren = true
                private var savedItemsClipToPadding = true

                private var cellLayout: ViewGroup? = null
                private var items: ViewGroup? = null

                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    savedFolderClipChildren = folder.clipChildren
                    savedFolderClipToPadding = folder.clipToPadding
                    savedContentClipChildren = folder.content.clipChildren
                    savedContentClipToPadding = folder.content.clipToPadding

                    folder.clipChildren = false
                    folder.clipToPadding = false
                    folder.content.clipChildren = false
                    folder.content.clipToPadding = false

                    val cell = folder.content.currentCellLayout
                    cellLayout = cell
                    if (cell != null) {
                        savedCellClipChildren = cell.clipChildren
                        savedCellClipToPadding = cell.clipToPadding
                        cell.clipChildren = false
                        cell.clipToPadding = false

                        val group = cell.shortcutsAndWidgets
                        items = group
                        if (group != null) {
                            savedItemsClipChildren = group.clipChildren
                            savedItemsClipToPadding = group.clipToPadding
                            group.clipChildren = false
                            group.clipToPadding = false
                        }
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    folder.clipChildren = savedFolderClipChildren
                    folder.clipToPadding = savedFolderClipToPadding
                    folder.content.clipChildren = savedContentClipChildren
                    folder.content.clipToPadding = savedContentClipToPadding
                    cellLayout?.let {
                        it.clipChildren = savedCellClipChildren
                        it.clipToPadding = savedCellClipToPadding
                    }
                    items?.let {
                        it.clipChildren = savedItemsClipChildren
                        it.clipToPadding = savedItemsClipToPadding
                    }
                    cellLayout = null
                    items = null
                }
            }
        )
    }
}
