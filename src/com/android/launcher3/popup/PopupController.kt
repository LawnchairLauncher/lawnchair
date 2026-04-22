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

import com.android.launcher3.model.data.ItemInfo

/**
 * Controller interface for popups. It handles actions for the popups such as showing and dismissing
 * popups.
 */
interface PopupController {
    /**
     * Shows the popup when called.
     *
     * @param popupDataRepository is the repository which has all the data we need to show the
     *   correct long press menu shortcuts.
     * @return PopupDragController which handles drag related actions due to showing the popup.
     */
    fun show(popupDataRepository: PopupDataRepository): Popup?

    /** Dismisses the popup when called. */
    fun dismiss()

    /** Factory for making a popup controller. */
    companion object PopupControllerFactory {
        /**
         * Creates a popup controller.
         *
         * @param itemInfo is the item info for the popup controller for which we create the popup
         *   controller.
         * @return a new PopupController.
         */
        fun createPopupControllerFactory(itemInfo: ItemInfo): PopupController {
            return TODO("Provide the return value")
        }
    }
}
