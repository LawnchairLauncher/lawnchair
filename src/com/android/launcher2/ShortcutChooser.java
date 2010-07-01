package com.android.launcher2;

import com.android.launcher.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;

public class ShortcutChooser extends HomeCustomizationItemGallery {

    public ShortcutChooser(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // todo: this code sorta overlaps with other places
        ResolveInfo info = (ResolveInfo)getAdapter().getItem(position);
        mLauncher.prepareAddItemFromHomeCustomizationDrawer();

        Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        if (info.labelRes == R.string.group_applications) {
            // Create app shortcuts is a special built-in case of shortcuts
            createShortcutIntent.putExtra(
                    Intent.EXTRA_SHORTCUT_NAME,getContext().getString(R.string.group_applications));
        } else {
            ComponentName name = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            createShortcutIntent.setComponent(name);
        }
        mLauncher.processShortcut(createShortcutIntent);

        return true;
    }
}
