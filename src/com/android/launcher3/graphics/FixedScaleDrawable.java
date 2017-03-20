package com.android.launcher3.graphics;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.DrawableWrapper;
import android.os.Build;
import android.util.AttributeSet;

import org.xmlpull.v1.XmlPullParser;

/**
 * Extension of {@link DrawableWrapper} which scales the child drawables by a fixed amount.
 */
@TargetApi(Build.VERSION_CODES.N)
public class FixedScaleDrawable extends DrawableWrapper {

    // TODO b/33553066 use the constant defined in MaskableIconDrawable
    private static final float LEGACY_ICON_SCALE = .7f * .6667f;
    private float mScale;

    public FixedScaleDrawable() {
        super(new ColorDrawable());
        mScale = LEGACY_ICON_SCALE;
    }

    @Override
    public void draw(Canvas canvas) {
        int saveCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.scale(mScale, mScale,
                getBounds().exactCenterX(), getBounds().exactCenterY());
        super.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs) { }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme) { }

    public void setScale(float scale) {
        mScale = scale * LEGACY_ICON_SCALE;
    }
}
