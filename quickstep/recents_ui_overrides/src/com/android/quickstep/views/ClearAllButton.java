/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.widget.Button;

import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.quickstep.views.RecentsView.PageCallbacks;
import com.android.quickstep.views.RecentsView.ScrollState;

public class ClearAllButton extends Button implements PageCallbacks {

    public static final FloatProperty<ClearAllButton> VISIBILITY_ALPHA =
            new FloatProperty<ClearAllButton>("visibilityAlpha") {
                @Override
                public Float get(ClearAllButton view) {
                    return view.mVisibilityAlpha;
                }

                @Override
                public void setValue(ClearAllButton view, float v) {
                    view.setVisibilityAlpha(v);
                }
            };

    private float mScrollAlpha = 1;
    private float mContentAlpha = 1;
    private float mVisibilityAlpha = 1;

    private boolean mIsRtl;

    private int mScrollOffset;

    public ClearAllButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        PagedOrientationHandler orientationHandler = getRecentsView().getPagedOrientationHandler();
        mScrollOffset = orientationHandler.getClearAllScrollOffset(getRecentsView(), mIsRtl);
    }

    private RecentsView getRecentsView() {
        return (RecentsView) getParent();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        mIsRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setContentAlpha(float alpha) {
        if (mContentAlpha != alpha) {
            mContentAlpha = alpha;
            updateAlpha();
        }
    }

    public void setVisibilityAlpha(float alpha) {
        if (mVisibilityAlpha != alpha) {
            mVisibilityAlpha = alpha;
            updateAlpha();
        }
    }

    @Override
    public void onPageScroll(ScrollState scrollState) {
        PagedOrientationHandler orientationHandler = getRecentsView().getPagedOrientationHandler();
        float orientationSize = orientationHandler.getPrimaryValue(getWidth(), getHeight());
        if (orientationSize == 0) {
            return;
        }

        float shift = Math.min(scrollState.scrollFromEdge, orientationSize);
        float translation = mIsRtl ? (mScrollOffset - shift) : (mScrollOffset + shift);
        orientationHandler.setPrimaryAndResetSecondaryTranslate(this, translation);
        mScrollAlpha = 1 - shift / orientationSize;
        updateAlpha();
    }

    private void updateAlpha() {
        final float alpha = mScrollAlpha * mContentAlpha * mVisibilityAlpha;
        setAlpha(alpha);
        setClickable(alpha == 1);
    }
}
