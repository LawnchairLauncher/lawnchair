package ch.deletescape.lawnchair.anim;

import android.graphics.Rect;

public class RoundedRectRevealOutlineProvider extends RevealOutlineAnimation {
    private final float mEndRadius;
    private final Rect mEndRect;
    private final int mRoundedCorners;
    private final float mStartRadius;
    private final Rect mStartRect;

    public RoundedRectRevealOutlineProvider(float f, float f2, Rect rect, Rect rect2) {
        this(f, f2, rect, rect2, 3);
    }

    public RoundedRectRevealOutlineProvider(float f, float f2, Rect rect, Rect rect2, int i) {
        this.mStartRadius = f;
        this.mEndRadius = f2;
        this.mStartRect = rect;
        this.mEndRect = rect2;
        this.mRoundedCorners = i;
    }

    public boolean shouldRemoveElevationDuringAnimation() {
        return false;
    }

    public void setProgress(float f) {
        mOutlineRadius = ((1.0f - f) * mStartRadius) + (mEndRadius * f);
        mOutline.left = (int) (((1.0f - f) * ((float) mStartRect.left)) + (((float) mEndRect.left) * f));
        mOutline.top = (int) (((1.0f - f) * ((float) mStartRect.top)) + (((float) mEndRect.top) * f));
        if ((mRoundedCorners & 1) == 0) {
            Rect rect = mOutline;
            rect.top = (int) (((float) rect.top) - mOutlineRadius);
        }
        mOutline.right = (int) (((1.0f - f) * ((float) mStartRect.right)) + (((float) mEndRect.right) * f));
        mOutline.bottom = (int) (((1.0f - f) * ((float) mStartRect.bottom)) + (((float) mEndRect.bottom) * f));
        if ((mRoundedCorners & 2) == 0) {
            Rect rect = mOutline;
            rect.bottom = (int) (((float) rect.bottom) + mOutlineRadius);
        }
    }
}