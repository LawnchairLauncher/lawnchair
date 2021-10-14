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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.core.content.FileProvider;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;

import java.io.File;

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

    private static final String TAG = "AppSharing";
    private static final String FILE_PROVIDER_SUFFIX = ".overview.fileprovider";
    private static final String APP_EXSTENSION = ".apk";
    private static final String APP_MIME_TYPE = "application/application";

    private final String mSharingComponent;

    private AppSharing(Launcher launcher) {
        mSharingComponent = launcher.getText(R.string.app_sharing_component).toString();
    }

    private boolean canShare(ItemInfo info) {
        /**
         * TODO: Implement once b/168831749 has been resolved
         * The implementation should check the validity of the app.
         * It should also check whether the app is free or paid, returning false in the latter case.
         * For now, all checks occur in the sharing app.
         * So, we simply check whether the sharing app is defined.
         */
        return !TextUtils.isEmpty(mSharingComponent);
    }

    private Uri getShareableUri(Context context, String path, String displayName) {
        String authority = BuildConfig.APPLICATION_ID + FILE_PROVIDER_SUFFIX;
        File pathFile = new File(path);
        return FileProvider.getUriForFile(context, authority, pathFile, displayName);
    }

    private SystemShortcut<Launcher> getShortcut(Launcher launcher, ItemInfo info) {
        if (!canShare(info)) {
            return null;
        }

        return new Share(launcher, info);
    }

    /**
     * The Share App system shortcut, used to initiate p2p sharing of a given app
     */
    public final class Share extends SystemShortcut<Launcher> {
        public Share(Launcher target, ItemInfo itemInfo) {
            super(R.drawable.ic_share, R.string.app_share_drop_target_label, target, itemInfo);
        }

        @Override
        public void onClick(View view) {
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
                        .toString() + APP_EXSTENSION;
            } catch (Exception e) {
                Log.e(TAG, "Could not find info for package \"" + packageName + "\"");
                return;
            }
            Uri uri = getShareableUri(view.getContext(), sourceDir, appLabel);
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
            sendIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);

            sendIntent.setType(APP_MIME_TYPE);
            sendIntent.setComponent(ComponentName.unflattenFromString(mSharingComponent));

            mTarget.startActivitySafely(view, sendIntent, mItemInfo);

            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }

    /**
     * Shortcut factory for generating the Share App button
     */
    public static final SystemShortcut.Factory<Launcher> SHORTCUT_FACTORY = (launcher, itemInfo) ->
            (new AppSharing(launcher)).getShortcut(launcher, itemInfo);
}
