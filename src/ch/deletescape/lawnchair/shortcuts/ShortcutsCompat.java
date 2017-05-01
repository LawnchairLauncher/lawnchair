package ch.deletescape.lawnchair.shortcuts;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

import ch.deletescape.lawnchair.LauncherAppState;

public class ShortcutsCompat {
    public static List<ShortcutInfoCompat> query(ComponentName component){
        try {
            Context context = LauncherAppState.getInstance().getContext();
            ActivityInfo ai = context.getPackageManager().getActivityInfo(component, PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            int resourceId = bundle.getInt("android.app.shortcuts");
            //Toast.makeText(context, "resId: "+resourceId,Toast.LENGTH_LONG).show();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("ShortcutsCompat", "Failed to load meta-data, NameNotFound: " + e.getMessage());
        } catch (NullPointerException e) {
            Log.e("ShortcutsCompat", "Failed to load meta-data, NullPointer: " + e.getMessage());
        }

        return Collections.EMPTY_LIST;
    }
}
