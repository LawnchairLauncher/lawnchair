/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragLayer;

/**
 * A temporary view which displays the a bitmap (used for folder icon animation)
 */
public class PreviewImageView extends ImageView {

    private final Rect mTempRect = new Rect();
    private final DragLayer mParent;

    private Bitmap mBitmap;
    private Canvas mCanvas;

    public PreviewImageView(DragLayer parent) {
        super(parent.getContext());
        mParent = parent;
    }

    public void copy(View view) {
        final int width = view.getMeasuredWidth();
        final int height = view.getMeasuredHeight();

        if (mBitmap == null || mBitmap.getWidth() != width || mBitmap.getHeight() != height) {
            mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }

        DragLayer.LayoutParams lp;
        if (getLayoutParams() instanceof DragLayer.LayoutParams) {
            lp = (DragLayer.LayoutParams) getLayoutParams();
        } else {
            lp = new DragLayer.LayoutParams(width, height);
        }

        // The layout from which the folder is being opened may be scaled, adjust the starting
        // view size by this scale factor.
        float scale = mParent.getDescendantRectRelativeToSelf(view, mTempRect);
        lp.customPosition = true;
        lp.x = mTempRect.left;
        lp.y = mTempRect.top;
        lp.width = (int) (scale * width);
        lp.height = (int) (scale * height);

        mCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        view.draw(mCanvas);
        setImageBitmap(mBitmap);

        // Just in case this image view is still in the drag layer from a previous animation,
        // we remove it and re-add it.
        removeFromParent();
        mParent.addView(this, lp);
    }

    public void removeFromParent() {
        if (mParent.indexOfChild(this) != -1) {
            mParent.removeView(this);
        }
    }

    public static PreviewImageView get(Context context) {
        DragLayer dragLayer = Launcher.getLauncher(context).getDragLayer();
        PreviewImageView view = (PreviewImageView) dragLayer.getTag(R.id.preview_image_id);
        if (view == null) {
            view = new PreviewImageView(dragLayer);
            dragLayer.setTag(R.id.preview_image_id, view);
        }
        return view;
    }
}
