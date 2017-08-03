package ch.deletescape.lawnchair.iconpack;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;

public class CustomIconAdapter extends RecyclerView.Adapter<CustomIconAdapter.Holder> {

    private final Context mContext;
    private final List<IconInfo> mIcons;
    private Listener mListener;

    CustomIconAdapter(Context context, LauncherActivityInfoCompat info, List<EditIconActivity.IconPackInfo> iconPacks) {
        mContext = context;
        mIcons = new ArrayList<>();
        DefaultIconPack defaultIconPack = new DefaultIconPack();
        mIcons.add(new IconInfo(defaultIconPack, defaultIconPack.getIcon(info)));

        for (EditIconActivity.IconPackInfo iconPack : iconPacks) {
            Drawable icon = iconPack.getIconPack().getIcon(info);
            if (icon != null) {
                mIcons.add(new IconInfo(iconPack.getIconPack(), icon));
            }
        }
    }

    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext).inflate(R.layout.icon_item, parent, false);
        return new Holder(itemView);
    }

    @Override
    public void onBindViewHolder(Holder holder, int position) {
        holder.bind(mIcons.get(position));
    }

    @Override
    public int getItemCount() {
        return mIcons.size();
    }

    private void onSelect(int position) {
        if (mListener != null)
            mListener.onSelect(mIcons.get(position));
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public class Holder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final ImageView mIconView;

        private Holder(View itemView) {
            super(itemView);

            mIconView = (ImageView) itemView;
            mIconView.setOnClickListener(this);
        }

        public void bind(IconInfo iconInfo) {
            mIconView.setImageDrawable(iconInfo.getIcon());
        }

        @Override
        public void onClick(View view) {
            onSelect(getAdapterPosition());
        }
    }

    class IconInfo {
        private final IconPack mIconPack;
        private final Drawable mIcon;

        IconInfo(IconPack iconPack, Drawable icon) {
            mIconPack = iconPack;
            mIcon = icon;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        @Override
        public String toString() {
            return "iconPacks/" + mIconPack.getPackageName();
        }
    }

    interface Listener {

        void onSelect(IconInfo iconInfo);
    }
}
