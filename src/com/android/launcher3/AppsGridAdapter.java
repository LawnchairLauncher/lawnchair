package com.android.launcher3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.android.launcher3.util.Thunk;

import java.util.HashMap;
import java.util.List;


/**
 * The grid view adapter of all the apps.
 */
class AppsGridAdapter extends RecyclerView.Adapter<AppsGridAdapter.ViewHolder> {

    public static final String TAG = "AppsGridAdapter";
    private static final boolean DEBUG = false;

    private static final int SECTION_BREAK_VIEW_TYPE = 0;
    private static final int ICON_VIEW_TYPE = 1;
    private static final int EMPTY_VIEW_TYPE = 2;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mContent;
        public boolean mIsSectionHeader;
        public boolean mIsEmptyRow;

        public ViewHolder(View v, boolean isSectionHeader, boolean isEmptyRow) {
            super(v);
            mContent = v;
            mIsSectionHeader = isSectionHeader;
            mIsEmptyRow = isEmptyRow;
        }
    }

    /**
     * Helper class to size the grid items.
     */
    public class GridSpanSizer extends GridLayoutManager.SpanSizeLookup {

        public GridSpanSizer() {
            super();
            setSpanIndexCacheEnabled(true);
        }

        @Override
        public int getSpanSize(int position) {
            if (mApps.hasNoFilteredResults()) {
                // Empty view spans full width
                return mAppsPerRow;
            }

            if (mApps.getAdapterItems().get(position).isSectionHeader) {
                // Section break spans full width
                if (AppsContainerView.GRID_HIDE_SECTION_HEADERS) {
                    return 0;
                } else {
                    return mAppsPerRow;
                }
            } else {
                return 1;
            }
        }
    }

    /**
     * Helper class to draw the section headers
     */
    public class GridItemDecoration extends RecyclerView.ItemDecoration {

        private static final boolean FADE_OUT_SECTIONS = false;

        private HashMap<String, Point> mCachedSectionBounds = new HashMap<>();
        private Rect mTmpBounds = new Rect();

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (mApps.hasFilter()) {
                return;
            }

            List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
            String lastSectionName = null;
            int appIndexInSection = 0;
            int lastSectionTop = 0;
            int lastSectionHeight = 0;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                ViewHolder holder = (ViewHolder) parent.getChildViewHolder(child);
                if (shouldDrawItemSection(holder, child, i, items)) {
                    int cellTopOffset = (2 * child.getPaddingTop());
                    int pos = holder.getPosition();
                    AlphabeticalAppsList.AdapterItem item = items.get(pos);
                    if (!item.sectionName.equals(lastSectionName)) {
                        lastSectionName = item.sectionName;

                        // Find the section code points
                        String sectionBegin = null;
                        String sectionEnd = null;
                        int charOffset = 0;
                        while (charOffset < item.sectionName.length()) {
                            int codePoint = item.sectionName.codePointAt(charOffset);
                            int codePointSize = Character.charCount(codePoint);
                            if (charOffset == 0) {
                                // The first code point
                                sectionBegin = item.sectionName.substring(charOffset, charOffset + codePointSize);
                            } else if ((charOffset + codePointSize) >= item.sectionName.length()) {
                                // The last code point
                                sectionEnd = item.sectionName.substring(charOffset, charOffset + codePointSize);
                            }
                            charOffset += codePointSize;
                        }

                        Point sectionBeginBounds = getAndCacheSectionBounds(sectionBegin);
                        int minTop = cellTopOffset + sectionBeginBounds.y;
                        int top = child.getTop() + cellTopOffset + sectionBeginBounds.y;
                        int left = mIsRtl ? parent.getWidth() - mPaddingStart - mStartMargin :
                                mPaddingStart;
                        int col = appIndexInSection % mAppsPerRow;
                        int nextRowPos = Math.min(pos - col + mAppsPerRow, items.size() - 1);
                        int alpha = 255;
                        boolean fixedToRow = !items.get(nextRowPos).sectionName.equals(item.sectionName);
                        if (fixedToRow) {
                            alpha = Math.min(255, (int) (255 * (Math.max(0, top) / (float) minTop)));
                        } else {
                            // If we aren't fixed to the current row, then bound into the viewport
                            top = Math.max(minTop, top);
                        }
                        if (lastSectionHeight > 0 && top <= (lastSectionTop + lastSectionHeight)) {
                            top += lastSectionTop - top + lastSectionHeight;
                        }
                        if (FADE_OUT_SECTIONS) {
                            mSectionTextPaint.setAlpha(alpha);
                        }
                        if (sectionEnd != null) {
                            Point sectionEndBounds = getAndCacheSectionBounds(sectionEnd);
                            c.drawText(sectionBegin + "/" + sectionEnd,
                                    left + (mStartMargin - sectionBeginBounds.x - sectionEndBounds.x) / 2, top,
                                    mSectionTextPaint);
                        } else {
                            c.drawText(sectionBegin, left + (mStartMargin - sectionBeginBounds.x) / 2, top,
                                    mSectionTextPaint);
                        }
                        lastSectionTop = top;
                        lastSectionHeight = sectionBeginBounds.y + mSectionHeaderOffset;
                    }
                }
                if (holder.mIsSectionHeader) {
                    appIndexInSection = 0;
                } else {
                    appIndexInSection++;
                }
            }
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            // Do nothing
        }

        private Point getAndCacheSectionBounds(String sectionName) {
            Point bounds = mCachedSectionBounds.get(sectionName);
            if (bounds == null) {
                mSectionTextPaint.getTextBounds(sectionName, 0, sectionName.length(), mTmpBounds);
                bounds = new Point(mTmpBounds.width(), mTmpBounds.height());
                mCachedSectionBounds.put(sectionName, bounds);
            }
            return bounds;
        }

        private boolean shouldDrawItemSection(ViewHolder holder, View child, int childIndex,
                List<AlphabeticalAppsList.AdapterItem> items) {
            // Ensure item is not already removed
            GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams)
                    child.getLayoutParams();
            if (lp.isItemRemoved()) {
                return false;
            }
            // Ensure we have a valid holder
            if (holder == null) {
                return false;
            }
            // Ensure it's not an empty row
            if (holder.mIsEmptyRow) {
                return false;
            }
            // Ensure we have a holder position
            int pos = holder.getPosition();
            if (pos < 0 || pos >= items.size()) {
                return false;
            }
            // Ensure this is not a section header
            if (items.get(pos).isSectionHeader) {
                return false;
            }
            // Only draw the header for the first item in a section, or whenever the sub-sections
            // changes (if AppsContainerView.GRID_MERGE_SECTIONS is true, but
            // AppsContainerView.GRID_MERGE_SECTION_HEADERS is false)
            return (childIndex == 0) ||
                    items.get(pos - 1).isSectionHeader && !items.get(pos).isSectionHeader ||
                    (!items.get(pos - 1).sectionName.equals(items.get(pos).sectionName));
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
    @Thunk int mSectionHeaderOffset;
    @Thunk Paint mSectionTextPaint;


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
        if (!AppsContainerView.GRID_HIDE_SECTION_HEADERS) {
            mStartMargin = res.getDimensionPixelSize(R.dimen.apps_grid_view_start_margin);
            mSectionHeaderOffset = res.getDimensionPixelSize(R.dimen.apps_grid_section_y_offset);
        }
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
    public GridLayoutManager getLayoutManager() {
        return mGridLayoutMgr;
    }

    /**
     * Returns the item decoration for the recycler view.
     */
    public RecyclerView.ItemDecoration getItemDecoration() {
        // We don't draw any headers when we are uncomfortably dense
        if (!AppsContainerView.GRID_HIDE_SECTION_HEADERS) {
            return mItemDecoration;
        }
        return null;
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
