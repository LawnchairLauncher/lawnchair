/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.accessibility;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;

import android.view.KeyEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.notification.NotificationMainView;
import com.android.launcher3.shortcuts.DeepShortcutView;

import java.util.Collections;
import java.util.List;

/**
 * Extension of {@link LauncherAccessibilityDelegate} with actions specific to shortcuts in
 * deep shortcuts menu.
 */
public class ShortcutMenuAccessibilityDelegate extends LauncherAccessibilityDelegate {

    private static final int DISMISS_NOTIFICATION = R.id.action_dismiss_notification;

    public ShortcutMenuAccessibilityDelegate(Launcher launcher) {
        super(launcher);
        mActions.put(DISMISS_NOTIFICATION, new LauncherAction(DISMISS_NOTIFICATION,
                R.string.action_dismiss_notification, KeyEvent.KEYCODE_X));
    }

    @Override
    protected void getSupportedActions(View host, ItemInfo item, List<LauncherAction> out) {
        if ((host.getParent() instanceof DeepShortcutView)) {
            out.add(mActions.get(ADD_TO_WORKSPACE));
        } else if (host instanceof NotificationMainView) {
            if (((NotificationMainView) host).canChildBeDismissed()) {
                out.add(mActions.get(DISMISS_NOTIFICATION));
            }
        }
    }

    @Override
    protected boolean performAction(View host, ItemInfo item, int action, boolean fromKeyboard) {
        if (action == ADD_TO_WORKSPACE) {
            if (!(host.getParent() instanceof DeepShortcutView)) {
                return false;
            }
            final WorkspaceItemInfo info = ((DeepShortcutView) host.getParent()).getFinalInfo();
            final int[] coordinates = new int[2];
            final int screenId = findSpaceOnWorkspace(item, coordinates);
            mLauncher.getStateManager().goToState(NORMAL, true, forSuccessCallback(() -> {
                mLauncher.getModelWriter().addItemToDatabase(info,
                        LauncherSettings.Favorites.CONTAINER_DESKTOP,
                        screenId, coordinates[0], coordinates[1]);
                mLauncher.bindItems(Collections.singletonList(info), true);
                AbstractFloatingView.closeAllOpenViews(mLauncher);
                announceConfirmation(R.string.item_added_to_workspace);
            }));
            return true;
        } else if (action == DISMISS_NOTIFICATION) {
            if (!(host instanceof NotificationMainView)) {
                return false;
            }
            ((NotificationMainView) host).onChildDismissed();
            announceConfirmation(R.string.notification_dismissed);
            return true;
        }
        return false;
    }
}
