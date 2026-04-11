/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;

/**
 * Utility class to handle updates while the popup is visible on the Launcher
 */
public class LauncherPopupLiveUpdateHandler extends PopupLiveUpdateHandler<Launcher> {

    public LauncherPopupLiveUpdateHandler(
            Launcher launcher, PopupContainerWithArrow<Launcher> popupContainerWithArrow) {
        super(launcher, popupContainerWithArrow);
    }

    private View getWidgetsView(ViewGroup container) {
        for (int i = container.getChildCount() - 1; i >= 0; --i) {
            View systemShortcutView = container.getChildAt(i);
            if (systemShortcutView.getTag() instanceof SystemShortcut.Widgets) {
                return systemShortcutView;
            }
        }
        return null;
    }

    @Override
    public void onWidgetsBound() {
        BubbleTextView originalIcon = mPopupContainerWithArrow.getOriginalIcon();
        
        // For widget stacks and widgets, mOriginalIcon is null
        // Skip widget bound updates for them to prevent deletion issues
        // Widget stacks have their own shortcuts and don't need this update logic
        // This prevents NullPointerException and widget deletion when popup is shown
        if (originalIcon == null) {
            // This is a widget or widget stack popup - skip widget shortcut handling
            // The widget bound update is only for app icons that can have widgets
            // Widget stacks don't use the WIDGETS shortcut system
            return;
        }
        
        // Validate widget info exists in model before proceeding
        // This prevents deletion if widget info is stale or invalid
        ItemInfo itemInfo = (ItemInfo) originalIcon.getTag();
        if (itemInfo == null) {
            android.util.Log.w("LauncherPopupLiveUpdateHandler", 
                    "ItemInfo is null, skipping widget bound update");
            return;
        }
        
        // For widgets, validate widget info exists in model before accessing it
        // This prevents deletion if widget info is stale or invalid
        if (itemInfo instanceof com.android.launcher3.model.data.LauncherAppWidgetInfo) {
            com.android.launcher3.model.data.LauncherAppWidgetInfo widgetInfo =
                    (com.android.launcher3.model.data.LauncherAppWidgetInfo) itemInfo;
            com.android.launcher3.model.BgDataModel bgDataModel = mContext.getModel().getBgDataModel();
            boolean widgetExists = false;
            synchronized (bgDataModel) {
                // itemsIdMap is keyed by stable ItemInfo.id; avoid matching only appWidgetId in case
                // the host reuses an id after delete/rebind.
                ItemInfo fresh = bgDataModel.itemsIdMap.get(widgetInfo.id);
                if (fresh instanceof com.android.launcher3.model.data.LauncherAppWidgetInfo) {
                    com.android.launcher3.model.data.LauncherAppWidgetInfo wInfo =
                            (com.android.launcher3.model.data.LauncherAppWidgetInfo) fresh;
                    if (wInfo.appWidgetId == widgetInfo.appWidgetId) {
                        widgetExists = true;
                        itemInfo = wInfo;
                        originalIcon.setTag(itemInfo);
                    }
                }
            }
            
            // If widget doesn't exist in model, don't proceed to avoid triggering deletion
            // This prevents the popup from trying to update with invalid widget info
            if (!widgetExists) {
                android.util.Log.w("LauncherPopupLiveUpdateHandler", 
                        "Widget not found in model, skipping widget bound update to prevent deletion");
                return;
            }
        }
        
        SystemShortcut widgetInfo = SystemShortcut.WIDGETS.getShortcut(mContext,
                itemInfo, originalIcon);
        View widgetsView = getWidgetsView(mPopupContainerWithArrow);
        if (widgetsView == null && mPopupContainerWithArrow.getWidgetContainer() != null) {
            widgetsView = getWidgetsView(mPopupContainerWithArrow.getWidgetContainer());
        }

        if (widgetInfo != null && widgetsView == null) {
            // We didn't have any widgets cached but now there are some, so enable the shortcut.
            if (mPopupContainerWithArrow.getSystemShortcutContainer()
                    != mPopupContainerWithArrow) {
                if (mPopupContainerWithArrow.getWidgetContainer() == null) {
                    mPopupContainerWithArrow.setWidgetContainer(
                            mPopupContainerWithArrow.inflateAndAdd(
                                    R.layout.widget_shortcut_container,
                                    mPopupContainerWithArrow));
                }
                mPopupContainerWithArrow.initializeWidgetShortcut(
                        mPopupContainerWithArrow.getWidgetContainer(),
                        widgetInfo);
            } else {
                // If using the expanded system shortcut (as opposed to just the icon), we need
                // to reopen the container to ensure measurements etc. all work out. While this
                // could be quite janky, in practice the user would typically see a small
                // flicker as the animation restarts partway through, and this is a very rare
                // edge case anyway.
                mPopupContainerWithArrow.close(false);
                PopupContainerWithArrow.showForIcon(originalIcon);
            }
        } else if (widgetInfo == null && widgetsView != null) {
            // No widgets exist, but we previously added the shortcut so remove it.
            if (mPopupContainerWithArrow.getSystemShortcutContainer()
                    != mPopupContainerWithArrow
                    && mPopupContainerWithArrow.getWidgetContainer() != null) {
                mPopupContainerWithArrow.getWidgetContainer().removeView(widgetsView);
            } else {
                mPopupContainerWithArrow.close(false);
                PopupContainerWithArrow.showForIcon(originalIcon);
            }
        }
    }
}
