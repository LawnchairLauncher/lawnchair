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

package com.android.launcher3;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_APP_SHARE_TAP;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.android.internal.annotations.VisibleForTesting;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.AppShareabilityChecker;
import com.android.launcher3.model.AppShareabilityJobService;
import com.android.launcher3.model.AppShareabilityManager;
import com.android.launcher3.model.AppShareabilityManager.ShareabilityStatus;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.views.ActivityContext;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Defines the Share system shortcut and its factory.
 * This shortcut can be added to the app long-press menu on the home screen.
 * Clicking the button will initiate peer-to-peer sharing of the app.
 */
public final class AppSharing {
    /**
     * This flag enables this feature. It is defined here rather than in launcher3's FeatureFlags
     * because it is unique to Go and not toggleable at runtime.
     */
    public static final boolean ENABLE_APP_SHARING = true;
    /**
     * With this flag enabled, the Share App button will be dynamically enabled/disabled based
     * on each app's shareability status.
     */
    public static final boolean ENABLE_SHAREABILITY_CHECK = true;

    private static final String TAG = "AppSharing";
    private static final String FILE_PROVIDER_SUFFIX = ".overview.fileprovider";
    private static final String APP_EXTENSION = ".apk";
    private static final String APP_MIME_TYPE = "application/application";

    private final String mSharingComponent;
    private AppShareabilityManager mShareabilityMgr;

    private AppSharing(Launcher launcher) {
        String sharingComponent = Settings.Secure.getString(launcher.getContentResolver(),
                Settings.Secure.NEARBY_SHARING_COMPONENT);
        mSharingComponent = TextUtils.isEmpty(sharingComponent) ? launcher.getText(
                R.string.app_sharing_component).toString() : sharingComponent;
    }

    private Uri getShareableUri(Context context, String path, String displayName) {
        String authority = BuildConfig.APPLICATION_ID + FILE_PROVIDER_SUFFIX;
        File pathFile = new File(path);
        return FileProvider.getUriForFile(context, authority, pathFile, displayName);
    }

    private SystemShortcut<Launcher> getShortcut(Launcher launcher, ItemInfo info,
            View originalView) {
        if (TextUtils.isEmpty(mSharingComponent)) {
            return null;
        }
        return new Share(launcher, info, originalView);
    }

    /**
     * Instantiates AppShareabilityManager, which then reads app shareability data from disk
     * Also schedules a job to update those data
     * @param context The application context
     * @param checker An implementation of AppShareabilityChecker to perform the actual checks
     *                when updating the data
     */
    public static void setUpShareabilityCache(Context context, AppShareabilityChecker checker) {
        AppShareabilityManager shareMgr = AppShareabilityManager.INSTANCE.get(context);
        shareMgr.setShareabilityChecker(checker);
        AppShareabilityJobService.schedule(context);
    }

    /**
     * The Share App system shortcut, used to initiate p2p sharing of a given app
     */
    public final class Share extends SystemShortcut<Launcher> {
        private final boolean mSharingEnabledForUser;

        private final Set<View> mBoundViews = Collections.newSetFromMap(new WeakHashMap<>());
        private boolean mIsEnabled = true;
        private StatsLogManager mStatsLogManager;

        public Share(Launcher target, ItemInfo itemInfo, View originalView) {
            super(R.drawable.ic_share, R.string.app_share_drop_target_label, target, itemInfo,
                    originalView);
            mStatsLogManager = ActivityContext.lookupContext(originalView.getContext())
                    .getStatsLogManager();
            mSharingEnabledForUser = bluetoothSharingEnabled(target);
            if (!mSharingEnabledForUser) {
                setEnabled(false);
            } else if (ENABLE_SHAREABILITY_CHECK) {
                mShareabilityMgr =
                        AppShareabilityManager.INSTANCE.get(target.getApplicationContext());
                checkShareability(/* requestUpdateIfUnknown */ true);
            }
        }

        @Override
        public void setIconAndLabelFor(View iconView, TextView labelView) {
            super.setIconAndLabelFor(iconView, labelView);
            mBoundViews.add(iconView);
            mBoundViews.add(labelView);
        }

        @Override
        public void setIconAndContentDescriptionFor(ImageView view) {
            super.setIconAndContentDescriptionFor(view);
            mBoundViews.add(view);
        }

        @Override
        public void onClick(View view) {
            mStatsLogManager.logger().log(LAUNCHER_SYSTEM_SHORTCUT_APP_SHARE_TAP);
            if (!mIsEnabled) {
                showCannotShareToast(view.getContext());
                return;
            }

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);

            ComponentName targetComponent = mItemInfo.getTargetComponent();
            if (targetComponent == null) {
                Log.e(TAG, "Item missing target component");
                return;
            }
            String packageName = targetComponent.getPackageName();
            PackageManager packageManager = view.getContext().getPackageManager();
            String sourceDir, appLabel;
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                sourceDir = packageInfo.applicationInfo.sourceDir;
                appLabel = packageManager.getApplicationLabel(packageInfo.applicationInfo)
                        + APP_EXTENSION;
            } catch (Exception e) {
                Log.e(TAG, "Could not find info for package \"" + packageName + "\"");
                return;
            }
            Uri uri = getShareableUri(view.getContext(), sourceDir, appLabel);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
            sendIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);

            sendIntent.setType(APP_MIME_TYPE);
            sendIntent.setComponent(ComponentName.unflattenFromString(mSharingComponent));

            UserHandle user = mItemInfo.user;
            if (user != null && !user.equals(Process.myUserHandle())) {
                mTarget.startActivityAsUser(sendIntent, user);
            } else {
                mTarget.startActivitySafely(view, sendIntent, mItemInfo);
            }

            AbstractFloatingView.closeAllOpenViews(mTarget);
        }

        private void onStatusUpdated(boolean success) {
            if (!success) {
                // Something went wrong. Specific error logged in AppShareabilityManager.
                return;
            }
            checkShareability(/* requestUpdateIfUnknown */ false);
        }

        private void checkShareability(boolean requestUpdateIfUnknown) {
            String packageName = mItemInfo.getTargetComponent().getPackageName();
            @ShareabilityStatus int status = mShareabilityMgr.getStatus(packageName);
            setEnabled(status == ShareabilityStatus.SHAREABLE);

            if (requestUpdateIfUnknown && status == ShareabilityStatus.UNKNOWN) {
                mShareabilityMgr.requestAppStatusUpdate(packageName, this::onStatusUpdated);
            }
        }

        private boolean bluetoothSharingEnabled(Context context) {
            return !context.getSystemService(UserManager.class)
                    .hasUserRestriction(UserManager.DISALLOW_BLUETOOTH_SHARING, mItemInfo.user);
        }

        private void showCannotShareToast(Context context) {
            ActivityContext activityContext = ActivityContext.lookupContext(context);
            String blockedByMessage = activityContext.getStringCache() != null
                    ? activityContext.getStringCache().disabledByAdminMessage
                    : context.getString(R.string.blocked_by_policy);

            CharSequence text = (mSharingEnabledForUser)
                    ? context.getText(R.string.toast_p2p_app_not_shareable)
                    : blockedByMessage;
            int duration = Toast.LENGTH_SHORT;
            Toast.makeText(context, text, duration).show();
        }

        public void setEnabled(boolean isEnabled) {
            if (mIsEnabled != isEnabled) {
                mIsEnabled = isEnabled;
                mBoundViews.forEach(v -> v.setEnabled(isEnabled));
            }
        }

        public boolean isEnabled() {
            return mIsEnabled;
        }

        @VisibleForTesting
        void setStatsLogManager(StatsLogManager statsLogManager) {
            mStatsLogManager = statsLogManager;
        }
    }

    /**
     * Shortcut factory for generating the Share App button
     */
    public static final SystemShortcut.Factory<Launcher> SHORTCUT_FACTORY =
            (launcher, itemInfo, originalView) ->
                    (new AppSharing(launcher)).getShortcut(launcher, itemInfo, originalView);
}
