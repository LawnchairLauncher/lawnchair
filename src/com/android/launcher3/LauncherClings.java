/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityManager;

import com.android.launcher3.util.Thunk;

class LauncherClings implements OnClickListener {
    private static final String MIGRATION_CLING_DISMISSED_KEY = "cling_gel.migration.dismissed";
    private static final String WORKSPACE_CLING_DISMISSED_KEY = "cling_gel.workspace.dismissed";

    private static final String TAG_CROP_TOP_AND_SIDES = "crop_bg_top_and_sides";

    private static final int SHOW_CLING_DURATION = 250;
    private static final int DISMISS_CLING_DURATION = 200;

    // New Secure Setting in L
    private static final String SKIP_FIRST_USE_HINTS = "skip_first_use_hints";

    @Thunk Launcher mLauncher;
    private LayoutInflater mInflater;
    @Thunk boolean mIsVisible;

    /** Ctor */
    public LauncherClings(Launcher launcher) {
        mLauncher = launcher;
        mInflater = LayoutInflater.from(mLauncher);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.cling_dismiss_migration_use_default) {
            // Disable the migration cling
            dismissMigrationCling();
        } else if (id == R.id.cling_dismiss_migration_copy_apps) {
            // Copy the shortcuts from the old database
            LauncherModel model = mLauncher.getModel();
            model.resetLoadedState(false, true);
            model.startLoader(PagedView.INVALID_RESTORE_PAGE,
                    LauncherModel.LOADER_FLAG_CLEAR_WORKSPACE
                            | LauncherModel.LOADER_FLAG_MIGRATE_SHORTCUTS);
            // Set the flag to skip the folder cling
            SharedPreferences.Editor editor = Utilities.getPrefs(mLauncher).edit();
            editor.putBoolean(Launcher.USER_HAS_MIGRATED, true);
            editor.apply();
            // Disable the migration cling
            dismissMigrationCling();
        } else if (id == R.id.cling_dismiss_longpress_info) {
            dismissLongPressCling();
        }
    }

    /**
     * Shows the migration cling.
     *
     * This flow is mutually exclusive with showFirstRunCling, and only runs if this Launcher
     * package was not preinstalled and there exists a db to migrate from.
     */
    public void showMigrationCling() {
        mIsVisible = true;
        mLauncher.hideWorkspaceSearchAndHotseat();

        ViewGroup root = (ViewGroup) mLauncher.findViewById(R.id.launcher);
        View inflated = mInflater.inflate(R.layout.migration_cling, root);
        inflated.findViewById(R.id.cling_dismiss_migration_copy_apps).setOnClickListener(this);
        inflated.findViewById(R.id.cling_dismiss_migration_use_default).setOnClickListener(this);
    }

    private void dismissMigrationCling() {
        mLauncher.showWorkspaceSearchAndHotseat();
        Runnable dismissCb = new Runnable() {
            public void run() {
                Runnable cb = new Runnable() {
                    public void run() {
                        // Show the longpress cling next
                        showLongPressCling(false);
                    }
                };
                dismissCling(mLauncher.findViewById(R.id.migration_cling), cb,
                        MIGRATION_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
            }
        };
        mLauncher.getWorkspace().post(dismissCb);
    }

    public void showLongPressCling(boolean showWelcome) {
        mIsVisible = true;
        ViewGroup root = (ViewGroup) mLauncher.findViewById(R.id.launcher);
        View cling = mInflater.inflate(R.layout.longpress_cling, root, false);

        cling.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                mLauncher.showOverviewMode(true);
                dismissLongPressCling();
                return true;
            }
        });

        final ViewGroup content = (ViewGroup) cling.findViewById(R.id.cling_content);
        mInflater.inflate(showWelcome ? R.layout.longpress_cling_welcome_content
                : R.layout.longpress_cling_content, content);
        content.findViewById(R.id.cling_dismiss_longpress_info).setOnClickListener(this);

        if (TAG_CROP_TOP_AND_SIDES.equals(content.getTag())) {
            Drawable bg = new BorderCropDrawable(mLauncher.getResources().getDrawable(R.drawable.cling_bg),
                    true, true, true, false);
            content.setBackground(bg);
        }

        root.addView(cling);

        if (showWelcome) {
            // This is the first cling being shown. No need to animate.
            return;
        }

        // Animate
        content.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                content.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                ObjectAnimator anim;
                if (TAG_CROP_TOP_AND_SIDES.equals(content.getTag())) {
                    content.setTranslationY(-content.getMeasuredHeight());
                    anim = LauncherAnimUtils.ofFloat(content, "translationY", 0);
                } else {
                    content.setScaleX(0);
                    content.setScaleY(0);
                    PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1);
                    PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1);
                    anim = LauncherAnimUtils.ofPropertyValuesHolder(content, scaleX, scaleY);
                }

                anim.setDuration(SHOW_CLING_DURATION);
                anim.setInterpolator(new LogDecelerateInterpolator(100, 0));
                anim.start();
            }
        });
    }

    @Thunk void dismissLongPressCling() {
        Runnable dismissCb = new Runnable() {
            public void run() {
                dismissCling(mLauncher.findViewById(R.id.longpress_cling), null,
                        WORKSPACE_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
            }
        };
        mLauncher.getWorkspace().post(dismissCb);
    }

    /** Hides the specified Cling */
    @Thunk void dismissCling(final View cling, final Runnable postAnimationCb,
                              final String flag, int duration) {
        // To catch cases where siblings of top-level views are made invisible, just check whether
        // the cling is directly set to GONE before dismissing it.
        if (cling != null && cling.getVisibility() != View.GONE) {
            final Runnable cleanUpClingCb = new Runnable() {
                public void run() {
                    cling.setVisibility(View.GONE);
                    mLauncher.getSharedPrefs().edit()
                        .putBoolean(flag, true)
                        .apply();
                    mIsVisible = false;
                    if (postAnimationCb != null) {
                        postAnimationCb.run();
                    }
                }
            };
            if (duration <= 0) {
                cleanUpClingCb.run();
            } else {
                cling.animate().alpha(0).setDuration(duration).withEndAction(cleanUpClingCb);
            }
        }
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    /** Returns whether the clings are enabled or should be shown */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean areClingsEnabled() {
        // disable clings when running in a test harness
        if(ActivityManager.isRunningInTestHarness()) return false;

        // Disable clings for accessibility when explore by touch is enabled
        final AccessibilityManager a11yManager = (AccessibilityManager) mLauncher.getSystemService(
                Launcher.ACCESSIBILITY_SERVICE);
        if (a11yManager.isTouchExplorationEnabled()) {
            return false;
        }

        // Restricted secondary users (child mode) will potentially have very few apps
        // seeded when they start up for the first time. Clings won't work well with that
        if (Utilities.ATLEAST_JB_MR2) {
            UserManager um = (UserManager) mLauncher.getSystemService(Context.USER_SERVICE);
            Bundle restrictions = um.getUserRestrictions();
            if (restrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false)) {
                return false;
            }
        }
        if (Settings.Secure.getInt(mLauncher.getContentResolver(), SKIP_FIRST_USE_HINTS, 0)
                == 1) {
            return false;
        }
        return true;
    }

    public boolean shouldShowFirstRunOrMigrationClings() {
        SharedPreferences sharedPrefs = mLauncher.getSharedPrefs();
        return areClingsEnabled() &&
            !sharedPrefs.getBoolean(WORKSPACE_CLING_DISMISSED_KEY, false) &&
            !sharedPrefs.getBoolean(MIGRATION_CLING_DISMISSED_KEY, false);
    }

    public static void markFirstRunClingDismissed(Context ctx) {
        Utilities.getPrefs(ctx).edit()
                .putBoolean(WORKSPACE_CLING_DISMISSED_KEY, true)
                .apply();
    }
}
