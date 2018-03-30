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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;

/**
 * Extension of {@link BubbleTextView} which draws two shadows on the text (ambient and key shadows}
 */
public class DoubleShadowBubbleTextView extends BubbleTextView {

    private final ShadowInfo mShadowInfo;

    public DoubleShadowBubbleTextView(Context context) {
        this(context, null);
    }

    public DoubleShadowBubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DoubleShadowBubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mShadowInfo = new ShadowInfo(context, attrs, defStyle);
        setShadowLayer(mShadowInfo.ambientShadowBlur, 0, 0, mShadowInfo.ambientShadowColor);
    }

    @Override
    public void onDraw(Canvas canvas) {
        // If text is transparent or shadow alpha is 0, don't draw any shadow
        if (mShadowInfo.skipDoubleShadow(this)) {
            super.onDraw(canvas);
            return;
        }
        int alpha = Color.alpha(getCurrentTextColor());

        // We enhance the shadow by drawing the shadow twice
        getPaint().setShadowLayer(mShadowInfo.ambientShadowBlur, 0, 0,
                ColorUtils.setAlphaComponent(mShadowInfo.ambientShadowColor, alpha));

        drawWithoutBadge(canvas);
        canvas.save();
        canvas.clipRect(getScrollX(), getScrollY() + getExtendedPaddingTop(),
                getScrollX() + getWidth(),
                getScrollY() + getHeight());

        getPaint().setShadowLayer(mShadowInfo.keyShadowBlur, 0.0f, mShadowInfo.keyShadowOffset,
                ColorUtils.setAlphaComponent(mShadowInfo.keyShadowColor, alpha));
        drawWithoutBadge(canvas);
        canvas.restore();

        drawBadgeIfNecessary(canvas);
    }

    public static class ShadowInfo {
        public final float ambientShadowBlur;
        public final int ambientShadowColor;

        public final float keyShadowBlur;
        public final float keyShadowOffset;
        public final int keyShadowColor;

        public ShadowInfo(Context c, AttributeSet attrs, int defStyle) {

            TypedArray a = c.obtainStyledAttributes(
                    attrs, R.styleable.ShadowInfo, defStyle, 0);

            ambientShadowBlur = a.getDimension(R.styleable.ShadowInfo_ambientShadowBlur, 0);
            ambientShadowColor = a.getColor(R.styleable.ShadowInfo_ambientShadowColor, 0);

            keyShadowBlur = a.getDimension(R.styleable.ShadowInfo_keyShadowBlur, 0);
            keyShadowOffset = a.getDimension(R.styleable.ShadowInfo_keyShadowOffset, 0);
            keyShadowColor = a.getColor(R.styleable.ShadowInfo_keyShadowColor, 0);
            a.recycle();
        }

        public boolean skipDoubleShadow(TextView textView) {
            int textAlpha = Color.alpha(textView.getCurrentTextColor());
            int keyShadowAlpha = Color.alpha(keyShadowColor);
            int ambientShadowAlpha = Color.alpha(ambientShadowColor);
            if (textAlpha == 0 || (keyShadowAlpha == 0 && ambientShadowAlpha == 0)) {
                textView.getPaint().clearShadowLayer();
                return true;
            } else if (ambientShadowAlpha > 0) {
                textView.getPaint().setShadowLayer(ambientShadowBlur, 0, 0,
                        ColorUtils.setAlphaComponent(ambientShadowColor, textAlpha));
                return true;
            } else if (keyShadowAlpha > 0) {
                textView.getPaint().setShadowLayer(keyShadowBlur, 0.0f, keyShadowOffset,
                        ColorUtils.setAlphaComponent(keyShadowColor, textAlpha));
                return true;
            } else {
                return false;
            }
        }
    }
}
