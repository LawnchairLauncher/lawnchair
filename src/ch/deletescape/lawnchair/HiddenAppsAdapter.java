package ch.deletescape.lawnchair;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.AppInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;
import com.google.android.apps.nexuslauncher.CustomAppFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HiddenAppsAdapter extends RecyclerView.Adapter<HiddenAppsAdapter.ViewHolder> {

    private List<LauncherActivityInfo> mResolveInfos;
    private Set<ComponentKey> mSelections;
    private Context mContext;
    private Callback mCallback;

    public HiddenAppsAdapter(Context context, List<LauncherActivityInfo> resolveInfos, Callback callback) {
        mContext = context;
        mResolveInfos = resolveInfos;

        mCallback = callback;

        Set<String> hiddenApps = CustomAppFilter.getHiddenApps(mContext);
        mSelections = new HashSet<>();
        //add already hidden apps to selections
        for (String hiddenApp : hiddenApps) {
            mSelections.add(new ComponentKey(context, hiddenApp));
        }
    }

    // Create new views
    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public HiddenAppsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                           int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.hide_item, null));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        LauncherActivityInfo info = mResolveInfos.get(position);
        AppInfo appInfo = new AppInfo(mContext, info, info.getUser());
        LauncherAppState.getInstance(mContext).getIconCache().getTitleAndIcon(appInfo, false);
        Bitmap icon = appInfo.iconBitmap;

        ComponentKey component = new ComponentKey(info.getComponentName(), info.getUser());
        viewHolder.label.setText(info.getLabel());
        viewHolder.icon.setImageDrawable(new BitmapDrawable(mContext.getResources(), icon));

        viewHolder.checkBox.setChecked(isSelected(component));
    }

    private String toggleSelection(int position) {
        LauncherActivityInfo info = mResolveInfos.get(position);
        ComponentKey componentKey = new ComponentKey(info.getComponentName(), info.getUser());
        if (mSelections.contains(componentKey)) {
            mSelections.remove(componentKey);
        } else {
            mSelections.add(componentKey);
        }
        Set<String> selections = new HashSet<>();
        for (ComponentKey component : mSelections) {
            selections.add(component.toString());
        }
        CustomAppFilter.setHiddenApps(mContext, selections);
        if (!mSelections.isEmpty()) {
            return mSelections.size() + mContext.getString(R.string.hide_app_selected);
        } else {
            return mContext.getString(R.string.hidden_app);
        }
    }

    private boolean isSelected(int position) {
        LauncherActivityInfo info = mResolveInfos.get(position);
        return isSelected(new ComponentKey(info.getComponentName(), info.getUser()));
    }

    private boolean isSelected(ComponentKey component) {
        return mSelections.contains(component);
    }

    public String clearSelection() {
        mSelections.clear();
        notifyDataSetChanged();
        return mContext.getString(R.string.hidden_app);
    }

    @Override
    public int getItemCount() {
        return mResolveInfos.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView label;
        private ImageView icon;
        private CheckBox checkBox;

        ViewHolder(View itemLayoutView) {
            super(itemLayoutView);

            label = itemLayoutView.findViewById(R.id.label);
            icon = itemLayoutView.findViewById(R.id.icon);
            checkBox = itemLayoutView.findViewById(R.id.check);

            itemLayoutView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            mCallback.setTitle(toggleSelection(getAdapterPosition()));
            checkBox.setChecked(isSelected(getAdapterPosition()));
        }
    }

    public interface Callback {

        void setTitle(String newTitle);
    }
}