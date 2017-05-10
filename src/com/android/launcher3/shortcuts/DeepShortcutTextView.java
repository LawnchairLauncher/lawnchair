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

package com.android.launcher3.shortcuts;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * A {@link BubbleTextView} that has the shortcut icon on the left and drag handle on the right.
 */
public class DeepShortcutTextView extends BubbleTextView {
    private final Rect mDragHandleBounds = new Rect();
    private final int mDragHandleWidth;
    private boolean mShouldPerformClick = true;

    public DeepShortcutTextView(Context context) {
        this(context, null, 0);
    }

    public DeepShortcutTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Resources resources = getResources();
        mDragHandleWidth = resources.getDimensionPixelSize(R.dimen.popup_padding_end)
                + resources.getDimensionPixelSize(R.dimen.deep_shortcut_drag_handle_size)
                + resources.getDimensionPixelSize(R.dimen.deep_shortcut_drawable_padding) / 2;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        mDragHandleBounds.set(0, 0, mDragHandleWidth, getMeasuredHeight());
        if (!Utilities.isRtl(getResources())) {
            mDragHandleBounds.offset(getMeasuredWidth() - mDragHandleBounds.width(), 0);
        }
    }

    @Override
    protected void applyCompoundDrawables(Drawable icon) {
        // The icon is drawn in a separate view.
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // Ignore clicks on the drag handle (long clicks still start the drag).
            mShouldPerformClick = !mDragHandleBounds.contains((int) ev.getX(), (int) ev.getY());
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean performClick() {
        return mShouldPerformClick && super.performClick();
    }
}
