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

package com.android.launcher3.util

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup.OnHierarchyChangeListener
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.WorkspaceLayoutManager
import com.android.launcher3.anim.AnimationSuccessListener
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragController.DragListener
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.FlagDebugUtils.appendFlag
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.PredictedAppIcon
import com.android.launcher3.views.PredictedAppIcon.PredictedIconOutlineDrawing
import java.io.PrintWriter
import java.util.StringJoiner
import java.util.function.Predicate

/** Utility class for mixing and laying out predicted hotseat items. */
class HybridHotseatOrganizer(
    private val activity: ActivityContext,
    private val workspace: WorkspaceLayoutManager,
    private val itemInflater: ItemInflater<*>,
    private val onItemLongClickListener: View.OnLongClickListener? = null,
    private val loadingCheck: (() -> Boolean) = { false },
) : DragListener, DragSource, OnHierarchyChangeListener {

    private val hotseat = workspace.hotseat
    private var pauseFlags = 0

    private val outlineDrawings = mutableListOf<PredictedIconOutlineDrawing>()
    private val mUpdateFillIfNotLoading = Runnable { this.updateFillIfNotLoading() }
    private var iconRemoveAnimators: AnimatorSet? = null

    var predictedItems = emptyList<ItemInfo>()
        set(value) {
            field = value
            fillGapsWithPrediction()
        }

    init {
        hotseat.shortcutsAndWidgets.setOnHierarchyChangeListener(this)
    }

    override fun onChildViewAdded(parent: View?, child: View?) {
        onHotseatHierarchyChanged()
    }

    override fun onChildViewRemoved(parent: View?, child: View?) {
        onHotseatHierarchyChanged()
    }

    private fun onHotseatHierarchyChanged() {
        if (pauseFlags == 0 && !loadingCheck.invoke()) {
            // Post update after a single frame to avoid layout within layout
            Executors.MAIN_EXECUTOR.handler.removeCallbacks(mUpdateFillIfNotLoading)
            Executors.MAIN_EXECUTOR.handler.post(mUpdateFillIfNotLoading)
        }
    }

    private fun updateFillIfNotLoading() {
        if (pauseFlags == 0 && !loadingCheck.invoke()) {
            fillGapsWithPrediction(true)
        }
    }

    fun getPredictedRank(cn: ComponentName, user: UserHandle) =
        predictedItems.indexOfFirst { cn == it.targetComponent && user == it.user }

    /**
     * Called when app/shortcut icon is removed by system. This is used to prune visible stale
     * predictions while while waiting for AppAPrediction service to send new batch of predictions.
     *
     * @param matcher filter matching items that have been removed
     */
    fun onModelItemsRemoved(matcher: Predicate<ItemInfo>) {
        val oldItems = predictedItems.toMutableList()
        if (oldItems.removeIf(matcher)) {
            predictedItems = oldItems
            fillGapsWithPrediction(true)
        }
    }

    @JvmOverloads
    fun fillGapsWithPrediction(animate: Boolean = false) {
        if (pauseFlags != 0) {
            return
        }

        var predictionIndex = 0
        var numViewsAnimated = 0
        val newItems = ArrayList<WorkspaceItemInfo>()
        // make sure predicted icon removal and filling predictions don't step on each other
        val currentAnimation = iconRemoveAnimators
        if (currentAnimation != null && currentAnimation.isRunning()) {
            currentAnimation.addListener(
                AnimatorListeners.forSuccessCallback { fillGapsWithPrediction(animate) }
            )
            return
        }

        val hotseatCount = activity.getDeviceProfile().numShownHotseatIcons

        pauseFlags = pauseFlags or FLAG_FILL_IN_PROGRESS
        for (rank in 0..<hotseatCount) {
            val child =
                hotseat.getChildAt(hotseat.getCellXFromOrder(rank), hotseat.getCellYFromOrder(rank))

            if (child != null && !isPredictedIcon(child)) continue

            if (predictedItems.size <= predictionIndex) {
                // Remove predicted apps from the past
                if (isPredictedIcon(child)) hotseat.removeView(child)
                continue
            }
            val predictedItem = predictedItems[predictionIndex++] as WorkspaceItemInfo

            if (isPredictedIcon(child) && child.isEnabled) {
                val icon = child as PredictedAppIcon
                if (icon.applyFromWorkspaceItemWithAnimation(predictedItem, numViewsAnimated)) {
                    numViewsAnimated++
                }
                finishBinding(icon)
            } else {
                newItems.add(predictedItem)
            }
            preparePredictionInfo(predictedItem, rank)
        }
        bindItems(newItems, animate)

        pauseFlags = pauseFlags and FLAG_FILL_IN_PROGRESS.inv()
    }

    private fun bindItems(itemsToAdd: List<WorkspaceItemInfo>, animate: Boolean) {
        val animationSet = AnimatorSet()
        for (item in itemsToAdd) {
            val icon: View = itemInflater.inflateItem(item, hotseat) ?: continue
            workspace.addInScreenFromBind(icon, item)
            finishBinding(icon)
            if (animate) {
                animationSet.play(
                    ObjectAnimator.ofFloat(icon, LauncherAnimUtils.SCALE_PROPERTY, 0.2f, 1f)
                )
            }
        }
        if (animate) {
            animationSet.addListener(
                AnimatorListeners.forSuccessCallback { this.removeOutlineDrawings() }
            )
            animationSet.start()
        } else {
            removeOutlineDrawings()
        }
    }

    private fun preparePredictionInfo(itemInfo: WorkspaceItemInfo, rank: Int) {
        itemInfo.container = Favorites.CONTAINER_HOTSEAT_PREDICTION
        itemInfo.rank = rank
        itemInfo.cellX = hotseat.getCellXFromOrder(rank)
        itemInfo.cellY = hotseat.getCellYFromOrder(rank)
        itemInfo.screenId = rank
    }

    private fun finishBinding(view: View) {
        view.setOnLongClickListener(onItemLongClickListener)
        (view.layoutParams as CellLayoutLayoutParams).canReorder = false
    }

    private fun removeOutlineDrawings() {
        if (outlineDrawings.isEmpty()) return
        for (outlineDrawing in outlineDrawings) {
            hotseat.removeDelegatedCellDrawing(outlineDrawing)
        }
        hotseat.invalidate()
        outlineDrawings.clear()
    }

    fun getPredictedIcons(): List<PredictedAppIcon> = buildList {
        val vg = hotseat.shortcutsAndWidgets
        for (i in 0..<vg.childCount) {
            val child = vg.getChildAt(i)
            if (isPredictedIcon(child)) {
                add(child as PredictedAppIcon)
            }
        }
    }

    private fun removePredictedApps(
        outlines: MutableList<PredictedIconOutlineDrawing>,
        dragObject: DragObject,
    ) {
        iconRemoveAnimators?.end()
        val anim = AnimatorSet()
        removeOutlineDrawings()
        for (icon in getPredictedIcons()) {
            if (!icon.isEnabled) {
                continue
            }
            if (dragObject.dragSource === this && icon == dragObject.originalView) {
                removeIconWithoutNotify(icon)
                continue
            }
            val rank = (icon.tag as WorkspaceItemInfo).rank
            outlines.add(
                PredictedIconOutlineDrawing(
                    hotseat.getCellXFromOrder(rank),
                    hotseat.getCellYFromOrder(rank),
                    icon,
                )
            )
            icon.isEnabled = false
            val animator = ObjectAnimator.ofFloat(icon, LauncherAnimUtils.SCALE_PROPERTY, 0f)
            animator.addListener(
                object : AnimationSuccessListener() {
                    override fun onAnimationSuccess(animator: Animator) {
                        if (icon.parent != null) {
                            removeIconWithoutNotify(icon)
                        }
                    }
                }
            )
            anim.play(animator)
        }
        iconRemoveAnimators = anim
        anim.start()
    }

    /**
     * Removes icon while suppressing any extra tasks performed on view-hierarchy changes. This
     * avoids recursive/redundant updates as the control updates the UI anyway after it's animation.
     */
    fun removeIconWithoutNotify(icon: PredictedAppIcon) {
        pauseFlags = pauseFlags or FLAG_REMOVING_PREDICTED_ICON
        hotseat.removeView(icon)
        pauseFlags = pauseFlags and FLAG_REMOVING_PREDICTED_ICON.inv()
    }

    override fun onDragStart(dragObject: DragObject, options: DragOptions?) {
        removePredictedApps(outlineDrawings, dragObject)
        if (outlineDrawings.isEmpty()) return
        for (outlineDrawing in outlineDrawings) {
            hotseat.addDelegatedCellDrawing(outlineDrawing)
        }
        pauseFlags = pauseFlags or FLAG_DRAG_IN_PROGRESS
        hotseat.invalidate()
    }

    override fun onDragEnd() {
        pauseFlags = pauseFlags and FLAG_DRAG_IN_PROGRESS.inv()
        fillGapsWithPrediction(true)
    }

    override fun onDropCompleted(target: View?, d: DragObject?, success: Boolean) {
        // Does nothing
    }

    fun dump(prefix: String, writer: PrintWriter) {
        writer.println(prefix + "HybridHotseatOrganizer")
        writer.println("$prefix\tFlags: ${getStateString(pauseFlags)}")
        writer.println("$prefix\tmPredictedItems: ${predictedItems.size}")
        for (info in predictedItems) {
            writer.println(prefix + "\t\t" + info)
        }
    }

    companion object {
        const val FLAG_DRAG_IN_PROGRESS: Int = 1 shl 0
        const val FLAG_FILL_IN_PROGRESS: Int = 1 shl 1
        const val FLAG_REMOVING_PREDICTED_ICON: Int = 1 shl 2

        @JvmStatic
        fun isPredictedIcon(view: View?): Boolean =
            view is PredictedAppIcon &&
                ((view.getTag() as? WorkspaceItemInfo)?.container ==
                    Favorites.CONTAINER_HOTSEAT_PREDICTION)

        private fun getStateString(flags: Int): String {
            val str = StringJoiner("|")
            appendFlag(str, flags, FLAG_DRAG_IN_PROGRESS, "FLAG_DRAG_IN_PROGRESS")
            appendFlag(str, flags, FLAG_FILL_IN_PROGRESS, "FLAG_FILL_IN_PROGRESS")
            appendFlag(str, flags, FLAG_REMOVING_PREDICTED_ICON, "FLAG_REMOVING_PREDICTED_ICON")
            return str.toString()
        }
    }
}
