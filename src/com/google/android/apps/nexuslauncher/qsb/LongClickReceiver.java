package com.google.android.apps.nexuslauncher.qsb;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.os.Bundle;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.ComponentKey;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;
import com.google.android.apps.nexuslauncher.search.AppSearchProvider;

import java.lang.ref.WeakReference;

public class LongClickReceiver extends BroadcastReceiver {
    private static WeakReference<NexusLauncherActivity> bR = new WeakReference<>(null);

    public static void bq(final NexusLauncherActivity nexusLauncherActivity) {
        LongClickReceiver.bR = new WeakReference<>(nexusLauncherActivity);
    }

    public void onReceive(final Context context, final Intent intent) {
        final NexusLauncherActivity launcher = LongClickReceiver.bR.get();
        if (launcher != null) {
            final ComponentKey dl = AppSearchProvider.uriToComponent(intent.getData(), context);
            final LauncherActivityInfo resolveActivity = LauncherAppsCompat.getInstance(context).resolveActivity(new Intent(Intent.ACTION_MAIN).setComponent(dl.componentName), dl.user);
            if (resolveActivity == null) {
                return;
            }
            final ItemDragListener onDragListener = new ItemDragListener(resolveActivity, intent.getSourceBounds());
            onDragListener.setLauncher(launcher);
            launcher.showWorkspace(false);
            launcher.getDragLayer().setOnDragListener(onDragListener);
            final ClipData clipData = new ClipData(new ClipDescription("", new String[] { onDragListener.getMimeType() }), new ClipData.Item(""));
            final Bundle bundle = new Bundle();
            bundle.putParcelable("clip_data", clipData);
            this.setResult(-1, null, bundle);
        }
    }
}
