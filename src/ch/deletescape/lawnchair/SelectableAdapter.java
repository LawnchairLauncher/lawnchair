package ch.deletescape.lawnchair;

import android.content.Context;
import android.support.v7.widget.RecyclerView;

import com.android.launcher3.R;
import com.google.android.apps.nexuslauncher.CustomAppFilter;

import java.util.HashSet;
import java.util.Set;

abstract class SelectableAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    private Set<String> mSelections;
    private Context mContext;

    SelectableAdapter(Context context) {

        mContext = context;

        Set<String> hiddenApps = CustomAppFilter.getHiddenApps(mContext);

        mSelections = new HashSet<>();

        //add already hidden apps to selections
        mSelections.addAll(hiddenApps);
    }

    boolean isSelected(String packageName) {

        return mSelections.contains(packageName);
    }

    public String toggleSelection(int position, String packageName) {
        if (mSelections.contains(packageName)) {
            mSelections.remove(packageName);
        } else {
            mSelections.add(packageName);
        }
        notifyItemChanged(position);
        if (!mSelections.isEmpty()) {
            return mSelections.size() + mContext.getString(R.string.hide_app_selected);
        } else {
            return mContext.getString(R.string.hidden_app);
        }
    }

    public String clearSelection() {
        mSelections.clear();
        notifyDataSetChanged();
        return mContext.getString(R.string.hidden_app);
    }

    public void addSelectionsToHideList(Context context) {
        CustomAppFilter.setHiddenApps(context, mSelections);
    }

    public Context getContext() {
        return mContext;
    }
}