package com.google.android.apps.nexuslauncher.smartspace;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.graphics.LauncherIcons;
import com.google.android.apps.nexuslauncher.smartspace.nano.SmartspaceProto.*;

public class NewCardInfo {
    public final b di;
    public final boolean dj;
    public final PackageInfo dk;
    public final long dl;
    public final Intent intent;

    public NewCardInfo(b di, Intent intent, boolean dj, long dl, PackageInfo dk) {
        this.di = di;
        this.dj = dj;
        this.intent = intent;
        this.dl = dl;
        this.dk = dk;
    }

    private static Object getParcelableExtra(String name, Intent intent) {
        if (!TextUtils.isEmpty(name)) {
            return intent.getParcelableExtra(name);
        }
        return null;
    }

    public Bitmap getBitmap(final Context context) {
        com.google.android.apps.nexuslauncher.smartspace.nano.SmartspaceProto.f fVar = this.di.cx;
        if (fVar == null) {
            return null;
        }
        Bitmap bitmap = (Bitmap) getParcelableExtra(fVar.cV, this.intent);
        if (bitmap != null) {
            return bitmap;
        }
        try {
            if (TextUtils.isEmpty(fVar.cW)) {
                if (!TextUtils.isEmpty(fVar.cX)) {
                    Resources resourcesForApplication = context.getPackageManager().getResourcesForApplication("com.google.android.googlequicksearchbox");
                    return LauncherIcons.createIconBitmap(resourcesForApplication.getDrawableForDensity(resourcesForApplication.getIdentifier(fVar.cX, null, null), LauncherAppState.getIDP(context).fillResIconDpi), context);
                }
                return null;
            }
            return MediaStore.Images.Media.getBitmap(context.getContentResolver(), Uri.parse(fVar.cW));
        } catch (Exception e) {
            Log.e("NewCardInfo", "retrieving bitmap uri=" + fVar.cW + " gsaRes=" + fVar.cX);
            return null;
        }
    }
}
