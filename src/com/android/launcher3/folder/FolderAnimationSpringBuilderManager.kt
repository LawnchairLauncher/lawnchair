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
package com.android.launcher3.folder

import android.animation.AnimatorSet
import android.graphics.Color
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_FOLDER_ANIM
import com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.folder.ClipRevealData.Factory.getClipRevealData
import com.android.launcher3.folder.FolderAnimationData.Factory.getAnimationData
import com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer
import com.android.launcher3.folder.IconAnimationData.Factory.getIconAnimationDataList
import com.android.launcher3.graphics.ShapeDelegate

/**
 * Manages the opening and closing animations for a [Folder].
 *
 * All of the animations are done in the Folder. ie. When the user taps on the FolderIcon, we
 * immediately hide the FolderIcon and show the Folder in its place before starting the animation.
 *
 * @param folder the [Folder] to animate open or closed
 * @param isOpening whether we are opening or closing the [Folder]
 */
class FolderAnimationSpringBuilderManager(
    private val folder: Folder,
    private val shapeDelegate: ShapeDelegate,
    private val launcherDelegate: LauncherDelegate,
) : FolderAnimationCreator {
    override fun createAnimatorSet(isOpening: Boolean): AnimatorSet {
        // Since we scale down workspace/hotseat when opening folder,
        // need to have initial values to find starting folder icon location
        resetLauncherScale(launcherDelegate)
        val folderAnimData: FolderAnimationData = folder.getAnimationData(isOpening)
        val clipRevealData: ClipRevealData = folder.getClipRevealData(shapeDelegate, folderAnimData)
        val iconAnimData: List<IconAnimationData> = folder.getIconAnimationDataList(folderAnimData)
        return FolderSpringAnimatorSet.build(
                folder = folder,
                launcherDelegate = launcherDelegate,
                folderAnimData = folderAnimData,
                clipRevealData = clipRevealData,
                iconAnimData = iconAnimData,
            )
            .animatorSet
    }

    companion object {
        /** Resets the scale of the launcher workspace. Used to prepare for folder calculations. */
        @JvmStatic
        fun resetLauncherScale(launcherDelegate: LauncherDelegate) {
            val launcher = launcherDelegate.launcher ?: return
            val workspace = launcher.workspace
            val hotseat = launcher.hotseat
            // Used to match the translation of the scaling between hotseat and workspace.
            workspace.setPivotToScaleWithSelf(hotseat)
            WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_FOLDER_ANIM).set(workspace, 1f)
            HOTSEAT_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_FOLDER_ANIM).set(hotseat, 1f)
        }

        /** Resets the scrim and wallpaper zoom, which are changed by the folder animation. */
        @JvmStatic
        fun resetScrimAndZoom(launcherDelegate: LauncherDelegate) {
            val launcher = launcherDelegate.launcher ?: return
            val scrim = launcher.scrimView
            launcherDelegate.launcher?.depthController?.folderZoom?.value = 0f
            if (!launcher.isInState(LauncherState.ALL_APPS)) {
                scrim.alpha = 1f
                scrim.setBackgroundColor(Color.TRANSPARENT)
            }
            if (Utilities.ATLEAST_S && launcher.stateManager.state.getDepth(launcher) == 0f) {
                for (target in launcher.depthBlurTargets) {
                    target.setRenderEffect(null)
                }
            }
        }

        /** Returns the list of "preview items" on {@param page}. */
        fun getPreviewIconsOnPage(folder: Folder, page: Int): List<View> {
            return createFolderGridOrganizer(folder.mActivityContext.deviceProfile)
                .setFolderInfo(folder.mInfo)
                .previewItemsForPage(page, folder.iconsInReadingOrder)
        }

        /**
         * Gets the [BubbleTextView] from an icon. In some cases the BubbleTextView is the whole
         * icon itself, while in others it is contained within the view and only serves to store the
         * title text.
         */
        fun getBubbleTextView(v: View): BubbleTextView {
            return if (v is AppPairIcon) v.titleTextView else (v as BubbleTextView)
        }
    }
}
