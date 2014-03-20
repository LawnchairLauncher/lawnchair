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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

class LauncherClings {
    private static final String FIRST_RUN_CLING_DISMISSED_KEY = "cling_gel.first_run.dismissed";
    private static final String MIGRATION_CLING_DISMISSED_KEY = "cling_gel.migration.dismissed";
    private static final String MIGRATION_WORKSPACE_CLING_DISMISSED_KEY =
            "cling_gel.migration_workspace.dismissed";
    private static final String WORKSPACE_CLING_DISMISSED_KEY = "cling_gel.workspace.dismissed";
    private static final String FOLDER_CLING_DISMISSED_KEY = "cling_gel.folder.dismissed";

    private static final boolean DISABLE_CLINGS = false;

    private static final int SHOW_CLING_DURATION = 250;
    private static final int DISMISS_CLING_DURATION = 200;

    private Launcher mLauncher;
    private LayoutInflater mInflater;
    private HideFromAccessibilityHelper mHideFromAccessibilityHelper
            = new HideFromAccessibilityHelper();

    /** Ctor */
    public LauncherClings(Launcher launcher) {
        mLauncher = launcher;
        mInflater = mLauncher.getLayoutInflater();
    }

    /** Initializes a cling */
    private Cling initCling(int clingId, int scrimId, boolean animate,
                            boolean dimNavBarVisibilty) {
        Cling cling = (Cling) mLauncher.findViewById(clingId);
        View scrim = null;
        if (scrimId > 0) {
            scrim = mLauncher.findViewById(scrimId);
        }
        if (cling != null) {
            cling.init(mLauncher, scrim);
            cling.show(animate, SHOW_CLING_DURATION);

            if (dimNavBarVisibilty) {
                cling.setSystemUiVisibility(cling.getSystemUiVisibility() |
                        View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }
        return cling;
    }

    /** Returns whether the clings are enabled or should be shown */
    private boolean areClingsEnabled() {
        if (DISABLE_CLINGS) {
            return false;
        }

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
        boolean supportsLimitedUsers =
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
        Account[] accounts = AccountManager.get(mLauncher).getAccounts();
        if (supportsLimitedUsers && accounts.length == 0) {
            UserManager um = (UserManager) mLauncher.getSystemService(Context.USER_SERVICE);
            Bundle restrictions = um.getUserRestrictions();
            if (restrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS, false)) {
                return false;
            }
        }
        return true;
    }

    /** Returns whether the folder cling is visible. */
    public boolean isFolderClingVisible() {
        Cling cling = (Cling) mLauncher.findViewById(R.id.folder_cling);
        if (cling != null) {
            return cling.getVisibility() == View.VISIBLE;
        }
        return false;
    }

    private boolean skipCustomClingIfNoAccounts() {
        Cling cling = (Cling) mLauncher.findViewById(R.id.workspace_cling);
        boolean customCling = cling.getDrawIdentifier().equals("workspace_custom");
        if (customCling) {
            AccountManager am = AccountManager.get(mLauncher);
            if (am == null) return false;
            Account[] accounts = am.getAccountsByType("com.google");
            return accounts.length == 0;
        }
        return false;
    }

    /** Updates the first run cling custom content hint */
    private void setCustomContentHintVisibility(Cling cling, String ccHintStr, boolean visible,
                                                boolean animate) {
        final TextView ccHint = (TextView) cling.findViewById(R.id.custom_content_hint);
        if (ccHint != null) {
            if (visible && !ccHintStr.isEmpty()) {
                ccHint.setText(ccHintStr);
                ccHint.setVisibility(View.VISIBLE);
                if (animate) {
                    ccHint.setAlpha(0f);
                    ccHint.animate().alpha(1f)
                            .setDuration(SHOW_CLING_DURATION)
                            .start();
                } else {
                    ccHint.setAlpha(1f);
                }
            } else {
                if (animate) {
                    ccHint.animate().alpha(0f)
                            .setDuration(SHOW_CLING_DURATION)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    ccHint.setVisibility(View.GONE);
                                }
                            })
                            .start();
                } else {
                    ccHint.setAlpha(0f);
                    ccHint.setVisibility(View.GONE);
                }
            }
        }
    }

    /** Updates the first run cling custom content hint */
    public void updateCustomContentHintVisibility() {
        Cling cling = (Cling) mLauncher.findViewById(R.id.first_run_cling);
        String ccHintStr = mLauncher.getFirstRunCustomContentHint();

        if (mLauncher.getWorkspace().hasCustomContent()) {
            // Show the custom content hint if ccHintStr is not empty
            if (cling != null) {
                setCustomContentHintVisibility(cling, ccHintStr, true, true);
            }
        } else {
            // Hide the custom content hint
            if (cling != null) {
                setCustomContentHintVisibility(cling, ccHintStr, false, true);
            }
        }
    }

    /** Updates the first run cling search bar hint. */
    public void updateSearchBarHint(String hint) {
        Cling cling = (Cling) mLauncher.findViewById(R.id.first_run_cling);
        if (cling != null && cling.getVisibility() == View.VISIBLE && !hint.isEmpty()) {
            TextView sbHint = (TextView) cling.findViewById(R.id.search_bar_hint);
            sbHint.setText(hint);
            sbHint.setVisibility(View.VISIBLE);
        }
    }

    public boolean shouldShowFirstRunOrMigrationClings() {
        SharedPreferences sharedPrefs = mLauncher.getSharedPrefs();
        return areClingsEnabled() &&
            !sharedPrefs.getBoolean(FIRST_RUN_CLING_DISMISSED_KEY, false) &&
            !sharedPrefs.getBoolean(MIGRATION_CLING_DISMISSED_KEY, false);
    }

    public void removeFirstRunAndMigrationClings() {
        removeCling(R.id.first_run_cling);
        removeCling(R.id.migration_cling);
    }

    /**
     * Shows the first run cling.
     *
     * This flow is mutually exclusive with showMigrationCling, and only runs if this Launcher
     * package was preinstalled or there is no db to migrate from.
     */
    public void showFirstRunCling() {
        if (!skipCustomClingIfNoAccounts()) {
            Cling cling = (Cling) mLauncher.findViewById(R.id.first_run_cling);
            if (cling != null) {
                String sbHintStr = mLauncher.getFirstRunClingSearchBarHint();
                String ccHintStr = mLauncher.getFirstRunCustomContentHint();
                if (!sbHintStr.isEmpty()) {
                    TextView sbHint = (TextView) cling.findViewById(R.id.search_bar_hint);
                    sbHint.setText(sbHintStr);
                    sbHint.setVisibility(View.VISIBLE);
                }
                setCustomContentHintVisibility(cling, ccHintStr, true, false);
            }
            initCling(R.id.first_run_cling, 0, false, true);
        } else {
            removeFirstRunAndMigrationClings();
        }
    }

    /**
     * Shows the migration cling.
     *
     * This flow is mutually exclusive with showFirstRunCling, and only runs if this Launcher
     * package was not preinstalled and there exists a db to migrate from.
     */
    public void showMigrationCling() {
        mLauncher.hideWorkspaceSearchAndHotseat();

        Cling c = initCling(R.id.migration_cling, 0, false, true);
        c.bringScrimToFront();
        c.bringToFront();
    }

    public void showMigrationWorkspaceCling() {
        // Enable the clings only if they have not been dismissed before
        if (areClingsEnabled() && !mLauncher.getSharedPrefs().getBoolean(
                MIGRATION_WORKSPACE_CLING_DISMISSED_KEY, false)) {
            Cling c = initCling(R.id.migration_workspace_cling, 0, false, true);
            c.updateMigrationWorkspaceBubblePosition();
            c.bringScrimToFront();
            c.bringToFront();
        } else {
            removeCling(R.id.migration_workspace_cling);
        }
    }

    public void showWorkspaceCling() {
        // Enable the clings only if they have not been dismissed before
        if (areClingsEnabled() && !mLauncher.getSharedPrefs().getBoolean(
                WORKSPACE_CLING_DISMISSED_KEY, false)) {
            Cling c = initCling(R.id.workspace_cling, 0, false, true);
            c.updateWorkspaceBubblePosition();

            // Set the focused hotseat app if there is one
            c.setFocusedHotseatApp(mLauncher.getFirstRunFocusedHotseatAppDrawableId(),
                    mLauncher.getFirstRunFocusedHotseatAppRank(),
                    mLauncher.getFirstRunFocusedHotseatAppComponentName(),
                    mLauncher.getFirstRunFocusedHotseatAppBubbleTitle(),
                    mLauncher.getFirstRunFocusedHotseatAppBubbleDescription());
        } else {
            removeCling(R.id.workspace_cling);
        }
    }

    public Cling showFoldersCling() {
        SharedPreferences sharedPrefs = mLauncher.getSharedPrefs();
        // Enable the clings only if they have not been dismissed before
        if (areClingsEnabled() &&
                !sharedPrefs.getBoolean(FOLDER_CLING_DISMISSED_KEY, false) &&
                !sharedPrefs.getBoolean(Launcher.USER_HAS_MIGRATED, false)) {
            Cling cling = initCling(R.id.folder_cling, R.id.cling_scrim,
                    true, true);
            Folder openFolder = mLauncher.getWorkspace().getOpenFolder();
            if (openFolder != null) {
                Rect openFolderRect = new Rect();
                openFolder.getHitRect(openFolderRect);
                cling.setOpenFolderRect(openFolderRect);
                openFolder.bringToFront();
            }
            return cling;
        } else {
            removeCling(R.id.folder_cling);
            return null;
        }
    }

    public static void synchonouslyMarkFirstRunClingDismissed(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(
                LauncherAppState.getSharedPreferencesKey(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(LauncherClings.FIRST_RUN_CLING_DISMISSED_KEY, true);
        editor.commit();
    }

    public void markFolderClingDismissed() {
        SharedPreferences.Editor editor = mLauncher.getSharedPrefs().edit();
        editor.putBoolean(LauncherClings.FOLDER_CLING_DISMISSED_KEY, true);
        editor.apply();
    }

    /** Removes the cling outright from the DragLayer */
    private void removeCling(int id) {
        final View cling = mLauncher.findViewById(id);
        if (cling != null) {
            final ViewGroup parent = (ViewGroup) cling.getParent();
            parent.post(new Runnable() {
                @Override
                public void run() {
                    parent.removeView(cling);
                }
            });
            mHideFromAccessibilityHelper.restoreImportantForAccessibility(mLauncher.getDragLayer());
        }
    }

    /** Hides the specified Cling */
    private void dismissCling(final Cling cling, final Runnable postAnimationCb,
                              final String flag, int duration, boolean restoreNavBarVisibilty) {
        // To catch cases where siblings of top-level views are made invisible, just check whether
        // the cling is directly set to GONE before dismissing it.
        if (cling != null && cling.getVisibility() != View.GONE) {
            final Runnable cleanUpClingCb = new Runnable() {
                public void run() {
                    cling.cleanup();
                    SharedPreferences.Editor editor = mLauncher.getSharedPrefs().edit();
                    editor.putBoolean(flag, true);
                    editor.apply();
                    if (postAnimationCb != null) {
                        postAnimationCb.run();
                    }
                }
            };
            if (duration <= 0) {
                cleanUpClingCb.run();
            } else {
                cling.hide(duration, cleanUpClingCb);
            }
            mHideFromAccessibilityHelper.restoreImportantForAccessibility(mLauncher.getDragLayer());

            if (restoreNavBarVisibilty) {
                cling.setSystemUiVisibility(cling.getSystemUiVisibility() &
                        ~View.SYSTEM_UI_FLAG_LOW_PROFILE);
            }
        }
    }

    public void dismissFirstRunCling(View v) {
        Cling cling = (Cling) mLauncher.findViewById(R.id.first_run_cling);
        Runnable cb = new Runnable() {
            public void run() {
                // Show the workspace cling next
                showWorkspaceCling();
            }
        };
        dismissCling(cling, cb, FIRST_RUN_CLING_DISMISSED_KEY,
                DISMISS_CLING_DURATION, false);

        // Fade out the search bar for the workspace cling coming up
        mLauncher.getSearchBar().hideSearchBar(true);
    }

    private void dismissMigrationCling() {
        mLauncher.showWorkspaceSearchAndHotseat();
        Runnable dismissCb = new Runnable() {
            public void run() {
                Cling cling = (Cling) mLauncher.findViewById(R.id.migration_cling);
                Runnable cb = new Runnable() {
                    public void run() {
                        // Show the migration workspace cling next
                        showMigrationWorkspaceCling();
                    }
                };
                dismissCling(cling, cb, MIGRATION_CLING_DISMISSED_KEY,
                        DISMISS_CLING_DURATION, true);
            }
        };
        mLauncher.getWorkspace().post(dismissCb);
    }

    private void dismissAnyWorkspaceCling(Cling cling, String key, View v) {
        Runnable cb = null;
        if (v == null) {
            cb = new Runnable() {
                public void run() {
                    mLauncher.getWorkspace().enterOverviewMode();
                }
            };
        }
        dismissCling(cling, cb, key, DISMISS_CLING_DURATION, true);

        // Fade in the search bar
        mLauncher.getSearchBar().showSearchBar(true);
    }

    public void dismissMigrationClingCopyApps(View v) {
        // Copy the shortcuts from the old database
        LauncherModel model = mLauncher.getModel();
        model.resetLoadedState(false, true);
        model.startLoader(false, PagedView.INVALID_RESTORE_PAGE,
                LauncherModel.LOADER_FLAG_CLEAR_WORKSPACE
                        | LauncherModel.LOADER_FLAG_MIGRATE_SHORTCUTS);

        // Set the flag to skip the folder cling
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = mLauncher.getSharedPreferences(spKey, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(Launcher.USER_HAS_MIGRATED, true);
        editor.apply();

        // Disable the migration cling
        dismissMigrationCling();
    }

    public void dismissMigrationClingUseDefault(View v) {
        // Clear the workspace
        LauncherModel model = mLauncher.getModel();
        model.resetLoadedState(false, true);
        model.startLoader(false, PagedView.INVALID_RESTORE_PAGE,
                LauncherModel.LOADER_FLAG_CLEAR_WORKSPACE);

        // Disable the migration cling
        dismissMigrationCling();
    }

    public void dismissMigrationWorkspaceCling(View v) {
        Cling cling = (Cling) mLauncher.findViewById(R.id.migration_workspace_cling);
        dismissAnyWorkspaceCling(cling, MIGRATION_WORKSPACE_CLING_DISMISSED_KEY, v);
    }

    public void dismissWorkspaceCling(View v) {
        Cling cling = (Cling) mLauncher.findViewById(R.id.workspace_cling);
        dismissAnyWorkspaceCling(cling, WORKSPACE_CLING_DISMISSED_KEY, v);
    }

    public void dismissFolderCling(View v) {
        Cling cling = (Cling) mLauncher.findViewById(R.id.folder_cling);
        dismissCling(cling, null, FOLDER_CLING_DISMISSED_KEY,
                DISMISS_CLING_DURATION, true);
    }
}
