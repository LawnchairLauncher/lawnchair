package ch.deletescape.lawnchair;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;

public class MultiSelectRecyclerViewAdapter extends SelectableAdapter<MultiSelectRecyclerViewAdapter.ViewHolder> {

    private List<LauncherActivityInfoCompat> mResolveInfos;
    private ItemClickListener mClickListener;

    public MultiSelectRecyclerViewAdapter(List<LauncherActivityInfoCompat> resolveInfos, ItemClickListener clickListener) {
        mResolveInfos = resolveInfos;
        mClickListener = clickListener;
    }

    // Create new views
    @Override
    public MultiSelectRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                                        int viewType) {
        View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.hide_item, null);

        return new ViewHolder(itemLayoutView, mClickListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {

        String component = mResolveInfos.get(position).getComponentName().flattenToString();
        viewHolder.label.setText(mResolveInfos.get(position).getLabel());
        viewHolder.icon.setImageDrawable(mResolveInfos.get(position).getIcon(0));

        viewHolder.checkBox.setChecked(isSelected(component));
    }

    @Override
    public int getItemCount() {
        return mResolveInfos.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView label;
        private ImageView icon;
        private CheckBox checkBox;
        private ItemClickListener listener;

        ViewHolder(View itemLayoutView, ItemClickListener listener) {
            super(itemLayoutView);

            this.listener = listener;

            label = itemLayoutView.findViewById(R.id.label);
            icon = itemLayoutView.findViewById(R.id.icon);
            checkBox = itemLayoutView.findViewById(R.id.check);

            itemLayoutView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (listener != null) {
                listener.onItemClicked(getAdapterPosition());
            }
        }
    }

    public interface ItemClickListener {
        void onItemClicked(int position);
    }
}