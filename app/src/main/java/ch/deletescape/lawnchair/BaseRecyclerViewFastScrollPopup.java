/*
 * Copyright (C) 2015 The Android Open Source Project
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
package ch.deletescape.lawnchair;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import ch.deletescape.lawnchair.allapps.theme.IAllAppsThemer;

/**
 * The fast scroller popup that shows the section name the list will jump to.
 */
public class BaseRecyclerViewFastScrollPopup {

    private static final float FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 1.5f;

    private static final int SHADOW_INSET = 3;
    private static final int SHADOW_SHIFT_Y = 2;
    private static final float SHADOW_ALPHA_MULTIPLIER = 0.67f;

    private Resources mRes;
    private BaseRecyclerView mRv;

    private Bitmap mShadow;
    private Paint mShadowPaint;

    private Drawable mBg;
    // The absolute bounds of the fast scroller bg
    private Rect mBgBounds = new Rect();
    private int mBgOriginalSize;
    private Rect mInvalidateRect = new Rect();
    private Rect mTmpRect = new Rect();

    private String mSectionName;
    private Paint mTextPaint;
    private Rect mTextBounds = new Rect();
    private float mAlpha;

    private Animator mAlphaAnimator;
    private boolean mVisible;

    public BaseRecyclerViewFastScrollPopup(BaseRecyclerView rv, Resources res) {
        mRes = res;
        mRv = rv;

        mBgOriginalSize = res.getDimensionPixelSize(R.dimen.container_fastscroll_popup_size);
        mBg = rv.getContext().getDrawable(R.drawable.container_fastscroll_popup_bg);
        mBg.setBounds(0, 0, mBgOriginalSize, mBgOriginalSize);

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(res.getDimensionPixelSize(R.dimen.container_fastscroll_popup_text_size));

        IAllAppsThemer theme = Utilities.getThemer().allAppsTheme(rv.getContext());

        if (theme.getFastScrollerPopupTintColor() != 0) {
            mBg.setTint(theme.getFastScrollerPopupTintColor());
        }
        mTextPaint.setColor(theme.getFastScrollerPopupTextColor());

        mShadowPaint = new Paint();
        mShadowPaint.setAntiAlias(true);
        mShadowPaint.setFilterBitmap(true);
        mShadowPaint.setDither(true);
    }

    /**
     * Sets the section name.
     */
    public void setSectionName(String sectionName) {
        if (!sectionName.equals(mSectionName)) {
            mSectionName = sectionName;
            mTextPaint.getTextBounds(sectionName, 0, sectionName.length(), mTextBounds);
            // Update the width to use measureText since that is more accurate
            mTextBounds.right = (int) (mTextBounds.left + mTextPaint.measureText(sectionName));
        }
    }

    /**
     * Updates the bounds for the fast scroller.
     *
     * @return the invalidation rect for this update.
     */
    public Rect updateFastScrollerBounds(int lastTouchY) {
        mInvalidateRect.set(mBgBounds);

        if (isVisible()) {
            // Calculate the dimensions and position of the fast scroller popup
            int edgePadding = mRv.getMaxScrollbarWidth();
            int bgPadding = (mBgOriginalSize - mTextBounds.height()) / 2;
            int bgHeight = mBgOriginalSize;
            int bgWidth = Math.max(mBgOriginalSize, mTextBounds.width() + (2 * bgPadding));
            if (Utilities.isRtl(mRes)) {
                mBgBounds.left = mRv.getBackgroundPadding().left + (2 * mRv.getMaxScrollbarWidth());
                mBgBounds.right = mBgBounds.left + bgWidth;
            } else {
                mBgBounds.right = mRv.getWidth() - mRv.getBackgroundPadding().right -
                        (2 * mRv.getMaxScrollbarWidth());
                mBgBounds.left = mBgBounds.right - bgWidth;
            }
            mBgBounds.top = lastTouchY - (int) (FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * bgHeight);
            mBgBounds.top = Math.max(edgePadding,
                    Math.min(mBgBounds.top, mRv.getVisibleHeight() - edgePadding - bgHeight));
            mBgBounds.bottom = mBgBounds.top + bgHeight;

            // Generate a bitmap for a shadow matching these bounds
            mShadow = HolographicOutlineHelper.obtain(
                    mRv.getContext()).createMediumDropShadow(mBg, false /* shouldCache */);
        } else {
            mShadow = null;
            mBgBounds.setEmpty();
        }

        // Combine the old and new fast scroller bounds to create the full invalidate rect
        mInvalidateRect.union(mBgBounds);
        return mInvalidateRect;
    }

    /**
     * Animates the visibility of the fast scroller popup.
     */
    public void animateVisibility(boolean visible) {
        if (mVisible != visible) {
            mVisible = visible;
            if (mAlphaAnimator != null) {
                mAlphaAnimator.cancel();
            }
            mAlphaAnimator = ObjectAnimator.ofFloat(this, "alpha", visible ? 1f : 0f);
            mAlphaAnimator.setDuration(visible ? 200 : 150);
            mAlphaAnimator.start();
        }
    }

    // Setter/getter for the popup alpha for animations
    public void setAlpha(float alpha) {
        mAlpha = alpha;
        mRv.invalidate(mBgBounds);
    }

    public float getAlpha() {
        return mAlpha;
    }

    public int getHeight() {
        return mBgOriginalSize;
    }

    public void draw(Canvas c) {
        if (isVisible()) {
            // Determine the alpha and prepare the canvas
            final int alpha = (int) (mAlpha * 255);
            int restoreCount = c.save(Canvas.MATRIX_SAVE_FLAG);
            c.translate(mBgBounds.left, mBgBounds.top);
            mTmpRect.set(mBgBounds);
            mTmpRect.offsetTo(0, 0);

            // Expand the rect (with a negative inset), translate it, and draw the shadow
            if (mShadow != null) {
                mTmpRect.inset(-SHADOW_INSET * 2, -SHADOW_INSET * 2);
                mTmpRect.offset(0, SHADOW_SHIFT_Y);
                mShadowPaint.setAlpha((int) (alpha * SHADOW_ALPHA_MULTIPLIER));
                c.drawBitmap(mShadow, null, mTmpRect, mShadowPaint);
                mTmpRect.inset(SHADOW_INSET * 2, SHADOW_INSET * 2);
                mTmpRect.offset(0, -SHADOW_SHIFT_Y);
            }

            // Draw the background
            mBg.setBounds(mTmpRect);
            mBg.setAlpha(alpha);
            mBg.draw(c);

            // Draw the text
            mTextPaint.setAlpha(alpha);
            c.drawText(mSectionName, (mBgBounds.width() - mTextBounds.width()) / 2,
                    mBgBounds.height() - (mBgBounds.height() / 2) - mTextBounds.exactCenterY(),
                    mTextPaint);
            c.restoreToCount(restoreCount);
        }
    }

    public boolean isVisible() {
        return (mAlpha > 0f) && (mSectionName != null);
    }
}
