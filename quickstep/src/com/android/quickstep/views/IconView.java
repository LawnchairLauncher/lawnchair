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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.RecentsOrientedState;

/**
 * A view which draws a drawable stretched to fit its size. Unlike ImageView, it avoids relayout
 * when the drawable changes.
 */
public class IconView extends View implements TaskViewIcon {
    private static final int NUM_ALPHA_CHANNELS = 2;
    private static final int INDEX_CONTENT_ALPHA = 0;
    private static final int INDEX_MODAL_ALPHA = 1;

    private final MultiValueAlpha mMultiValueAlpha;

    @Nullable
    private Drawable mDrawable;
    private int mDrawableWidth, mDrawableHeight;

    public IconView(Context context) {
        this(context, null);
    }

    public IconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public IconView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mMultiValueAlpha = new MultiValueAlpha(this, NUM_ALPHA_CHANNELS);
        mMultiValueAlpha.setUpdateVisibility(/* updateVisibility= */ true);
    }

    /**
     * Sets a {@link Drawable} to be displayed.
     */
    @Override
    public void setDrawable(@Nullable Drawable d) {
        if (mDrawable != null) {
            mDrawable.setCallback(null);
        }
        mDrawable = d;
        if (mDrawable != null) {
            mDrawable.setCallback(this);
            setDrawableSizeInternal(getWidth(), getHeight());
        }
        invalidate();
    }

    /**
     * Sets the size of the icon drawable.
     */
    @Override
    public void setDrawableSize(int iconWidth, int iconHeight) {
        mDrawableWidth = iconWidth;
        mDrawableHeight = iconHeight;
        if (mDrawable != null) {
            setDrawableSizeInternal(getWidth(), getHeight());
        }
    }

    private void setDrawableSizeInternal(int selfWidth, int selfHeight) {
        Rect selfRect = new Rect(0, 0, selfWidth, selfHeight);
        Rect drawableRect = new Rect();
        Gravity.apply(Gravity.CENTER, mDrawableWidth, mDrawableHeight, selfRect, drawableRect);
        mDrawable.setBounds(drawableRect);
    }

    @Override
    @Nullable
    public Drawable getDrawable() {
        return mDrawable;
    }

    @Override
    public int getDrawableWidth() {
        return mDrawableWidth;
    }

    @Override
    public int getDrawableHeight() {
        return mDrawableHeight;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mDrawable != null) {
            setDrawableSizeInternal(w, h);
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == mDrawable;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        final Drawable drawable = mDrawable;
        if (drawable != null && drawable.isStateful()
                && drawable.setState(getDrawableState())) {
            invalidateDrawable(drawable);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDrawable != null) {
            mDrawable.draw(canvas);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void setContentAlpha(float alpha) {
        mMultiValueAlpha.get(INDEX_CONTENT_ALPHA).setValue(alpha);
    }

    @Override
    public void setModalAlpha(float alpha) {
        mMultiValueAlpha.get(INDEX_MODAL_ALPHA).setValue(alpha);
    }

    /**
     * Set the tint color of the icon, useful for scrimming or dimming.
     *
     * @param color to blend in.
     * @param amount [0,1] 0 no tint, 1 full tint
     */
    @Override
    public void setIconColorTint(int color, float amount) {
        if (mDrawable != null) {
            mDrawable.setColorFilter(Utilities.makeColorTintingColorFilter(color, amount));
        }
    }

    @Override
    public void setIconOrientation(RecentsOrientedState orientationState, boolean isGridTask) {
        RecentsPagedOrientationHandler orientationHandler =
                orientationState.getOrientationHandler();
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        DeviceProfile deviceProfile =
                ActivityContext.lookupContext(getContext()).getDeviceProfile();

        FrameLayout.LayoutParams iconParams = (FrameLayout.LayoutParams) getLayoutParams();

        int thumbnailTopMargin = deviceProfile.overviewTaskThumbnailTopMarginPx;
        int taskIconHeight = deviceProfile.overviewTaskIconSizePx;
        int taskMargin = deviceProfile.overviewTaskMarginPx;

        orientationHandler.setTaskIconParams(iconParams, taskMargin, taskIconHeight,
                thumbnailTopMargin, isRtl);
        iconParams.width = iconParams.height = taskIconHeight;
        setLayoutParams(iconParams);

        setRotation(orientationHandler.getDegreesRotated());
        int iconDrawableSize = isGridTask ? deviceProfile.overviewTaskIconDrawableSizeGridPx
                : deviceProfile.overviewTaskIconDrawableSizePx;
        setDrawableSize(iconDrawableSize, iconDrawableSize);
    }

    @Override
    public View asView() {
        return this;
    }
}
