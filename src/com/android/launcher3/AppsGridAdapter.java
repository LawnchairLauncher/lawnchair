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
import android.widget.TextView;

import com.android.launcher3.util.Thunk;

import java.util.List;


/**
 * The grid view adapter of all the apps.
 */
class AppsGridAdapter extends RecyclerView.Adapter<AppsGridAdapter.ViewHolder> {

    public static final String TAG = "AppsGridAdapter";

    private static final int SECTION_BREAK_VIEW_TYPE = 0;
    private static final int ICON_VIEW_TYPE = 1;
    private static final int EMPTY_VIEW_TYPE = 2;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mContent;
        public boolean mIsSectionRow;
        public boolean mIsEmptyRow;

        public ViewHolder(View v, boolean isSectionRow, boolean isEmptyRow) {
            super(v);
            mContent = v;
            mIsSectionRow = isSectionRow;
            mIsEmptyRow = isEmptyRow;
        }
    }

    /**
     * Helper class to size the grid items.
     */
    public class GridSpanSizer extends GridLayoutManager.SpanSizeLookup {
        @Override
        public int getSpanSize(int position) {
            if (mApps.hasNoFilteredResults()) {
                // Empty view spans full width
                return mAppsPerRow;
            }

            if (mApps.getAdapterItems().get(position).isSectionHeader) {
                // Section break spans full width
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
            List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
            if (items.isEmpty()) {
                return;
            }

            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                ViewHolder holder = (ViewHolder) parent.getChildViewHolder(child);
                if (holder != null) {
                    GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams)
                            child.getLayoutParams();
                    if (!holder.mIsSectionRow && !holder.mIsEmptyRow && !lp.isItemRemoved()) {
                        if (items.get(holder.getPosition() - 1).isSectionHeader) {
                            // Draw at the parent
                            AlphabeticalAppsList.AdapterItem item =
                                    items.get(holder.getPosition());
                            String section = item.sectionName;
                            mSectionTextPaint.getTextBounds(section, 0, section.length(),
                                    mTmpBounds);
                            if (mIsRtl) {
                                int left = parent.getWidth() - mPaddingStart - mStartMargin;
                                c.drawText(section, left + (mStartMargin - mTmpBounds.width()) / 2,
                                        child.getTop() + (2 * child.getPaddingTop()) +
                                                mTmpBounds.height(), mSectionTextPaint);
                            } else {
                                int left = mPaddingStart;
                                c.drawText(section, left + (mStartMargin - mTmpBounds.width()) / 2,
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
    @Thunk AlphabeticalAppsList mApps;
    private GridLayoutManager mGridLayoutMgr;
    private GridSpanSizer mGridSizer;
    private GridItemDecoration mItemDecoration;
    private View.OnTouchListener mTouchListener;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;
    @Thunk int mAppsPerRow;
    @Thunk boolean mIsRtl;
    private String mEmptySearchText;

    // Section drawing
    @Thunk int mPaddingStart;
    @Thunk int mStartMargin;
    @Thunk Paint mSectionTextPaint;
    @Thunk Rect mTmpBounds = new Rect();


    public AppsGridAdapter(Context context, AlphabeticalAppsList apps, int appsPerRow,
            View.OnTouchListener touchListener, View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener) {
        Resources res = context.getResources();
        mApps = apps;
        mAppsPerRow = appsPerRow;
        mGridSizer = new GridSpanSizer();
        mGridLayoutMgr = new GridLayoutManager(context, appsPerRow, GridLayoutManager.VERTICAL,
                false);
        mGridLayoutMgr.setSpanSizeLookup(mGridSizer);
        mItemDecoration = new GridItemDecoration();
        mLayoutInflater = LayoutInflater.from(context);
        mTouchListener = touchListener;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mStartMargin = res.getDimensionPixelSize(R.dimen.apps_grid_view_start_margin);
        mPaddingStart = res.getDimensionPixelSize(R.dimen.apps_container_inset);
        mSectionTextPaint = new Paint();
        mSectionTextPaint.setTextSize(res.getDimensionPixelSize(
                R.dimen.apps_view_section_text_size));
        mSectionTextPaint.setColor(res.getColor(R.color.apps_view_section_text_color));
        mSectionTextPaint.setAntiAlias(true);
    }

    /**
     * Sets the number of apps per row.
     */
    public void setNumAppsPerRow(int appsPerRow) {
        mAppsPerRow = appsPerRow;
        mGridLayoutMgr.setSpanCount(appsPerRow);
    }

    /**
     * Sets whether we are in RTL mode.
     */
    public void setRtl(boolean rtl) {
        mIsRtl = rtl;
    }

    /**
     * Sets the text to show when there are no apps.
     */
    public void setEmptySearchText(String query) {
        mEmptySearchText = query;
    }

    /**
     * Returns the grid layout manager.
     */
    public GridLayoutManager getLayoutManager(Context context) {
        return mGridLayoutMgr;
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
            case EMPTY_VIEW_TYPE:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.apps_empty_view, parent,
                        false), false /* isSectionRow */, true /* isEmptyRow */);
            case SECTION_BREAK_VIEW_TYPE:
                return new ViewHolder(new View(parent.getContext()), true /* isSectionRow */,
                        false /* isEmptyRow */);
            case ICON_VIEW_TYPE:
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.apps_grid_row_icon_view, parent, false);
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setFocusable(true);
                return new ViewHolder(icon, false /* isSectionRow */, false /* isEmptyRow */);
            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case ICON_VIEW_TYPE:
                AppInfo info = mApps.getAdapterItems().get(position).appInfo;
                BubbleTextView icon = (BubbleTextView) holder.mContent;
                icon.applyFromApplicationInfo(info);
                break;
            case EMPTY_VIEW_TYPE:
                TextView emptyViewText = (TextView) holder.mContent.findViewById(R.id.empty_text);
                emptyViewText.setText(mEmptySearchText);
                break;
        }
    }

    @Override
    public int getItemCount() {
        if (mApps.hasNoFilteredResults()) {
            // For the empty view
            return 1;
        }
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (mApps.hasNoFilteredResults()) {
            return EMPTY_VIEW_TYPE;
        } else if (mApps.getAdapterItems().get(position).isSectionHeader) {
            return SECTION_BREAK_VIEW_TYPE;
        }
        return ICON_VIEW_TYPE;
    }
}
