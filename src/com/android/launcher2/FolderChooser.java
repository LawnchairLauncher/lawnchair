package com.android.launcher2;

import com.android.launcher.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.LiveFolders;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;

public class FolderChooser extends HomeCustomizationItemGallery {

    public FolderChooser(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // todo: this code sorta overlaps with other places
        ResolveInfo info = (ResolveInfo)getAdapter().getItem(position);
        mLauncher.prepareAddItemFromHomeCustomizationDrawer();

        Intent createFolderIntent = new Intent(LiveFolders.ACTION_CREATE_LIVE_FOLDER);
        if (info.labelRes == R.string.group_folder) {
            // Create app shortcuts is a special built-in case of shortcuts
            createFolderIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getContext().getString(R.string.group_folder));
        } else {
            ComponentName name = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            createFolderIntent.setComponent(name);
        }
        mLauncher.addLiveFolder(createFolderIntent);

        return true;
    }
}
