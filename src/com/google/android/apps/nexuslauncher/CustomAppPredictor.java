package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.AppFilter;
import com.android.launcher3.AppInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ComponentKeyMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomAppPredictor extends UserEventDispatcher implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int MAX_PREDICTIONS = 10;
    private static final int BOOST_ON_OPEN = 9;
    private static final String PREDICTION_SET = "pref_prediction_set";
    private static final String PREDICTION_PREFIX = "pref_prediction_count_";
    private static final Set<String> EMPTY_SET = new HashSet<>();
    private final Context mContext;
    private final AppFilter mAppFilter;
    private final SharedPreferences mPrefs;
    private final PackageManager mPackageManager;

    private final static String[] PLACE_HOLDERS = new String[] {
            "com.google.android.apps.photos",
            "com.google.android.apps.maps",
            "com.google.android.gm",
            "com.google.android.deskclock",
            "com.android.settings",
            "com.whatsapp",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.google.android.youtube",
            "com.yodo1.crossyroad",
            "com.spotify.music",
            "com.android.chrome",
            "com.instagram.android",
            "com.skype.raider",
            "com.snapchat.android",
            "com.viber.voip",
            "com.twitter.android",
            "com.android.phone",
            "com.google.android.music",
            "com.google.android.calendar",
            "com.google.android.apps.genie.geniewidget",
            "com.netflix.mediaclient",
            "bbc.iplayer.android",
            "com.google.android.videos",
            "com.amazon.mShop.android.shopping",
            "com.microsoft.office.word",
            "com.google.android.apps.docs",
            "com.google.android.keep",
            "com.google.android.apps.plus",
            "com.google.android.talk"
    };

    public CustomAppPredictor(Context context) {
        mContext = context;
        mAppFilter = AppFilter.newInstance(mContext);
        mPrefs = Utilities.getPrefs(context);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        mPackageManager = context.getPackageManager();
    }

    List<ComponentKeyMapper<AppInfo>> getPredictions() {
        List<ComponentKeyMapper<AppInfo>> list = new ArrayList<>();
        if (isPredictorEnabled()) {
            clearNonExistentPackages();

            List<String> predictionList = new ArrayList<>(getStringSetCopy());

            Collections.sort(predictionList, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return Integer.compare(getLaunchCount(o2), getLaunchCount(o1));
                }
            });

            for (String prediction : predictionList) {
                list.add(getComponentFromString(prediction));
            }

            for (int i = 0; i < PLACE_HOLDERS.length && list.size() < MAX_PREDICTIONS; i++) {
                String placeHolder = PLACE_HOLDERS[i];
                Intent intent = mPackageManager.getLaunchIntentForPackage(placeHolder);
                if (intent != null) {
                    ComponentName componentInfo = intent.getComponent();
                    if (componentInfo != null) {
                        ComponentKey key = new ComponentKey(componentInfo, Process.myUserHandle());
                        if (!predictionList.contains(key.toString())) {
                            list.add(new ComponentKeyMapper<AppInfo>(key));
                        }
                    }
                }
            }
        }
        return list;
    }

    @Override
    public void logAppLaunch(View v, Intent intent, UserHandle user) {
        super.logAppLaunch(v, intent, user);
        if (isPredictorEnabled() && recursiveIsDrawer(v)) {
            ComponentName componentInfo = intent.getComponent();
            if (componentInfo != null && mAppFilter.shouldShowApp(componentInfo, user)) {
                clearNonExistentPackages();
                
                Set<String> predictionSet = getStringSetCopy();
                SharedPreferences.Editor edit = mPrefs.edit();

                String prediction = new ComponentKey(componentInfo, user).toString();
                if (predictionSet.contains(prediction)) {
                    edit.putInt(PREDICTION_PREFIX + prediction, getLaunchCount(prediction) + BOOST_ON_OPEN);
                } else if (predictionSet.size() < MAX_PREDICTIONS || decayHasSpotFree(predictionSet, edit)) {
                    predictionSet.add(prediction);
                }

                edit.putStringSet(PREDICTION_SET, predictionSet);
                edit.apply();
            }
        }
    }

    private boolean decayHasSpotFree(Set<String> toDecay, SharedPreferences.Editor edit) {
        boolean spotFree = false;
        Set<String> toRemove = new HashSet<>();
        for (String prediction : toDecay) {
            int launchCount = getLaunchCount(prediction);
            if (launchCount > 0) {
                edit.putInt(PREDICTION_PREFIX + prediction, --launchCount);
            } else if (!spotFree) {
                edit.remove(PREDICTION_PREFIX + prediction);
                toRemove.add(prediction);
                spotFree = true;
            }
        }
        for (String prediction : toRemove) {
            toDecay.remove(prediction);
        }
        return spotFree;
    }

    /**
     * Zero-based launch count of a shortcut
     * @param component serialized component
     * @return the number of launches, at least zero
     */
    private int getLaunchCount(String component) {
        return mPrefs.getInt(PREDICTION_PREFIX + component, 0);
    }

    private boolean recursiveIsDrawer(View v) {
        if (v != null) {
            ViewParent parent = v.getParent();
            while (parent != null) {
                if (parent instanceof AllAppsContainerView) {
                    return true;
                }
                parent = parent.getParent();
            }
        }
        return false;
    }

    private boolean isPredictorEnabled() {
        return Utilities.getPrefs(mContext).getBoolean(SettingsActivity.SHOW_PREDICTIONS_PREF, true);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SettingsActivity.SHOW_PREDICTIONS_PREF) && !isPredictorEnabled()) {
            Set<String> predictionSet = getStringSetCopy();

            SharedPreferences.Editor edit = mPrefs.edit();
            for (String prediction : predictionSet) {
                Log.i("Predictor", "Clearing " + prediction + " at " + getLaunchCount(prediction));
                edit.remove(PREDICTION_PREFIX + prediction);
            }
            edit.putStringSet(PREDICTION_SET, EMPTY_SET);
            edit.apply();
        }
    }

    private ComponentKeyMapper<AppInfo> getComponentFromString(String str) {
        return new ComponentKeyMapper<>(new ComponentKey(mContext, str));
    }

    private void clearNonExistentPackages() {
        Set<String> originalSet = mPrefs.getStringSet(PREDICTION_SET, EMPTY_SET);
        Set<String> predictionSet = new HashSet<>(originalSet);

        SharedPreferences.Editor edit = mPrefs.edit();
        for (String prediction : originalSet) {
            try {
                mPackageManager.getPackageInfo(new ComponentKey(mContext, prediction).componentName.getPackageName(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                predictionSet.remove(prediction);
                edit.remove(PREDICTION_PREFIX + prediction);
            }
        }

        edit.putStringSet(PREDICTION_SET, predictionSet);
        edit.apply();
    }

    private Set<String> getStringSetCopy() {
        Set<String> set = new HashSet<>();
        set.addAll(mPrefs.getStringSet(PREDICTION_SET, EMPTY_SET));
        return set;
    }
}
