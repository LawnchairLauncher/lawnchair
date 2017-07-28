package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TextView;

import ch.deletescape.lawnchair.R;


public class DoubleShadowTextView extends TextView {
    private final float ambientShadowBlur;
    private final int ambientShadowColor;
    private final float keyShadowBlur;
    private final int keyShadowColor;
    private final float keyShadowOffset;

    public DoubleShadowTextView(Context context) {
        this(context, null);
    }

    public DoubleShadowTextView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public DoubleShadowTextView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        TypedArray obtainStyledAttributes = context.obtainStyledAttributes(attributeSet, R.styleable.DoubleShadowTextView, i, 0);
        ambientShadowBlur = obtainStyledAttributes.getDimension(R.styleable.DoubleShadowTextView_ambientShadowBlur2, 0.0f);
        keyShadowBlur = obtainStyledAttributes.getDimension(R.styleable.DoubleShadowTextView_keyShadowBlur2, 0.0f);
        keyShadowOffset = obtainStyledAttributes.getDimension(R.styleable.DoubleShadowTextView_keyShadowOffset2, 0.0f);
        ambientShadowColor = obtainStyledAttributes.getColor(R.styleable.DoubleShadowTextView_ambientShadowColor2, 0);
        keyShadowColor = obtainStyledAttributes.getColor(R.styleable.DoubleShadowTextView_keyShadowColor2, 0);
        obtainStyledAttributes.recycle();
        setShadowLayer(Math.max(keyShadowBlur + keyShadowOffset, ambientShadowBlur), 0.0f, 0.0f, keyShadowColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getPaint().setShadowLayer(keyShadowBlur, 0.0f, keyShadowOffset, keyShadowColor);
        super.onDraw(canvas);
        getPaint().setShadowLayer(ambientShadowBlur, 0.0f, 0.0f, ambientShadowColor);
        super.onDraw(canvas);
    }
}
