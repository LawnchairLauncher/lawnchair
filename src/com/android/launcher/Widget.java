/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.graphics.Bitmap;
import com.android.internal.provider.Settings;

/**
 * Represents one instance of a Launcher widget (clock, search, photo frame).
 *
 */
class Widget extends ItemInfo {

    int layoutResource;
    Bitmap photo;

    static Widget makeClock() {
        Widget w = new Widget();
        w.itemType = Settings.Favorites.ITEM_TYPE_WIDGET_CLOCK;
        w.spanX = 2;
        w.spanY = 2;
        w.layoutResource = R.layout.widget_clock;
        return w;
    }
    
    static Widget makePhotoFrame() {
        Widget w = new Widget();
        w.itemType = Settings.Favorites.ITEM_TYPE_WIDGET_PHOTO_FRAME;
        w.spanX = 2;
        w.spanY = 2;
        w.layoutResource = R.layout.widget_photo_frame;
        return w;
    } 
    
    static Widget makeSearch() {
        Widget w = new Widget();
        w.itemType = Settings.Favorites.ITEM_TYPE_WIDGET_SEARCH;
        w.spanX = 4;
        w.spanY = 1;
        w.layoutResource = R.layout.widget_search;
        return w;
    }

    @Override
    void onAddToDatabase(ContentValues values) { 
        super.onAddToDatabase(values);
        writeBitmap(values, photo);
    }

}
