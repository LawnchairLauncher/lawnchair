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

    private final int TYPE_LOADING = 0;
    private final int TYPE_CATEGORY = 1;

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
                .inflate(getItemLayout(viewType), parent, false);
        return getItemViewType(viewType) == TYPE_CATEGORY ? new CategoryHolder(itemView) : new LoadingHolder(itemView);
    }

    private int getItemLayout(int viewType) {
        return viewType == TYPE_CATEGORY ? R.layout.icon_category : R.layout.icon_category_loading;
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemViewType(int position) {
        return mCategoryList.size() > 0 ? TYPE_CATEGORY : TYPE_LOADING;
    }

    @Override
    public int getItemCount() {
        return Math.max(mCategoryList.size(), 1);
    }

    public void setListener(IconGridAdapter.Listener listener) {
        mListener = listener;
    }

    @Override
    public void onSelect(IconPack.IconEntry iconEntry) {
        if (mListener != null)
            mListener.onSelect(iconEntry);
    }

    public abstract class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }

        public abstract void bind(int position);
    }

    public class LoadingHolder extends Holder {

        public LoadingHolder(View itemView) {
            super(itemView);
        }

        @Override
        public void bind(int position) {

        }
    }

    public class CategoryHolder extends Holder {

        private TextView title;
        private RecyclerView recyclerView;
        private IconGridAdapter adapter;
        private GridLayoutManager layoutManager;

        public CategoryHolder(View itemView) {
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
