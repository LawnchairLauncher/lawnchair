/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.views;

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;

/**
 * Extension of {@link BubbleTextView} which draws two shadows on the text (ambient and key shadows}
 */
public class DoubleShadowBubbleTextView extends BubbleTextView {

    public final ShadowInfo mShadowInfo;

    public DoubleShadowBubbleTextView(Context context) {
        this(context, null);
    }

    public DoubleShadowBubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DoubleShadowBubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mShadowInfo = ShadowInfo.Companion.fromContext(context, attrs, defStyle);
        setShadowLayer(
                mShadowInfo.getAmbientShadowBlur(),
                0,
                0,
                mShadowInfo.getAmbientShadowColor()
        );
    }

    @Override
    public void setTextWithStartIcon(CharSequence text, @DrawableRes int drawableId) {
        Drawable drawable = getContext().getDrawable(drawableId);
        if (drawable == null) {
            setText(text);
            Log.w(TAG, "setTextWithStartIcon: start icon Drawable not found from resources"
                    + ", will just set text instead.");
            return;
        }
        drawable.setTint(getCurrentTextColor());
        int textSize = Math.round(getTextSize());
        ImageSpan imageSpan;
        if (!skipDoubleShadow() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            drawable = getDoubleShadowDrawable(drawable, textSize);
        }
        drawable.setBounds(0, 0, textSize, textSize);
        imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_CENTER);
        // First space will be replaced with Drawable, second space is for space before text.
        SpannableString spannable = new SpannableString("  " + text);
        spannable.setSpan(imageSpan, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        setText(spannable);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private DoubleShadowIconDrawable getDoubleShadowDrawable(
            @NonNull Drawable drawable, int textSize
    ) {
        // add some padding via inset to avoid shadow clipping
        int iconInsetSize = getContext().getResources()
                .getDimensionPixelSize(R.dimen.app_title_icon_shadow_inset);
        return new DoubleShadowIconDrawable(
                mShadowInfo,
                drawable,
                textSize,
                iconInsetSize
        );
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (shouldDrawAppContrastTile() && !TextUtils.isEmpty(getText())) {
            drawAppContrastTile(canvas);
        }
        // If text is transparent or shadow alpha is 0, don't draw any shadow
        if (skipDoubleShadow()) {
            super.onDraw(canvas);
            return;
        }
        int alpha = Color.alpha(getCurrentTextColor());

        // We enhance the shadow by drawing the shadow twice
        getPaint().setShadowLayer(mShadowInfo.getAmbientShadowBlur(), 0, 0,
                getTextShadowColor(mShadowInfo.getAmbientShadowColor(), alpha));

        drawWithoutDot(canvas);
        canvas.save();
        canvas.clipRect(getScrollX(), getScrollY() + getExtendedPaddingTop(),
                getScrollX() + getWidth(),
                getScrollY() + getHeight());

        getPaint().setShadowLayer(
                mShadowInfo.getKeyShadowBlur(),
                mShadowInfo.getKeyShadowOffsetX(),
                mShadowInfo.getKeyShadowOffsetY(),
                getTextShadowColor(mShadowInfo.getKeyShadowColor(), alpha));
        drawWithoutDot(canvas);
        canvas.restore();

        drawDotIfNecessary(canvas);
        drawRunningAppIndicatorIfNecessary(canvas);
    }

    private boolean skipDoubleShadow() {
        int textAlpha = Color.alpha(getCurrentTextColor());
        int keyShadowAlpha = Color.alpha(mShadowInfo.getKeyShadowColor());
        int ambientShadowAlpha = Color.alpha(mShadowInfo.getAmbientShadowColor());
        if (textAlpha == 0 || (keyShadowAlpha == 0 && ambientShadowAlpha == 0)) {
            getPaint().clearShadowLayer();
            return true;
        } else if (ambientShadowAlpha > 0 && keyShadowAlpha == 0) {
            getPaint().setShadowLayer(mShadowInfo.getAmbientShadowBlur(), 0, 0,
                    getTextShadowColor(mShadowInfo.getAmbientShadowColor(), textAlpha));
            return true;
        } else if (keyShadowAlpha > 0 && ambientShadowAlpha == 0) {
            getPaint().setShadowLayer(
                    mShadowInfo.getKeyShadowBlur(),
                    mShadowInfo.getKeyShadowOffsetX(),
                    mShadowInfo.getKeyShadowOffsetY(),
                    getTextShadowColor(mShadowInfo.getKeyShadowColor(), textAlpha));
            return true;
        } else {
            return false;
        }
    }


    // Multiplies the alpha of shadowColor by textAlpha.
    private static int getTextShadowColor(int shadowColor, int textAlpha) {
        return setColorAlphaBound(shadowColor,
                Math.round(Color.alpha(shadowColor) * textAlpha / 255f));
    }

    public ShadowInfo getShadowInfo() {
        return mShadowInfo;
    }
}
