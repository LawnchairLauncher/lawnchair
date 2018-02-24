package ch.deletescape.lawnchair;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.graphics.LauncherIcons;

import java.util.List;

public class MultiSelectRecyclerViewAdapter extends SelectableAdapter<MultiSelectRecyclerViewAdapter.ViewHolder> {

    private List<LauncherActivityInfo> mResolveInfos;
    private ItemClickListener mClickListener;

    public MultiSelectRecyclerViewAdapter(Context context, List<LauncherActivityInfo> resolveInfos, ItemClickListener clickListener) {
        super(context);

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
        LauncherActivityInfo info = mResolveInfos.get(position);
        Drawable drawable = LauncherAppState.getInstance(getContext()).getIconCache()
                .getIconProvider().getIcon(info, 0, true);
        Bitmap icon = LauncherIcons.createBadgedIconBitmap(drawable, Process.myUserHandle(),
                getContext(), info.getApplicationInfo().targetSdkVersion);

        String component = info.getComponentName().toString();
        viewHolder.label.setText(info.getLabel());
        viewHolder.icon.setImageDrawable(new BitmapDrawable(getContext().getResources(), icon));

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