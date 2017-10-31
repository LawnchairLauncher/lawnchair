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

package com.android.launcher3;

import static com.android.launcher3.FastBitmapDrawable.CLICK_FEEDBACK_DURATION;
import static com.android.launcher3.FastBitmapDrawable.CLICK_FEEDBACK_INTERPOLATOR;
import static com.android.launcher3.LauncherAnimUtils.ELEVATION;
import static com.android.launcher3.graphics.HolographicOutlineHelper.ADAPTIVE_ICON_SHADOW_BITMAP;

import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Property;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

public class ClickShadowView extends View {

    private static final int SHADOW_SIZE_FACTOR = 3;
    private static final int SHADOW_LOW_ALPHA = 30;
    private static final int SHADOW_HIGH_ALPHA = 60;

    private static float sAdaptiveIconScaleFactor = 1f;

    private final Paint mPaint;

    @ViewDebug.ExportedProperty(category = "launcher")
    private final float mShadowOffset;
    @ViewDebug.ExportedProperty(category = "launcher")
    private final float mShadowPadding;

    private Bitmap mBitmap;
    private ObjectAnimator mAnim;

    private Drawable mAdaptiveIcon;
    private ViewOutlineProvider mOutlineProvider;

    public ClickShadowView(Context context) {
        super(context);
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        mPaint.setColor(Color.BLACK);

        mShadowPadding = getResources().getDimension(R.dimen.blur_size_click_shadow);
        mShadowOffset = getResources().getDimension(R.dimen.click_shadow_high_shift);
    }

    public static void setAdaptiveIconScaleFactor(float factor) {
        sAdaptiveIconScaleFactor = factor;
    }

    /**
     * @return extra space required by the view to show the shadow.
     */
    public int getExtraSize() {
        return (int) (SHADOW_SIZE_FACTOR * mShadowPadding);
    }

    public void setPressedIcon(BubbleTextView icon, Bitmap background) {
        if (icon == null) {
            setBitmap(null);
            cancelAnim();
            return;
        }
        if (background == null) {
            if (mBitmap == ADAPTIVE_ICON_SHADOW_BITMAP) {
                // clear animation shadow
            }
            setBitmap(null);
            cancelAnim();
            icon.setOutlineProvider(null);
        } else if (setBitmap(background)) {
            if (mBitmap == ADAPTIVE_ICON_SHADOW_BITMAP) {
                setupAdaptiveShadow(icon);
                cancelAnim();
                startAnim(icon, ELEVATION,
                        getResources().getDimension(R.dimen.click_shadow_elevation));
            } else {
                alignWithIconView(icon);
                startAnim(this, ALPHA, 1);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void setupAdaptiveShadow(final BubbleTextView view) {
        if (mAdaptiveIcon == null) {
            mAdaptiveIcon = new AdaptiveIconDrawable(null, null);
            mOutlineProvider = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    mAdaptiveIcon.getOutline(outline);
                }
            };
        }

        int iconWidth = view.getRight() - view.getLeft();
        int iconHSpace = iconWidth - view.getCompoundPaddingRight() - view.getCompoundPaddingLeft();
        int drawableWidth = view.getIcon().getBounds().width();

        Rect bounds = new Rect();
        bounds.left = view.getCompoundPaddingLeft() + (iconHSpace - drawableWidth) / 2;
        bounds.right = bounds.left + drawableWidth;
        bounds.top = view.getPaddingTop();
        bounds.bottom = bounds.top + view.getIcon().getBounds().height();
        Utilities.scaleRectAboutCenter(bounds, sAdaptiveIconScaleFactor);

        mAdaptiveIcon.setBounds(bounds);
        view.setOutlineProvider(mOutlineProvider);
    }

    /**
     * Applies the new bitmap.
     * @return true if the view was invalidated.
     */
    private boolean setBitmap(Bitmap b) {
        if (b != mBitmap){
            mBitmap = b;
            invalidate();
            return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null) {
            mPaint.setAlpha(SHADOW_LOW_ALPHA);
            canvas.drawBitmap(mBitmap, 0, 0, mPaint);
            mPaint.setAlpha(SHADOW_HIGH_ALPHA);
            canvas.drawBitmap(mBitmap, 0, mShadowOffset, mPaint);
        }
    }

    private void cancelAnim() {
        if (mAnim != null) {
            mAnim.cancel();
            mAnim.setCurrentPlayTime(0);
            mAnim = null;
        }
    }

    private void startAnim(View target, Property<View, Float> property, float endValue) {
        cancelAnim();
        property.set(target, 0f);
        mAnim = ObjectAnimator.ofFloat(target, property, endValue);
        mAnim.setDuration(CLICK_FEEDBACK_DURATION)
                .setInterpolator(CLICK_FEEDBACK_INTERPOLATOR);
        mAnim.start();
    }

    /**
     * Aligns the shadow with {@param view}
     * Note: {@param view} must be a descendant of my parent.
     */
    private void alignWithIconView(BubbleTextView view) {
        int[] coords = new int[] {0, 0};
        Utilities.getDescendantCoordRelativeToAncestor(
                (ViewGroup) view.getParent(), (View) getParent(), coords, false);

        float leftShift = view.getLeft() + coords[0] - getLeft();
        float topShift = view.getTop() + coords[1] - getTop();
        int iconWidth = view.getRight() - view.getLeft();
        int iconHeight = view.getBottom() - view.getTop();
        int iconHSpace = iconWidth - view.getCompoundPaddingRight() - view.getCompoundPaddingLeft();
        float drawableWidth = view.getIcon().getBounds().width();

        // Set the bounds to clip against
        int clipLeft = (int) Math.max(0, coords[0] - leftShift - mShadowPadding);
        int clipTop = (int) Math.max(0, coords[1] - topShift - mShadowPadding) ;
        setClipBounds(new Rect(clipLeft, clipTop, clipLeft + iconWidth, clipTop + iconHeight));

        setTranslationX(leftShift
                + view.getCompoundPaddingLeft() * view.getScaleX()
                + (iconHSpace - drawableWidth) * view.getScaleX() / 2  /* drawable gap */
                + iconWidth * (1 - view.getScaleX()) / 2  /* gap due to scale */
                - mShadowPadding  /* extra shadow size */
                );
        setTranslationY(topShift
                + view.getPaddingTop() * view.getScaleY()  /* drawable gap */
                + view.getHeight() * (1 - view.getScaleY()) / 2  /* gap due to scale */
                - mShadowPadding  /* extra shadow size */
                );
    }
}
