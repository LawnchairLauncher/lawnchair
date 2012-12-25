package com.cyanogenmod.trebuchet.preference;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.cyanogenmod.trebuchet.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HiddenAppsActivity extends ListActivity implements MenuItem.OnMenuItemClickListener {

    private boolean mSaved;

    private static final int MENU_DELETE = 0;

    private PackageManager mPackageManager;

    private AppsAdapter mAppsAdapter;

    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        setTitle(R.string.hidden_apps_title);
        setContentView(R.layout.hidden_apps_list);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        setProgressBarIndeterminateVisibility(true);
        setProgressBarIndeterminate(true);

        mPackageManager = getPackageManager();
        mAppsAdapter = new AppsAdapter(this, R.layout.hidden_apps_list_item);
        mAppsAdapter.setNotifyOnChange(true);

        setListAdapter(mAppsAdapter);

        AsyncTask<Void, Void, Void> refreshAppsTask = new AsyncTask<Void, Void, Void>(){

            @Override
            protected void onPostExecute(Void result) {
                restore();
                setProgressBarIndeterminateVisibility(false);
                setProgressBarIndeterminate(false);
                super.onPostExecute(result);
            }

            @Override
            protected Void doInBackground(Void... params) {
                refreshApps();
                return null;
            }
        };
        refreshAppsTask.execute(null, null, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        if (!mSaved) {
            StringBuilder string = new StringBuilder("");

            SparseBooleanArray checked = getListView().getCheckedItemPositions();

            for (int i = 0; i < checked.size(); i++) {
                if (checked.valueAt(i)) {
                    ResolveInfo app = (ResolveInfo) getListView().getItemAtPosition(checked.keyAt(i));
                    if (string.length() > 0) string.append("|");
                    string.append(new ComponentName(app.activityInfo.packageName, app.activityInfo.name).flattenToString());

                }
            }

            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor.putString("ui_drawer_hidden_apps", string.toString());
            editor.putBoolean(PreferencesProvider.PREFERENCES_CHANGED, true);
            editor.commit();

            mSaved = true;
        }
    }

    private void restore() {
        List<ComponentName> apps = new ArrayList<ComponentName>();
        String[] flattened = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("ui_drawer_hidden_apps", "").split("\\|");
        for (String flat : flattened) {
            apps.add(ComponentName.unflattenFromString(flat));
        }

        for (int i = 0; i < getListAdapter().getCount(); i++) {
            ResolveInfo info = (ResolveInfo) getListAdapter().getItem(i);
            if (apps.contains(new ComponentName(info.activityInfo.packageName, info.activityInfo.name))) {
                getListView().setItemChecked(i, true);
            }
        }

        mSaved = true;

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(0, MENU_DELETE, 0, R.string.menu_hidden_apps_delete)
                .setOnMenuItemClickListener(this)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DELETE:
                delete();
                return true;
        }
        return false;
    }

    private void delete() {
        for (int i = 0; i < getListView().getCount(); i++) {
            getListView().setItemChecked(i, false);
        }

        mSaved = false;
    }

    private void refreshApps() {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = mPackageManager.queryIntentActivities(mainIntent, 0);
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(mPackageManager));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAppsAdapter.clear();
                mAppsAdapter.addAll(apps);
            }
        });
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        boolean result = super.onMenuItemSelected(featureId, item);
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return result;
    }

    private class AppsAdapter extends ArrayAdapter<ResolveInfo> {

        private final LayoutInflater mInfaltor;

        public AppsAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);

            mInfaltor = LayoutInflater.from(context);

        }

        @Override
        public long getItemId(int id) {
            return id;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final ResolveInfo info = getItem(position);

            if(convertView == null) {
                convertView = mInfaltor.inflate(R.layout.hidden_apps_list_item, parent, false);
            }

            final View item = convertView;

            ImageView icon = (ImageView) item.findViewById(R.id.icon);
            TextView title = (TextView) item.findViewById(R.id.title);

            icon.setImageDrawable(info.loadIcon(mPackageManager));
            title.setText(info.loadLabel(mPackageManager));

            item.setTag(info.activityInfo.packageName);

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    getListView().setItemChecked(position, !((Checkable) item).isChecked());
                    mSaved = false;
                }
            });

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
