/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.BaseExpandableListAdapter;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shows a list of all the items that can be added to the workspace.
 */
public final class AddAdapter extends BaseExpandableListAdapter {
    private static final int GROUP_APPLICATIONS = 0;
    private static final int GROUP_SHORTCUTS = 1;
    private static final int GROUP_WIDGETS = 2;
    private static final int GROUP_WALLPAPERS = 3;

    private final Intent mCreateShortcutIntent;
    private Intent mSetWallpaperIntent;
    private final LayoutInflater mInflater;
    private Launcher mLauncher;
    private Group[] mGroups;

    /**
     * Abstract class representing one thing that can be added
     */
    public abstract class AddAction implements Runnable {
        private final Context mContext;

        AddAction(Context context) {
            mContext = context;
        }

        Drawable getIcon(int resource) {
            return mContext.getResources().getDrawable(resource);
        }

        public abstract void bindView(View v);
    }

    /**
     * Class representing an action that will create set the wallpaper.
     */
    public class SetWallpaperAction extends CreateShortcutAction {
        SetWallpaperAction(Context context, ResolveInfo info) {
            super(context, info);
        }

        public void run() {
            Intent intent = new Intent(mSetWallpaperIntent);
            ActivityInfo activityInfo = mInfo.activityInfo;
            intent.setComponent(new ComponentName(activityInfo.applicationInfo.packageName,
                    activityInfo.name));
            mLauncher.startActivity(intent);
        }
    }
    
    /**
     * Class representing an action that will create a specific type
     * of shortcut
     */
    public class CreateShortcutAction extends AddAction {
        
        ResolveInfo mInfo;
        private CharSequence mLabel;
        private Drawable mIcon;

        CreateShortcutAction(Context context, ResolveInfo info) {
            super(context);
            mInfo = info;
        }

        @Override
        public void bindView(View view) {
            ResolveInfo info = mInfo;
            TextView text = (TextView) view;

            PackageManager pm = mLauncher.getPackageManager();

            if (mLabel == null) {
                mLabel = info.loadLabel(pm);
                if (mLabel == null) {
                    mLabel = info.activityInfo.name;
                }
            }
            if (mIcon == null) {
                mIcon = info.loadIcon(pm);
            }

            text.setText(mLabel);
            text.setCompoundDrawablesWithIntrinsicBounds(mIcon, null, null, null);
        }

        public void run() {
            Intent intent = new Intent(mCreateShortcutIntent);
            ActivityInfo activityInfo = mInfo.activityInfo;
            intent.setComponent(new ComponentName(activityInfo.applicationInfo.packageName,
                    activityInfo.name));
            mLauncher.addShortcut(intent);
        }
    }
    
    /**
     * Class representing an action that will add a folder
     */
    public class CreateFolderAction extends AddAction {
        
        CreateFolderAction(Context context) {
            super(context);
        }

        @Override
        public void bindView(View view) {
            TextView text = (TextView) view;
            text.setText(R.string.add_folder);
            text.setCompoundDrawablesWithIntrinsicBounds(getIcon(R.drawable.ic_launcher_folder),
                    null, null, null);
        }

        public void run() {
            mLauncher.addFolder();
        }
    }

    /**
     * Class representing an action that will add a folder
     */
    public class CreateClockAction extends AddAction {

        CreateClockAction(Context context) {
            super(context);
        }

        @Override
        public void bindView(View view) {
            TextView text = (TextView) view;
            text.setText(R.string.add_clock);
            Drawable icon = getIcon(R.drawable.ic_launcher_alarmclock);
            text.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }

        public void run() {
            mLauncher.addClock();
        }
    }

    /**
     * Class representing an action that will add a PhotoFrame
     */
    public class CreatePhotoFrameAction extends AddAction {
        CreatePhotoFrameAction(Context context) {
            super(context);
        }

        @Override
        public void bindView(View view) {
            TextView text = (TextView) view;
            text.setText(R.string.add_photo_frame);
            Drawable icon = getIcon(R.drawable.ic_launcher_gallery);
            text.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }

        public void run() {
            mLauncher.getPhotoForPhotoFrame();
        }
    }


    /**
     * Class representing an action that will add a Search widget
     */
    public class CreateSearchAction extends AddAction {
        CreateSearchAction(Context context) {
            super(context);
        }

        @Override
        public void bindView(View view) {
            TextView text = (TextView) view;
            text.setText(R.string.add_search);
            Drawable icon = getIcon(R.drawable.ic_search_gadget);
            text.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }

        public void run() {
            mLauncher.addSearch();
        }
    }
    
    private class Group {
        private String mName;
        private ArrayList<AddAction> mList;

        Group(String name) {
            mName = name;
            mList = new ArrayList<AddAction>();
        }

        void add(AddAction action) {
            mList.add(action);
        }

        int size() {
            return mList.size();
        }

        String getName() {
            return mName;
        }

        void run(int position) {
            mList.get(position).run();
        }

        void bindView(int childPosition, View view) {
            mList.get(childPosition).bindView(view);
        }

        public Object get(int childPosition) {
            return mList.get(childPosition);
        }
    }

    private class ApplicationsGroup extends Group {
        private final Launcher mLauncher;
        private final ArrayList<ApplicationInfo> mApplications;

        ApplicationsGroup(Launcher launcher, String name) {
            super(name);
            mLauncher = launcher;
            mApplications = Launcher.getModel().getApplications();
        }

        @Override
        int size() {
            return mApplications == null ? 0 : mApplications.size();
        }

        @Override
        void add(AddAction action) {
        }

        @Override
        void run(int position) {
            final ApplicationInfo info = mApplications.get(position);
            mLauncher.addApplicationShortcut(info);
        }

        @Override
        void bindView(int childPosition, View view) {
            TextView text = (TextView) view.findViewById(R.id.title);

            final ApplicationInfo info = mApplications.get(childPosition);
            text.setText(info.title);
            if (!info.filtered) {
                info.icon = Utilities.createIconThumbnail(info.icon, mLauncher);
                info.filtered = true;
            }
            text.setCompoundDrawablesWithIntrinsicBounds(info.icon, null, null, null);
        }

        @Override
        public Object get(int childPosition) {
            return mApplications.get(childPosition);
        }
    }

    public AddAdapter(Launcher launcher, boolean forFolder) {
        mCreateShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        mCreateShortcutIntent.setComponent(null);

        mSetWallpaperIntent = new Intent(Intent.ACTION_SET_WALLPAPER);
        mSetWallpaperIntent.setComponent(null);

        mLauncher = launcher;
        mInflater = (LayoutInflater) launcher.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mGroups = new Group[forFolder ? 2 : 4];
        final Group[] groups = mGroups;
        groups[GROUP_APPLICATIONS] = new ApplicationsGroup(mLauncher,
                mLauncher.getString(R.string.group_applications));
        groups[GROUP_SHORTCUTS] = new Group(mLauncher.getString(R.string.group_shortcuts));

        if (!forFolder) {
            groups[GROUP_WALLPAPERS] = new Group(mLauncher.getString(R.string.group_wallpapers));
            groups[GROUP_SHORTCUTS].add(new CreateFolderAction(launcher));
            groups[GROUP_WIDGETS] = new Group(mLauncher.getString(R.string.group_widgets));
            final Group widgets = groups[GROUP_WIDGETS];
            widgets.add(new CreateClockAction(launcher));
            widgets.add(new CreatePhotoFrameAction(launcher));
            widgets.add(new CreateSearchAction(launcher));
        }
        
        PackageManager packageManager = launcher.getPackageManager();

        List<ResolveInfo> list = findTargetsForIntent(mCreateShortcutIntent, packageManager);
        if (list != null && list.size() > 0) {
            int count = list.size();
            final Group shortcuts = groups[GROUP_SHORTCUTS];
            for (int i = 0; i < count; i++) {
                ResolveInfo resolveInfo = list.get(i);
                shortcuts.add(new CreateShortcutAction(launcher, resolveInfo));
            }
        }

        list = findTargetsForIntent(mSetWallpaperIntent, packageManager);
        if (list != null && list.size() > 0) {
            int count = list.size();
            final Group shortcuts = groups[GROUP_WALLPAPERS];
            for (int i = 0; i < count; i++) {
                ResolveInfo resolveInfo = list.get(i);
                shortcuts.add(new SetWallpaperAction(launcher, resolveInfo));
            }
        }
    }

    private List<ResolveInfo> findTargetsForIntent(Intent intent, PackageManager packageManager) {
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (list != null) {
            int count = list.size();
            if (count > 1) {
                // Only display the first matches that are either of equal
                // priority or have asked to be default options.
                ResolveInfo firstInfo = list.get(0);
                for (int i=1; i<count; i++) {
                    ResolveInfo resolveInfo = list.get(i);
                    if (firstInfo.priority != resolveInfo.priority ||
                        firstInfo.isDefault != resolveInfo.isDefault) {
                        while (i < count) {
                            list.remove(i);
                            count--;
                        }
                    }
                }
                Collections.sort(list, new ResolveInfo.DisplayNameComparator(packageManager));
            }
        }
        return list;
    }

    public int getGroupCount() {
        return mGroups.length;
    }

    public int getChildrenCount(int groupPosition) {
        return mGroups[groupPosition].size();
    }

    public Object getGroup(int groupPosition) {
        return mGroups[groupPosition].getName();
    }

    public Object getChild(int groupPosition, int childPosition) {
        return mGroups[groupPosition].get(childPosition);
    }

    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    public long getChildId(int groupPosition, int childPosition) {
        return (groupPosition << 16) | childPosition;
    }

    public boolean hasStableIds() {
        return true;
    }

    public View getGroupView(int groupPosition, boolean isExpanded,
            View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = mInflater.inflate(R.layout.create_shortcut_group_item, parent, false);
        } else {
            view = convertView;
        }
        ((TextView) view).setText(mGroups[groupPosition].getName());
        return view;
    }

    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
            View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = mInflater.inflate(R.layout.create_shortcut_list_item, parent, false);
        } else {
            view = convertView;
        }
        mGroups[groupPosition].bindView(childPosition, view);
        return view;
    }

    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    void performAction(int groupPosition, int childPosition) {
        mGroups[groupPosition].run(childPosition);
    }
}
