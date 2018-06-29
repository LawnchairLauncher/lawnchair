package ch.deletescape.lawnchair.preferences;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.MultiSelectRecyclerViewAdapter;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.compat.LauncherAppsCompat;

public class ShortcutBlacklistFragment extends Fragment implements MultiSelectRecyclerViewAdapter.ItemClickListener {
    private List<LauncherActivityInfoCompat> installedApps;
    private MultiSelectRecyclerViewAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_selectable_apps, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = view.getContext();
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);

        // Sort installed apps by using a custom Comparator
        installedApps = getAppsList(context);
        Collections.sort(installedApps, new Comparator<LauncherActivityInfoCompat>() {
            @Override
            public int compare(LauncherActivityInfoCompat a, LauncherActivityInfoCompat b) {
                return a.getLabel().toString().compareToIgnoreCase(b.getLabel().toString());
            }
        });

        // Inherit SelectableAdapter for hidden apps and apply shortcut blacklist
        adapter = new MultiSelectRecyclerViewAdapter(installedApps, this){
            @Override
            public String getString(Context context, int state) {
                switch (state) {
                    case HIDDEN_APP:
                        return context.getString(R.string.blacklist_app);
                    case HIDDEN_APP_SELECTED:
                        return context.getString(R.string.blacklist_app_selected);
                    default:
                        return null;
                }
            }

            @Override
            protected Set<String> getSelectionsFromList() {
                return PreferenceProvider.INSTANCE.getPreferences(mContext).getShortcutBlacklist();
            }

            @Override
            public void addSelectionsToList(Context context) {
                PreferenceProvider.INSTANCE.getPreferences(context).setShortcutBlacklist(mSelections);
            }

            @Override
            public String getComponent(ComponentName component) {
                return component.getPackageName();
            }
        };

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Set<String> blacklistApps = PreferenceProvider.INSTANCE.getPreferences(context).getShortcutBlacklist();
        if (!blacklistApps.isEmpty()) {
            getActivity().setTitle(blacklistApps.size() + getString(R.string.blacklist_app_selected));
        } else {
            getActivity().setTitle(getString(R.string.blacklist_app));
        }
    }

    @Override
    public void onItemClicked(int position) {
        getActivity().setTitle(adapter.toggleSelection(position, installedApps.get(position).getComponentName().getPackageName()));
    }

    private List<LauncherActivityInfoCompat> getAppsList(Context context){
        return LauncherAppsCompat.getInstance(context).getActivityList(null, Process.myUserHandle());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_hide_apps, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_apply:
                adapter.addSelectionsToList(getActivity());
                LauncherAppState.getInstanceNoCreate().reloadAllApps();
                getActivity().onBackPressed();
                return true;
            case R.id.action_reset:
                getActivity().setTitle(adapter.clearSelection());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
