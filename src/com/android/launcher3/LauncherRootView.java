package com.android.launcher3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

public class LauncherRootView extends InsettableFrameLayout {

    private final Paint mOpaquePaint;
    private boolean mDrawRightInsetBar;

    public LauncherRootView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mOpaquePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mOpaquePaint.setColor(Color.BLACK);
        mOpaquePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        setInsets(insets);
        mDrawRightInsetBar = mInsets.right > 0 && LauncherAppState
                .getInstance().getInvariantDeviceProfile().isRightInsetOpaque;

        return true; // I'll take it from here
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // If the right inset is opaque, draw a black rectangle to ensure that is stays opaque.
        if (mDrawRightInsetBar) {
            int width = getWidth();
            canvas.drawRect(width - mInsets.right, 0, width, getHeight(), mOpaquePaint);
        }
    }
}