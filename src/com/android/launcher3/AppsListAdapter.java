package com.android.launcher3;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The linear list view adapter for all the apps.
 */
class AppsListAdapter extends RecyclerView.Adapter<AppsListAdapter.ViewHolder> {

    /**
     * ViewHolder for each row.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mContent;

        public ViewHolder(View v) {
            super(v);
            mContent = v;
        }
    }

    private static final int SECTION_BREAK_VIEW_TYPE = 0;
    private static final int ICON_VIEW_TYPE = 1;
    private static final int EMPTY_VIEW_TYPE = 2;

    private LayoutInflater mLayoutInflater;
    private AlphabeticalAppsList mApps;
    private View.OnTouchListener mTouchListener;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;
    private String mEmptySearchText;

    public AppsListAdapter(Context context, AlphabeticalAppsList apps,
            View.OnTouchListener touchListener, View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener) {
        mApps = apps;
        mLayoutInflater = LayoutInflater.from(context);
        mTouchListener = touchListener;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
    }

    public RecyclerView.LayoutManager getLayoutManager(Context context) {
        return new LinearLayoutManager(context);
    }

    /**
     * Sets the text to show when there are no apps.
     */
    public void setEmptySearchText(String query) {
        mEmptySearchText = query;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case EMPTY_VIEW_TYPE:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.apps_empty_view, parent,
                        false));
            case SECTION_BREAK_VIEW_TYPE:
                return new ViewHolder(new View(parent.getContext()));
            case ICON_VIEW_TYPE:
                // Inflate the row and all the icon children necessary
                ViewGroup row = (ViewGroup) mLayoutInflater.inflate(R.layout.apps_list_row_view,
                        parent, false);
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.apps_list_row_icon_view, row, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                lp.gravity = Gravity.CENTER_VERTICAL;
                icon.setLayoutParams(lp);
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setFocusable(true);
                row.addView(icon);
                return new ViewHolder(row);
            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case ICON_VIEW_TYPE:
                AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
                ViewGroup content = (ViewGroup) holder.mContent;
                String sectionDescription = item.sectionName;

                // Bind the section header
                boolean showSectionHeader = true;
                if (position > 0) {
                    AlphabeticalAppsList.AdapterItem prevItem =
                            mApps.getAdapterItems().get(position - 1);
                    showSectionHeader = prevItem.isSectionHeader;
                }
                TextView tv = (TextView) content.findViewById(R.id.section);
                if (showSectionHeader) {
                    tv.setText(sectionDescription);
                    tv.setVisibility(View.VISIBLE);
                } else {
                    tv.setVisibility(View.INVISIBLE);
                }

                // Bind the icon
                BubbleTextView icon = (BubbleTextView) content.getChildAt(1);
                icon.applyFromApplicationInfo(item.appInfo);
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
