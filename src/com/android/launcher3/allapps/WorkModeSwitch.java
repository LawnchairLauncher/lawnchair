/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.util.PackageManagerHelper.hasShortcutsPermission;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.widget.Switch;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.views.ArrowTipView;

import java.lang.ref.WeakReference;

/**
 * Work profile toggle switch shown at the bottom of AllApps work tab
 */
public class WorkModeSwitch extends Switch implements Insettable {

    private static final int WORK_TIP_THRESHOLD = 2;
    public static final String KEY_WORK_TIP_COUNTER = "worked_tip_counter";

    private Rect mInsets = new Rect();

    public WorkModeSwitch(Context context) {
        super(context);
    }

    public WorkModeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WorkModeSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setChecked(boolean checked) {

    }

    @Override
    public void toggle() {
        Launcher launcher = Launcher.getLauncher(getContext());
        // don't show tip if user uses toggle
        launcher.getSharedPrefs().edit().putInt(KEY_WORK_TIP_COUNTER, -1).apply();
        trySetQuietModeEnabledToAllProfilesAsync(isChecked());
    }

    private void setCheckedInternal(boolean checked) {
        super.setChecked(checked);
        setCompoundDrawablesRelativeWithIntrinsicBounds(
                checked ? R.drawable.ic_corp : R.drawable.ic_corp_off, 0, 0, 0);
    }

    public void refresh() {
        if (!shouldShowWorkSwitch()) return;
        UserCache userManager = UserCache.INSTANCE.get(getContext());
        setCheckedInternal(!userManager.isAnyProfileQuietModeEnabled());
        setEnabled(true);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        this.setVisibility(shouldShowWorkSwitch() ? VISIBLE : GONE);
    }

    private void trySetQuietModeEnabledToAllProfilesAsync(boolean enabled) {
        new SetQuietModeEnabledAsyncTask(enabled, new WeakReference<>(this)).execute();
    }

    @Override
    public void setInsets(Rect insets) {
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
                getPaddingBottom() + bottomInset);
    }

    /**
     * Animates in/out work profile toggle panel based on the tab user is on
     */
    public void setWorkTabVisible(boolean workTabVisible) {
        if (!shouldShowWorkSwitch()) return;
        clearAnimation();
        if (workTabVisible) {
            setVisibility(VISIBLE);
            setAlpha(0);
            animate().alpha(1).start();
            showTipifNeeded();
        } else {
            animate().alpha(0).withEndAction(() -> this.setVisibility(GONE)).start();
        }
    }

    private static final class SetQuietModeEnabledAsyncTask
            extends AsyncTask<Void, Void, Boolean> {

        private final boolean enabled;
        private final WeakReference<WorkModeSwitch> switchWeakReference;

        SetQuietModeEnabledAsyncTask(boolean enabled,
                                     WeakReference<WorkModeSwitch> switchWeakReference) {
            this.enabled = enabled;
            this.switchWeakReference = switchWeakReference;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            WorkModeSwitch workModeSwitch = switchWeakReference.get();
            if (workModeSwitch != null) {
                workModeSwitch.setEnabled(false);
            }
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            WorkModeSwitch workModeSwitch = switchWeakReference.get();
            if (workModeSwitch == null || !Utilities.ATLEAST_P) {
                return false;
            }

            Context context = workModeSwitch.getContext();
            UserManager userManager = context.getSystemService(UserManager.class);
            boolean showConfirm = false;
            for (UserHandle userProfile : UserCache.INSTANCE.get(context).getUserProfiles()) {
                if (Process.myUserHandle().equals(userProfile)) {
                    continue;
                }
                showConfirm |= !userManager.requestQuietModeEnabled(enabled, userProfile);
            }
            return showConfirm;
        }

        @Override
        protected void onPostExecute(Boolean showConfirm) {
            if (showConfirm) {
                WorkModeSwitch workModeSwitch = switchWeakReference.get();
                if (workModeSwitch != null) {
                    workModeSwitch.setEnabled(true);
                }
            }
        }
    }

    private boolean shouldShowWorkSwitch() {
        Launcher launcher = Launcher.getLauncher(getContext());
        return Utilities.ATLEAST_P && (hasShortcutsPermission(launcher)
                || launcher.checkSelfPermission("android.permission.MODIFY_QUIET_MODE")
                == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Shows a work tip on the Nth work tab open
     */
    public void showTipifNeeded() {
        Launcher launcher = Launcher.getLauncher(getContext());
        int tipCounter = launcher.getSharedPrefs().getInt(KEY_WORK_TIP_COUNTER, WORK_TIP_THRESHOLD);
        if (tipCounter < 0) return;
        if (tipCounter == 0) {
            new ArrowTipView(launcher).show(launcher.getString(R.string.work_switch_tip), getTop());
        }
        launcher.getSharedPrefs().edit().putInt(KEY_WORK_TIP_COUNTER, tipCounter - 1).apply();
    }
}
