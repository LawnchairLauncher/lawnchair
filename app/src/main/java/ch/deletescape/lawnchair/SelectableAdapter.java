package ch.deletescape.lawnchair;

import android.app.ActionBar;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;

import java.util.HashSet;
import java.util.Set;

import ch.deletescape.lawnchair.preferences.PreferenceProvider;

abstract class SelectableAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    private Set<String> mSelections;
    private Context mContext;

    SelectableAdapter() {

        mContext = LauncherAppState.getInstanceNoCreate().getContext();

        Set<String> hiddenApps = PreferenceProvider.INSTANCE.getPreferences(mContext).getHiddenAppsSet();

        mSelections = new HashSet<>();

        //add already hidden apps to selections
        if (hiddenApps != null && !hiddenApps.isEmpty()) {
            mSelections.addAll(hiddenApps);
        }
    }

    boolean isSelected(String packageName) {

        return mSelections.contains(packageName);
    }

    void toggleSelection(ActionBar actionBar, int position, String packageName) {

        if (mSelections.contains(packageName)) {

            mSelections.remove(packageName);
        } else {
            mSelections.add(packageName);
        }
        if (!mSelections.isEmpty()) {
            actionBar.setTitle(String.valueOf(mSelections.size()) + mContext.getString(R.string.hide_app_selected));
        } else {
            actionBar.setTitle(mContext.getString(R.string.hidden_app));
        }
        notifyItemChanged(position);
    }

    void addSelectionsToHideList(Context context) {
        PreferenceProvider.INSTANCE.getPreferences(context).setHiddenAppsSet(mSelections);
    }
}