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

/** Enum for the type of poppable. Based on this we want to show different shortcuts */
enum class PoppableType {
    APP,
    FOLDER,
    WIDGET,
    APP_PAIR,
}

/** Items for which we can trigger a popup menu would implement this interface. */
interface Poppable {

    /** @return a controller to handle actions for the popup. */
    fun getPopupController(): PopupController?

    /** Sets the popup controller to help us handle actions for the popup. */
    fun setPopupController(popupController: PopupController)

    /** @return the type of poppable that this item is. */
    fun getPoppableType(): PoppableType
}
