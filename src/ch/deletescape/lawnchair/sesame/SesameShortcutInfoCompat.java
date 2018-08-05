package ch.deletescape.lawnchair.sesame;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.util.Log;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.R;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

import ch.deletescape.lawnchair.sesame.SesameDataProvider;

public class SesameShortcutInfoCompat extends ShortcutInfoCompat {

    private SesameDataProvider.SesameResult sesameResult;

    public SesameShortcutInfoCompat(SesameDataProvider.SesameResult sesameResult) {
        super(null);
        this.sesameResult = sesameResult;
    }

    @TargetApi(Build.VERSION_CODES.N)
    public Intent makeIntent() {
        try {
            return Intent
                    // TODO: use #getIntent() to relay through sesame once we get access to that
                    .parseUri(sesameResult.getDirectIntent().toString(),0)
                    .setPackage(getPackage())
                    .putExtra("ch.deletescape.lawnchair.SESAME_MARKER", true)
                    .putExtra(ShortcutInfoCompat.EXTRA_SHORTCUT_ID, getId());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public Drawable getIcon(Context context, int density) {
        if(sesameResult.getIconUri() != null){
            String scheme = sesameResult.getIconUri().getScheme();
            if(scheme != null && scheme.startsWith("http")){
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream((InputStream)new URL(sesameResult.getIconUri().toString()).getContent());
                    if (bitmap != null) return new FastBitmapDrawable(bitmap);
                } catch (IOException ignored) {}
            } else {
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(sesameResult.getIconUri()));
                    if (bitmap != null) return new FastBitmapDrawable(bitmap);
                } catch (FileNotFoundException ignored) {}
            }
        }
        if (sesameResult.getComponentName() != null){
            try {
                return context.getPackageManager().getActivityIcon(sesameResult.getComponentName());
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        if (sesameResult.getPackageName() != null) {
            try {
                return context.getPackageManager().getApplicationIcon(sesameResult.getPackageName());
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return context.getResources().getDrawableForDensity(R.drawable.ic_default_shortcut, density);
    }

    public String getPackage() {
        if(sesameResult.getPackageName() != null){
            return sesameResult.getPackageName();
        } else {
            return "ninja.sesame.app.edge";
        }
    }

    public String getId() {
        return sesameResult.getUri();
    }

    public CharSequence getShortLabel() {
        return sesameResult.getDisplayLabel();
    }

    public CharSequence getLongLabel() {
        return sesameResult.getDisplayLabel();
    }

    public long getLastChangedTimestamp() {
        return System.currentTimeMillis();
    }

    public ComponentName getActivity() {
        if(sesameResult.getComponentName() != null) {
            return sesameResult.getComponentName();
        } else {
            return new ComponentName(getPackage(), getId());
        }
    }

    public UserHandle getUserHandle() {
        return Process.myUserHandle();
    }

    public boolean hasKeyFieldsOnly() {
        return true;
    }

    public boolean isPinned() {
        return false;
    }

    public boolean isDeclaredInManifest() {
        return false;
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean isDynamic() {
        return true;
    }

    public int getRank() {
        return 1;
    }

    public CharSequence getDisabledMessage() {
        return "";
    }

    @Override
    public String toString() {
        return "";
    }
}
