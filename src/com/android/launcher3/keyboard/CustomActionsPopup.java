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

package com.android.launcher3.keyboard;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.popup.PopupContainerWithArrow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles showing a popup menu with available custom actions for a launcher icon.
 * This allows exposing various custom actions using keyboard shortcuts.
 */
public class CustomActionsPopup implements OnMenuItemClickListener {

    private final Launcher mLauncher;
    private final LauncherAccessibilityDelegate mDelegate;
    private final View mIcon;

    public CustomActionsPopup(Launcher launcher, View icon) {
        mLauncher = launcher;
        mIcon = icon;
        PopupContainerWithArrow container = PopupContainerWithArrow.getOpen(launcher);
        if (container != null) {
            mDelegate = container.getAccessibilityDelegate();
        } else {
            mDelegate = launcher.getAccessibilityDelegate();
        }
    }

    private List<AccessibilityAction> getActionList() {
        if (mIcon == null || !(mIcon.getTag() instanceof ItemInfo)) {
            return Collections.EMPTY_LIST;
        }

        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        mDelegate.addSupportedActions(mIcon, info, true);
        List<AccessibilityAction> result = new ArrayList<>(info.getActionList());
        info.recycle();
        return result;
    }

    public boolean canShow() {
        return !getActionList().isEmpty();
    }

    public boolean show() {
        List<AccessibilityAction> actions = getActionList();
        if (actions.isEmpty()) {
            return false;
        }

        PopupMenu popup = new PopupMenu(mLauncher, mIcon);
        popup.setOnMenuItemClickListener(this);
        Menu menu = popup.getMenu();
        for (AccessibilityAction action : actions) {
            menu.add(Menu.NONE, action.getId(), Menu.NONE, action.getLabel());
        }
        popup.show();
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        return mDelegate.performAction(mIcon, (ItemInfo) mIcon.getTag(), menuItem.getItemId());
    }
}
