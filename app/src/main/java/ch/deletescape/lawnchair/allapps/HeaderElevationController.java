package ch.deletescape.lawnchair.allapps;

import android.content.res.Resources;
import android.graphics.Outline;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewOutlineProvider;

import ch.deletescape.lawnchair.BaseRecyclerView;
import ch.deletescape.lawnchair.R;

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
        mCurrentY = ((BaseRecyclerView) recyclerView).getCurrentScrollY();
        onScroll(mCurrentY);
    }

    abstract void onScroll(int scrollY);

    public static class ControllerVL extends HeaderElevationController {

        private final View mHeader;
        private final float mMaxElevation;
        private final float mScrollToElevation;

        public ControllerVL(View header) {
            mHeader = header;
            Resources res = mHeader.getContext().getResources();
            mMaxElevation = res.getDimension(R.dimen.all_apps_header_max_elevation);
            mScrollToElevation = res.getDimension(R.dimen.all_apps_header_scroll_to_elevation);

            // We need to provide a custom outline so the shadow only appears on the bottom edge.
            // The top, left and right edges are all extended out, and the shadow is clipped
            // by the parent.
            final ViewOutlineProvider vop = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    final View parent = (View) mHeader.getParent();

                    final int left = parent.getLeft(); // Use the parent to account for offsets
                    final int top = view.getTop();
                    final int right = left + view.getWidth();
                    final int bottom = view.getBottom();

                    outline.setRect(
                            left - (int) mMaxElevation,
                            top - (int) mMaxElevation,
                            right + (int) mMaxElevation,
                            bottom);
                }
            };
            mHeader.setOutlineProvider(vop);
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
