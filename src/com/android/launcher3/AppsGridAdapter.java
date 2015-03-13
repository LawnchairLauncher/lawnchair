package com.android.launcher3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.launcher3.compat.AlphabeticIndexCompat;


/**
 * The grid view adapter of all the apps.
 */
class AppsGridAdapter extends RecyclerView.Adapter<AppsGridAdapter.ViewHolder> {

    public static final String TAG = "AppsGridAdapter";

    private static final int SECTION_BREAK_VIEW_TYPE = 0;
    private static final int ICON_VIEW_TYPE = 1;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mContent;
        public boolean mIsSectionRow;

        public ViewHolder(View v, boolean isSectionRow) {
            super(v);
            mContent = v;
            mIsSectionRow = isSectionRow;
        }
    }

    /**
     * Helper class to size the grid items.
     */
    public class GridSpanSizer extends GridLayoutManager.SpanSizeLookup {
        @Override
        public int getSpanSize(int position) {
            AppInfo info = mApps.getApps().get(position);
            if (info == AlphabeticalAppsList.SECTION_BREAK_INFO) {
                return mAppsPerRow;
            } else {
                return 1;
            }
        }
    }

    /**
     * Helper class to draw the section headers
     */
    public class GridItemDecoration extends RecyclerView.ItemDecoration {

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            AlphabeticIndexCompat indexer = mApps.getIndexer();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                ViewHolder holder = (ViewHolder) parent.getChildViewHolder(child);
                if (holder != null) {
                    GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams)
                            child.getLayoutParams();
                    if (!holder.mIsSectionRow && !lp.isItemRemoved()) {
                        if (mApps.getApps().get(holder.getPosition() - 1) ==
                                AlphabeticalAppsList.SECTION_BREAK_INFO) {
                            // Draw at the parent
                            AppInfo info = mApps.getApps().get(holder.getPosition());
                            String section = mApps.getSectionNameForApp(info);
                            mSectionTextPaint.getTextBounds(section, 0, section.length(),
                                    mTmpBounds);
                            if (mIsRtl) {
                                c.drawText(section, parent.getWidth() - mStartMargin +
                                                (mStartMargin - mTmpBounds.width()) / 2,
                                        child.getTop() + (2 * child.getPaddingTop()) +
                                                mTmpBounds.height(), mSectionTextPaint);
                            } else {
                                c.drawText(section, (mStartMargin - mTmpBounds.width()) / 2,
                                    child.getTop() + (2 * child.getPaddingTop()) +
                                            mTmpBounds.height(), mSectionTextPaint);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            // Do nothing
        }
    }

    private LayoutInflater mLayoutInflater;
    private AlphabeticalAppsList mApps;
    private GridSpanSizer mGridSizer;
    private GridItemDecoration mItemDecoration;
    private View.OnTouchListener mTouchListener;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;
    private int mAppsPerRow;
    private boolean mIsRtl;

    // Section drawing
    private int mStartMargin;
    private Paint mSectionTextPaint;
    private Rect mTmpBounds = new Rect();


    public AppsGridAdapter(Context context, AlphabeticalAppsList apps, int appsPerRow,
            View.OnTouchListener touchListener, View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener) {
        Resources res = context.getResources();
        mApps = apps;
        mAppsPerRow = appsPerRow;
        mGridSizer = new GridSpanSizer();
        mItemDecoration = new GridItemDecoration();
        mLayoutInflater = LayoutInflater.from(context);
        mTouchListener = touchListener;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mStartMargin = res.getDimensionPixelSize(R.dimen.apps_grid_view_start_margin);
        mSectionTextPaint = new Paint();
        mSectionTextPaint.setTextSize(res.getDimensionPixelSize(
                R.dimen.apps_view_section_text_size));
        mSectionTextPaint.setColor(res.getColor(R.color.apps_view_section_text_color));
        mSectionTextPaint.setAntiAlias(true);
    }

    /**
     * Sets whether we are in RTL mode.
     */
    public void setRtl(boolean rtl) {
        mIsRtl = rtl;
    }

    /**
     * Returns the grid layout manager.
     */
    public GridLayoutManager getLayoutManager(Context context) {
        GridLayoutManager layoutMgr = new GridLayoutManager(context, mAppsPerRow,
                GridLayoutManager.VERTICAL, false);
        layoutMgr.setSpanSizeLookup(mGridSizer);
        return layoutMgr;
    }

    /**
     * Returns the item decoration for the recycler view.
     */
    public RecyclerView.ItemDecoration getItemDecoration() {
        return mItemDecoration;
    }

    /**
     * Returns the left padding for the recycler view.
     */
    public int getContentMarginStart() {
        return mStartMargin;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case SECTION_BREAK_VIEW_TYPE:
                return new ViewHolder(new View(parent.getContext()), true);
            case ICON_VIEW_TYPE:
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.apps_grid_row_icon_view, parent, false);
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setFocusable(true);
                return new ViewHolder(icon, false);
            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AppInfo info = mApps.getApps().get(position);
        if (info != AlphabeticalAppsList.SECTION_BREAK_INFO) {
            BubbleTextView icon = (BubbleTextView) holder.mContent;
            icon.applyFromApplicationInfo(info);
        }
    }

    @Override
    public int getItemCount() {
        return mApps.getApps().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (mApps.getApps().get(position) == AlphabeticalAppsList.SECTION_BREAK_INFO) {
            return SECTION_BREAK_VIEW_TYPE;
        }
        return ICON_VIEW_TYPE;
    }
}
