package ch.deletescape.lawnchair.shortcuts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.compat.UserHandleCompat;

public class ShortcutsCompat {
    public static List<ShortcutInfoCompat> query(ComponentName component, UserHandleCompat user){
            Context context = LauncherAppState.getInstance().getContext();
            PackageManager pm = context.getPackageManager();
            List<ActivityInfo> activities;
            if(component == null){
                activities = new ArrayList<>();
                Intent intent = new Intent("android.intent.action.MAIN");
                intent.addCategory("android.intent.category.LAUNCHER");
                for(ResolveInfo info : pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)){
                    activities.add(info.activityInfo);
                }

            } else {
                try {
                    activities = Collections.singletonList(pm.getActivityInfo(component, PackageManager.GET_META_DATA));
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                    activities = Collections.EMPTY_LIST;
                }
            }
            for(ActivityInfo activity : activities){
                try {
                    int resourceId = activity.metaData.getInt("android.app.shortcuts");
                    if(resourceId != 0){
                        Toast.makeText(context, "resId: "+resourceId,Toast.LENGTH_SHORT).show();
                    }
                } catch (NullPointerException ignored){
                }
            }

        return Collections.EMPTY_LIST;
    }
}
