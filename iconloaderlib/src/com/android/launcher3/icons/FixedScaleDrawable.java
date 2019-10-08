package com.android.launcher3.icons;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.xmlpull.v1.XmlPullParser;

/**
 * Extension of {@link DrawableWrapper} which scales the child drawables by a fixed amount.
 */
public class FixedScaleDrawable extends DrawableWrapper {

    // TODO b/33553066 use the constant defined in MaskableIconDrawable
    public static final float LEGACY_ICON_SCALE = .7f * .6667f;
    private float mScaleX, mScaleY;

    private ScaleState mState = new ScaleState();

    public Throwable mCreated;

    public FixedScaleDrawable() {
        super(new ColorDrawable());
        mScaleX = LEGACY_ICON_SCALE;
        mScaleY = LEGACY_ICON_SCALE;
        mCreated = new Throwable();
        if (getDrawable() != null) {
            mState.mDrawableState = getDrawable().getConstantState();
        }
    }

    @Override
    public void setDrawable(@Nullable Drawable dr) {
        super.setDrawable(dr);
        if (mState != null) {
            if (dr != null) {
                mState.mDrawableState = dr.getConstantState();
            } else {
                mState.mDrawableState = null;
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        int saveCount = canvas.save();
        canvas.scale(mScaleX, mScaleY,
                getBounds().exactCenterX(), getBounds().exactCenterY());
        super.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs) { }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme) { }

    public void setScale(float scale) {
        mState.mScale = scale;
        float h = getIntrinsicHeight();
        float w = getIntrinsicWidth();
        mScaleX = scale * LEGACY_ICON_SCALE;
        mScaleY = scale * LEGACY_ICON_SCALE;
        if (h > w && w > 0) {
            mScaleX *= w / h;
        } else if (w > h && h > 0) {
            mScaleY *= h / w;
        }
    }

    @Nullable
    @Override
    public ConstantState getConstantState() {
        if (mState.mDrawableState != null) {
            return mState;
        } else {
            return null;
        }
    }

    private static class ScaleState extends ConstantState {

        private Drawable.ConstantState mDrawableState;
        private float mScale = 1f;

        @NonNull
        @Override
        public Drawable newDrawable() {
            FixedScaleDrawable drawable = new FixedScaleDrawable();
            if (mScale != 1f) {
                drawable.setScale(mScale);
            }
            if (mDrawableState != null) {
                drawable.setDrawable(mDrawableState.newDrawable());
            }
            return drawable;
        }

        @Override
        public int getChangingConfigurations() {
            if (mDrawableState != null) {
                return mDrawableState.getChangingConfigurations();
            } else {
                return 0;
            }
        }
    }
}
