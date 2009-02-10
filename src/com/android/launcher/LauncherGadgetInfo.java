/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher;

import android.content.ContentValues;
import android.gadget.GadgetHostView;

/**
 * Represents a gadget, which just contains an identifier.
 */
class LauncherGadgetInfo extends ItemInfo {

    /**
     * Identifier for this gadget when talking with {@link GadgetManager} for updates.
     */
    int gadgetId;
    
    /**
     * View that holds this gadget after it's been created.  This view isn't created
     * until Launcher knows it's needed.
     */
    GadgetHostView hostView = null;

    LauncherGadgetInfo(int gadgetId) {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_GADGET;
        this.gadgetId = gadgetId;
    }
    
    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);
        values.put(LauncherSettings.Favorites.GADGET_ID, gadgetId);
    }

    @Override
    public String toString() {
        return Integer.toString(gadgetId);
    }
}
