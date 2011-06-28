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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.TextView;



/**
 * An icon on a PagedView, specifically for items in the launcher's paged view (with compound
 * drawables on the top).
 */
public class HolographicPagedViewIcon extends TextView  {
    PagedViewIcon mOriginalIcon;
    Paint mPaint;

    public HolographicPagedViewIcon(Context context, PagedViewIcon original) {
        super(context);
        mOriginalIcon = original;
        mPaint = new Paint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap overlay = mOriginalIcon.getHolographicOutline();
        if (overlay != null) {
            final int offset = getScrollX();
            final int compoundPaddingLeft = getCompoundPaddingLeft();
            final int compoundPaddingRight = getCompoundPaddingRight();
            int hspace = getWidth() - compoundPaddingRight - compoundPaddingLeft;
            canvas.drawBitmap(overlay,
                    offset + compoundPaddingLeft + (hspace - overlay.getWidth()) / 2,
                    mOriginalIcon.getPaddingTop(),
                    mPaint);
        }
    }
}
