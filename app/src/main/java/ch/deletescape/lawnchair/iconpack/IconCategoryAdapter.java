package ch.deletescape.lawnchair.iconpack;

import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import ch.deletescape.lawnchair.R;

class IconCategoryAdapter extends RecyclerView.Adapter<IconCategoryAdapter.Holder> implements IconGridAdapter.Listener {

    private List<IconPack.IconCategory> mCategoryList;
    private RecyclerView mRecyclerView;

    private int mColumnWidth = 0;
    private IconGridAdapter.Listener mListener;

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        mRecyclerView = recyclerView;
        mColumnWidth = recyclerView.getResources().getDimensionPixelSize(R.dimen.icon_grid_column_width);
    }

    public void setCategoryList(List<IconPack.IconCategory> categoryList) {
        mCategoryList = categoryList;
        notifyDataSetChanged();
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.icon_category, parent, false);
        return new Holder(itemView);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return mCategoryList.size();
    }

    public void setListener(IconGridAdapter.Listener listener) {
        mListener = listener;
    }

    @Override
    public void onSelect(IconPack.IconEntry iconEntry) {
        if (mListener != null)
            mListener.onSelect(iconEntry);
    }

    public class Holder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final RecyclerView recyclerView;
        private final IconGridAdapter adapter;
        private final GridLayoutManager layoutManager;

        public Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(android.R.id.title);
            recyclerView = itemView.findViewById(R.id.iconRecyclerView);
            adapter = new IconGridAdapter();
            adapter.setListener(IconCategoryAdapter.this);
            layoutManager = new GridLayoutManager(itemView.getContext(), 1);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setAdapter(adapter);
        }

        public void bind(int position) {
            layoutManager.setSpanCount(mRecyclerView.getWidth() / mColumnWidth);
            IconPack.IconCategory category = mCategoryList.get(position);
            title.setText(category.getTitle());
            adapter.setIconCategory(category);
        }
    }
}
