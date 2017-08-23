package com.android.launcher3.pixel;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TextClock;

import com.android.launcher3.R;

public class DoubleShadowTextClock extends TextClock {
    private final float ambientShadowBlur;
    private final int ambientShadowColor;
    private final float keyShadowBlur;
    private final int keyShadowColor;
    private final float keyShadowOffset;

    public DoubleShadowTextClock(Context context) {
        this(context, null);
    }

    public DoubleShadowTextClock(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public DoubleShadowTextClock(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        TypedArray ta = context.obtainStyledAttributes(attributeSet, new int[] { R.attr.ambientShadowColor, R.attr.keyShadowColor, R.attr.ambientShadowBlur, R.attr.keyShadowBlur, R.attr.keyShadowOffset }, i, 0);
        ambientShadowColor = ta.getColor(0, 0);
        keyShadowColor = ta.getColor(1, 0);
        ambientShadowBlur = ta.getDimension(2, 0.0F);
        keyShadowBlur = ta.getDimension(3, 0.0F);
        keyShadowOffset = ta.getDimension(4, 0.0F);
        ta.recycle();
        setShadowLayer(Math.max(keyShadowBlur + keyShadowOffset, ambientShadowBlur), 0.0f, 0.0f, keyShadowColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getPaint().setShadowLayer(keyShadowBlur, 0.0f, keyShadowOffset, keyShadowColor);
        super.onDraw(canvas);
        getPaint().setShadowLayer(ambientShadowBlur, 0.0f, 0.0f, ambientShadowColor);
        super.onDraw(canvas);
    }

    public void setFormat(CharSequence charSequence) {
        setFormat24Hour(charSequence);
        setFormat12Hour(charSequence);
    }
}