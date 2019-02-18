package com.android.quickstep.views;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class LiveTileOverlay extends Drawable {

    private final Paint mPaint = new Paint();

    private Rect mBoundsRect = new Rect();
    private RectF mCurrentRect;
    private float mCornerRadius;

    private boolean mDrawEnabled = true;

    public LiveTileOverlay() {
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void update(RectF currentRect, float cornerRadius) {
        invalidateSelf();

        mCurrentRect = currentRect;
        mCornerRadius = cornerRadius;

        mCurrentRect.roundOut(mBoundsRect);
        setBounds(mBoundsRect);
        invalidateSelf();
    }

    public void setDrawEnabled(boolean drawEnabled) {
        if (mDrawEnabled != drawEnabled) {
            mDrawEnabled = drawEnabled;
            invalidateSelf();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mCurrentRect != null && mDrawEnabled) {
            canvas.drawRoundRect(mCurrentRect, mCornerRadius, mCornerRadius, mPaint);
        }
    }

    @Override
    public void setAlpha(int i) { }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
