package ch.deletescape.lawnchair.iconpack;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.List;

import ch.deletescape.lawnchair.R;

public class IconGridAdapter extends RecyclerView.Adapter<IconGridAdapter.Holder> {

    List<IconPack.IconEntry> mIconList;
    private Listener mListener;

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.icon_item, parent, false);
        return new Holder(itemView);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        holder.bind(mIconList.get(position));
    }

    @Override
    public int getItemCount() {
        return mIconList == null ? 0 : mIconList.size();
    }

    public void setIconList(List<IconPack.IconEntry> iconList) {
        mIconList = iconList;
        notifyDataSetChanged();
    }

    private void onSelect(int position) {
        IconPack.IconEntry iconEntry = mIconList.get(position);
        if (mListener != null)
            mListener.onSelect(iconEntry);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public class Holder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final ImageView mIconView;

        public Holder(View itemView) {
            super(itemView);

            mIconView = (ImageView) itemView;
            mIconView.setOnClickListener(this);
        }

        public void bind(IconPack.IconEntry iconEntry) {
            mIconView.setImageDrawable(iconEntry.loadDrawable());
        }

        @Override
        public void onClick(View view) {
            onSelect(getAdapterPosition());
        }
    }

    interface Listener {

        void onSelect(IconPack.IconEntry iconEntry);
    }
}
