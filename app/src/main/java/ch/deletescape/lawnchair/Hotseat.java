/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewDebug;
import android.widget.FrameLayout;

import ch.deletescape.lawnchair.blur.BlurDrawable;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.dynamicui.ExtractedColors;

public class Hotseat extends FrameLayout {

    private CellLayout mContent;

    private Launcher mLauncher;

    @ViewDebug.ExportedProperty(category = "launcher")
    private final boolean mHasVerticalHotseat;

    @ViewDebug.ExportedProperty(category = "launcher")
    private int mBackgroundColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private Drawable mBackground;
    private ValueAnimator mBackgroundColorAnimator;
    private final Rect mBoundsRect = new Rect();

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
        mHasVerticalHotseat = mLauncher.getDeviceProfile().isVerticalBarLayout();
        if (Utilities.getPrefs(context).getTransparentHotseat() || mHasVerticalHotseat) {
            setBackgroundColor(Color.TRANSPARENT);
        } else {
            mBackgroundColor = ColorUtils.setAlphaComponent(
                    Utilities.resolveAttributeData(context, R.attr.allAppsContainerColor), 0);
            mBackground = BlurWallpaperProvider.Companion.isEnabled(BlurWallpaperProvider.BLUR_ALLAPPS) ?
                    mLauncher.getBlurWallpaperProvider().createDrawable() : new ColorDrawable(mBackgroundColor);
            setBackground(mBackground);
        }
    }

    public CellLayout getLayout() {
        return mContent;
    }

    /**
     * Registers the specified listener on the cell layout of the hotseat.
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mContent.setOnLongClickListener(l);
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        int xOrder = mHasVerticalHotseat ? (mContent.getCountY() - y - 1) : x;
        int yOrder = mHasVerticalHotseat ? x * mContent.getCountY() : y * mContent.getCountX();
        return xOrder + yOrder;
    }

    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        int size = mHasVerticalHotseat ? mContent.getCountY() : mContent.getCountX();
        return mHasVerticalHotseat ? rank / size : rank % size;
    }

    int getCellYFromOrder(int rank) {
        int size = mHasVerticalHotseat ? mContent.getCountY() : mContent.getCountX();
        return mHasVerticalHotseat ? (mContent.getCountY() - ((rank % size) + 1)) : rank / size;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.layout);
        mContent.setIsHotseat(true);

        refresh();
        resetLayout();
    }

    public void refresh() {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int rows = Utilities.getNumberOfHotseatRows(mLauncher);
        if (grid.isVerticalBarLayout()) {
            mContent.setGridSize(rows, grid.inv.numHotseatIcons);
        } else {
            mContent.setGridSize(grid.inv.numHotseatIcons, rows);
        }
        mContent.requestLayout();
    }

    void resetLayout() {
        mContent.removeAllViewsInLayout();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We don't want any clicks to go through to the hotseat unless the workspace is in
        // the normal state or an accessible drag is in progress.
        return mLauncher.getWorkspace().workspaceInModalState() &&
                !mLauncher.getAccessibilityDelegate().isInAccessibleDrag();
    }

    public void updateColor(ExtractedColors extractedColors, boolean animate) {
        if (!(mBackground instanceof ColorDrawable)) return;
        if (!mHasVerticalHotseat) {
            int color = extractedColors.getHotseatColor(getContext());
            if (mBackgroundColorAnimator != null) {
                mBackgroundColorAnimator.cancel();
            }
            if (!animate) {
                setBackgroundColor(color);
            } else {
                mBackgroundColorAnimator = ValueAnimator.ofInt(mBackgroundColor, color);
                mBackgroundColorAnimator.setEvaluator(new ArgbEvaluator());
                mBackgroundColorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        ((ColorDrawable) mBackground).setColor((Integer) animation.getAnimatedValue());
                    }
                });
                mBackgroundColorAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mBackgroundColorAnimator = null;
                    }
                });
                mBackgroundColorAnimator.start();
            }
            mBackgroundColor = color;
        }
    }

    public void setBackgroundTransparent(boolean enable) {
        if (mBackground == null) return;
        if (enable) {
            mBackground.setAlpha(0);
        } else {
            mBackground.setAlpha(255);
        }
    }

    public int getBackgroundDrawableColor() {
        return mBackgroundColor;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mBoundsRect.set(0, 0, right - left, bottom - top);
        setClipBounds(mBoundsRect);
        if (mBackground instanceof BlurDrawable) {
            ((BlurDrawable) mBackground).setTranslation(top);
        }
    }

    public void setWallpaperTranslation(float translation) {
        if (mBackground instanceof BlurDrawable) {
            ((BlurDrawable) mBackground).setTranslation(translation);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mBackground instanceof BlurDrawable) {
            ((BlurDrawable) mBackground).startListening();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBackground instanceof BlurDrawable) {
            ((BlurDrawable) mBackground).stopListening();
        }
    }

    public void setOverscroll(float progress) {
        if (mBackground instanceof BlurDrawable) {
            ((BlurDrawable) mBackground).setOverscroll(progress);
        }
    }

    @Override
    public void setTranslationX(float translationX) {
        super.setTranslationX(translationX);
        LauncherAppState.getInstance().getLauncher().mHotseat.setOverscroll(translationX);
    }
}
