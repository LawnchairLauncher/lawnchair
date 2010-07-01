package com.android.launcher2;

import com.android.launcher.R;

import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;


public class ShortcutListAdapter extends IntentListAdapter {

    public ShortcutListAdapter(Context context, String actionFilter) {
        super(context, actionFilter);

        // Manually create a separate entry for creating an Application shortcut
        ResolveInfo folder = new ResolveInfo();

        folder.icon = R.drawable.ic_launcher_application;
        folder.labelRes = R.string.group_applications;
        folder.resolvePackageName = context.getPackageName();
        mIntentList.add(0, folder);
    }
}
