/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.popup;

import static com.android.launcher3.util.SplitConfigurationOptions.getLogEventForPosition;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.quickstep.views.RecentsView;

public interface QuickstepSystemShortcut {

    String TAG = QuickstepSystemShortcut.class.getSimpleName();

    static SystemShortcut.Factory<QuickstepLauncher> getSplitSelectShortcutByPosition(
            SplitPositionOption position) {
        return (activity, itemInfo, originalView) ->
                new QuickstepSystemShortcut.SplitSelectSystemShortcut(activity, itemInfo,
                        originalView, position);
    }

    class SplitSelectSystemShortcut extends SystemShortcut<QuickstepLauncher> {

        private final SplitPositionOption mPosition;

        public SplitSelectSystemShortcut(QuickstepLauncher launcher, ItemInfo itemInfo,
                View originalView, SplitPositionOption position) {
            super(position.iconResId, position.textResId, launcher, itemInfo, originalView);

            mPosition = position;
        }

        @Override
        public void onClick(View view) {
            // Initiate splitscreen from the Home screen or Home All Apps
            Bitmap bitmap;
            Intent intent;
            if (mItemInfo instanceof WorkspaceItemInfo) {
                final WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) mItemInfo;
                bitmap = workspaceItemInfo.bitmap.icon;
                intent = workspaceItemInfo.intent;
            } else if (mItemInfo instanceof com.android.launcher3.model.data.AppInfo) {
                final com.android.launcher3.model.data.AppInfo appInfo =
                        (com.android.launcher3.model.data.AppInfo) mItemInfo;
                bitmap = appInfo.bitmap.icon;
                intent = appInfo.intent;
            } else {
                Log.e(TAG, "unknown item type");
                return;
            }

            RecentsView recentsView = mTarget.getOverviewPanel();
            StatsLogManager.EventEnum splitEvent = getLogEventForPosition(mPosition.stagePosition);
            recentsView.initiateSplitSelect(
                    new SplitSelectSource(mOriginalView, new BitmapDrawable(bitmap), intent,
                            mPosition, mItemInfo, splitEvent));
        }
    }

    class SplitSelectSource {

        public final View view;
        public final Drawable drawable;
        public final Intent intent;
        public final SplitPositionOption position;
        public final ItemInfo mItemInfo;
        public final StatsLogManager.EventEnum splitEvent;

        public SplitSelectSource(View view, Drawable drawable, Intent intent,
                SplitPositionOption position, ItemInfo itemInfo,
                StatsLogManager.EventEnum splitEvent) {
            this.view = view;
            this.drawable = drawable;
            this.intent = intent;
            this.position = position;
            this.mItemInfo = itemInfo;
            this.splitEvent = splitEvent;
        }
    }
}
