/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.appprediction;

import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.quickstep.InstantAppResolverImpl.COMPONENT_CLASS_MARKER;

import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import com.android.launcher3.AppInfo;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore.OnUpdateListener;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.MainThreadInitializedObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handler responsible to updating the UI due to predicted apps changes. Operations:
 * 1) Pushes the predicted apps to all-apps. If all-apps is visible, waits until it becomes
 * invisible again before applying the changes. This ensures that the UI does not change abruptly
 * in front of the user, even if an app launched and user pressed back button to return to the
 * all-apps UI again.
 * 2) Prefetch high-res icons for predicted apps. This ensures that we have the icons in memory
 * even if all-apps is not opened as they are shown in search UI as well
 * 3) Load instant app if it is not already in memory. As predictions are persisted on disk,
 * instant app will not be in memory when launcher starts.
 * 4) Maintains the current active client id (for the predictions) and all updates are performed on
 * that client id.
 */
public class PredictionUiStateManager implements OnGlobalLayoutListener, ItemInfoUpdateReceiver,
        OnSharedPreferenceChangeListener, OnIDPChangeListener, OnUpdateListener {

    public static final String KEY_APP_SUGGESTION = "pref_show_predictions";

    // TODO (b/129421797): Update the client constants
    public enum Client {
        HOME("GEL"),
        OVERVIEW("OVERVIEW_GEL");

        public final String id;

        Client(String id) {
            this.id = id;
        }
    }

    public static final MainThreadInitializedObject<PredictionUiStateManager> INSTANCE =
            new MainThreadInitializedObject<>(PredictionUiStateManager::new);

    private final Context mContext;
    private final SharedPreferences mMainPrefs;

    private final DynamicItemCache mDynamicItemCache;
    private final List[] mPredictionServicePredictions;

    private int mMaxIconsPerRow;
    private Client mActiveClient;

    private AllAppsContainerView mAppsView;

    private PredictionState mPendingState;
    private PredictionState mCurrentState;

    private PredictionUiStateManager(Context context) {
        mContext = context;
        mMainPrefs = Utilities.getPrefs(context);

        mDynamicItemCache = new DynamicItemCache(context, this::onAppsUpdated);

        mActiveClient = Client.HOME;

        InvariantDeviceProfile idp = LauncherAppState.getIDP(context);
        mMaxIconsPerRow = idp.numColumns;

        idp.addOnChangeListener(this);
        mPredictionServicePredictions = new List[Client.values().length];
        for (int i = 0; i < mPredictionServicePredictions.length; i++) {
            mPredictionServicePredictions[i] = Collections.emptyList();
        }
        // Listens for enable/disable signal, and predictions if using AiAi is disabled.
        mMainPrefs.registerOnSharedPreferenceChangeListener(this);
        // Call this last
        mCurrentState = parseLastState();
    }

    @Override
    public void onIdpChanged(int changeFlags, InvariantDeviceProfile profile) {
        mMaxIconsPerRow = profile.numColumns;
    }

    public Client getClient() {
        return mActiveClient;
    }

    public void switchClient(Client client) {
        if (client == mActiveClient) {
            return;
        }
        mActiveClient = client;
        dispatchOnChange(true);
    }

    public void setTargetAppsView(AllAppsContainerView appsView) {
        if (mAppsView != null) {
            mAppsView.getAppsStore().removeUpdateListener(this);
        }
        mAppsView = appsView;
        if (mAppsView != null) {
            mAppsView.getAppsStore().addUpdateListener(this);
        }
        if (mPendingState != null) {
            applyState(mPendingState);
            mPendingState = null;
        } else {
            applyState(mCurrentState);
        }
        updateDependencies(mCurrentState);
    }

    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) { }

    @Override
    public void onGlobalLayout() {
        if (mAppsView == null) {
            return;
        }
        if (mPendingState != null && canApplyPredictions(mPendingState)) {
            applyState(mPendingState);
            mPendingState = null;
        }
        if (mPendingState == null) {
            mAppsView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }

    private void scheduleApplyPredictedApps(PredictionState state) {
        boolean registerListener = mPendingState == null;
        mPendingState = state;
        if (registerListener) {
            // OnGlobalLayoutListener is called whenever a view in the view tree changes
            // visibility. Add a listener and wait until appsView is invisible again.
            mAppsView.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (KEY_APP_SUGGESTION.equals(key)) {
            dispatchOnChange(true);
        }
    }

    private void applyState(PredictionState state) {
        boolean wasEnabled = mCurrentState.isEnabled;
        mCurrentState = state;
        if (mAppsView != null) {
            mAppsView.getFloatingHeaderView().findFixedRowByType(PredictionRowView.class)
                    .setPredictedApps(mCurrentState.isEnabled, mCurrentState.apps);

            if (wasEnabled != mCurrentState.isEnabled) {
                // Reapply state as the State UI might have changed.
                Launcher.getLauncher(mAppsView.getContext()).getStateManager().reapplyState(true);
            }
        }
    }

    public AppPredictor.Callback appPredictorCallback(Client client) {
        return targets -> {
            mPredictionServicePredictions[client.ordinal()] = targets;
            dispatchOnChange(true);
        };
    }

    private void dispatchOnChange(boolean changed) {
        PredictionState newState = changed ? parseLastState() :
                (mPendingState == null ? mCurrentState : mPendingState);
        if (changed && mAppsView != null && !canApplyPredictions(newState)) {
            scheduleApplyPredictedApps(newState);
        } else {
            applyState(newState);
        }
    }

    private PredictionState parseLastState() {
        PredictionState state = new PredictionState();
        state.isEnabled = mMainPrefs.getBoolean(KEY_APP_SUGGESTION, true);
        if (!state.isEnabled) {
            state.apps = Collections.EMPTY_LIST;
            return state;
        }

        state.apps = new ArrayList<>();

        List<AppTarget> appTargets = mPredictionServicePredictions[mActiveClient.ordinal()];
        if (!appTargets.isEmpty()) {
            for (AppTarget appTarget : appTargets) {
                ComponentKey key;
                if (appTarget.getShortcutInfo() != null) {
                    key = ShortcutKey.fromInfo(appTarget.getShortcutInfo());
                } else {
                    key = new ComponentKey(new ComponentName(appTarget.getPackageName(),
                            appTarget.getClassName()), appTarget.getUser());
                }
                state.apps.add(new ComponentKeyMapper(mContext, key, mDynamicItemCache));
            }
        }
        updateDependencies(state);
        return state;
    }

    private void updateDependencies(PredictionState state) {
        if (!state.isEnabled || mAppsView == null) {
            return;
        }

        IconCache iconCache = LauncherAppState.getInstance(mContext).getIconCache();
        List<String> instantAppsToLoad = new ArrayList<>();
        List<ShortcutKey> shortcutsToLoad = new ArrayList<>();
        int total = state.apps.size();
        for (int i = 0, count = 0; i < total && count < mMaxIconsPerRow; i++) {
            ComponentKeyMapper mapper = state.apps.get(i);
            // Update instant apps
            if (COMPONENT_CLASS_MARKER.equals(mapper.getComponentClass())) {
                instantAppsToLoad.add(mapper.getPackage());
                count++;
            } else if (mapper.getComponentKey() instanceof ShortcutKey) {
                shortcutsToLoad.add((ShortcutKey) mapper.getComponentKey());
                count++;
            } else {
                // Reload high res icon
                AppInfo info = (AppInfo) mapper.getApp(mAppsView.getAppsStore());
                if (info != null) {
                    if (info.usingLowResIcon()) {
                        // TODO: Update icon cache to support null callbacks.
                        iconCache.updateIconInBackground(this, info);
                    }
                    count++;
                }
            }
        }
        mDynamicItemCache.cacheItems(shortcutsToLoad, instantAppsToLoad);
    }

    @Override
    public void onAppsUpdated() {
        dispatchOnChange(false);
    }

    public boolean arePredictionsEnabled() {
        return mCurrentState.isEnabled;
    }

    private boolean canApplyPredictions(PredictionState newState) {
        if (mAppsView == null) {
            // If there is no apps view, no need to schedule.
            return true;
        }
        Launcher launcher = Launcher.getLauncher(mAppsView.getContext());
        PredictionRowView predictionRow = mAppsView.getFloatingHeaderView().
                findFixedRowByType(PredictionRowView.class);
        if (!predictionRow.isShown() || predictionRow.getAlpha() == 0 ||
                launcher.isForceInvisible()) {
            return true;
        }

        if (mCurrentState.isEnabled != newState.isEnabled
                || mCurrentState.apps.isEmpty() != newState.apps.isEmpty()) {
            // If the visibility of the prediction row is changing, apply immediately.
            return true;
        }

        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            // If we are here & mAppsView.isShown() = true, we are probably in all-apps or mid way
            return false;
        }
        if (!launcher.isInState(OVERVIEW) && !launcher.isInState(BACKGROUND_APP)) {
            // Just a fallback as we dont need to apply instantly, if we are not in the swipe-up UI
            return false;
        }

        // Instead of checking against 1, we should check against (1 + delta), where delta accounts
        // for the nav-bar height (as app icon can still be visible under the nav-bar). Checking
        // against 1, keeps the logic simple :)
        return launcher.getAllAppsController().getProgress() > 1;
    }

    public PredictionState getCurrentState() {
        return mCurrentState;
    }

    public static class PredictionState {

        public boolean isEnabled;
        public List<ComponentKeyMapper> apps;
    }
}
