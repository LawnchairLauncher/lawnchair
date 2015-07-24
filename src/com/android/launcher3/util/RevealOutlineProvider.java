package com.android.launcher3.util;

import android.annotation.TargetApi;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class RevealOutlineProvider extends ViewOutlineProvider {

    private int mCenterX;
    private int mCenterY;
    private float mRadius0;
    private float mRadius1;
    private int mCurrentRadius;

    private final Rect mOval;

    /**
     * @param x reveal center x
     * @param y reveal center y
     * @param r0 initial radius
     * @param r1 final radius
     */
    public RevealOutlineProvider(int x, int y, float r0, float r1) {
        mCenterX = x;
        mCenterY = y;
        mRadius0 = r0;
        mRadius1 = r1;

        mOval = new Rect();
    }

    public void setProgress(float progress) {
        mCurrentRadius = (int) ((1 - progress) * mRadius0 + progress * mRadius1);

        mOval.left = mCenterX - mCurrentRadius;
        mOval.top = mCenterY - mCurrentRadius;
        mOval.right = mCenterX + mCurrentRadius;
        mOval.bottom = mCenterY + mCurrentRadius;
    }

    @Override
    public void getOutline(View v, Outline outline) {
        outline.setRoundRect(mOval, mCurrentRadius);
    }
}
