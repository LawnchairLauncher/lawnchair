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
package com.android.quickstep.util.unfold

import android.graphics.Point
import android.view.ViewGroup
import com.android.app.animation.Interpolators.LINEAR
import com.android.app.animation.Interpolators.clampToProgress
import com.android.launcher3.CellLayout
import com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_UNFOLD_ANIMATION
import com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X
import com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y
import com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY
import com.android.launcher3.Workspace
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.HorizontalInsettableView

private typealias ViewGroupAction = (ViewGroup, Boolean) -> Unit

object UnfoldAnimationBuilder {

    private val CLIP_CHILDREN: ViewGroupAction = ViewGroup::setClipChildren
    private val CLIP_TO_PADDING: ViewGroupAction = ViewGroup::setClipToPadding

    data class RestoreInfo(val action: ViewGroupAction, var target: ViewGroup, var value: Boolean)

    // Percentage of the width of the quick search bar that will be reduced
    // from the both sides of the bar when progress is 0
    private const val MAX_WIDTH_INSET_FRACTION = 0.04f

    // Scale factor for the whole workspace and hotseat
    private const val SCALE_LAUNCHER_FROM = 0.92f

    // Translation factor for all the items on the homescreen
    private const val TRANSLATION_PERCENTAGE = 0.08f

    private fun setClipChildren(
        target: ViewGroup,
        value: Boolean,
        restoreList: MutableList<RestoreInfo>
    ) {
        val originalValue = target.clipChildren
        if (originalValue != value) {
            target.clipChildren = value
            restoreList.add(RestoreInfo(CLIP_CHILDREN, target, originalValue))
        }
    }

    private fun setClipToPadding(
        target: ViewGroup,
        value: Boolean,
        restoreList: MutableList<RestoreInfo>
    ) {
        val originalValue = target.clipToPadding
        if (originalValue != value) {
            target.clipToPadding = value
            restoreList.add(RestoreInfo(CLIP_TO_PADDING, target, originalValue))
        }
    }

    private fun addChildrenAnimation(
        itemsContainer: ViewGroup,
        isVerticalFold: Boolean,
        screenSize: Point,
        anim: PendingAnimation
    ) {
        val tempLocation = IntArray(2)
        for (i in 0 until itemsContainer.childCount) {
            val child = itemsContainer.getChildAt(i)

            child.getLocationOnScreen(tempLocation)
            if (isVerticalFold) {
                val viewCenterX = tempLocation[0] + child.width / 2
                val distanceFromScreenCenterToViewCenter = screenSize.x / 2 - viewCenterX
                anim.addFloat(
                    child,
                    VIEW_TRANSLATE_X,
                    distanceFromScreenCenterToViewCenter * TRANSLATION_PERCENTAGE,
                    0f,
                    LINEAR
                )
            } else {
                val viewCenterY = tempLocation[1] + child.height / 2
                val distanceFromScreenCenterToViewCenter = screenSize.y / 2 - viewCenterY
                anim.addFloat(
                    child,
                    VIEW_TRANSLATE_Y,
                    distanceFromScreenCenterToViewCenter * TRANSLATION_PERCENTAGE,
                    0f,
                    LINEAR
                )
            }
        }
    }

    /**
     * Builds an animation for the unfold experience and adds it to the provided PendingAnimation
     */
    fun buildUnfoldAnimation(
        launcher: QuickstepLauncher,
        isVerticalFold: Boolean,
        screenSize: Point,
        anim: PendingAnimation
    ) {
        val restoreList = ArrayList<RestoreInfo>()
        val registerViews: (CellLayout) -> Unit = { cellLayout ->
            setClipChildren(cellLayout, false, restoreList)
            setClipToPadding(cellLayout, false, restoreList)
            addChildrenAnimation(cellLayout.shortcutsAndWidgets, isVerticalFold, screenSize, anim)
        }

        val workspace: Workspace<*> = launcher.workspace
        val hotseat = launcher.hotseat

        // Animation icons from workspace for all orientations
        workspace.forEachVisiblePage { registerViews(it as CellLayout) }
        setClipChildren(workspace, false, restoreList)
        setClipToPadding(workspace, true, restoreList)

        // Workspace scale
        launcher.workspace.setPivotToScaleWithSelf(launcher.hotseat)
        val interpolator = clampToProgress(LINEAR, 0f, 1f)
        anim.addFloat(
            workspace,
            WORKSPACE_SCALE_PROPERTY_FACTORY[SCALE_INDEX_UNFOLD_ANIMATION],
            SCALE_LAUNCHER_FROM,
            1f,
            interpolator
        )
        anim.addFloat(
            hotseat,
            HOTSEAT_SCALE_PROPERTY_FACTORY[SCALE_INDEX_UNFOLD_ANIMATION],
            SCALE_LAUNCHER_FROM,
            1f,
            interpolator
        )

        if (isVerticalFold) {
            if (hotseat.qsb is HorizontalInsettableView) {
                anim.addFloat(
                    hotseat.qsb as HorizontalInsettableView,
                    HorizontalInsettableView.HORIZONTAL_INSETS,
                    MAX_WIDTH_INSET_FRACTION,
                    0f,
                    LINEAR
                )
            }
            registerViews(hotseat)
        }
        anim.addEndListener { restoreList.forEach { it.action(it.target, it.value) } }
    }
}
