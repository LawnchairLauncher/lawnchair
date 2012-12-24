package com.cyanogenmod.trebuchet;

import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.List;

public class LauncherAction {

    public enum Action {
        AllApps;
        int getString() {
            return getString(this);
        }
        int getDrawable() {
            return getDrawable(this);
        }
        static int getString(Action action) {
            switch (action) {
                case AllApps:
                    return R.string.all_apps_button_label;
                default:
                    return -1;
            }
        }
        static int getDrawable(Action action) {
            switch (action) {
                case AllApps:
                    return R.drawable.ic_allapps;
                default:
                    return -1;
            }
        }
    }

    public static List<LauncherActionInfo> getAllActions(Context context) {
        List<LauncherActionInfo> actions = new ArrayList<LauncherActionInfo>();

        final Resources res = context.getResources();

        LauncherActionInfo allAppsAction = new LauncherActionInfo();
        allAppsAction.action = Action.AllApps;
        allAppsAction.drawable = Action.getDrawable(Action.AllApps);
        allAppsAction.title = res.getString(Action.getString(Action.AllApps));
        actions.add(allAppsAction);

        return actions;
    }
}
