/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher2;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

public class WidgetChooser extends HomeCustomizationItemGallery implements DragSource {
    private DragController mDragController;

    public WidgetChooser(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setDragController(DragController dragger) {
        mDragController = dragger;
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Drawable[] drawables = ((TextView)view).getCompoundDrawables();
        Bitmap bmp = ((BitmapDrawable)drawables[1]).getBitmap();
        final int w = bmp.getWidth();
        final int h = bmp.getHeight();

        // We don't really have an accurate location to use.  This will do.
        int screenX = mMotionDownRawX - (w / 2);
        int screenY = mMotionDownRawY - h;

        AppWidgetProviderInfo info = (AppWidgetProviderInfo)getAdapter().getItem(position);
        LauncherAppWidgetInfo dragInfo = new LauncherAppWidgetInfo(info.provider);
        // TODO: Is this really the best place to do this?
        dragInfo.minWidth = info.minWidth;
        dragInfo.minHeight = info.minHeight;
        mDragController.startDrag(bmp, screenX, screenY,
                0, 0, w, h, this, dragInfo, DragController.DRAG_ACTION_COPY);
        mLauncher.hideCustomizationDrawer();
        return true;
    }

    public void onDropCompleted(View target, boolean success) {
    }
}

