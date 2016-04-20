package com.android.launcher3.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.CustomAppWidget;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

import java.util.HashMap;

public class TestingUtils {

    public static final String MEMORY_TRACKER = "com.android.launcher3.testing.MemoryTracker";
    public static final String ACTION_START_TRACKING = "com.android.launcher3.action.START_TRACKING";

    public static final boolean MEMORY_DUMP_ENABLED = false;
    public static final String SHOW_WEIGHT_WATCHER = "debug.show_mem";

    public static final boolean ENABLE_CUSTOM_WIDGET_TEST = false;
    public static final String DUMMY_WIDGET = "com.android.launcher3.testing.DummyWidget";

    public static void startTrackingMemory(Context context) {
        if (MEMORY_DUMP_ENABLED) {
            context.startService(new Intent()
                .setComponent(new ComponentName(context.getPackageName(), MEMORY_TRACKER))
                .setAction(ACTION_START_TRACKING)
                .putExtra("pid", android.os.Process.myPid())
                .putExtra("name", "L"));
        }
    }

    public static void addWeightWatcher(Launcher launcher) {
        if (MEMORY_DUMP_ENABLED) {
            boolean show = Utilities.getPrefs(launcher).getBoolean(SHOW_WEIGHT_WATCHER, true);

            int id = launcher.getResources().getIdentifier("zzz_weight_watcher", "layout",
                    launcher.getPackageName());
            View watcher = launcher.getLayoutInflater().inflate(id, null);
            watcher.setAlpha(0.5f);
            ((FrameLayout) launcher.findViewById(R.id.launcher)).addView(watcher,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM)
            );

            watcher.setVisibility(show ? View.VISIBLE : View.GONE);
            launcher.mWeightWatcher = watcher;
        }
    }

    public static void addDummyWidget(HashMap<String, CustomAppWidget> set) {
        if (ENABLE_CUSTOM_WIDGET_TEST) {
            try {
                Class<?> clazz = Class.forName(DUMMY_WIDGET);
                CustomAppWidget widget = (CustomAppWidget) clazz.newInstance();
                set.put(widget.getClass().getName(), widget);
            } catch (Exception e) {
                Log.e("TestingUtils", "Error adding dummy widget", e);
            }
        }
    }
}
