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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * A {@link BubbleTextView} that has the shortcut icon on the left and drag handle on the right.
 */
public class DeepShortcutTextView extends BubbleTextView {

    private boolean mShowLoadingState;
    private Drawable mLoadingStatePlaceholder;
    private final Rect mLoadingStateBounds = new Rect();

    public DeepShortcutTextView(Context context) {
        this(context, null, 0);
    }

    public DeepShortcutTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        showLoadingState(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setLoadingBounds();
    }

    private void setLoadingBounds() {
        if (mLoadingStatePlaceholder == null) {
            return;
        }
        mLoadingStateBounds.set(
                0,
                0,
                getMeasuredWidth() - getPaddingEnd() - getPaddingStart(),
                mLoadingStatePlaceholder.getIntrinsicHeight());
        mLoadingStateBounds.offset(
                Utilities.isRtl(getResources()) ? getPaddingEnd() : getPaddingStart(),
                (int) ((getMeasuredHeight() - mLoadingStatePlaceholder.getIntrinsicHeight())
                        / 2.0f)
        );
        mLoadingStatePlaceholder.setBounds(mLoadingStateBounds);
    }

    @Override
    protected void applyCompoundDrawables(Drawable icon) {
        // The icon is drawn in a separate view.
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);

        if (!TextUtils.isEmpty(text)) {
            showLoadingState(false);
        }
    }

    @Override
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        // assume the whole view as clickable
        return false;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (!mShowLoadingState) {
            super.onDraw(canvas);
            return;
        }

        mLoadingStatePlaceholder.draw(canvas);
    }

    @Override
    protected void drawDotIfNecessary(Canvas canvas) {
        // This view (with the text label to the side of the icon) is not designed for a dot to be
        // drawn on top of it, so never draw one even if a notification for this shortcut exists.
    }

    private void showLoadingState(boolean loading) {
        if (loading == mShowLoadingState) {
            return;
        }

        mShowLoadingState = loading;

        if (loading) {
            mLoadingStatePlaceholder = getContext().getDrawable(
                    R.drawable.deep_shortcuts_text_placeholder);
            setLoadingBounds();
        } else {
            mLoadingStatePlaceholder = null;
        }

        invalidate();
    }
}
