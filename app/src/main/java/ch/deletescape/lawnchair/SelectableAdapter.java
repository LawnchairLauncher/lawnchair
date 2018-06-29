package ch.deletescape.lawnchair;

import android.content.Context;
import android.support.v7.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

import ch.deletescape.lawnchair.preferences.PreferenceProvider;

abstract class SelectableAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    protected Set<String> mSelections;
    protected Context mContext;

    public static final int HIDDEN_APP = 0;
    public static final int HIDDEN_APP_SELECTED = 1;

    SelectableAdapter() {

        mContext = LauncherAppState.getInstanceNoCreate().getContext();

        Set<String> hiddenApps = getSelectionsFromList();

        mSelections = new HashSet<>();

        //add already hidden apps to selections
        if (hiddenApps != null && !hiddenApps.isEmpty()) {
            mSelections.addAll(hiddenApps);
        }
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
            return mSelections.size() + getString(mContext, HIDDEN_APP_SELECTED);
        } else {
            return getString(mContext, HIDDEN_APP);
        }
    }

    public String clearSelection() {
        mSelections.clear();
        notifyDataSetChanged();
        return getString(mContext, HIDDEN_APP);
    }

    public String getString(Context context, int state) {
        switch (state) {
            case HIDDEN_APP:
                return context.getString(R.string.hidden_app);
            case HIDDEN_APP_SELECTED:
                return context.getString(R.string.hidden_app_selected);
            default:
                return null;
        }
    }

    protected Set<String> getSelectionsFromList() {
        return PreferenceProvider.INSTANCE.getPreferences(mContext).getHiddenAppsSet();
    }

    public void addSelectionsToList(Context context) {
        PreferenceProvider.INSTANCE.getPreferences(context).setHiddenAppsSet(mSelections);
    }
}
