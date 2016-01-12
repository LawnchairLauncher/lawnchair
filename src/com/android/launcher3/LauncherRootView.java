package com.android.launcher3;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class LauncherRootView extends InsettableFrameLayout {

    private final Paint mOpaquePaint;
    private boolean mDrawRightInsetBar;
    private int mRightInsetBarWidth;

    private View mAlignedView;

    public LauncherRootView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mOpaquePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mOpaquePaint.setColor(Color.BLACK);
        mOpaquePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onFinishInflate() {
        if (getChildCount() > 0) {
            // LauncherRootView contains only one child, which should be aligned
            // based on the horizontal insets.
            mAlignedView = getChildAt(0);
        }
        super.onFinishInflate();
    }

    @TargetApi(23)
    @Override
    protected boolean fitSystemWindows(Rect insets) {
        mDrawRightInsetBar = insets.right > 0 &&
                (!Utilities.ATLEAST_MARSHMALLOW ||
                getContext().getSystemService(ActivityManager.class).isLowRamDevice());
        mRightInsetBarWidth = insets.right;
        setInsets(mDrawRightInsetBar ? new Rect(0, insets.top, 0, insets.bottom) : insets);

        if (mAlignedView != null && mDrawRightInsetBar) {
            // Apply margins on aligned view to handle left/right insets.
            MarginLayoutParams lp = (MarginLayoutParams) mAlignedView.getLayoutParams();
            if (lp.leftMargin != insets.left || lp.rightMargin != insets.right) {
                lp.leftMargin = insets.left;
                lp.rightMargin = insets.right;
                mAlignedView.setLayoutParams(lp);
            }
        }

        return true; // I'll take it from here
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // If the right inset is opaque, draw a black rectangle to ensure that is stays opaque.
        if (mDrawRightInsetBar) {
            int width = getWidth();
            canvas.drawRect(width - mRightInsetBarWidth, 0, width, getHeight(), mOpaquePaint);
        }
    }
}