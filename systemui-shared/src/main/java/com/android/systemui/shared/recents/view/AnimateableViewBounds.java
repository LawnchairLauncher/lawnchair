/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.shared.recents.view;

import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewOutlineProvider;

import com.android.systemui.shared.recents.utilities.Utilities;

/**
 * An outline provider that has a clip and outline that can be animated.
 */
public class AnimateableViewBounds extends ViewOutlineProvider {

    private static final float MIN_ALPHA = 0.1f;
    private static final float MAX_ALPHA = 0.8f;

    protected View mSourceView;
    protected Rect mClipRect = new Rect();
    protected Rect mClipBounds = new Rect();
    protected Rect mLastClipBounds = new Rect();
    protected int mCornerRadius;
    protected float mAlpha = 1f;

    public AnimateableViewBounds(View source, int cornerRadius) {
        mSourceView = source;
        mCornerRadius = cornerRadius;
    }

    /**
     * Resets the right and bottom clip for this view.
     */
    public void reset() {
        mClipRect.set(0, 0, 0, 0);
        updateClipBounds();
    }

    @Override
    public void getOutline(View view, Outline outline) {
        outline.setAlpha(Utilities.mapRange(mAlpha, MIN_ALPHA, MAX_ALPHA));
        if (mCornerRadius > 0) {
            outline.setRoundRect(mClipRect.left, mClipRect.top,
                    mSourceView.getWidth() - mClipRect.right,
                    mSourceView.getHeight() - mClipRect.bottom,
                    mCornerRadius);
        } else {
            outline.setRect(mClipRect.left, mClipRect.top,
                    mSourceView.getWidth() - mClipRect.right,
                    mSourceView.getHeight() - mClipRect.bottom);
        }
    }

    /**
     * Sets the view outline alpha.
     */
    public void setAlpha(float alpha) {
        if (Float.compare(alpha, mAlpha) != 0) {
            mAlpha = alpha;
            // TODO, If both clip and alpha change in the same frame, only invalidate once
            mSourceView.invalidateOutline();
        }
    }

    /**
     * @return the outline alpha.
     */
    public float getAlpha() {
        return mAlpha;
    }

    /**
     * Sets the top clip.
     */
    public void setClipTop(int top) {
        mClipRect.top = top;
        updateClipBounds();
    }

    /**
     * @return the top clip.
     */
    public int getClipTop() {
        return mClipRect.top;
    }

    /**
     * Sets the bottom clip.
     */
    public void setClipBottom(int bottom) {
        mClipRect.bottom = bottom;
        updateClipBounds();
    }

    /**
     * @return the bottom clip.
     */
    public int getClipBottom() {
        return mClipRect.bottom;
    }

    /**
     * @return the clip bounds.
     */
    public Rect getClipBounds() {
        return mClipBounds;
    }

    protected void updateClipBounds() {
        mClipBounds.set(Math.max(0, mClipRect.left), Math.max(0, mClipRect.top),
                mSourceView.getWidth() - Math.max(0, mClipRect.right),
                mSourceView.getHeight() - Math.max(0, mClipRect.bottom));
        if (!mLastClipBounds.equals(mClipBounds)) {
            mSourceView.setClipBounds(mClipBounds);
            // TODO, If both clip and alpha change in the same frame, only invalidate once
            mSourceView.invalidateOutline();
            mLastClipBounds.set(mClipBounds);
        }
    }
}
