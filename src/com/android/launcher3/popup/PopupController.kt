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

package com.android.launcher3.popup

import android.content.Context
import android.view.View
import com.android.launcher3.dragndrop.LauncherDragController
import com.android.launcher3.views.ActivityContext

/**
 * Controller interface for popups. It handles actions for the popups such as showing and dismissing
 * popups.
 */
interface PopupController<T> where T : Context, T : ActivityContext {
    /**
     * Shows the popup when called.
     *
     * @return Popup which handles drag related actions due to showing the popup.
     */
    fun show(view: View): Popup?

    /** Dismisses the popup when called. */
    fun dismiss()

    /** Factory for making a popup controller. */
    companion object PopupControllerFactory {
        /**
         * Creates a popup controller.
         *
         * @param popupDataRepository has the popup data for each item.
         * @param dragController handles drag actions.
         * @return a new PopupController.
         */
        fun <T> createPopupController(
            popupDataRepository: PopupDataRepository,
            dragController: LauncherDragController,
        ): PopupController<T> where T : Context, T : ActivityContext? {
            return PopupControllerForExtraHomeScreenItems(popupDataRepository, dragController)
        }

        /**
         * Creates a popup controller.
         *
         * @return a new PopupController.
         */
        fun <T> createPopupController(): PopupController<T> where
        T : Context,
        T : ActivityContext? {
            return PopupControllerForAppIcon()
        }
    }
}
