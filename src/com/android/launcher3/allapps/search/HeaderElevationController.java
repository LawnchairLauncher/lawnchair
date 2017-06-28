package com.android.launcher3.allapps.search;

import android.content.res.Resources;
import android.graphics.Outline;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.R;

/**
 * Helper class for controlling the header elevation in response to RecyclerView scroll.
 */
public class HeaderElevationController extends RecyclerView.OnScrollListener {

    private final View mHeader;
    private final View mHeaderChild;
    private final float mMaxElevation;
    private final float mScrollToElevation;

    private int mCurrentY = 0;

    public HeaderElevationController(View header) {
        mHeader = header;
        final Resources res = mHeader.getContext().getResources();
        mMaxElevation = res.getDimension(R.dimen.all_apps_header_max_elevation);
        mScrollToElevation = res.getDimension(R.dimen.all_apps_header_scroll_to_elevation);

        // We need to provide a custom outline so the shadow only appears on the bottom edge.
        // The top, left and right edges are all extended out to match parent's edge, so that
        // the shadow is clipped by the parent.
        final ViewOutlineProvider vop = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // Set the left and top to be at the parents edge. Since the coordinates are
                // relative to this view,
                //    (x = -view.getLeft()) for this view => (x = 0) for parent
                final int left = -view.getLeft();
                final int top = -view.getTop();

                // Since the view is centered align, the spacing on left and right are same.
                // Add same spacing on the right to reach parent's edge.
                final int right = view.getWidth() - left;
                final int bottom = view.getHeight();
                final int offset = (int) mMaxElevation;
                outline.setRect(left - offset, top - offset, right + offset, bottom);
            }
        };
        mHeader.setOutlineProvider(vop);
        mHeaderChild = ((ViewGroup) mHeader).getChildAt(0);
    }

    public void reset() {
        mCurrentY = 0;
        onScroll(mCurrentY);
    }

    @Override
    public final void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        mCurrentY = ((BaseRecyclerView) recyclerView).getCurrentScrollY();
        onScroll(mCurrentY);
    }

    private void onScroll(int scrollY) {
        float elevationPct = Math.min(scrollY, mScrollToElevation) / mScrollToElevation;
        float newElevation = mMaxElevation * elevationPct;
        if (Float.compare(mHeader.getElevation(), newElevation) != 0) {
            mHeader.setElevation(newElevation);

            // To simulate a scrolling effect for the header, we translate the header down, and
            // its content up by the same amount, so that it gets clipped by the parent, making it
            // look like the content was scrolled out of the view.
            int shift = Math.min(mHeader.getHeight(), scrollY);
            mHeader.setTranslationY(-shift);
            mHeaderChild.setTranslationY(shift);
        }
    }

}
