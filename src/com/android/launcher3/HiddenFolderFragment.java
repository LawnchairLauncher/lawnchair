package com.android.launcher3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.media.Image;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.android.launcher3.settings.SettingsProvider;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

public class HiddenFolderFragment extends Fragment {
    public static final String HIDDEN_FOLDER_FRAGMENT = "hiddenFolderFragment";
    public static final String HIDDEN_FOLDER_NAME = "hiddenFolderName";
    public static final String HIDDEN_FOLDER_STATUS = "hiddenFolderStatus";
    public static final String HIDDEN_FOLDER_INFO = "hiddenFolderInfo";
    public static final String HIDDEN_FOLDER_INFO_TITLES = "hiddenFolderInfoTitles";
    public static final String HIDDEN_FOLDER_LAUNCH = "hiddenFolderLaunchPosition";

    private static final int REQ_LOCK_PATTERN = 1;

    private String[] mComponentInfo;
    private String[] mComponentTitles;
    private boolean mHidden;
    private PackageManager mPackageManager;
    private AppsAdapter mAppsAdapter;
    private ArrayList<AppEntry> mAppEntries;

    private EditText mFolderName;
    private ListView mListView;

    private Launcher mLauncher;

    private boolean mAuth = false;
    private boolean mSent = false;

    private OnClickListener mClicklistener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mHidden = !mHidden;

            ImageView mLock = (ImageView) v;
            Drawable mLockIcon = mHidden ? getResources().getDrawable(R.drawable.folder_lock_light)
                    : getResources().getDrawable(R.drawable.folder_unlock);
            mLock.setImageDrawable(mLockIcon);
        }
    };

    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container,
                              Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.hidden_folder, container, false);

        mLauncher = (Launcher) getActivity();
        mPackageManager = mLauncher.getPackageManager();

        mHidden = getArguments().getBoolean(HIDDEN_FOLDER_STATUS);
        Folder folder = mLauncher.mHiddenFolderIcon.getFolder();
        mComponentInfo = folder.getComponents();
        mComponentTitles = folder.getComponentTitles();
        String title = mLauncher.mHiddenFolderIcon.getFolderInfo().title.toString();

        mFolderName = (EditText) v.findViewById(R.id.folder_name);
        mFolderName.setText(title);
        mFolderName.setSelectAllOnFocus(true);
        mFolderName.setInputType(mFolderName.getInputType() |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        mFolderName.setImeOptions(EditorInfo.IME_ACTION_DONE);
        mFolderName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    doneEditingText(v);
                    return true;
                }
                return false;
            }
        });
        mFolderName.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    doneEditingText(v);
                }
            }
        });

        ImageView mLock = (ImageView) v.findViewById(R.id.folder_lock_icon);
        Drawable mLockIcon = mHidden ? getResources().getDrawable(R.drawable.folder_lock_light)
                : getResources().getDrawable(R.drawable.folder_unlock);
        mLock.setImageDrawable(mLockIcon);
        mLock.setOnClickListener(mClicklistener);

        mAppsAdapter = new AppsAdapter(mLauncher, R.layout.hidden_apps_list_item);
        mAppsAdapter.setNotifyOnChange(true);
        mAppEntries = loadApps();
        mAppsAdapter.clear();
        mAppsAdapter.addAll(mAppEntries);

        mListView = (ListView) v.findViewById(R.id.hidden_apps_list);
        mListView.setAdapter(mAppsAdapter);

        return v;
    }

    private void doneEditingText(View v) {
        InputMethodManager mInputMethodManager = (InputMethodManager)
                mLauncher.getSystemService(Context.INPUT_METHOD_SERVICE);
        mInputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);

        mListView.requestFocus();
    }

    private ArrayList<AppEntry> loadApps() {
        ArrayList<AppEntry> apps = new ArrayList<AppEntry>();
        int size = mComponentInfo.length;
        for (int i = 0; i < size; i++) {
            apps.add(new AppEntry(mComponentInfo[i], mComponentTitles[i]));
        }
        return apps;
    }

    private void removeComponentFromFolder(AppEntry app) {
        mLauncher.mHiddenFolderIcon.getFolderInfo().remove(
                mLauncher.mHiddenFolderIcon.getFolder()
                        .getShortcutForComponent(app.componentName));

        mAppEntries.remove(app);
        mAppsAdapter.remove(app);
        mAppsAdapter.notifyDataSetInvalidated();
    }

    public void saveHiddenFolderStatus(int position) {
        String newTitle = mFolderName.getText().toString();
        if (mLauncher.mHiddenFolderIcon != null) {
            if (position != -1) {
                Folder folder = mLauncher.mHiddenFolderIcon.getFolder();
                View v = folder.getViewFromPosition(position);
                Object tag = v.getTag();
                if (tag instanceof ShortcutInfo) {
                    mLauncher.startActivitySafely(v,
                            ((ShortcutInfo) tag).getIntent(),
                            v.getTag());

                    return;
                }
            }

            // Folder name
            FolderInfo info = mLauncher.mHiddenFolderIcon.getFolderInfo();
            if (!info.title.equals(newTitle)) {
                info.setTitle(newTitle);
                mLauncher.mHiddenFolderIcon.getFolder().setFolderName();
                LauncherModel.updateItemInDatabase(mLauncher, info);
            }

            // Folder hidden status
            if (info.hidden == mHidden) {
                return;
            } else {
                info.hidden = mHidden;
                // flip the boolean value to accomodate framework
                // in framework "false" is "protected" and "true" is "visible"
                mLauncher.mHiddenFolderIcon.getFolder().modifyProtectedApps(!info.hidden);

                LauncherModel.updateItemInDatabase(mLauncher, info);
                // We need to make sure this change gets written to the DB before
                // OnResume restarts the process
                mLauncher.mModel.flushWorkerThread();
            }
        }

        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction
                .remove(mLauncher.mHiddenFolderFragment).commit();
    }

    public class AppsAdapter extends ArrayAdapter<AppEntry> {

        private final LayoutInflater mInflator;

        private ConcurrentHashMap<String, Drawable> mIcons;
        private Drawable mDefaultImg;
        private List<AppEntry> mApps;

        public AppsAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);

            mApps = new ArrayList<AppEntry>();

            mInflator = LayoutInflater.from(context);

            // set the default icon till the actual app icon is loaded in async
            // task
            mDefaultImg = context.getResources().getDrawable(
                    android.R.mipmap.sym_def_app_icon);
            mIcons = new ConcurrentHashMap<String, Drawable>();
        }

        @Override
        public View getView(final int position, View convertView,
                ViewGroup parent) {
            final AppViewHolder viewHolder;

            if (convertView == null) {
                convertView = mInflator.inflate(
                        R.layout.hidden_folder_apps_list_item, parent, false);
                viewHolder = new AppViewHolder(convertView, position);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (AppViewHolder) convertView.getTag();
            }

            final AppEntry app = getItem(position);

            viewHolder.title.setText(app.title);

            Drawable icon = mIcons.get(app.componentName.getPackageName());
            viewHolder.icon.setImageDrawable(icon != null ? icon : mDefaultImg);
            viewHolder.remove.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeComponentFromFolder(app);
                }
            });

            convertView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveHiddenFolderStatus(viewHolder.position);
                }
            });

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            // If we have new items, we have to load their icons
            // If items were deleted, remove them from our mApps
            List<AppEntry> newApps = new ArrayList<AppEntry>(getCount());
            List<AppEntry> oldApps = new ArrayList<AppEntry>(getCount());
            for (int i = 0; i < getCount(); i++) {
                AppEntry app = getItem(i);
                if (mApps.contains(app)) {
                    oldApps.add(app);
                } else {
                    newApps.add(app);
                }
            }

            if (newApps.size() > 0) {
                new LoadIconsTask().execute(newApps.toArray(new AppEntry[] {}));
                newApps.addAll(oldApps);
                mApps = newApps;
            } else {
                mApps = oldApps;
            }
        }

        /**
         * An asynchronous task to load the icons of the installed applications.
         */
        private class LoadIconsTask extends AsyncTask<AppEntry, Void, Void> {
            @Override
            protected Void doInBackground(AppEntry... apps) {
                for (AppEntry app : apps) {
                    try {
                        if (mIcons.containsKey(app.componentName
                                .getPackageName())) {
                            continue;
                        }
                        Drawable icon = mPackageManager
                                .getApplicationIcon(app.componentName
                                        .getPackageName());
                        mIcons.put(app.componentName.getPackageName(), icon);
                        publishProgress();
                    } catch (PackageManager.NameNotFoundException e) {
                        // ignored; app will show up with default image
                    }
                }

                return null;
            }

            @Override
            protected void onProgressUpdate(Void... progress) {
                notifyDataSetChanged();
            }
        }
    }

    private final class AppEntry {

        public final ComponentName componentName;
        public final String title;

        public AppEntry(String component, String title) {
            componentName = ComponentName.unflattenFromString(component);
            this.title = title;
        }
    }

    private static class AppViewHolder {
        public final TextView title;
        public final ImageView icon;
        public final ImageView remove;
        public final int position;

        public AppViewHolder(View parentView, int position) {
            icon = (ImageView) parentView.findViewById(R.id.icon);
            remove = (ImageView) parentView.findViewById(R.id.remove);
            title = (TextView) parentView.findViewById(R.id.title);
            this.position = position;
        }
    }
}
