package com.android.launcher3.allapps;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import com.android.launcher3.R;

/**
 * Helper class for controlling the header elevation in response to RecyclerView scroll.
 */
public abstract class HeaderElevationController extends RecyclerView.OnScrollListener {

    private int mCurrentY = 0;

    public void reset() {
        mCurrentY = 0;
        onScroll(mCurrentY);
    }

    @Override
    public final void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        mCurrentY += dy;
        onScroll(mCurrentY);
    }

    public void updateBackgroundPadding(Rect bgPadding) { }

    abstract void onScroll(int scrollY);

    public static class ControllerV16 extends HeaderElevationController {

        private final View mShadow;
        private final float mScrollToElevation;

        public ControllerV16(View header) {
            Resources res = header.getContext().getResources();
            mScrollToElevation = res.getDimension(R.dimen.all_apps_header_scroll_to_elevation);

            mShadow = new View(header.getContext());
            mShadow.setBackground(new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x1E000000, 0x00000000}));
            mShadow.setAlpha(0);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    res.getDimensionPixelSize(R.dimen.all_apps_header_shadow_height));
            lp.topMargin = ((FrameLayout.LayoutParams) header.getLayoutParams()).height;

            ((ViewGroup) header.getParent()).addView(mShadow, lp);
        }

        @Override
        public void onScroll(int scrollY) {
            float elevationPct = (float) Math.min(scrollY, mScrollToElevation) /
                    mScrollToElevation;
            mShadow.setAlpha(elevationPct);
        }

        @Override
        public void updateBackgroundPadding(Rect bgPadding) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mShadow.getLayoutParams();
            lp.leftMargin = bgPadding.left;
            lp.rightMargin = bgPadding.right;
            mShadow.requestLayout();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static class ControllerVL extends HeaderElevationController {

        private final View mHeader;
        private final float mMaxElevation;
        private final float mScrollToElevation;

        public ControllerVL(View header) {
            mHeader = header;
            mHeader.setOutlineProvider(ViewOutlineProvider.BOUNDS);

            Resources res = header.getContext().getResources();
            mMaxElevation = res.getDimension(R.dimen.all_apps_header_max_elevation);
            mScrollToElevation = res.getDimension(R.dimen.all_apps_header_scroll_to_elevation);
        }

        @Override
        public void onScroll(int scrollY) {
            float elevationPct = Math.min(scrollY, mScrollToElevation) / mScrollToElevation;
            float newElevation = mMaxElevation * elevationPct;
            if (Float.compare(mHeader.getElevation(), newElevation) != 0) {
                mHeader.setElevation(newElevation);
            }
        }
    }
}
