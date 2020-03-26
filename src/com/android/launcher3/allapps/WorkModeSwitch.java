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

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Switch;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.pm.UserCache;

import java.lang.ref.WeakReference;

/**
 * Work profile toggle switch shown at the bottom of AllApps work tab
 */
public class WorkModeSwitch extends Switch implements Insettable {

    private Rect mInsets = new Rect();
    protected ObjectAnimator mOpenCloseAnimator;


    public WorkModeSwitch(Context context) {
        super(context);
        init();
    }

    public WorkModeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WorkModeSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mOpenCloseAnimator = ObjectAnimator.ofPropertyValuesHolder(this);
    }

    @Override
    public void setChecked(boolean checked) {

    }

    @Override
    public void toggle() {
        trySetQuietModeEnabledToAllProfilesAsync(isChecked());
    }

    private void setCheckedInternal(boolean checked) {
        super.setChecked(checked);
        setCompoundDrawablesWithIntrinsicBounds(
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

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return ev.getActionMasked() == MotionEvent.ACTION_MOVE || super.onTouchEvent(ev);
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

        mOpenCloseAnimator.setValues(PropertyValuesHolder.ofFloat(ALPHA, workTabVisible ? 1 : 0));
        mOpenCloseAnimator.start();
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
}
