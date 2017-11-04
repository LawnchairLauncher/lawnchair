package ch.deletescape.lawnchair;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import ch.deletescape.lawnchair.preferences.PreferenceProvider;

public class MultiSelectRecyclerViewActivity extends Activity implements MultiSelectRecyclerViewAdapter.ItemClickListener {

    private List<ResolveInfo> mInstalledPackages;
    private ActionBar mActionBar;
    private MultiSelectRecyclerViewAdapter mAdapter;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        //getMenuInflater().inflate(R.menu.hide_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        updateHiddenApps();
        return super.onOptionsItemSelected(item);
    }

    private void updateHiddenApps() {

        mAdapter.addSelectionsToHideList(MultiSelectRecyclerViewActivity.this);
        LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
        if (appState != null) {
            appState.getModel().forceReload();
        }

        navigateUpTo(new Intent(MultiSelectRecyclerViewActivity.this, Launcher.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiselect);
        mActionBar = getActionBar();

        Set<String> hiddenApps = PreferenceProvider.INSTANCE.getPreferences(this).getHiddenAppsSet();
        if (hiddenApps != null) {
            if (!hiddenApps.isEmpty()) {
                mActionBar.setTitle(String.valueOf(hiddenApps.size()) + getString(R.string.hide_app_selected));
            } else {
                mActionBar.setTitle(getString(R.string.hidden_app));
            }
        }

        mInstalledPackages = getInstalledApps();
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(MultiSelectRecyclerViewActivity.this, LinearLayoutManager.VERTICAL, false));
        mAdapter = new MultiSelectRecyclerViewAdapter(MultiSelectRecyclerViewActivity.this, mInstalledPackages, this);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onItemClicked(int position) {

        mAdapter.toggleSelection(mActionBar, position, mInstalledPackages.get(position).activityInfo.packageName);
    }

    private List<ResolveInfo> getInstalledApps() {
        //get a list of installed apps.
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> installedApps = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA);
        Collections.sort(installedApps, new ResolveInfo.DisplayNameComparator(packageManager));
        return installedApps;
    }
}