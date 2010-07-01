package com.android.launcher2;

import com.android.launcher.R;

import android.content.Context;
import android.content.pm.ResolveInfo;

public class FolderListAdapter extends IntentListAdapter {

    public FolderListAdapter(Context context, String actionFilter) {
        super(context, actionFilter);

        // Manually create a separate entry for creating a folder in Launcher
        ResolveInfo folder = new ResolveInfo();
        folder.icon = R.drawable.ic_launcher_folder;
        folder.labelRes = R.string.group_folder;
        folder.resolvePackageName = context.getPackageName();
        mIntentList.add(0, folder);
    }
}
