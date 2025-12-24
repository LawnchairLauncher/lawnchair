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
package com.android.launcher3.integration.popup

import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.PopupData
import com.android.launcher3.popup.PopupDataRepository

class FakePopupDataRepository : PopupDataRepository {
    private var popupData: MutableMap<Int, List<PopupData>> = mutableMapOf()

    override fun getAllPopupData(): Map<Int, List<PopupData>> {
        return popupData
    }

    override fun getPopupDataByItemInfo(itemInfo: ItemInfo): List<PopupData>? {
        return popupData[itemInfo.id]
    }

    fun addPopupData(itemInfoId: Int, popupDataToAdd: List<PopupData>) {
        popupData[itemInfoId] = popupDataToAdd
    }

    fun clearPopupData() {
        popupData.clear()
    }
}
